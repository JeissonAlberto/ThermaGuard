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
    // Campos runtime (no persistidos en Room) — constructor secundario para KAPT
    @Ignore constructor(
        id: Long, timestamp: Long, batteryTemp: Float, cpuTemp: Float, gpuTemp: Float,
        skinTemp: Float, boardTemp: Float, modemTemp: Float, displayTemp: Float,
        cpuUsage: Float, batteryLevel: Int, isCharging: Boolean, thermalStatus: Int,
        topApp: String, wifiActive: Boolean, bluetoothActive: Boolean,
        brightnessLevel: Int, ramUsageMb: Int,
        allZones: Map<String, Float>, perCoreUsage: List<Float>,
        topProcesses: List<ProcessInfo>, cpuFreqsMHz: List<Float>,
        thermalPowerScore: Float
    ) : this(id, timestamp, batteryTemp, cpuTemp, gpuTemp, skinTemp, boardTemp,
             modemTemp, displayTemp, cpuUsage, batteryLevel, isCharging, thermalStatus,
             topApp, wifiActive, bluetoothActive, brightnessLevel, ramUsageMb) {
        this.allZones         = allZones
        this.perCoreUsage     = perCoreUsage
        this.topProcesses     = topProcesses
        this.cpuFreqsMHz      = cpuFreqsMHz
        this.thermalPowerScore = thermalPowerScore
    }

    var allZones: Map<String, Float>  = emptyMap()
    var cpuFreqsMHz: List<Float>      = emptyList()
    var thermalPowerScore: Float      = 0f
    var perCoreUsage: List<Float>     = emptyList()
    var topProcesses: List<ProcessInfo> = emptyList()
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

// ── Motor v5: estado de potencia según Ley de Moore ─────────────────────────
data class MoorePowerState(
    val powerScore: Float = 0f,         // P=V²·F normalizado 0-100
    val freqsMHz: List<Float> = emptyList(),
    val avgFreqMHz: Float = 0f,
    val maxFreqMHz: Float = 0f,
    val predictedTempRise: Float = 0f,  // °C adicionales esperados en 3 min
    val recommendation: MooreAction = MooreAction.NONE,
    val efficiencyRatio: Float = 1f     // actual vs óptimo (1.0 = perfecto)
)

enum class MooreAction {
    NONE,           // todo OK
    REDUCE_LOAD,    // CPU al límite, bajar carga
    BIG_LITTLE,     // migrar a núcleos eficientes
    WARN_THROTTLE,  // throttle del sistema en ~2 min
    CRITICAL        // reducción urgente
}

