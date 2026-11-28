package dev.dreamteam.sportpro.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import dev.dreamteam.sportpro.MainActivity
import dev.dreamteam.sportpro.R
import dev.dreamteam.sportpro.data.model.Announcements

/**
 * US-17: suscripción a los temas FCM `team_<teamId>` de los equipos que ve el usuario (dueña, miembro o
 * tutor; el admin no se suscribe). Se guarda en el celular qué temas tiene para dar de baja los que sobran
 * al dejar el equipo y todos al cerrar sesión. Los temas no tienen control de acceso: el push es genérico.
 */
object TeamTopics {
    private const val PREFS = "sportpro_team_topics"
    private const val KEY = "topics"

    private fun stored(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty().toSet()

    private fun save(context: Context, topics: Set<String>) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, topics).apply()

    /** Deja suscritos exactamente los temas de [wanted] (altas y bajas por diferencia). */
    fun sync(context: Context, wanted: Set<String>) {
        val app = context.applicationContext
        val (add, remove) = Announcements.topicDiff(stored(app), wanted)
        val messaging = FirebaseMessaging.getInstance()
        add.forEach { topic -> messaging.subscribeToTopic(topic).addOnSuccessListener { save(app, stored(app) + topic) } }
        remove.forEach { topic -> messaging.unsubscribeFromTopic(topic).addOnSuccessListener { save(app, stored(app) - topic) } }
    }

    /** Al cerrar sesión: baja de todos los temas de este celular. */
    fun unsubscribeAll(context: Context) {
        val app = context.applicationContext
        val messaging = FirebaseMessaging.getInstance()
        stored(app).forEach { topic -> messaging.unsubscribeFromTopic(topic) }
        save(app, emptySet())
    }
}

/** Canal y aviso local de los push de anuncios. */
object AnnouncementNotifications {
    const val CHANNEL_ID = "anuncios"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Anuncios", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Avisos de nuevos anuncios de tus equipos" })
        }
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Muestra SIEMPRE el texto fijo (docs/fcm-envio-manual.md), aunque el push traiga otro. */
    fun show(context: Context) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(Announcements.PUSH_TITLE)
            .setContentText(Announcements.PUSH_TEXT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
        } catch (_: SecurityException) {
            // Permiso retirado justo ahora: no se muestra.
        }
    }
}
