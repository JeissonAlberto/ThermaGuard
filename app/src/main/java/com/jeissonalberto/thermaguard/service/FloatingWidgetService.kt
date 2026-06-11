package com.jeissonalberto.thermaguard.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import com.jeissonalberto.thermaguard.data.ThermalLevel
import com.jeissonalberto.thermaguard.data.toThermalLevel
import kotlinx.coroutines.*

/**
 * Widget flotante de temperatura.
 * DISEÑO DE EFICIENCIA:
 * - NO lee sensores propios — consume el lastSnapshot del ThermalMonitorService
 * - Solo actualiza la burbuja en pantalla (operación de UI, ~0 CPU)
 * - Se actualiza cada 5s SOLO si hay cambio de más de 0.5°C (evita redraws innecesarios)
 * - Se pausa cuando la pantalla está apagada (BroadcastReceiver de pantalla)
 */
class FloatingWidgetService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastDisplayedTemp = -999f
    private var screenOn = true

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: android.content.Context, intent: Intent) {
            screenOn = intent.action == Intent.ACTION_SCREEN_ON
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        setupOverlayView()
        registerReceiver(screenReceiver, android.content.IntentFilter().also {
            it.addAction(Intent.ACTION_SCREEN_ON)
            it.addAction(Intent.ACTION_SCREEN_OFF)
        })
        startUpdateLoop()
    }

    private fun setupOverlayView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Layout programático — sin XML, sin inflate pesado
        val tv = TextView(this).apply {
            text = "--°C"
            textSize = 13f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(20, 10, 20, 10)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 50f
                setColor(android.graphics.Color.argb(200, 10, 20, 40))
                setStroke(2, android.graphics.Color.argb(180, 0, 200, 255))
            }
        }
        floatingView = tv

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16; y = 120
        }

        // Arrastre táctil para mover la burbuja
        var initX = 0; var initY = 0; var initTouchX = 0f; var initTouchY = 0f
        tv.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    initTouchX = event.rawX; initTouchY = event.rawY; false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initX + (initTouchX - event.rawX).toInt()
                    params.y = initY + (event.rawY - initTouchY).toInt()
                    windowManager?.updateViewLayout(floatingView, params); true
                }
                else -> false
            }
        }

        try { windowManager?.addView(floatingView, params) }
        catch (_: Exception) {}
    }

    private fun startUpdateLoop() {
        scope.launch {
            try { while (true) {
                delay(5_000L)  // Actualizar cada 5s — suficiente para una burbuja
                if (!screenOn) continue  // Pantalla apagada → no actualizar

                val snap = ThermalMonitorService.lastSnapshot ?: continue
                val temp = if (snap.cpuTemp > 20f) snap.cpuTemp else snap.batteryTemp

                // Solo actualizar si cambió más de 0.5°C (evita redraws innecesarios)
                if (kotlin.math.abs(temp - lastDisplayedTemp) < 0.5f) continue
                lastDisplayedTemp = temp

                val level = temp.toThermalLevel()
                val color = when (level) {
                    ThermalLevel.NORMAL    -> android.graphics.Color.argb(200, 0, 200, 100)
                    ThermalLevel.WARM      -> android.graphics.Color.argb(200, 255, 180, 0)
                    ThermalLevel.HOT       -> android.graphics.Color.argb(200, 255, 100, 0)
                    ThermalLevel.CRITICAL,
                    ThermalLevel.EMERGENCY -> android.graphics.Color.argb(220, 255, 30, 30)
                }
                val emoji = when (level) {
                    ThermalLevel.NORMAL    -> "🟢"
                    ThermalLevel.WARM      -> "🟡"
                    ThermalLevel.HOT       -> "🟠"
                    ThermalLevel.CRITICAL,
                    ThermalLevel.EMERGENCY -> "🔴"
                }

                (floatingView as? TextView)?.apply {
                    text = "$emoji ${temp.toInt()}°C"
                    (background as? android.graphics.drawable.GradientDrawable)
                        ?.setStroke(2, color)
                }
            }
            } catch (e: Exception) { android.util.Log.e("FloatingWidget","loop crash",e) }
        }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        try { floatingView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        fun start(context: android.content.Context) {
            val intent = Intent(context, FloatingWidgetService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else context.startService(intent)
        }
        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, FloatingWidgetService::class.java))
        }
    }
}
