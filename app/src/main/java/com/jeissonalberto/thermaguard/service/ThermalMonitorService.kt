package com.jeissonalberto.thermaguard.service

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.jeissonalberto.thermaguard.MainActivity
import com.jeissonalberto.thermaguard.R
import com.jeissonalberto.thermaguard.data.*
import kotlinx.coroutines.*

/**
 * ThermalMonitorService v5 — Mínimo consumo, máxima acción
 *
 * Principios de diseño:
 * 1. Intervalo ADAPTATIVO: 60s en reposo, 20s cuando hay calor, 10s en crítico
 * 2. Lecturas LAZY: solo lee lo que necesita según el nivel térmico actual
 * 3. SharedPreferences: escritura en batch, solo cuando hay cambio real
 * 4. Optimización AGRESIVA: actúa inmediatamente, no solo reporta
 * 5. Motor en memoria: no toca disco en cada ciclo salvo que sea necesario
 */
class ThermalMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var sensorRepo: SensorRepository
    private lateinit var learningEngine: ThermalLearningEngine
    private lateinit var optRepo: OptimizationRepository
    private lateinit var db: ThermalDatabase
    private lateinit var nm: NotificationManager

    companion object {
        const val CHANNEL_ID     = "thermaguard_monitor"
        const val CHANNEL_ALERT  = "thermaguard_alerts"
        const val NOTIF_ID       = 1001
        const val ALERT_NOTIF_ID = 1002
        const val ACTION_STOP    = "STOP_MONITOR"

        // Intervalos adaptativos
        const val INTERVAL_NORMAL   = 60_000L  // 60s — reposo
        const val INTERVAL_WARM     = 30_000L  // 30s — tibio
        const val INTERVAL_HOT      = 15_000L  // 15s — caliente
        const val INTERVAL_CRITICAL = 8_000L   // 8s  — crítico

        @Volatile var isRunning   = false
        @Volatile var lastRiskScore: Int = 0
        @Volatile var lastLevel: ThermalLevel = ThermalLevel.NORMAL
        @Volatile var lastSnap: ThermalSnapshot? = null
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
        startForeground(NOTIF_ID, buildNotification("Iniciando…", ThermalLevel.NORMAL, 0))
        serviceScope.launch { monitorLoop() }
        return START_STICKY
    }

    private suspend fun monitorLoop() {
        var lastSaveTime = 0L
        var lastAlertTime = 0L
        var lastOptTime   = 0L
        var cycleCount    = 0

        while (true) {
            try {
                val now = System.currentTimeMillis()

                // ── Lectura mínima según nivel actual ──────────────────
                val snap = sensorRepo.readSnapshot(lightMode = lastLevel == ThermalLevel.NORMAL)
                lastSnap = snap
                val level = snap.batteryTemp.toThermalLevel()
                lastLevel = level

                // ── Motor aprende (en memoria, sin disco) ──────────────
                val profile = learningEngine.learnFast(snap)
                lastRiskScore = profile.riskScore

                // ── ACCIÓN AGRESIVA — actuar, no solo reportar ─────────
                val timeSinceOpt = now - lastOptTime
                val optCooldown = when (level) {
                    ThermalLevel.CRITICAL, ThermalLevel.EMERGENCY -> 30_000L  // cada 30s
                    ThermalLevel.HOT                              -> 60_000L  // cada 1min
                    else                                          -> Long.MAX_VALUE
                }
                if (timeSinceOpt >= optCooldown) {
                    val actions = optRepo.executeAggressiveOptimization(snap, profile)
                    if (actions.isNotEmpty()) {
                        lastOptTime = now
                        // Notificar acción tomada
                        if (now - lastAlertTime > 120_000L) {
                            sendActionNotification(actions.first())
                            lastAlertTime = now
                        }
                    }
                }

                // ── Guardar en DB cada 5 ciclos o si hay anomalía ──────
                cycleCount++
                val shouldSave = cycleCount % 5 == 0
                    || level == ThermalLevel.CRITICAL
                    || level == ThermalLevel.EMERGENCY
                    || profile.isAnomaly

                if (shouldSave) {
                    withContext(Dispatchers.IO) {
                        db.thermalDao().insert(snap)
                        // Limpiar registros >7 días (solo 1 de cada 50 ciclos)
                        if (cycleCount % 50 == 0) {
                            db.thermalDao().deleteOlderThan(now - 7 * 24 * 3600 * 1000L)
                        }
                    }
                    // Persistir motor al disco solo cada 10 ciclos
                    if (cycleCount % 10 == 0) {
                        learningEngine.persistState()
                    }
                }

                // ── Notificación ───────────────────────────────────────
                nm.notify(NOTIF_ID, buildNotification(
                    "${snap.batteryTemp}°C · ${level.label}",
                    level, profile.riskScore
                ))

                // ── Alerta crítica ─────────────────────────────────────
                if ((level == ThermalLevel.CRITICAL || level == ThermalLevel.EMERGENCY)
                    && now - lastAlertTime > 300_000L) {
                    sendThermalAlert(snap, profile)
                    lastAlertTime = now
                }

            } catch (_: CancellationException) { break }
              catch (_: Exception) { }

            // ── Intervalo adaptativo — duerme según temperatura ────────
            val interval = when (lastLevel) {
                ThermalLevel.EMERGENCY -> INTERVAL_CRITICAL
                ThermalLevel.CRITICAL  -> INTERVAL_CRITICAL
                ThermalLevel.HOT       -> INTERVAL_HOT
                ThermalLevel.WARM      -> INTERVAL_WARM
                ThermalLevel.NORMAL    -> INTERVAL_NORMAL
            }
            delay(interval)
        }
    }

    private fun sendActionNotification(action: String) {
        val notif = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⚡ ThermaGuard actuó")
            .setContentText(action)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(ALERT_NOTIF_ID + 1, notif)
    }

    private fun sendThermalAlert(snap: ThermalSnapshot, profile: LearnedProfile) {
        val title = when (snap.batteryTemp.toThermalLevel()) {
            ThermalLevel.EMERGENCY -> "🚨 Temperatura de emergencia"
            else                   -> "🔴 Temperatura crítica"
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText("${snap.batteryTemp}°C · Riesgo ${profile.riskScore}% · El motor está actuando")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(ALERT_NOTIF_ID, notif)
    }

    private fun buildNotification(text: String, level: ThermalLevel, risk: Int): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, ThermalMonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val icon = when (level) {
            ThermalLevel.NORMAL   -> android.R.drawable.ic_menu_compass
            ThermalLevel.WARM     -> android.R.drawable.ic_menu_compass
            ThermalLevel.HOT      -> android.R.drawable.ic_dialog_alert
            else                  -> android.R.drawable.ic_dialog_alert
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle("ThermaGuard · Riesgo $risk%")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannels() {
        val monitor = NotificationChannel(CHANNEL_ID, "Monitor activo",
            NotificationManager.IMPORTANCE_LOW).apply {
            description = "Estado térmico en tiempo real"
            setShowBadge(false)
        }
        val alerts = NotificationChannel(CHANNEL_ALERT, "Alertas térmicas",
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alertas y acciones del motor"
        }
        nm.createNotificationChannel(monitor)
        nm.createNotificationChannel(alerts)
    }

    override fun onDestroy() {
        isRunning = false
        learningEngine.persistState()  // guardar estado al salir
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
