package com.jeissonalberto.thermaguard.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ThermalStatusTest {
    @Test
    fun systemSevereStateRaisesNominalBatteryToAlert() {
        assertEquals("ALERT", thermalEngineStatus(35f, "SEVERE"))
    }

    @Test
    fun criticalSystemStateTakesPriorityOverBatteryReading() {
        assertEquals("CRITICAL", thermalEngineStatus(35f, "CRITICAL"))
    }

    @Test
    fun batteryThresholdsRemainEffectiveWithoutSystemRisk() {
        assertEquals("ALERT", thermalEngineStatus(40f, "NORMAL"))
        assertEquals("CRITICAL", thermalEngineStatus(45f, "NORMAL"))
        assertEquals("SENSOR UNAVAILABLE", thermalEngineStatus(null, "NORMAL"))
    }
}
