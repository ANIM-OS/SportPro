package dev.dreamteam.sportpro.domain.tryouts

import dev.dreamteam.sportpro.data.model.TRYOUT_CANCELLED
import dev.dreamteam.sportpro.data.model.TryoutNotice
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// Same limits as the tryoutNotices block in firestore.rules.
const val TRYOUT_TITLE_MAX = 100
const val TRYOUT_DESCRIPTION_MAX = 2000
const val TRYOUT_LOCATION_MAX = 120

enum class TryoutState { VIGENTE, VENCIDO, CANCELADO }

/** US-20: las fechas de los avisos se guardan y se muestran en hora de Lima, sin depender del celular. */
val TRYOUT_ZONE: ZoneId = ZoneId.of("America/Lima")

/** Hoy en Lima. */
fun tryoutToday(nowMillis: Long = System.currentTimeMillis()): LocalDate = epochMillisToDate(nowMillis, TRYOUT_ZONE)

/** Cancelado por alguien que no es el autor (admin o moderador). */
fun TryoutNotice.cancelledByModeration(): Boolean =
    status == TRYOUT_CANCELLED && !cancelledBy.isNullOrBlank() && cancelledBy != authorId

/** Puede cancelar: el autor o el admin/moderador, solo si sigue vigente. */
fun TryoutNotice.canCancel(viewerId: String?, isModerator: Boolean, nowMillis: Long): Boolean =
    state(nowMillis) == TryoutState.VIGENTE && !viewerId.isNullOrBlank() && (authorId == viewerId || isModerator)

/**
 * A notice expires once its deadline passes; without a deadline it stays current until cancelled.
 * Computed on the device because Spark has no Cloud Functions to update the status.
 */
fun TryoutNotice.state(nowMillis: Long): TryoutState = when {
    status == TRYOUT_CANCELLED -> TryoutState.CANCELADO
    deadlineMillis != null && nowMillis > deadlineMillis -> TryoutState.VENCIDO
    else -> TryoutState.VIGENTE
}

/** Player filters. A notice without category or position is open to all, so it always matches those filters. */
data class TryoutFilter(
    val category: String? = null,
    val position: String? = null,
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val location: String = ""
) {
    val isActive: Boolean
        get() = category != null || position != null || fromDate != null || toDate != null || location.isNotBlank()

    fun matches(notice: TryoutNotice, zone: ZoneId = TRYOUT_ZONE): Boolean {
        val tryoutDate = notice.tryoutDateMillis?.let { epochMillisToDate(it, zone) }
        return (category == null || notice.category == null || notice.category == category) &&
            (position == null || notice.position == null || notice.position == position) &&
            (fromDate == null || (tryoutDate != null && !tryoutDate.isBefore(fromDate))) &&
            (toDate == null || (tryoutDate != null && !tryoutDate.isAfter(toDate))) &&
            (location.isBlank() || notice.location?.normalizedForSearch()?.contains(location.normalizedForSearch()) == true)
    }
}

data class TryoutFormErrors(
    val title: String? = null,
    val description: String? = null,
    val location: String? = null,
    val tryoutDate: String? = null,
    val deadline: String? = null
) {
    val hasErrors: Boolean
        get() = listOf(title, description, location, tryoutDate, deadline).any { it != null }
}

fun validateTryout(
    title: String,
    description: String,
    location: String,
    tryoutDate: LocalDate?,
    deadline: LocalDate?,
    today: LocalDate
): TryoutFormErrors = TryoutFormErrors(
    title = when {
        title.isBlank() -> "Ingresa un título"
        title.trim().length > TRYOUT_TITLE_MAX -> "El título admite hasta $TRYOUT_TITLE_MAX caracteres"
        else -> null
    },
    description = when {
        description.isBlank() -> "Ingresa una descripción"
        description.trim().length > TRYOUT_DESCRIPTION_MAX -> "La descripción admite hasta $TRYOUT_DESCRIPTION_MAX caracteres"
        else -> null
    },
    location = if (location.trim().length > TRYOUT_LOCATION_MAX) "La ubicación admite hasta $TRYOUT_LOCATION_MAX caracteres" else null,
    tryoutDate = if (tryoutDate != null && tryoutDate.isBefore(today)) "La fecha de la prueba no puede ser anterior a hoy (hora de Lima)" else null,
    deadline = when {
        deadline == null -> null
        deadline.isBefore(today) -> "La fecha límite no puede ser anterior a hoy (hora de Lima)"
        tryoutDate != null && deadline.isAfter(tryoutDate) -> "La fecha límite debe ser el día de la prueba o antes"
        else -> null
    }
)

fun String.normalizedForSearch(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

fun LocalDate.startOfDayMillis(zone: ZoneId = TRYOUT_ZONE): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()

/** US-20: la fecha límite vence a las 23:59:59 de ese día en Lima (zona fija, no la del celular). */
fun LocalDate.endOfDayMillis(zone: ZoneId = TRYOUT_ZONE): Long =
    atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()

fun epochMillisToDate(millis: Long, zone: ZoneId = TRYOUT_ZONE): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

// Material3's DatePicker works with UTC midnight.
fun datePickerMillisToDate(utcMillis: Long): LocalDate = epochMillisToDate(utcMillis, ZoneOffset.UTC)

fun LocalDate.toDatePickerMillis(): Long = startOfDayMillis(ZoneOffset.UTC)
