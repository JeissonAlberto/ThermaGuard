package com.jeissonalberto.thermaguard.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalDiagnosticContractTest {
    @Test
    fun initial_contract_marks_every_signal_as_not_read_yet() {
        val contract = ThermalDiagnosticContract.initial()

        assertEquals("WAITING", contract.appStatus)
        assertEquals(
            DiagnosticUnavailableReason.NOT_READ_YET,
            (contract.batteryTemperature as DiagnosticValue.Unavailable).reason
        )
        assertEquals(
            DiagnosticUnavailableReason.NOT_READ_YET,
            (contract.historyCount as DiagnosticValue.Unavailable).reason
        )
    }

    @Test
    fun contract_contains_only_observed_values_and_local_history() {
        val contract = buildThermalDiagnosticContract(
            observedAtMs = 123L,
            appStatus = "NOMINAL",
            batteryTemperature = 31.5f,
            systemThermalStatus = "NORMAL",
            batteryLevelPercent = 80,
            charging = true,
            batteryVoltageMv = 4_100,
            batteryCurrentMicroamps = 250_000,
            historyCount = 4,
            historyStorageError = false,
            kernelThermalZoneCount = 2
        )

        assertEquals(31.5f, contract.batteryTemperature.valueOrNull())
        assertEquals("NORMAL", contract.systemThermalStatus.valueOrNull())
        assertEquals(80, contract.batteryLevelPercent.valueOrNull())
        assertEquals(true, contract.charging.valueOrNull())
        assertEquals(4, contract.historyCount.valueOrNull())
        assertEquals(2, contract.kernelThermalZoneCount.valueOrNull())
    }

    @Test
    fun missing_platform_sensor_and_storage_are_explicit_after_a_read() {
        val contract = buildThermalDiagnosticContract(
            observedAtMs = 123L,
            appStatus = "SENSOR UNAVAILABLE",
            batteryTemperature = null,
            systemThermalStatus = null,
            batteryLevelPercent = null,
            charging = null,
            batteryVoltageMv = null,
            batteryCurrentMicroamps = null,
            historyCount = null,
            historyStorageError = true,
            kernelThermalZoneCount = null
        )

        assertEquals(
            DiagnosticUnavailableReason.SENSOR_NOT_EXPOSED,
            (contract.batteryTemperature as DiagnosticValue.Unavailable).reason
        )
        assertEquals(
            DiagnosticUnavailableReason.PLATFORM_STATUS_NOT_EXPOSED,
            (contract.systemThermalStatus as DiagnosticValue.Unavailable).reason
        )
        assertEquals(
            DiagnosticUnavailableReason.LOCAL_STORAGE_UNAVAILABLE,
            (contract.historyCount as DiagnosticValue.Unavailable).reason
        )
        assertTrue(contract.kernelThermalZoneCount is DiagnosticValue.Unavailable)
    }
}
