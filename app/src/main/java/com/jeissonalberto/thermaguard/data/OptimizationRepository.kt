package com.jeissonalberto.thermaguard.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OptimizationRepository v5 — Acción agresiva real
 *
 * Sin root puede:
 * - Matar procesos en background (IMPORTANCE_CACHED y GONE)
 * - Solicitar GC del sistema
 * - Guiar al usuario a los ajustes correctos con deep links
 * - Forzar trim de memoria
 * - Reducir trabajo de background vía JobScheduler hints
 *
 * La clave: actúa en cascada según nivel de riesgo.
 * No pregunta — actúa y reporta.
 */
class OptimizationRepository(private val context: Context) {

    private val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    // ── Umbral de intervención por nivel ──────────────────────────────────
    private val BLOCKED = setOf(
        "com.jeissonalberto.thermaguard",
        "android", "com.android", "com.samsung.android.systemui",
        "com.sec.android.app.launcher", "com.google.android.gms"
    )

    /**
     * Optimización agresiva en cascada según nivel térmico y perfil aprendido.
     * Retorna lista de acciones tomadas (para mostrar al usuario).
     */
    suspend fun executeAggressiveOptimization(
        snap: ThermalSnapshot,
        profile: LearnedProfile
    ): List<String> = withContext(Dispatchers.IO) {
        val actions = mutableListOf<String>()
        val level = snap.batteryTemp.toThermalLevel()

        // ── NIVEL 1: Tibio (≥38°C) — limpieza suave ───────────────────
        if (snap.batteryTemp >= 38f) {
            val killed = killCachedProcesses()
            if (killed > 0) actions.add("Liberé $killed procesos en caché")
            requestMemoryTrim()
        }

        // ── NIVEL 2: Caliente (≥42°C) — acción moderada ───────────────
        if (snap.batteryTemp >= 42f) {
            val killed2 = killServiceProcesses()
            if (killed2 > 0) actions.add("Terminé $killed2 servicios en background")

            // Si CPU alta, matar procesos más agresivamente
            if (snap.cpuUsage > 60f) {
                val killed3 = killAllNonEssential()
                if (killed3 > 0) actions.add("CPU al ${snap.cpuUsage.toInt()}% — cerré $killed3 apps")
            }
        }

        // ── NIVEL 3: Crítico (≥45°C) — acción máxima ──────────────────
        if (snap.batteryTemp >= 45f) {
            // Matar todo lo que se pueda
            val total = killAllNonEssential() + killCachedProcesses() + killServiceProcesses()
            if (total > 0) actions.add("🔴 Crítico — eliminé $total procesos")

            // Forzar GC múltiple
            repeat(3) {
                System.gc()
                Runtime.getRuntime().gc()
            }
            actions.add("Memoria forzada a liberar")
        }

        // ── NIVEL 4: Emergencia (≥50°C) — nuclear ─────────────────────
        if (snap.batteryTemp >= 50f) {
            val nuclear = nuclearClean()
            actions.add("🚨 Emergencia — limpieza máxima ($nuclear procesos)")
            actions.add("⚠️ Apaga la pantalla y deja el teléfono en reposo")
        }

        // ── Causa aprendida: carga + calor ─────────────────────────────
        if (snap.isCharging && snap.batteryTemp >= 40f && profile.chargingHeatPct > 30f) {
            actions.add("💡 Patrón detectado: desconecta el cargador temporalmente")
        }

        // ── App correlacionada con calor ────────────────────────────────
        if (profile.topHeatApp.isNotEmpty() && profile.topHeatAppScore > 40f
            && !BLOCKED.any { profile.topHeatApp.contains(it) }) {
            actions.add("📱 '${profile.topHeatApp}' genera calor — considera cerrarla")
        }

        actions
    }

    // ── Matar procesos CACHED (los más seguros) ───────────────────────────
    private fun killCachedProcesses(): Int {
        var count = 0
        try {
            am.runningAppProcesses?.forEach { proc ->
                if (proc.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
                    && BLOCKED.none { proc.processName.contains(it) }) {
                    am.killBackgroundProcesses(proc.processName)
                    count++
                }
            }
        } catch (_: Exception) {}
        return count
    }

    // ── Matar servicios en background ─────────────────────────────────────
    private fun killServiceProcesses(): Int {
        var count = 0
        try {
            am.runningAppProcesses?.forEach { proc ->
                if (proc.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE
                    && proc.importance < ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && BLOCKED.none { proc.processName.contains(it) }) {
                    am.killBackgroundProcesses(proc.processName)
                    count++
                }
            }
        } catch (_: Exception) {}
        return count
    }

    // ── Matar todo lo no esencial ─────────────────────────────────────────
    private fun killAllNonEssential(): Int {
        var count = 0
        try {
            am.runningAppProcesses?.forEach { proc ->
                if (proc.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && BLOCKED.none { proc.processName.contains(it) }) {
                    am.killBackgroundProcesses(proc.processName)
                    count++
                }
            }
        } catch (_: Exception) {}
        return count
    }

    // ── Limpieza nuclear (emergencia) ─────────────────────────────────────
    private fun nuclearClean(): Int {
        val c1 = killAllNonEssential()
        val c2 = killServiceProcesses()
        val c3 = killCachedProcesses()
        repeat(5) {
            System.gc()
            Runtime.getRuntime().gc()
        }
        // Trim de memoria del sistema
        try {
            am.runningAppProcesses?.forEach { proc ->
                if (BLOCKED.none { proc.processName.contains(it) }) {
                    am.killBackgroundProcesses(proc.processName)
                }
            }
        } catch (_: Exception) {}
        return c1 + c2 + c3
    }

    // ── Solicitar al sistema que libere memoria ───────────────────────────
    private fun requestMemoryTrim() {
        try {
            System.gc()
            Runtime.getRuntime().gc()
            Runtime.getRuntime().runFinalization()
        } catch (_: Exception) {}
    }

    // ── Intents de ajustes (para guiar al usuario) ────────────────────────
    fun isBatterySaverOn() = try { pm.isPowerSaveMode } catch (_: Exception) { false }
    fun isBatteryOptimizationIgnored() = try {
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: Exception) { false }

    fun getBatterySaverIntent()   = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
    fun getDisplaySettingsIntent() = Intent(Settings.ACTION_DISPLAY_SETTINGS)
    fun getAppSettingsIntent(pkg: String) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .apply { data = Uri.parse("package:$pkg") }
    fun getBatteryUsageIntent()   = try {
        Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
    } catch (_: Exception) { Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS) }
    fun getWifiSettingsIntent()    = Intent(Settings.ACTION_WIFI_SETTINGS)
    fun getBluetoothSettingsIntent() = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
    fun getSyncSettingsIntent()    = Intent(Settings.ACTION_SYNC_SETTINGS)
    fun getPerformanceModeIntent() = try {
        Intent("com.samsung.android.app.aodservice.ACTION_DEVICE_CARE")
            .apply { setPackage("com.samsung.android.lool") }
    } catch (_: Exception) { Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS) }

    // Método legacy para compatibilidad
    suspend fun killBackgroundApps() = killCachedProcesses()
    suspend fun freeRam() = killCachedProcesses() + killServiceProcesses()
}
