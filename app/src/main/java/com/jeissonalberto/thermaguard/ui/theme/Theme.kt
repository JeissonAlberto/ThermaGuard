package com.jeissonalberto.thermaguard.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.jeissonalberto.thermaguard.data.AppTheme

// ── MODO OSCURO ───────────────────────────────────────────────────────────────
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
    primary            = Color(0xFF006B7D),
    onPrimary          = Color(0xFFFFFFFF),
    primaryContainer   = Color(0xFFB8EAFF),
    secondary          = Color(0xFF7B4F9E),
    tertiary           = Color(0xFF1B7D44),
    background         = Color(0xFFF0F4F8),
    surface            = Color(0xFFFFFFFF),
    onBackground       = Color(0xFF1A1C2E),
    onSurface          = Color(0xFF1A1C2E),
    error              = Color(0xFFB00020),
    onError            = Color(0xFFFFFFFF)
)

// ── TOKENS DE DISEÑO REACTIVOS ────────────────────────────────────────────────
data class TgColors(
    val bg:          Color,
    val surface:     Color,
    val glass:       Color,
    val glassBorder: Color,
    val textPri:     Color,
    val textSec:     Color,
    val textDim:     Color,
    val blue:        Color,
    val green:       Color,
    val amber:       Color,
    val red:         Color,
    val purple:      Color,
    val teal:        Color,
    val cyan:        Color,
    val isDark:      Boolean
)

private val DarkTgColors = TgColors(
    bg          = Color(0xFF070B12),
    surface     = Color(0xFF0D1520),
    glass       = Color(0x12FFFFFF),
    glassBorder = Color(0x18FFFFFF),
    textPri     = Color(0xFFF0F4FF),
    textSec     = Color(0x80F0F4FF),
    textDim     = Color(0x40F0F4FF),
    blue        = Color(0xFF82B1FF),
    green       = Color(0xFF00E676),
    amber       = Color(0xFFFFAB40),
    red         = Color(0xFFFF5252),
    purple      = Color(0xFFCE93D8),
    teal        = Color(0xFF80CBC4),
    cyan        = Color(0xFF00D4FF),
    isDark      = true
)

private val LightTgColors = TgColors(
    bg          = Color(0xFFF0F4F8),
    surface     = Color(0xFFFFFFFF),
    glass       = Color(0x14000000),
    glassBorder = Color(0x20000000),
    textPri     = Color(0xFF1A1C2E),
    textSec     = Color(0x99222444),
    textDim     = Color(0x55222444),
    blue        = Color(0xFF005A8E),
    green       = Color(0xFF1B7D44),
    amber       = Color(0xFFD67000),
    red         = Color(0xFFB00020),
    purple      = Color(0xFF7B4F9E),
    teal        = Color(0xFF00695C),
    cyan        = Color(0xFF006B7D),
    isDark      = false
)

// CompositionLocal que toda la UI puede leer
val LocalTgColors = staticCompositionLocalOf { DarkTgColors }

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
    val tgColors    = if (darkTheme) DarkTgColors    else LightTgColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalTgColors provides tgColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content     = content
        )
    }
}
