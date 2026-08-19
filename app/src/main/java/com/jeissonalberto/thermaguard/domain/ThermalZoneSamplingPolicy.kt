package com.jeissonalberto.thermaguard.domain

/**
 * Kernel thermal-zone reads are diagnostic detail, not the primary alert signal.
 * Keep them out of normal dashboard polling and refresh them at most every 15 s
 * only while the diagnosis screen is visible.
 */
internal const val HARDWARE_ZONE_REFRESH_INTERVAL_MS = 15_000L

internal fun shouldRefreshHardwareZones(
    diagnosticsVisible: Boolean,
    nowMs: Long,
    lastReadMs: Long
): Boolean = diagnosticsVisible &&
    (lastReadMs == 0L || nowMs - lastReadMs >= HARDWARE_ZONE_REFRESH_INTERVAL_MS)
