package com.jeissonalberto.thermaguard.data

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class SensorRepository(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    suspend fun readSnapshot(): ThermalSnapshot = withContext(Dispatchers.IO) {
        val batteryTemp = readBatteryTemp()
        val batteryLevel = readBatteryLevel()
        val isCharging = readIsCharging()
        val thermalStatus = readThermalStatus()
        val zones = readThermalZones()
        val cpuTemp = zones["cpu"] ?: zones.values.firstOrNull() ?: 0f
        val gpuTemp = zones["gpu"] ?: 0f
        val skinTemp = zones["skin"] ?: zones["board"] ?: 0f
        val cpuUsage = readCpuUsage()
        val topApp = getTopApp()
        val wifiActive = isWifiActive()
        val bluetoothActive = readBluetoothState()
        val brightness = readBrightness()

        ThermalSnapshot(
            batteryTemp = batteryTemp,
            cpuTemp = cpuTemp,
            gpuTemp = gpuTemp,
            skinTemp = skinTemp,
            cpuUsage = cpuUsage,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            thermalStatus = thermalStatus,
            topApp = topApp,
            wifiActive = wifiActive,
            bluetoothActive = bluetoothActive,
            brightnessLevel = brightness
        )
    }

    private fun readBatteryTemp(): Float {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        } catch (e: Exception) { 0f }
    }

    private fun readBatteryLevel(): Int {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        } catch (e: Exception) { 0 }
    }

    private fun readIsCharging(): Boolean {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            status == BatteryManager.BATTERY_STATUS_CHARGING
        } catch (e: Exception) { false }
    }

    private fun readThermalStatus(): Int {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                powerManager.currentThermalStatus
            } else 0
        } catch (e: Exception) { 0 }
    }

    private fun readBluetoothState(): Boolean {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bm?.adapter?.isEnabled ?: false
        } catch (e: Exception) { false }
    }

    private fun readThermalZones(): Map<String, Float> {
        val result = mutableMapOf<String, Float>()
        return try {
            val thermalDir = File("/sys/class/thermal/")
            if (!thermalDir.exists()) return result

            thermalDir.listFiles { f -> f.name.startsWith("thermal_zone") }
                ?.forEach { zone ->
                    try {
                        val tempFile = File(zone, "temp")
                        val typeFile = File(zone, "type")
                        if (tempFile.exists() && typeFile.exists()) {
                            val rawTemp = tempFile.readText().trim().toLongOrNull() ?: return@forEach
                            val type = typeFile.readText().trim().lowercase()
                            val temp = if (rawTemp > 1000) rawTemp / 1000f else rawTemp.toFloat()
                            val key = when {
                                type.contains("cpu") || type.contains("processor") -> "cpu"
                                type.contains("gpu") -> "gpu"
                                type.contains("skin") || type.contains("surface") -> "skin"
                                type.contains("battery") || type.contains("batt") -> "battery_zone"
                                type.contains("board") || type.contains("pcb") -> "board"
                                else -> type
                            }
                            if (key == "cpu") {
                                val existing = result["cpu"] ?: 0f
                                if (temp > existing) result["cpu"] = temp
                            } else {
                                result[key] = temp
                            }
                        }
                    } catch (e: Exception) { }
                }
            result
        } catch (e: Exception) { result }
    }

    private suspend fun readCpuUsage(): Float = withContext(Dispatchers.IO) {
        try {
            val stat1 = File("/proc/stat").readLines().firstOrNull() ?: return@withContext 0f
            delay(500L)  // usar delay de coroutine, no Thread.sleep
            val stat2 = File("/proc/stat").readLines().firstOrNull() ?: return@withContext 0f

            val values1 = stat1.split(" ").drop(2).mapNotNull { it.toLongOrNull() }
            val values2 = stat2.split(" ").drop(2).mapNotNull { it.toLongOrNull() }

            if (values1.size < 4 || values2.size < 4) return@withContext 0f

            val total1 = values1.sum()
            val total2 = values2.sum()
            val idle1 = values1[3]
            val idle2 = values2[3]
            val totalDiff = total2 - total1
            val idleDiff = idle2 - idle1

            if (totalDiff <= 0L) 0f
            else ((totalDiff - idleDiff).toFloat() / totalDiff.toFloat()) * 100f
        } catch (e: Exception) { 0f }
    }

    private fun getTopApp(): String {
        return try {
            val processes = activityManager.runningAppProcesses
            processes?.firstOrNull {
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }?.processName?.split(".")?.last() ?: ""
        } catch (e: Exception) { "" }
    }

    private fun isWifiActive(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) { false }
    }

    private fun readBrightness(): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 0)
        } catch (e: Exception) { 0 }
    }

    fun analyzeHeatCauses(snapshot: ThermalSnapshot): List<HeatCause> {
        val causes = mutableListOf<HeatCause>()
        if (snapshot.cpuUsage > 70f) {
            causes.add(HeatCause(
                title = "CPU saturada",
                description = "Uso al ${snapshot.cpuUsage.toInt()}%${if (snapshot.topApp.isNotEmpty()) " — app activa: ${snapshot.topApp}" else ""}",
                severity = if (snapshot.cpuUsage > 90f) 5 else 3
            ))
        }
        if (snapshot.isCharging && snapshot.batteryTemp > 38f) {
            causes.add(HeatCause(
                title = "Carga + uso simultaneo",
                description = "Cargar mientras usas el telefono genera calor extra",
                severity = 4
            ))
        }
        if (snapshot.batteryTemp > 40f) {
            causes.add(HeatCause(
                title = "Bateria sobrecalentada",
                description = "Temperatura: ${snapshot.batteryTemp}C — por encima del limite seguro",
                severity = 5
            ))
        }
        if (snapshot.brightnessLevel > 200) {
            causes.add(HeatCause(
                title = "Brillo maximo",
                description = "Pantalla al maximo consume mas energia",
                severity = 2
            ))
        }
        return causes.sortedByDescending { it.severity }
    }
}
