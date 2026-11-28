package dev.dreamteam.sportpro.data.repository

import android.content.Context
import dev.dreamteam.sportpro.data.model.EventAuditEntry
import dev.dreamteam.sportpro.data.model.MatchEventRecord
import dev.dreamteam.sportpro.data.model.MatchEvents
import org.json.JSONArray
import org.json.JSONObject

/** Suscripción a cambios; se cancela con [remove]. */
fun interface EventSubscription { fun remove() }

/**
 * US-13/14: almacén de eventos de un partido real. Hoy lo implementa [LiveEventRepository] en el
 * celular; en la parte 3 se añadirá una implementación con Firestore sin tocar las pantallas.
 */
interface MatchEventStore {
    fun observe(matchId: String, onChange: (List<MatchEventRecord>) -> Unit): EventSubscription
    fun find(matchId: String, eventId: String): MatchEventRecord?
    fun history(matchId: String, eventId: String): List<EventAuditEntry>
    fun add(event: MatchEventRecord)
    fun correct(matchId: String, eventId: String, changed: MatchEventRecord, actorId: String)
    fun void(matchId: String, eventId: String, actorId: String)
}

/** Implementación local (SharedPreferences) con aviso a los observadores en cada cambio. */
class LiveEventRepository(context: Context) : MatchEventStore {
    private val prefs = context.applicationContext.getSharedPreferences("us13_match_events", Context.MODE_PRIVATE)
    private val storageKey = "records_v1"
    private val auditKey = "event_audit_v1"

    private fun JSONObject.optionalString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun all(): List<MatchEventRecord> = try {
        val items = JSONArray(prefs.getString(storageKey, "[]"))
        (0 until items.length()).map { index ->
            val obj = items.getJSONObject(index)
            val extras = obj.optJSONObject("extraFields") ?: JSONObject()
            val extraMap = mutableMapOf<String, String>()
            extras.keys().forEach { key -> extraMap[key] = extras.optString(key) }
            MatchEventRecord(
                id = obj.getString("id"),
                matchId = obj.getString("matchId"),
                teamId = obj.optString("teamId"),
                typeId = obj.getString("typeId"),
                typeName = obj.getString("typeName"),
                minute = if (obj.isNull("minute") || !obj.has("minute")) null else obj.optInt("minute"),
                team = obj.optionalString("team"),
                playerId = obj.optionalString("playerId"),
                notes = obj.optString("notes"),
                extraFields = extraMap,
                authorId = obj.optString("authorId"),
                createdAtMillis = obj.optLong("createdAtMillis"),
                voided = obj.optBoolean("voided", false),
                lastModifiedBy = obj.optionalString("lastModifiedBy"),
                lastModifiedAtMillis = if (!obj.has("lastModifiedAtMillis") || obj.isNull("lastModifiedAtMillis"))
                    null else obj.optLong("lastModifiedAtMillis"),
                version = obj.optInt("version", 1)
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun audit(): List<EventAuditEntry> = try {
        val array = JSONArray(prefs.getString(auditKey, "[]"))
        (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            EventAuditEntry(obj.getString("eventId"), obj.getString("matchId"), obj.getString("action"), obj.getString("actorId"),
                obj.getLong("changedAtMillis"), obj.getString("previous"), obj.getString("current"))
        }
    } catch (_: Exception) {
        emptyList()
    }

    override fun observe(matchId: String, onChange: (List<MatchEventRecord>) -> Unit): EventSubscription {
        val listener = { onChange(forMatch(matchId)) }
        synchronized(observers) { observers += listener }
        listener()
        return EventSubscription { synchronized(observers) { observers -= listener } }
    }

    fun forMatch(matchId: String): List<MatchEventRecord> = MatchEvents.chronology(all().filter { it.matchId == matchId })

    override fun find(matchId: String, eventId: String): MatchEventRecord? =
        all().firstOrNull { it.matchId == matchId && it.id == eventId }

    override fun history(matchId: String, eventId: String): List<EventAuditEntry> =
        audit().filter { it.matchId == matchId && it.eventId == eventId }.sortedByDescending { it.changedAtMillis }

    private fun toJson(record: MatchEventRecord): JSONObject = JSONObject().apply {
        put("id", record.id)
        put("matchId", record.matchId)
        put("teamId", record.teamId)
        put("typeId", record.typeId)
        put("typeName", record.typeName)
        put("minute", record.minute ?: JSONObject.NULL)
        put("team", record.team ?: JSONObject.NULL)
        put("playerId", record.playerId ?: JSONObject.NULL)
        put("notes", record.notes)
        put("extraFields", JSONObject(record.extraFields))
        put("authorId", record.authorId)
        put("createdAtMillis", record.createdAtMillis)
        put("voided", record.voided)
        put("lastModifiedBy", record.lastModifiedBy ?: JSONObject.NULL)
        put("lastModifiedAtMillis", record.lastModifiedAtMillis ?: JSONObject.NULL)
        put("version", record.version)
    }

    private fun auditJson(entry: EventAuditEntry) = JSONObject().apply {
        put("eventId", entry.eventId); put("matchId", entry.matchId); put("action", entry.action)
        put("actorId", entry.actorId); put("changedAtMillis", entry.changedAtMillis)
        put("previous", entry.previous); put("current", entry.current)
    }

    @Synchronized
    override fun add(event: MatchEventRecord) {
        val existing = all()
        check(existing.none { it.matchId == event.matchId && it.id == event.id }) { "El evento ya existe" }
        val array = JSONArray().apply { (existing + event).forEach { put(toJson(it)) } }
        check(prefs.edit().putString(storageKey, array.toString()).commit()) { "No se pudo guardar en el dispositivo" }
        notifyObservers()
    }

    @Synchronized
    override fun correct(matchId: String, eventId: String, changed: MatchEventRecord, actorId: String) {
        require(actorId.isNotBlank()) { "Inicia sesión para corregir eventos" }
        val existing = all()
        val before = existing.firstOrNull { it.matchId == matchId && it.id == eventId } ?: error("No se encontró el evento")
        check(!before.voided) { "No se puede corregir un evento anulado" }
        require(changed.matchId == matchId && changed.id == eventId) { "Identidad inválida" }
        val now = System.currentTimeMillis()
        val after = changed.copy(authorId = before.authorId, createdAtMillis = before.createdAtMillis, voided = false,
            lastModifiedBy = actorId, lastModifiedAtMillis = now, version = before.version + 1)
        persistChange(existing.map { if (it.matchId == matchId && it.id == eventId) after else it },
            EventAuditEntry(eventId, matchId, "CORRECCION", actorId, now, toJson(before).toString(), toJson(after).toString()))
    }

    @Synchronized
    override fun void(matchId: String, eventId: String, actorId: String) {
        require(actorId.isNotBlank()) { "Inicia sesión para anular eventos" }
        val existing = all()
        val before = existing.firstOrNull { it.matchId == matchId && it.id == eventId } ?: error("No se encontró el evento")
        check(!before.voided) { "Este evento ya está anulado" }
        val now = System.currentTimeMillis()
        val after = before.copy(voided = true, lastModifiedBy = actorId, lastModifiedAtMillis = now, version = before.version + 1)
        persistChange(existing.map { if (it.matchId == matchId && it.id == eventId) after else it },
            EventAuditEntry(eventId, matchId, "ANULACION", actorId, now, toJson(before).toString(), toJson(after).toString()))
    }

    /**
     * Borra los eventos y el historial de los partidos de demostración (`demo-*`), que no existen en
     * matchFixtures y no se pueden asociar a un partido real. Devuelve cuántos eventos se borraron.
     */
    @Synchronized
    fun removeDemoEvents(): Int {
        val records = all()
        val demo = records.count { MatchEvents.isDemoMatch(it.matchId) }
        val demoAudit = audit().count { MatchEvents.isDemoMatch(it.matchId) }
        if (demo == 0 && demoAudit == 0) return 0
        val keep = JSONArray().apply { records.filterNot { MatchEvents.isDemoMatch(it.matchId) }.forEach { put(toJson(it)) } }
        val keepAudit = JSONArray().apply { audit().filterNot { MatchEvents.isDemoMatch(it.matchId) }.forEach { put(auditJson(it)) } }
        prefs.edit().putString(storageKey, keep.toString()).putString(auditKey, keepAudit.toString()).apply()
        notifyObservers()
        return demo
    }

    private fun persistChange(records: List<MatchEventRecord>, entry: EventAuditEntry) {
        val updatedAudit = JSONArray(prefs.getString(auditKey, "[]")).apply { put(auditJson(entry)) }
        val updated = JSONArray().apply { records.forEach { put(toJson(it)) } }
        check(prefs.edit().putString(storageKey, updated.toString()).putString(auditKey, updatedAudit.toString()).commit()) {
            "No fue posible conservar la corrección"
        }
        notifyObservers()
    }

    private fun notifyObservers() {
        val current = synchronized(observers) { observers.toList() }
        current.forEach { it() }
    }

    companion object {
        /** Observadores compartidos por todas las instancias (mismo archivo de preferencias). */
        private val observers = mutableListOf<() -> Unit>()
    }
}
