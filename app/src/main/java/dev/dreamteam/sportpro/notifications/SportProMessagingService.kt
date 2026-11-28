package dev.dreamteam.sportpro.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * US-17: con la app abierta Android no muestra el push por sí solo; se muestra aquí con el texto fijo
 * "Nuevo anuncio en tu equipo. Abre SportPro". Con la app en segundo plano lo muestra el sistema con el
 * texto que se escribió en la consola (por eso debe usarse la plantilla de docs/fcm-envio-manual.md).
 */
class SportProMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        AnnouncementNotifications.show(this)
    }

    override fun onNewToken(token: String) {
        // Los temas se suscriben por token: con un token nuevo se vuelven a suscribir al abrir la app.
        getSharedPreferences("sportpro_team_topics", MODE_PRIVATE).edit().clear().apply()
    }
}
