package com.jeissonalberto.thermaguard.root

import android.content.Context

/**
 * Quarantined compatibility facade for the former root/system-control API.
 *
 * ThermaGuard does not request root access and must not write sysfs nodes,
 * execute shell commands, change radios/brightness, kill processes, or claim
 * CPU/GPU control. The old API surface remains only to avoid accidental source
 * breakage in code that may still reference it; every mutating operation is an
 * explicit no-op and reports that the requested capability is unavailable.
 */
object RootEngine {

    /** Root is never a supported runtime capability of this application. */
    suspend fun isRootAvailable(): Boolean = false

    /** CPU frequency and governor changes are intentionally unsupported. */
    suspend fun setCpuMaxFreq(level: CpuLevel): Boolean = false

    suspend fun setCpuGovernor(governor: String): Boolean = false

    /** GPU frequency changes are intentionally unsupported. */
    suspend fun setGpuMaxFreq(level: GpuLevel): Boolean = false

    /** System brightness changes are intentionally unsupported. */
    suspend fun setBrightness(percent: Int): Boolean = false

    /** Radio changes are intentionally unsupported. */
    suspend fun disableMobileData(): Boolean = false

    suspend fun enableMobileData(): Boolean = false

    /** Process termination is intentionally unsupported. */
    suspend fun killBackgroundApps(context: Context): Int = 0

    /**
     * The former super-cooling operation performs no system action and reports
     * every capability as unavailable.
     */
    suspend fun activateSuperCool(
        context: Context,
        ultra: Boolean = false
    ): SuperCoolResult = SuperCoolResult.unsupported()

    suspend fun deactivateSuperCool(): Boolean = false

    /** Frequency control is unsupported; no kernel nodes are read here. */
    suspend fun readCurrentFreqs(): Map<String, Long> = emptyMap()

    data class SuperCoolResult(
        val cpuThrottled: Boolean,
        val gpuThrottled: Boolean,
        val brightnessSet: Boolean,
        val dataDisabled: Boolean,
        val appsKilled: Int
    ) {
        companion object {
            fun unsupported() = SuperCoolResult(
                cpuThrottled = false,
                gpuThrottled = false,
                brightnessSet = false,
                dataDisabled = false,
                appsKilled = 0
            )
        }
    }

    enum class CpuLevel { NORMAL, THROTTLE, ULTRA }
    enum class GpuLevel { NORMAL, THROTTLE, ULTRA }
}
