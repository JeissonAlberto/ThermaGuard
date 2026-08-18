package com.jeissonalberto.thermaguard.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RetentionPolicyTest {
    @Test
    fun supported_local_retention_values_are_preserved() {
        assertEquals(6, normalizeHistoryRetentionHours(6))
        assertEquals(24, normalizeHistoryRetentionHours(24))
        assertEquals(72, normalizeHistoryRetentionHours(72))
    }

    @Test
    fun unsupported_values_fall_back_to_the_default() {
        assertEquals(24, normalizeHistoryRetentionHours(0))
        assertEquals(24, normalizeHistoryRetentionHours(48))
    }
}
