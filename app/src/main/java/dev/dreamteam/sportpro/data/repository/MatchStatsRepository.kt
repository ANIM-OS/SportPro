package dev.dreamteam.sportpro.data.repository

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import dev.dreamteam.sportpro.data.model.GuardianLink
import dev.dreamteam.sportpro.data.model.MatchEventRecord
import dev.dreamteam.sportpro.data.model.MatchInput
import dev.dreamteam.sportpro.data.model.Team

/** Histórico cargado: partidos con sus datos, nombres que el rol puede ver y partidos que no se pudieron leer. */
data class StatsLoad(val matches: List<MatchInput>, val rosterNames: Map<String, String> = emptyMap(), val failed: Int = 0)

/**
 * US-16: carga el histórico de partidos para Estadísticas con lecturas únicas (get: caché y servidor).
 * Usa solo lecturas que las reglas ya permiten a cada rol (sin collectionGroup ni reglas nuevas):
 * - dueña: partidos del equipo, eventos, convocatoria y alineación, y la plantilla para los nombres;
 * - jugador: sus convocatorias vigentes → partido, eventos y alineación;
 * - tutor: convocatorias vigentes de su jugador → acceso de tutor (US-11) → eventos y alineación;
 * - admin: todos los partidos, eventos, convocatoria y alineación (sin nombres).
 * El tiempo real del partido en juego o abierto lo da [FirestoreMatchEventStore.observe] en la pantalla.
 */
class MatchStatsRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val fixtures = db.collection("matchFixtures")
    private val selections = db.collection("matchCallupPlayers")
    private val store = FirestoreMatchEventStore(db)
    private val uid get() = requireNotNull(FirebaseAuth.getInstance().currentUser?.uid) { "Inicia sesión" }

    /** Datos básicos de un partido antes de leer sus eventos. */
    private data class Fixture(val id: String, val teamId: String, val teamName: String, val category: String,
        val opponent: String, val kickoffMillis: Long, val endedAtMillis: Long?)

    private fun fixture(doc: DocumentSnapshot, teamName: String): Fixture? = Fixture(doc.id,
        doc.getString("teamId") ?: return null, teamName, doc.getString("category") ?: return null,
        doc.getString("opponent").orEmpty(), doc.getTimestamp("kickoff")?.toDate()?.time ?: return null,
        doc.getTimestamp("endedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time)

    private fun lineup(matchId: String): Task<Pair<Set<String>, Set<String>>> =
        db.collection("matchLineups").document(matchId).collection("players").get().continueWith { task ->
            if (!task.isSuccessful) return@continueWith emptySet<String>() to emptySet()
            val docs = task.result.documents
            docs.filter { it.getString("kind") == "STARTER" }.map { it.id }.toSet() to
                docs.filter { it.getString("kind") == "BENCH" }.map { it.id }.toSet()
        }

    /** Lee eventos, convocados y alineación de cada partido. Un partido cuyos eventos fallan se cuenta en failed. */
    private fun assemble(list: List<Fixture>, called: (Fixture) -> Task<Set<String>>, names: Map<String, String> = emptyMap()): Task<StatsLoad> {
        val tasks = list.map { f ->
            val events = store.fetch(f.id)
            val calledTask = called(f).continueWith { if (it.isSuccessful) it.result else emptySet() }
            val lineupTask = lineup(f.id)
            Tasks.whenAllComplete(events, calledTask, lineupTask).continueWith {
                if (!events.isSuccessful) null
                else {
                    val (starters, bench) = lineupTask.result
                    MatchInput(f.id, f.teamId, f.teamName, f.category, f.opponent, f.kickoffMillis, f.endedAtMillis,
                        events.result, calledTask.result, starters, bench)
                }
            }
        }
        return Tasks.whenAllComplete(tasks).continueWith {
            val loaded = tasks.mapNotNull { if (it.isSuccessful) it.result else null }
            StatsLoad(loaded, names, failed = list.size - loaded.size)
        }
    }

    private fun selectedIds(snapshot: QuerySnapshot): Set<String> =
        snapshot.documents.filter { it.getBoolean("selected") == true }.mapNotNull { it.getString("playerId") }.toSet()

    /** Dueña: todos los partidos del equipo, con nombres de la plantilla. */
    fun loadOwner(team: Team): Task<StatsLoad> {
        val me = uid
        val roster = db.collection("teamMemberships").whereEqualTo("teamId", team.id).get()
        return fixtures.whereEqualTo("teamId", team.id).get().continueWithTask { task ->
            val list = task.result.documents.mapNotNull { fixture(it, team.name) }
            Tasks.whenAllComplete(roster).continueWithTask {
                val names = if (roster.isSuccessful) roster.result.documents.mapNotNull { doc ->
                    val id = doc.getString("userId") ?: return@mapNotNull null
                    id to (doc.getString("playerName") ?: "Jugador")
                }.toMap() else emptyMap()
                assemble(list, { f ->
                    selections.whereEqualTo("ownerId", me).whereEqualTo("matchId", f.id).get().continueWith { selectedIds(it.result) }
                }, names)
            }
        }
    }

    /** Jugador: solo los partidos donde está convocado (selected == true). */
    fun loadPlayer(): Task<StatsLoad> {
        val me = uid
        return selections.whereEqualTo("playerId", me).get().continueWithTask { task ->
            val rows = task.result.documents.filter { it.getBoolean("selected") == true }
            val reads = rows.map { row -> fixtureOrCopy(row, readFixture = true) }
            Tasks.whenAllComplete(reads).continueWithTask {
                val list = reads.mapNotNull { if (it.isSuccessful) it.result else null }.distinctBy { it.id }
                assemble(list, { Tasks.forResult(setOf(me)) })
            }
        }
    }

    /**
     * Tutor: partidos donde su jugador vinculado está convocado. Antes de leer los eventos guarda su acceso
     * de tutor en cada partido (matchLineups/{m}/guardians/{uid}, US-11), igual que al abrir la pestaña LIVE.
     */
    fun loadGuardian(link: GuardianLink): Task<StatsLoad> =
        selections.whereEqualTo("playerId", link.playerId).get().continueWithTask { task ->
            val rows = task.result.documents.filter { it.getBoolean("selected") == true }
            val list = rows.mapNotNull { copyFixture(it) }.distinctBy { it.id }
            val access = list.map { MatchLineupRepository().openAsGuardian(it.id, link.playerId) }
            Tasks.whenAllComplete(access).continueWithTask {
                // Sin acceso (p. ej. partido cancelado) no se pueden leer sus eventos: queda fuera.
                val allowed = list.filterIndexed { i, _ -> access[i].isSuccessful }
                assemble(allowed, { Tasks.forResult(setOf(link.playerId)) }, mapOf(link.playerId to link.playerName))
            }
        }

    /** Admin: todos los partidos; sin nombres de equipo ni de jugadores. */
    fun loadAdmin(): Task<StatsLoad> = fixtures.get().continueWithTask { task ->
        val list = task.result.documents.mapNotNull { fixture(it, teamName = "") }
        assemble(list, { f -> selections.whereEqualTo("matchId", f.id).get().continueWith { selectedIds(it.result) } })
    }

    /** Eventos de un partido (reintento puntual o vista de partido). */
    fun events(matchId: String): Task<List<MatchEventRecord>> = store.fetch(matchId)

    /** El partido leído de matchFixtures (con endedAt) o, si no se puede leer, la copia de la convocatoria. */
    private fun fixtureOrCopy(row: DocumentSnapshot, readFixture: Boolean): Task<Fixture> {
        val copy = copyFixture(row)
        val matchId = row.getString("matchId") ?: return Tasks.forException(IllegalStateException("Sin partido"))
        if (!readFixture) return copy?.let { Tasks.forResult(it) } ?: Tasks.forException(IllegalStateException("Sin datos"))
        return fixtures.document(matchId).get().continueWith { task ->
            if (task.isSuccessful) {
                val doc = task.result
                // Confirmado por el servidor que ya no existe: partido cancelado, no entra en el histórico.
                if (!doc.exists()) { if (doc.metadata.isFromCache) copy ?: throw IllegalStateException("Sin datos") else throw IllegalStateException("Cancelado") }
                else fixture(doc, copy?.teamName.orEmpty()) ?: throw IllegalStateException("Sin datos")
            } else copy ?: throw IllegalStateException("Sin datos")
        }
    }

    private fun copyFixture(row: DocumentSnapshot): Fixture? {
        val encounter = row.get("encounter") as? Map<*, *> ?: return null
        val kickoff = (encounter["kickoff"] as? Timestamp)?.toDate()?.time ?: return null
        return Fixture(row.getString("matchId") ?: return null, row.getString("teamId").orEmpty(),
            encounter["teamName"] as? String ?: "", encounter["category"] as? String ?: "",
            encounter["opponent"] as? String ?: "", kickoff, null)
    }
}
