package com.jeissonalberto.thermaguard.data
import androidx.compose.ui.graphics.Color
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "thermal_history")
data class ThermalSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val batteryTemp: Float = 0f,
    val cpuTemp: Float = 0f
)

enum class ThermalLevel { COOL, NORMAL, WARM, HOT, CRITICAL, EMERGENCY }
enum class OperationMode { AUTO, PERFORMANCE, POWER_SAVE, MANUAL, LEARNING, GAMER, ACTIVE }
object TG {
    val red = Color(0xFFE57373); val amber = Color(0xFFFFB74D); val green = Color(0xFF81C784)
    fun accentFor(level: ThermalLevel) = when(level) { ThermalLevel.COOL, ThermalLevel.NORMAL -> green; ThermalLevel.WARM, ThermalLevel.HOT -> amber; else -> red }
}
data class GovernorConfig(val name: String = "")
fun detectDevicePhysicsParams(): Map<String, Any> = emptyMap()
