package com.jeissonalberto.thermaguard.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerPolicyTest {
    @Test
    fun low_battery_without_charging_pauses_non_essential_work() {
        assertTrue(shouldPauseNonEssentialWork(15, false))
        assertTrue(shouldPauseNonEssentialWork(5, null))
    }

    @Test
    fun charging_or_unknown_level_does_not_pause_by_this_policy() {
        assertFalse(shouldPauseNonEssentialWork(15, true))
        assertFalse(shouldPauseNonEssentialWork(null, false))
    }
}
