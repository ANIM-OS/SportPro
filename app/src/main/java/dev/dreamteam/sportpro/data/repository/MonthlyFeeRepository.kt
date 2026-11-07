package dev.dreamteam.sportpro.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import dev.dreamteam.sportpro.data.model.FeeStatus
import dev.dreamteam.sportpro.data.model.MonthlyFee
import dev.dreamteam.sportpro.data.model.Team
import dev.dreamteam.sportpro.data.model.TeamMember

/** US-05: registro simulado de mensualidades. No integra pasarela de pago ni procesa dinero. */
class MonthlyFeeRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val fees = firestore.collection(COLLECTION)

    /** Mensualidades de un equipo en un periodo, para el entrenador que las registra. */
    fun listenTeamPeriod(
        teamId: String,
        period: String,
        onChange: (List<MonthlyFee>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: run {
            onError("Debes iniciar sesión")
            return null
        }
        return listen(
            fees.whereEqualTo("coachId", uid).whereEqualTo("teamId", teamId).whereEqualTo("period", period),
            onChange, onError
        )
    }

    /** Historial de un jugador; [asCoach] limita a lo registrado por el entrenador actual. */
    fun listenPlayer(
        playerId: String,
        asCoach: Boolean,
        onChange: (List<MonthlyFee>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: run {
            onError("Debes iniciar sesión")
            return null
        }
        var query: Query = fees.whereEqualTo("playerId", playerId)
        if (asCoach) query = query.whereEqualTo("coachId", uid)
        return listen(query, onChange, onError)
    }

    /** Registra la mensualidad solo si el jugador no tiene ya una para ese periodo. */
    fun register(
        team: Team,
        member: TeamMember,
        period: String,
        amount: Double?,
        status: FeeStatus,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run {
            onError("Debes iniciar sesión")
            return
        }
        val ref = fees.document(MonthlyFee.documentId(member.userId, period))
        firestore.runTransaction { transaction ->
            if (transaction.get(ref).exists()) throw DuplicatePeriodException()
            val data = hashMapOf<String, Any>(
                "playerId" to member.userId,
                "playerName" to member.playerName,
                "teamId" to team.id,
                "teamName" to team.name,
                "coachId" to uid,
                "period" to period,
                "status" to status.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (amount != null) data["amount"] = amount
            if (status == FeeStatus.PAGADA) data["paidAt"] = FieldValue.serverTimestamp()
            transaction.set(ref, data)
            null
        }.addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(
                    if (error is DuplicatePeriodException || error.cause is DuplicatePeriodException) {
                        "${member.playerName} ya tiene una mensualidad registrada en ese periodo"
                    } else {
                        error.userMessage("No se pudo registrar la mensualidad")
                    }
                )
            }
    }

    fun updateStatus(fee: MonthlyFee, status: FeeStatus, onSuccess: () -> Unit, onError: (String) -> Unit) {
        fees.document(fee.id).update(
            mapOf(
                "status" to status.name,
                "paidAt" to if (status == FeeStatus.PAGADA) FieldValue.serverTimestamp() else FieldValue.delete(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.userMessage("No se pudo actualizar la mensualidad")) }
    }

    private fun listen(
        query: Query,
        onChange: (List<MonthlyFee>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration = query.addSnapshotListener { snapshot, error ->
        if (error != null) {
            onError(error.userMessage("No se pudieron cargar las mensualidades"))
            return@addSnapshotListener
        }
        onChange(snapshot?.documents?.mapNotNull(::fromDocument)?.sortedByDescending { it.period }.orEmpty())
    }

    private fun fromDocument(doc: DocumentSnapshot): MonthlyFee? {
        val playerId = doc.getString("playerId") ?: return null
        return MonthlyFee(
            id = doc.id,
            playerId = playerId,
            playerName = doc.getString("playerName").orEmpty(),
            teamId = doc.getString("teamId").orEmpty(),
            teamName = doc.getString("teamName").orEmpty(),
            coachId = doc.getString("coachId").orEmpty(),
            period = doc.getString("period").orEmpty(),
            amount = doc.getDouble("amount"),
            status = FeeStatus.fromCode(doc.getString("status")),
            paidAt = doc.getTimestamp("paidAt"),
            updatedAt = doc.getTimestamp("updatedAt")
        )
    }

    private class DuplicatePeriodException : Exception("Periodo duplicado")

    companion object {
        const val COLLECTION = "monthlyFees"
    }
}
