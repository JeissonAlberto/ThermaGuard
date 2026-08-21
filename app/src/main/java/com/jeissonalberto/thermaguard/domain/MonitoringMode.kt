package com.jeissonalberto.thermaguard.domain

/** User-controlled cadence for background and foreground thermal monitoring. */
enum class MonitoringMode(
    val label: String,
    val intervalMinutes: Long,
    val description: String,
    /** Foreground refresh cadence; the UI never polls more frequently than this. */
    val foregroundIntervalMs: Long
) {
    SAVER(
        "AHORRO",
        60L,
        "Menos ejecuciones; conserva alertas cuando Android lo permita.",
        5 * 60 * 1_000L
    ),
    BALANCED(
        "EQUILIBRADO",
        30L,
        "Vigilancia periódica con consumo moderado.",
        2 * 60 * 1_000L
    ),
    PREVENTIVE(
        "PREVENTIVO",
        15L,
        "Monitoreo más frecuente únicamente mientras carga.",
        30 * 1_000L
    );

    companion object {
        const val PREFS_NAME = "therma_monitoring_preferences"
        const val MODE_KEY = "monitoring_mode"

        fun fromStored(value: String?): MonitoringMode =
            values().firstOrNull { it.name == value } ?: BALANCED
    }
}

/** Low battery pauses local persistence, while thermal alerts remain evaluated. */
internal fun shouldPauseNonEssentialWork(levelPercent: Int?, isCharging: Boolean?): Boolean =
    ThermalMonitoringPolicy.shouldPauseNonEssentialWork(levelPercent, isCharging)

/**
 * Policy used by the foreground loop. It deliberately slows, rather than stops,
 * thermal evaluation on a low battery so alert transitions remain observable.
 */
data class ForegroundPollingPolicy(
    val intervalMs: Long,
    val lowBatteryLimited: Boolean,
    val costLimited: Boolean = false
)

internal const val LOW_BATTERY_FOREGROUND_INTERVAL_MS = 15 * 60 * 1_000L

internal fun calculateForegroundPollingPolicy(
    mode: MonitoringMode,
    batteryLevelPercent: Int?,
    isCharging: Boolean?,
    measuredCost: MonitoringCostSample? = null
): ForegroundPollingPolicy = ThermalMonitoringPolicy.foregroundPolling(
    mode,
    batteryLevelPercent,
    isCharging,
    measuredCost
)
