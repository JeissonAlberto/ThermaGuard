package com.jeissonalberto.thermaguard.root

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File

/**
 * RootEngine — control térmico de bajo nivel para Galaxy S22 Snapdragon
 * Requiere acceso root (Magisk / KernelSU / su binario en PATH)
 *
 * Capacidades:
 *  1. Underclocking CPU por cluster (Silver/Gold/Prime)
 *  2. Governor térmico (schedutil → powersave bajo carga térmica)
 *  3. Underclocking GPU (gpuss-0)
 *  4. Limitar brillo directamente via sysfs
 *  5. Apagar radio 5G/WiFi via shell
 *  6. Modo SuperAhorro: todas las intervenciones al máximo
 */
object RootEngine {

    // ── Rutas Snapdragon 8 Gen 1 / SM8450 ────────────────────────────────
    private const val CPU_BASE = "/sys/devices/system/cpu"
    // Silver cluster: cpu0-cpu3 (pequeños / eficiencia)
    // Gold cluster:   cpu4-cpu6
    // Prime:          cpu7
    private val SILVER = (0..3).map { it }
    private val GOLD   = (4..6).map { it }
    private val PRIME  = listOf(7)

    // Frecuencias máximas Snapdragon 8 Gen 1
    // Normal: Silver=1804800, Gold=2649600, Prime=3000000 (kHz)
    // Throttle seguro: Silver=1209600, Gold=1804800, Prime=1804800
    // Ultra ahorro:    Silver=768000,  Gold=1209600, Prime=1209600
    private val FREQ_NORMAL  = mapOf(0 to 1804800, 4 to 2649600, 7 to 3000000)
    private val FREQ_THROTTLE= mapOf(0 to 1209600, 4 to 1804800, 7 to 1804800)
    private val FREQ_ULTRA   = mapOf(0 to 768000,  4 to 1209600, 7 to 1209600)

    private const val GPU_MAX_FREQ = "/sys/class/kgsl/kgsl-3d0/max_gpuclk"
    private const val GPU_NORMAL   = 818000000  // 818 MHz
    private const val GPU_THROTTLE = 490000000  // 490 MHz
    private const val GPU_ULTRA    = 257000000  // 257 MHz

    // ── Estado ────────────────────────────────────────────────────────────
    private var rootAvailable: Boolean? = null  // null = no chequeado aún

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

    // ═══════════════════════════════════════════════════════════════════════
    //  1. CPU UNDERCLOCKING
    // ═══════════════════════════════════════════════════════════════════════
    suspend fun setCpuMaxFreq(level: CpuLevel): Boolean {
        val freqMap = when (level) {
            CpuLevel.NORMAL   -> FREQ_NORMAL
            CpuLevel.THROTTLE -> FREQ_THROTTLE
            CpuLevel.ULTRA    -> FREQ_ULTRA
        }
        val cmds = mutableListOf<String>()
        for (core in 0..7) {
            val freqFile = "$CPU_BASE/cpu$core/cpufreq/scaling_max_freq"
            if (!File(freqFile).exists()) continue
            // Tomar la frecuencia del primer core de cada cluster
            val freq = when (core) {
                in SILVER -> freqMap[0]
                in GOLD   -> freqMap[4]
                in PRIME  -> freqMap[7]
                else -> null
            } ?: continue
            cmds.add("echo $freq > $freqFile")
        }
        return su(*cmds.toTypedArray()).isSuccess
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  2. CPU GOVERNOR
    // ═══════════════════════════════════════════════════════════════════════
    suspend fun setCpuGovernor(governor: String): Boolean {
        val cmds = (0..7).map { core ->
            "echo $governor > $CPU_BASE/cpu$core/cpufreq/scaling_governor 2>/dev/null || true"
        }
        return su(*cmds.toTypedArray()).isSuccess
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  3. GPU UNDERCLOCKING
    // ═══════════════════════════════════════════════════════════════════════
    suspend fun setGpuMaxFreq(level: GpuLevel): Boolean {
        val freq = when (level) {
            GpuLevel.NORMAL   -> GPU_NORMAL
            GpuLevel.THROTTLE -> GPU_THROTTLE
            GpuLevel.ULTRA    -> GPU_ULTRA
        }
        val result = su("echo $freq > $GPU_MAX_FREQ")
        return result.isSuccess
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  4. BRILLO DIRECTO (sin permiso WRITE_SETTINGS)
    // ═══════════════════════════════════════════════════════════════════════
    suspend fun setBrightness(value: Int /* 0-255 */): Boolean {
        val clamped = value.coerceIn(10, 255)
        return su(
            "settings put system screen_brightness $clamped",
            "settings put system screen_brightness_mode 0"
        ).isSuccess
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  5. RADIO CONTROL
    // ═══════════════════════════════════════════════════════════════════════
    suspend fun disableMobileData(): Boolean =
        su("svc data disable").isSuccess

    suspend fun enableMobileData(): Boolean =
        su("svc data enable").isSuccess

    suspend fun disableWifi(): Boolean =
        su("svc wifi disable").isSuccess

    suspend fun enableWifi(): Boolean =
        su("svc wifi enable").isSuccess

    suspend fun set5GOnly(enable: Boolean): Boolean {
        // En One UI: NETWORK_MODE_LTE_ONLY = 11, LTE+NR = 33
        val mode = if (enable) 33 else 11
        return su("settings put global preferred_network_mode $mode").isSuccess
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  6. MATAR APPS EN SEGUNDO PLANO
    // ═══════════════════════════════════════════════════════════════════════
    suspend fun killBackgroundApps(context: Context): Int {
        // Obtener PIDs de apps en segundo plano
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val procs = am.runningAppProcesses ?: return 0
        val toKill = procs.filter { it.importance > android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE }
        if (toKill.isEmpty()) return 0
        val cmds = toKill.map { p -> "kill -9 ${p.pid} 2>/dev/null" }
        return if (su(*cmds.toTypedArray()).isSuccess) toKill.size else 0
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  7. MODO SUPER AHORRO — todas las intervenciones al máximo
    // ═══════════════════════════════════════════════════════════════════════
    data class SuperCoolResult(
        val cpuThrottled: Boolean,
        val gpuThrottled: Boolean,
        val brightnessSet: Boolean,
        val dataDisabled: Boolean,
        val appsKilled: Int
    )

    suspend fun activateSuperCool(context: Context, ultra: Boolean = false): SuperCoolResult {
        val cpuLevel = if (ultra) CpuLevel.ULTRA else CpuLevel.THROTTLE
        val gpuLevel = if (ultra) GpuLevel.ULTRA else GpuLevel.THROTTLE

        val cpu = setCpuMaxFreq(cpuLevel)
        val gov = setCpuGovernor(if (ultra) "powersave" else "schedutil")
        val gpu = setGpuMaxFreq(gpuLevel)
        val bri = setBrightness(if (ultra) 30 else 80)
        val dat = disableMobileData()
        val killed = killBackgroundApps(context)

        return SuperCoolResult(
            cpuThrottled  = cpu && gov,
            gpuThrottled  = gpu,
            brightnessSet = bri,
            dataDisabled  = dat,
            appsKilled    = killed
        )
    }

    suspend fun deactivateSuperCool(): Boolean {
        val cpu = setCpuMaxFreq(CpuLevel.NORMAL)
        val gov = setCpuGovernor("schedutil")
        val gpu = setGpuMaxFreq(GpuLevel.NORMAL)
        val dat = enableMobileData()
        return cpu && gov && gpu && dat
    }

    // ── Leer frecuencias actuales (diagnóstico) ───────────────────────────
    suspend fun readCurrentFreqs(): Map<String, Long> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, Long>()
        for (core in 0..7) {
            val f = File("$CPU_BASE/cpu$core/cpufreq/scaling_cur_freq")
            if (f.exists()) result["cpu$core"] = f.readText().trim().toLongOrNull() ?: 0L
        }
        val gpuFile = File("/sys/class/kgsl/kgsl-3d0/gpuclk")
        if (gpuFile.exists()) result["gpu"] = gpuFile.readText().trim().toLongOrNull() ?: 0L
        result
    }

    enum class CpuLevel { NORMAL, THROTTLE, ULTRA }
    enum class GpuLevel { NORMAL, THROTTLE, ULTRA }
}
