package com.jeissonalberto.thermaguard.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

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
    val severity: SiliconSeverity = SiliconSeverity.OPTIMAL
)

enum class SiliconSeverity { OPTIMAL, NOMINAL, STRESSED, CRITICAL, DAMAGING }

data class CoolingRecommendation(
    val icon: String, val title: String, val detail: String, val impactDegrees: Float
)

data class AutoAction(
    val timestamp: Long, val title: String, val description: String, val trigger: String
)

data class ComponentDiagnosis(
    val component: String, val health: Int, val status: String, val temperature: Float, val risk: String
)

data class GameModeState(val isActive: Boolean = false)
data class SafeChargeState(val isCharging: Boolean = false)

enum class OperationMode { 
    AUTO, PERFORMANCE, POWER_SAVE, MANUAL, LEARNING;
    companion object {
        val EFFICIENT = POWER_SAVE
        val ACTIVE = AUTO
        val GAMER = PERFORMANCE
    }
}

data class SensorLog(
    val timestamp: Long, val tag: String, val field: String, val source: String,
    val rawValue: String, val parsedValue: Float, val unit: String, val isEstimated: Boolean
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
    val red = androidx.compose.ui.graphics.Color(0xFFE57373)
    val amber = androidx.compose.ui.graphics.Color(0xFFFFB74D)
    val green = androidx.compose.ui.graphics.Color(0xFF81C784)
    fun accentFor(level: ThermalLevel) = when(level) {
        ThermalLevel.COOL -> green
        ThermalLevel.NORMAL -> green
        ThermalLevel.WARM -> amber
        ThermalLevel.HOT -> amber
        else -> red
    }
}
