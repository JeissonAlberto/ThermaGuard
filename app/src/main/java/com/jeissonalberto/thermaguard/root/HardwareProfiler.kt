package com.jeissonalberto.thermaguard.root

import android.os.Build
import java.io.File

/**
 * HardwareProfiler — detección dinámica de hardware para compatibilidad universal
 *
 * Funciona en cualquier dispositivo Android: Qualcomm, MediaTek, Exynos, Tensor, etc.
 * No asume rutas, frecuencias ni número de cores — todo se descubre en tiempo de ejecución.
 */
object HardwareProfiler {

    // ── Perfil detectado del dispositivo ─────────────────────────────────────
    data class DeviceProfile(
        val cpuCores:        Int,
        val cpuClusters:     List<CpuCluster>,
        val gpuBackend:      GpuBackend,
        val gpuPaths:        GpuPaths,
        val thermalZones:    List<ThermalZoneInfo>,
        val governors:       List<String>,
        val chipset:         String,
        val isQualcomm:      Boolean,
        val isMediatek:      Boolean,
        val isExynos:        Boolean,
        val isTensor:        Boolean
    )

    data class CpuCluster(
        val id:       Int,
        val cores:    List<Int>,     // índices de cores en este cluster
        val maxFreqKhz: Long,        // frecuencia máxima real leída del kernel
        val minFreqKhz: Long,
        val governor: String         // governor actual
    )

    data class GpuPaths(
        val maxFreqPath:  String?,
        val minFreqPath:  String?,
        val governorPath: String?,
        val curFreqPath:  String?,
        val busyPath:     String?
    )

    enum class GpuBackend { ADRENO, MALI, POWERVR, TENSOR, UNKNOWN }

    data class ThermalZoneInfo(
        val index:   Int,
        val type:    String,
        val path:    String,
        val tempC:   Float    // temperatura actual al momento de la detección
    )

    // ── Cache del perfil (se detecta una sola vez por sesión) ────────────────
    @Volatile private var cachedProfile: DeviceProfile? = null

    fun getProfile(): DeviceProfile {
        return cachedProfile ?: detectProfile().also { cachedProfile = it }
    }

    fun resetCache() { cachedProfile = null }

    // ─────────────────────────────────────────────────────────────────────────
    //  DETECCIÓN PRINCIPAL
    // ─────────────────────────────────────────────────────────────────────────
    private fun detectProfile(): DeviceProfile {
        val cores     = detectCpuCores()
        val clusters  = detectCpuClusters(cores)
        val governors = detectAvailableGovernors()
        val gpuBack   = detectGpuBackend()
        val gpuPaths  = detectGpuPaths(gpuBack)
        val thermal   = detectThermalZones()
        val chipset   = detectChipset()

        return DeviceProfile(
            cpuCores     = cores,
            cpuClusters  = clusters,
            gpuBackend   = gpuBack,
            gpuPaths     = gpuPaths,
            thermalZones = thermal,
            governors    = governors,
            chipset      = chipset,
            isQualcomm   = chipset.contains("snapdragon", ignoreCase = true)
                        || chipset.contains("qualcomm",   ignoreCase = true)
                        || chipset.contains("SM8",        ignoreCase = false)
                        || File("/sys/class/kgsl").exists(),
            isMediatek   = chipset.contains("mediatek", ignoreCase = true)
                        || chipset.contains("helio",    ignoreCase = true)
                        || chipset.contains("dimensity", ignoreCase = true),
            isExynos     = chipset.contains("exynos",  ignoreCase = true)
                        || File("/sys/devices/platform/gpuss-0").exists(),
            isTensor     = chipset.contains("tensor", ignoreCase = true)
        )
    }

    // ── Detectar número de cores CPU ─────────────────────────────────────────
    private fun detectCpuCores(): Int {
        val cpuDir = File("/sys/devices/system/cpu")
        if (!cpuDir.exists()) return Runtime.getRuntime().availableProcessors()
        val count = cpuDir.listFiles()
            ?.count { it.name.matches(Regex("cpu\\d+")) } ?: 0
        return if (count > 0) count else Runtime.getRuntime().availableProcessors()
    }

    // ── Detectar clusters (agrupando por frecuencia máxima) ──────────────────
    private fun detectCpuClusters(totalCores: Int): List<CpuCluster> {
        val coresByMaxFreq = mutableMapOf<Long, MutableList<Int>>()
        val governorByCoreMap = mutableMapOf<Int, String>()

        for (i in 0 until totalCores) {
            val maxFreqFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            val govFile     = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor")
            val maxFreq     = maxFreqFile.readTextSafe()?.trim()?.toLongOrNull() ?: 0L
            val governor    = govFile.readTextSafe()?.trim() ?: "unknown"
            coresByMaxFreq.getOrPut(maxFreq) { mutableListOf() }.add(i)
            governorByCoreMap[i] = governor
        }

        return coresByMaxFreq.entries
            .sortedBy { it.key }
            .mapIndexed { idx, (maxFreq, cores) ->
                val minFreqFile = File("/sys/devices/system/cpu/cpu${cores.first()}/cpufreq/cpuinfo_min_freq")
                val minFreq = minFreqFile.readTextSafe()?.trim()?.toLongOrNull() ?: 0L
                CpuCluster(
                    id          = idx,
                    cores       = cores.sorted(),
                    maxFreqKhz  = maxFreq,
                    minFreqKhz  = minFreq,
                    governor    = governorByCoreMap[cores.first()] ?: "unknown"
                )
            }
    }

    // ── Detectar governors disponibles ───────────────────────────────────────
    private fun detectAvailableGovernors(): List<String> {
        val govFile = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors")
        val text = govFile.readTextSafe() ?: return listOf("schedutil", "performance", "powersave")
        return text.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
    }

    // ── Detectar backend de GPU ───────────────────────────────────────────────
    private fun detectGpuBackend(): GpuBackend {
        return when {
            File("/sys/class/kgsl/kgsl-3d0").exists()          -> GpuBackend.ADRENO   // Qualcomm
            File("/sys/class/misc/mali0").exists()             -> GpuBackend.MALI     // ARM Mali
            File("/sys/devices/platform/gpuss-0").exists()     -> GpuBackend.MALI     // Exynos Xclipse
            File("/sys/kernel/gpu").exists()                   -> GpuBackend.MALI     // Samsung Mali genérico
            File("/sys/class/pvrsrvkm").exists()               -> GpuBackend.POWERVR  // PowerVR
            else                                               -> GpuBackend.UNKNOWN
        }
    }

    // ── Detectar rutas de control de GPU según backend ───────────────────────
    private fun detectGpuPaths(backend: GpuBackend): GpuPaths {
        return when (backend) {
            GpuBackend.ADRENO -> GpuPaths(
                maxFreqPath  = findExisting(
                    "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
                    "/sys/class/kgsl/kgsl-3d0/max_gpuclk"
                ),
                minFreqPath  = findExisting(
                    "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq"
                ),
                governorPath = findExisting(
                    "/sys/class/kgsl/kgsl-3d0/devfreq/governor"
                ),
                curFreqPath  = findExisting(
                    "/sys/class/kgsl/kgsl-3d0/gpuclk",
                    "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq"
                ),
                busyPath     = findExisting(
                    "/sys/class/kgsl/kgsl-3d0/gpubusy"
                )
            )
            GpuBackend.MALI -> GpuPaths(
                maxFreqPath  = findExisting(
                    "/sys/kernel/gpu/gpu_max_clock",
                    "/sys/devices/platform/gpuss-0/devfreq/gpuss-0/max_freq",
                    "/sys/class/misc/mali0/device/devfreq/devfreq0/max_freq"
                ),
                minFreqPath  = findExisting(
                    "/sys/kernel/gpu/gpu_min_clock",
                    "/sys/devices/platform/gpuss-0/devfreq/gpuss-0/min_freq"
                ),
                governorPath = findExisting(
                    "/sys/kernel/gpu/gpu_governor",
                    "/sys/devices/platform/gpuss-0/devfreq/gpuss-0/governor"
                ),
                curFreqPath  = findExisting(
                    "/sys/kernel/gpu/gpu_clock",
                    "/sys/devices/platform/gpuss-0/devfreq/gpuss-0/cur_freq"
                ),
                busyPath     = findExisting(
                    "/sys/kernel/gpu/gpu_busy",
                    "/sys/devices/platform/gpuss-0/devfreq/gpuss-0/trans_stat"
                )
            )
            else -> GpuPaths(null, null, null, null, null)
        }
    }

    // ── Detectar zonas térmicas reales ────────────────────────────────────────
    private fun detectThermalZones(): List<ThermalZoneInfo> {
        val thermalDir = File("/sys/class/thermal")
        if (!thermalDir.exists()) return emptyList()

        return thermalDir.listFiles()
            ?.filter { it.name.startsWith("thermal_zone") }
            ?.mapNotNull { zone ->
                val index = zone.name.removePrefix("thermal_zone").toIntOrNull() ?: return@mapNotNull null
                val type  = File(zone, "type").readTextSafe()?.trim() ?: "unknown"
                val tempRaw = File(zone, "temp").readTextSafe()?.trim()?.toIntOrNull() ?: return@mapNotNull null
                val tempC = if (tempRaw > 1000) tempRaw / 1000f else tempRaw.toFloat()
                ThermalZoneInfo(
                    index = index,
                    type  = type,
                    path  = "${zone.absolutePath}/temp",
                    tempC = tempC
                )
            }
            ?.sortedBy { it.index }
            ?: emptyList()
    }

    // ── Detectar chipset ─────────────────────────────────────────────────────
    private fun detectChipset(): String {
        // 1. Build.HARDWARE (más confiable)
        val hw = Build.HARDWARE
        if (hw.isNotBlank() && hw != "unknown") return hw

        // 2. /proc/cpuinfo
        File("/proc/cpuinfo").readTextSafe()?.lines()?.forEach { line ->
            if (line.startsWith("Hardware", ignoreCase = true)) {
                return line.substringAfter(":").trim()
            }
        }

        // 3. Build props como fallback
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private fun findExisting(vararg paths: String): String? =
        paths.firstOrNull { File(it).exists() }

    private fun File.readTextSafe(): String? = try {
        if (exists() && canRead()) readText() else null
    } catch (_: Exception) { null }

    // ─────────────────────────────────────────────────────────────────────────
    //  API PÚBLICA — helpers de alto nivel para el resto de la app
    // ─────────────────────────────────────────────────────────────────────────

    /** Frecuencia máxima real del core más potente (kHz) */
    fun getPeakCoreMaxFreqKhz(): Long =
        getProfile().cpuClusters.maxOfOrNull { it.maxFreqKhz } ?: 0L

    /** Todos los índices de cores, agrupados por cluster de mayor a menor rendimiento */
    fun getCoresDescendingPerformance(): List<Int> =
        getProfile().cpuClusters
            .sortedByDescending { it.maxFreqKhz }
            .flatMap { it.cores }

    /** Ruta de max_freq para el governor del core indicado */
    fun getScalingMaxFreqPath(core: Int): String =
        "/sys/devices/system/cpu/cpu$core/cpufreq/scaling_max_freq"

    /** Ruta del governor del core indicado */
    fun getScalingGovernorPath(core: Int): String =
        "/sys/devices/system/cpu/cpu$core/cpufreq/scaling_governor"

    /** Devuelve el mejor governor disponible según prioridad */
    fun bestBalancedGovernor(): String {
        val avail = getProfile().governors
        return listOf("schedutil", "interactive", "ondemand", "conservative", "powersave")
            .firstOrNull { it in avail } ?: "schedutil"
    }

    fun bestPerformanceGovernor(): String {
        val avail = getProfile().governors
        return listOf("performance", "schedutil", "interactive", "ondemand")
            .firstOrNull { it in avail } ?: "performance"
    }

    fun bestPowersaveGovernor(): String {
        val avail = getProfile().governors
        return listOf("powersave", "conservative", "schedutil")
            .firstOrNull { it in avail } ?: "powersave"
    }

    /** Resumen legible del perfil para mostrar en UI de diagnóstico */
    fun getSummary(): String {
        val p = getProfile()
        val sb = StringBuilder()
        sb.appendLine("Chipset: ${p.chipset}")
        sb.appendLine("Cores: ${p.cpuCores} en ${p.cpuClusters.size} cluster(s)")
        p.cpuClusters.forEachIndexed { i, c ->
            sb.appendLine("  Cluster $i: cores ${c.cores} — max ${c.maxFreqKhz/1000}MHz — gov:${c.governor}")
        }
        sb.appendLine("GPU: ${p.gpuBackend} — max_freq: ${p.gpuPaths.maxFreqPath ?: "no detectado"}")
        sb.appendLine("Zonas térmicas: ${p.thermalZones.size}")
        val cpuZone = p.thermalZones.firstOrNull { it.type.contains("cpu", true) || it.type.contains("tsens", true) }
        if (cpuZone != null) sb.appendLine("  CPU zone: ${cpuZone.type} = ${cpuZone.tempC}°C")
        return sb.toString().trimEnd()
    }
}
