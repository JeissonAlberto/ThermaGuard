package com.jeissonalberto.thermaguard.service

import android.content.Context
import androidx.work.*
import com.jeissonalberto.thermaguard.data.TelemetryRepository
import com.jeissonalberto.thermaguard.data.ThermalDatabase
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * TelemetryWorker — Se ejecuta en background cada 24 horas.
 * Recolecta las últimas lecturas térmicas y las envía como reporte.
 */
class TelemetryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db        = ThermalDatabase.getInstance(applicationContext)
            val snapshots = db.thermalDao().getHistory(200).first()
            TelemetryRepository.sendIfDue(applicationContext, snapshots)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.w("ThermaGuard", "TelemetryWorker: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "therma_telemetry_daily"

        /** Programa el worker para ejecutarse cada 24h (solo si WiFi disponible) */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<TelemetryWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(2, TimeUnit.HOURS) // 2h tras instalar
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
