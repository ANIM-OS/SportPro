package dev.dreamteam.sportpro

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

class SportProApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.FIREBASE_SANDBOX) {
            FirebaseApp.initializeApp(
                this,
                FirebaseOptions.Builder()
                    .setProjectId("demo-sportpro")
                    .setApplicationId("1:1234567890:android:0123456789abcdef")
                    .setApiKey("sandbox-api-key")
                    .setStorageBucket("demo-sportpro.appspot.com")
                    .build()
            )
            FirebaseAuth.getInstance().useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 9099)
        }
        val firestore = FirebaseFirestore.getInstance()
        firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
        if (BuildConfig.FIREBASE_SANDBOX) {
            firestore.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 8080)
        }
    }
}
