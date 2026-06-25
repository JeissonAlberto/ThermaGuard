package com.jeissonalberto.thermaguard.data

data class ThermalSnapshot(
    val batteryTemp: Float = 30f,
    val cpuTemp: Float = 30f,
    val cpuUsage: Float = 0f
)

data class ThermalPrediction(
    val expectedTemp2Min: Float,
    val trendSeverity: Float,
    val timeToThrottle: Int
)

data class DevicePhysicsParams(
    val thermalMassJK: Double,
    val tdpW: Double
)

object SiliconPhysics {
    fun detectDevicePhysicsParams() = DevicePhysicsParams(40.0, 10.0)
    fun predictFuture(snap: ThermalSnapshot, params: DevicePhysicsParams, history: List<ThermalSnapshot>): ThermalPrediction {
        return ThermalPrediction(snap.batteryTemp + 2f, 0.5f, 300)
    }
}
