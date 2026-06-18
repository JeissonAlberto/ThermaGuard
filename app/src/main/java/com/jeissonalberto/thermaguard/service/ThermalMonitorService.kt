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
import kotlinx.coroutines.isActive

import android.content.BroadcastReceiver
import android.content.IntentFilter
class ThermalMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var sensorRepo: SensorRepository
    private lateinit var learningEngine: ThermalLearningEngine
    private lateinit var optRepo: OptimizationRepository
    private lateinit var db: ThermalDatabase
    private var nm: NotificationManager? = null

    companion object {
        const val CHANNEL_ID      = NotificationEngine.CHANNEL_MONITOR   // "tg_monitor"
        const val CHANNEL_ALERT   = NotificationEngine.CHANNEL_ALERT     // "tg_alert_critical"
        const val NOTIF_ID        = 1001
        const val ALERT_NOTIF_ID  = 1002
        const val ACTION_STOP     = "STOP_MONITOR"
        const val INTERVAL_MS     = 60_000L  // Reducido — solo actualiza notif, VM lee los datos
        const val INTERVAL_MS_ACTIVE = 20_000L
        var isRunning = false

        // Estado compartido para que el VM pueda leer sin duplicar lecturas
        @Volatile var lastRiskScore: Int = 0
        @Volatile var lastLevel: ThermalLevel = ThermalLevel.NORMAL
        // Snapshot publicado por el ViewModel — el Service lo consume en lugar de leer de nuevo
        @Volatile var lastSnapshot: ThermalSnapshot? = null
        @Volatile var lastProfile: LearnedProfile? = null
    }

    override fun onCreate() {
        super.onCreate()
        sensorRepo     = SensorRepository(this)
        learningEngine = ThermalLearningEngine(this)
        optRepo        = OptimizationRepository(this)
        db             = ThermalDatabase.getInstance(this)
        nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        createChannels()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Guard: si onCreate no completó por algún motivo, reinicializar
        if (!::sensorRepo.isInitialized) {
            try {
                sensorRepo     = SensorRepository(this)
                learningEngine = ThermalLearningEngine(this)
                optRepo        = OptimizationRepository(this)
                db             = ThermalDatabase.getInstance(this)
            } catch (e: Exception) {
                android.util.Log.e("ThermaGuard", "Service reinit failed: ${e.message}")
                return START_NOT_STICKY
            }
        }
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }

        val notif = buildNotification("Iniciando…", ThermalLevel.NORMAL, 0)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID, notif,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (e: Exception) {
            android.util.Log.w("ThermaGuard", "startForeground: ${e.message}")
        }

        val screenFilter = android.content.IntentFilter().also {
            it.addAction(android.content.Intent.ACTION_SCREEN_OFF)
            it.addAction(android.content.Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, screenFilter)
        serviceScope.launch {
            try { monitorLoop() }
            catch (e: Exception) {
                android.util.Log.e("ThermaGuard", "monitorLoop crash: ${e.message}", e)
            }
        }
        return START_STICKY
    }

    private suspend fun monitorLoop() {
        var wasHot        = false
        var heatStartTime = 0L
        var lastDbWrite   = 0L   // solo escribir DB cada 5 min

        while (true) {
            try {
                // Pantalla apagada → reutilizar último snapshot si disponible
                val snap = if (screenOff && lastSnapshot != null) lastSnapshot ?: ThermalSnapshot()
                           else sensorRepo.readSnapshot()
                // learningEngine.learn solo si temperatura cambió >0.5°C o cada 10 ciclos
                val mainT = if (snap.cpuTemp > 20f) snap.cpuTemp
                            else if (snap.modemTemp > 20f) snap.modemTemp
                            else snap.batteryTemp
                val tempChanged = kotlin.math.abs(mainT - lastKnownTemp) > 0.5f
                if (tempChanged || learnCycle % 10 == 0) {
                    lastProfile = learningEngine.learn(snap)
                    lastKnownTemp = mainT
                }
                learnCycle++
                val profile = lastProfile ?: learningEngine.learn(snap)

                // Temperatura principal: CPU si disponible, si no modem, si no batería
                val mainTemp = when {
                    snap.cpuTemp   > 20f -> snap.cpuTemp
                    snap.modemTemp > 20f -> snap.modemTemp
                    else                  -> snap.batteryTemp
                }

                lastRiskScore = profile.riskScore
                lastLevel     = mainTemp.toThermalLevel()

                // ── Tracking cooldown ────────────────────────────────────
                val isHot = mainTemp >= profile.dynamicThreshold
                if (isHot && !wasHot) {
                    heatStartTime = System.currentTimeMillis()
                } else if (!isHot && wasHot && heatStartTime > 0L) {
                    val mins = (System.currentTimeMillis() - heatStartTime) / 60_000f
                    if (mins in 0.5f..60f) learningEngine.recordCooldown(mins)
                }
                wasHot = isHot

                // ── Notificación ─────────────────────────────────────────
                updateNotification(snap, profile)

                // ── Alerta crítica ───────────────────────────────────────
                if (lastLevel == ThermalLevel.CRITICAL || lastLevel == ThermalLevel.EMERGENCY) {
                    sendAlert(snap, profile)
                }

                // ── Modo Bestia ──────────────────────────────────────────
                if (lastGamerMode && mainTemp >= 40f) triggerGamerCooling(snap)

                // ── Escribir DB solo cada 5 minutos (no cada ciclo) ──────
                val now = System.currentTimeMillis()
                if (now - lastDbWrite >= 5 * 60_000L) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        db.thermalDao().insert(snap)
                        val sevenDaysAgo = now - 7 * 24 * 60 * 60 * 1000L
                        db.thermalDao().deleteOlderThan(sevenDaysAgo)
                    }
                    lastDbWrite = now
                }

            } catch (_: Exception) { }

            // ── INTERVALO ADAPTATIVO ─────────────────────────────────────
            // Normal/Aprendizaje → 60s (casi sin consumo)
            // Tibio            → 30s
            // Caliente/Crítico → 20s
            // Emergencia       → 10s
            val interval = when (lastLevel) {
                ThermalLevel.EMERGENCY, ThermalLevel.CRITICAL -> 10_000L
                ThermalLevel.HOT                              -> 20_000L
                ThermalLevel.WARM                             -> 30_000L
                else                                          -> 60_000L
            }
            delay(if (screenOff) maxOf(interval, 120_000L) else interval)
        }
    }


    private fun updateNotification(snap: ThermalSnapshot, profile: LearnedProfile) {
        val level = (if (snap.cpuTemp > 20f) snap.cpuTemp else snap.batteryTemp).toThermalLevel()
        val riskEmoji = when {
            profile.riskScore >= 75 -> "🔴"
            profile.riskScore >= 50 -> "🟠"
            profile.riskScore >= 25 -> "🟡"
            else                    -> "🟢"
        }
        val mainT = if (snap.cpuTemp > 20f) snap.cpuTemp else snap.batteryTemp
        val text = "$riskEmoji ${mainT.toInt()}°C · CPU ${snap.cpuUsage.toInt()}% · Riesgo ${profile.riskScore}/100"
        val title = when (level) {
            ThermalLevel.NORMAL    -> "Estado normal"
            ThermalLevel.WARM      -> "Dispositivo tibio"
            ThermalLevel.HOT       -> "⚠️ Calentando"
            ThermalLevel.CRITICAL  -> "🔥 Temperatura crítica"
            ThermalLevel.EMERGENCY -> "🚨 Emergencia térmica"
        }
        nm?.notify(NOTIF_ID, buildNotification(text, level, profile.riskScore, title))
    }

    private fun sendAlert(snap: ThermalSnapshot, profile: LearnedProfile) {
        val mainTemp = when {
            snap.cpuTemp   > 20f -> snap.cpuTemp
            snap.modemTemp > 20f -> snap.modemTemp
            else                  -> snap.batteryTemp
        }
        NotificationEngine.sendThermalAlert(
            context     = this,
            level       = lastLevel,
            temperature = mainTemp,
            riskScore   = profile.riskScore,
            details     = "Motor automático activo"
        )
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
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_compass, "Detener", stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannels() {
        NotificationEngine.createChannels(this)
    }

    override fun onDestroy() {
        isRunning = false
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    // ── MODO GAMER ──────────────────────────────────────────────────────────
    var lastGamerMode:  Boolean = false
    private var lastKnownTemp: Float = 0f
    private var learnCycle:    Int   = 0
    @Volatile private var screenOff: Boolean = false
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
            when (i?.action) {
                android.content.Intent.ACTION_SCREEN_OFF -> screenOff = true
                android.content.Intent.ACTION_SCREEN_ON  -> { screenOff = false; lastSnapshot = null }
            }
        }
    }

    private fun triggerGamerCooling(snap: ThermalSnapshot) {
        val mainTemp = if (snap.cpuTemp > 20f) snap.cpuTemp else snap.batteryTemp

        // Ejecutar todas las intervenciones del motor de física
        com.jeissonalberto.thermaguard.domain.BeastCoolingEngine.executeAll(
            this, mainTemp, snap
        )

        // Notificación con intervenciones activas
        val interventions = com.jeissonalberto.thermaguard.domain.BeastCoolingEngine
            .getActiveInterventions(mainTemp, snap)
        val summary = interventions.take(2).joinToString(" · ")

        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val alertNotif = android.app.Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚡ Modo Bestia — ${mainTemp.toInt()}°C · ${interventions.size} intervenciones")
            .setContentText(summary)
            .setOnlyAlertOnce(true)
            .setStyle(android.app.Notification.BigTextStyle().bigText(
                interventions.joinToString("\n")
            ))
            .build()
        nm?.notify(NOTIF_ID + 1, alertNotif)
    }

}
