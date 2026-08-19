package com.jeissonalberto.thermaguard.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalZoneSamplingPolicyTest {
    @Test
    fun hidden_diagnosis_never_reads_kernel_zones() {
        assertFalse(
            shouldRefreshHardwareZones(
                diagnosticsVisible = false,
                nowMs = 60_000L,
                lastReadMs = 0L
            )
        )
    }

    @Test
    fun visible_diagnosis_reads_immediately_and_then_at_interval() {
        assertTrue(shouldRefreshHardwareZones(true, 1_000L, 0L))
        assertFalse(shouldRefreshHardwareZones(true, 15_999L, 1_000L))
        assertTrue(shouldRefreshHardwareZones(true, 16_000L, 1_000L))
    }
}
