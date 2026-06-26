package com.jeissonalberto.thermaguard.data
import androidx.compose.ui.graphics.Color
enum class ThermalLevel { COOL, NORMAL, WARM, HOT, CRITICAL, EMERGENCY }
enum class OperationMode { AUTO, PERFORMANCE, POWER_SAVE, MANUAL, LEARNING, GAMER, ACTIVE }
object TG {
    val red = Color(0xFFE57373); val amber = Color(0xFFFFB74D); val green = Color(0xFF81C784); val teal = Color(0xFF4DB6AC)
    fun accentFor(level: ThermalLevel) = when(level) { ThermalLevel.COOL, ThermalLevel.NORMAL -> green; ThermalLevel.WARM, ThermalLevel.HOT -> amber; else -> red }
}
data class GovernorConfig(val name: String = "", val maxFreqGHz: Float = 0f, val gpuMaxFreqMHz: Int = 0, val reason: String = "")
fun detectDevicePhysicsParams(): Map<String, Any> = emptyMap()
data class BatteryHealthScore(val score: Int = 0)
data class HourlyDataPoint(val hour: Int = 0, val temp: Float = 0f)
enum class AppTheme { SYSTEM, LIGHT, DARK }
enum class AppLanguage { SPANISH, ENGLISH }
