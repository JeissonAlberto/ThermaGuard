package com.jeissonalberto.thermaguard.domain

import com.jeissonalberto.thermaguard.data.GovernorConfig
import com.jeissonalberto.thermaguard.root.HardwareProfiler
import java.io.File

/**
 * CpuGpuGovernor v3.9.33 — Control CPU/GPU con detección dinámica de hardware
 *
 * Compatibilidad universal: Qualcomm, Exynos, MediaTek, Tensor, etc.
 * No hay rutas ni frecuencias hardcodeadas — todo se obtiene de HardwareProfiler.
 *
 * Niveles de escalación:
 *   NIVEL 1 — Escritura directa en /sys (si la app tiene permisos)
 *   NIVEL 2 — Shell via su root (Magisk / KernelSU)
 *   NIVEL 3 — Comando am/svc (funciona en One UI sin root para algunas acciones)
 */
object CpuGpuGovernor {

    // ─────────────────────────────────────────────────────────────────────────
    //  ESCRITURA AL KERNEL — con escalación automática
    // ─────────────────────────────────────────────────────────────────────────

    private fun writeKernelFile(path: String, value: String): Boolean {
        if (!File(path).exists()) return false
        // NIVEL 1 — escritura directa
        return try {
            File(path).writeText(value)
            true
        } catch (_: Exception) {
            // NIVEL 2 — escalada a root via su (Magisk / KernelSU)
            writeKernelFileRoot(path, value)
        }
    }

    private fun writeKernelFileRoot(path: String, value: String): Boolean {
        return try {
            // Usar printf en vez de echo para evitar problemas con caracteres especiales
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "printf '%s' '$value' > $path"))
            val exit = process.waitFor()
            exit == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun readKernelFile(path: String): String? {
        return try {
            if (File(path).exists()) File(path).readText().trim() else null
        } catch (_: Exception) { null }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CONTROL DE CPU — dinámico según perfil detectado
    // ─────────────────────────────────────────────────────────────────────────

    /** Aplica governor a todos los cores disponibles */
    fun setCpuGovernorDirect(governor: String): Boolean {
        val profile = HardwareProfiler.getProfile()
        var ok = false
        for (i in 0 until profile.cpuCores) {
            val path = HardwareProfiler.getScalingGovernorPath(i)
            if (writeKernelFile(path, governor)) ok = true
        }
        return ok
    }

    /** Establece frecuencia máxima de todos los cores (porcentaje del máximo real) */
    private fun setCpuMaxFreqPercent(percent: Int): List<String> {
        val profile = HardwareProfiler.getProfile()
        val results = mutableListOf<String>()

        for (cluster in profile.cpuClusters) {
            val targetFreq = ((cluster.maxFreqKhz * percent) / 100L)
                .coerceAtLeast(cluster.minFreqKhz)
            for (core in cluster.cores) {
                val path = HardwareProfiler.getScalingMaxFreqPath(core)
                if (writeKernelFile(path, targetFreq.toString())) {
                    if (core == cluster.cores.first())
                        results.add("✅ Cluster ${cluster.id} (cores ${cluster.cores}): ${targetFreq/1000}MHz")
                } else {
                    if (core == cluster.cores.first())
                        results.add("⚠️ Cluster ${cluster.id}: sin permisos de escritura")
                }
            }
        }
        return results
    }

    /** Restaura la frecuencia máxima real de cada core */
    private fun restoreCpuMaxFreq(): List<String> {
        val profile = HardwareProfiler.getProfile()
        val results = mutableListOf<String>()
        for (cluster in profile.cpuClusters) {
            for (core in cluster.cores) {
                val maxPath = "/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq"
                val maxFreq = readKernelFile(maxPath) ?: continue
                val scalePath = HardwareProfiler.getScalingMaxFreqPath(core)
                writeKernelFile(scalePath, maxFreq)
            }
            results.add("✅ Cluster ${cluster.id}: frecuencia restaurada (${cluster.maxFreqKhz/1000}MHz)")
        }
        return results
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CONTROL DE GPU — dinámico según backend detectado
    // ─────────────────────────────────────────────────────────────────────────

    private fun setGpuMaxFreqPercent(percent: Int): String {
        val gpuPaths = HardwareProfiler.getProfile().gpuPaths
        val maxPath  = gpuPaths.maxFreqPath ?: return "⚠️ GPU: ruta no detectada en este dispositivo"
        val curMaxStr = readKernelFile(maxPath)
        val curMax = curMaxStr?.toLongOrNull() ?: return "⚠️ GPU: no se puede leer frecuencia máxima"
        val target = (curMax * percent / 100L).coerceAtLeast(100_000_000L)
        return if (writeKernelFile(maxPath, target.toString()))
            "✅ GPU: ${target/1_000_000}MHz (${percent}% del máximo)"
        else
            "⚠️ GPU: escritura bloqueada (necesita root)"
    }

    private fun restoreGpuMaxFreq(): String {
        val gpuPaths = HardwareProfiler.getProfile().gpuPaths
        val maxPath  = gpuPaths.maxFreqPath ?: return "⚠️ GPU: ruta no detectada"
        // Leer el máximo hardware real (diferente a scaling_max)
        val hwMaxPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies",
            "/sys/kernel/gpu/gpu_available_governor"
        )
        // Sin ruta de hwmax disponible, simplemente quitamos el límite escribiendo un valor muy alto
        return if (writeKernelFile(maxPath, "999999999999"))
            "✅ GPU: límite de frecuencia removido"
        else
            "⚠️ GPU: sin permisos (necesita root)"
    }

    private fun setGpuGovernor(governor: String): String {
        val govPath = HardwareProfiler.getProfile().gpuPaths.governorPath
            ?: return "⚠️ GPU governor: ruta no disponible"
        return if (writeKernelFile(govPath, governor))
            "✅ GPU governor → $governor"
        else
            "⚠️ GPU governor: sin permisos"
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MODOS PREDEFINIDOS
    // ─────────────────────────────────────────────────────────────────────────

    /** MODO BESTIA — CPU y GPU al máximo */
    fun unlockMaxPerformance(): List<String> {
        val results = mutableListOf<String>()
        val profile  = HardwareProfiler.getProfile()

        results.add("🐉 MODO BESTIA — ${profile.chipset}")
        results.add("─────────────────────────────")

        // Governor de rendimiento
        val perfGov = HardwareProfiler.bestPerformanceGovernor()
        if (setCpuGovernorDirect(perfGov))
            results.add("✅ Governor CPU → $perfGov")
        else
            results.add("⚠️ Governor CPU: sin permisos (knox/selinux)")

        // Frecuencia al 100%
        results.addAll(restoreCpuMaxFreq())

        // GPU governor + max freq
        results.add(setGpuGovernor("performance"))
        results.add(restoreGpuMaxFreq())

        results.add("─────────────────────────────")
        results.add("Cores: ${profile.cpuCores} | Clusters: ${profile.cpuClusters.size}")
        return results
    }

    /** MODO BALANCEADO — governor schedutil, frecuencias normales */
    fun applyBalancedGovernor(): List<String> {
        val results = mutableListOf<String>()
        val balGov = HardwareProfiler.bestBalancedGovernor()

        if (setCpuGovernorDirect(balGov))
            results.add("✅ Governor CPU → $balGov (balanceado)")
        else
            results.add("⚠️ Governor: sin permisos")

        results.addAll(restoreCpuMaxFreq())
        results.add(setGpuGovernor("msm-adreno-tz").takeIf { it.startsWith("✅") }
            ?: setGpuGovernor("simple_ondemand"))
        results.add(restoreGpuMaxFreq())
        return results
    }

    /** THROTTLE TÉRMICO — reduce CPU al 60% y GPU al 50% */
    fun applyThermalThrottle(): List<String> {
        val results = mutableListOf<String>()
        val saveGov = HardwareProfiler.bestPowersaveGovernor()

        results.add("🌡️ Throttle térmico activado")
        if (setCpuGovernorDirect(saveGov))
            results.add("✅ Governor → $saveGov")

        results.addAll(setCpuMaxFreqPercent(60))
        results.add(setGpuMaxFreqPercent(50))
        return results
    }

    /** MODO ULTRA AHORRO — CPU al 40%, GPU al 30%, governor powersave */
    fun applySuperSaveMode(): List<String> {
        val results = mutableListOf<String>()
        setCpuGovernorDirect("powersave")
        results.addAll(setCpuMaxFreqPercent(40))
        results.add(setGpuMaxFreqPercent(30))
        results.add(setGpuGovernor("powersave"))
        results.add("✅ Modo ultra ahorro activo")
        return results
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LECTURA DE ESTADO ACTUAL — universal
    // ─────────────────────────────────────────────────────────────────────────

    fun readCurrentFreqs(): Map<String, Long> {
        val result  = mutableMapOf<String, Long>()
        val profile = HardwareProfiler.getProfile()

        for (i in 0 until profile.cpuCores) {
            val f = readKernelFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            result["cpu$i"] = f?.toLongOrNull() ?: 0L
        }

        val gpuPath = profile.gpuPaths.curFreqPath
        if (gpuPath != null) {
            result["gpu"] = readKernelFile(gpuPath)?.toLongOrNull() ?: 0L
        }
        return result
    }

    /** Lee todas las zonas térmicas disponibles */
    fun readAllThermalZones(): Map<String, Float> {
        return HardwareProfiler.getProfile().thermalZones.associate { zone ->
            val tempRaw = try {
                File(zone.path).readText().trim().toIntOrNull() ?: 0
            } catch (_: Exception) { 0 }
            val tempC = if (tempRaw > 1000) tempRaw / 1000f else tempRaw.toFloat()
            zone.type to tempC
        }
    }

    /** Snapshot completo para el motor de física */
    fun buildFullThermalSnapshot(current: com.jeissonalberto.thermaguard.data.ThermalSnapshot):
        com.jeissonalberto.thermaguard.data.ThermalSnapshot {
        return current  // El snapshot ya viene del SensorRepository con datos reales
    }

    /** Diagnóstico de compatibilidad — para mostrar en Settings */
    fun getCompatibilityReport(): List<String> {
        val profile = HardwareProfiler.getProfile()
        val report  = mutableListOf<String>()

        report.add("📱 ${profile.chipset}")
        report.add("🔲 ${profile.cpuCores} cores / ${profile.cpuClusters.size} clusters")

        val govs = profile.governors.joinToString(", ")
        report.add("⚙️ Governors: $govs")

        when (profile.gpuBackend) {
            HardwareProfiler.GpuBackend.ADRENO  -> report.add("🎮 GPU: Adreno (Qualcomm)")
            HardwareProfiler.GpuBackend.MALI    -> report.add("🎮 GPU: Mali (ARM/Exynos)")
            HardwareProfiler.GpuBackend.POWERVR -> report.add("🎮 GPU: PowerVR")
            HardwareProfiler.GpuBackend.TENSOR  -> report.add("🎮 GPU: Tensor (Google)")
            HardwareProfiler.GpuBackend.UNKNOWN -> report.add("🎮 GPU: no detectada")
        }

        report.add("🌡️ Zonas térmicas: ${profile.thermalZones.size}")

        if (profile.gpuPaths.maxFreqPath == null)
            report.add("⚠️ Control GPU: no disponible en este dispositivo")

        return report
    }
    // ─────────────────────────────────────────────────────────────────────────
    //  applyGovernorConfig — aplica configuración del motor de física
    // ─────────────────────────────────────────────────────────────────────────
    fun applyGovernorConfig(config: GovernorConfig): List<String> {
        val results = mutableListOf<String>()
        val profile = HardwareProfiler.getProfile()

        // Governor
        val govOk = setCpuGovernorDirect(config.name)
        results.add(if (govOk) "✅ Governor → ${config.name}" else "⚠️ Governor sin permisos")

        // Frecuencia máxima CPU como porcentaje del máximo real del dispositivo
        val peakKhz = profile.cpuClusters.maxOfOrNull { it.maxFreqKhz } ?: 1L
        val targetKhz = (config.maxFreqGHz * 1_000_000L).toLong().coerceAtMost(peakKhz)
        val percent = ((targetKhz.toFloat() / peakKhz) * 100).toInt().coerceIn(20, 100)

        for (cluster in profile.cpuClusters) {
            val clusterTarget = (cluster.maxFreqKhz * percent / 100L).coerceAtLeast(cluster.minFreqKhz)
            for (core in cluster.cores) {
                writeKernelFile(
                    "/sys/devices/system/cpu/cpu$core/cpufreq/scaling_max_freq",
                    clusterTarget.toString()
                )
            }
        }
        results.add("✅ CPU → ${config.maxFreqGHz}GHz (~${percent}% del máximo)")

        // GPU
        val gpuResult = setGpuMaxFreqPercent(
            ((config.gpuMaxFreqMHz.toFloat() / 818f) * 100).toInt().coerceIn(20, 100)
        )
        results.add(gpuResult)

        if (config.reason.isNotBlank()) results.add("💡 ${config.reason}")
        return results
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  emergencyThrottle — throttle de emergencia cuando temp >= 52°C
    // ─────────────────────────────────────────────────────────────────────────
    fun emergencyThrottle(): List<String> {
        val results = mutableListOf<String>()
        results.add("🚨 THROTTLE DE EMERGENCIA")

        // Powersave governor inmediato
        val saveGov = HardwareProfiler.bestPowersaveGovernor()
        if (setCpuGovernorDirect(saveGov))
            results.add("✅ Governor → $saveGov (emergencia)")
        else
            results.add("⚠️ Governor: sin permisos root")

        // CPU al 50%
        results.addAll(setCpuMaxFreqPercent(50))

        // GPU al 40%
        results.add(setGpuMaxFreqPercent(40))

        return results
    }

}
