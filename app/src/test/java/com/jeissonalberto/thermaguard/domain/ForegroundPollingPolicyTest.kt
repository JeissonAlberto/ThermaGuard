package com.jeissonalberto.thermaguard.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundPollingPolicyTest {
    @Test
    fun selected_modes_have_progressively_faster_foreground_cadence() {
        assertEquals(5 * 60 * 1_000L, calculateForegroundPollingPolicy(MonitoringMode.SAVER, 80, false).intervalMs)
        assertEquals(2 * 60 * 1_000L, calculateForegroundPollingPolicy(MonitoringMode.BALANCED, 80, false).intervalMs)
        assertEquals(30 * 1_000L, calculateForegroundPollingPolicy(MonitoringMode.PREVENTIVE, 80, true).intervalMs)
    }

    @Test
    fun low_battery_stretches_polling_without_disabling_alert_evaluation() {
        val policy = calculateForegroundPollingPolicy(MonitoringMode.PREVENTIVE, 15, false)

        assertEquals(LOW_BATTERY_FOREGROUND_INTERVAL_MS, policy.intervalMs)
        assertTrue(policy.lowBatteryLimited)
    }

    @Test
    fun charging_at_low_level_keeps_selected_mode_cadence() {
        val policy = calculateForegroundPollingPolicy(MonitoringMode.SAVER, 10, true)

        assertEquals(MonitoringMode.SAVER.foregroundIntervalMs, policy.intervalMs)
        assertFalse(policy.lowBatteryLimited)
    }
}
