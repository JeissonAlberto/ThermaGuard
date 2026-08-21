package com.jeissonalberto.thermaguard.domain

/**
 * Pure, shared decisions for the foreground view model and background worker.
 *
 * Android adapters translate platform values to the stable labels used here;
 * this policy never invents a sensor value when one is unavailable.
 */
internal object ThermalMonitoringPolicy {
    private val alertStatuses = setOf("ALERT", "CRITICAL")
    private val systemRiskStatuses = setOf("SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN")

    data class Decision(
        val status: String,
        val shouldNotify: Boolean,
        val pauseNonEssentialWork: Boolean
    )

    fun evaluate(
        batteryTemperatureCelsius: Float?,
        systemStatus: String?,
        previousStatus: String?,
        batteryLevelPercent: Int?,
        isCharging: Boolean?
    ): Decision {
        val status = classify(batteryTemperatureCelsius, systemStatus)
        return Decision(
            status = status,
            shouldNotify = shouldNotify(previousStatus, status),
            pauseNonEssentialWork = shouldPauseNonEssentialWork(batteryLevelPercent, isCharging)
        )
    }

    fun classify(batteryTemperatureCelsius: Float?, systemStatus: String?): String = when {
        systemStatus in setOf("CRITICAL", "EMERGENCY", "SHUTDOWN") -> "CRITICAL"
        systemStatus == "SEVERE" -> "ALERT"
        batteryTemperatureCelsius == null -> "SENSOR UNAVAILABLE"
        batteryTemperatureCelsius >= 45f -> "CRITICAL"
        batteryTemperatureCelsius >= 40f -> "ALERT"
        else -> "NOMINAL"
    }

    fun shouldNotify(previousStatus: String?, currentStatus: String): Boolean =
        currentStatus in alertStatuses && previousStatus != currentStatus

    fun isSystemThermalRisk(systemStatus: String?): Boolean =
        systemStatus in systemRiskStatuses

    fun shouldPauseNonEssentialWork(levelPercent: Int?, isCharging: Boolean?): Boolean =
        levelPercent != null && levelPercent <= 15 && isCharging != true

    fun foregroundPolling(
        mode: MonitoringMode,
        batteryLevelPercent: Int?,
        isCharging: Boolean?,
        measuredCost: MonitoringCostSample? = null
    ): ForegroundPollingPolicy {
        val lowBatteryLimited = shouldPauseNonEssentialWork(batteryLevelPercent, isCharging)
        val baseIntervalMs = if (lowBatteryLimited) {
            LOW_BATTERY_FOREGROUND_INTERVAL_MS
        } else {
            mode.foregroundIntervalMs
        }
        val costLimited = !lowBatteryLimited && shouldAdaptForegroundPolling(measuredCost)
        return ForegroundPollingPolicy(
            intervalMs = if (costLimited) {
                adaptedForegroundIntervalMs(baseIntervalMs, measuredCost)
            } else {
                baseIntervalMs
            },
            lowBatteryLimited = lowBatteryLimited,
            costLimited = costLimited
        )
    }

    /** Android reports battery temperature in tenths of a degree Celsius. */
    fun batteryTemperatureCelsius(rawTemperature: Int): Float? =
        rawTemperature.takeUnless { it == Int.MIN_VALUE }?.div(10f)
}

// Kept as small package-level seams for existing domain tests and callers.
internal fun thermalEngineStatus(temperature: Float?, systemStatus: String?): String =
    ThermalMonitoringPolicy.classify(temperature, systemStatus)

internal fun shouldNotifyThermalStatus(previous: String?, current: String): Boolean =
    ThermalMonitoringPolicy.shouldNotify(previous, current)

internal fun isSystemThermalRisk(status: String?): Boolean =
    ThermalMonitoringPolicy.isSystemThermalRisk(status)

internal fun batteryTemperatureCelsius(rawTemperature: Int): Float? =
    ThermalMonitoringPolicy.batteryTemperatureCelsius(rawTemperature)
