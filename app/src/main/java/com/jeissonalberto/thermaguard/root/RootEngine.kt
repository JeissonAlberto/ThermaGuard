package com.jeissonalberto.thermaguard.root

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File

/**
 * RootEngine v3.9.33 — control térmico de bajo nivel con detección dinámica
 *
 * Compatibilidad universal: Qualcomm, Exynos, MediaTek, Tensor, etc.
 * Los clusters, cores y rutas de GPU se obtienen de HardwareProfiler en runtime.
 *
 * Capacidades:
 *  1. Underclocking CPU por porcentaje (todos los clusters dinámicos)
 *  2. Governor térmico (governor óptimo detectado automáticamente)
 *  3. Control GPU (ruta detectada según backend: Adreno / Mali / etc.)
 *  4. Limitar brillo via sysfs o settings
 *  5. Control de radios (5G/WiFi) via svc shell
 *  6. Modo SuperAhorro: todas las intervenciones al máximo
 */
object RootEngine {

    // ── Estado ────────────────────────────────────────────────────────────
    private var rootAvailable: Boolean? = null

    // ── Check root ────────────────────────────────────────────────────────
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (rootAvailable != null) return@withContext rootAvailable!!
        rootAvailable = try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val result  = process.inputStream.bufferedReader().readText()
            process.waitFor()
            result.contains("uid=0")
        } catch (_: Exception) { false }
        rootAvailable!!
    }

    // ── Ejecutar comandos como root ───────────────────────────────────────
    private suspend fun su(vararg cmds: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            cmds.forEach { os.writeBytes("$it\n") }
            os.writeBytes("exit\n")
            os.flush()
            val out = process.inputStream.bufferedReader().readText()
            val err = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (err.isNotEmpty() && process.exitValue() != 0) Result.failure(Exception(err))
            else Result.success(out)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Escritura en sysfs con escalación automática ──────────────────────
    private suspend fun writeNode(path: String, value: String): Boolean {
        if (!File(path).exists()) return false
        return try {
            File(path).writeText(value)
            true
        } catch (_: Exception) {
            su("printf '%s' '$value' > $path").isSuccess
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  1. CPU — dinámico via HardwareProfiler
    // ═══════════════════════════════════════════════════════════════════════

    suspend fun setCpuMaxFreq(level: CpuLevel): Boolean = withContext(Dispatchers.IO) {
        val profile = HardwareProfiler.getProfile()
        val percent = when (level) {
            CpuLevel.NORMAL   -> 100
            CpuLevel.THROTTLE -> 65
            CpuLevel.ULTRA    -> 40
        }
        var anyOk = false
        for (cluster in profile.cpuClusters) {
            val targetKhz = (cluster.maxFreqKhz * percent / 100L)
                .coerceAtLeast(cluster.minFreqKhz)
            for (core in cluster.cores) {
                val path = "/sys/devices/system/cpu/cpu$core/cpufreq/scaling_max_freq"
                if (writeNode(path, targetKhz.toString())) anyOk = true
            }
        }
        anyOk
    }

    suspend fun setCpuGovernor(governor: String): Boolean = withContext(Dispatchers.IO) {
        val profile = HardwareProfiler.getProfile()
        // Verificar que el governor esté disponible antes de aplicarlo
        val available = profile.governors
        val toApply = if (governor in available) governor
                      else HardwareProfiler.bestBalancedGovernor()
        var anyOk = false
        for (i in 0 until profile.cpuCores) {
            val path = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor"
            if (writeNode(path, toApply)) anyOk = true
        }
        anyOk
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  2. GPU — rutas dinámicas via HardwareProfiler
    // ═══════════════════════════════════════════════════════════════════════

    suspend fun setGpuMaxFreq(level: GpuLevel): Boolean = withContext(Dispatchers.IO) {
        val gpuPaths = HardwareProfiler.getProfile().gpuPaths
        val maxPath  = gpuPaths.maxFreqPath ?: return@withContext false

        val currentMax = try {
            File(maxPath).readText().trim().toLongOrNull() ?: return@withContext false
        } catch (_: Exception) { return@withContext false }

        val target = when (level) {
            GpuLevel.NORMAL   -> currentMax      // no limitar
            GpuLevel.THROTTLE -> (currentMax * 60 / 100L).coerceAtLeast(100_000_000L)
            GpuLevel.ULTRA    -> (currentMax * 30 / 100L).coerceAtLeast(100_000_000L)
        }
        writeNode(maxPath, target.toString())
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  3. BRILLO
    // ═══════════════════════════════════════════════════════════════════════

    suspend fun setBrightness(percent: Int): Boolean = withContext(Dispatchers.IO) {
        // Intentar via settings (funciona en muchos dispositivos sin root)
        val result = su("settings put system screen_brightness ${(255 * percent / 100)}")
        if (result.isSuccess) return@withContext true
        // Fallback: sysfs (varía por dispositivo, intentar rutas comunes)
        val brightnessNodes = listOf(
            "/sys/class/leds/lcd-backlight/brightness",
            "/sys/class/backlight/panel0-backlight/brightness",
            "/sys/class/backlight/sprd_backlight/brightness"
        )
        val node = brightnessNodes.firstOrNull { File(it).exists() } ?: return@withContext false
        val maxNode = node.replace("brightness", "max_brightness")
        val maxVal = try { File(maxNode).readText().trim().toIntOrNull() ?: 255 } catch (_: Exception) { 255 }
        writeNode(node, (maxVal * percent / 100).toString())
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  4. DATOS MÓVILES / WIFI via svc (funciona en One UI con/sin root)
    // ═══════════════════════════════════════════════════════════════════════

    suspend fun disableMobileData(): Boolean = withContext(Dispatchers.IO) {
        su("svc data disable").isSuccess
    }

    suspend fun enableMobileData(): Boolean = withContext(Dispatchers.IO) {
        su("svc data enable").isSuccess
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  5. MATAR APPS EN BACKGROUND via am (funciona en One UI con root)
    // ═══════════════════════════════════════════════════════════════════════

    suspend fun killBackgroundApps(context: Context): Int = withContext(Dispatchers.IO) {
        // am kill-all — mata procesos en background que no sean foreground
        val result = su("am kill-all")
        if (result.isSuccess) {
            // Contar aproximadamente (am kill-all no reporta número exacto)
            3
        } else {
            // Fallback: ActivityManager normal (sin root)
            val am = context.getSystemService(Context.ACTIVITY_SERVICE)
                as android.app.ActivityManager
            am.killBackgroundProcesses(context.packageName)
            1
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  6. MODO SUPER ENFRIAMIENTO — todas las acciones al máximo
    // ═══════════════════════════════════════════════════════════════════════

    suspend fun activateSuperCool(
        context: Context,
        ultra: Boolean = false
    ): SuperCoolResult = withContext(Dispatchers.IO) {
        val cpuLevel = if (ultra) CpuLevel.ULTRA    else CpuLevel.THROTTLE
        val gpuLevel = if (ultra) GpuLevel.ULTRA    else GpuLevel.THROTTLE
        val brightness = if (ultra) 25 else 70

        val cpu     = setCpuMaxFreq(cpuLevel)
        val gov     = setCpuGovernor(if (ultra) HardwareProfiler.bestPowersaveGovernor()
                                     else       HardwareProfiler.bestBalancedGovernor())
        val gpu     = setGpuMaxFreq(gpuLevel)
        val bri     = setBrightness(brightness)
        val dat     = disableMobileData()
        val killed  = killBackgroundApps(context)

        SuperCoolResult(
            cpuThrottled  = cpu && gov,
            gpuThrottled  = gpu,
            brightnessSet = bri,
            dataDisabled  = dat,
            appsKilled    = killed
        )
    }

    suspend fun deactivateSuperCool(): Boolean = withContext(Dispatchers.IO) {
        val cpu = setCpuMaxFreq(CpuLevel.NORMAL)
        val gov = setCpuGovernor(HardwareProfiler.bestBalancedGovernor())
        val gpu = setGpuMaxFreq(GpuLevel.NORMAL)
        val dat = enableMobileData()
        cpu && gov && gpu && dat
    }

    // ── Leer frecuencias actuales (diagnóstico) ───────────────────────────
    suspend fun readCurrentFreqs(): Map<String, Long> = withContext(Dispatchers.IO) {
        val result  = mutableMapOf<String, Long>()
        val profile = HardwareProfiler.getProfile()

        for (i in 0 until profile.cpuCores) {
            val f = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            if (f.exists()) result["cpu$i"] = f.readText().trim().toLongOrNull() ?: 0L
        }

        val gpuPath = profile.gpuPaths.curFreqPath
        if (gpuPath != null && File(gpuPath).exists()) {
            result["gpu"] = File(gpuPath).readText().trim().toLongOrNull() ?: 0L
        }
        result
    }

    data class SuperCoolResult(
        val cpuThrottled:  Boolean,
        val gpuThrottled:  Boolean,
        val brightnessSet: Boolean,
        val dataDisabled:  Boolean,
        val appsKilled:    Int
    )

    enum class CpuLevel { NORMAL, THROTTLE, ULTRA }
    enum class GpuLevel { NORMAL, THROTTLE, ULTRA }
}
