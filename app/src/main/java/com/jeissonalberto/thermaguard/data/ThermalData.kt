package com.jeissonalberto.thermaguard.data

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.ui.graphics.Color
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "thermal_history")
data class ThermalSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val batteryTemp: Float = 0f,
    val cpuTemp: Float = 0f,
    val batteryLevel: Int? = null,
    val isCharging: Boolean? = null,
    val batteryVoltageMv: Int? = null,
    val batteryCurrentMicroamps: Int? = null
)

enum class ThermalLevel { COOL, NORMAL, WARM, HOT, CRITICAL, EMERGENCY }
enum class OperationMode { AUTO, PERFORMANCE, POWER_SAVE, MANUAL, LEARNING, GAMER, ACTIVE }
object TG {
    val red = Color(0xFFE57373); val amber = Color(0xFFFFB74D); val green = Color(0xFF81C784)
    fun accentFor(level: ThermalLevel) = when(level) { ThermalLevel.COOL, ThermalLevel.NORMAL -> green; ThermalLevel.WARM, ThermalLevel.HOT -> amber; else -> red }
}
data class GovernorConfig(val name: String = "")
fun detectDevicePhysicsParams(): Map<String, Any> = emptyMap()

/**
 * Reads optional battery metadata from Android's sticky battery broadcast.
 * Missing extras remain null; no value is inferred or simulated.
 */
private const val BATTERY_EXTRA_CURRENT_NOW = "current_now"

data class BatteryTelemetry(
    val levelPercent: Int?,
    val isCharging: Boolean?,
    val voltageMv: Int?,
    val currentMicroamps: Int?
)

internal fun batteryTelemetryFromExtras(
    level: Int?,
    scale: Int?,
    status: Int?,
    voltage: Int?,
    current: Int?
): BatteryTelemetry {
    val unavailable = Int.MIN_VALUE
    return BatteryTelemetry(
        levelPercent = if (level != null && scale != null && level >= 0 && scale > 0) {
            (level * 100f / scale).toInt().coerceIn(0, 100)
        } else null,
        isCharging = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        },
        voltageMv = voltage?.takeUnless { it == unavailable || it <= 0 },
        currentMicroamps = current?.takeUnless { it == unavailable }
    )
}

fun readBatteryTelemetry(intent: Intent?): BatteryTelemetry {
    val unavailable = Int.MIN_VALUE
    // The documented "current_now" broadcast extra is not exposed by every SDK stub.
    return batteryTelemetryFromExtras(
        level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, unavailable),
        scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, unavailable),
        status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, unavailable),
        voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, unavailable),
        current = intent?.getIntExtra(BATTERY_EXTRA_CURRENT_NOW, unavailable)
    )
}
