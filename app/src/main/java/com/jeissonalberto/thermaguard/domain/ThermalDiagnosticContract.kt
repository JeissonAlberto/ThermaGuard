package com.jeissonalberto.thermaguard.domain

/**
 * Read-only value reported by the diagnostic pipeline.
 *
 * An unavailable value is represented explicitly instead of being replaced by
 * zero, a cached value, or a prediction. The contract contains only readings
 * already observed by Android or local Room state.
 */
sealed interface DiagnosticValue<out T> {
    data class Available<T>(val value: T) : DiagnosticValue<T>
    data class Unavailable(val reason: DiagnosticUnavailableReason) : DiagnosticValue<Nothing>
}

enum class DiagnosticUnavailableReason(val label: String) {
    NOT_READ_YET("todavía no se ha recibido una lectura"),
    SENSOR_NOT_EXPOSED("Android no expone esta señal en este dispositivo"),
    PLATFORM_STATUS_NOT_EXPOSED("Android no expone el estado térmico agregado"),
    LOCAL_STORAGE_UNAVAILABLE("el almacenamiento local no está disponible"),
    KERNEL_ZONES_NOT_EXPOSED("no hay zonas térmicas del kernel legibles")
}

fun <T> DiagnosticValue<T>.valueOrNull(): T? =
    (this as? DiagnosticValue.Available<T>)?.value

fun DiagnosticValue<*>.unavailableLabel(): String = when (this) {
    is DiagnosticValue.Available<*> -> "disponible"
    is DiagnosticValue.Unavailable -> reason.label
}

/**
 * Stable diagnostic contract consumed by the UI and future read-only adapters.
 * It has no mutating operation and intentionally does not expose CPU/GPU
 * temperatures, process control, or inferred risk values.
 */
data class ThermalDiagnosticContract(
    val observedAtMs: Long?,
    val appStatus: String,
    val batteryTemperature: DiagnosticValue<Float>,
    val systemThermalStatus: DiagnosticValue<String>,
    val batteryLevelPercent: DiagnosticValue<Int>,
    val charging: DiagnosticValue<Boolean>,
    val batteryVoltageMv: DiagnosticValue<Int>,
    val batteryCurrentMicroamps: DiagnosticValue<Int>,
    val historyCount: DiagnosticValue<Int>,
    val kernelThermalZoneCount: DiagnosticValue<Int>
) {
    companion object {
        fun initial(): ThermalDiagnosticContract = ThermalDiagnosticContract(
            observedAtMs = null,
            appStatus = "WAITING",
            batteryTemperature = DiagnosticValue.Unavailable(DiagnosticUnavailableReason.NOT_READ_YET),
            systemThermalStatus = DiagnosticValue.Unavailable(DiagnosticUnavailableReason.NOT_READ_YET),
            batteryLevelPercent = DiagnosticValue.Unavailable(DiagnosticUnavailableReason.NOT_READ_YET),
            charging = DiagnosticValue.Unavailable(DiagnosticUnavailableReason.NOT_READ_YET),
            batteryVoltageMv = DiagnosticValue.Unavailable(DiagnosticUnavailableReason.NOT_READ_YET),
            batteryCurrentMicroamps = DiagnosticValue.Unavailable(DiagnosticUnavailableReason.NOT_READ_YET),
            historyCount = DiagnosticValue.Unavailable(DiagnosticUnavailableReason.NOT_READ_YET),
            kernelThermalZoneCount = DiagnosticValue.Unavailable(DiagnosticUnavailableReason.NOT_READ_YET)
        )
    }
}

/** Pure adapter from current app state into the diagnostic contract. */
internal fun buildThermalDiagnosticContract(
    observedAtMs: Long?,
    appStatus: String,
    batteryTemperature: Float?,
    systemThermalStatus: String?,
    batteryLevelPercent: Int?,
    charging: Boolean?,
    batteryVoltageMv: Int?,
    batteryCurrentMicroamps: Int?,
    historyCount: Int?,
    historyStorageError: Boolean,
    kernelThermalZoneCount: Int?
): ThermalDiagnosticContract {
    fun <T> observedValue(
        value: T?,
        missingAfterRead: DiagnosticUnavailableReason = DiagnosticUnavailableReason.SENSOR_NOT_EXPOSED
    ): DiagnosticValue<T> = value?.let(DiagnosticValue::Available)
        ?: DiagnosticValue.Unavailable(
            if (observedAtMs == null) DiagnosticUnavailableReason.NOT_READ_YET else missingAfterRead
        )

    return ThermalDiagnosticContract(
        observedAtMs = observedAtMs,
        appStatus = appStatus,
        batteryTemperature = observedValue(batteryTemperature),
        systemThermalStatus = observedValue(
            systemThermalStatus,
            DiagnosticUnavailableReason.PLATFORM_STATUS_NOT_EXPOSED
        ),
        batteryLevelPercent = observedValue(batteryLevelPercent),
        charging = observedValue(charging),
        batteryVoltageMv = observedValue(batteryVoltageMv),
        batteryCurrentMicroamps = observedValue(batteryCurrentMicroamps),
        historyCount = if (historyStorageError) {
            DiagnosticValue.Unavailable(DiagnosticUnavailableReason.LOCAL_STORAGE_UNAVAILABLE)
        } else {
            observedValue(historyCount)
        },
        kernelThermalZoneCount = if (kernelThermalZoneCount != null && kernelThermalZoneCount > 0) {
            DiagnosticValue.Available(kernelThermalZoneCount)
        } else {
            DiagnosticValue.Unavailable(DiagnosticUnavailableReason.KERNEL_ZONES_NOT_EXPOSED)
        }
    )
}
