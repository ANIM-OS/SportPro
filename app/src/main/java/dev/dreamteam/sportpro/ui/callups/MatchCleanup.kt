package dev.dreamteam.sportpro.ui.callups

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

/**
 * Borrado en cascada de un partido: alineación (jugadores, posiciones y documento),
 * respuestas de disponibilidad, convocatorias y, al final, el partido.
 * Lo usan "Cancelar partido" (US-09) y la eliminación de equipos (US-03).
 */
class MatchCleanup(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    fun deleteTeamMatches(teamId: String, done: () -> Unit, failed: (Exception) -> Unit) {
        firestore.collection("matchFixtures").whereEqualTo("teamId", teamId).get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                val matches = snapshot.documents
                fun next(index: Int) {
                    if (index == matches.size) done()
                    else deleteMatch(matches[index].reference, { next(index + 1) }, failed)
                }
                next(0)
            }.addOnFailureListener(failed)
    }

    fun deleteMatch(fixture: DocumentReference, done: () -> Unit, failed: (Exception) -> Unit) {
        val lineup = firestore.collection("matchLineups").document(fixture.id)
        fun deleteSelections() {
            val ownerId = auth.currentUser?.uid ?: return failed(IllegalStateException("La sesión terminó"))
            firestore.collection("matchCallupPlayers")
                .whereEqualTo("ownerId", ownerId).whereEqualTo("matchId", fixture.id).limit(100)
                .get(Source.SERVER).addOnSuccessListener { snapshot ->
                    if (snapshot.isEmpty) {
                        fixture.delete().addOnSuccessListener { done() }.addOnFailureListener(failed)
                    } else {
                        val responses = snapshot.documents.map { selection ->
                            firestore.collection("matchAvailability").document(selection.id).get(Source.SERVER)
                        }
                        Tasks.whenAllSuccess<DocumentSnapshot>(responses).addOnSuccessListener { responseDocs ->
                            val batch = firestore.batch()
                            responseDocs.filter { it.exists() }.forEach { batch.delete(it.reference) }
                            snapshot.documents.forEach { batch.delete(it.reference) }
                            batch.commit().addOnSuccessListener { deleteSelections() }.addOnFailureListener(failed)
                        }.addOnFailureListener(failed)
                    }
                }.addOnFailureListener(failed)
        }
        fun deleteLineup() {
            lineup.collection("players").limit(5).get(Source.SERVER).addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val batch = firestore.batch()
                    snapshot.documents.forEach { player ->
                        if (player.getString("kind") == "STARTER") {
                            player.getString("position")?.let { batch.delete(lineup.collection("slots").document(it)) }
                        }
                        batch.delete(player.reference)
                    }
                    batch.commit().addOnSuccessListener { deleteLineup() }.addOnFailureListener(failed)
                } else {
                    deleteLineupSlots(lineup, {
                        lineup.get(Source.SERVER).addOnSuccessListener { parent ->
                            if (parent.exists()) lineup.delete().addOnSuccessListener { deleteSelections() }.addOnFailureListener(failed)
                            else deleteSelections()
                        }.addOnFailureListener(failed)
                    }, failed)
                }
            }.addOnFailureListener(failed)
        }
        deleteLineup()
    }

    private fun deleteLineupSlots(lineup: DocumentReference, done: () -> Unit, failed: (Exception) -> Unit) {
        lineup.collection("slots").limit(10).get(Source.SERVER).addOnSuccessListener { snapshot ->
            if (snapshot.isEmpty) done()
            else {
                val batch = firestore.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().addOnSuccessListener { deleteLineupSlots(lineup, done, failed) }.addOnFailureListener(failed)
            }
        }.addOnFailureListener(failed)
    }
}
