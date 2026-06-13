package com.jeissonalberto.thermaguard.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "splash")

    val orbAlpha by inf.animateFloat(0.3f, 0.7f,
        infiniteRepeatable(tween(1800, easing = EaseInOut), RepeatMode.Reverse), label = "orb")
    val orbScale by inf.animateFloat(0.9f, 1.1f,
        infiniteRepeatable(tween(2200, easing = EaseInOut), RepeatMode.Reverse), label = "orbS")
    val iconPulse by inf.animateFloat(0.92f, 1.08f,
        infiniteRepeatable(tween(1600, easing = EaseInOut), RepeatMode.Reverse), label = "ip")

    var logoAlpha    by remember { mutableStateOf(0f) }
    var logoScale    by remember { mutableStateOf(0.7f) }
    var taglineAlpha by remember { mutableStateOf(0f) }

    val logoAlphaAnim   by animateFloatAsState(logoAlpha,    tween(700, easing = EaseOutCubic), label = "la")
    val logoScaleAnim   by animateFloatAsState(logoScale,    tween(700, easing = EaseOutBack),  label = "ls")
    val taglineAlphaAnim by animateFloatAsState(taglineAlpha, tween(500),                        label = "ta")

    LaunchedEffect(Unit) {
        delay(200)
        logoAlpha = 1f
        logoScale = 1f
        delay(500)
        taglineAlpha = 1f
        delay(1800)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TG.bg),
        contentAlignment = Alignment.Center
    ) {
        // Orb de fondo
        Box(
            modifier = Modifier
                .size(380.dp)
                .scale(orbScale)
                .blur(90.dp)
                .background(
                    Color(0xFF00E5FF).copy(alpha = orbAlpha * 0.18f),
                    RoundedCornerShape(50)
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Icono vectorial — sin dependencia de assets externos
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(logoScaleAnim * iconPulse)
                    .alpha(logoAlphaAnim)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.12f), RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Círculo externo decorativo
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Símbolo temperatura — 100% vectorial, sin assets
                    Text(
                        text = "🌡",
                        fontSize = 44.sp
                    )
                }
            }

            // Nombre app
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(logoAlphaAnim)
            ) {
                Text(
                    "ThermaGuard",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = (-1).sp
                )
                Text(
                    "by Jasol Group",
                    fontSize = 13.sp,
                    color = Color(0xFF00E5FF).copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
            }

            // Tagline
            Text(
                "Innovación · Tecnología · Futuro",
                fontSize = 11.sp,
                color = TG.textDim,
                letterSpacing = 1.5.sp,
                modifier = Modifier.alpha(taglineAlphaAnim)
            )
        }

        // Versión
        Text(
            "v3.9.12",
            fontSize = 10.sp,
            color = TG.textDim,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .alpha(taglineAlphaAnim)
        )
    }
}
