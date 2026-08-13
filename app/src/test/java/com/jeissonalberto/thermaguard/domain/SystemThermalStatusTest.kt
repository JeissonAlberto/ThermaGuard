package com.jeissonalberto.thermaguard.domain

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemThermalStatusTest {
    @Test
    fun mapsEveryAndroidStatusToAnExplicitLabel() {
        val expected = mapOf(
            PowerManager.THERMAL_STATUS_NONE to "NORMAL",
            PowerManager.THERMAL_STATUS_LIGHT to "LIGHT",
            PowerManager.THERMAL_STATUS_MODERATE to "MODERATE",
            PowerManager.THERMAL_STATUS_SEVERE to "SEVERE",
            PowerManager.THERMAL_STATUS_CRITICAL to "CRITICAL",
            PowerManager.THERMAL_STATUS_EMERGENCY to "EMERGENCY",
            PowerManager.THERMAL_STATUS_SHUTDOWN to "SHUTDOWN"
        )

        expected.forEach { (status, label) ->
            assertEquals(label, systemThermalStatusLabel(status))
        }
    }

    @Test
    fun unknownAndroidStatusDoesNotLookLikeNormal() {
        assertEquals("UNKNOWN", systemThermalStatusLabel(Int.MAX_VALUE))
    }

    @Test
    fun thermalNotificationOnlyFiresWhenEnteringAnAlertState() {
        assertEquals(true, shouldNotifyThermalStatus(null, "ALERT"))
        assertEquals(true, shouldNotifyThermalStatus("ALERT", "CRITICAL"))
        assertEquals(false, shouldNotifyThermalStatus("ALERT", "ALERT"))
        assertEquals(false, shouldNotifyThermalStatus("CRITICAL", "NOMINAL"))
    }

    @Test
    fun preservesValidSubZeroBatteryTemperature() {
        assertEquals(-3.5f, batteryTemperatureCelsius(-35))
    }

    @Test
    fun mapsMissingBatteryTemperatureToNull() {
        assertEquals(null, batteryTemperatureCelsius(Int.MIN_VALUE))
    }
}
