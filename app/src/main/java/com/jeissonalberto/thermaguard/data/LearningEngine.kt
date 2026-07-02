package com.jeissonalberto.thermaguard.data

import android.util.Log
import kotlin.math.abs

object LearningEngine {
    private var neuralBias = 0.1f
    private val historicalData = mutableListOf<Float>()
    private const val MAX_HISTORY = 100

    fun learnFromPattern(temp: Float, usage: Float) {
        historicalData.add(temp)
        if (historicalData.size > MAX_HISTORY) historicalData.removeAt(0)
        
        // Ajuste de sesgo basado en la desviación media
        val average = historicalData.average().toFloat()
        neuralBias += (temp - average) * 0.01f
        Log.d("LearningEngine", "Bias updated to: $neuralBias")
    }

    fun getOptimizedThreshold(baseThreshold: Float): Float {
        // El umbral se auto-ajusta según lo aprendido
        return baseThreshold - (neuralBias * 2.0f)
    }
}
