package dev.dreamteam.sportpro.data.model

const val TRYOUT_ACTIVE = "ACTIVE"
const val TRYOUT_CANCELLED = "CANCELLED"

/** US-20: tryout or open call notice. Dates are epoch millis; null means the field was left empty. */
data class TryoutNotice(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val title: String = "",
    val description: String = "",
    val category: String? = null,
    val position: String? = null,
    val tryoutDateMillis: Long? = null,
    val location: String? = null,
    val deadlineMillis: Long? = null,
    val status: String = TRYOUT_ACTIVE,
    val createdAtMillis: Long? = null
)
