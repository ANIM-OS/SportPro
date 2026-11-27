package dev.dreamteam.sportpro.data.repository

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Registro local de eventos US-13 + historial de correcciones y anulaciones US-14.
 *  Sin Firebase: la sincronizacion se integrara despues.
 */
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
    val syncStatus: String = "PENDIENTE",
    val voided: Boolean = false,
    val lastModifiedBy: String? = null,
    val lastModifiedAtMillis: Long? = null
)

data class EventAuditEntry(
    val eventId: String,
    val matchId: String,
    val action: String,
    val actorId: String,
    val changedAtMillis: Long,
    val previous: String,
    val current: String
)

class LiveEventRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "us13_match_events", Context.MODE_PRIVATE
    )
    private val storageKey = "records_v1"
    private val auditKey = "event_audit_v1"

    private fun JSONObject.optionalString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    fun all(): List<MatchEventRecord> = try {
        val items = JSONArray(prefs.getString(storageKey, "[]"))
        (0 until items.length()).map { index ->
            val obj = items.getJSONObject(index)
            val extras = obj.optJSONObject("extraFields") ?: JSONObject()
            val extraMap = mutableMapOf<String, String>()
            val keys = extras.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                extraMap[key] = extras.optString(key)
            }
            MatchEventRecord(
                id = obj.getString("id"),
                matchId = obj.getString("matchId"),
                typeId = obj.getString("typeId"),
                typeName = obj.getString("typeName"),
                minute = if (obj.isNull("minute") || !obj.has("minute")) null else obj.optInt("minute"),
                team = obj.optionalString("team"),
                player = obj.optionalString("player"),
                notes = obj.optString("notes"),
                extraFields = extraMap,
                authorId = obj.optString("authorId"),
                createdAtMillis = obj.optLong("createdAtMillis"),
                syncStatus = obj.optString("syncStatus", "PENDIENTE"),
                voided = obj.optBoolean("voided", false),
                lastModifiedBy = obj.optionalString("lastModifiedBy"),
                lastModifiedAtMillis = if (!obj.has("lastModifiedAtMillis") || obj.isNull("lastModifiedAtMillis"))
                    null else obj.optLong("lastModifiedAtMillis")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun forMatch(matchId: String): List<MatchEventRecord> = all()
        .filter { it.matchId == matchId }
        .sortedWith(compareBy<MatchEventRecord> { it.minute ?: Int.MAX_VALUE }
            .thenBy { it.createdAtMillis })

    fun find(matchId: String, eventId: String): MatchEventRecord? =
        all().firstOrNull { it.matchId == matchId && it.id == eventId }

    fun history(matchId: String, eventId: String): List<EventAuditEntry> {
        return try {
            val auditArray = JSONArray(prefs.getString(auditKey, "[]"))
            val entries = mutableListOf<EventAuditEntry>()

            for (index in 0 until auditArray.length()) {
                val obj = auditArray.getJSONObject(index)
                entries.add(
                    EventAuditEntry(
                        eventId = obj.getString("eventId"),
                        matchId = obj.getString("matchId"),
                        action = obj.getString("action"),
                        actorId = obj.getString("actorId"),
                        changedAtMillis = obj.getLong("changedAtMillis"),
                        previous = obj.getString("previous"),
                        current = obj.getString("current")
                    )
                )
            }

            entries.filter { entry ->
                entry.matchId == matchId && entry.eventId == eventId
            }.sortedByDescending { entry -> entry.changedAtMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun toJson(record: MatchEventRecord): JSONObject = JSONObject().apply {
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
        put("voided", record.voided)
        put("lastModifiedBy", record.lastModifiedBy ?: JSONObject.NULL)
        put("lastModifiedAtMillis", record.lastModifiedAtMillis ?: JSONObject.NULL)
    }

    @Synchronized
    fun save(event: MatchEventRecord) {
        val existing = all()
        check(existing.none { it.matchId == event.matchId && it.id == event.id }) {
            "El evento ya existe"
        }
        val array = JSONArray().apply { (existing + event).forEach { put(toJson(it)) } }
        check(prefs.edit().putString(storageKey, array.toString()).commit()) {
            "No se pudo guardar en el dispositivo"
        }
    }

    @Synchronized
    fun correct(matchId: String, eventId: String, changed: MatchEventRecord, actorId: String) {
        require(actorId.isNotBlank() && actorId != "sin_sesion") { "Inicia sesion para corregir eventos" }
        val existing = all()
        val before = existing.firstOrNull { it.matchId == matchId && it.id == eventId }
            ?: error("No se encontro el evento")
        check(!before.voided) { "No se puede corregir un evento anulado" }
        require(changed.matchId == matchId && changed.id == eventId) { "Identidad invalida" }
        val whenChanged = System.currentTimeMillis()
        val after = changed.copy(
            authorId = before.authorId,
            createdAtMillis = before.createdAtMillis,
            voided = false,
            syncStatus = "PENDIENTE",
            lastModifiedBy = actorId,
            lastModifiedAtMillis = whenChanged
        )
        persistChange(
            existing.map { if (it.matchId == matchId && it.id == eventId) after else it },
            EventAuditEntry(eventId, matchId, "CORRECCION", actorId, whenChanged,
                toJson(before).toString(), toJson(after).toString())
        )
    }

    @Synchronized
    fun void(matchId: String, eventId: String, actorId: String) {
        require(actorId.isNotBlank() && actorId != "sin_sesion") { "Inicia sesion para anular eventos" }
        val existing = all()
        val before = existing.firstOrNull { it.matchId == matchId && it.id == eventId }
            ?: error("No se encontro el evento")
        check(!before.voided) { "Este evento ya esta anulado" }
        val whenChanged = System.currentTimeMillis()
        val after = before.copy(
            voided = true,
            syncStatus = "PENDIENTE",
            lastModifiedBy = actorId,
            lastModifiedAtMillis = whenChanged
        )
        persistChange(
            existing.map { if (it.matchId == matchId && it.id == eventId) after else it },
            EventAuditEntry(eventId, matchId, "ANULACION", actorId, whenChanged,
                toJson(before).toString(), toJson(after).toString())
        )
    }

    private fun persistChange(records: List<MatchEventRecord>, entry: EventAuditEntry) {
        val audit = JSONArray(prefs.getString(auditKey, "[]"))
        audit.put(JSONObject().apply {
            put("eventId", entry.eventId)
            put("matchId", entry.matchId)
            put("action", entry.action)
            put("actorId", entry.actorId)
            put("changedAtMillis", entry.changedAtMillis)
            put("previous", entry.previous)
            put("current", entry.current)
        })
        val updated = JSONArray().apply { records.forEach { put(toJson(it)) } }
        check(prefs.edit()
            .putString(storageKey, updated.toString())
            .putString(auditKey, audit.toString())
            .commit()) { "No fue posible conservar la correccion" }
    }
}
