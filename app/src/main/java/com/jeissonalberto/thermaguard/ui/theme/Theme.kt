package com.jeissonalberto.thermaguard.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.jeissonalberto.thermaguard.data.AppTheme

// ── MODO OSCURO (por defecto) ─────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary            = Color(0xFF00E5FF),
    onPrimary          = Color(0xFF080C14),
    primaryContainer   = Color(0xFF0F1623),
    secondary          = Color(0xFFCE93D8),
    tertiary           = Color(0xFF69F0AE),
    background         = Color(0xFF080C14),
    surface            = Color(0xFF0F1623),
    onBackground       = Color(0xFFE0E6FF),
    onSurface          = Color(0xFFE0E6FF),
    error              = Color(0xFFFF1744),
    onError            = Color(0xFFFFFFFF)
)

// ── MODO CLARO ────────────────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary            = Color(0xFF006B7D),   // Cian oscuro
    onPrimary          = Color(0xFFFFFFFF),
    primaryContainer   = Color(0xFFB8EAFF),
    secondary          = Color(0xFF7B4F9E),
    tertiary           = Color(0xFF1B7D44),
    background         = Color(0xFFF0F4F8),   // Gris azulado muy claro
    surface            = Color(0xFFFFFFFF),
    onBackground       = Color(0xFF1A1C2E),
    onSurface          = Color(0xFF1A1C2E),
    error              = Color(0xFFB00020),
    onError            = Color(0xFFFFFFFF)
)

@Composable
fun ThermaGuardTheme(
    appTheme: AppTheme = AppTheme.DARK,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme) {
        AppTheme.DARK   -> true
        AppTheme.LIGHT  -> false
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content     = content
    )
}
