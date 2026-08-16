package com.jeissonalberto.thermaguard.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jeissonalberto.thermaguard.MainActivity
import com.jeissonalberto.thermaguard.domain.isSystemThermalRisk

/** Posts a thermal alert only when the caller has observed a real state transition. */
object ThermalAlertNotifier {
    private const val CHANNEL_ID = "therma_alerts"
    private const val NOTIFICATION_ID = 9902

    fun notify(
        context: Context,
        status: String,
        temperature: Float?,
        systemStatus: String?
    ): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Alertas térmicas",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Avisos cuando la batería o el estado térmico del sistema indican riesgo."
                    }
                )
            }
            val batteryLabel = temperature?.let { "%.1f°C".format(it) } ?: "no disponible"
            val body = if (isSystemThermalRisk(systemStatus)) {
                "Android reportó un estado térmico $systemStatus; la batería marca $batteryLabel. " +
                    "Reduce la carga y comprueba la ventilación del dispositivo."
            } else {
                "Android reportó una temperatura real de batería de $batteryLabel. " +
                    "Reduce la carga y comprueba la ventilación del dispositivo."
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(if (status == "CRITICAL") "Alerta térmica crítica" else "Alerta térmica")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }
}
