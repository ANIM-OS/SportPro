package dev.dreamteam.sportpro.ui.callups

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*

/** Shared encounter identity for Partidos, convocatorias, disponibilidad and lineups. */
data class CallupMatch(val id: String, val teamId: String, val category: String, val opponent: String, val kickoff: Timestamp, val responseDeadline: Timestamp = kickoff, val ownerId: String = "", val teamName: String = "")
data class PlayerCallup(val id: String, val matchId: String, val teamId: String, val playerId: String, val selected: Boolean, val encounter: CallupMatch? = null, val ownerId: String = "")

internal const val MAX_CALLUP_WRITE_COST = 15
internal fun callupWriteCost(selected: Set<String>, saved: Set<String>): Int =
    (selected - saved).size + 3 * (saved - selected).size
/** Estado de una convocatoria tal como la ven el jugador y el tutor. */
enum class CallupCardState(val label: String) {
    CONVOCADO("Convocado"), RETIRADA("Convocatoria retirada"), FINALIZADO("Partido finalizado"),
    CANCELADO("Partido cancelado"), SIN_CONFIRMAR("Convocatoria sin confirmar")
}

/** [fixtureExists]: true = existe, false = ya no existe (cancelado), null = desconocido o sin acceso. */
internal fun callupCardState(selected: Boolean, fixtureExists: Boolean?, kickoffMillis: Long?, nowMillis: Long): CallupCardState = when {
    fixtureExists == false -> CallupCardState.CANCELADO
    !selected -> CallupCardState.RETIRADA
    kickoffMillis == null -> CallupCardState.SIN_CONFIRMAR
    kickoffMillis <= nowMillis -> CallupCardState.FINALIZADO
    else -> CallupCardState.CONVOCADO
}

internal fun encounterLabel(match: CallupMatch): String =
    (if (match.teamName.isBlank()) "" else "Equipo: ${match.teamName}\n") +
        "vs ${match.opponent}\n${match.category}\n${dateLabel(match.kickoff.toDate().time)} (hora local)"

internal const val CALLUP_WRITE_LIMIT_MESSAGE = "Guarda primero estos cambios para continuar: hasta 15 altas o 5 retiros por guardado."

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
    /** [missing] se llama cuando el servidor confirma que el partido ya no existe (cancelado). */
    fun match(id: String, change: (CallupMatch?) -> Unit, error: (String) -> Unit, missing: () -> Unit = {}) =
        fixtures.document(id).addSnapshotListener { snapshot, failure ->
            when {
                failure != null -> error("No se pudo consultar el encuentro")
                snapshot != null && !snapshot.exists() && !snapshot.metadata.isFromCache -> missing()
                else -> change(snapshot?.let(::match))
            }
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
    fun playerSelections(playerId: String, change: (List<PlayerCallup>) -> Unit, error: (String) -> Unit) =
        selections.whereEqualTo("playerId", playerId).addSnapshotListener { snapshot, failure ->
            if (failure != null) error("No se pudieron consultar las convocatorias del jugador")
            else change(snapshot?.documents.orEmpty().mapNotNull(::selection))
        }
    fun saveMatch(existing: CallupMatch?, teamId: String, teamName: String, category: String, opponent: String, kickoff: Long, deadline: Long = kickoff): Task<Void> {
        if (opponent.isBlank() || opponent.trim().length > 100 || kickoff <= System.currentTimeMillis() || deadline <= System.currentTimeMillis() || deadline > kickoff)
            return Tasks.forException(IllegalArgumentException("El cierre debe ser futuro y no posterior al partido"))
        if (existing != null && existing.responseDeadline.toDate().time <= System.currentTimeMillis())
            return Tasks.forException(IllegalStateException("El plazo de respuesta terminó; este partido ya no se puede editar"))
        val data = mapOf("teamId" to teamId, "ownerId" to uid, "category" to category,
            "opponent" to opponent.trim(), "kickoff" to Timestamp(java.util.Date(kickoff)),
            "responseDeadline" to Timestamp(java.util.Date(deadline)), "updatedAt" to FieldValue.serverTimestamp())
        val ref = existing?.let { fixtures.document(it.id) } ?: fixtures.document()
        if (existing == null) return ref.set(data)
        // Publish encounter details to the existing recipients without exposing other teams to guardians.
        return selections.whereEqualTo("ownerId", uid).whereEqualTo("matchId", existing.id).get(Source.SERVER)
            .continueWithTask { task ->
                val docs = task.result.documents
                check(docs.size <= 450) { "Demasiados destinatarios para actualizar el partido" }
                val batch = db.batch().set(ref, data)
                val details = mapOf("opponent" to opponent.trim(), "category" to category, "teamName" to teamName,
                    "kickoff" to Timestamp(java.util.Date(kickoff)), "responseDeadline" to Timestamp(java.util.Date(deadline)))
                docs.forEach { batch.update(it.reference, "encounter", details, "updatedAt", FieldValue.serverTimestamp()) }
                batch.commit()
            }
    }
    /** Commit the changed recipients and any lineup withdrawals together. */
    fun saveSelections(match: CallupMatch, selected: Set<String>, previous: List<PlayerCallup>): Task<Void> {
        val actor = uid
        val previouslySelected = previous.filter { it.selected }.map { it.playerId }.toSet()
        val additions = selected - previouslySelected
        val withdrawals = previouslySelected - selected
        val changed = additions + withdrawals
        if (changed.isEmpty()) return Tasks.forResult(null)
        if (callupWriteCost(selected, previouslySelected) > MAX_CALLUP_WRITE_COST)
            return Tasks.forException(IllegalArgumentException(CALLUP_WRITE_LIMIT_MESSAGE))
        return db.runTransaction { tx ->
            val encounter = tx.get(fixtures.document(match.id))
            val team = tx.get(db.collection("teams").document(match.teamId))
            val current = changed.associateWith { player -> tx.get(selections.document("${match.id}_$player")) }
            val memberships = additions.associateWith { player ->
                tx.get(db.collection("teamMemberships").document("${match.teamId}_$player"))
            }
            val lineupRef = db.collection("matchLineups").document(match.id)
            val assignments = withdrawals.associateWith { player -> tx.get(lineupRef.collection("players").document(player)) }
            // US-10: al retirar, y al reconvocar a quien ya tenía convocatoria, la respuesta anterior deja de valer.
            // Se lee primero (la regla get no depende de que exista) y solo se borra si existe.
            val reconvocations = additions.filter { current.getValue(it).exists() }
            val responses = (withdrawals + reconvocations).associateWith { player ->
                tx.get(db.collection("matchAvailability").document("${match.id}_$player"))
            }
            check(encounter.getString("teamId") == match.teamId && encounter.getString("category") == match.category) {
                "El partido cambió. Vuelve a seleccionarlo."
            }
            check(encounter.getString("ownerId") == actor && team.getString("createdBy") == actor) {
                "Ya no administras este equipo"
            }
            check((encounter.getTimestamp("kickoff")?.toDate()?.time ?: 0L) > System.currentTimeMillis()) {
                "El partido ya comenzó"
            }
            changed.forEach { player ->
                check((current.getValue(player).getBoolean("selected") == true) == (player in previouslySelected)) {
                    "La convocatoria cambió en otro dispositivo. Recárgala antes de guardar."
                }
            }
            additions.forEach { player ->
                val membership = memberships.getValue(player)
                check(membership.getString("teamId") == match.teamId && membership.getString("userId") == player &&
                    membership.getString("category") == match.category) {
                    "Un jugador ya no pertenece a esta categoría"
                }
            }
            val details = mapOf("opponent" to encounter.getString("opponent"), "category" to match.category,
                "teamName" to team.getString("name").orEmpty(),
                "kickoff" to encounter.getTimestamp("kickoff"),
                "responseDeadline" to (encounter.getTimestamp("responseDeadline") ?: encounter.getTimestamp("kickoff")))
            changed.forEach { player ->
                val oldAssignment = assignments[player]
                if (oldAssignment?.exists() == true) {
                    if (oldAssignment.getString("kind") == "STARTER") {
                        tx.delete(lineupRef.collection("slots").document(requireNotNull(oldAssignment.getString("position"))))
                    }
                    tx.delete(lineupRef.collection("players").document(player))
                }
                responses[player]?.takeIf { it.exists() }?.let { tx.delete(it.reference) }
                tx.set(selections.document("${match.id}_$player"), mapOf(
                    "matchId" to match.id, "teamId" to match.teamId, "ownerId" to actor,
                    "playerId" to player, "selected" to (player in selected), "encounter" to details,
                    "updatedAt" to FieldValue.serverTimestamp()))
            }
            null
        }
    }
    private fun match(doc: DocumentSnapshot): CallupMatch? {
        return CallupMatch(doc.id, doc.getString("teamId") ?: return null,
            doc.getString("category") ?: return null, doc.getString("opponent") ?: return null,
            doc.getTimestamp("kickoff") ?: return null,
            doc.getTimestamp("responseDeadline") ?: doc.getTimestamp("kickoff") ?: return null,
            doc.getString("ownerId").orEmpty())
    }
    private fun selection(doc: DocumentSnapshot): PlayerCallup? {
        return PlayerCallup(doc.id, doc.getString("matchId") ?: return null,
            doc.getString("teamId") ?: return null, doc.getString("playerId") ?: return null,
            doc.getBoolean("selected") ?: false,
            (doc.get("encounter") as? Map<*, *>)?.let { fields ->
                val kickoff = fields["kickoff"] as? Timestamp ?: return@let null
                CallupMatch(doc.getString("matchId").orEmpty(), doc.getString("teamId").orEmpty(),
                    fields["category"] as? String ?: "", fields["opponent"] as? String ?: "", kickoff,
                    fields["responseDeadline"] as? Timestamp ?: kickoff, doc.getString("ownerId").orEmpty(),
                    fields["teamName"] as? String ?: "")
            }, doc.getString("ownerId").orEmpty())
    }
}
