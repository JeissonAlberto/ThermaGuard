package com.jeissonalberto.thermaguard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Snapshot completo del estado térmico del dispositivo
 */
@Entity(tableName = "thermal_history")
data class ThermalSnapshot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val batteryTemp: Float = 0f,       // °C
    val cpuTemp: Float = 0f,           // °C (zona térmica)
    val gpuTemp: Float = 0f,           // °C (zona térmica)
    val skinTemp: Float = 0f,          // °C (temperatura superficie)
    val cpuUsage: Float = 0f,          // % uso CPU
    val batteryLevel: Int = 0,         // % carga
    val isCharging: Boolean = false,
    val thermalStatus: Int = 0,        // PowerManager thermal status
    val topApp: String = "",           // App más consumidora
    val wifiActive: Boolean = false,
    val bluetoothActive: Boolean = false,
    val brightnessLevel: Int = 0       // 0-255
)

/**
 * Niveles de temperatura para alertas
 */
enum class ThermalLevel(val label: String, val emoji: String) {
    NORMAL("Normal", "🟢"),
    WARM("Tibio", "🟡"),
    HOT("Caliente", "🟠"),
    CRITICAL("Crítico", "🔴"),
    EMERGENCY("Emergencia", "🚨")
}

fun Float.toThermalLevel(): ThermalLevel = when {
    this < 35f -> ThermalLevel.NORMAL
    this < 40f -> ThermalLevel.WARM
    this < 45f -> ThermalLevel.HOT
    this < 50f -> ThermalLevel.CRITICAL
    else       -> ThermalLevel.EMERGENCY
}

/**
 * Causa detectada de calentamiento
 */
data class HeatCause(
    val title: String,
    val description: String,
    val severity: Int, // 1-5
    val actionable: Boolean = true
)
