package com.jeissonalberto.thermaguard.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.jeissonalberto.thermaguard.MainActivity
import com.jeissonalberto.thermaguard.R
import com.jeissonalberto.thermaguard.data.UpdateChecker
import java.util.concurrent.TimeUnit

/**
 * UpdateWorker — Verifica si hay una nueva versión de ThermaGuard cada 6h.
 * Si hay actualización disponible, lanza una notificación con link de descarga.
 */
class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val update = UpdateChecker.checkIfDue(applicationContext) ?: return Result.success()
            // Hay actualización disponible — notificar al usuario
            showUpdateNotification(update.version, update.releaseNotes, update.downloadUrl)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.w("ThermaGuard", "UpdateWorker: ${e.message}")
            Result.retry()
        }
    }

    private fun showUpdateNotification(version: String, notes: String, url: String) {
        val channelId = "therma_updates"
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Actualizaciones", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Nuevas versiones de ThermaGuard" }
            )
        }

        // Intent para abrir la URL de descarga
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pi = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("🚀 ThermaGuard $version disponible")
            .setContentText(notes.take(100))
            .setStyle(NotificationCompat.BigTextStyle().bigText(notes.take(300)))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        nm.notify(9901, notif)
    }

    companion object {
        private const val WORK_NAME = "therma_update_check"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(10, TimeUnit.MINUTES)
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
