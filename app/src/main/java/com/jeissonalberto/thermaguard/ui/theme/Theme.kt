package com.jeissonalberto.thermaguard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** ThermaGuard's quiet industrial palette: readable in the field, not decorative. */
val TGBackground = Color(0xFF0B1117)
val TGSurface = Color(0xFF111A22)
val TGSurfaceRaised = Color(0xFF17232D)
val TGSurfaceVariant = Color(0xFF1D2B35)
val TGOutline = Color(0xFF40515C)
val TGText = Color(0xFFE8F0F2)
val TGTextMuted = Color(0xFFAAB9BF)
val TGPrimary = Color(0xFF76D1C4)
val TGPrimaryContainer = Color(0xFF173B3B)
val TGWarning = Color(0xFFE8B86D)
val TGWarningContainer = Color(0xFF44351D)
val TGCritical = Color(0xFFE57C75)
val TGCriticalContainer = Color(0xFF492725)
val TGSuccess = Color(0xFF8BCB9D)

private val ThermaGuardColors = darkColorScheme(
    primary = TGPrimary,
    onPrimary = Color(0xFF003735),
    primaryContainer = TGPrimaryContainer,
    onPrimaryContainer = Color(0xFFB0F0E5),
    secondary = TGWarning,
    onSecondary = Color(0xFF3B2A0E),
    secondaryContainer = TGWarningContainer,
    onSecondaryContainer = Color(0xFFFFDEA5),
    background = TGBackground,
    onBackground = TGText,
    surface = TGSurface,
    onSurface = TGText,
    surfaceVariant = TGSurfaceVariant,
    onSurfaceVariant = TGTextMuted,
    outline = TGOutline,
    error = TGCritical,
    onError = Color(0xFF3D0907),
    errorContainer = TGCriticalContainer,
    onErrorContainer = Color(0xFFFFDAD6)
)

private val ThermaGuardTypography = Typography(
    displaySmall = androidx.compose.ui.text.TextStyle(
        fontSize = 42.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-1).sp
    ),
    headlineSmall = androidx.compose.ui.text.TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    ),
    labelMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    )
)

@Composable
fun ThermaGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ThermaGuardColors,
        typography = ThermaGuardTypography,
        content = content
    )
}
