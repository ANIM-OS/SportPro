package dev.dreamteam.sportpro

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

/** Only the separately installed local build can connect to test services. */
object LocalFirebase {
    private var configured = false
    @Synchronized fun configure() {
        if (!BuildConfig.LOCAL_FIREBASE || configured) return
        FirebaseAuth.getInstance().useEmulator("127.0.0.1", 9099)
        FirebaseFirestore.getInstance().useEmulator("127.0.0.1", 8080)
        FirebaseStorage.getInstance().useEmulator("127.0.0.1", 9199)
        configured = true
    }
}
