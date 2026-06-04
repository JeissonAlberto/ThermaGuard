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
import kotlinx.coroutines.withContext
import java.io.File

class SensorRepository(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    /**
     * Lee snapshot completo de todos los sensores disponibles
     */
    suspend fun readSnapshot(): ThermalSnapshot = withContext(Dispatchers.IO) {
        val batteryIntent = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val batteryTemp = batteryIntent
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            ?.div(10f) ?: 0f

        val batteryLevel = batteryIntent
            ?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0

        val isCharging = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN
        ) == BatteryManager.BATTERY_STATUS_CHARGING

        val thermalStatus = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            powerManager.currentThermalStatus
        } else 0

        val zones = readThermalZones()
        val cpuTemp = zones["cpu"] ?: zones.values.firstOrNull() ?: 0f
        val gpuTemp = zones["gpu"] ?: 0f
        val skinTemp = zones["skin"] ?: zones["board"] ?: 0f

        val cpuUsage = readCpuUsage()
        val topApp = getTopApp()
        val wifiActive = isWifiActive()
        val bluetoothActive = bluetoothManager.adapter?.isEnabled ?: false
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

    /**
     * Lee todas las zonas térmicas del sistema desde /sys/class/thermal/
     * Funciona sin root en la mayoría de dispositivos Android
     */
    private fun readThermalZones(): Map<String, Float> {
        val result = mutableMapOf<String, Float>()
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

                        // Android puede reportar en mili-celsius o celsius
                        val temp = if (rawTemp > 1000) rawTemp / 1000f else rawTemp.toFloat()

                        // Clasificar zona por tipo
                        val key = when {
                            type.contains("cpu") || type.contains("processor") -> "cpu"
                            type.contains("gpu") -> "gpu"
                            type.contains("skin") || type.contains("surface") -> "skin"
                            type.contains("battery") || type.contains("batt") -> "battery_zone"
                            type.contains("board") || type.contains("pcb") -> "board"
                            else -> type
                        }

                        // Para CPU, quedarse con la más alta
                        if (key == "cpu") {
                            val existing = result["cpu"] ?: 0f
                            if (temp > existing) result["cpu"] = temp
                        } else {
                            result[key] = temp
                        }
                    }
                } catch (e: Exception) {
                    // Zona no accesible, ignorar
                }
            }

        return result
    }

    /**
     * Estimación de uso de CPU via /proc/stat
     */
    private fun readCpuUsage(): Float {
        return try {
            val stat1 = File("/proc/stat").readLines().first()
            Thread.sleep(200)
            val stat2 = File("/proc/stat").readLines().first()

            val values1 = stat1.split(" ").drop(2).map { it.toLong() }
            val values2 = stat2.split(" ").drop(2).map { it.toLong() }

            val idle1 = values1[3]
            val idle2 = values2[3]
            val total1 = values1.sum()
            val total2 = values2.sum()

            val totalDiff = total2 - total1
            val idleDiff = idle2 - idle1

            if (totalDiff == 0L) 0f
            else ((totalDiff - idleDiff).toFloat() / totalDiff) * 100f
        } catch (e: Exception) {
            0f
        }
    }

    /**
     * App en primer plano (requiere PACKAGE_USAGE_STATS)
     */
    private fun getTopApp(): String {
        return try {
            val processes = activityManager.runningAppProcesses
            processes?.firstOrNull {
                it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }?.processName?.split(".")?.last() ?: "Desconocida"
        } catch (e: Exception) {
            "Desconocida"
        }
    }

    private fun isWifiActive(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun readBrightness(): Int {
        return try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                0
            )
        } catch (e: Exception) { 0 }
    }

    /**
     * Detecta causas probables de calentamiento
     */
    fun analyzeHeatCauses(snapshot: ThermalSnapshot): List<HeatCause> {
        val causes = mutableListOf<HeatCause>()

        if (snapshot.cpuUsage > 70f) {
            causes.add(HeatCause(
                title = "CPU saturada",
                description = "Uso al ${snapshot.cpuUsage.toInt()}% — app '${snapshot.topApp}' probablemente responsable",
                severity = if (snapshot.cpuUsage > 90f) 5 else 3
            ))
        }

        if (snapshot.isCharging && snapshot.batteryTemp > 38f) {
            causes.add(HeatCause(
                title = "Carga + uso simultáneo",
                description = "Cargar mientras usas el teléfono genera calor extra",
                severity = 4
            ))
        }

        if (snapshot.batteryTemp > 40f) {
            causes.add(HeatCause(
                title = "Batería sobrecalentada",
                description = "Temperatura de batería: ${snapshot.batteryTemp}°C — por encima del límite seguro",
                severity = 5
            ))
        }

        if (snapshot.brightnessLevel > 200) {
            causes.add(HeatCause(
                title = "Brillo máximo",
                description = "Pantalla al máximo consume más energía y genera calor",
                severity = 2
            ))
        }

        if (snapshot.wifiActive && snapshot.bluetoothActive && snapshot.cpuUsage > 50f) {
            causes.add(HeatCause(
                title = "Múltiples radios activos",
                description = "WiFi + Bluetooth simultáneos con CPU alta aumentan temperatura",
                severity = 2
            ))
        }

        if (causes.isEmpty() && snapshot.batteryTemp > 35f) {
            causes.add(HeatCause(
                title = "Temperatura moderada",
                description = "El dispositivo está tibio pero dentro del rango aceptable",
                severity = 1,
                actionable = false
            ))
        }

        return causes.sortedByDescending { it.severity }
    }
}
