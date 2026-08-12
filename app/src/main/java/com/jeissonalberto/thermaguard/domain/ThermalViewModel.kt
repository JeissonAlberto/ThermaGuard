package com.jeissonalberto.thermaguard.domain

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jeissonalberto.thermaguard.MainActivity
import com.jeissonalberto.thermaguard.data.ThermalDatabase
import com.jeissonalberto.thermaguard.data.ThermalSnapshot
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
        const val ALERT_THRESHOLD_C = 40f
        const val CRITICAL_THRESHOLD_C = 45f
        const val THERMAL_ALERT_CHANNEL_ID = "therma_alerts"
        const val THERMAL_ALERT_NOTIFICATION_ID = 9902
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
    private val _alertThreshold = MutableStateFlow(ALERT_THRESHOLD_C)
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

    private fun notifyThermalAlert(status: String, temperature: Float): Boolean {
        val application = getApplication<Application>()
        if (!NotificationManagerCompat.from(application).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(application, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = application.getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(
                    NotificationChannel(
                        THERMAL_ALERT_CHANNEL_ID,
                        "Alertas térmicas",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Avisos cuando la temperatura real de batería supera el umbral."
                    }
                )
            }
            val notification = NotificationCompat.Builder(application, THERMAL_ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(if (status == "CRITICAL") "Alerta térmica crítica" else "Alerta térmica")
                .setContentText("Temperatura real de batería: %.1f°C".format(temperature))
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        ("Android reportó una temperatura real de batería de %.1f°C. " +
                            "Reduce la carga y comprueba la ventilación del dispositivo.").format(temperature)
                    )
                )
                .setContentIntent(
                    android.app.PendingIntent.getActivity(
                        application,
                        0,
                        Intent(application, MainActivity::class.java),
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                            android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            NotificationManagerCompat.from(application).notify(THERMAL_ALERT_NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
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
        val batteryLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, UNAVAILABLE)
        val batteryScale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, UNAVAILABLE)
        val chargingStatus = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, UNAVAILABLE)
        val temperature = rawTemperature
            .takeUnless { it == UNAVAILABLE || it <= 0 }
            ?.div(10f)
        val now = System.currentTimeMillis()
        refreshHardwareThermalZones(now)

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
        if (intent != null) {
            _lastUpdated.value = now
        }
        val currentStatus = when {
            temperature == null -> "SENSOR UNAVAILABLE"
            temperature >= CRITICAL_THRESHOLD_C -> "CRITICAL"
            temperature >= ALERT_THRESHOLD_C -> "ALERT"
            else -> "NOMINAL"
        }
        _engineStatus.value = currentStatus
        if (temperature != null && shouldNotifyThermalStatus(lastNotifiedEngineStatus, currentStatus)) {
            if (notifyThermalAlert(currentStatus, temperature)) {
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
                    runCatching {
                        temperature?.let {
                            dao.insert(ThermalSnapshot(timestamp = now, batteryTemp = it))
                        }
                        dao.deleteOlderThan(now - HISTORY_RETENTION_MS)
                    }.onFailure { _historyStorageError.value = true }
                }
            }
        }
    }
}
