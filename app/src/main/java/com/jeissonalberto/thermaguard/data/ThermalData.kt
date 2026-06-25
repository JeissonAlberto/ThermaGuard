package com.jeissonalberto.thermaguard.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.compose.ui.graphics.Color

@Entity(tableName = "thermal_history")
data class ThermalSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val batteryTemp: Float = 0f,
    val cpuTemp: Float = 0f,
    val gpuTemp: Float = 0f,
    val cpuUsage: Float = 0f,
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val topApp: String = "",
    val modemTemp: Float = 0f,
    val skinTemp: Float = 0f,
    val boardTemp: Float = 0f,
    val displayTemp: Float = 0f,
    val wifiActive: Boolean = false,
    val bluetoothActive: Boolean = false,
    val brightnessLevel: Int = 0,
    val ramUsageMb: Int = 0
)

data class SiliconAnalysis(
    val conductionFlux_W: Float = 0f,
    val convectionLoss_W: Float = 0f,
    val radiationLoss_W: Float = 0f,
    val netHeatAccumulation_W: Float = 0f,
    val dynamicPower_W: Float = 0f,
    val leakagePower_W: Float = 0f,
    val totalSocPower_W: Float = 0f,
    val joulePmicHeat_W: Float = 0f,
    val dennardEfficiency: Float = 1.0f,
    val pollackPerfPerWatt: Float = 1.0f,
    val pollackWastedHeat: Float = 0f,
    val amdahlThrottleEta: Float = 0f,
    val amdahlParallelScore: Float = 1.0f,
    val amdahlTimeToThrottle: Int = 999,
    val throttlePct: Int = 0,
    val performanceRatio: Float = 1f,
    val mttfHours: Double = 100000.0,
    val degradationIndex: Double = 0.0,
    val thermalResistance_KW: Float = 0f,
    val thermalTimeConst_s: Float = 0f,
    val gpuThrottlePct: Int = 0,
    val recommendedGovernor: String = "schedutil",
    val recommendedMaxFreqGHz: Float = 3.0f,
    val cpuAffinityMask: Int = 0xFF,
    val summaryLines: List<String> = emptyList(),
    val dominantLaw: String = "Moore",
    val recommendation: String = "Optimal",
    val severity: SiliconSeverity = SiliconSeverity.OPTIMAL,
    val pollackPerf: Float = 1.0f,
    val dennardLeakage: Float = 0f,
    val dennardThermalDensity: Float = 0f,
    val moorePower: Float = 0f
)

enum class SiliconSeverity { 
    OPTIMAL, NOMINAL, STRESSED, CRITICAL, DAMAGING;
    companion object {
        val THERMAL_RUNAWAY = DAMAGING
    }
}

data class CoolingRecommendation(
    val icon: String = "❄️", val title: String = "", val detail: String = "", val impactDegrees: Float = 0f
)

data class AutoAction(
    val timestamp: Long = 0L, val title: String = "", val description: String = "", val trigger: String = "",
    val effort: Int = 1
)

data class ComponentDiagnosis(
    val component: String = "", val health: Int = 100, val status: String = "", 
    val temperature: Float = 0f, val risk: String = "",
    val label: String = "", val temp: Float = 0f, val advice: String = "", val cause: String = ""
)

data class GameModeState(
    val isActive: Boolean = false, 
    val detectedGame: String = "None"
)

data class SafeChargeState(
    val isCharging: Boolean = false,
    val isOverheating: Boolean = false,
    val recommendation: String = "Normal",
    val chargingTemp: Float = 0f
)

enum class OperationMode { 
    AUTO, PERFORMANCE, POWER_SAVE, MANUAL, LEARNING;
    companion object {
        val EFFICIENT = POWER_SAVE
        val ACTIVE = AUTO
        val GAMER = PERFORMANCE
    }
}

data class SensorLog(
    val timestamp: Long = 0L, val tag: String = "", val field: String = "", val source: String = "",
    val rawValue: String = "", val parsedValue: Float = 0f, val unit: String = "", val isEstimated: Boolean = false,
    val label: String = ""
)

enum class AppTheme { SYSTEM, LIGHT, DARK }
enum class AppLanguage(val code: String, val label: String) {
    SPANISH("es", "Español"), ENGLISH("en", "English")
}

enum class ThermalLevel { COOL, NORMAL, WARM, HOT, CRITICAL, EMERGENCY }

fun Float.toThermalLevel(): ThermalLevel = when {
    this < 33f -> ThermalLevel.COOL
    this < 37f -> ThermalLevel.NORMAL
    this < 40f -> ThermalLevel.WARM
    this < 43f -> ThermalLevel.HOT
    this < 46f -> ThermalLevel.CRITICAL
    else -> ThermalLevel.EMERGENCY
}

object TG {
    val red = Color(0xFFE57373)
    val amber = Color(0xFFFFB74D)
    val green = Color(0xFF81C784)
    fun accentFor(level: ThermalLevel) = when(level) {
        ThermalLevel.COOL -> green
        ThermalLevel.NORMAL -> green
        ThermalLevel.WARM -> amber
        ThermalLevel.HOT -> amber
        else -> red
    }
}

data class ComponentStatus(val label: String = "") {
    companion object {
        val OPTIMAL = ComponentStatus("Óptimo")
        val NOMINAL = ComponentStatus("Nominal")
        val STRESSED = ComponentStatus("Estresado")
        val CRITICAL = ComponentStatus("Crítico")
        val HOT = ComponentStatus("Caliente")
        val WARM = ComponentStatus("Tibio")
    }
}

enum class ThermalComponent { 
    BATTERY, CPU, GPU, MODEM, SKIN, BOARD, DISPLAY, PROCESS;
    companion object {
        val PROCESSOR = CPU
        val CORE = CPU
    }
}

data class HeatCause(
    val title: String = "", 
    val description: String = "", 
    val severity: Int = 1,
    val label: String = ""
)

data class LearnedProfile(
    val samplesCollected: Int = 0,
    val baselineTemp: Float = 30f,
    val baselineCpu: Float = 0f,
    val averageTemp: Float = 30f,
    val averageCpu: Float = 0f,
    val maxRecordedTemp: Float = 30f,
    val minRecordedTemp: Float = 30f,
    val tempAnomaly: Float = 0f,
    val isAnomaly: Boolean = false,
    val trend: TempTrend = TempTrend.STABLE,
    val likelyCause: LearnedCause = LearnedCause.UNKNOWN,
    val personalRisk: RiskLevel = RiskLevel.NORMAL,
    val consecutiveHotReadings: Int = 0,
    val chargingHeatPct: Float = 0f,
    val highCpuHeatPct: Float = 0f,
    val dynamicThreshold: Float = 40f,
    val topHeatApp: String = "",
    val topHeatAppScore: Float = 0f,
    val hourAnomaly: Float? = 0f,
    val expectedThisHour: Float? = 0f,
    val heatSessionsToday: Int = 0,
    val avgCooldownMinutes: Float = 5f,
    val riskScore: Int = 0
)

enum class TempTrend { STABLE, RISING, RISING_FAST }
enum class LearnedCause { UNKNOWN, CHARGING_HABIT, HIGH_CPU_APPS, BACKGROUND_DRAIN }
enum class RiskLevel { NORMAL, LOW, MEDIUM, HIGH, CRITICAL }

data class TempPrediction(
    val predictedTemp: Float = 0f,
    val confidence: PredictionConfidence = PredictionConfidence.MEDIUM,
    val trendText: String = "",
    val slope: Float = 0f
)
enum class PredictionConfidence { LOW, MEDIUM, HIGH }

data class BatteryHealthScore(
    val score: Int = 100,
    val level: String = "Good",
    val factors: List<String> = emptyList()
)

data class HourlyDataPoint(val hour: Int, val avgTemp: Float)

data class SmartTip(
    val icon: String = "",
    val title: String = "",
    val detail: String = "",
    val priority: Int = 1
)
