package com.jeissonalberto.thermaguard.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jeissonalberto.thermaguard.data.ThermalAlertNotifier
import com.jeissonalberto.thermaguard.data.BatteryTelemetry
import com.jeissonalberto.thermaguard.data.ThermalDatabase
import com.jeissonalberto.thermaguard.data.ThermalSnapshot
import com.jeissonalberto.thermaguard.data.readBatteryTelemetry
import com.jeissonalberto.thermaguard.domain.MonitoringMode
import com.jeissonalberto.thermaguard.domain.batteryTemperatureCelsius
import com.jeissonalberto.thermaguard.domain.shouldNotifyThermalStatus
import com.jeissonalberto.thermaguard.domain.systemThermalStatusLabel
import com.jeissonalberto.thermaguard.domain.thermalEngineStatus
import com.jeissonalberto.thermaguard.domain.shouldPauseNonEssentialWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Keeps real battery history and alert transitions alive when the UI is closed.
 * WorkManager controls the cadence; Android may defer a run for battery policy.
 */
class ThermalMonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val context = applicationContext
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val rawTemperature = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE,
                Int.MIN_VALUE
            ) ?: Int.MIN_VALUE
            val temperature = batteryTemperatureCelsius(rawTemperature)
            val batteryTelemetry = readBatteryTelemetry(batteryIntent)
            val systemStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getSystemService(PowerManager::class.java)
                    ?.currentThermalStatus
                    ?.let(::systemThermalStatusLabel)
            } else {
                null
            }
            val status = thermalEngineStatus(temperature, systemStatus)
            notifyOnTransition(context, status, temperature, systemStatus)
            if (!shouldPauseNonEssentialWork(batteryTelemetry.levelPercent, batteryTelemetry.isCharging)) {
                persistSnapshot(context, temperature, batteryTelemetry)
            }
            Result.success()
        }.getOrElse { error ->
            Log.w("ThermaGuard", "ThermalMonitorWorker failed: ${error.message}")
            Result.retry()
        }
    }

    private fun notifyOnTransition(
        context: Context,
        status: String,
        temperature: Float?,
        systemStatus: String?
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previous = prefs.getString(KEY_LAST_STATUS, null)
        if (shouldNotifyThermalStatus(previous, status)) {
            if (ThermalAlertNotifier.notify(context, status, temperature, systemStatus)) {
                prefs.edit().putString(KEY_LAST_STATUS, status).apply()
            }
        } else if (status != "ALERT" && status != "CRITICAL") {
            prefs.edit().remove(KEY_LAST_STATUS).apply()
        }
    }

    private suspend fun persistSnapshot(
        context: Context,
        temperature: Float?,
        batteryTelemetry: BatteryTelemetry
    ) {
        val now = System.currentTimeMillis()
        val dao = ThermalDatabase.getInstance(context).thermalDao()
        temperature?.let {
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
        dao.deleteOlderThan(now - HISTORY_RETENTION_MS)
    }

    companion object {
        private const val WORK_NAME = "therma_background_monitor"
        private const val PREFS_NAME = "therma_background_monitor"
        private const val KEY_LAST_STATUS = "last_alert_status"
        private const val HISTORY_RETENTION_MS = 24 * 60 * 60 * 1_000L

        fun schedule(context: Context) {
            val appContext = context.applicationContext
            val mode = appContext.getSharedPreferences(
                MonitoringMode.PREFS_NAME,
                Context.MODE_PRIVATE
            ).getString(MonitoringMode.MODE_KEY, null)?.let(MonitoringMode::fromStored)
                ?: MonitoringMode.BALANCED
            val constraints = androidx.work.Constraints.Builder()
                .apply {
                    if (mode == MonitoringMode.PREVENTIVE) {
                        // Intensive cadence is available only while the device is charging.
                        setRequiresCharging(true)
                    }
                }
                .build()
            val request = PeriodicWorkRequestBuilder<ThermalMonitorWorker>(
                mode.intervalMinutes,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                // Avoid an immediate duplicate wake-up when the UI schedules work.
                .setInitialDelay(mode.intervalMinutes, TimeUnit.MINUTES)
                // A failed run should not retry in a tight loop and drain the battery.
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, mode.intervalMinutes, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
