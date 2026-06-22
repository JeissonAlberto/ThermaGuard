package com.jeissonalberto.thermaguard.data

import kotlin.math.*
import com.jeissonalberto.thermaguard.root.HardwareProfiler

/**
 * ════════════════════════════════════════════════════════════════════════════
 *  SiliconPhysicsEngine — Motor de Física del Silicio v2.0
 *  ThermaGuard v3.9.25
 *
 *  LEYES INTEGRADAS:
 *
 *  ── TERMODINÁMICA ───────────────────────────────────────────────────────────
 *  1. Fourier (Conducción): Q = -k·A·(ΔT/Δx)
 *     → Flujo de calor a través del die → heatspreader → carcasa del S22
 *  2. Newton (Convección): Q = h·A·(T_surface - T_ambient)
 *     → Disipación por la carcasa al aire (mayor con funda: h menor)
 *  3. Stefan-Boltzmann (Radiación): P = ε·σ·A·(T⁴ - T_amb⁴)
 *     → Emisión infrarroja del panel trasero de vidrio
 *  4. Fick (Difusión): J = -D·(dC/dx)
 *     → Difusión de portadores de carga → degradación de transistores
 *  5. Wiedemann-Franz: κ/σ = L·T
 *     → Relación conductividad térmica/eléctrica del cobre en las vías
 *
 *  ── MICROELECTRÓNICA / VLSI ─────────────────────────────────────────────────
 *  6. Moore (Carga Térmica): P = α·C·V²·f
 *     → Potencia dinámica del Snapdragon 8 Gen 1 / Exynos 2200
 *  7. Dennard (Fuga de Corriente): P_leak = I_leak·V_dd
 *     → A temperaturas altas la corriente de fuga crece exponencialmente
 *  8. Pollack (Rendimiento Real): Perf ∝ √Power
 *     → Rendimiento real vs potencia — throttle no es lineal
 *  9. Amdahl (Límite de Throttle): T = 1/(s + (1-s)/n)
 *     → Cuánto throughput pierde la CPU cuando el governor la limita
 *  10. NBTI/HCI (Degradación): MTTF = A·e^(Ea/kT)
 *     → Mean Time To Failure — temperatura alta degrada transistores
 *
 *  ── CIRCUITOS ELECTRÓNICOS ──────────────────────────────────────────────────
 *  11. Ohm Térmico: R_th = ΔT/P   (θ_ja, θ_jc, θ_cs, θ_sa)
 *     → Red de resistencias térmicas del SoC al aire
 *  12. RC Térmico (Constante de Tiempo): τ = R_th·C_th
 *     → Tiempo que tarda el die en estabilizarse térmicamente
 *  13. Joule (Disipación): P = I²·R
 *     → Calor generado en inductores/reguladores PMIC
 *  14. Capacitancia Parásita: t_rise = 2.2·R·C
 *     → Afecta frecuencia máxima de conmutación (eficiencia del GPU)
 *
 *  ── ANDROID HARDWARE ────────────────────────────────────────────────────────
 *  15. Thermal HAL (HIDL/AIDL): ThermalStatus → NONE/LIGHT/MODERATE/SEVERE/CRITICAL/EMERGENCY/SHUTDOWN
 *  16. CPU Governor: schedutil → latencia de respuesta < 1ms
 *  17. GPU DVFS: Adreno/Mali frequency scaling
 *  18. EAS (Energy Aware Scheduling): colocación de tareas en cores big/little
 *  19. Thermal Throttling → freq cap: big cores, GPU, modem, display
 * ════════════════════════════════════════════════════════════════════════════
 */

// ─── CONSTANTES FÍSICAS ───────────────────────────────────────────────────────
object PhysicsConst {
    const val BOLTZMANN_K  = 1.380649e-23  // J/K
    const val PLANCK_H     = 6.62607015e-34 // J·s
    const val STEFAN_SIGMA = 5.670374419e-8 // W/m²·K⁴
    const val ELECTRON_Q   = 1.602176634e-19 // C
    const val WIEDEMANN_L  = 2.44e-8        // W·Ω/K² (Lorenz number)
    const val ACTIVATION_EA = 0.7           // eV — energía activación Si (NBTI)

    // Parámetros físicos del dispositivo (constantes universales + detección dinámica)
    const val HEATSPREADER_K   = 400.0   // W/m·K — conductividad cobre (universal)
    const val GLASS_EMISSIVITY = 0.93    // ε vidrio Gorilla Glass (universal)
    const val AMBIENT_TEMP_K   = 298.15  // 25°C en Kelvin (referencia)
    const val VDD_CORE         = 0.85    // V — voltaje nominal (estimado)
    const val CAPACITANCE_pF   = 120.0   // pF — capacitancia efectiva (estimado)
    const val ACTIVITY_FACTOR  = 0.3     // α promedio (0.15 idle, 0.5 gaming)

}


// ─── PARÁMETROS FÍSICOS POR DISPOSITIVO ──────────────────────────────────────
data class DevicePhysicsParams(
    val dieAreaM2:     Double,
    val chassisAreaM2: Double,
    val thermalMassJK: Double,
    val tdpW:          Double,
    val clockGhzMax:   Double
)

// ─── CACHE DE PARÁMETROS DE HARDWARE (se refresca cada 5 min) ─────────────────
private var _cachedDeviceParams: DevicePhysicsParams? = null
private var _cacheTimestamp: Long = 0L
private const val DEVICE_PARAMS_TTL_MS = 5 * 60 * 1000L  // 5 minutos

/** Detecta parámetros físicos del dispositivo en runtime via HardwareProfiler.
 *  Resultado cacheado 5 min — el hardware no cambia entre muestras. */
internal fun detectDevicePhysicsParams(): DevicePhysicsParams {
    val now = System.currentTimeMillis()
    _cachedDeviceParams?.takeIf { now - _cacheTimestamp < DEVICE_PARAMS_TTL_MS }
        ?.let { return it }

    val p       = HardwareProfiler.getProfile()
    val peakKhz = p.cpuClusters.maxOfOrNull { c -> c.maxFreqKhz } ?: 3_000_000L
    val peakGhz = peakKhz / 1_000_000.0
    val tdp = when {
        peakGhz >= 3.2 -> 12.0
        peakGhz >= 3.0 -> 10.5
        peakGhz >= 2.5 -> 9.0
        peakGhz >= 2.0 -> 7.0
        else           -> 5.5
    }
    val dieArea = when {
        p.cpuCores >= 8 && peakGhz >= 3.0 -> 78e-6 * 78e-6
        p.cpuCores >= 8                    -> 70e-6 * 70e-6
        p.cpuCores >= 6                    -> 60e-6 * 60e-6
        else                               -> 50e-6 * 50e-6
    }
    val thermalMass = when {
        p.cpuCores >= 8 && peakGhz >= 3.0 -> 45.0
        p.cpuCores >= 8                    -> 40.0
        else                               -> 35.0
    }
    val result = DevicePhysicsParams(
        dieAreaM2     = dieArea,
        chassisAreaM2 = 0.006,
        thermalMassJK = thermalMass,
        tdpW          = tdp,
        clockGhzMax   = peakGhz
    )
    _cachedDeviceParams = result
    _cacheTimestamp     = now
    return result
}

// ─── RESULTADO DEL ANÁLISIS DE FÍSICA ────────────────────────────────────────
data class PhysicsAnalysis(
    // Termodinámica
    val conductionFlux_W: Double,       // Flujo de calor por conducción (Fourier)
    val convectionLoss_W: Double,       // Pérdida por convección (Newton)
    val radiationLoss_W: Double,        // Pérdida por radiación (Stefan-Boltzmann)
    val netHeatAccumulation_W: Double,  // Calor neto acumulado

    // Potencia del SoC
    val dynamicPower_W: Double,         // P dinámica (Moore/CMOS)
    val leakagePower_W: Double,         // P fuga (Dennard)
    val totalSocPower_W: Double,        // Potencia total estimada

    // Circuitos
    val thermalResistance_KW: Double,   // R_th total (θ_ja) K/W
    val thermalTimeConst_s: Double,     // τ = R_th·C_th (segundos a equilibrio)
    val joulePmicHeat_W: Double,        // Calor PMIC por ley de Joule

    // Rendimiento y Degradación
    val throttlePct: Int,               // % throttle estimado (Amdahl)
    val performanceRatio: Double,       // Rendimiento real vs peak (Pollack)
    val mttfHours: Double,              // MTTF horas (Arrhenius)
    val degradationIndex: Double,       // 0-1 (0=nuevo, 1=degradado)

    // Android/Governor
    val recommendedGovernor: String,    // schedutil/conservative/powersave
    val recommendedMaxFreqGHz: Double,  // Frecuencia máxima recomendada
    val cpuAffinityMask: Int,           // Máscara de afinidad recomendada
    val gpuThrottlePct: Int,            // % throttle GPU

    // Diagnóstico textual
    val summaryLines: List<String>,     // Resumen para la UI
    val thermalLevel: ThermalLevel,
    val riskScore: Int                  // 0-100
)

// ─── PARÁMETROS ANDROID GOVERNOR ─────────────────────────────────────────────
data class GovernorConfig(
    val name: String,
    val maxFreqGHz: Double,
    val activeCores: Int,
    val gpuMaxFreqMHz: Int,
    val reason: String
)

// ─── MOTOR PRINCIPAL ──────────────────────────────────────────────────────────
object SiliconPhysicsEngine {

    private val K = PhysicsConst

    /**
     * Análisis completo del estado térmico del dispositivo.
     * Integra las 19 leyes físicas para generar un diagnóstico preciso.
     */
    fun analyze(snap: ThermalSnapshot, ambientTemp: Float = 25f): PhysicsAnalysis {
        val D     = detectDevicePhysicsParams()   // parámetros físicos del dispositivo actual
        val T_die = snap.cpuTemp.toDouble().let { if (it > 20.0) it else snap.batteryTemp.toDouble() }
        val T_amb = ambientTemp.toDouble()
        val T_die_K = T_die + 273.15
        val T_amb_K = T_amb + 273.15
        val cpuLoad = snap.cpuUsage / 100.0

        // ── 1. CONDUCCIÓN (Fourier) ────────────────────────────────────────
        // Q = k·A·ΔT/Δx  (k=400 W/m·K cobre, Δx=0.3mm die-a-frame)
        val deltaX = 0.0003 // 0.3mm
        val conductionFlux = K.HEATSPREADER_K * D.dieAreaM2 * (T_die - T_amb) / deltaX

        // ── 2. CONVECCIÓN (Newton) ────────────────────────────────────────
        // h = 6 W/m²·K (convección natural), 12 con funda quitada (mejor flujo)
        val h_conv = if (T_die > 45.0) 8.0 else 6.0
        val convectionLoss = h_conv * D.chassisAreaM2 * (T_die - T_amb)

        // ── 3. RADIACIÓN (Stefan-Boltzmann) ───────────────────────────────
        val radiationLoss = K.GLASS_EMISSIVITY * K.STEFAN_SIGMA * D.chassisAreaM2 *
            (T_die_K.pow(4) - T_amb_K.pow(4))

        // ── 4. CARGA TÉRMICA DINÁMICA (Moore/CMOS) ────────────────────────
        // P = α·C·V²·f   donde f = carga * f_max
        val freq = cpuLoad * D.clockGhzMax * 1e9 // Hz
        val dynamicPower = K.ACTIVITY_FACTOR * (K.CAPACITANCE_pF * 1e-12) *
            K.VDD_CORE.pow(2) * freq

        // ── 5. CORRIENTE DE FUGA (Dennard) ────────────────────────────────
        // I_leak aumenta exponencialmente con temperatura (modelo DIBL)
        // I_leak ∝ e^(q·T / k·T_ref) — simplificado
        val leakageFactor = exp((T_die - 25.0) / 12.0) // duplica cada ~12°C
        val leakagePower  = 0.8 * leakageFactor        // W — estimado

        val totalSocPower = dynamicPower + leakagePower

        // ── 6. JOULE en PMIC ─────────────────────────────────────────────
        // P = I²·R_inductor, I ≈ totalSocPower / Vdd, R_inductor ≈ 0.05Ω
        val I_pmic = totalSocPower / K.VDD_CORE
        val joulePmicHeat = I_pmic.pow(2) * 0.05

        // ── 7. RED TÉRMICA RC (Ohm Térmico) ──────────────────────────────
        // θ_ja (junction-to-ambient) estimado S22 SoC: ~8 K/W
        val R_ja = 8.0 // K/W
        val C_th = D.thermalMassJK
        val thermalTimeConst = R_ja * C_th // segundos

        // ── 8. CALOR NETO ACUMULADO ───────────────────────────────────────
        val netHeat = totalSocPower - convectionLoss - radiationLoss

        // ── 9. THROTTLE POR AMDAHL ────────────────────────────────────────
        // Fracción serial del workload ≈ 0.05 (95% paralelo en Cortex-X2)
        val s = 0.05
        val n = if (T_die < 40.0) 8.0 else if (T_die < 45.0) 6.0 else if (T_die < 50.0) 4.0 else 2.0
        val amdahlThroughput = 1.0 / (s + (1.0 - s) / n)
        val throttlePct = ((1.0 - amdahlThroughput / (1.0 / (s + (1.0 - s) / 8.0))) * 100).toInt()
            .coerceIn(0, 100)

        // ── 10. RENDIMIENTO REAL (Pollack) ────────────────────────────────
        // Perf ∝ √Power relativo al peak
        val performanceRatio = sqrt(totalSocPower / D.tdpW).coerceIn(0.0, 1.0)

        // ── 11. MTTF (Ley de Arrhenius) ───────────────────────────────────
        // MTTF = A·e^(Ea/(k·T))   Ea=0.7eV para NBTI en Si
        val Ea_J = K.ACTIVATION_EA * K.ELECTRON_Q
        val mttfNorm = exp(Ea_J / (K.BOLTZMANN_K * T_die_K))
        val mttfRef  = exp(Ea_J / (K.BOLTZMANN_K * (85.0 + 273.15))) // ref a 85°C
        val mttfHours = (mttfNorm / mttfRef) * 50000.0 // horas ref

        // Índice de degradación: cuánto se "consume" de vida por temperatura
        val degradationIndex = (1.0 - (mttfHours / 100000.0)).coerceIn(0.0, 1.0)

        // ── 12. GOVERNOR Y AFINIDAD ANDROID ──────────────────────────────
        val govConfig = recommendGovernor(T_die, cpuLoad, netHeat)

        // ── 13. THROTTLE GPU ─────────────────────────────────────────────
        val gpuTemp = snap.gpuTemp.toDouble().let { if (it > 20.0) it else T_die * 0.92 }
        val gpuThrottlePct = when {
            gpuTemp >= 52.0 -> 60
            gpuTemp >= 48.0 -> 35
            gpuTemp >= 44.0 -> 15
            else            -> 0
        }

        // ── 14. NIVEL TÉRMICO Y RISK SCORE ───────────────────────────────
        val level = T_die.toFloat().toThermalLevel()
        val riskScore = calculateRiskScore(T_die, cpuLoad, netHeat, degradationIndex)

        // ── 15. RESUMEN PARA UI ───────────────────────────────────────────
        val summary = buildSummary(
            T_die, dynamicPower, leakagePower, convectionLoss, radiationLoss,
            netHeat, throttlePct, mttfHours, govConfig, gpuThrottlePct
        )

        return PhysicsAnalysis(
            conductionFlux_W       = conductionFlux,
            convectionLoss_W       = convectionLoss,
            radiationLoss_W        = radiationLoss,
            netHeatAccumulation_W  = netHeat,
            dynamicPower_W         = dynamicPower,
            leakagePower_W         = leakagePower,
            totalSocPower_W        = totalSocPower,
            thermalResistance_KW   = R_ja,
            thermalTimeConst_s     = thermalTimeConst,
            joulePmicHeat_W        = joulePmicHeat,
            throttlePct            = throttlePct,
            performanceRatio       = performanceRatio,
            mttfHours              = mttfHours,
            degradationIndex       = degradationIndex,
            recommendedGovernor    = govConfig.name,
            recommendedMaxFreqGHz  = govConfig.maxFreqGHz,
            cpuAffinityMask        = buildAffinityMask(govConfig.activeCores),
            gpuThrottlePct         = gpuThrottlePct,
            summaryLines           = summary,
            thermalLevel           = level,
            riskScore              = riskScore
        )
    }

    // ─── Governor recommendation basado en T, carga y calor neto ─────────────
    private fun recommendGovernor(T_die: Double, cpuLoad: Double, netHeat: Double): GovernorConfig {
        return when {
            T_die >= 52.0 -> GovernorConfig(
                name         = "powersave",
                maxFreqGHz   = 1.8,
                activeCores  = 4,
                gpuMaxFreqMHz = 350,
                reason       = "Temperatura crítica — modo supervivencia"
            )
            T_die >= 47.0 -> GovernorConfig(
                name         = "conservative",
                maxFreqGHz   = 2.4,
                activeCores  = 6,
                gpuMaxFreqMHz = 550,
                reason       = "Zona caliente — reducción conservadora"
            )
            T_die >= 43.0 && netHeat > 0 -> GovernorConfig(
                name         = "schedutil",
                maxFreqGHz   = 2.8,
                activeCores  = 7,
                gpuMaxFreqMHz = 700,
                reason       = "Zona tibia — schedutil balanceado"
            )
            cpuLoad > 0.8 -> GovernorConfig(
                name         = "performance",
                maxFreqGHz   = 3.0,
                activeCores  = 8,
                gpuMaxFreqMHz = 818,
                reason       = "Alta carga con temperatura OK"
            )
            else -> GovernorConfig(
                name         = "schedutil",
                maxFreqGHz   = 2.6,
                activeCores  = 8,
                gpuMaxFreqMHz = 818,
                reason       = "Operación normal"
            )
        }
    }

    // ─── Máscara de afinidad CPU Android ─────────────────────────────────────
    // S22 tiene 8 cores: 0-3 little (Cortex-A510), 4-6 mid (A710), 7 prime (X2)
    // Con n cores activos: preferir big-first (7→6→5→4→3→2→1→0)
    private fun buildAffinityMask(activeCores: Int): Int {
        return when (activeCores) {
            8 -> 0xFF    // todos
            7 -> 0xFE    // sin core 0 (little más lento)
            6 -> 0xFC    // 2 cores big + mids + prime
            5 -> 0xF8    // solo mid + prime
            4 -> 0xF0    // solo cores grandes
            3 -> 0xE0    // 2 mid + prime
            2 -> 0xC0    // prime + 1 mid
            else -> 0x80 // solo prime core
        }
    }

    // ─── Risk Score 0-100 ──────────────────────────────────────────────────────
    private fun calculateRiskScore(
        T_die: Double, cpuLoad: Double, netHeat: Double, degradation: Double
    ): Int {
        val tempScore   = ((T_die - 30.0) / 30.0 * 40.0).coerceIn(0.0, 40.0)
        val loadScore   = (cpuLoad * 20.0).coerceIn(0.0, 20.0)
        val heatScore   = if (netHeat > 0) (netHeat / 5.0 * 20.0).coerceIn(0.0, 20.0) else 0.0
        val degradScore = (degradation * 20.0).coerceIn(0.0, 20.0)
        return (tempScore + loadScore + heatScore + degradScore).toInt().coerceIn(0, 100)
    }

    // ─── Resumen textual ──────────────────────────────────────────────────────
    private fun buildSummary(
        T: Double, dynP: Double, leakP: Double, conv: Double, rad: Double,
        net: Double, throttle: Int, mttf: Double, gov: GovernorConfig, gpuThrottle: Int
    ): List<String> = buildList {
        add("🌡 Temperatura: ${T.toInt()}°C  |  Potencia SoC: ${"%.1f".format(dynP + leakP)}W")
        add("⚡ Dinámica: ${"%.1f".format(dynP)}W  |  Fuga: ${"%.2f".format(leakP)}W (Dennard)")
        add("❄️ Convección: ${"%.2f".format(conv)}W  |  Radiación: ${"%.3f".format(rad)}W")
        add("🔥 Calor neto: ${if(net>0) "+${"%.2f".format(net)}W (acumulando)" else "${"%.2f".format(net)}W (disipando)"}")
        if (throttle > 0) add("⚠️ Throttle CPU: -$throttle% (Amdahl)  |  GPU: -$gpuThrottle%")
        else              add("✅ Sin throttle — rendimiento pleno")
        add("🧬 MTTF estimado: ${"%.0f".format(mttf/1000)}k horas  (Arrhenius @ ${T.toInt()}°C)")
        add("🖥 Governor: ${gov.name} @ ${"%.1f".format(gov.maxFreqGHz)}GHz — ${gov.reason}")
    }
}
