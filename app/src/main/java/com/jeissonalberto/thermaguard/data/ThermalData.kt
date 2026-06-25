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
    val cpuUsage: Float = 0f,
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val topApp: String = ""
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
    val severity: SiliconSeverity = SiliconSeverity.OPTIMAL
)

enum class SiliconSeverity { OPTIMAL, NOMINAL, STRESSED, CRITICAL, DAMAGING }

data class CoolingRecommendation(
    val icon: String,
    val title: String,
    val detail: String,
    val impactDegrees: Float
)

data class AutoAction(
    val timestamp: Long,
    val title: String,
    val description: String,
    val trigger: String
)

data class ComponentDiagnosis(
    val component: String,
    val health: Int,
    val status: String,
    val temperature: Float,
    val risk: String
)

data class GameModeState(val isActive: Boolean = false)
data class SafeChargeState(val isCharging: Boolean = false)
enum class OperationMode { AUTO, PERFORMANCE, POWER_SAVE, MANUAL }
