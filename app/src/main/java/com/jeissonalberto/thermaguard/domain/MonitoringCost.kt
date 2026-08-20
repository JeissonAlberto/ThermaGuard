package com.jeissonalberto.thermaguard.domain

/**
 * Local, opt-in measurement of the foreground monitor's own work.
 *
 * Only elapsed work time and bounded counters are kept. No temperature,
 * device identifier, or network data is part of this measurement.
 */
data class MonitoringCostSample(
    val sampleCount: Int = 0,
    val totalElapsedMs: Long = 0L,
    val maxElapsedMs: Long = 0L
) {
    val averageElapsedMs: Long
        get() = if (sampleCount == 0) 0L else totalElapsedMs / sampleCount

    fun record(elapsedMs: Long): MonitoringCostSample {
        val boundedElapsed = elapsedMs.coerceAtLeast(0L)
        return MonitoringCostSample(
            sampleCount = sampleCount + 1,
            totalElapsedMs = totalElapsedMs + boundedElapsed,
            maxElapsedMs = maxOf(maxElapsedMs, boundedElapsed)
        )
    }
}

internal const val COST_ADAPTATION_MIN_SAMPLES = 5
internal const val COST_ADAPTATION_THRESHOLD_MS = 100L
internal const val COST_ADAPTATION_MAX_INTERVAL_MS = 15 * 60 * 1_000L

/**
 * Stretch foreground polling only after repeated, locally measured slow work.
 * This changes cadence, never CPU/GPU settings, and does not claim a battery
 * percentage saving.
 */
internal fun shouldAdaptForegroundPolling(cost: MonitoringCostSample?): Boolean =
    cost != null &&
        cost.sampleCount >= COST_ADAPTATION_MIN_SAMPLES &&
        cost.averageElapsedMs >= COST_ADAPTATION_THRESHOLD_MS

internal fun adaptedForegroundIntervalMs(
    baseIntervalMs: Long,
    cost: MonitoringCostSample?
): Long = if (shouldAdaptForegroundPolling(cost)) {
    (baseIntervalMs * 2L).coerceAtMost(COST_ADAPTATION_MAX_INTERVAL_MS)
} else {
    baseIntervalMs
}
