package com.jeissonalberto.thermaguard.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun comparesInstalledVersionWithBuildSuffix() {
        assertTrue(UpdateChecker.isNewerVersion("v4.5.2", "4.5.1-FINAL"))
        assertFalse(UpdateChecker.isNewerVersion("v4.5.1", "4.5.1-FINAL"))
    }

    @Test
    fun rejectsMalformedVersionsWithoutClaimingAnUpdate() {
        assertFalse(UpdateChecker.isNewerVersion("latest", "4.5.1-FINAL"))
        assertFalse(UpdateChecker.isNewerVersion("v4.5.2", "unknown"))
    }

    @Test
    fun retriesWhenNoCheckExistsOrClockMovedBackwards() {
        val now = 100_000L
        assertTrue(UpdateChecker.isUpdateCheckDue(now, 0L))
        assertTrue(UpdateChecker.isUpdateCheckDue(now, now + 1L))
    }

    @Test
    fun respectsSixHourAutomaticCheckWindow() {
        val now = 100_000L
        val sixHours = 6 * 60 * 60 * 1_000L
        assertFalse(UpdateChecker.isUpdateCheckDue(now, now - sixHours + 1L))
        assertTrue(UpdateChecker.isUpdateCheckDue(now, now - sixHours))
    }
}
