package dev.dreamteam.sportpro.data.repository

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/** US-12: catálogo local. No sincroniza con Firestore todavía. */
data class CatalogEventType(
    val id: String,
    val name: String,
    val description: String = "",
    val requiredFields: List<String> = listOf("minuto"),
    val active: Boolean = true,
    val base: Boolean = false
)

class EventCatalogRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("us12_event_catalog", Context.MODE_PRIVATE)
    private val key = "event_types_v1"

    fun load(): List<CatalogEventType> {
        val raw = prefs.getString(key, null) ?: return defaults()
        return try {
            val array = JSONArray(raw)
            val stored = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                val fields = item.optJSONArray("requiredFields") ?: JSONArray()
                CatalogEventType(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    description = item.optString("description"),
                    requiredFields = (0 until fields.length()).map { fields.getString(it) },
                    active = item.optBoolean("active", true),
                    base = item.optBoolean("base", false)
                )
            }
            // If app adds base types in a later version, keep them available.
            val savedIds = stored.map { it.id }.toSet()
            defaults().filterNot { it.id in savedIds } + stored
        } catch (_: Exception) {
            defaults()
        }
    }

    fun save(types: List<CatalogEventType>) {
        val array = JSONArray()
        types.forEach { type ->
            array.put(JSONObject().apply {
                put("id", type.id)
                put("name", type.name)
                put("description", type.description)
                put("active", type.active)
                put("base", type.base)
                put("requiredFields", JSONArray(type.requiredFields))
            })
        }
        check(prefs.edit().putString(key, array.toString()).commit()) { "No se pudo guardar el catálogo" }
    }

    fun upsert(type: CatalogEventType) {
        val current = load()
        save(if (current.any { it.id == type.id }) current.map { if (it.id == type.id) type else it } else current + type)
    }

    fun setActive(typeId: String, active: Boolean) {
        save(load().map { if (it.id == typeId) it.copy(active = active) else it })
    }

    /** Utilizar esta función al construir las opciones de registro en US-13. */
    fun activeTypes(): List<CatalogEventType> = load().filter { it.active }

    companion object {
        fun slug(value: String): String = Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

        fun defaults(): List<CatalogEventType> = listOf(
            CatalogEventType("gol", "Gol", requiredFields = listOf("jugadorId", "minuto"), base = true),
            CatalogEventType("falta", "Falta", requiredFields = listOf("jugadorId", "minuto"), base = true),
            CatalogEventType("penal", "Penal", requiredFields = listOf("jugadorId", "minuto", "favor"), base = true),
            CatalogEventType("tarjeta", "Tarjeta", requiredFields = listOf("jugadorId", "minuto", "color"), base = true),
            CatalogEventType("fuera_de_juego", "Fuera de juego", base = true),
            CatalogEventType("cambio", "Cambio", requiredFields = listOf("jugadorSaleId", "jugadorEntraId", "minuto"), base = true),
            CatalogEventType("saque_lateral", "Saque lateral", base = true),
            CatalogEventType("saque_de_meta", "Saque de meta", base = true),
            CatalogEventType("tiro_de_esquina", "Tiro de esquina", base = true)
        )
    }
}
