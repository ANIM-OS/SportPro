package dev.dreamteam.sportpro.ui.callups

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import dev.dreamteam.sportpro.BuildConfig

/** Shared encounter identity; independent from the demo match screens. */
data class CallupMatch(val id: String, val teamId: String, val category: String, val opponent: String, val kickoff: Timestamp)
data class PlayerCallup(val id: String, val matchId: String, val teamId: String, val playerId: String, val selected: Boolean)

class CallupRepository {
    private val db = FirebaseFirestore.getInstance()
    private val fixtures = db.collection("matchFixtures")
    private val selections = db.collection("matchCallupPlayers")
    private val uid get() = requireNotNull(FirebaseAuth.getInstance().currentUser?.uid)

    fun matches(teamId: String, change: (List<CallupMatch>) -> Unit, error: (String) -> Unit) =
        fixtures.whereEqualTo("teamId", teamId).addSnapshotListener { snapshot, failure ->
            if (failure != null) error("No se pudieron cargar los partidos: ${failure.localizedMessage}")
            else change(snapshot?.documents.orEmpty().mapNotNull(::match).sortedBy { it.kickoff })
        }
    fun match(id: String, change: (CallupMatch?) -> Unit, error: (String) -> Unit) =
        fixtures.document(id).addSnapshotListener { snapshot, failure ->
            if (failure != null) error("No se pudo consultar el encuentro") else change(snapshot?.let(::match))
        }
    fun coachSelections(matchId: String, change: (List<PlayerCallup>) -> Unit, error: (String) -> Unit) =
        selections.whereEqualTo("ownerId", uid).whereEqualTo("matchId", matchId)
            .addSnapshotListener { snapshot, failure ->
                if (failure != null) error("No se pudo cargar la convocatoria")
                else change(snapshot?.documents.orEmpty().mapNotNull(::selection))
            }
    fun mySelections(change: (List<PlayerCallup>) -> Unit, error: (String) -> Unit) =
        selections.whereEqualTo("playerId", uid).addSnapshotListener { snapshot, failure ->
            if (failure != null) error("No se pudieron cargar tus convocatorias")
            else change(snapshot?.documents.orEmpty().mapNotNull(::selection))
        }
    fun saveMatch(existing: CallupMatch?, teamId: String, category: String, opponent: String, kickoff: Long): Task<Void> {
        if (!BuildConfig.LOCAL_FIREBASE) return Tasks.forException(IllegalStateException("Publicación pendiente de habilitar"))
        if (opponent.isBlank() || opponent.trim().length > 100 || kickoff <= System.currentTimeMillis())
            return Tasks.forException(IllegalArgumentException("Completa el rival y elige una fecha futura"))
        val data = mapOf("teamId" to teamId, "ownerId" to uid, "category" to category,
            "opponent" to opponent.trim(), "kickoff" to Timestamp(java.util.Date(kickoff)), "updatedAt" to FieldValue.serverTimestamp())
        return (existing?.let { fixtures.document(it.id) } ?: fixtures.document()).set(data)
    }
    /** Each player's update is independently validated. A retry converges to the requested selection. */
    fun saveSelections(match: CallupMatch, selected: Set<String>, previous: List<PlayerCallup>): Task<Void> {
        if (!BuildConfig.LOCAL_FIREBASE) return Tasks.forException(IllegalStateException("Publicación pendiente de habilitar"))
        val targets = selected + previous.filter { it.selected }.map { it.playerId }
        val writes = targets.map { player ->
            val ref = selections.document("${match.id}_$player")
            db.runTransaction { tx ->
                val encounter = tx.get(fixtures.document(match.id))
                val team = tx.get(db.collection("teams").document(match.teamId))
                val membership = if (player in selected) tx.get(db.collection("teamMemberships").document("${match.teamId}_$player")) else null
                check(encounter.getString("teamId") == match.teamId && encounter.getString("category") == match.category) { "El partido cambió. Vuelve a seleccionarlo." }
                check(team.getString("createdBy") == uid) { "Ya no administras este equipo" }
                check((encounter.getTimestamp("kickoff")?.toDate()?.time ?: 0L) > System.currentTimeMillis()) { "El partido ya comenzó" }
                if (player in selected) check(membership?.getString("category") == match.category && membership.getString("userId") == player) { "Un jugador ya no pertenece a esta categoría" }
                tx.set(ref, mapOf("matchId" to match.id, "teamId" to match.teamId, "ownerId" to uid,
                    "playerId" to player, "selected" to (player in selected), "updatedAt" to FieldValue.serverTimestamp()))
                null
            }
        }
        return Tasks.whenAll(writes)
    }
    private fun match(doc: DocumentSnapshot): CallupMatch? {
        return CallupMatch(doc.id, doc.getString("teamId") ?: return null,
            doc.getString("category") ?: return null, doc.getString("opponent") ?: return null,
            doc.getTimestamp("kickoff") ?: return null)
    }
    private fun selection(doc: DocumentSnapshot): PlayerCallup? {
        return PlayerCallup(doc.id, doc.getString("matchId") ?: return null,
            doc.getString("teamId") ?: return null, doc.getString("playerId") ?: return null,
            doc.getBoolean("selected") ?: false)
    }
}
