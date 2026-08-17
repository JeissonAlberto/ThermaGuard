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
    fun onlyElevatedSystemStatesAreThermalRisks() {
        assertEquals(false, isSystemThermalRisk(null))
        assertEquals(false, isSystemThermalRisk("NORMAL"))
        assertEquals(false, isSystemThermalRisk("MODERATE"))
        assertEquals(true, isSystemThermalRisk("SEVERE"))
        assertEquals(true, isSystemThermalRisk("CRITICAL"))
        assertEquals(true, isSystemThermalRisk("EMERGENCY"))
        assertEquals(true, isSystemThermalRisk("SHUTDOWN"))
    }

    @Test
    fun historyStorageErrorClearsAfterSuccessfulCleanupWithoutSensorSample() {
        assertEquals(true, historyStorageWriteSucceeded(null, true))
        assertEquals(true, historyStorageWriteSucceeded(true, true))
        assertEquals(false, historyStorageWriteSucceeded(false, true))
        assertEquals(false, historyStorageWriteSucceeded(true, false))
    }

    @Test
    fun historyRetentionIsBoundedToSupportedChoices() {
        assertEquals(6, normalizeHistoryRetentionHours(6))
        assertEquals(24, normalizeHistoryRetentionHours(24))
        assertEquals(72, normalizeHistoryRetentionHours(72))
        assertEquals(24, normalizeHistoryRetentionHours(0))
        assertEquals(24, normalizeHistoryRetentionHours(999))
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
