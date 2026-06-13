package com.jeissonalberto.thermaguard.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jeissonalberto.thermaguard.MainActivity
import com.jeissonalberto.thermaguard.R
import com.jeissonalberto.thermaguard.data.ThermalLevel

// ─────────────────────────────────────────────────────────────────────────────
//  NotificationEngine — Motor de notificaciones push de ThermaGuard
//
//  Canales:
//    • CHANNEL_MONITOR  → Notificación persistente del servicio (baja prioridad)
//    • CHANNEL_ALERT    → Alertas térmicas críticas (alta prioridad + vibra)
//    • CHANNEL_WARNING  → Avisos de calentamiento progresivo (prioridad media)
//    • CHANNEL_INFO     → Informes de enfriamiento y eventos puntuales
//
//  Características:
//    • Cooldown por nivel para no spamear
//    • Acciones en la notificación (Optimizar / Descartar)
//    • Vibración adaptativa según severidad
//    • Notificaciones agrupadas (bundle) cuando hay varias seguidas
// ─────────────────────────────────────────────────────────────────────────────
object NotificationEngine {

    const val CHANNEL_MONITOR = "tg_monitor"
    const val CHANNEL_ALERT   = "tg_alert_critical"
    const val CHANNEL_WARNING = "tg_alert_warning"
    const val CHANNEL_INFO    = "tg_info"

    private const val GRP_ALERTS   = "tg_group_alerts"
    private const val NOTIF_MONITOR = 1001
    private const val NOTIF_ALERT   = 1002
    private const val NOTIF_WARNING = 1003
    private const val NOTIF_INFO    = 1004
    private const val NOTIF_SUMMARY = 1005

    // Cooldown mínimo entre alertas del mismo nivel (ms)
    private val COOLDOWN_MS = mapOf(
        ThermalLevel.EMERGENCY to 30_000L,
        ThermalLevel.CRITICAL  to 60_000L,
        ThermalLevel.HOT       to 120_000L,
        ThermalLevel.WARM      to 300_000L,
        ThermalLevel.NORMAL    to Long.MAX_VALUE,
    )

    private val lastAlertAt = mutableMapOf<ThermalLevel, Long>()
    private var pendingAlertCount = 0

    // ── Inicialización de canales ─────────────────────────────────────────
    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channels = listOf(
            NotificationChannel(
                CHANNEL_MONITOR,
                "Monitor activo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Estado térmico en tiempo real — siempre visible"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
            NotificationChannel(
                CHANNEL_ALERT,
                "🔥 Alertas críticas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Temperatura crítica o emergencia — requiere atención inmediata"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 400, 100, 600)
                enableLights(true)
                lightColor = android.graphics.Color.RED
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(
                    android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
            },
            NotificationChannel(
                CHANNEL_WARNING,
                "⚠️ Advertencias térmicas",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Temperatura elevada — aviso preventivo"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 100, 150)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(
                CHANNEL_INFO,
                "ℹ️ Información térmica",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Eventos de enfriamiento, resúmenes y estadísticas"
                setShowBadge(false)
            }
        )

        channels.forEach { nm.createNotificationChannel(it) }
    }

    // ── API pública principal ────────────────────────────────────────────
    fun sendThermalAlert(
        context: Context,
        level: ThermalLevel,
        temperature: Float,
        riskScore: Int,
        details: String = "",
        forceShow: Boolean = false
    ) {
        if (!canSendAlert(level) && !forceShow) return
        lastAlertAt[level] = System.currentTimeMillis()

        when (level) {
            ThermalLevel.EMERGENCY -> sendEmergency(context, temperature, riskScore, details)
            ThermalLevel.CRITICAL  -> sendCritical(context, temperature, riskScore, details)
            ThermalLevel.HOT       -> sendHot(context, temperature, riskScore, details)
            ThermalLevel.WARM      -> sendWarm(context, temperature, riskScore)
            ThermalLevel.NORMAL    -> { /* no notificar en normal */ }
        }
    }

    fun sendCoolingReport(context: Context, peakTemp: Float, currentTemp: Float, minutes: Float) {
        val nm = NotificationManagerCompat.from(context)
        if (!hasPermission(context)) return
        val notif = NotificationCompat.Builder(context, CHANNEL_INFO)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("✅ Dispositivo enfriándose")
            .setContentText("Bajó de ${peakTemp.toInt()}°C → ${currentTemp.toInt()}°C en ${minutes.toInt()} min")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setGroup(GRP_ALERTS)
            .build()
        nm.notify(NOTIF_INFO, notif)
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
        pendingAlertCount = 0
    }

    fun cancelAlert(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ALERT)
        NotificationManagerCompat.from(context).cancel(NOTIF_WARNING)
        pendingAlertCount = 0
    }

    // ── Niveles específicos ───────────────────────────────────────────────
    private fun sendEmergency(context: Context, temp: Float, risk: Int, details: String) {
        val nm = NotificationManagerCompat.from(context)
        if (!hasPermission(context)) return
        vibrate(context, VibrationPattern.EMERGENCY)
        pendingAlertCount++

        val notif = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 EMERGENCIA TÉRMICA — ${temp.toInt()}°C")
            .setContentText("Riesgo ${risk}/100 · ${details.ifEmpty { "Apaga apps pesadas ahora" }}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("⚠️ La temperatura alcanzó ${temp.toInt()}°C con riesgo $risk/100.\n\n" +
                         "${details.ifEmpty { "Cierra todas las aplicaciones en segundo plano, desconecta el cargador y coloca el dispositivo en un lugar fresco." }}\n\n" +
                         "ThermaGuard está aplicando medidas automáticas."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setGroup(GRP_ALERTS)
            .setColor(android.graphics.Color.RED)
            .addAction(
                android.R.drawable.ic_menu_manage,
                "Optimizar ahora",
                buildAction(context, "ACTION_OPTIMIZE")
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Descartar",
                buildAction(context, "ACTION_DISMISS")
            )
            .setContentIntent(buildTapIntent(context))
            .build()

        nm.notify(NOTIF_ALERT, notif)
        updateSummary(context)
    }

    private fun sendCritical(context: Context, temp: Float, risk: Int, details: String) {
        val nm = NotificationManagerCompat.from(context)
        if (!hasPermission(context)) return
        vibrate(context, VibrationPattern.CRITICAL)
        pendingAlertCount++

        val notif = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🔥 Temperatura crítica — ${temp.toInt()}°C")
            .setContentText("Riesgo ${risk}/100 · ${details.ifEmpty { "Se recomienda acción" }}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("La temperatura llegó a ${temp.toInt()}°C (riesgo: $risk/100).\n" +
                         "${details.ifEmpty { "Reducir uso del procesador y cerrar apps en segundo plano." }}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setGroup(GRP_ALERTS)
            .setColor(0xFFFF5252.toInt())
            .addAction(
                android.R.drawable.ic_menu_manage,
                "Optimizar",
                buildAction(context, "ACTION_OPTIMIZE")
            )
            .setContentIntent(buildTapIntent(context))
            .build()

        nm.notify(NOTIF_ALERT, notif)
        updateSummary(context)
    }

    private fun sendHot(context: Context, temp: Float, risk: Int, details: String) {
        val nm = NotificationManagerCompat.from(context)
        if (!hasPermission(context)) return
        vibrate(context, VibrationPattern.WARNING)
        pendingAlertCount++

        val notif = NotificationCompat.Builder(context, CHANNEL_WARNING)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("⚠️ Calentando — ${temp.toInt()}°C")
            .setContentText("Riesgo ${risk}/100 · ${details.ifEmpty { "Temperatura elevada" }}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(GRP_ALERTS)
            .setColor(0xFFFF6D00.toInt())
            .addAction(
                android.R.drawable.ic_menu_manage,
                "Ver en app",
                buildTapIntent(context)
            )
            .setContentIntent(buildTapIntent(context))
            .build()

        nm.notify(NOTIF_WARNING, notif)
        updateSummary(context)
    }

    private fun sendWarm(context: Context, temp: Float, risk: Int) {
        val nm = NotificationManagerCompat.from(context)
        if (!hasPermission(context)) return
        pendingAlertCount++

        val notif = NotificationCompat.Builder(context, CHANNEL_WARNING)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("🟡 Dispositivo tibio — ${temp.toInt()}°C")
            .setContentText("Riesgo ${risk}/100 · Monitoreando")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setGroup(GRP_ALERTS)
            .setContentIntent(buildTapIntent(context))
            .build()

        nm.notify(NOTIF_WARNING, notif)
        updateSummary(context)
    }

    // ── Resumen agrupado ──────────────────────────────────────────────────
    private fun updateSummary(context: Context) {
        if (pendingAlertCount < 2) return
        val nm = NotificationManagerCompat.from(context)
        if (!hasPermission(context)) return
        val summary = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("ThermaGuard — $pendingAlertCount alertas")
            .setContentText("Toca para ver el estado térmico")
            .setGroup(GRP_ALERTS)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(buildTapIntent(context))
            .build()
        nm.notify(NOTIF_SUMMARY, summary)
    }

    // ── Cooldown ──────────────────────────────────────────────────────────
    private fun canSendAlert(level: ThermalLevel): Boolean {
        val last    = lastAlertAt[level] ?: 0L
        val cooldown = COOLDOWN_MS[level] ?: Long.MAX_VALUE
        return System.currentTimeMillis() - last >= cooldown
    }

    // ── Vibración ─────────────────────────────────────────────────────────
    private enum class VibrationPattern { EMERGENCY, CRITICAL, WARNING }

    private fun vibrate(context: Context, pattern: VibrationPattern) {
        try {
            @Suppress("DEPRECATION")
            val vib: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
                    ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
                    ?: return
            } else {
                (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator) ?: return
            }
            if (!vib.hasVibrator()) return
            val effect = when (pattern) {
                VibrationPattern.EMERGENCY -> VibrationEffect.createWaveform(
                    longArrayOf(0, 300, 100, 600, 100, 900), -1)
                VibrationPattern.CRITICAL  -> VibrationEffect.createWaveform(
                    longArrayOf(0, 200, 100, 400), -1)
                VibrationPattern.WARNING   -> VibrationEffect.createOneShot(
                    150, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vib.vibrate(effect)
        } catch (_: Exception) { /* sin vibración disponible */ }
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private fun hasPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else true

    private fun buildTapIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildAction(context: Context, action: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
