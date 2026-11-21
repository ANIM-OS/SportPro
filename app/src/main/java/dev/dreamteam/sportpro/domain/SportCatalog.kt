package dev.dreamteam.sportpro.domain

/** Values shared by teams and tryout notices. firestore.rules repeats these lists for tryoutNotices. */
object SportCatalog {
    val CATEGORIES = listOf(
        "SUB-10", "SUB-12", "SUB-15", "SUB-17", "PRIMERA", "FEMENIL-SUB-15", "FEMENIL-SUB-17"
    )

    // Same values as PlayerProfile.POSITIONS (US-04) so tryout filters match player profiles.
    val POSITIONS = listOf(
        "Portero", "Defensa central", "Lateral", "Mediocampista", "Extremo", "Delantero"
    )
}
