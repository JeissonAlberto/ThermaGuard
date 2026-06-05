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

        val notification = buildNotification("Monitoreando temperatura...")

        // dataSync no requiere permisos especiales en ninguna version de Android
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
                    if (snapshot.batteryTemp >= 45f) sendThermalAlert(snapshot)
                    val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                    db.thermalDao().deleteOlderThan(weekAgo)
                } catch (e: Exception) { }
                delay(MONITOR_INTERVAL_MS)
            }
        }
    }

    private fun updateNotification(snapshot: ThermalSnapshot) {
        val level = snapshot.batteryTemp.toThermalLevel()
        val text = "${level.emoji} ${snapshot.batteryTemp}C | CPU ${snapshot.cpuUsage.toInt()}% | ${if (snapshot.isCharging) "Cargando" else "Bat ${snapshot.batteryLevel}%"}"
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification(text))
    }

    private fun sendThermalAlert(snapshot: ThermalSnapshot) {
        val alert = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ThermaGuard - Temperatura critica")
            .setContentText("Bateria: ${snapshot.batteryTemp}C - Cierra apps pesadas")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID + 1, alert)
    }

    private fun buildNotification(text: String): Notification {
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
            .setContentTitle("ThermaGuard activo")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Detener", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Monitor Termico", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitoreo continuo de temperatura"
            setShowBadge(false)
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
