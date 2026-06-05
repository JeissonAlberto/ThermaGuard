package com.jeissonalberto.thermaguard.data

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.max

class ThermalLearningEngine(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("thermaguard_learning", Context.MODE_PRIVATE)

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

    fun learn(snapshot: ThermalSnapshot): LearnedProfile {
        val n = sampleCount + 1
        sampleCount = n
        totalTempSum = totalTempSum + snapshot.batteryTemp
        totalCpuSum = totalCpuSum + snapshot.cpuUsage
        val alpha = if (n < 20) 0.2f else 0.05f
        baselineTemp = baselineTemp * (1 - alpha) + snapshot.batteryTemp * alpha
        baselineCpu = baselineCpu * (1 - alpha) + snapshot.cpuUsage * alpha
        if (snapshot.batteryTemp > maxRecordedTemp) maxRecordedTemp = snapshot.batteryTemp
        if (snapshot.batteryTemp >= 40f) {
            when {
                snapshot.isCharging -> chargingHeatEvents++
                snapshot.cpuUsage > 60f -> highCpuHeatEvents++
                snapshot.cpuUsage < 20f -> idleHeatEvents++
            }
            consecutiveHotReadings++
        } else {
            consecutiveHotReadings = 0
        }
        return buildProfile(snapshot)
    }

    fun buildProfile(snapshot: ThermalSnapshot): LearnedProfile {
        val n = sampleCount
        val avgTemp = if (n > 0) totalTempSum / n else snapshot.batteryTemp
        val avgCpu = if (n > 0) totalCpuSum / n else snapshot.cpuUsage
        val tempAnomaly = snapshot.batteryTemp - baselineTemp
        val isAnomaly = tempAnomaly > 6f
        val trend = when {
            consecutiveHotReadings >= 3 -> TempTrend.RISING_FAST
            consecutiveHotReadings >= 1 -> TempTrend.RISING
            else -> TempTrend.STABLE
        }
        val likelyCause = when {
            chargingHeatEvents > highCpuHeatEvents && chargingHeatEvents > idleHeatEvents -> LearnedCause.CHARGING_HABIT
            highCpuHeatEvents > idleHeatEvents -> LearnedCause.HIGH_CPU_APPS
            idleHeatEvents > 3 -> LearnedCause.BACKGROUND_DRAIN
            else -> LearnedCause.UNKNOWN
        }
        val personalRisk = when {
            maxRecordedTemp > 0f && snapshot.batteryTemp > maxRecordedTemp - 2f -> RiskLevel.CRITICAL
            tempAnomaly > 8f -> RiskLevel.HIGH
            tempAnomaly > 4f -> RiskLevel.MEDIUM
            tempAnomaly > 0f -> RiskLevel.LOW
            else -> RiskLevel.NORMAL
        }
        return LearnedProfile(
            samplesCollected = n, baselineTemp = baselineTemp, baselineCpu = baselineCpu,
            averageTemp = avgTemp, averageCpu = avgCpu, maxRecordedTemp = maxRecordedTemp,
            tempAnomaly = tempAnomaly, isAnomaly = isAnomaly, trend = trend,
            likelyCause = likelyCause, personalRisk = personalRisk,
            consecutiveHotReadings = consecutiveHotReadings,
            chargingHeatPct = if (n > 0) chargingHeatEvents * 100f / max(1, n) else 0f,
            highCpuHeatPct = if (n > 0) highCpuHeatEvents * 100f / max(1, n) else 0f
        )
    }

    fun generateSmartTips(profile: LearnedProfile, snapshot: ThermalSnapshot): List<SmartTip> {
        val tips = mutableListOf<SmartTip>()
        if (profile.chargingHeatPct > 30f)
            tips.add(SmartTip("⚡", "Habito de carga detectado",
                "Tu telefono calienta mas cuando carga. Evita usarlo mientras carga y no lo dejes cargar cubierto de noche.", 5))
        if (profile.highCpuHeatPct > 25f) {
            val app = snapshot.topApp.ifEmpty { "Algunas apps" }
            tips.add(SmartTip("📱", "Apps exigentes detectadas",
                "$app genera carga sostenida de CPU. Cierra apps en background y desactiva sincronizacion automatica.", 4))
        }
        if (profile.isAnomaly)
            tips.add(SmartTip("🔬", "Anomalia termica detectada",
                "Temp actual (${snapshot.batteryTemp.toInt()}C) es ${profile.tempAnomaly.toInt()}C sobre tu baseline de ${profile.baselineTemp.toInt()}C.", 5))
        if (profile.trend == TempTrend.RISING_FAST)
            tips.add(SmartTip("📈", "Subida rapida detectada",
                "Temperatura en ascenso por ${profile.consecutiveHotReadings} lecturas consecutivas. Revisa apps en background.", 5))
        if (profile.personalRisk == RiskLevel.CRITICAL && profile.maxRecordedTemp > 0f)
            tips.add(SmartTip("🏆", "Cerca del maximo historico",
                "Llegando al record de ${profile.maxRecordedTemp.toInt()}C. Deja enfriar 15 minutos antes de continuar.", 5))
        if (profile.likelyCause == LearnedCause.BACKGROUND_DRAIN)
            tips.add(SmartTip("👻", "Calor sin uso aparente",
                "El telefono calienta sin uso intensivo. Revisa apps con GPS activo, sincronizacion excesiva o software malicioso.", 4))
        when {
            snapshot.batteryTemp >= 50f -> tips.add(SmartTip("🚨", "EMERGENCIA TERMICA",
                "50C+ daña bateria y componentes. Apaga YA, retira la funda, superficie fria, no cargues hasta enfriar.", 5))
            snapshot.batteryTemp >= 45f -> tips.add(SmartTip("🔴", "Temperatura critica",
                "Cierra todas las apps. Baja brillo al minimo. Desconecta el cargador. Modo avion 10 minutos.", 4))
            snapshot.batteryTemp >= 40f -> tips.add(SmartTip("🟠", "Temperatura alta",
                "Cierra apps pesadas, baja brillo, desactiva BT y WiFi si no los usas activamente.", 3))
            snapshot.batteryTemp >= 35f -> tips.add(SmartTip("🟡", "Temperatura elevada",
                "Monitorea. Evita sol directo, superficies calientes y fundas muy gruesas.", 2))
            else -> tips.add(SmartTip("🟢", "Temperatura optima",
                "Rango saludable 20-35C. Esto prolonga significativamente la vida de la bateria.", 1))
        }
        if (snapshot.isCharging && snapshot.batteryLevel >= 90)
            tips.add(SmartTip("🔋", "Carga al limite",
                "Cargar siempre al 100% degrada la bateria. Rango ideal 20-80%. Busca 'Proteccion de carga' en Ajustes Bateria.", 2))
        return tips.sortedByDescending { it.priority }.take(5)
    }

    fun reset() { prefs.edit().clear().apply() }
}

data class LearnedProfile(
    val samplesCollected: Int, val baselineTemp: Float, val baselineCpu: Float,
    val averageTemp: Float, val averageCpu: Float, val maxRecordedTemp: Float,
    val tempAnomaly: Float, val isAnomaly: Boolean, val trend: TempTrend,
    val likelyCause: LearnedCause, val personalRisk: RiskLevel,
    val consecutiveHotReadings: Int, val chargingHeatPct: Float, val highCpuHeatPct: Float
)
data class SmartTip(val icon: String, val title: String, val detail: String, val priority: Int)
enum class TempTrend { STABLE, RISING, RISING_FAST }
enum class LearnedCause { UNKNOWN, CHARGING_HABIT, HIGH_CPU_APPS, BACKGROUND_DRAIN }
enum class RiskLevel { NORMAL, LOW, MEDIUM, HIGH, CRITICAL }
