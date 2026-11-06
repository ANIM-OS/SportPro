package dev.dreamteam.sportpro.data.repository

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** US-13. Registros locales pendientes; una integración de servidor debe sincronizarlos. */
data class MatchEventRecord(
    val id: String = UUID.randomUUID().toString(),
    val matchId: String,
    val typeId: String,
    val typeName: String,
    val minute: Int?,
    val team: String?,
    val player: String?,
    val notes: String,
    val extraFields: Map<String, String> = emptyMap(),
    val authorId: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val syncStatus: String = "PENDIENTE"
)

class LiveEventRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("us13_match_events", Context.MODE_PRIVATE)
    private val storageKey = "records_v1"

    fun all(): List<MatchEventRecord> = try {
        val items = JSONArray(prefs.getString(storageKey, "[]"))
        (0 until items.length()).map { index ->
            val obj = items.getJSONObject(index)
            val extras = obj.optJSONObject("extraFields") ?: JSONObject()
            val keys = extras.keys()
            val mapped = mutableMapOf<String, String>()
            while (keys.hasNext()) {
                val k = keys.next()
                mapped[k] = extras.optString(k)
            }
            MatchEventRecord(
                id = obj.getString("id"),
                matchId = obj.getString("matchId"),
                typeId = obj.getString("typeId"),
                typeName = obj.getString("typeName"),
                minute = if (obj.isNull("minute")) null else obj.optInt("minute"),
                team = obj.optString("team").takeIf { it.isNotBlank() },
                player = obj.optString("player").takeIf { it.isNotBlank() },
                notes = obj.optString("notes"),
                extraFields = mapped,
                authorId = obj.optString("authorId"),
                createdAtMillis = obj.optLong("createdAtMillis"),
                syncStatus = obj.optString("syncStatus", "PENDIENTE")
            )
        }
    } catch (_: Exception) { emptyList() }

    fun forMatch(matchId: String): List<MatchEventRecord> = all()
        .filter { it.matchId == matchId }
        .sortedWith(compareBy<MatchEventRecord> { it.minute ?: Int.MAX_VALUE }.thenBy { it.createdAtMillis })

    fun save(event: MatchEventRecord) {
        val existing = all()
        check(existing.none { it.id == event.id }) { "El evento ya existe" }
        val array = JSONArray()
        (existing + event).forEach { record ->
            array.put(JSONObject().apply {
                put("id", record.id)
                put("matchId", record.matchId)
                put("typeId", record.typeId)
                put("typeName", record.typeName)
                put("minute", record.minute ?: JSONObject.NULL)
                put("team", record.team ?: JSONObject.NULL)
                put("player", record.player ?: JSONObject.NULL)
                put("notes", record.notes)
                put("extraFields", JSONObject(record.extraFields))
                put("authorId", record.authorId)
                put("createdAtMillis", record.createdAtMillis)
                put("syncStatus", record.syncStatus)
            })
        }
        check(prefs.edit().putString(storageKey, array.toString()).commit()) { "No se pudo guardar en el dispositivo" }
    }
}
