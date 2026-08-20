package com.jeissonalberto.thermaguard.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringCostTest {
    @Test
    fun aggregates_only_elapsed_work_and_exposes_average_and_max() {
        val cost = MonitoringCostSample().record(40).record(160)

        assertEquals(2, cost.sampleCount)
        assertEquals(100L, cost.averageElapsedMs)
        assertEquals(160L, cost.maxElapsedMs)
    }

    @Test
    fun slow_work_requires_repeated_samples_before_adapting() {
        val fourSlowSamples = (1..4).fold(MonitoringCostSample()) { value, _ -> value.record(200) }
        val fiveSlowSamples = fourSlowSamples.record(200)

        assertFalse(shouldAdaptForegroundPolling(fourSlowSamples))
        assertTrue(shouldAdaptForegroundPolling(fiveSlowSamples))
        assertEquals(
            MonitoringMode.BALANCED.foregroundIntervalMs * 2,
            adaptedForegroundIntervalMs(MonitoringMode.BALANCED.foregroundIntervalMs, fiveSlowSamples)
        )
    }

    @Test
    fun normal_work_does_not_change_selected_cadence() {
        val cost = (1..10).fold(MonitoringCostSample()) { value, _ -> value.record(20) }

        val policy = calculateForegroundPollingPolicy(
            MonitoringMode.PREVENTIVE,
            batteryLevelPercent = 80,
            isCharging = true,
            measuredCost = cost
        )

        assertEquals(MonitoringMode.PREVENTIVE.foregroundIntervalMs, policy.intervalMs)
        assertFalse(policy.costLimited)
    }

    @Test
    fun low_battery_limit_has_priority_over_cost_adaptation() {
        val cost = (1..10).fold(MonitoringCostSample()) { value, _ -> value.record(200) }

        val policy = calculateForegroundPollingPolicy(
            MonitoringMode.PREVENTIVE,
            batteryLevelPercent = 10,
            isCharging = false,
            measuredCost = cost
        )

        assertEquals(LOW_BATTERY_FOREGROUND_INTERVAL_MS, policy.intervalMs)
        assertFalse(policy.costLimited)
    }
}
