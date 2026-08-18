package com.jeissonalberto.thermaguard.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSettingsTargetTest {
    @Test
    fun exposes_only_user_invoked_safe_destinations() {
        assertEquals("android.settings.APPLICATION_DETAILS_SETTINGS", AndroidSettingsTarget.APP_DETAILS.action)
        assertEquals("android.settings.BATTERY_SAVER_SETTINGS", AndroidSettingsTarget.BATTERY_SAVER.action)
        assertEquals("android.settings.APP_NOTIFICATION_SETTINGS", AndroidSettingsTarget.NOTIFICATIONS.action)
        assertTrue(AndroidSettingsTarget.APP_DETAILS.requiresAppPackage)
        assertTrue(AndroidSettingsTarget.NOTIFICATIONS.requiresAppPackage)
        assertFalse(AndroidSettingsTarget.BATTERY_SAVER.requiresAppPackage)
    }
}
