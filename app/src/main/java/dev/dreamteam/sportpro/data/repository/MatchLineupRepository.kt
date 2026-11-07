package dev.dreamteam.sportpro.data.repository

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import dev.dreamteam.sportpro.BuildConfig
import dev.dreamteam.sportpro.data.model.MatchLineupEntry
import dev.dreamteam.sportpro.ui.callups.CallupMatch


/** US-11. One lineup per encounter; one player document and exclusive starter-slot documents. */
class MatchLineupRepository {
    private val db = FirebaseFirestore.getInstance()
    private fun lineup(matchId: String) = db.collection("matchLineups").document(matchId)
    fun observeFormation(matchId: String, change: (String) -> Unit, error: (String) -> Unit) =
        lineup(matchId).addSnapshotListener { snapshot, failure ->
            if (failure != null) error("No se pudo cargar la formación")
            else change(snapshot?.getString("formation") ?: "4-3-3")
        }
    fun observePlayers(matchId: String, change: (List<MatchLineupEntry>) -> Unit, error: (String) -> Unit) =
        lineup(matchId).collection("players").addSnapshotListener { snapshot, failure ->
            if (failure != null) error("No se pudo cargar la alineación")
            else change(snapshot?.documents.orEmpty().mapNotNull { doc ->
                MatchLineupEntry(doc.id, doc.getString("kind") ?: return@mapNotNull null,
                    doc.getString("position") ?: return@mapNotNull null)
            })
        }
    fun formation(match: CallupMatch, formation: String): Task<Void> {
        if (!BuildConfig.LOCAL_FIREBASE) return denied()
        return lineup(match.id).set(mapOf("teamId" to match.teamId, "formation" to formation, "updatedAt" to FieldValue.serverTimestamp()))
    }
    fun assign(match: CallupMatch, formation: String, playerId: String, kind: String, position: String): Task<Void> {
        if (!BuildConfig.LOCAL_FIREBASE) return denied()
        return db.runTransaction { tx ->
            val ref = lineup(match.id)
            val parent = tx.get(ref)
            check(!parent.exists() || parent.getString("formation") == formation) { "La formación cambió. Revisa la cancha antes de guardar." }
            val playerRef = ref.collection("players").document(playerId)
            val previous = tx.get(playerRef)
            val target = if (kind == "STARTER") ref.collection("slots").document(position) else null
            val targetDoc = target?.let { tx.get(it) }
            check(targetDoc == null || !targetDoc.exists() || targetDoc.getString("playerId") == playerId) { "Esta posición ya está ocupada. Retira o mueve primero al titular." }
            val callup = tx.get(db.collection("matchCallupPlayers").document("${match.id}_$playerId"))
            check(callup.getBoolean("selected") == true) { "El jugador ya no está convocado" }
            if (!parent.exists()) tx.set(ref, mapOf("teamId" to match.teamId, "formation" to formation, "updatedAt" to FieldValue.serverTimestamp()))
            val oldPosition = previous.getString("position")
            if (previous.getString("kind") == "STARTER" && (kind != "STARTER" || oldPosition != position)) {
                tx.delete(ref.collection("slots").document(requireNotNull(oldPosition)))
            }
            if (target != null) tx.set(target, mapOf("playerId" to playerId, "updatedAt" to FieldValue.serverTimestamp()))
            tx.set(playerRef, mapOf("playerId" to playerId, "kind" to kind, "position" to position, "updatedAt" to FieldValue.serverTimestamp()))
            null
        }
    }
    fun remove(matchId: String, playerId: String): Task<Void> {
        if (!BuildConfig.LOCAL_FIREBASE) return denied()
        return db.runTransaction { tx ->
            val ref = lineup(matchId).collection("players").document(playerId)
            val old = tx.get(ref)
            if (old.getString("kind") == "STARTER") tx.delete(lineup(matchId).collection("slots").document(requireNotNull(old.getString("position"))))
            tx.delete(ref)
            null
        }
    }
    private fun denied(): Task<Void> = Tasks.forException(IllegalStateException("Función en pruebas locales"))
}
