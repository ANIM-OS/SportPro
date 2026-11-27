package dev.dreamteam.sportpro.ui.auth

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** True si hay una red activa con acceso a internet declarado. */
internal fun isOnline(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
