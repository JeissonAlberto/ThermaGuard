package com.jeissonalberto.thermaguard.data

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * ThermaGuard Learning Engine v2.0
 * Motor de aprendizaje adaptativo avanzado con:
 * - Prediccion de temperatura (regresion lineal simple sobre ventana de 5 puntos)
 * - Deteccion de ciclos horarios (patron por hora del dia)
 * - Score de salud de bateria
 * - Correlacion app-temperatura
 * - Umbral dinamico personalizado
 */
class ThermalLearningEngine(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("thermaguard_learning_v2", Context.MODE_PRIVATE)

    // ---- Baseline ----
    private var baselineTemp: Float
        get() = prefs.getFloat("baseline_temp", 32f)
        set(v) { prefs.edit().putFloat("baseline_temp", v).apply() }
    private var baselineCpu: Float
        get() = prefs.getFloat("baseline_cpu", 15f)
        set(v) { prefs.edit().putFloat("baseline_cpu", v).apply() }
    private var sampleCount: Int
        get() = prefs.getInt("sample_count", 0)
        set(v) { prefs.edit().putInt("sample_count", v).apply() }
    private var totalTempSum: Float
        get() = prefs.getFloat("total_temp_sum", 0f)
        set(v) { prefs.edit().putFloat("total_temp_sum", v).apply() }
    private var totalCpuSum: Float
        get() = prefs.getFloat("total_cpu_sum", 0f)
        set(v) { prefs.edit().putFloat("total_cpu_sum", v).apply() }
    private var maxRecordedTemp: Float
        get() = prefs.getFloat("max_recorded_temp", 0f)
        set(v) { prefs.edit().putFloat("max_recorded_temp", v).apply() }
    private var minRecordedTemp: Float
        get() = prefs.getFloat("min_recorded_temp", 60f)
        set(v) { prefs.edit().putFloat("min_recorded_temp", v).apply() }

    // ---- Patron de calor ----
    private var chargingHeatEvents: Int
        get() = prefs.getInt("charging_heat_events", 0)
        set(v) { prefs.edit().putInt("charging_heat_events", v).apply() }
    private var highCpuHeatEvents: Int
        get() = prefs.getInt("high_cpu_heat_events", 0)
        set(v) { prefs.edit().putInt("high_cpu_heat_events", v).apply() }
    private var idleHeatEvents: Int
        get() = prefs.getInt("idle_heat_events", 0)
        set(v) { prefs.edit().putInt("idle_heat_events", v).apply() }
    private var consecutiveHotReadings: Int
        get() = prefs.getInt("consec_hot", 0)
        set(v) { prefs.edit().putInt("consec_hot", v).apply() }

    // ---- Ventana de prediccion (ultimas 6 lecturas) ----
    private var windowTemps: String
        get() = prefs.getString("window_temps", "") ?: ""
        set(v) { prefs.edit().putString("window_temps", v).apply() }
    private val WINDOW_SIZE = 6

    // ---- Ciclos horarios (24 slots, temp promedio por hora) ----
    private fun getHourAvg(hour: Int): Float = prefs.getFloat("hour_avg_$hour", -1f)
    private fun setHourAvg(hour: Int, v: Float) { prefs.edit().putFloat("hour_avg_$hour", v).apply() }
    private fun getHourCount(hour: Int): Int = prefs.getInt("hour_count_$hour", 0)
    private fun setHourCount(hour: Int, v: Int) { prefs.edit().putInt("hour_count_$hour", v).apply() }

    // ---- Correlacion app-temperatura ----
    private fun getAppHeatScore(app: String): Float = prefs.getFloat("app_heat_$app", 0f)
    private fun setAppHeatScore(app: String, v: Float) { prefs.edit().putFloat("app_heat_$app", v).apply() }
    private var topHeatApp: String
        get() = prefs.getString("top_heat_app", "") ?: ""
        set(v) { prefs.edit().putString("top_heat_app", v).apply() }
    private var topHeatAppScore: Float
        get() = prefs.getFloat("top_heat_app_score", 0f)
        set(v) { prefs.edit().putFloat("top_heat_app_score", v).apply() }

    // ---- Salud de bateria ----
    private var totalHotMinutes: Int
        get() = prefs.getInt("total_hot_minutes", 0)
        set(v) { prefs.edit().putInt("total_hot_minutes", v).apply() }
    private var totalCriticalMinutes: Int
        get() = prefs.getInt("total_critical_minutes", 0)
        set(v) { prefs.edit().putInt("total_critical_minutes", v).apply() }

    // ---- Umbral dinamico ----
    private var dynamicThreshold: Float
        get() = prefs.getFloat("dynamic_threshold", 43f)
        set(v) { prefs.edit().putFloat("dynamic_threshold", v).apply() }

    // ========== APRENDIZAJE PRINCIPAL ==========

    fun learn(snapshot: ThermalSnapshot): LearnedProfile {
        val n = sampleCount + 1
        sampleCount = n
        totalTempSum += snapshot.batteryTemp
        totalCpuSum += snapshot.cpuUsage

        // EMA adaptativo: rapido al principio, estable despues
        val alpha = when {
            n < 10  -> 0.3f
            n < 30  -> 0.15f
            n < 100 -> 0.08f
            else    -> 0.03f
        }
        baselineTemp = baselineTemp * (1 - alpha) + snapshot.batteryTemp * alpha
        baselineCpu  = baselineCpu  * (1 - alpha) + snapshot.cpuUsage  * alpha

        if (snapshot.batteryTemp > maxRecordedTemp) maxRecordedTemp = snapshot.batteryTemp
        if (snapshot.batteryTemp < minRecordedTemp) minRecordedTemp = snapshot.batteryTemp

        // Ventana de prediccion
        updateWindow(snapshot.batteryTemp)

        // Ciclo horario
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val prevAvg = getHourAvg(hour)
        val prevCount = getHourCount(hour)
        if (prevAvg < 0f) {
            setHourAvg(hour, snapshot.batteryTemp)
            setHourCount(hour, 1)
        } else {
            val newAvg = prevAvg * (prevCount.toFloat() / (prevCount + 1)) +
                         snapshot.batteryTemp * (1f / (prevCount + 1))
            setHourAvg(hour, newAvg)
            setHourCount(hour, prevCount + 1)
        }

        // Correlacion app-temp
        if (snapshot.topApp.isNotEmpty() && snapshot.batteryTemp >= 38f) {
            val prev = getAppHeatScore(snapshot.topApp)
            val score = prev * 0.85f + snapshot.batteryTemp * 0.15f
            setAppHeatScore(snapshot.topApp, score)
            if (score > topHeatAppScore) {
                topHeatApp = snapshot.topApp
                topHeatAppScore = score
            }
        }

        // Patrones de calor
        if (snapshot.batteryTemp >= 40f) {
            when {
                snapshot.isCharging        -> chargingHeatEvents++
                snapshot.cpuUsage > 60f    -> highCpuHeatEvents++
                snapshot.cpuUsage < 20f    -> idleHeatEvents++
            }
            consecutiveHotReadings++
            totalHotMinutes += (ThermalMonitorIntervalMin)
        } else {
            consecutiveHotReadings = 0
        }
        if (snapshot.batteryTemp >= 45f) totalCriticalMinutes += ThermalMonitorIntervalMin

        // Umbral dinamico: baselineTemp + desviacion personalizada
        val deviation = if (n > 20) computeStdDevEstimate() else 8f
        dynamicThreshold = (baselineTemp + deviation * 1.5f).coerceIn(38f, 50f)

        return buildProfile(snapshot)
    }

    private fun updateWindow(temp: Float) {
        val list = windowTemps.split(",").filter { it.isNotEmpty() }.mapNotNull { it.toFloatOrNull() }.toMutableList()
        list.add(temp)
        if (list.size > WINDOW_SIZE) list.removeAt(0)
        windowTemps = list.joinToString(",")
    }

    private fun getWindow(): List<Float> =
        windowTemps.split(",").filter { it.isNotEmpty() }.mapNotNull { it.toFloatOrNull() }

    private fun computeStdDevEstimate(): Float {
        val window = getWindow()
        if (window.size < 3) return 8f
        val mean = window.average().toFloat()
        val variance = window.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance).coerceAtLeast(2f)
    }

    // ========== PREDICCION ==========

    fun predictNextTemp(): TempPrediction {
        val window = getWindow()
        if (window.size < 3) return TempPrediction(0f, PredictionConfidence.LOW, "Recopilando datos...")

        // Regresion lineal simple sobre la ventana
        val n = window.size
        val xMean = (n - 1) / 2.0
        val yMean = window.average()
        var num = 0.0; var den = 0.0
        for (i in window.indices) {
            num += (i - xMean) * (window[i] - yMean)
            den += (i - xMean) * (i - xMean)
        }
        val slope = if (den != 0.0) (num / den).toFloat() else 0f
        val intercept = (yMean - slope * xMean).toFloat()
        val predicted = intercept + slope * n  // siguiente punto

        val confidence = when {
            window.size >= WINDOW_SIZE && abs(slope) < 0.5f -> PredictionConfidence.HIGH
            window.size >= 4 -> PredictionConfidence.MEDIUM
            else -> PredictionConfidence.LOW
        }

        val trend = when {
            slope > 1.5f  -> "🔺 Subira ~${slope.toInt()}C por lectura — RIESGO"
            slope > 0.3f  -> "📈 Tendencia al alza (+${String.format("%.1f",slope)}C)"
            slope < -0.3f -> "📉 Enfriandose (${String.format("%.1f",slope)}C)"
            else          -> "➡️ Estable"
        }

        return TempPrediction(predicted.coerceIn(20f, 80f), confidence, trend, slope)
    }

    // ========== SALUD DE BATERIA ==========

    fun computeBatteryHealthScore(): BatteryHealthScore {
        val n = sampleCount
        if (n < 5) return BatteryHealthScore(100, "Sin datos suficientes aun", emptyList())

        var score = 100

        // Penalizar por minutos en zona caliente (>40C) — cada 60 min = -1 punto
        val hotPenalty = (totalHotMinutes / 60).coerceAtMost(20)
        score -= hotPenalty

        // Penalizar por minutos en zona critica (>45C) — cada 30 min = -2 puntos
        val critPenalty = (totalCriticalMinutes / 30 * 2).coerceAtMost(30)
        score -= critPenalty

        // Penalizar si temp maxima fue muy alta
        val maxTempPenalty = when {
            maxRecordedTemp >= 55f -> 15
            maxRecordedTemp >= 50f -> 10
            maxRecordedTemp >= 45f -> 5
            else -> 0
        }
        score -= maxTempPenalty

        score = score.coerceIn(0, 100)

        val level = when {
            score >= 85 -> "Excelente 🟢"
            score >= 70 -> "Buena 🟡"
            score >= 50 -> "Regular 🟠"
            else        -> "Degradada 🔴"
        }

        val tips = mutableListOf<String>()
        if (hotPenalty > 5)  tips.add("Redujo por ${totalHotMinutes} min en zona caliente")
        if (critPenalty > 0) tips.add("${totalCriticalMinutes} min en zona critica registrados")
        if (maxRecordedTemp >= 45f) tips.add("Pico de ${maxRecordedTemp.toInt()}C registrado")
        if (score >= 85) tips.add("Habitos de temperatura saludables")

        return BatteryHealthScore(score, level, tips)
    }

    // ========== PERFIL HORARIO ==========

    fun getHourlyProfile(): List<HourlyDataPoint> {
        return (0..23).mapNotNull { h ->
            val avg = getHourAvg(h)
            if (avg >= 0f) HourlyDataPoint(h, avg) else null
        }
    }

    fun getCurrentHourExpected(): Float? {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val avg = getHourAvg(h)
        return if (avg >= 0f) avg else null
    }

    // ========== PERFIL COMPLETO ==========

    fun buildProfile(snapshot: ThermalSnapshot): LearnedProfile {
        val n = sampleCount
        val avgTemp = if (n > 0) totalTempSum / n else snapshot.batteryTemp
        val avgCpu  = if (n > 0) totalCpuSum  / n else snapshot.cpuUsage
        val tempAnomaly = snapshot.batteryTemp - baselineTemp
        val isAnomaly = tempAnomaly > 6f

        // Comparar con ciclo esperado
        val expectedThisHour = getCurrentHourExpected()
        val hourAnomaly = expectedThisHour?.let { snapshot.batteryTemp - it }

        val trend = when {
            consecutiveHotReadings >= 3 -> TempTrend.RISING_FAST
            consecutiveHotReadings >= 1 -> TempTrend.RISING
            else                        -> TempTrend.STABLE
        }
        val likelyCause = when {
            chargingHeatEvents > highCpuHeatEvents && chargingHeatEvents > idleHeatEvents
                                          -> LearnedCause.CHARGING_HABIT
            highCpuHeatEvents > idleHeatEvents -> LearnedCause.HIGH_CPU_APPS
            idleHeatEvents > 3            -> LearnedCause.BACKGROUND_DRAIN
            else                          -> LearnedCause.UNKNOWN
        }
        val personalRisk = when {
            maxRecordedTemp > 0f && snapshot.batteryTemp > maxRecordedTemp - 2f -> RiskLevel.CRITICAL
            tempAnomaly > 8f  -> RiskLevel.HIGH
            tempAnomaly > 4f  -> RiskLevel.MEDIUM
            tempAnomaly > 0f  -> RiskLevel.LOW
            else              -> RiskLevel.NORMAL
        }

        return LearnedProfile(
            samplesCollected    = n,
            baselineTemp        = baselineTemp,
            baselineCpu         = baselineCpu,
            averageTemp         = avgTemp,
            averageCpu          = avgCpu,
            maxRecordedTemp     = maxRecordedTemp,
            minRecordedTemp     = if (minRecordedTemp < 60f) minRecordedTemp else 0f,
            tempAnomaly         = tempAnomaly,
            isAnomaly           = isAnomaly,
            trend               = trend,
            likelyCause         = likelyCause,
            personalRisk        = personalRisk,
            consecutiveHotReadings = consecutiveHotReadings,
            chargingHeatPct     = if (n > 0) chargingHeatEvents * 100f / max(1, n) else 0f,
            highCpuHeatPct      = if (n > 0) highCpuHeatEvents  * 100f / max(1, n) else 0f,
            dynamicThreshold    = dynamicThreshold,
            topHeatApp          = topHeatApp,
            topHeatAppScore     = topHeatAppScore,
            hourAnomaly         = hourAnomaly,
            expectedThisHour    = expectedThisHour
        )
    }

    // ========== SMART TIPS v2 ==========

    fun generateSmartTips(profile: LearnedProfile, snapshot: ThermalSnapshot, prediction: TempPrediction): List<SmartTip> {
        val tips = mutableListOf<SmartTip>()

        // Prediccion: alerta ANTES de que ocurra
        if (prediction.predictedTemp >= 43f && prediction.confidence != PredictionConfidence.LOW) {
            tips.add(SmartTip("🔮", "Prediccion: temperatura subira",
                "El modelo estima que llegara a ${prediction.predictedTemp.toInt()}C pronto. ${prediction.trendText}. Actua ahora: cierra apps pesadas.",
                5, TipCategory.PREDICTION))
        }

        // Anomalia horaria: inusual para esta hora del dia
        profile.hourAnomaly?.let { ha ->
            if (ha > 5f && profile.expectedThisHour != null) {
                tips.add(SmartTip("🕐", "Inusual para esta hora",
                    "A esta hora tu telefono suele estar a ${profile.expectedThisHour.toInt()}C, pero ahora esta ${ha.toInt()}C mas caliente. Algo diferente esta pasando.",
                    4, TipCategory.ANOMALY))
            }
        }

        // App correlacionada con calor
        if (profile.topHeatApp.isNotEmpty() && profile.topHeatAppScore > 38f) {
            tips.add(SmartTip("📱", "App causante identificada",
                "'${profile.topHeatApp}' esta correlacionada con el calentamiento de tu dispositivo segun tu historial. Considera cerrarla o revisar sus permisos de background.",
                4, TipCategory.APP_CORRELATION))
        }

        // Habito de carga
        if (profile.chargingHeatPct > 30f)
            tips.add(SmartTip("⚡", "Habito de carga detectado",
                "El ${profile.chargingHeatPct.toInt()}% de tus eventos de calor ocurren mientras cargas. Evita usar el telefono intensivamente mientras carga y no lo cubras.",
                4, TipCategory.LEARNED_PATTERN))

        // CPU apps
        if (profile.highCpuHeatPct > 25f)
            tips.add(SmartTip("💻", "Apps de alto consumo detectadas",
                "El ${profile.highCpuHeatPct.toInt()}% de los calentamientos coinciden con CPU alta. Revisa que apps corren en background desde Ajustes > Bateria > Uso de bateria.",
                3, TipCategory.LEARNED_PATTERN))

        // Anomalia general
        if (profile.isAnomaly)
            tips.add(SmartTip("🔬", "Anomalia termica",
                "Temp actual (${snapshot.batteryTemp.toInt()}C) supera ${profile.tempAnomaly.toInt()}C tu baseline personal de ${profile.baselineTemp.toInt()}C. Fuera de lo normal para tu dispositivo.",
                5, TipCategory.ANOMALY))

        // Subida rapida
        if (profile.trend == TempTrend.RISING_FAST)
            tips.add(SmartTip("📈", "Ascenso rapido",
                "La temperatura sube continuamente por ${profile.consecutiveHotReadings} lecturas seguidas. Toma accion antes de llegar a zona critica.",
                5, TipCategory.TREND))

        // Record historico
        if (profile.personalRisk == RiskLevel.CRITICAL && profile.maxRecordedTemp > 0f)
            tips.add(SmartTip("🏆", "Cerca del maximo historico",
                "Llegando al record de ${profile.maxRecordedTemp.toInt()}C. Deja el telefono en reposo 15 minutos en superficie fria.",
                5, TipCategory.RISK))

        // Background drain
        if (profile.likelyCause == LearnedCause.BACKGROUND_DRAIN)
            tips.add(SmartTip("👻", "Drenaje en background",
                "Calenta sin uso intensivo. Causas posibles: GPS activo, app de tracking, sincronizacion excesiva o software malicioso. Revisa Ajustes > Bateria.",
                4, TipCategory.LEARNED_PATTERN))

        // Tips por temperatura actual
        when {
            snapshot.batteryTemp >= 50f -> tips.add(SmartTip("🚨", "EMERGENCIA TERMICA",
                "50C+ puede dañar permanentemente la bateria. APAGA el telefono YA, retira la funda, ponlo en superficie fria (no fridge). No lo cargues hasta que enfrie a menos de 35C.",
                5, TipCategory.CRITICAL))
            snapshot.batteryTemp >= 45f -> tips.add(SmartTip("🔴", "Temperatura critica",
                "Cierra TODAS las apps. Baja brillo al minimo. Desconecta el cargador. Activa modo avion 10 minutos. Si tienes funda gruesa, quitala.",
                4, TipCategory.CRITICAL))
            snapshot.batteryTemp >= 40f -> tips.add(SmartTip("🟠", "Temperatura alta",
                "Cierra apps pesadas (juegos, navegador, camara). Baja el brillo. Desactiva BT y WiFi si no los usas. Evita el sol directo.",
                3, TipCategory.WARNING))
            snapshot.batteryTemp >= 35f -> tips.add(SmartTip("🟡", "Temperatura elevada",
                "Monitorea. Evita sol directo y superficies calientes. Las fundas muy gruesas impiden la disipacion de calor.",
                2, TipCategory.INFO))
            else -> tips.add(SmartTip("🟢", "Temperatura optima",
                "Rango ideal 20-35C para baterias de litio. Tus habitos actuales son buenos para la longevidad de la bateria.",
                1, TipCategory.INFO))
        }

        // Consejo de carga
        if (snapshot.isCharging && snapshot.batteryLevel >= 90)
            tips.add(SmartTip("🔋", "Carga al limite",
                "Cargar al 100% constantemente degrada la capacidad. Ideal: mantener entre 20-80%. Samsung: Ajustes > Bateria > Proteccion de carga.",
                2, TipCategory.INFO))

        return tips.sortedWith(compareByDescending<SmartTip> { it.priority }.thenBy {
            when (it.category) {
                TipCategory.PREDICTION -> 0
                TipCategory.CRITICAL -> 1
                TipCategory.ANOMALY -> 2
                TipCategory.TREND -> 3
                else -> 4
            }
        }).take(6)
    }

    fun reset() { prefs.edit().clear().apply() }

    companion object {
        const val ThermalMonitorIntervalMin = 1 // cada 30s aprox = ~1 min entre 2 lecturas
    }
}

// ========== DATA MODELS ==========

data class LearnedProfile(
    val samplesCollected: Int,
    val baselineTemp: Float,
    val baselineCpu: Float,
    val averageTemp: Float,
    val averageCpu: Float,
    val maxRecordedTemp: Float,
    val minRecordedTemp: Float,
    val tempAnomaly: Float,
    val isAnomaly: Boolean,
    val trend: TempTrend,
    val likelyCause: LearnedCause,
    val personalRisk: RiskLevel,
    val consecutiveHotReadings: Int,
    val chargingHeatPct: Float,
    val highCpuHeatPct: Float,
    val dynamicThreshold: Float,
    val topHeatApp: String,
    val topHeatAppScore: Float,
    val hourAnomaly: Float?,
    val expectedThisHour: Float?
)

data class TempPrediction(
    val predictedTemp: Float,
    val confidence: PredictionConfidence,
    val trendText: String,
    val slope: Float = 0f
)

data class BatteryHealthScore(
    val score: Int,
    val level: String,
    val factors: List<String>
)

data class HourlyDataPoint(val hour: Int, val avgTemp: Float)

data class SmartTip(
    val icon: String,
    val title: String,
    val detail: String,
    val priority: Int,
    val category: TipCategory = TipCategory.INFO
)

enum class TempTrend { STABLE, RISING, RISING_FAST }
enum class LearnedCause { UNKNOWN, CHARGING_HABIT, HIGH_CPU_APPS, BACKGROUND_DRAIN }
enum class RiskLevel { NORMAL, LOW, MEDIUM, HIGH, CRITICAL }
enum class PredictionConfidence { LOW, MEDIUM, HIGH }
enum class TipCategory { INFO, WARNING, CRITICAL, PREDICTION, ANOMALY, TREND, RISK, APP_CORRELATION, LEARNED_PATTERN }
