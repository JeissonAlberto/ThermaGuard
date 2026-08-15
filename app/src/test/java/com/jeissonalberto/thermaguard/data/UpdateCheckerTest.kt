package com.jeissonalberto.thermaguard.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun comparesInstalledVersionWithBuildSuffix() {
        assertTrue(isNewerVersion("v4.5.2", "4.5.1-FINAL"))
        assertFalse(isNewerVersion("v4.5.1", "4.5.1-FINAL"))
    }

    @Test
    fun rejectsMalformedVersionsWithoutClaimingAnUpdate() {
        assertFalse(isNewerVersion("latest", "4.5.1-FINAL"))
        assertFalse(isNewerVersion("v4.5.2", "unknown"))
    }
}
