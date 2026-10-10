package dev.dreamteam.sportpro.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import dev.dreamteam.sportpro.data.model.Team
import dev.dreamteam.sportpro.data.model.TeamMember

/** Equipo al que pertenece el usuario como jugador, con la categoría asignada. */
data class PlayerMembership(
    val team: Team,
    val category: String,
    val playerName: String
)

/**
 * Lectura de equipos y plantillas para los módulos de entrenamiento y jugadores.
 * Usa las mismas colecciones que la gestión de equipos (US-03): teams y teamMemberships.
 */
class TeamDirectoryRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    val currentUserId: String? get() = auth.currentUser?.uid

    /** Equipos y academias creados por el usuario (los que puede administrar). */
    fun listenOwnedTeams(onChange: (List<Team>) -> Unit, onError: (String) -> Unit): ListenerRegistration? {
        val uid = currentUserId ?: run {
            onError("Debes iniciar sesión")
            return null
        }
        return firestore.collection("teams").whereEqualTo("createdBy", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.userMessage("No se pudieron cargar tus equipos"))
                    return@addSnapshotListener
                }
                onChange(snapshot?.documents?.mapNotNull(::teamFromDocument)?.sortedBy { it.name.lowercase() }.orEmpty())
            }
    }

    /** Equipos en los que el usuario figura como jugador. */
    fun loadMemberships(onResult: (List<PlayerMembership>) -> Unit, onError: (String) -> Unit) {
        val uid = currentUserId ?: run {
            onResult(emptyList())
            return
        }
        firestore.collection("teamMemberships").whereEqualTo("userId", uid).get()
            .addOnSuccessListener { snapshot ->
                val rows = snapshot.documents.mapNotNull { doc ->
                    val teamId = doc.getString("teamId") ?: return@mapNotNull null
                    Triple(teamId, doc.getString("category").orEmpty(), doc.getString("playerName").orEmpty())
                }
                if (rows.isEmpty()) {
                    onResult(emptyList())
                    return@addOnSuccessListener
                }
                val result = mutableListOf<PlayerMembership>()
                var pending = rows.size
                rows.forEach { (teamId, category, playerName) ->
                    firestore.collection("teams").document(teamId).get()
                        .addOnSuccessListener { doc ->
                            teamFromDocument(doc)?.let { result += PlayerMembership(it, category, playerName) }
                        }
                        .addOnCompleteListener {
                            pending--
                            if (pending == 0) onResult(result.sortedBy { it.team.name.lowercase() })
                        }
                }
            }
            .addOnFailureListener { onError(it.userMessage("No se pudieron cargar tus equipos")) }
    }

    /** Plantilla de un equipo (jugadores aceptados), ordenada por categoría y nombre. */
    fun listenRoster(teamId: String, onChange: (List<TeamMember>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        firestore.collection("teamMemberships").whereEqualTo("teamId", teamId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.userMessage("No se pudo cargar la plantilla"))
                    return@addSnapshotListener
                }
                val members = snapshot?.documents?.mapNotNull { doc ->
                    val uid = doc.getString("userId") ?: return@mapNotNull null
                    TeamMember(
                        userId = uid,
                        playerName = doc.getString("playerName") ?: "Jugador ${uid.take(6)}",
                        category = doc.getString("category").orEmpty(),
                        jerseyNumber = doc.getLong("jerseyNumber")?.toInt()
                    )
                }?.sortedWith(compareBy({ it.category }, { it.playerName.lowercase() })).orEmpty()
                onChange(members)
            }

    private fun teamFromDocument(doc: DocumentSnapshot): Team? {
        val name = doc.getString("name") ?: return null
        return Team(
            id = doc.id,
            createdBy = doc.getString("createdBy").orEmpty(),
            name = name,
            type = doc.getString("type") ?: "EQUIPO",
            academyId = doc.getString("academyId"),
            categories = (doc.get("categories") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            city = doc.getString("city").orEmpty(),
            description = doc.getString("description").orEmpty(),
            createdAt = doc.getTimestamp("createdAt")
        )
    }
}

/** Mensaje claro en español para errores de Firestore. */
internal fun Exception.userMessage(fallback: String): String =
    if ((this as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        "No tienes permiso para realizar esta acción"
    } else {
        localizedMessage ?: fallback
    }
