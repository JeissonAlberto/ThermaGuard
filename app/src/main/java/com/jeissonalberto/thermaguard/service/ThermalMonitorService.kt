package com.jeissonalberto.thermaguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jeissonalberto.thermaguard.MainActivity
import com.jeissonalberto.thermaguard.data.SensorRepository
import com.jeissonalberto.thermaguard.data.ThermalDatabase
import com.jeissonalberto.thermaguard.data.ThermalSnapshot
import com.jeissonalberto.thermaguard.data.toThermalLevel
import kotlinx.coroutines.*

class ThermalMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var sensorRepository: SensorRepository
    private lateinit var db: ThermalDatabase

    companion object {
        const val CHANNEL_ID = "thermaguard_monitor"
        const val NOTIF_ID = 1001
        const val ALERT_NOTIF_ID = 1002
        const val ACTION_STOP = "STOP_MONITOR"
        const val MONITOR_INTERVAL_MS = 30_000L
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        sensorRepository = SensorRepository(this)
        db = ThermalDatabase.getInstance(this)
        createNotificationChannel()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Notificacion inicial en barra de notificaciones
        val notification = buildNotification(
            emoji = "🌡️",
            title = "ThermaGuard activo",
            text = "Iniciando monitoreo de temperatura..."
        )

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            }
            else -> startForeground(NOTIF_ID, notification)
        }

        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val snapshot: ThermalSnapshot = sensorRepository.readSnapshot()
                    db.thermalDao().insert(snapshot)
                    updateNotification(snapshot)

                    // Alerta separada si temperatura critica
                    if (snapshot.batteryTemp >= 45f) {
                        sendThermalAlert(snapshot)
                    }

                    // Limpiar historial mayor a 7 dias
                    val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                    db.thermalDao().deleteOlderThan(weekAgo)
                } catch (e: Exception) { }
                delay(MONITOR_INTERVAL_MS)
            }
        }
    }

    private fun updateNotification(snapshot: ThermalSnapshot) {
        val level = snapshot.batteryTemp.toThermalLevel()
        val charging = if (snapshot.isCharging) "⚡ Cargando" else "🔋 ${snapshot.batteryLevel}%"
        val cpu = "CPU ${snapshot.cpuUsage.toInt()}%"
        val wifi = if (snapshot.wifiActive) "📶" else ""

        val notif = buildNotification(
            emoji = level.emoji,
            title = "ThermaGuard — ${snapshot.batteryTemp}°C ${level.label}",
            text = "$charging  |  $cpu  |  $wifi",
            subText = if (snapshot.topApp.isNotEmpty()) "App: ${snapshot.topApp}" else null
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, notif)
    }

    private fun sendThermalAlert(snapshot: ThermalSnapshot) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val alert = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔴 ThermaGuard — Temperatura critica!")
            .setContentText("${snapshot.batteryTemp}°C — Cierra apps pesadas ahora")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Tu dispositivo esta a ${snapshot.batteryTemp}°C. Cierra apps pesadas, baja el brillo y desconecta el cargador si es posible. CPU al ${snapshot.cpuUsage.toInt()}%."))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(ALERT_NOTIF_ID, alert)
    }

    private fun buildNotification(
        emoji: String,
        title: String,
        text: String,
        subText: String? = null
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ThermalMonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .apply { subText?.let { setSubText(it) } }
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Detener", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Monitor Termico ThermaGuard",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Temperatura del dispositivo en tiempo real"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
