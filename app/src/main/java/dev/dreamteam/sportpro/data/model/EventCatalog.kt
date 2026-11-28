package dev.dreamteam.sportpro.data.model

import java.text.Normalizer

/**
 * US-12: tipos de evento del catálogo global (eventCatalog) y del equipo (teams/{id}/eventTypes).
 * Por defecto solo el tipo es obligatorio; cada tipo puede exigir datos de [CoreFields] y datos extra.
 */
data class ExtraField(
    val key: String,
    val label: String,
    val kind: String = ExtraKinds.TEXT,
    val options: List<String> = emptyList(),
    val required: Boolean = false
)

data class EventType(
    val id: String,
    val name: String,
    val description: String = "",
    val requiredCore: List<String> = emptyList(),
    val extraFields: List<ExtraField> = emptyList(),
    val active: Boolean = true,
    val base: Boolean = false,
    val order: Int = 1000,
    /** true = tipo propio del equipo (teams/{id}/eventTypes, kind CUSTOM). */
    val custom: Boolean = false,
    /** Cambio guardado en el celular y aún no confirmado por el servidor. */
    val pending: Boolean = false
) {
    val requiredExtra: List<String> get() = extraFields.filter { it.required }.map { it.key }
}

object ExtraKinds {
    const val TEXT = "text"
    const val NUMBER = "number"
    const val CHOICE = "choice"
    /** Uno de los convocados del partido (se guarda su uid). */
    const val PLAYER = "player"
    val ALL = listOf(TEXT, NUMBER, CHOICE, PLAYER)
    fun label(kind: String) = when (kind) { NUMBER -> "Número"; CHOICE -> "Opciones"; PLAYER -> "Jugador convocado"; else -> "Texto" }
}

/** Lista cerrada de datos comunes que un tipo puede marcar como obligatorios. */
object CoreFields {
    const val MINUTE = "minute"
    const val TEAM = "team"
    const val PLAYER = "playerId"
    const val NOTES = "notes"
    val ALL = listOf(MINUTE, TEAM, PLAYER, NOTES)
    fun label(field: String) = when (field) {
        MINUTE -> "Minuto"; TEAM -> "Equipo"; PLAYER -> "Jugador"; NOTES -> "Observaciones"; else -> field
    }
}

object EventCatalogRules {
    const val CUSTOM_PREFIX = "eq_"
    const val MAX_ID = 40
    const val MAX_EXTRA_KEY = 30
    const val MAX_EXTRAS = 6
    const val MAX_NAME = 40
    const val MAX_DESCRIPTION = 200
    const val MAX_LABEL = 30

    /** Los 9 tipos base con IDs estables. También son el respaldo si aún no hay catálogo global. */
    val BASE_TYPES: List<EventType> = listOf(
        EventType("gol", "Gol", base = true, order = 10),
        EventType("falta", "Falta", base = true, order = 20),
        EventType("penal", "Penal", base = true, order = 30, extraFields = listOf(
            ExtraField("resultado", "Resultado", ExtraKinds.CHOICE, listOf("Gol", "Fallado", "Atajado")))),
        EventType("tarjeta", "Tarjeta", base = true, order = 40, extraFields = listOf(
            ExtraField("color", "Color", ExtraKinds.CHOICE, listOf("Amarilla", "Roja"), required = true))),
        EventType("fuera_de_juego", "Fuera de juego", base = true, order = 50),
        EventType("cambio", "Cambio", base = true, order = 60, extraFields = listOf(
            ExtraField("sale", "Sale", ExtraKinds.PLAYER), ExtraField("entra", "Entra", ExtraKinds.PLAYER))),
        EventType("saque_lateral", "Saque lateral", base = true, order = 70),
        EventType("saque_de_meta", "Saque de meta", base = true, order = 80),
        EventType("tiro_de_esquina", "Tiro de esquina", base = true, order = 90)
    )
    val BASE_IDS: Set<String> = BASE_TYPES.map { it.id }.toSet()

    /** Minúsculas, sin tildes, solo [a-z0-9_]; recortado a [max]. Puede quedar vacío. */
    fun slug(text: String, max: Int = MAX_ID): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(max)
            .trimEnd('_')

    /**
     * Clave interna generada a partir del nombre (no se muestra). [prefix] "eq_" para los tipos propios.
     * Si el nombre no da al menos 2 caracteres se usa [fallback]; si ya existe se añade _2, _3…
     */
    fun uniqueKey(name: String, existing: Set<String>, prefix: String = "", max: Int = MAX_ID, fallback: String = "tipo"): String {
        val room = max - prefix.length
        val core = slug(name, room).takeIf { it.length >= 2 } ?: fallback
        var candidate = prefix + core
        var n = 2
        while (candidate in existing) {
            val suffix = "_$n"
            candidate = prefix + core.take(room - suffix.length).trimEnd('_') + suffix
            n++
        }
        return candidate
    }

    /** Globales efectivos: los de Firestore o, si aún no hay ninguno, los 9 base del código. */
    fun effectiveGlobals(globals: List<EventType>): List<EventType> = globals.ifEmpty { BASE_TYPES }

    /**
     * Tipos que se ofrecen al registrar en un equipo = globales activos sin desactivar por el equipo
     * + propios activos. Un global desactivado por el admin no vuelve aunque el equipo lo active.
     */
    fun activeTypes(globals: List<EventType>, overrides: Map<String, Boolean>, customs: List<EventType>): List<EventType> =
        (effectiveGlobals(globals).filter { it.active && overrides[it.id] != false } + customs.filter { it.active })
            .sortedWith(compareBy<EventType> { it.order }.thenBy { it.name.lowercase() })

    /** Texto legible de lo que exige un tipo. */
    fun requirementsLabel(type: EventType): String {
        val core = listOf("Tipo") + type.requiredCore.map(CoreFields::label)
        val extras = type.extraFields.joinToString(", ") { if (it.required) "${it.label} (obligatorio)" else it.label }
        return "Obligatorio: ${core.joinToString(", ")}" + if (extras.isNotBlank()) " · Datos adicionales: $extras" else ""
    }

    /**
     * Tipo propio a partir de uno antiguo del celular (SharedPreferences). Todo queda no obligatorio
     * salvo el tipo; las claves técnicas se convierten en casillas o datos con etiqueta legible.
     */
    fun fromLegacy(id: String, name: String, description: String, fields: List<String>, active: Boolean, existing: Set<String>): EventType {
        val newId = uniqueKey(name.ifBlank { id }, existing, CUSTOM_PREFIX)
        val extras = mutableListOf<ExtraField>()
        fields.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach { field ->
            when (field.lowercase()) {
                "minuto", "jugadorid" -> Unit // pasan a casillas, sin marcar como obligatorias
                "color" -> extras += ExtraField("color", "Color", ExtraKinds.CHOICE, listOf("Amarilla", "Roja"))
                else -> {
                    val label = humanize(field).take(MAX_LABEL)
                    extras += ExtraField(uniqueKey(label, extras.map { it.key }.toSet(), max = MAX_EXTRA_KEY, fallback = "dato"), label)
                }
            }
        }
        return EventType(newId, name.ifBlank { id }.take(MAX_NAME), description.take(MAX_DESCRIPTION),
            extraFields = extras.take(MAX_EXTRAS), active = active, custom = true)
    }

    /** "jugadorSaleId" → "Jugador sale id"; "dato_extra" → "Dato extra". */
    fun humanize(key: String): String {
        val words = key.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ').trim().lowercase()
        return words.replaceFirstChar { it.uppercase() }
    }
}
