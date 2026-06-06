package com.jeissonalberto.thermaguard.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "thermal_history")
data class ThermalSnapshot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val batteryTemp: Float = 0f,
    val cpuTemp: Float = 0f,
    val gpuTemp: Float = 0f,
    val skinTemp: Float = 0f,
    val boardTemp: Float = 0f,
    val modemTemp: Float = 0f,
    val displayTemp: Float = 0f,
    val cpuUsage: Float = 0f,
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val thermalStatus: Int = 0,
    val topApp: String = "",
    val wifiActive: Boolean = false,
    val bluetoothActive: Boolean = false,
    val brightnessLevel: Int = 0,
    val ramUsageMb: Int = 0
) {
    // Campos no persistidos en Room (calculados en runtime)
    @Ignore var allZones: Map<String, Float> = emptyMap()
    @Ignore var perCoreUsage: List<Float> = emptyList()
    @Ignore var topProcesses: List<ProcessInfo> = emptyList()
}

enum class ThermalLevel(val label: String, val emoji: String) {
    NORMAL("Normal", "🟢"),
    WARM("Tibio", "🟡"),
    HOT("Caliente", "🟠"),
    CRITICAL("Critico", "🔴"),
    EMERGENCY("Emergencia", "🚨")
}

fun Float.toThermalLevel(): ThermalLevel = when {
    this < 35f -> ThermalLevel.NORMAL
    this < 40f -> ThermalLevel.WARM
    this < 45f -> ThermalLevel.HOT
    this < 50f -> ThermalLevel.CRITICAL
    else       -> ThermalLevel.EMERGENCY
}

data class HeatCause(
    val title: String,
    val description: String,
    val severity: Int,
    val actionable: Boolean = true
)

// ---- Diagnostico de componentes ----

enum class ThermalComponent(val label: String, val icon: String) {
    CPU("Procesador", "💻"),
    GPU("Graficos", "🎮"),
    BATTERY("Bateria", "🔋"),
    MODEM("Modem / Radio", "📡"),
    DISPLAY("Pantalla", "📱"),
    BOARD("Placa base", "🔧"),
    PROCESS("Proceso activo", "⚙️")
}

enum class ComponentStatus(val label: String, val color: Long) {
    NORMAL("Normal", 0xFF00E676),
    WARM("Tibio", 0xFFFFD600),
    HOT("Caliente", 0xFFFF6D00),
    CRITICAL("Critico", 0xFFFF1744)
}

data class ComponentDiagnosis(
    val component: ThermalComponent,
    val temp: Float,
    val usagePct: Float,          // -1 si no aplica
    val status: ComponentStatus,
    val cause: String,
    val advice: String,
    val perCore: List<Float> = emptyList(),
    val processes: List<ProcessInfo> = emptyList()
)

data class ProcessInfo(
    val pid: Int,
    val name: String,
    val fullName: String,
    val importance: Int,
    val description: String
)

// ---- Modo Juego ----
data class GameModeState(
    val isActive: Boolean = false,
    val detectedGame: String = "",
    val permissiveThreshold: Float = 46f,  // más alto que el normal
    val startedAt: Long = 0L
)

// ---- Modo Carga Segura ----
data class SafeChargeState(
    val isCharging: Boolean = false,
    val chargingTemp: Float = 0f,
    val isOverheating: Boolean = false,   // >38°C mientras carga
    val recommendation: String = ""
)

// ---- Exportación ----
data class ExportRecord(
    val timestamp: String,
    val batteryTemp: Float,
    val cpuUsage: Float,
    val batteryLevel: Int,
    val topApp: String,
    val riskLevel: String
)

// ---- Alerta inteligente ----
data class SmartAlert(
    val id: String,
    val title: String,
    val body: String,
    val priority: Int,  // 1=info, 2=warning, 3=critical
    val suppressUntil: Long = 0L  // no molestar hasta este timestamp
)
