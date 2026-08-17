package com.jeissonalberto.thermaguard.data

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryTelemetryTest {
    @Test
    fun readsOnlyAvailableBatteryExtras() {
        assertEquals(
            BatteryTelemetry(75, true, 4_120, 850_000),
            batteryTelemetryFromExtras(
                level = 75,
                scale = 100,
                status = BatteryManager.BATTERY_STATUS_CHARGING,
                voltage = 4_120,
                current = 850_000
            )
        )
    }

    @Test
    fun missingOrInvalidExtrasRemainUnavailable() {
        val telemetry = batteryTelemetryFromExtras(
            level = 100,
            scale = 0,
            status = null,
            voltage = -1,
            current = Int.MIN_VALUE
        )

        assertEquals(null, telemetry.levelPercent)
        assertEquals(null, telemetry.isCharging)
        assertEquals(null, telemetry.voltageMv)
        assertEquals(null, telemetry.currentMicroamps)
    }
}
