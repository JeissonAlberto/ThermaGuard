package com.jeissonalberto.thermaguard.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalMonitoringPolicyTest {
    @Test
    fun evaluate_preserves_unavailable_sensor_and_still_classifies_system_risk() {
        val decision = ThermalMonitoringPolicy.evaluate(
            batteryTemperatureCelsius = null,
            systemStatus = "SEVERE",
            previousStatus = null,
            batteryLevelPercent = null,
            isCharging = null
        )

        assertEquals("ALERT", decision.status)
        assertTrue(decision.shouldNotify)
        assertFalse(decision.pauseNonEssentialWork)
    }

    @Test
    fun evaluate_notifies_only_on_alert_transition() {
        val repeated = ThermalMonitoringPolicy.evaluate(
            batteryTemperatureCelsius = 41f,
            systemStatus = "NORMAL",
            previousStatus = "ALERT",
            batteryLevelPercent = 80,
            isCharging = false
        )
        val escalation = ThermalMonitoringPolicy.evaluate(
            batteryTemperatureCelsius = 46f,
            systemStatus = "NORMAL",
            previousStatus = "ALERT",
            batteryLevelPercent = 80,
            isCharging = false
        )

        assertEquals("ALERT", repeated.status)
        assertFalse(repeated.shouldNotify)
        assertEquals("CRITICAL", escalation.status)
        assertTrue(escalation.shouldNotify)
    }

    @Test
    fun evaluate_rearms_after_an_alert_returns_to_nominal() {
        val cleared = ThermalMonitoringPolicy.evaluate(
            batteryTemperatureCelsius = 35f,
            systemStatus = "NORMAL",
            previousStatus = "ALERT",
            batteryLevelPercent = 80,
            isCharging = false
        )
        val nextAlert = ThermalMonitoringPolicy.evaluate(
            batteryTemperatureCelsius = 41f,
            systemStatus = "NORMAL",
            previousStatus = null,
            batteryLevelPercent = 80,
            isCharging = false
        )

        assertEquals("NOMINAL", cleared.status)
        assertFalse(cleared.shouldNotify)
        assertEquals("ALERT", nextAlert.status)
        assertTrue(nextAlert.shouldNotify)
    }

    @Test
    fun evaluate_pauses_only_non_essential_work_at_low_battery_while_alerts_continue() {
        val decision = ThermalMonitoringPolicy.evaluate(
            batteryTemperatureCelsius = 45f,
            systemStatus = "NORMAL",
            previousStatus = null,
            batteryLevelPercent = 15,
            isCharging = false
        )

        assertEquals("CRITICAL", decision.status)
        assertTrue(decision.shouldNotify)
        assertTrue(decision.pauseNonEssentialWork)
    }
}
