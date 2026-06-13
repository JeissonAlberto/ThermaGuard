package com.jeissonalberto.thermaguard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

// ─────────────────────────────────────────────────────────────────────────────
//  NotificationActionReceiver — Maneja acciones desde notificaciones
//  (Optimizar / Descartar)
// ─────────────────────────────────────────────────────────────────────────────
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "ACTION_OPTIMIZE" -> {
                // Lanza la app directo a la pestaña de optimización
                val launchIntent = context.packageManager
                    .getLaunchIntentForPackage(context.packageName)
                    ?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("NAVIGATE_TO", "optimize")
                    }
                launchIntent?.let { context.startActivity(it) }
                NotificationEngine.cancelAlert(context)
            }
            "ACTION_DISMISS" -> {
                NotificationEngine.cancelAlert(context)
                Toast.makeText(context, "Alerta descartada", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
