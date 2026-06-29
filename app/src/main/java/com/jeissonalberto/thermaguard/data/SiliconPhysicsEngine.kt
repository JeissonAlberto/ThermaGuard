package com.jeissonalberto.thermaguard.data

import kotlin.math.exp

object SiliconPhysicsEngine {
    fun calculateThermalResistance(usage: Float, temp: Float): Float {
        // Ley de enfriamiento de Newton + Curva de saturación de silicio
        return (usage * 0.12f) * exp(temp / 100f)
    }

    fun predictThrottling(temp: Float, threshold: Float = 45f): Boolean {
        return temp >= threshold
    }
}
