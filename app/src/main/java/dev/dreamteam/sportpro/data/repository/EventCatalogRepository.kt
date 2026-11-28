package dev.dreamteam.sportpro.data.repository

import android.content.Context
import dev.dreamteam.sportpro.data.model.EventCatalogRules
import org.json.JSONArray

/** Tipo guardado por la versión anterior del catálogo, que vivía solo en el celular. */
data class CatalogEventType(
    val id: String,
    val name: String,
    val description: String = "",
    val requiredFields: List<String> = emptyList(),
    val active: Boolean = true,
    val base: Boolean = false
)

/**
 * US-12: el catálogo ya vive en Firestore (EventCatalogFirestoreRepository). Esta clase solo lee los
 * tipos PROPIOS que se crearon antes en este celular, para ofrecer "Importar a este equipo" o
 * "Descartar" una única vez. Los tipos base locales no se migran: ya existen en el catálogo global.
 */
class EventCatalogRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("us12_event_catalog", Context.MODE_PRIVATE)
    private val key = "event_types_v1"

    fun legacyCustomTypes(): List<CatalogEventType> = try {
        val array = JSONArray(prefs.getString(key, null) ?: "[]")
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val fields = item.optJSONArray("requiredFields") ?: JSONArray()
            CatalogEventType(
                id = item.getString("id"),
                name = item.optString("name"),
                description = item.optString("description"),
                requiredFields = (0 until fields.length()).map { fields.getString(it) },
                active = item.optBoolean("active", true),
                base = item.optBoolean("base", false)
            )
        }.filter { !it.base && it.id !in EventCatalogRules.BASE_IDS }
    } catch (_: Exception) {
        emptyList()
    }

    /** Tras importar o descartar: el catálogo antiguo deja de existir en este celular. */
    fun clearLegacy() {
        prefs.edit().remove(key).apply()
    }
}
