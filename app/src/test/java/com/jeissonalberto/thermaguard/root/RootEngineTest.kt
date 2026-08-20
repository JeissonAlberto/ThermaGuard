package com.jeissonalberto.thermaguard.root

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RootEngineTest {
    @Test
    fun privileged_and_system_mutations_are_explicitly_unavailable() = runBlocking {
        assertFalse(RootEngine.isRootAvailable())
        assertFalse(RootEngine.setCpuMaxFreq(RootEngine.CpuLevel.THROTTLE))
        assertFalse(RootEngine.setCpuGovernor("schedutil"))
        assertFalse(RootEngine.setGpuMaxFreq(RootEngine.GpuLevel.THROTTLE))
        assertFalse(RootEngine.setBrightness(50))
        assertFalse(RootEngine.disableMobileData())
        assertFalse(RootEngine.enableMobileData())
        assertFalse(RootEngine.deactivateSuperCool())
        assertEquals(emptyMap<String, Long>(), RootEngine.readCurrentFreqs())
    }

    @Test
    fun super_cool_result_cannot_claim_actions_were_applied() {
        val result = RootEngine.SuperCoolResult.unsupported()

        assertFalse(result.cpuThrottled)
        assertFalse(result.gpuThrottled)
        assertFalse(result.brightnessSet)
        assertFalse(result.dataDisabled)
        assertEquals(0, result.appsKilled)
    }
}
