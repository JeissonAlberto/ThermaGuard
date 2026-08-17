package com.jeissonalberto.thermaguard.data

import android.content.Intent
import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryTelemetryTest {
    @Test
    fun readsOnlyAvailableBatteryExtras() {
        val intent = Intent().apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 75)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            putExtra(BatteryManager.EXTRA_VOLTAGE, 4_120)
            putExtra("current_now", 850_000)
        }

        assertEquals(
            BatteryTelemetry(75, true, 4_120, 850_000),
            readBatteryTelemetry(intent)
        )
    }

    @Test
    fun missingOrInvalidExtrasRemainUnavailable() {
        val telemetry = readBatteryTelemetry(Intent().apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 100)
            putExtra(BatteryManager.EXTRA_SCALE, 0)
            putExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            putExtra("current_now", Int.MIN_VALUE)
        })

        assertEquals(null, telemetry.levelPercent)
        assertEquals(null, telemetry.isCharging)
        assertEquals(null, telemetry.voltageMv)
        assertEquals(null, telemetry.currentMicroamps)
    }
}
