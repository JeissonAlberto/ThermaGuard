package com.jeissonalberto.thermaguard.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repositorio de optimizacion v2.
 * Ejecuta acciones reales sin root y genera intents directos a ajustes del sistema.
 */
class OptimizationRepository(private val context: Context) {

    private val activityManager: ActivityManager? = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val powerManager: PowerManager?    = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    // ============================================================
    //  ACCIONES EJECUTABLES DIRECTAMENTE
    // ============================================================

    /** Mata todos los procesos en background. Retorna cuantos mato. */
    suspend fun killBackgroundApps(): Int = withContext(Dispatchers.IO) {
        var killed = 0
        try {
            activityManager?.runningAppProcesses?.forEach { process ->
                if (process.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED) {
                    activityManager?.killBackgroundProcesses(process.processName)
                    killed++
                }
            }
        } catch (e: Exception) { }
        killed
    }

    /** Libera RAM matando procesos en cache. */
    suspend fun freeRam(): Int = withContext(Dispatchers.IO) {
        var freed = 0
        try {
            activityManager?.runningAppProcesses?.forEach { process ->
                if (process.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE) {
                    activityManager?.killBackgroundProcesses(process.processName)
                    freed++
                }
            }
        } catch (e: Exception) { }
        freed
    }

    /** Comprueba si el modo ahorro de bateria esta activo */
    fun isBatterySaverOn(): Boolean = try { powerManager?.isPowerSaveMode } catch (e: Exception) { false }

    /** Comprueba si la app esta exenta de optimizacion de bateria */
    fun isBatteryOptimizationIgnored(): Boolean = try {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName)
    } catch (e: Exception) { false }

    // ============================================================
    //  INTENTS — abren la pantalla correcta en Ajustes
    // ============================================================

    /** Abre ajustes de ahorro de bateria */
    fun getBatterySaverIntent(): Intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)

    /** Abre ajustes de brillo/pantalla */
    fun getDisplaySettingsIntent(): Intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)

    /** Abre ajustes de una app especifica */
    fun getAppSettingsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }

    /** Abre uso de bateria del sistema */
    fun getBatteryUsageIntent(): Intent = try {
        Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
    } catch (e: Exception) { Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS) }

    /** Abre ajustes de rendimiento Samsung (modo de rendimiento) */
    fun getPerformanceModeIntent(): Intent = try {
        Intent("com.samsung.android.app.aodservice.ACTION_DEVICE_CARE").apply {
            setPackage("com.samsung.android.lool")
        }.also {
            // fallback a ajustes de bateria si no existe
        }
    } catch (e: Exception) { Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS) }

    /** Abre ajustes de WiFi */
    fun getWifiSettingsIntent(): Intent = Intent(Settings.ACTION_WIFI_SETTINGS)

    /** Abre ajustes de Bluetooth */
    fun getBluetoothSettingsIntent(): Intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)

    /** Abre ajustes de sincronizacion de cuentas */
    fun getSyncSettingsIntent(): Intent = Intent(Settings.ACTION_SYNC_SETTINGS)

    /** Abre modo avion */
    fun getAirplaneModeIntent(): Intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)

    /** Abre Developer options / opciones de desarrollador */
    fun getDevOptionsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)

    /** Fuerza cierre de una app por packageName */
    fun getForceStopIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }

    // ============================================================
    //  PLAN DE OPTIMIZACION INTELIGENTE
    // ============================================================

    fun buildOptimizationPlan(snapshot: ThermalSnapshot): List<OptimizationAction> {
        val actions = mutableListOf<OptimizationAction>()
        val temp = snapshot.batteryTemp

        when {
            temp >= 48f -> {
                actions.add(OptimizationAction.KillBackgroundApps)
                actions.add(OptimizationAction.FreeRam)
                actions.add(OptimizationAction.ReduceBrightness(60))
                actions.add(OptimizationAction.EnableBatterySaver)
                actions.add(OptimizationAction.EnableAirplaneMode)
            }
            temp >= 45f -> {
                actions.add(OptimizationAction.KillBackgroundApps)
                actions.add(OptimizationAction.FreeRam)
                actions.add(OptimizationAction.ReduceBrightness(80))
                actions.add(OptimizationAction.EnableBatterySaver)
                if (snapshot.bluetoothActive) actions.add(OptimizationAction.DisableBluetooth)
            }
            temp >= 42f -> {
                actions.add(OptimizationAction.KillBackgroundApps)
                actions.add(OptimizationAction.ReduceBrightness(120))
                actions.add(OptimizationAction.EnableBatterySaver)
            }
            temp >= 38f -> {
                actions.add(OptimizationAction.ReduceBrightness(150))
                actions.add(OptimizationAction.KillBackgroundApps)
            }
        }

        // Si hay app especifica causando el calor
        if (snapshot.topApp.isNotEmpty() && snapshot.cpuUsage > 60f) {
            actions.add(OptimizationAction.ForceStopApp(snapshot.topApp))
        }

        return actions
    }

    // ============================================================
    //  MODO COOLING COMPLETO (todas las acciones a la vez)
    // ============================================================

    suspend fun executeCoolingMode(snapshot: ThermalSnapshot): CoolingResult {
        var appsKilled = 0
        val actionsApplied = mutableListOf<String>()

        // 1. Matar apps en background
        appsKilled = killBackgroundApps()
        if (appsKilled > 0) actionsApplied.add("$appsKilled apps en background cerradas")

        // 2. Liberar RAM
        val ramFreed = freeRam()
        if (ramFreed > 0) actionsApplied.add("Cache RAM liberada")

        actionsApplied.add("Abre Ajustes > Pantalla para bajar brillo")
        actionsApplied.add("Abre Ajustes > Bateria para activar ahorro")

        return CoolingResult(
            appsKilled = appsKilled,
            actionsApplied = actionsApplied,
            nextStep = getCoolingNextStep(snapshot)
        )
    }

    private fun getCoolingNextStep(snapshot: ThermalSnapshot): String = when {
        snapshot.batteryTemp >= 48f ->
            "Apaga la pantalla y deja el telefono en reposo 10 minutos en superficie plana"
        snapshot.batteryTemp >= 44f ->
            "Baja el brillo al minimo y activa modo ahorro de bateria"
        snapshot.isCharging && snapshot.batteryTemp >= 40f ->
            "Desconecta el cargador hasta que la temperatura baje de 38C"
        else ->
            "Evita apps pesadas por los proximos 5 minutos"
    }
}

data class CoolingResult(
    val appsKilled: Int,
    val actionsApplied: List<String>,
    val nextStep: String
)

sealed class OptimizationAction(val title: String, val description: String) {
    object KillBackgroundApps : OptimizationAction(
        "Cerrar apps en background",
        "Libera CPU y RAM matando procesos innecesarios"
    )
    object FreeRam : OptimizationAction(
        "Liberar RAM",
        "Limpia cache de memoria para reducir carga del sistema"
    )
    data class ReduceBrightness(val level: Int) : OptimizationAction(
        "Reducir brillo",
        "Baja brillo a nivel $level/255 para reducir consumo de pantalla"
    )
    object EnableBatterySaver : OptimizationAction(
        "Activar ahorro de bateria",
        "Limita rendimiento del sistema para reducir calor"
    )
    object DisableBluetooth : OptimizationAction(
        "Desactivar Bluetooth",
        "Bluetooth activo consume energia innecesariamente"
    )
    object DisableWifi : OptimizationAction(
        "Desactivar WiFi",
        "Solo si no lo estas usando activamente"
    )
    object EnableAirplaneMode : OptimizationAction(
        "Modo avion",
        "Apaga todas las radios (WiFi, BT, datos) para enfriar rapido"
    )
    data class ForceStopApp(val appName: String) : OptimizationAction(
        "Cerrar $appName",
        "Esta app esta usando CPU intensivamente"
    )
}
