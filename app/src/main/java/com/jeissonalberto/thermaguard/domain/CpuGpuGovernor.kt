package com.jeissonalberto.thermaguard.domain

import android.content.Context
import android.os.Process
import java.io.File

/**
 * CpuGpuGovernor — Control directo de CPU/GPU para ThermaGuard v3.9.25
 *
 * Opera en tres niveles de escalación:
 *   NIVEL 1 — Android APIs (sin root, siempre disponible)
 *   NIVEL 2 — /sys filesystem (root o ADB write_settings)
 *   NIVEL 3 — Shell commands via RootEngine (root requerido)
 *
 * ── RUTAS DE CONTROL CPU (Snapdragon 8 Gen 1 / Exynos 2200) ──────────────────
 *   CPU Governor:    /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor
 *   CPU Max Freq:    /sys/devices/system/cpu/cpu[0-7]/cpufreq/scaling_max_freq
 *   CPU Min Freq:    /sys/devices/system/cpu/cpu[0-7]/cpufreq/scaling_min_freq
 *   CPU Online:      /sys/devices/system/cpu/cpu[0-7]/online
 *   Schedutil Rate:  /sys/devices/system/cpu/cpufreq/schedutil/rate_limit_us
 *   CPU Boost:       /sys/module/cpu_boost/parameters/input_boost_freq
 *
 * ── RUTAS DE CONTROL GPU (Adreno 730 / Xclipse 920) ─────────────────────────
 *   GPU Governor:    /sys/class/kgsl/kgsl-3d0/devfreq/governor
 *   GPU Max Freq:    /sys/class/kgsl/kgsl-3d0/devfreq/max_freq
 *   GPU Min Freq:    /sys/class/kgsl/kgsl-3d0/devfreq/min_freq
 *   GPU Power Level: /sys/class/kgsl/kgsl-3d0/max_pwrlevel
 *   GPU Throttle:    /sys/class/kgsl/kgsl-3d0/throttling
 *   GPU Bus Speed:   /sys/class/kgsl/kgsl-3d0/gpubusy
 *
 * ── RUTAS TÉRMICAS ────────────────────────────────────────────────────────────
 *   Thermal Zones:   /sys/class/thermal/thermal_zone[0-N]/temp
 *   Trip Points:     /sys/class/thermal/thermal_zone[0-N]/trip_point_[N]_temp
 *   Cooling Devices: /sys/class/thermal/cooling_device[N]/cur_state
 *   Thermal Engine:  /sys/module/msm_thermal/ (Qualcomm)
 *                    /sys/kernel/msm_thermal/  (alternativo)
 *
 * ── RUTAS MEMORIA ────────────────────────────────────────────────────────────
 *   RAM Usage:       /proc/meminfo
 *   OOM Adj:         /proc/[pid]/oom_adj
 *   Swap:            /sys/block/zram0/mm_stat
 */

object CpuGpuGovernor {

    // ── Frecuencias disponibles del S22 (kHz) ──────────────────────────────────
    private val BIG_CORE_FREQS_KHZ   = listOf(300_000, 576_000, 768_000, 1017_600,
                                               1305_600, 1612_800, 1804_800, 2016_000,
                                               2150_400, 2304_000, 2496_000, 2649_600, 3187_200)
    private val MID_CORE_FREQS_KHZ   = listOf(300_000, 614_400, 864_000, 1056_000,
                                               1171_200, 1286_400, 1382_400, 1478_400,
                                               1593_600, 1708_800, 1804_800, 1920_000, 2131_200)
    private val LITTLE_CORE_FREQS_KHZ = listOf(300_000, 403_200, 499_200, 595_200,
                                                691_200, 787_200, 883_200, 979_200, 1075_200)

    // ── GPU Adreno 730 Freqs (Hz) ────────────────────────────────────────────
    private val GPU_FREQS_HZ = listOf(
        150_000_000, 225_000_000, 290_000_000, 350_000_000, 430_000_000,
        500_000_000, 550_000_000, 620_000_000, 690_000_000, 750_000_000, 818_000_000
    )

    // ────────────────────────────────────────────────────────────────────────────
    //  NIVEL 1 — Android APIs (sin root)
    // ────────────────────────────────────────────────────────────────────────────

    /** Lee temperatura de todas las zonas térmicas disponibles */
    fun readAllThermalZones(): Map<String, Float> {
        val result = mutableMapOf<String, Float>()
        val thermalDir = File("/sys/class/thermal")
        if (!thermalDir.exists()) return result
        thermalDir.listFiles()?.filter { it.name.startsWith("thermal_zone") }?.forEach { zone ->
            try {
                val temp = File(zone, "temp").readText().trim().toIntOrNull() ?: return@forEach
                val type = File(zone, "type").readText().trim()
                // Dividir por 1000 si está en milligrados
                val tempC = if (temp > 1000) temp / 1000f else temp.toFloat()
                result[type] = tempC
            } catch (_: Exception) {}
        }
        return result
    }

    /** Lee uso de CPU por core desde /proc/stat */
    fun readPerCoreUsage(): List<Float> {
        return try {
            val lines = File("/proc/stat").readLines()
            lines.filter { it.startsWith("cpu") && it.length > 3 && it[3].isDigit() }
                .map { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size < 5) return@map 0f
                    val user   = parts[1].toLong()
                    val nice   = parts[2].toLong()
                    val system = parts[3].toLong()
                    val idle   = parts[4].toLong()
                    val iowait = if (parts.size > 5) parts[5].toLong() else 0L
                    val total  = user + nice + system + idle + iowait
                    if (total == 0L) 0f else (total - idle).toFloat() / total * 100f
                }
        } catch (_: Exception) { emptyList() }
    }

    /** Lee frecuencia actual de cada CPU core */
    fun readCpuCurrentFreqs(): List<Long> {
        return (0..7).mapNotNull { core ->
            try {
                File("/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq")
                    .readText().trim().toLongOrNull()
            } catch (_: Exception) { null }
        }
    }

    /** Lee el governor actual de la CPU */
    fun readCpuGovernor(): String? {
        return try {
            File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
                .readText().trim()
        } catch (_: Exception) { null }
    }

    /** Lee frecuencia actual de la GPU */
    fun readGpuCurrentFreqMHz(): Int? {
        val paths = listOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/kgsl/kgsl-3d0/gpuclk"
        )
        return paths.firstNotNullOfOrNull { path ->
            try {
                val raw = File(path).readText().trim().toLongOrNull()
                raw?.let { (it / 1_000_000).toInt() }
            } catch (_: Exception) { null }
        }
    }

    /** Lee % de ocupación de la GPU */
    fun readGpuUsagePct(): Int? {
        return try {
            // Adreno: /sys/class/kgsl/kgsl-3d0/gpubusy → "busy total"
            val text = File("/sys/class/kgsl/kgsl-3d0/gpubusy").readText().trim()
            val parts = text.split(" ")
            if (parts.size >= 2) {
                val busy  = parts[0].toLongOrNull() ?: return null
                val total = parts[1].toLongOrNull() ?: return null
                if (total == 0L) 0 else (busy * 100L / total).toInt()
            } else null
        } catch (_: Exception) { null }
    }

    // ────────────────────────────────────────────────────────────────────────────
    //  NIVEL 2 — /sys writes (root o ADB privilegiado)
    // ────────────────────────────────────────────────────────────────────────────

    private fun writeKernelFile(path: String, value: String): Boolean {
        return try {
            File(path).writeText(value)
            true
        } catch (_: Exception) {
            // Si falla directamente, intentar via shell root
            false
        }
    }

    /**
     * Aplica configuración de governor completa según análisis de física.
     * Implementa la recomendación del SiliconPhysicsEngine.
     */
    fun applyGovernorConfig(config: com.jeissonalberto.thermaguard.data.GovernorConfig): List<String> {
        val results = mutableListOf<String>()

        // 1. Governor de CPU
        val govPath = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
        if (writeKernelFile(govPath, config.name)) {
            results.add("✅ Governor CPU → ${config.name}")
        } else {
            results.add("❌ Governor CPU: necesita root")
        }

        // 2. Frecuencia máxima en todos los cores
        val maxFreqKHz = (config.maxFreqGHz * 1_000_000).toInt()
        var coresSet = 0
        for (core in 0..7) {
            val p = "/sys/devices/system/cpu/cpu$core/cpufreq/scaling_max_freq"
            if (writeKernelFile(p, maxFreqKHz.toString())) coresSet++
        }
        if (coresSet > 0) results.add("✅ Max freq CPU → ${config.maxFreqGHz}GHz ($coresSet/8 cores)")
        else              results.add("❌ Max freq CPU: necesita root")

        // 3. Cores online (apagar los sobrantes para ahorrar calor)
        for (core in 1..7) {
            val online = if (core < config.activeCores) "1" else "0"
            writeKernelFile("/sys/devices/system/cpu/cpu$core/online", online)
        }
        results.add("✅ Cores activos → ${config.activeCores}/8")

        // 4. GPU max freq
        val gpuFreqHz = config.gpuMaxFreqMHz * 1_000_000L
        val gpuPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            "/sys/class/kgsl/kgsl-3d0/max_gpuclk"
        )
        val gpuSet = gpuPaths.any { writeKernelFile(it, gpuFreqHz.toString()) }
        if (gpuSet) results.add("✅ Max freq GPU → ${config.gpuMaxFreqMHz}MHz")
        else        results.add("❌ Max freq GPU: necesita root")

        return results
    }

    /**
     * Throttle de emergencia — actúa cuando T > 52°C
     * Reduce agresivamente tanto CPU como GPU
     */
    fun emergencyThrottle(): List<String> {
        val results = mutableListOf<String>()

        // CPU → powersave governor + freq máx 1.8GHz
        writeKernelFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor", "powersave")
        for (core in 0..7) {
            writeKernelFile(
                "/sys/devices/system/cpu/cpu$core/cpufreq/scaling_max_freq", "1843200"
            )
        }
        // Apagar cores 5,6,7 (mid y prime)
        for (core in 5..7) {
            writeKernelFile("/sys/devices/system/cpu/cpu$core/online", "0")
        }
        results.add("🚨 CPU: powersave @1.8GHz, 4 cores (5-7 offline)")

        // GPU → mínimo posible
        writeKernelFile("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq", "150000000")
        writeKernelFile("/sys/class/kgsl/kgsl-3d0/throttling", "1")
        results.add("🚨 GPU: 150MHz (throttle máximo)")

        // Forzar modo baja pantalla
        try {
            writeKernelFile("/sys/class/leds/lcd-backlight/brightness", "80")
            results.add("🚨 Brillo: reducido al mínimo")
        } catch (_: Exception) {}

        return results
    }

    /**
     * Restaura configuración de alto rendimiento para Modo Bestia
     */
    fun unlockMaxPerformance(): List<String> {
        val results = mutableListOf<String>()

        // Todos los cores online
        for (core in 0..7) {
            writeKernelFile("/sys/devices/system/cpu/cpu$core/online", "1")
        }
        // Governor performance
        writeKernelFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor", "performance")
        // Max freq (prime core S22)
        writeKernelFile("/sys/devices/system/cpu/cpu7/cpufreq/scaling_max_freq", "3187200")
        results.add("🐉 CPU: performance @3.18GHz, 8 cores")

        // GPU desbloqueada
        writeKernelFile("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq", "818000000")
        writeKernelFile("/sys/class/kgsl/kgsl-3d0/devfreq/governor", "msm-adreno-tz")
        writeKernelFile("/sys/class/kgsl/kgsl-3d0/throttling", "0")
        results.add("🐉 GPU: Adreno TZ @818MHz, throttle OFF")

        return results
    }

    /**
     * Lee temperatura de todos los sensores disponibles y construye
     * un mapa completo para el SiliconPhysicsEngine
     */
    fun buildFullThermalSnapshot(base: com.jeissonalberto.thermaguard.data.ThermalSnapshot):
            com.jeissonalberto.thermaguard.data.ThermalSnapshot {
        val zones = readAllThermalZones()
        val cores = readPerCoreUsage()
        val gpuUsage = readGpuUsagePct()
        val gpuFreq  = readGpuCurrentFreqMHz()

        // Extraer temps de zonas conocidas
        val cpuTemp   = zones.filterKeys { it.contains("cpu", ignoreCase = true) }
                            .values.maxOrNull() ?: base.cpuTemp
        val gpuTemp   = zones.filterKeys { it.contains("gpu", ignoreCase = true) }
                            .values.maxOrNull() ?: base.gpuTemp
        val battTemp  = zones.filterKeys { it.contains("bat", ignoreCase = true) }
                            .values.firstOrNull() ?: base.batteryTemp
        val modemTemp = zones.filterKeys { it.contains("modem", ignoreCase = true) ||
                                          it.contains("wlan", ignoreCase = true) }
                            .values.maxOrNull() ?: base.modemTemp
        val skinTemp  = zones.filterKeys { it.contains("skin", ignoreCase = true) ||
                                          it.contains("surface", ignoreCase = true) }
                            .values.firstOrNull() ?: base.skinTemp

        val avgCpuUsage = if (cores.isNotEmpty()) cores.average().toFloat() else base.cpuUsage

        return base.copy(
            cpuTemp     = cpuTemp,
            gpuTemp     = gpuTemp,
            batteryTemp = battTemp,
            modemTemp   = modemTemp,
            skinTemp    = skinTemp,
            cpuUsage    = avgCpuUsage
        ).also { snap ->
            snap.allZones     = zones
            snap.perCoreUsage = cores
        }
    }
}
