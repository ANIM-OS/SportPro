package dev.dreamteam.sportpro.data.repository

import android.content.Context
import org.json.JSONArray

/**
 * Eventos que la versión anterior guardaba solo en el celular (SharedPreferences). Desde la parte 3
 * los eventos viven en Firestore (FirestoreMatchEventStore); esta clase solo los descarta una vez
 * (partidos demo y reales, decisión D4) para mostrar un aviso único.
 */
class LiveEventRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("us13_match_events", Context.MODE_PRIVATE)
    private val storageKey = "records_v1"
    private val auditKey = "event_audit_v1"

    /** Borra los eventos y el historial locales. Devuelve cuántos eventos había (0 = no hay aviso). */
    @Synchronized
    fun discardLocalEvents(): Int {
        if (!prefs.contains(storageKey) && !prefs.contains(auditKey)) return 0
        val count = try { JSONArray(prefs.getString(storageKey, "[]")).length() } catch (_: Exception) { 0 }
        prefs.edit().remove(storageKey).remove(auditKey).apply()
        return count
    }
}
