package com.jeissonalberto.thermaguard.domain

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jeissonalberto.thermaguard.data.ThermalAlertNotifier
import com.jeissonalberto.thermaguard.data.ThermalDatabase
import com.jeissonalberto.thermaguard.data.ThermalSnapshot
import com.jeissonalberto.thermaguard.data.readBatteryTelemetry
import com.jeissonalberto.thermaguard.root.HardwareProfiler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Converts Android's public thermal status constants to stable UI labels. */
internal fun systemThermalStatusLabel(status: Int): String = when (status) {
    PowerManager.THERMAL_STATUS_NONE -> "NORMAL"
    PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
    PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
    PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
    PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
    PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
    PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
    else -> "UNKNOWN"
}

/** A notification is emitted only when the app enters a new alert state. */
internal fun shouldNotifyThermalStatus(previous: String?, current: String): Boolean =
    current in setOf("ALERT", "CRITICAL") && previous != current

/** Converts Android's tenths-of-a-degree value without discarding valid sub-zero readings. */
internal fun batteryTemperatureCelsius(rawTemperature: Int): Float? =
    rawTemperature.takeUnless { it == Int.MIN_VALUE }?.div(10f)

/** Android reports these states when the system needs the user to reduce thermal load. */
internal fun isSystemThermalRisk(status: String?): Boolean =
    status in setOf("SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN")

/** Combines the battery reading with Android's aggregated thermal status. */
internal fun thermalEngineStatus(temperature: Float?, systemStatus: String?): String = when {
    systemStatus in setOf("CRITICAL", "EMERGENCY", "SHUTDOWN") -> "CRITICAL"
    systemStatus == "SEVERE" -> "ALERT"
    temperature == null -> "SENSOR UNAVAILABLE"
    temperature >= 45f -> "CRITICAL"
    temperature >= 40f -> "ALERT"
    else -> "NOMINAL"
}

/** A missing sample is valid when the sensor is unavailable; cleanup still proves storage works. */
internal fun historyStorageWriteSucceeded(sampleWriteSucceeded: Boolean?, cleanupSucceeded: Boolean): Boolean =
    sampleWriteSucceeded != false && cleanupSucceeded

/**
 * Exposes readings from the Android battery service.
 *
 * Battery temperature is reported by the operating system in tenths of a
 * degree Celsius. Some devices do not expose it; in that case the value stays
 * null instead of being replaced with a simulated reading.
 */
class ThermalViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
        const val HISTORY_SAMPLE_INTERVAL_MS = 60_000L
        const val HISTORY_RETENTION_MS = 24 * 60 * 60 * 1_000L
        const val HARDWARE_ZONE_REFRESH_INTERVAL_MS = 15_000L
        // One sample per minute, matching the 24-hour retention window.
        const val HISTORY_LIMIT = 24 * 60
        const val UNAVAILABLE = Int.MIN_VALUE
    }

    private val powerManager = application.getSystemService(PowerManager::class.java)
    private var lastNotifiedEngineStatus: String? = null

    private val thermalDao = runCatching {
        ThermalDatabase.getInstance(application).thermalDao()
    }.getOrNull()

    private val _batteryTemp = MutableStateFlow<Float?>(null)
    val batteryTemp: StateFlow<Float?> = _batteryTemp

    private val _sensorAvailable = MutableStateFlow(false)
    val sensorAvailable: StateFlow<Boolean> = _sensorAvailable

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel

    private val _isCharging = MutableStateFlow<Boolean?>(null)
    val isCharging: StateFlow<Boolean?> = _isCharging

    private val _batteryVoltageMv = MutableStateFlow<Int?>(null)
    val batteryVoltageMv: StateFlow<Int?> = _batteryVoltageMv

    private val _batteryCurrentMicroamps = MutableStateFlow<Int?>(null)
    val batteryCurrentMicroamps: StateFlow<Int?> = _batteryCurrentMicroamps

    private val _lastUpdated = MutableStateFlow<Long?>(null)
    val lastUpdated: StateFlow<Long?> = _lastUpdated

    private val _engineStatus = MutableStateFlow("WAITING")
    val engineStatus: StateFlow<String> = _engineStatus

    /** Aggregated thermal status reported by Android 10+ (not a CPU/GPU reading). */
    private val _systemThermalStatus = MutableStateFlow<String?>(null)
    val systemThermalStatus: StateFlow<String?> = _systemThermalStatus

    /** Raw thermal zones exposed by the kernel, when the device permits reading them. */
    private val _hardwareThermalZones = MutableStateFlow<List<HardwareProfiler.ThermalZoneInfo>>(emptyList())
    val hardwareThermalZones: StateFlow<List<HardwareProfiler.ThermalZoneInfo>> = _hardwareThermalZones

    /** Kept public for the alerts screen and shared threshold presentation. */
    private val _alertThreshold = MutableStateFlow(40f)
    val alertThreshold: StateFlow<Float> = _alertThreshold

    private val _history = MutableStateFlow<List<ThermalSnapshot>>(emptyList())
    val history: StateFlow<List<ThermalSnapshot>> = _history

    private val _historyStorageError = MutableStateFlow(thermalDao == null)
    val historyStorageError: StateFlow<Boolean> = _historyStorageError

    private var lastPersistedAt = 0L
    private var lastHardwareZoneReadAt = 0L

    init {
        thermalDao?.let { dao ->
            viewModelScope.launch(Dispatchers.IO) {
                dao.observeRecent(HISTORY_LIMIT)
                    .catch { _historyStorageError.value = true }
                    .collect { snapshots -> _history.value = snapshots }
            }
        }

        viewModelScope.launch {
            while (isActive) {
                refreshReading()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun updateSystemThermalStatus() {
        _systemThermalStatus.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager?.currentThermalStatus?.let(::systemThermalStatusLabel) ?: "UNKNOWN"
        } else {
            null
        }
    }

    private fun refreshHardwareThermalZones(now: Long) {
        if (now - lastHardwareZoneReadAt < HARDWARE_ZONE_REFRESH_INTERVAL_MS) return
        lastHardwareZoneReadAt = now
        viewModelScope.launch(Dispatchers.IO) {
            _hardwareThermalZones.value = runCatching {
                HardwareProfiler.readCurrentThermalZones()
            }.getOrDefault(emptyList())
        }
    }

    /** Refreshes the sticky battery broadcast without requiring a permission. */
    fun refreshReading() {
        updateSystemThermalStatus()
        val intent = runCatching {
            getApplication<Application>().registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
        }.getOrNull()
        val rawTemperature = intent?.getIntExtra(
            BatteryManager.EXTRA_TEMPERATURE,
            UNAVAILABLE
        ) ?: UNAVAILABLE
        val batteryTelemetry = readBatteryTelemetry(intent)
        val temperature = batteryTemperatureCelsius(rawTemperature)
        val now = System.currentTimeMillis()
        refreshHardwareThermalZones(now)

        _batteryTemp.value = temperature
        _sensorAvailable.value = temperature != null
        _batteryLevel.value = batteryTelemetry.levelPercent
        _isCharging.value = batteryTelemetry.isCharging
        _batteryVoltageMv.value = batteryTelemetry.voltageMv
        _batteryCurrentMicroamps.value = batteryTelemetry.currentMicroamps
        if (intent != null) {
            _lastUpdated.value = now
        }
        val systemStatus = _systemThermalStatus.value
        val currentStatus = thermalEngineStatus(temperature, systemStatus)
        _engineStatus.value = currentStatus
        if (shouldNotifyThermalStatus(lastNotifiedEngineStatus, currentStatus)) {
            if (ThermalAlertNotifier.notify(getApplication<Application>(), currentStatus, temperature, systemStatus)) {
                lastNotifiedEngineStatus = currentStatus
            }
        } else if (currentStatus != "ALERT" && currentStatus != "CRITICAL") {
            lastNotifiedEngineStatus = null
        }

        // Keep retention maintenance independent from sensor availability. A device
        // that stops exposing temperature must not keep stale history indefinitely.
        if (now - lastPersistedAt >= HISTORY_SAMPLE_INTERVAL_MS) {
            lastPersistedAt = now
            thermalDao?.let { dao ->
                viewModelScope.launch(Dispatchers.IO) {
                    // Cleanup must still run if a new sample cannot be written.
                    val insertResult = temperature?.let {
                        runCatching {
                            dao.insert(
                                ThermalSnapshot(
                                    timestamp = now,
                                    batteryTemp = it,
                                    batteryLevel = batteryTelemetry.levelPercent,
                                    isCharging = batteryTelemetry.isCharging,
                                    batteryVoltageMv = batteryTelemetry.voltageMv,
                                    batteryCurrentMicroamps = batteryTelemetry.currentMicroamps
                                )
                            )
                        }
                    }
                    val cleanupResult = runCatching {
                        dao.deleteOlderThan(now - HISTORY_RETENTION_MS)
                    }
                    if (insertResult?.isFailure == true || cleanupResult.isFailure) {
                        _historyStorageError.value = true
                    } else if (historyStorageWriteSucceeded(insertResult?.isSuccess, cleanupResult.isSuccess)) {
                        // A later successful write clears a transient database error.
                        _historyStorageError.value = false
                    }
                }
            }
        }
    }
}
