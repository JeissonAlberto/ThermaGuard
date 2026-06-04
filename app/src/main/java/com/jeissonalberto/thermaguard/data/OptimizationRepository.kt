package com.jeissonalberto.thermaguard.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Acciones de optimización que se pueden ejecutar sin root
 */
class OptimizationRepository(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /**
     * Reduce brillo de pantalla al nivel especificado (0-255)
     */
    fun reduceBrightness(level: Int = 100): Intent {
        // Se necesita permiso WRITE_SETTINGS — abre la pantalla de ajuste
        return Intent(Settings.ACTION_DISPLAY_SETTINGS)
    }

    /**
     * Mata procesos en background para liberar CPU/RAM
     */
    suspend fun killBackgroundApps(): Int = withContext(Dispatchers.IO) {
        var killed = 0
        try {
            activityManager.runningAppProcesses?.forEach { process ->
                if (process.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) {
                    activityManager.killBackgroundProcesses(process.processName)
                    killed++
                }
            }
        } catch (e: Exception) { /* Sin permisos suficientes */ }
        killed
    }

    /**
     * Activa el modo ahorro de batería del sistema
     */
    fun getBatterySaverIntent(): Intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)

    /**
     * Abre ajustes de apps para que el usuario cierre manualmente
     */
    fun getAppSettingsIntent(packageName: String): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
        }
    }

    /**
     * Plan de optimización automático basado en temperatura
     */
    fun buildOptimizationPlan(snapshot: ThermalSnapshot): List<OptimizationAction> {
        val actions = mutableListOf<OptimizationAction>()
        val temp = snapshot.batteryTemp

        when {
            temp >= 45f -> {
                actions.add(OptimizationAction.KillBackgroundApps)
                actions.add(OptimizationAction.ReduceBrightness(80))
                actions.add(OptimizationAction.EnableBatterySaver)
                actions.add(OptimizationAction.DisableBluetooth)
                if (!snapshot.wifiActive) actions.add(OptimizationAction.DisableWifi)
            }
            temp >= 40f -> {
                actions.add(OptimizationAction.KillBackgroundApps)
                actions.add(OptimizationAction.ReduceBrightness(120))
                actions.add(OptimizationAction.EnableBatterySaver)
            }
            temp >= 37f -> {
                actions.add(OptimizationAction.ReduceBrightness(150))
                actions.add(OptimizationAction.KillBackgroundApps)
            }
        }

        return actions
    }
}

sealed class OptimizationAction(val title: String, val description: String) {
    object KillBackgroundApps : OptimizationAction(
        "Cerrar apps en background",
        "Libera CPU y RAM matando procesos innecesarios"
    )
    data class ReduceBrightness(val level: Int) : OptimizationAction(
        "Reducir brillo",
        "Baja brillo a nivel $level/255 para reducir consumo"
    )
    object EnableBatterySaver : OptimizationAction(
        "Activar ahorro de batería",
        "Limita rendimiento del sistema para reducir calor"
    )
    object DisableBluetooth : OptimizationAction(
        "Desactivar Bluetooth",
        "Bluetooth activo consume energía innecesariamente"
    )
    object DisableWifi : OptimizationAction(
        "Desactivar WiFi",
        "Solo si no lo estás usando activamente"
    )
}
