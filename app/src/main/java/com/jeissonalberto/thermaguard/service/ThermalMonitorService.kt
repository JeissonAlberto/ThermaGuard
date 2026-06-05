package com.jeissonalberto.thermaguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jeissonalberto.thermaguard.MainActivity
import com.jeissonalberto.thermaguard.R
import com.jeissonalberto.thermaguard.data.*
import kotlinx.coroutines.*

class ThermalMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var sensorRepo: SensorRepository
    private lateinit var learningEngine: ThermalLearningEngine
    private lateinit var optRepo: OptimizationRepository
    private lateinit var db: ThermalDatabase
    private lateinit var nm: NotificationManager

    companion object {
        const val CHANNEL_ID      = "thermaguard_monitor"
        const val CHANNEL_ALERT   = "thermaguard_alerts"
        const val NOTIF_ID        = 1001
        const val ALERT_NOTIF_ID  = 1002
        const val ACTION_STOP     = "STOP_MONITOR"
        const val INTERVAL_MS     = 30_000L
        var isRunning = false

        // Estado compartido para que el VM pueda leer sin duplicar lecturas
        @Volatile var lastRiskScore: Int = 0
        @Volatile var lastLevel: ThermalLevel = ThermalLevel.NORMAL
    }

    override fun onCreate() {
        super.onCreate()
        sensorRepo     = SensorRepository(this)
        learningEngine = ThermalLearningEngine(this)
        optRepo        = OptimizationRepository(this)
        db             = ThermalDatabase.getInstance(this)
        nm             = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createChannels()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }

        val notif = buildNotification("Iniciando…", ThermalLevel.NORMAL, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        serviceScope.launch { monitorLoop() }
        return START_STICKY
    }

    private suspend fun monitorLoop() {
        var wasHot = false
        var heatStartTime = 0L

        while (isActive) {
            try {
                val snap    = sensorRepo.readSnapshot()
                val profile = learningEngine.learn(snap)

                // Guardar en DB
                db.thermalDao().insert(snap)

                // Actualizar estado compartido
                lastRiskScore = profile.riskScore
                lastLevel     = snap.batteryTemp.toThermalLevel()

                // Tracking de cooldown: mide tiempo de enfriamiento
                val isHot = snap.batteryTemp >= profile.dynamicThreshold
                if (isHot && !wasHot) {
                    heatStartTime = System.currentTimeMillis()
                } else if (!isHot && wasHot && heatStartTime > 0L) {
                    val mins = (System.currentTimeMillis() - heatStartTime) / 60_000f
                    if (mins in 0.5f..60f) learningEngine.recordCooldown(mins)
                }
                wasHot = isHot

                // Actualizar notificación persistente con info real
                updateNotification(snap, profile)

                // Alerta si temperatura crítica
                if (lastLevel == ThermalLevel.CRITICAL || lastLevel == ThermalLevel.EMERGENCY) {
                    sendAlert(snap, profile)
                }

                // Limpiar historial antiguo (>7 días)
                val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
                db.thermalDao().deleteOlderThan(sevenDaysAgo)

            } catch (_: Exception) { }

            delay(INTERVAL_MS)
        }
    }

    private fun updateNotification(snap: ThermalSnapshot, profile: LearnedProfile) {
        val level = snap.batteryTemp.toThermalLevel()
        val riskEmoji = when {
            profile.riskScore >= 75 -> "🔴"
            profile.riskScore >= 50 -> "🟠"
            profile.riskScore >= 25 -> "🟡"
            else                    -> "🟢"
        }
        val text = "$riskEmoji ${snap.batteryTemp}°C · CPU ${snap.cpuUsage.toInt()}% · Riesgo ${profile.riskScore}/100"
        val title = when (level) {
            ThermalLevel.NORMAL    -> "Estado normal"
            ThermalLevel.WARM      -> "Dispositivo tibio"
            ThermalLevel.HOT       -> "⚠️ Calentando"
            ThermalLevel.CRITICAL  -> "🔥 Temperatura crítica"
            ThermalLevel.EMERGENCY -> "🚨 Emergencia térmica"
        }
        nm.notify(NOTIF_ID, buildNotification(text, level, profile.riskScore, title))
    }

    private fun sendAlert(snap: ThermalSnapshot, profile: LearnedProfile) {
        val notif = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_launcher_round)
            .setContentTitle("🔥 Temperatura crítica — ${snap.batteryTemp}°C")
            .setContentText("Risk score ${profile.riskScore}/100 · El motor está actuando automáticamente")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(ALERT_NOTIF_ID, notif)
    }

    private fun buildNotification(
        text: String,
        level: ThermalLevel,
        riskScore: Int,
        title: String = "ThermaGuard activo"
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, ThermalMonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_round, "Detener", stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannels() {
        val monitorCh = NotificationChannel(CHANNEL_ID, "Monitor activo", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Muestra el estado térmico en tiempo real"
            setShowBadge(false)
        }
        val alertCh = NotificationChannel(CHANNEL_ALERT, "Alertas térmicas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alertas cuando la temperatura es crítica"
        }
        nm.createNotificationChannel(monitorCh)
        nm.createNotificationChannel(alertCh)
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
