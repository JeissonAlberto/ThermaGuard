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

// ════════════════════════════════════════════════════════════════════════════
//  MOTOR v6 — TRES LEYES DEL SILICIO
//  Moore  : P = C·V²·F       → carga térmica bruta del chip
//  Dennard: ΔP/ΔT ∝ V·I·α   → eficiencia de escala (fuga de corriente)
//  Pollack: Perf ∝ √Power    → rendimiento real vs calor generado
//  Amdahl : T_throttle = 1/(s + (1-s)/n) → cuánto aguanta antes de throttle
// ════════════════════════════════════════════════════════════════════════════
data class SiliconAnalysis(
    // ── Moore P = C·V²·F ─────────────────────────────────────────────────
    val moorePower: Float,          // 0-100: carga térmica estimada del chip
    val mooreVoltage: Float,        // voltaje proxy (0.6–1.0 V normalizado)
    val mooreFrequency: Float,      // frecuencia proxy (0.0–1.0)
    // ── Dennard Scaling ──────────────────────────────────────────────────
    val dennardLeakage: Float,      // 0-100: fuga de corriente (calor "gratis" inevitable)
    val dennardEfficiency: Float,   // 0-100: qué tan bien escala la potencia
    val dennardThermalDensity: Float, // °C/cm² estimado
    // ── Pollack's Rule ───────────────────────────────────────────────────
    val pollackPerf: Float,         // rendimiento real 0-100
    val pollackPerfPerWatt: Float,  // eficiencia: rendimiento / potencia
    val pollackWastedHeat: Float,   // calor generado sin rendimiento útil (%)
    // ── Amdahl Thermal ───────────────────────────────────────────────────
    val amdahlThrottleEta: Float,   // 0-100: % de carga hasta throttle
    val amdahlParallelScore: Float, // cuántos núcleos están activos efectivamente
    val amdahlTimeToThrottle: Int,  // segundos estimados hasta throttle (0 = ya throttleando)
    // ── Diagnóstico final ────────────────────────────────────────────────
    val dominantLaw: String,        // qué ley explica mejor el calor ahora
    val recommendation: String,
    val severity: SiliconSeverity
)

enum class SiliconSeverity { OPTIMAL, EFFICIENT, STRESSED, CRITICAL, THERMAL_RUNAWAY }

// ════════════════════════════════════════════════════════════════════════════
//  MODO DE OPERACIÓN — controlado por el usuario
// ════════════════════════════════════════════════════════════════════════════
// ════════════════════════════════════════════════════════════════════════════
//  SENSOR LOG — entrada de log con timestamp y fuente
// ════════════════════════════════════════════════════════════════════════════
data class SensorLog(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,         // THERMAL / CPU / RAM / BATTERY / SENSOR
    val source: String,      // /proc/stat, /sys/class/thermal/..., API
    val field: String,       // nombre del campo
    val rawValue: String,    // valor crudo leído
    val parsedValue: String, // valor interpretado
    val unit: String = "",
    val isEstimated: Boolean = false  // true si no pudo leer y usó fallback
)

enum class OperationMode {
    LEARNING,   // Solo observa y aprende — NO toma acciones automáticas
    AUTO,       // Actúa automáticamente según lo aprendido
    ACTIVE      // Modo máxima intervención — actúa agresivamente
}

// ════════════════════════════════════════════════════════════════════════════
//  RECOMENDACIONES DE HARDWARE Y SOFTWARE (el núcleo de la propuesta de valor)
// ════════════════════════════════════════════════════════════════════════════
enum class CoolingCategory {
    DISPLAY,        // Pantalla
    CONNECTIVITY,   // WiFi, BT, datos móviles
    BACKGROUND,     // Apps en segundo plano
    CHARGING,       // Carga de batería
    PERFORMANCE,    // Rendimiento del CPU/GPU
    ENVIRONMENT,    // Temperatura ambiente, funda, sol
    SYSTEM          // Ajustes del sistema
}

data class CoolingRecommendation(
    val id: String,
    val category: CoolingCategory,
    val title: String,
    val detail: String,
    val impactDegrees: Float,   // cuántos °C puede bajar aplicando esto
    val effort: Int,            // 1=fácil, 2=medio, 3=requiere ajuste
    val isActionable: Boolean = true,  // si el usuario puede hacerlo ahora mismo
    val icon: String = "💡"
)

enum class ThermalLevel(val label: String, val emoji: String) {
    NORMAL("Normal", "🟢"),
    WARM("Tibio", "🟡"),
    HOT("Caliente", "🟠"),
    CRITICAL("Critico", "🔴"),
    EMERGENCY("Emergencia", "🚨")
}

fun Float.toThermalLevel(): ThermalLevel = when {
    this < 40f -> ThermalLevel.NORMAL    // frío / temperatura ambiente
    this < 47f -> ThermalLevel.WARM      // carga normal — esperado en uso
    this < 55f -> ThermalLevel.HOT       // carga alta — vigilar
    this < 65f -> ThermalLevel.CRITICAL  // temperatura peligrosa
    else       -> ThermalLevel.EMERGENCY // emergencia térmica
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
    PROCESS("Proceso activo", "⚙️"),
    SKIN("Carcasa / Piel", "🖐️")
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
