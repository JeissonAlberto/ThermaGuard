package com.jeissonalberto.thermaguard.domain

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jeissonalberto.thermaguard.data.ThermalDatabase
import com.jeissonalberto.thermaguard.data.ThermalSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
        const val HISTORY_LIMIT = 24
        const val UNAVAILABLE = Int.MIN_VALUE
        const val ALERT_THRESHOLD_C = 40f
        const val CRITICAL_THRESHOLD_C = 45f
    }

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

    private val _lastUpdated = MutableStateFlow<Long?>(null)
    val lastUpdated: StateFlow<Long?> = _lastUpdated

    private val _engineStatus = MutableStateFlow("WAITING")
    val engineStatus: StateFlow<String> = _engineStatus

    /** Kept public for the alerts screen and shared threshold presentation. */
    private val _alertThreshold = MutableStateFlow(ALERT_THRESHOLD_C)
    val alertThreshold: StateFlow<Float> = _alertThreshold

    private val _history = MutableStateFlow<List<ThermalSnapshot>>(emptyList())
    val history: StateFlow<List<ThermalSnapshot>> = _history

    private val _historyStorageError = MutableStateFlow(thermalDao == null)
    val historyStorageError: StateFlow<Boolean> = _historyStorageError

    private var lastPersistedAt = 0L

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

    /** Refreshes the sticky battery broadcast without requiring a permission. */
    fun refreshReading() {
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
        val batteryLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, UNAVAILABLE)
        val batteryScale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, UNAVAILABLE)
        val chargingStatus = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, UNAVAILABLE)
        val temperature = rawTemperature
            .takeUnless { it == UNAVAILABLE || it <= 0 }
            ?.div(10f)
        val now = System.currentTimeMillis()

        _batteryTemp.value = temperature
        _sensorAvailable.value = temperature != null
        _batteryLevel.value = if (batteryLevel != null && batteryScale != null && batteryLevel >= 0 && batteryScale > 0) {
            (batteryLevel * 100f / batteryScale).toInt().coerceIn(0, 100)
        } else {
            null
        }
        _isCharging.value = when (chargingStatus) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        }
        _lastUpdated.value = now
        _engineStatus.value = when {
            temperature == null -> "SENSOR UNAVAILABLE"
            temperature >= CRITICAL_THRESHOLD_C -> "CRITICAL"
            temperature >= ALERT_THRESHOLD_C -> "ALERT"
            else -> "NOMINAL"
        }

        if (temperature != null && now - lastPersistedAt >= HISTORY_SAMPLE_INTERVAL_MS) {
            lastPersistedAt = now
            thermalDao?.let { dao ->
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        dao.insert(ThermalSnapshot(timestamp = now, batteryTemp = temperature))
                        dao.deleteOlderThan(now - HISTORY_RETENTION_MS)
                    }.onFailure { _historyStorageError.value = true }
                }
            }
        }
    }
}
