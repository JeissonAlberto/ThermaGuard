package com.jeissonalberto.thermaguard.data

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * ThermaGuard Learning Engine v3.0
 * Motor de aprendizaje adaptativo avanzado con:
 * - EMA (Exponential Moving Average) en lecturas de temperatura — alpha=0.3
 * - Prediccion multi-factor: tendencia temp + tendencia CPU + estado de carga
 * - Deteccion de anomalias: desviacion > 2 SD sobre media movil (20 muestras)
 * - Seguimiento de sesiones de calor: conteo diario de episodios de sobrecalentamiento
 * - Cooldown adaptativo: aprende cuanto tarda en bajar 3C tras accion automatica
 * - Risk score compuesto 0-100
 * - Baseline por hora (temp + CPU) para anomalias mas precisas
 */
class ThermalLearningEngine(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("thermaguard_learning_v3", Context.MODE_PRIVATE)

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

    // ---- EMA temperatura (alpha=0.3 fija) ----
    private var emaTemp: Float
        get() = prefs.getFloat("ema_temp", -1f)
        set(v) { prefs.edit().putFloat("ema_temp", v).apply() }
    private var emaCpu: Float
        get() = prefs.getFloat("ema_cpu", -1f)
        set(v) { prefs.edit().putFloat("ema_cpu", v).apply() }
    // EMA alpha adaptativo: conservador en zona normal, reactivo en anomalía
    private fun emaAlpha(current: Float, baseline: Float): Float {
        val deviation = kotlin.math.abs(current - baseline)
        return when {
            deviation > 6f -> 0.45f   // Cambio brusco: reaccionar rápido
            deviation > 3f -> 0.25f   // Cambio moderado
            deviation > 1f -> 0.15f   // Zona estable: suavizar mucho
            else           -> 0.08f   // Temperatura muy estable
        }
    }

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
    private var consecutiveCoolReadings: Int = 0  // no persistido — se reinicia con la app

    // ---- Ventana de prediccion EMA (ultimas 6 lecturas suavizadas) ----
    private var windowTemps: String
        get() = prefs.getString("window_temps", "") ?: ""
        set(v) { prefs.edit().putString("window_temps", v).apply() }
    private var windowCpus: String
        get() = prefs.getString("window_cpus", "") ?: ""
        set(v) { prefs.edit().putString("window_cpus", v).apply() }
    private val WINDOW_SIZE = 6

    // ---- Rolling buffer de 20 muestras para deteccion de anomalias ----
    private var rollingTemps: String
        get() = prefs.getString("rolling_temps", "") ?: ""
        set(v) { prefs.edit().putString("rolling_temps", v).apply() }
    private var rollingCpus: String
        get() = prefs.getString("rolling_cpus", "") ?: ""
        set(v) { prefs.edit().putString("rolling_cpus", v).apply() }
    private val ROLLING_SIZE = 20

    // ---- Ciclos horarios (24 slots, temp + CPU promedio por hora) ----
    private fun getHourAvg(hour: Int): Float = prefs.getFloat("hour_avg_$hour", -1f)
    private fun setHourAvg(hour: Int, v: Float) { prefs.edit().putFloat("hour_avg_$hour", v).apply() }
    private fun getHourCount(hour: Int): Int = prefs.getInt("hour_count_$hour", 0)
    private fun setHourCount(hour: Int, v: Int) { prefs.edit().putInt("hour_count_$hour", v).apply() }
    private fun getHourCpuAvg(hour: Int): Float = prefs.getFloat("hour_cpu_avg_$hour", -1f)
    private fun setHourCpuAvg(hour: Int, v: Float) { prefs.edit().putFloat("hour_cpu_avg_$hour", v).apply() }

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

    // ---- Sesiones de calor ----
    private var heatSessionsToday: Int
        get() = prefs.getInt("heat_sessions_today", 0)
        set(v) { prefs.edit().putInt("heat_sessions_today", v).apply() }
    private var lastSessionDay: Int
        get() = prefs.getInt("last_session_day", -1)
        set(v) { prefs.edit().putInt("last_session_day", v).apply() }
    private var inHeatSession: Boolean
        get() = prefs.getBoolean("in_heat_session", false)
        set(v) { prefs.edit().putBoolean("in_heat_session", v).apply() }

    // ---- Cooldown adaptativo ----
    private var avgCooldownMinutes: Float
        get() = prefs.getFloat("avg_cooldown_minutes", 5f)
        set(v) { prefs.edit().putFloat("avg_cooldown_minutes", v).apply() }
    private var cooldownSamples: Int
        get() = prefs.getInt("cooldown_samples", 0)
        set(v) { prefs.edit().putInt("cooldown_samples", v).apply() }
    // Temp al momento de la ultima auto-accion (para medir cooldown)
    private var cooldownStartTemp: Float
        get() = prefs.getFloat("cooldown_start_temp", -1f)
        set(v) { prefs.edit().putFloat("cooldown_start_temp", v).apply() }
    private var cooldownStartTime: Long
        get() = prefs.getLong("cooldown_start_time", -1L)
        set(v) { prefs.edit().putLong("cooldown_start_time", v).apply() }

    // ========== APRENDIZAJE PRINCIPAL ==========

    fun learn(snapshot: ThermalSnapshot): LearnedProfile {
        val n = sampleCount + 1
        sampleCount = n
        totalTempSum += snapshot.batteryTemp
        totalCpuSum  += snapshot.cpuUsage

        // --- EMA fija alpha=0.3 para suavizado de temperatura ---
        val prevEmaTemp = emaTemp
        val prevEmaCpu  = emaCpu
        val alphaTemp = if (prevEmaTemp < 0f) 1f else emaAlpha(snapshot.batteryTemp, baselineTemp.coerceAtLeast(snapshot.batteryTemp - 5f))
        val alphaCpu  = if (prevEmaCpu  < 0f) 1f else emaAlpha(snapshot.cpuUsage, baselineCpu.coerceAtLeast(snapshot.cpuUsage - 10f))
        emaTemp = if (prevEmaTemp < 0f) snapshot.batteryTemp
                  else prevEmaTemp * (1f - alphaTemp) + snapshot.batteryTemp * alphaTemp
        emaCpu  = if (prevEmaCpu < 0f) snapshot.cpuUsage
                  else prevEmaCpu * (1f - alphaCpu) + snapshot.cpuUsage * alphaCpu

        // Baseline adaptativo (mas lento, largo plazo)
        val alpha = when {
            n < 10  -> 0.3f
            n < 30  -> 0.15f
            n < 100 -> 0.08f
            else    -> 0.03f
        }
        baselineTemp = baselineTemp * (1f - alpha) + snapshot.batteryTemp * alpha
        baselineCpu  = baselineCpu  * (1f - alpha) + snapshot.cpuUsage  * alpha

        if (snapshot.batteryTemp > maxRecordedTemp) maxRecordedTemp = snapshot.batteryTemp
        if (snapshot.batteryTemp < minRecordedTemp) minRecordedTemp = snapshot.batteryTemp

        // Ventana de prediccion con valores EMA suavizados
        updateWindow(emaTemp, emaCpu)

        // Rolling buffer 20 muestras (temperatura + CPU raw para anomalias)
        updateRolling(snapshot.batteryTemp, snapshot.cpuUsage)

        // Ciclo horario: temp + CPU
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val prevAvg   = getHourAvg(hour)
        val prevCount = getHourCount(hour)
        val prevCpuAvg = getHourCpuAvg(hour)
        if (prevAvg < 0f) {
            setHourAvg(hour, snapshot.batteryTemp)
            setHourCpuAvg(hour, snapshot.cpuUsage)
            setHourCount(hour, 1)
        } else {
            val cnt = prevCount + 1
            setHourAvg(hour, prevAvg * (prevCount.toFloat() / cnt) + snapshot.batteryTemp / cnt)
            val cpuBase = if (prevCpuAvg < 0f) snapshot.cpuUsage
                          else prevCpuAvg * (prevCount.toFloat() / cnt) + snapshot.cpuUsage / cnt
            setHourCpuAvg(hour, cpuBase)
            setHourCount(hour, cnt)
        }

        // Correlacion app-temp
        val BLOCKED_APPS = setOf("thermaguard", "android", "systemui", "launcher")
        if (snapshot.topApp.isNotEmpty() && snapshot.batteryTemp >= 38f
            && BLOCKED_APPS.none { snapshot.topApp.lowercase().contains(it) }) {
            val prev  = getAppHeatScore(snapshot.topApp)
            val score = prev * 0.85f + snapshot.batteryTemp * 0.15f
            setAppHeatScore(snapshot.topApp, score)
            if (score > topHeatAppScore) {
                topHeatApp      = snapshot.topApp
                topHeatAppScore = score
            }
        }

        // Patrones de calor
        val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        if (lastSessionDay != dayOfYear) {
            heatSessionsToday = 0
            lastSessionDay    = dayOfYear
            inHeatSession     = false
        }

        if (snapshot.batteryTemp >= 40f) {
            when {
                snapshot.isCharging     -> chargingHeatEvents++
                snapshot.cpuUsage > 60f -> highCpuHeatEvents++
                snapshot.cpuUsage < 20f -> idleHeatEvents++
            }
            consecutiveHotReadings++
            consecutiveCoolReadings = 0   // cancelar cooldown si vuelve a calentar
            totalHotMinutes += ThermalMonitorIntervalMin

            // Inicio de sesion de calor
            if (!inHeatSession) {
                inHeatSession = true
                heatSessionsToday++
            }
        } else {
            // Histéresis: solo bajar consecutiveHot si hay 2 lecturas frías seguidas
            consecutiveCoolReadings++
            if (consecutiveCoolReadings >= 2) {
                if (inHeatSession) inHeatSession = false
                if (consecutiveHotReadings > 0) consecutiveHotReadings--
            }
        }
        if (snapshot.batteryTemp >= 45f) totalCriticalMinutes += ThermalMonitorIntervalMin

        // Medir cooldown si habia auto-accion pendiente
        val cst = cooldownStartTemp
        val cstTime = cooldownStartTime
        if (cst > 0f && cstTime > 0L && snapshot.batteryTemp <= cst - 3f) {
            val minutesElapsed = ((System.currentTimeMillis() - cstTime) / 60000f)
                .coerceAtLeast(0.5f)
            val samples = cooldownSamples + 1
            avgCooldownMinutes = (avgCooldownMinutes * cooldownSamples + minutesElapsed) / samples
            cooldownSamples    = samples
            cooldownStartTemp  = -1f
            cooldownStartTime  = -1L
        }

        // Umbral dinamico
        val deviation = if (n > 20) computeStdDevFromRolling() else 8f
        // Umbral dinámico con contexto: baseline + desviación adaptativa
        dynamicThreshold = if (sampleCount < 20) {
            // Inicio: umbral conservador hasta tener datos suficientes
            42f
        } else {
            // Ajuste por hora: de noche/madrugada la temp ambiente es menor
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val timeBonus = if (hour in 0..6 || hour in 22..23) -1f else 0f  // más estricto de noche
            (baselineTemp + deviation * 1.5f + timeBonus).coerceIn(37f, 50f)
        }

        return buildProfile(snapshot)
    }

    /**
     * Llamar externamente cuando se ejecuta una auto-accion de enfriamiento,
     * para que el motor mida cuanto tiempo tarda en bajar 3C.
     */
    fun recordAutoAction(currentTemp: Float) {
        cooldownStartTemp = currentTemp
        cooldownStartTime = System.currentTimeMillis()
    }

    private fun updateWindow(smoothTemp: Float, smoothCpu: Float) {
        fun addToList(stored: String, value: Float, size: Int): String {
            val list = stored.split(",").filter { it.isNotEmpty() }.mapNotNull { it.toFloatOrNull() }.toMutableList()
            list.add(value)
            if (list.size > size) list.removeAt(0)
            return list.joinToString(",")
        }
        windowTemps = addToList(windowTemps, smoothTemp, WINDOW_SIZE)
        windowCpus  = addToList(windowCpus,  smoothCpu,  WINDOW_SIZE)
    }

    private fun updateRolling(temp: Float, cpu: Float) {
        fun addToList(stored: String, value: Float, size: Int): String {
            val list = stored.split(",").filter { it.isNotEmpty() }.mapNotNull { it.toFloatOrNull() }.toMutableList()
            list.add(value)
            if (list.size > size) list.removeAt(0)
            return list.joinToString(",")
        }
        rollingTemps = addToList(rollingTemps, temp, ROLLING_SIZE)
        rollingCpus  = addToList(rollingCpus,  cpu,  ROLLING_SIZE)
    }

    private fun getWindow(): List<Float> =
        windowTemps.split(",").filter { it.isNotEmpty() }.mapNotNull { it.toFloatOrNull() }

    private fun getWindowCpus(): List<Float> =
        windowCpus.split(",").filter { it.isNotEmpty() }.mapNotNull { it.toFloatOrNull() }

    private fun getRollingTemps(): List<Float> =
        rollingTemps.split(",").filter { it.isNotEmpty() }.mapNotNull { it.toFloatOrNull() }

    private fun getRollingCpus(): List<Float> =
        rollingCpus.split(",").filter { it.isNotEmpty() }.mapNotNull { it.toFloatOrNull() }

    private fun computeStdDevFromRolling(): Float {
        val samples = getRollingTemps()
        if (samples.size < 3) return 8f
        val mean = samples.average().toFloat()
        val variance = samples.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance).coerceAtLeast(2f)
    }

    /**
     * Detecta anomalia: temp actual > media_rolling + 2*SD_rolling
     * Tambien considera baseline horario de CPU si disponible.
     */
    private fun detectAnomaly(currentTemp: Float, currentCpu: Float, hour: Int): Boolean {
        val temps = getRollingTemps()
        if (temps.size < 5) return currentTemp - baselineTemp > 6f  // fallback inicial

        val meanTemp = temps.average().toFloat()
        val sdTemp   = computeStdDevFromRolling()
        val tempAnomaly = currentTemp > meanTemp + 2f * sdTemp

        // Anomalia CPU horaria (si tenemos baseline)
        val hourCpuBase = getHourCpuAvg(hour)
        val cpus = getRollingCpus()
        val cpuAnomaly = if (hourCpuBase >= 0f && cpus.size >= 5) {
            val meanCpu = cpus.average().toFloat()
            val sdCpu   = cpus.map { (it - meanCpu) * (it - meanCpu) }.average().toFloat().let { sqrt(it) }.coerceAtLeast(5f)
            currentCpu > meanCpu + 2f * sdCpu
        } else false

        return tempAnomaly || cpuAnomaly
    }

    // ========== PREDICCION MULTI-FACTOR ==========

    fun predictNextTemp(): TempPrediction {
        val window    = getWindow()
        val windowCpu = getWindowCpus()
        if (window.size < 3) return TempPrediction(0f, PredictionConfidence.LOW, "Recopilando datos...")

        // --- Factor 1: tendencia de temperatura (regresion lineal sobre ventana EMA) ---
        val n = window.size
        val xMean = (n - 1) / 2.0
        val yMean = window.average()
        var num = 0.0; var den = 0.0
        for (i in window.indices) {
            num += (i - xMean) * (window[i] - yMean)
            den += (i - xMean) * (i - xMean)
        }
        val rawTempSlope  = if (den != 0.0) (num / den).toFloat() else 0f
        // Limitar pendiente: máx 0.8°C por intervalo (evita predicciones absurdas)
        val tempSlope     = rawTempSlope.coerceIn(-0.8f, 0.8f)
        val tempIntercept = (yMean - tempSlope * xMean).toFloat()
        val tempPredicted = (tempIntercept + tempSlope * n).coerceIn(20f, 62f)

        // --- Factor 2: tendencia de CPU (indica si la carga va a aumentar la temp) ---
        val cpuSlope = if (windowCpu.size >= 3) {
            val nc = windowCpu.size
            val xc = (nc - 1) / 2.0
            val yc = windowCpu.average()
            var cn = 0.0; var cd = 0.0
            for (i in windowCpu.indices) {
                cn += (i - xc) * (windowCpu[i] - yc)
                cd += (i - xc) * (i - xc)
            }
            if (cd != 0.0) (cn / cd).toFloat() else 0f
        } else 0f
        // CPU slope contribuye: cada +1%/lectura de CPU = ~0.05C adicional
        val cpuContribution = (cpuSlope * 0.05f).coerceIn(-1f, 1f)

        // --- Factor 3: estado de carga (cargando = +0.3C adicional) ---
        val chargingContribution = if (chargingHeatPct > 20f) 0.3f else 0f

        // Prediccion multi-factor ponderada
        val multiPredicted = (tempPredicted * 0.7f
                            + (tempPredicted + cpuContribution) * 0.2f
                            + (tempPredicted + chargingContribution) * 0.1f)
            .coerceIn(20f, 80f)

        val confidence = when {
            window.size >= WINDOW_SIZE && abs(tempSlope) < 0.5f -> PredictionConfidence.HIGH
            window.size >= 4 -> PredictionConfidence.MEDIUM
            else             -> PredictionConfidence.LOW
        }

        val trend = when {
            tempSlope > 1.5f  -> "🔺 Subira ~${tempSlope.toInt()}C por lectura — RIESGO"
            tempSlope > 0.3f  -> "📈 Tendencia al alza (+${String.format("%.1f", tempSlope)}C)"
            tempSlope < -0.3f -> "📉 Enfriandose (${String.format("%.1f", tempSlope)}C)"
            else              -> "➡️ Estable"
        }

        return TempPrediction(multiPredicted, confidence, trend, tempSlope)
    }

    // ========== SALUD DE BATERIA ==========

    fun computeBatteryHealthScore(): BatteryHealthScore {
        val n = sampleCount
        if (n < 5) return BatteryHealthScore(100, "Sin datos suficientes aun", emptyList())

        var score = 100

        val hotPenalty  = (totalHotMinutes  / 60).coerceAtMost(20)
        score -= hotPenalty

        val critPenalty = (totalCriticalMinutes / 30 * 2).coerceAtMost(30)
        score -= critPenalty

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
        if (sampleCount >= 10 && maxRecordedTemp >= 45f) tips.add("Pico de ${maxRecordedTemp.toInt()}C registrado")
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

    // Porcentaje de calor por carga (helper interno)
    private val chargingHeatPct: Float
        get() = if (sampleCount > 0) chargingHeatEvents * 100f / max(1, sampleCount) else 0f

    // ========== RISK SCORE 0-100 ==========

    private fun computeRiskScore(
        temp: Float,
        cpuUsage: Float,
        isCharging: Boolean,
        consecutiveHot: Int,
        modemTemp: Float = 0f,
        trend: TempTrend = TempTrend.STABLE
    ): Int {
        // Temperatura: factor principal (0-50 pts) — escala no lineal
        val tempPts = when {
            temp >= 52f -> 50f
            temp >= 48f -> 43f
            temp >= 45f -> 36f
            temp >= 42f -> 27f
            temp >= 39f -> 17f
            temp >= 36f -> 8f
            else        -> 0f
        }
        // CPU: 0-20 pts
        val cpuPts = cpuUsage / 100f * 20f
        // Tendencia sostenida: 0-15 pts (mas peligroso que un spike puntual)
        val trendPts = when (trend) {
            TempTrend.RISING_FAST -> (consecutiveHot * 2.5f).coerceAtMost(15f)
            TempTrend.RISING      -> (consecutiveHot * 1.0f).coerceAtMost(8f)
            TempTrend.STABLE      -> 0f
        }
        // Carga simultanea con calor: +10 pts
        val chargingPts = if (isCharging && temp >= 38f) 10f else 0f
        // Modem caliente 5G/LTE: hasta +5 pts
        val modemPts = if (modemTemp >= 45f) 5f else if (modemTemp >= 40f) 2f else 0f
        return (tempPts + cpuPts + trendPts + chargingPts + modemPts).toInt().coerceIn(0, 100)
    }

    // ========== PERFIL COMPLETO ==========

    fun buildProfile(snapshot: ThermalSnapshot): LearnedProfile {
        val n       = sampleCount
        val avgTemp = if (n > 0) totalTempSum / n else snapshot.batteryTemp
        val avgCpu  = if (n > 0) totalCpuSum  / n else snapshot.cpuUsage

        val hour        = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val tempAnomaly = snapshot.batteryTemp - baselineTemp
        val isAnomalyNow = detectAnomaly(snapshot.batteryTemp, snapshot.cpuUsage, hour)

        val expectedThisHour = getCurrentHourExpected()
        val hourAnomaly      = expectedThisHour?.let { snapshot.batteryTemp - it }

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
            sampleCount >= 20 && maxRecordedTemp > 0f && snapshot.batteryTemp > maxRecordedTemp - 2f -> RiskLevel.CRITICAL
            tempAnomaly > 8f  -> RiskLevel.HIGH
            tempAnomaly > 4f  -> RiskLevel.MEDIUM
            tempAnomaly > 0f  -> RiskLevel.LOW
            else              -> RiskLevel.NORMAL
        }

        val currentTrend = when {
            consecutiveHotReadings >= 3 -> TempTrend.RISING_FAST
            consecutiveHotReadings >= 1 -> TempTrend.RISING
            else                        -> TempTrend.STABLE
        }
        val riskScoreNow = computeRiskScore(
            snapshot.batteryTemp,
            snapshot.cpuUsage,
            snapshot.isCharging,
            consecutiveHotReadings,
            snapshot.modemTemp,
            currentTrend
        )

        return LearnedProfile(
            samplesCollected       = n,
            baselineTemp           = baselineTemp,
            baselineCpu            = baselineCpu,
            averageTemp            = avgTemp,
            averageCpu             = avgCpu,
            maxRecordedTemp        = maxRecordedTemp,
            minRecordedTemp        = if (minRecordedTemp < 60f) minRecordedTemp else 0f,
            tempAnomaly            = tempAnomaly,
            isAnomaly              = isAnomalyNow,
            trend                  = trend,
            likelyCause            = likelyCause,
            personalRisk           = personalRisk,
            consecutiveHotReadings = consecutiveHotReadings,
            chargingHeatPct        = if (n > 0) chargingHeatEvents * 100f / max(1, n) else 0f,
            highCpuHeatPct         = if (n > 0) highCpuHeatEvents  * 100f / max(1, n) else 0f,
            dynamicThreshold       = dynamicThreshold,
            topHeatApp             = topHeatApp,
            topHeatAppScore        = topHeatAppScore,
            hourAnomaly            = hourAnomaly,
            expectedThisHour       = expectedThisHour,
            heatSessionsToday      = heatSessionsToday,
            avgCooldownMinutes     = avgCooldownMinutes,
            riskScore              = riskScoreNow
        )
    }

    // ========== SMART TIPS v3 ==========

    fun generateSmartTips(profile: LearnedProfile, snapshot: ThermalSnapshot, prediction: TempPrediction): List<SmartTip> {
        val tips = mutableListOf<SmartTip>()

        // Risk score alto
        if (profile.riskScore >= 75) {
            tips.add(SmartTip("⚠️", "Riesgo termico elevado",
                "El indice de riesgo compuesto es ${profile.riskScore}/100. Temperatura, CPU y estado de carga combinados indican alto estres termico.",
                5, TipCategory.RISK))
        }

        // Prediccion: alerta ANTES de que ocurra
        if (prediction.predictedTemp >= 43f && prediction.confidence != PredictionConfidence.LOW) {
            tips.add(SmartTip("🔮", "Prediccion: temperatura subira",
                "Modelo multi-factor estima ${prediction.predictedTemp.toInt()}C pronto. ${prediction.trendText}. Actua ahora: cierra apps pesadas.",
                5, TipCategory.PREDICTION))
        }

        // Anomalia estadistica
        if (profile.isAnomaly) {
            tips.add(SmartTip("🔬", "Anomalia termica estadistica",
                "Temperatura actual supera 2 desviaciones estandar sobre la media movil. Inusual para tu patron de uso. Causa probable: ${profile.likelyCause.name}.",
                5, TipCategory.ANOMALY))
        }

        // Anomalia horaria
        profile.hourAnomaly?.let { ha ->
            if (ha > 5f && profile.expectedThisHour != null) {
                tips.add(SmartTip("🕐", "Inusual para esta hora",
                    "A esta hora tu telefono suele estar a ${profile.expectedThisHour.toInt()}C, pero ahora esta ${ha.toInt()}C mas caliente.",
                    4, TipCategory.ANOMALY))
            }
        }

        // Sesiones de calor
        if (profile.heatSessionsToday >= 3) {
            tips.add(SmartTip("🌡️", "Multiples sesiones de calor hoy",
                "Tu dispositivo ha superado el umbral de calor ${profile.heatSessionsToday} veces hoy. El cooldown promedio es ${profile.avgCooldownMinutes.toInt()} min.",
                4, TipCategory.TREND))
        }

        // App correlacionada
        if (profile.topHeatApp.isNotEmpty() && profile.topHeatAppScore > 38f) {
            tips.add(SmartTip("📱", "App causante identificada",
                "'${profile.topHeatApp}' esta correlacionada con el calentamiento segun tu historial. Considera cerrarla o revisar permisos de background.",
                4, TipCategory.APP_CORRELATION))
        }

        // Habito de carga
        if (profile.chargingHeatPct > 30f)
            tips.add(SmartTip("⚡", "Habito de carga detectado",
                "El ${profile.chargingHeatPct.toInt()}% de tus eventos de calor ocurren mientras cargas. Evita uso intensivo y no cubras el telefono al cargar.",
                4, TipCategory.LEARNED_PATTERN))

        // CPU apps
        if (profile.highCpuHeatPct > 25f)
            tips.add(SmartTip("💻", "Apps de alto consumo detectadas",
                "El ${profile.highCpuHeatPct.toInt()}% de los calentamientos coinciden con CPU alta. Revisa Ajustes > Bateria > Uso de bateria.",
                3, TipCategory.LEARNED_PATTERN))

        // Subida rapida
        if (profile.trend == TempTrend.RISING_FAST)
            tips.add(SmartTip("📈", "Ascenso rapido",
                "Temperatura sube por ${profile.consecutiveHotReadings} lecturas seguidas. Toma accion antes de llegar a zona critica.",
                5, TipCategory.TREND))

        // Record historico
        if (profile.personalRisk == RiskLevel.CRITICAL && profile.maxRecordedTemp > 0f && profile.samplesCollected >= 20)
            tips.add(SmartTip("🏆", "Cerca del maximo historico",
                "Llegando al record de ${profile.maxRecordedTemp.toInt()}C. Deja el telefono en reposo ${profile.avgCooldownMinutes.toInt()} min en superficie fria.",
                5, TipCategory.RISK))

        // Background drain
        if (profile.likelyCause == LearnedCause.BACKGROUND_DRAIN)
            tips.add(SmartTip("👻", "Drenaje en background",
                "Calienta sin uso intensivo. Posible: GPS, tracking, sincronizacion excesiva o software malicioso. Revisa Ajustes > Bateria.",
                4, TipCategory.LEARNED_PATTERN))

        // Tips por temperatura actual
        when {
            snapshot.batteryTemp >= 50f -> tips.add(SmartTip("🚨", "EMERGENCIA TERMICA",
                "50C+ puede dañar permanentemente la bateria. APAGA el telefono YA, retira la funda, ponlo en superficie fria. No cargues hasta <35C.",
                5, TipCategory.CRITICAL))
            snapshot.batteryTemp >= 45f -> tips.add(SmartTip("🔴", "Temperatura critica",
                "Cierra TODAS las apps. Baja brillo. Desconecta el cargador. Activa modo avion 10 min. Quita funda si es gruesa.",
                4, TipCategory.CRITICAL))
            snapshot.batteryTemp >= 40f -> tips.add(SmartTip("🟠", "Temperatura alta",
                "Cierra apps pesadas. Baja brillo. Desactiva BT y WiFi si no los usas. Evita el sol directo.",
                3, TipCategory.WARNING))
            snapshot.batteryTemp >= 35f -> tips.add(SmartTip("🟡", "Temperatura elevada",
                "Monitorea. Evita sol directo y superficies calientes. Las fundas gruesas impiden la disipacion de calor.",
                2, TipCategory.INFO))
            else -> tips.add(SmartTip("🟢", "Temperatura optima",
                "Rango ideal 20-35C para baterias de litio. Tus habitos actuales son buenos para la longevidad de la bateria.",
                1, TipCategory.INFO))
        }

        // Consejo de carga
        if (snapshot.isCharging && snapshot.batteryLevel >= 90)
            tips.add(SmartTip("🔋", "Carga al limite",
                "Cargar al 100% constantemente degrada la capacidad. Ideal: 20-80%. Samsung: Ajustes > Bateria > Proteccion de carga.",
                2, TipCategory.INFO))

        return tips.sortedWith(compareByDescending<SmartTip> { it.priority }.thenBy {
            when (it.category) {
                TipCategory.PREDICTION -> 0
                TipCategory.CRITICAL   -> 1
                TipCategory.ANOMALY    -> 2
                TipCategory.TREND      -> 3
                else -> 4
            }
        }).take(6)
    }

    fun recordCooldown(minutes: Float) {
        avgCooldownMinutes = 0.3f * minutes + 0.7f * avgCooldownMinutes
    }

    fun reset() { prefs.edit().clear().apply() }


    // ════════════════════════════════════════════════════════════════════════
    //  MOTOR v6 — TRES LEYES DEL SILICIO
    //  Usa datos REALES del dispositivo:
    //    • cpuUsage  → de /proc/stat (lectura real del kernel)
    //    • batteryTemp → de BatteryManager (sensor real)
    //    • cpuTemp, modemTemp → de /sys/class/thermal (sensores del SoC)
    //    • perCoreUsage → de /proc/stat por núcleo
    // ════════════════════════════════════════════════════════════════════════

    fun analyzeSilicon(snapshot: ThermalSnapshot): SiliconAnalysis {
        val cpu  = snapshot.cpuUsage.coerceIn(0f, 100f)
        // Usar cpuTemp si el sensor lo lee (SoC Exynos), sino batteryTemp
        val rawTemp = if (snapshot.cpuTemp > 20f) snapshot.cpuTemp else snapshot.batteryTemp
        val temp = rawTemp.coerceIn(20f, 65f)

        // ── LEY 1: MOORE  P = C·V²·F ─────────────────────────────────────
        // Frecuencia proxy: uso de CPU normalizado (a más uso, más frecuencia efectiva)
        val f = cpu / 100f
        // Voltaje proxy: voltaje de un chip moderno varía ~0.6V (idle) a ~1.0V (full load)
        // Dennard descubrió que V ∝ √F cuando el scaling funciona; cuando no funciona
        // (chips modernos), V sube más rápido → más calor por unidad de trabajo
        val vDennardIdeal = 0.6f + 0.2f * f          // si Dennard escalara perfectamente
        val vReal         = 0.6f + 0.4f * f          // voltaje real (Dennard roto en ~2006)
        val moorePower    = (vReal * vReal * f * 100f).coerceIn(0f, 100f)

        // ── LEY 2: DENNARD SCALING ───────────────────────────────────────
        // Dennard Scaling se rompió porque el voltaje no puede bajar más sin errores
        // → fuga de corriente estática sube exponencialmente con temperatura
        // Leakage ∝ e^(T/Tref) — modelo simplificado de Arrhenius
        val Tref = 40f   // temperatura de referencia para el Exynos S22
        val leakageFactor = Math.exp(((temp - Tref) / 10.0)).toFloat().coerceIn(1f, 4f)
        val dennardLeakage = ((leakageFactor - 1f) / 3f * 100f).coerceIn(0f, 100f)
        // Eficiencia Dennard: qué tanto del voltaje adicional genera trabajo útil
        val powerIdeal = (vDennardIdeal * vDennardIdeal * f * 100f).coerceIn(0f, 100f)
        val dennardEfficiency = if (moorePower > 0f)
            (powerIdeal / moorePower * 100f).coerceIn(0f, 100f) else 100f
        // Densidad térmica proxy: más temperatura = más densidad en menos área
        val dennardThermalDensity = (temp - 20f) * 0.8f  // °C·factor → proxy °C/cm²

        // ── LEY 3: POLLACK'S RULE ────────────────────────────────────────
        // Pollack: rendimiento ∝ √(potencia consumida)
        // → duplicar la potencia solo da √2 ≈ 1.41x más rendimiento
        // → el calor extra NO se convierte en trabajo proporcional
        val pollackPerf = Math.sqrt(moorePower.toDouble()).toFloat().coerceIn(0f, 100f)
        val pollackPerfPerWatt = if (moorePower > 0f)
            (pollackPerf / moorePower * 100f).coerceIn(0f, 100f) else 100f
        // Calor desperdiciado = diferencia entre potencia invertida y rendimiento obtenido
        val pollackWastedHeat = ((moorePower - pollackPerf) / moorePower.coerceAtLeast(1f) * 100f)
            .coerceIn(0f, 100f)

        // ── LEY 4: AMDAHL THERMAL ────────────────────────────────────────
        // Amdahl aplicado al throttling térmico:
        // La parte "serial" del trabajo es lo que el SoC DEBE hacer sin escalar
        // Cuando la temperatura sube → el scheduler fuerza throttle en los núcleos
        // Tiempo hasta throttle: función inversa de qué tan cerca estamos del límite
        val thermalHeadroom = (50f - temp).coerceAtLeast(0f)   // margen hasta ~50°C (throttle)
        // núcleos efectivos: S22 tiene 8 núcleos, pero a alta temp el scheduler apaga cores
        val coresTotal = 8
        val coresActive = when {
            temp >= 50f -> 2   // throttle severo — solo 2 cores pequeños
            temp >= 47f -> 4
            temp >= 45f -> 6
            temp >= 42f -> 7
            else        -> 8
        }
        // Amdahl: speedup = 1 / (s + (1-s)/n) donde s = fracción serial
        // Usamos inversamente: a menos cores activos, más latencia térmica
        val s = 0.15f  // ~15% del trabajo es inherentemente serial (S22 Exynos)
        val amdahlSpeedup = 1f / (s + (1f - s) / coresActive)
        val amdahlParallelScore = (amdahlSpeedup / (1f / (s + (1f - s) / coresTotal)) * 100f)
            .coerceIn(0f, 100f)
        val amdahlThrottleEta = (thermalHeadroom / 10f * 100f).coerceIn(0f, 100f)
        // Tiempo hasta throttle: si moorePower > 60 y temp > 40, el throttle llega rápido
        val tempRiseRate = if (moorePower > 70f) 0.8f   // °C/min
                           else if (moorePower > 50f) 0.4f
                           else 0.15f
        val amdahlTimeToThrottle = if (thermalHeadroom <= 0f) 0
            else (thermalHeadroom / tempRiseRate * 60f).toInt().coerceIn(0, 7200)

        // ── DIAGNÓSTICO FINAL ─────────────────────────────────────────────
        val severity = when {
            temp >= 50f || moorePower >= 90f  -> SiliconSeverity.THERMAL_RUNAWAY
            temp >= 47f || moorePower >= 75f  -> SiliconSeverity.CRITICAL
            temp >= 43f || moorePower >= 55f  -> SiliconSeverity.STRESSED
            dennardEfficiency < 50f           -> SiliconSeverity.EFFICIENT  // poco margen
            else                             -> SiliconSeverity.OPTIMAL
        }

        // Ley dominante: la que más explica el calor actual
        val dominantLaw = when {
            dennardLeakage > 40f  -> "Dennard — fuga de corriente dominante"
            pollackWastedHeat > 50f -> "Pollack — rendimiento marginal del chip"
            amdahlTimeToThrottle < 120 -> "Amdahl — throttle térmico inminente"
            moorePower > 60f      -> "Moore — carga alta P=V²·F"
            else                  -> "Equilibrio térmico estable"
        }

        val recommendation = when (severity) {
            SiliconSeverity.THERMAL_RUNAWAY ->
                "🚨 El chip está al límite — cierra TODAS las apps y espera que se enfríe"
            SiliconSeverity.CRITICAL ->
                "🔴 Throttle inminente en ${amdahlTimeToThrottle/60} min — reduce carga ahora"
            SiliconSeverity.STRESSED ->
                if (pollackWastedHeat > 50f)
                    "🟠 Alto calor por bajo rendimiento (Pollack) — cierra apps ineficientes"
                else
                    "🟠 Carga alta (Moore P=${moorePower.toInt()}) — monitorea de cerca"
            SiliconSeverity.EFFICIENT ->
                "🟡 Eficiencia limitada por temperatura (Dennard ${dennardEfficiency.toInt()}%) — deja el teléfono en reposo breve"
            SiliconSeverity.OPTIMAL ->
                "🟢 Silicio operando en zona óptima — eficiencia ${pollackPerfPerWatt.toInt()}%"
        }

        return SiliconAnalysis(
            moorePower           = moorePower,
            mooreVoltage         = vReal,
            mooreFrequency       = f * 100f,
            dennardLeakage       = dennardLeakage,
            dennardEfficiency    = dennardEfficiency,
            dennardThermalDensity = dennardThermalDensity,
            pollackPerf          = pollackPerf,
            pollackPerfPerWatt   = pollackPerfPerWatt,
            pollackWastedHeat    = pollackWastedHeat,
            amdahlThrottleEta    = amdahlThrottleEta,
            amdahlParallelScore  = amdahlParallelScore,
            amdahlTimeToThrottle = amdahlTimeToThrottle,
            dominantLaw          = dominantLaw,
            recommendation       = recommendation,
            severity             = severity
        )
    }

    // Calcular power score para UI (retrocompatible con DashboardScreen)
    fun computePowerFromCpu(cpuUsage: Float): Float {
        val f = (cpuUsage / 100f).coerceIn(0f, 1f)
        val v = 0.6f + 0.4f * f
        return (v * v * f * 100f).coerceIn(0f, 100f)
    }


    // ════════════════════════════════════════════════════════════════════════
    //  GENERADOR DE RECOMENDACIONES DE ENFRIAMIENTO
    //  Usa datos REALES del hardware para dar consejos precisos y priorizados
    // ════════════════════════════════════════════════════════════════════════
    fun generateCoolingRecommendations(
        snapshot: ThermalSnapshot,
        silicon: SiliconAnalysis,
        mode: OperationMode
    ): List<CoolingRecommendation> {

        val temp   = snapshot.batteryTemp
        val cpu    = snapshot.cpuUsage
        val recs   = mutableListOf<CoolingRecommendation>()

        // ── PANTALLA (mayor fuente de calor en smartphones) ──────────────
        if (snapshot.brightnessLevel > 180) {
            recs.add(CoolingRecommendation(
                id = "display_brightness",
                category = CoolingCategory.DISPLAY,
                icon = "☀️",
                title = "Reduce el brillo de pantalla",
                detail = "El panel AMOLED del S22 genera calor proporcional al brillo. " +
                         "Bajar de ${snapshot.brightnessLevel}/255 a 120 puede reducir hasta 2-3°C.",
                impactDegrees = 2.5f, effort = 1
            ))
        }
        if (snapshot.displayTemp > 36f) {
            recs.add(CoolingRecommendation(
                id = "display_timeout",
                category = CoolingCategory.DISPLAY,
                icon = "📴",
                title = "Activa el timeout de pantalla corto",
                detail = "La pantalla activa es el mayor consumidor térmico del S22. " +
                         "Configura apagado en 30s cuando no la uses activamente.",
                impactDegrees = 3f, effort = 1
            ))
        }

        // ── CONECTIVIDAD ─────────────────────────────────────────────────
        if (snapshot.bluetoothActive && temp > 38f) {
            recs.add(CoolingRecommendation(
                id = "bluetooth_off",
                category = CoolingCategory.CONNECTIVITY,
                icon = "🔵",
                title = "Desactiva Bluetooth si no lo usas",
                detail = "El chip Bluetooth/WiFi comparte disipador con el modem. " +
                         "Temperatura del modem: ${snapshot.modemTemp.toInt()}°C.",
                impactDegrees = 1f, effort = 1
            ))
        }
        if (snapshot.modemTemp > 40f) {
            recs.add(CoolingRecommendation(
                id = "modem_heat",
                category = CoolingCategory.CONNECTIVITY,
                icon = "📡",
                title = "El modem está caliente — revisa señal",
                detail = "A baja señal el modem amplifica más para conectarse, generando más calor. " +
                         "Modem: ${snapshot.modemTemp.toInt()}°C. Activa modo avión brevemente para resetear.",
                impactDegrees = 2f, effort = 1
            ))
        }

        // ── APPS EN SEGUNDO PLANO ────────────────────────────────────────
        if (cpu > 35f && snapshot.topApp.isNotEmpty()) {
            recs.add(CoolingRecommendation(
                id = "top_app_cpu",
                category = CoolingCategory.BACKGROUND,
                icon = "📱",
                title = "${snapshot.topApp.split('.').last()} esta usando ${cpu.toInt()}% del CPU",
                detail = "Esta app es la causa principal del calor actual. " +
                         "Ciérrala o limítala en Ajustes → Apps → Batería → Restringida.",
                impactDegrees = silicon.moorePower / 30f, effort = 1
            ))
        }
        if (snapshot.ramUsageMb in 1..500) {
            recs.add(CoolingRecommendation(
                id = "ram_pressure",
                category = CoolingCategory.BACKGROUND,
                icon = "🧠",
                title = "RAM crítica — el sistema trabaja de más",
                detail = "Con solo ${snapshot.ramUsageMb}MB libre, el sistema hace swap y " +
                         "el Exynos 2200 genera calor extra gestionando la memoria. " +
                         "Cierra apps que no uses.",
                impactDegrees = 1.5f, effort = 1
            ))
        }

        // ── CARGA DE BATERÍA ─────────────────────────────────────────────
        if (snapshot.isCharging && temp > 38f) {
            recs.add(CoolingRecommendation(
                id = "charging_heat",
                category = CoolingCategory.CHARGING,
                icon = "⚡",
                title = "Cargando y caliente — riesgo para la batería",
                detail = "Cargar con el teléfono caliente degrada la batería más rápido. " +
                         "Desconéctalo unos minutos, deja que baje de 36°C y vuelve a cargar. " +
                         "Evita usar apps pesadas mientras carga.",
                impactDegrees = 3.5f, effort = 1
            ))
        }

        // ── HARDWARE FÍSICO ──────────────────────────────────────────────
        if (temp > 40f) {
            recs.add(CoolingRecommendation(
                id = "remove_case",
                category = CoolingCategory.ENVIRONMENT,
                icon = "🧳",
                title = "Retira la funda del teléfono",
                detail = "Las fundas de plástico o silicona actúan como aislante térmico. " +
                         "Sin funda, la carcasa de aluminio del S22 actúa como disipador y " +
                         "puede bajar 3-5°C rápidamente.",
                impactDegrees = 3.5f, effort = 1
            ))
        }

        // ── THROTTLE INMINENTE (Amdahl) ──────────────────────────────────
        if (silicon.amdahlTimeToThrottle in 1..180) {
            val mins = silicon.amdahlTimeToThrottle / 60
            recs.add(CoolingRecommendation(
                id = "throttle_warning",
                category = CoolingCategory.PERFORMANCE,
                icon = "⚠️",
                title = "Throttle térmico en ~${mins} min — Amdahl",
                detail = "El scheduler del S22 va a reducir la frecuencia del CPU para enfriar. " +
                         "Solo quedan ${silicon.amdahlParallelScore.toInt()}/8 núcleos activos. " +
                         "Reduce la carga ahora para evitar la desaceleración.",
                impactDegrees = 4f, effort = 2
            ))
        }

        // ── DENNARD LEAKAGE (calor sin trabajo) ──────────────────────────
        if (silicon.dennardLeakage > 35f) {
            recs.add(CoolingRecommendation(
                id = "dennard_leakage",
                category = CoolingCategory.SYSTEM,
                icon = "🔬",
                title = "Fuga de corriente elevada — Dennard",
                detail = "El chip genera ${silicon.dennardLeakage.toInt()}% de calor de fondo " +
                         "aunque no esté procesando (corriente de fuga del Exynos 2200). " +
                         "Reiniciar el dispositivo puede reducirla temporalmente.",
                impactDegrees = 2f, effort = 2
            ))
        }

        // Ordenar por impacto descendente
        return recs.sortedByDescending { it.impactDegrees }
    }

    companion object {
        const val ThermalMonitorIntervalMin = 1
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
    val isAnomaly: Boolean = false,
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
    val expectedThisHour: Float?,
    // ---- Nuevos campos v3 ----
    val heatSessionsToday: Int = 0,
    val avgCooldownMinutes: Float = 5f,
    val riskScore: Int = 0
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
