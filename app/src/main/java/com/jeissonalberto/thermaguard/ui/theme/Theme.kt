package com.jeissonalberto.thermaguard.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AppColorScheme = darkColorScheme(
    primary            = Color(0xFF00E5FF),
    onPrimary          = Color(0xFF080C14),
    primaryContainer   = Color(0xFF0F1623),
    secondary          = Color(0xFFCE93D8),
    tertiary           = Color(0xFF00E676),
    background         = Color(0xFF080C14),
    surface            = Color(0xFF0F1623),
    onBackground       = Color(0xFFF0F4FF),
    onSurface          = Color(0xFFF0F4FF),
    surfaceVariant     = Color(0x14FFFFFF),
    outline            = Color(0x1AFFFFFF),
    error              = Color(0xFFFF5252)
)

@Composable
fun ThermaGuardTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor     = Color(0xFF080C14).toArgb()
            window.navigationBarColor = Color(0xFF080C14).toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = AppColorScheme,
        content     = content
    )
}
