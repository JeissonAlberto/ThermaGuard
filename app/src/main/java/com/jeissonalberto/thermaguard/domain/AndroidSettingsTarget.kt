package com.jeissonalberto.thermaguard.domain

import android.provider.Settings

/** Safe, user-invoked destinations; ThermaGuard never changes these settings itself. */
enum class AndroidSettingsTarget(
    val label: String,
    val action: String,
    val requiresAppPackage: Boolean = false
) {
    APP_DETAILS("FICHA DE LA APP", Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true),
    BATTERY_SAVER("BATERÍA DEL SISTEMA", Settings.ACTION_BATTERY_SAVER_SETTINGS),
    NOTIFICATIONS("NOTIFICACIONES", Settings.ACTION_APP_NOTIFICATION_SETTINGS, true)
}
