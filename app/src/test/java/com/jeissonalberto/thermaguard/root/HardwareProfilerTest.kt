package com.jeissonalberto.thermaguard.root

import org.junit.Assert.assertEquals
import org.junit.Test

class HardwareProfilerTest {
    @Test
    fun convertsKernelMilliCelsiusIncludingSubZeroValues() {
        assertEquals(45.0f, thermalZoneTemperatureCelsius(45_000))
        assertEquals(-0.5f, thermalZoneTemperatureCelsius(-500))
    }

    @Test
    fun preservesSmallNonNegativeValuesAlreadyReportedInCelsius() {
        assertEquals(85.0f, thermalZoneTemperatureCelsius(85))
        assertEquals(1.0f, thermalZoneTemperatureCelsius(1_000))
    }
}
