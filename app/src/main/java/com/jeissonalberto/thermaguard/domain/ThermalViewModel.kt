package com.jeissonalberto.thermaguard.domain

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jeissonalberto.thermaguard.data.ThermalAlertNotifier
import com.jeissonalberto.thermaguard.data.ThermalDatabase
import com.jeissonalberto.thermaguard.data.ThermalSnapshot
import com.jeissonalberto.thermaguard.data.readBatteryTelemetry
import com.jeissonalberto.thermaguard.root.HardwareProfiler
import com.jeissonalberto.thermaguard.service.ThermalMonitorWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

/** Keeps user-selected retention within small, predictable local-storage bounds. */
internal fun normalizeHistoryRetentionHours(hours: Int): Int = when (hours) {
    6, 72 -> hours
    else -> 24
}

/**
 * Exposes readings from the Android battery service.
 *
 * Battery temperature is reported by the operating system in tenths of a
 * degree Celsius. Some devices do not expose it; in that case the value stays
 * null instead of being replaced with a simulated reading.
 */
class ThermalViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val HISTORY_SAMPLE_INTERVAL_MS = 60_000L
        const val DEFAULT_HISTORY_RETENTION_HOURS = 24
        const val RETENTION_PREFERENCES = "telemetry_preferences"
        const val RETENTION_HOURS_KEY = "history_retention_hours"
        const val COST_PREFERENCES = "monitoring_cost_preferences"
        const val COST_ENABLED_KEY = "enabled"
        const val COST_SAMPLES_KEY = "sample_count"
        const val COST_TOTAL_MS_KEY = "total_elapsed_ms"
        const val COST_MAX_MS_KEY = "max_elapsed_ms"
        const val COST_PERSIST_EVERY_SAMPLES = 10
        // One sample per minute; 72 hours is the largest supported local history.
        const val MAX_HISTORY_LIMIT = 72 * 60
        const val UNAVAILABLE = Int.MIN_VALUE
    }

    private val powerManager = application.getSystemService(PowerManager::class.java)
    private val retentionPreferences = application.getSharedPreferences(
        RETENTION_PREFERENCES,
        Context.MODE_PRIVATE
    )
    private val _retentionHours = MutableStateFlow(
        normalizeHistoryRetentionHours(
            retentionPreferences.getInt(RETENTION_HOURS_KEY, DEFAULT_HISTORY_RETENTION_HOURS)
        )
    )
    val retentionHours: StateFlow<Int> = _retentionHours

    private val costPreferences = application.getSharedPreferences(
        COST_PREFERENCES,
        Context.MODE_PRIVATE
    )
    private val _costMeasurementEnabled = MutableStateFlow(
        costPreferences.getBoolean(COST_ENABLED_KEY, false)
    )
    val costMeasurementEnabled: StateFlow<Boolean> = _costMeasurementEnabled
    private val _monitoringCost = MutableStateFlow(
        MonitoringCostSample(
            sampleCount = costPreferences.getInt(COST_SAMPLES_KEY, 0),
            totalElapsedMs = costPreferences.getLong(COST_TOTAL_MS_KEY, 0L),
            maxElapsedMs = costPreferences.getLong(COST_MAX_MS_KEY, 0L)
        )
    )
    val monitoringCost: StateFlow<MonitoringCostSample> = _monitoringCost
    private var samplesAtLastCostPersist = _monitoringCost.value.sampleCount

    private fun historyRetentionMs(): Long = _retentionHours.value * 60 * 60 * 1_000L
    private val monitoringPreferences = application.getSharedPreferences(
        MonitoringMode.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val _monitoringMode = MutableStateFlow(
        MonitoringMode.fromStored(
            monitoringPreferences.getString(MonitoringMode.MODE_KEY, null)
        )
    )
    val monitoringMode: StateFlow<MonitoringMode> = _monitoringMode
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
    private var foregroundPollingJob: Job? = null
    private var foregroundMonitoringActive = false
    private var diagnosticsVisible = false

    private val _foregroundPollingPolicy = MutableStateFlow(
        calculateForegroundPollingPolicy(
            _monitoringMode.value,
            null,
            null,
            _monitoringCost.value.takeIf { _costMeasurementEnabled.value }
        )
    )
    val foregroundPollingPolicy: StateFlow<ForegroundPollingPolicy> = _foregroundPollingPolicy

    init {
        thermalDao?.let { dao ->
            viewModelScope.launch(Dispatchers.IO) {
                val cleanupResult = runCatching {
                    dao.deleteOlderThan(System.currentTimeMillis() - historyRetentionMs())
                }
                if (cleanupResult.isFailure) {
                    _historyStorageError.value = true
                }
                dao.observeRecent(MAX_HISTORY_LIMIT)
                    .catch { _historyStorageError.value = true }
                    .collect { snapshots -> _history.value = snapshots }
            }
        }

    }

    /**
     * Starts or stops UI-only polling according to the Activity lifecycle.
     * Background monitoring remains owned by WorkManager.
     */
    fun setForegroundMonitoringActive(active: Boolean) {
        foregroundMonitoringActive = active
        if (active) {
            if (foregroundPollingJob?.isActive != true) startForegroundPolling()
        } else {
            foregroundPollingJob?.cancel()
            foregroundPollingJob = null
        }
    }

    /**
     * Kernel thermal-zone files are only useful while the diagnosis screen is
     * visible. Avoid reading them during dashboard/alert polling, while the
     * battery sensor and Android's aggregated thermal status remain active.
     */
    fun setDiagnosticsVisible(visible: Boolean) {
        diagnosticsVisible = visible
        if (visible) refreshHardwareThermalZones(System.currentTimeMillis())
    }

    /**
     * Keeps the first reading immediate, then follows the selected mode. The
     * battery policy can stretch the cadence to 15 minutes, but never disables
     * refresh/alert evaluation; a manual refresh remains immediate.
     */
    private fun startForegroundPolling() {
        foregroundPollingJob?.cancel()
        foregroundPollingJob = viewModelScope.launch {
            while (isActive) {
                refreshReading()
                val policy = calculateForegroundPollingPolicy(
                    _monitoringMode.value,
                    _batteryLevel.value,
                    _isCharging.value,
                    _monitoringCost.value.takeIf { _costMeasurementEnabled.value }
                )
                _foregroundPollingPolicy.value = policy
                delay(policy.intervalMs)
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
        if (!shouldRefreshHardwareZones(diagnosticsVisible, now, lastHardwareZoneReadAt)) return
        lastHardwareZoneReadAt = now
        viewModelScope.launch(Dispatchers.IO) {
            _hardwareThermalZones.value = runCatching {
                HardwareProfiler.readCurrentThermalZones()
            }.getOrDefault(emptyList())
        }
    }

    /** Enables optional local measurement of this app's own foreground work. */
    fun setCostMeasurementEnabled(enabled: Boolean) {
        costPreferences.edit().putBoolean(COST_ENABLED_KEY, enabled).apply()
        _costMeasurementEnabled.value = enabled
        if (!enabled) clearMonitoringCost()
        updateForegroundPollingPolicy()
    }

    /** Deletes local thermal history and the optional work-cost aggregate. */
    fun clearLocalHistory() {
        clearMonitoringCost()
        val dao = thermalDao ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { dao.deleteAll() }
                .onSuccess { _historyStorageError.value = false }
                .onFailure { _historyStorageError.value = true }
        }
    }

    private fun clearMonitoringCost() {
        costPreferences.edit()
            .remove(COST_SAMPLES_KEY)
            .remove(COST_TOTAL_MS_KEY)
            .remove(COST_MAX_MS_KEY)
            .apply()
        _monitoringCost.value = MonitoringCostSample()
        samplesAtLastCostPersist = 0
    }

    private fun updateForegroundPollingPolicy() {
        _foregroundPollingPolicy.value = calculateForegroundPollingPolicy(
            _monitoringMode.value,
            _batteryLevel.value,
            _isCharging.value,
            _monitoringCost.value.takeIf { _costMeasurementEnabled.value }
        )
    }

    private fun recordRefreshCost(elapsedMs: Long) {
        if (!_costMeasurementEnabled.value) return
        val updated = _monitoringCost.value.record(elapsedMs)
        _monitoringCost.value = updated
        if (updated.sampleCount - samplesAtLastCostPersist >= COST_PERSIST_EVERY_SAMPLES) {
            samplesAtLastCostPersist = updated.sampleCount
            costPreferences.edit()
                .putInt(COST_SAMPLES_KEY, updated.sampleCount)
                .putLong(COST_TOTAL_MS_KEY, updated.totalElapsedMs)
                .putLong(COST_MAX_MS_KEY, updated.maxElapsedMs)
                .apply()
        }
    }

    /** Refreshes the sticky battery broadcast without requiring a permission. */
    fun refreshReading() {
        val startedAt = SystemClock.elapsedRealtime()
        try {
            refreshReadingInternal()
        } finally {
            recordRefreshCost(SystemClock.elapsedRealtime() - startedAt)
            updateForegroundPollingPolicy()
        }
    }

    /** Persists a bounded local-retention choice and immediately removes older samples. */
    fun setRetentionHours(hours: Int) {
        val normalized = normalizeHistoryRetentionHours(hours)
        if (_retentionHours.value == normalized) return
        retentionPreferences.edit().putInt(RETENTION_HOURS_KEY, normalized).apply()
        _retentionHours.value = normalized
        val dao = thermalDao ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { dao.deleteOlderThan(System.currentTimeMillis() - historyRetentionMs()) }
                .onSuccess { _historyStorageError.value = false }
                .onFailure { _historyStorageError.value = true }
        }
    }

    /** Changes the persisted cadence and replaces the WorkManager schedule immediately. */
    fun setMonitoringMode(mode: MonitoringMode) {
        if (_monitoringMode.value == mode) return
        monitoringPreferences.edit().putString(MonitoringMode.MODE_KEY, mode.name).apply()
        _monitoringMode.value = mode
        _foregroundPollingPolicy.value = calculateForegroundPollingPolicy(
            mode,
            _batteryLevel.value,
            _isCharging.value,
            _monitoringCost.value.takeIf { _costMeasurementEnabled.value }
        )
        if (foregroundMonitoringActive) startForegroundPolling()
        ThermalMonitorWorker.schedule(getApplication<Application>())
    }

    private fun refreshReadingInternal() {
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
        _foregroundPollingPolicy.value = calculateForegroundPollingPolicy(
            _monitoringMode.value,
            batteryTelemetry.levelPercent,
            batteryTelemetry.isCharging,
            _monitoringCost.value.takeIf { _costMeasurementEnabled.value }
        )
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
        // Match the background worker: alert evaluation continues, but local history
        // writes and retention cleanup pause below the low-battery threshold.
        if (!shouldPauseNonEssentialWork(batteryTelemetry.levelPercent, batteryTelemetry.isCharging) &&
            now - lastPersistedAt >= HISTORY_SAMPLE_INTERVAL_MS
        ) {
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
                        dao.deleteOlderThan(now - historyRetentionMs())
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

