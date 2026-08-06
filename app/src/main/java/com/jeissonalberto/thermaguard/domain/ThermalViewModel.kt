package com.jeissonalberto.thermaguard.domain

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        const val UNAVAILABLE = Int.MIN_VALUE
        const val ALERT_THRESHOLD_C = 40f
        const val CRITICAL_THRESHOLD_C = 45f
    }

    private val _batteryTemp = MutableStateFlow<Float?>(null)
    val batteryTemp: StateFlow<Float?> = _batteryTemp

    private val _sensorAvailable = MutableStateFlow(false)
    val sensorAvailable: StateFlow<Boolean> = _sensorAvailable

    private val _lastUpdated = MutableStateFlow<Long?>(null)
    val lastUpdated: StateFlow<Long?> = _lastUpdated

    private val _engineStatus = MutableStateFlow("WAITING")
    val engineStatus: StateFlow<String> = _engineStatus

    /** Kept public for the alerts screen and shared threshold presentation. */
    private val _alertThreshold = MutableStateFlow(ALERT_THRESHOLD_C)
    val alertThreshold: StateFlow<Float> = _alertThreshold

    init {
        viewModelScope.launch {
            while (true) {
                refreshReading()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Refreshes the sticky battery broadcast without requiring a permission. */
    fun refreshReading() {
        val intent = getApplication<Application>().registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val rawTemperature = intent?.getIntExtra(
            BatteryManager.EXTRA_TEMPERATURE,
            UNAVAILABLE
        ) ?: UNAVAILABLE
        val temperature = rawTemperature
            .takeUnless { it == UNAVAILABLE || it <= 0 }
            ?.div(10f)

        _batteryTemp.value = temperature
        _sensorAvailable.value = temperature != null
        _lastUpdated.value = System.currentTimeMillis()
        _engineStatus.value = when {
            temperature == null -> "SENSOR UNAVAILABLE"
            temperature >= CRITICAL_THRESHOLD_C -> "CRITICAL"
            temperature >= ALERT_THRESHOLD_C -> "ALERT"
            else -> "NOMINAL"
        }
    }
}
