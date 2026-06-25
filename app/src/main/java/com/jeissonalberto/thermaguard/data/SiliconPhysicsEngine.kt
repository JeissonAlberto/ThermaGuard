package com.jeissonalberto.thermaguard.data

import kotlin.math.*
import com.jeissonalberto.thermaguard.root.HardwareProfiler

data class ThermalPrediction(
    val expectedTemp2Min: Float,
    val trendSeverity: Float,
    val timeToThrottle: Int
)

data class DevicePhysicsParams(
    val dieAreaM2:     Double,
    val chassisAreaM2: Double,
    val thermalMassJK: Double,
    val tdpW:          Double,
    val clockGhzMax:   Double
)

object SiliconPhysics {

    fun detectDevicePhysicsParams(): DevicePhysicsParams {
        val p = HardwareProfiler.getProfile()
        val peakKhz = p.cpuClusters.maxOfOrNull { it.maxFreqKhz } ?: 3_000_000L
        val peakGhz = peakKhz / 1_000_000.0
        val tdp = if (peakGhz >= 3.0) 10.5 else 7.0
        return DevicePhysicsParams(70e-6 * 70e-6, 0.006, 40.0, tdp, peakGhz)
    }

    fun predictFuture(snap: ThermalSnapshot, params: DevicePhysicsParams, history: List<ThermalSnapshot>): ThermalPrediction {
        if (history.size < 5) return ThermalPrediction(snap.batteryTemp, 0f, 999)
        val powerIn = (snap.cpuUsage / 100f) * params.tdpW
        val deltaT = (snap.batteryTemp - 25f).coerceAtLeast(0f)
        val powerOut = deltaT / 5.0f 
        val netPower = powerIn - powerOut
        val projectedRise = (netPower / params.thermalMassJK) * 120f
        val predicted = snap.batteryTemp + projectedRise.toFloat()
        val recent = history.takeLast(5)
        val slope = (recent.last().batteryTemp - recent.first().batteryTemp) / (recent.size * 5)
        return ThermalPrediction(predicted, (slope * 10).coerceIn(0f, 1f), if (slope <= 0) 999 else ((42f - snap.batteryTemp) / slope).toInt().coerceAtLeast(0))
    }
}
