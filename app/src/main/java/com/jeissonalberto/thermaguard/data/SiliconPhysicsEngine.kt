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

    object PhysicsConst {
        const val STEFAN_SIGMA = 5.670374419e-8
        const val ACTIVATION_EA = 0.7
        const val AMBIENT_TEMP_K   = 298.15
    }

    private var _cachedDeviceParams: DevicePhysicsParams? = null
    private var _cacheTimestamp: Long = 0L
    private const val DEVICE_PARAMS_TTL_MS = 5 * 60 * 1000L

    fun detectDevicePhysicsParams(): DevicePhysicsParams {
        val now = System.currentTimeMillis()
        _cachedDeviceParams?.takeIf { now - _cacheTimestamp < DEVICE_PARAMS_TTL_MS }?.let { return it }

        val p = HardwareProfiler.getProfile()
        val peakKhz = p.cpuClusters.maxOfOrNull { it.maxFreqKhz } ?: 3_000_000L
        val peakGhz = peakKhz / 1_000_000.0
        val tdp = when {
            peakGhz >= 3.2 -> 12.0
            peakGhz >= 3.0 -> 10.5
            peakGhz >= 2.5 -> 9.0
            peakGhz >= 2.0 -> 7.0
            else           -> 5.5
        }
        val dieArea = when {
            p.cpuCores >= 8 && peakGhz >= 3.0 -> 78e-6 * 78e-6
            p.cpuCores >= 8                    -> 70e-6 * 70e-6
            p.cpuCores >= 6                    -> 60e-6 * 60e-6
            else                               -> 50e-6 * 50e-6
        }
        val thermalMass = when {
            p.cpuCores >= 8 && peakGhz >= 3.0 -> 45.0
            p.cpuCores >= 8                    -> 40.0
            else                               -> 35.0
        }
        return DevicePhysicsParams(dieArea, 0.006, thermalMass, tdp, peakGhz).also {
            _cachedDeviceParams = it
            _cacheTimestamp = now
        }
    }

    fun predictFuture(snap: ThermalSnapshot, params: DevicePhysicsParams, history: List<ThermalSnapshot>): ThermalPrediction {
        if (history.size < 5) return ThermalPrediction(snap.batteryTemp, 0f, 999)
        
        val powerIn = (snap.cpuUsage / 100f) * params.tdpW
        val deltaT = (snap.batteryTemp - 25f).coerceAtLeast(0f)
        val powerOut = deltaT / 5.0f // R_theta simplificada
        
        val netPower = powerIn - powerOut
        val projectedRise = (netPower / params.thermalMassJK) * 120f
        val predicted = snap.batteryTemp + projectedRise.toFloat()
        
        val recent = history.takeLast(5)
        val slope = (recent.last().batteryTemp - recent.first().batteryTemp) / (recent.size * 5) // C/sec
        val severity = (slope * 10).coerceIn(0f, 1f)
        
        return ThermalPrediction(
            expectedTemp2Min = predicted,
            trendSeverity    = severity,
            timeToThrottle   = if (slope <= 0) 999 else ((42f - snap.batteryTemp) / slope).toInt().coerceAtLeast(0)
        )
    }
}
