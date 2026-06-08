package com.jeissonalberto.thermaguard.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.ThermalLevel
import kotlin.math.*

// ════════════════════════════════════════════════════════════════════════════
//  AVATAR MOOD
// ════════════════════════════════════════════════════════════════════════════
enum class AvatarMood(
    val emoji: String,
    val label: String,
    val bodyColor: Long,
    val glowColor: Long,
    val message: String
) {
    COOL    ("😎", "Fresco",    0xFF00E5FF, 0xFF00B8D4, "Tu dispositivo está fresco y feliz"),
    NORMAL  ("🙂", "Normal",    0xFF69F0AE, 0xFF00C853, "Todo en orden, monitoreando..."),
    WARM    ("😅", "Tibio",     0xFFFFD740, 0xFFFF8F00, "Calentando un poco, ojo..."),
    HOT     ("😰", "Caliente",  0xFFFF6E40, 0xFFDD2C00, "¡Está caliente! Cierra apps pesadas"),
    CRITICAL("🚨", "¡Crítico!", 0xFFFF1744, 0xFF9B0000, "¡EMERGENCIA! El dispositivo está al límite")
}

fun ThermalLevel.toMood() = when (this) {
    ThermalLevel.NORMAL    -> AvatarMood.COOL
    ThermalLevel.WARM      -> AvatarMood.NORMAL
    ThermalLevel.HOT       -> AvatarMood.WARM
    ThermalLevel.CRITICAL  -> AvatarMood.HOT
    ThermalLevel.EMERGENCY -> AvatarMood.CRITICAL
}

// ════════════════════════════════════════════════════════════════════════════
//  AVATAR PRINCIPAL
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun ThermaAvatar(
    level: ThermalLevel,
    temp: Float,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp
) {
    val mood       = level.toMood()
    val bodyColor  = Color(mood.bodyColor)
    val glowColor  = Color(mood.glowColor)
    val inf        = rememberInfiniteTransition(label = "avatar")

    // Flotación
    val floatY by inf.animateFloat(
        initialValue = -5f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(2400, easing = EaseInOut), RepeatMode.Reverse),
        label = "float"
    )
    // Parpadeo
    val blinkSpeed = when (mood) { AvatarMood.CRITICAL -> 600; AvatarMood.HOT -> 900; else -> 3800 }
    val blinkProgress by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = blinkSpeed
            0f at 0; 0f at (blinkSpeed * 0.87f).toInt()
            1f at (blinkSpeed * 0.93f).toInt(); 0f at blinkSpeed
        }, RepeatMode.Restart), label = "blink"
    )
    // Temblor crítico
    val shakeX by inf.animateFloat(
        initialValue = if (mood == AvatarMood.CRITICAL) -3f else 0f,
        targetValue  = if (mood == AvatarMood.CRITICAL)  3f else 0f,
        animationSpec = infiniteRepeatable(tween(75), RepeatMode.Reverse), label = "shake"
    )
    // Respiración
    val breathScale by inf.animateFloat(
        initialValue = 1f, targetValue = if (mood == AvatarMood.CRITICAL) 1.06f else 1.02f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOut), RepeatMode.Reverse),
        label = "breath"
    )
    // Glow pulsante
    val glowAlpha by inf.animateFloat(
        initialValue = 0.4f, targetValue = if (mood == AvatarMood.CRITICAL) 1.0f else 0.7f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOut), RepeatMode.Reverse),
        label = "glow"
    )
    // Anillo orbital — rotación continua
    val orbitalAngle by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbit"
    )
    // Scan line — barre de arriba a abajo
    val scanY by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "scan"
    )
    // Partículas / datos flotantes
    val particlePhase by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart),
        label = "particle"
    )
    // Ojos
    val eyeOpenRatio by animateFloatAsState(
        targetValue = when (mood) {
            AvatarMood.HOT -> 0.6f; AvatarMood.CRITICAL -> 0.5f
            AvatarMood.COOL -> 1.25f; else -> 1.0f
        }, animationSpec = tween(500), label = "eye"
    )
    // Ceja
    val browAngle by animateFloatAsState(
        targetValue = when (mood) {
            AvatarMood.WARM -> 8f; AvatarMood.HOT -> 14f; AvatarMood.CRITICAL -> 22f
            AvatarMood.COOL -> -6f; else -> 0f
        }, animationSpec = tween(600, easing = EaseOutCubic), label = "brow"
    )
    // Boca
    val mouthCurve by animateFloatAsState(
        targetValue = when (mood) {
            AvatarMood.COOL -> 0.9f; AvatarMood.NORMAL -> 0.3f; AvatarMood.WARM -> -0.1f
            AvatarMood.HOT -> -0.5f; AvatarMood.CRITICAL -> -0.85f
        }, animationSpec = tween(700, easing = EaseOutCubic), label = "mouth"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Glow halo exterior — doble capa
            Box(Modifier.size(size * 1.6f).clip(CircleShape)
                .background(glowColor.copy(alpha = glowAlpha * 0.12f)))
            Box(Modifier.size(size * 1.25f).clip(CircleShape)
                .background(glowColor.copy(alpha = glowAlpha * 0.18f)))

            Canvas(
                modifier = Modifier
                    .size(size)
                    .offset(x = shakeX.dp, y = floatY.dp)
                    .scale(breathScale)
            ) {
                val w = this.size.width
                val h = this.size.height
                val cx = w / 2f
                val cy = h / 2f

                drawHolographicAvatar(
                    cx = cx, cy = cy, w = w, h = h,
                    bodyColor = bodyColor, glowColor = glowColor,
                    mood = mood, blinkProgress = blinkProgress,
                    eyeOpenRatio = eyeOpenRatio, browAngle = browAngle,
                    mouthCurve = mouthCurve, orbitalAngle = orbitalAngle,
                    scanY = scanY, particlePhase = particlePhase,
                    glowAlpha = glowAlpha
                )
            }
        }

        // Badge de estado con efecto neón
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(mood.glowColor).copy(alpha = 0.15f))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${mood.emoji} ${mood.label}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(mood.bodyColor)
            )
        }
        Text(
            text = mood.message,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.55f)
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  DIBUJADO HOLOGRÁFICO
// ════════════════════════════════════════════════════════════════════════════
private fun DrawScope.drawHolographicAvatar(
    cx: Float, cy: Float, w: Float, h: Float,
    bodyColor: Color, glowColor: Color,
    mood: AvatarMood,
    blinkProgress: Float, eyeOpenRatio: Float, browAngle: Float, mouthCurve: Float,
    orbitalAngle: Float, scanY: Float, particlePhase: Float, glowAlpha: Float
) {
    val unit = w * 0.08f

    // ── ANILLOS ORBITALES ────────────────────────────────────────────────
    val ringCy = cy - unit * 0.5f
    rotate(orbitalAngle, pivot = Offset(cx, ringCy)) {
        drawOval(
            color = bodyColor.copy(alpha = 0.25f),
            topLeft = Offset(cx - unit * 4f, ringCy - unit * 0.6f),
            size = Size(unit * 8f, unit * 1.2f),
            style = Stroke(width = unit * 0.12f)
        )
        // Bead en el anillo
        drawCircle(bodyColor, radius = unit * 0.28f,
            center = Offset(cx + unit * 4f, ringCy))
    }
    rotate(-orbitalAngle * 0.6f, pivot = Offset(cx, ringCy)) {
        drawOval(
            color = glowColor.copy(alpha = 0.18f),
            topLeft = Offset(cx - unit * 3.4f, ringCy - unit * 1.2f),
            size = Size(unit * 6.8f, unit * 2.4f),
            style = Stroke(width = unit * 0.1f)
        )
    }

    // ── PARTÍCULAS FLOTANTES ─────────────────────────────────────────────
    val particlePositions = listOf(
        Pair(0.15f, 0.20f), Pair(0.82f, 0.15f), Pair(0.10f, 0.60f),
        Pair(0.88f, 0.55f), Pair(0.25f, 0.82f), Pair(0.75f, 0.78f),
        Pair(0.50f, 0.05f), Pair(0.05f, 0.40f), Pair(0.95f, 0.35f)
    )
    particlePositions.forEachIndexed { i, (px, py) ->
        val phase = (particlePhase + i * 0.11f) % 1f
        val alpha = (sin(phase * PI * 2).toFloat() * 0.4f + 0.5f).coerceIn(0.1f, 0.9f)
        val yOffset = sin((particlePhase + i * 0.13f) * PI * 2).toFloat() * unit * 0.8f
        val size = unit * (0.08f + (i % 3) * 0.05f)
        drawCircle(
            color = bodyColor.copy(alpha = alpha * 0.7f),
            radius = size,
            center = Offset(px * w, py * h + yOffset)
        )
        // Algunos son rombos / cuadrados rotados
        if (i % 3 == 0) {
            rotate(45f, Offset(px * w, py * h + yOffset)) {
                drawRect(
                    color = glowColor.copy(alpha = alpha * 0.5f),
                    topLeft = Offset(px * w - size, py * h + yOffset - size),
                    size = Size(size * 2, size * 2),
                    style = Stroke(size * 0.4f)
                )
            }
        }
    }

    // ── CUERPO ────────────────────────────────────────────────────────────
    val bodyTop    = cy - unit * 1.8f
    val bodyBottom = cy + unit * 3.2f
    val bodyLeft   = cx - unit * 2.8f
    val bodyRight  = cx + unit * 2.8f
    val bodyW      = bodyRight - bodyLeft
    val bodyH      = bodyBottom - bodyTop

    // Glow exterior del cuerpo
    drawRoundRect(
        color = glowColor.copy(alpha = 0.25f),
        topLeft = Offset(bodyLeft - unit * 0.4f, bodyTop - unit * 0.4f),
        size = Size(bodyW + unit * 0.8f, bodyH + unit * 0.8f),
        cornerRadius = CornerRadius(unit * 2.5f)
    )
    // Cuerpo — gradiente holográfico
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                bodyColor.copy(alpha = 0.85f),
                glowColor.copy(alpha = 0.6f),
                bodyColor.copy(alpha = 0.75f)
            ),
            start = Offset(bodyLeft, bodyTop), end = Offset(bodyRight, bodyBottom)
        ),
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(unit * 2f)
    )
    // Brillo superior (reflection)
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
            start = Offset(cx, bodyTop), end = Offset(cx, bodyTop + bodyH * 0.4f)
        ),
        topLeft = Offset(bodyLeft + unit * 0.3f, bodyTop + unit * 0.3f),
        size = Size(bodyW - unit * 0.6f, bodyH * 0.38f),
        cornerRadius = CornerRadius(unit * 1.8f)
    )
    // Borde neón
    drawRoundRect(
        color = bodyColor.copy(alpha = 0.7f),
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(unit * 2f),
        style = Stroke(unit * 0.18f)
    )

    // ── LÍNEAS DE CIRCUITO en el cuerpo ─────────────────────────────────
    val circuitColor = bodyColor.copy(alpha = 0.4f)
    val cStroke = Stroke(unit * 0.08f, cap = StrokeCap.Round)
    // Línea horizontal central
    drawLine(circuitColor,
        Offset(bodyLeft + unit * 0.6f, cy + unit * 0.8f),
        Offset(bodyRight - unit * 0.6f, cy + unit * 0.8f), unit * 0.08f)
    // Rama izquierda
    drawLine(circuitColor, Offset(bodyLeft + unit * 1.0f, cy + unit * 0.8f),
        Offset(bodyLeft + unit * 1.0f, cy + unit * 1.8f), unit * 0.08f)
    drawLine(circuitColor, Offset(bodyLeft + unit * 1.0f, cy + unit * 1.8f),
        Offset(bodyLeft + unit * 1.6f, cy + unit * 1.8f), unit * 0.08f)
    drawCircle(bodyColor.copy(alpha = 0.6f), unit * 0.14f,
        Offset(bodyLeft + unit * 1.6f, cy + unit * 1.8f))
    // Rama derecha
    drawLine(circuitColor, Offset(bodyRight - unit * 1.0f, cy + unit * 0.8f),
        Offset(bodyRight - unit * 1.0f, cy + unit * 1.6f), unit * 0.08f)
    drawLine(circuitColor, Offset(bodyRight - unit * 1.0f, cy + unit * 1.6f),
        Offset(bodyRight - unit * 1.8f, cy + unit * 1.6f), unit * 0.08f)
    drawCircle(bodyColor.copy(alpha = 0.6f), unit * 0.14f,
        Offset(bodyRight - unit * 1.8f, cy + unit * 1.6f))

    // ── SCAN LINE (efecto holograma) ─────────────────────────────────────
    val slY = bodyTop + bodyH * scanY
    if (slY in bodyTop..bodyBottom) {
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, bodyColor.copy(alpha = 0.5f), Color.Transparent),
                startX = bodyLeft, endX = bodyRight
            ),
            start = Offset(bodyLeft, slY), end = Offset(bodyRight, slY),
            strokeWidth = unit * 0.2f
        )
    }
    // Scan line en cabeza también
    val headR = unit * 2.5f
    val headCy = bodyTop - headR * 0.6f
    val slYHead = (headCy - headR) + headR * 2f * scanY
    if (slYHead in (headCy - headR)..(headCy + headR)) {
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, bodyColor.copy(alpha = 0.45f), Color.Transparent),
                startX = cx - headR, endX = cx + headR
            ),
            start = Offset(cx - headR * 0.85f, slYHead),
            end = Offset(cx + headR * 0.85f, slYHead),
            strokeWidth = unit * 0.15f
        )
    }

    // ── CABEZA ────────────────────────────────────────────────────────────
    // Glow cabeza
    drawRoundRect(
        color = glowColor.copy(alpha = 0.3f),
        topLeft = Offset(cx - headR - unit * 0.4f, headCy - headR - unit * 0.4f),
        size = Size(headR * 2f + unit * 0.8f, headR * 2f + unit * 0.8f),
        cornerRadius = CornerRadius(headR * 0.8f)
    )
    // Cabeza principal
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                bodyColor.copy(alpha = 0.9f),
                glowColor.copy(alpha = 0.65f),
                bodyColor.copy(alpha = 0.8f)
            ),
            start = Offset(cx - headR, headCy), end = Offset(cx + headR, headCy + headR)
        ),
        topLeft = Offset(cx - headR, headCy - headR),
        size = Size(headR * 2f, headR * 2f),
        cornerRadius = CornerRadius(headR * 0.65f)
    )
    // Brillo cabeza
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.3f), Color.Transparent),
            start = Offset(cx, headCy - headR), end = Offset(cx, headCy)
        ),
        topLeft = Offset(cx - headR * 0.7f, headCy - headR + unit * 0.3f),
        size = Size(headR * 1.4f, headR * 0.6f),
        cornerRadius = CornerRadius(headR * 0.4f)
    )
    // Borde neón cabeza
    drawRoundRect(
        color = bodyColor.copy(alpha = 0.75f),
        topLeft = Offset(cx - headR, headCy - headR),
        size = Size(headR * 2f, headR * 2f),
        cornerRadius = CornerRadius(headR * 0.65f),
        style = Stroke(unit * 0.18f)
    )

    // ── ANTENA ────────────────────────────────────────────────────────────
    val antY = headCy - headR
    drawLine(glowColor.copy(alpha = 0.9f), Offset(cx, antY), Offset(cx, antY - unit * 1.6f), unit * 0.2f)
    // Punta de la antena con glow
    val antTipColor = if (mood == AvatarMood.CRITICAL) Color.Red else bodyColor
    drawCircle(antTipColor.copy(alpha = 0.35f), unit * 0.7f, Offset(cx, antY - unit * 2.0f))
    drawCircle(antTipColor, unit * 0.42f, Offset(cx, antY - unit * 2.0f))
    drawCircle(Color.White.copy(alpha = 0.8f), unit * 0.15f, Offset(cx, antY - unit * 2.0f))

    // ── OREJAS / SENSORES ─────────────────────────────────────────────────
    listOf(-1f, 1f).forEach { side ->
        val ex = cx + side * (headR + unit * 0.1f)
        drawCircle(glowColor.copy(alpha = 0.4f), unit * 0.65f, Offset(ex, headCy))
        drawCircle(bodyColor, unit * 0.4f, Offset(ex, headCy))
        drawCircle(Color.White.copy(alpha = 0.4f), unit * 0.4f, Offset(ex, headCy), style = Stroke(unit * 0.1f))
        // Detalle interior
        drawCircle(Color.White.copy(alpha = 0.6f), unit * 0.12f, Offset(ex, headCy))
    }

    // ── OJOS ──────────────────────────────────────────────────────────────
    val eyeY     = headCy - unit * 0.25f
    val eyeSpacing = unit * 1.1f
    val eyeW     = unit * 0.9f * eyeOpenRatio.coerceIn(0.5f, 1.3f)
    val eyeH     = unit * 0.9f * (1f - blinkProgress * 0.95f) * eyeOpenRatio.coerceIn(0.5f, 1.3f)

    listOf(-1f, 1f).forEach { side ->
        val ex = cx + side * eyeSpacing

        // Glow del ojo
        if (eyeH > 1f) {
            drawRoundRect(
                color = bodyColor.copy(alpha = 0.3f),
                topLeft = Offset(ex - eyeW * 1.4f, eyeY - eyeH * 1.4f),
                size = Size(eyeW * 2.8f, eyeH * 2.8f),
                cornerRadius = CornerRadius(eyeW * 0.7f)
            )
        }

        // Ceja — neón
        val browX1 = ex - eyeW * 0.95f
        val browX2 = ex + eyeW * 0.95f
        val browYBase = eyeY - eyeH - unit * 0.55f
        val browLift = if (side < 0) browAngle * 0.4f else -browAngle * 0.4f
        // Sombra ceja
        drawLine(bodyColor.copy(alpha = 0.35f),
            Offset(browX1, browYBase + browLift + unit * 0.08f),
            Offset(browX2, browYBase - browLift + unit * 0.08f),
            strokeWidth = unit * 0.32f, cap = StrokeCap.Round)
        // Ceja principal
        drawLine(Color.White.copy(alpha = 0.95f),
            Offset(browX1, browYBase + browLift),
            Offset(browX2, browYBase - browLift),
            strokeWidth = unit * 0.24f, cap = StrokeCap.Round)

        if (eyeH > 1f) {
            // Ojo exterior (iris)
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.95f), bodyColor.copy(alpha = 0.7f), glowColor.copy(alpha = 0.5f)),
                    center = Offset(ex, eyeY), radius = eyeW * 1.2f
                ),
                topLeft = Offset(ex - eyeW, eyeY - eyeH),
                size = Size(eyeW * 2f, eyeH * 2f),
                cornerRadius = CornerRadius(eyeW * 0.55f)
            )
            // Pupila con glow
            val pupilR = eyeH * 0.52f
            drawCircle(glowColor.copy(alpha = 0.5f), pupilR * 1.4f, Offset(ex, eyeY))
            drawCircle(Color(0xFF0A0A2E), radius = pupilR, center = Offset(ex, eyeY))
            // Iris ring
            drawCircle(bodyColor.copy(alpha = 0.8f), radius = pupilR,
                center = Offset(ex, eyeY), style = Stroke(unit * 0.12f))
            // Punto de luz neón en la pupila
            drawCircle(bodyColor.copy(alpha = 0.9f), radius = pupilR * 0.38f,
                center = Offset(ex + pupilR * 0.1f, eyeY - pupilR * 0.1f))
            // Brillo especular
            drawCircle(Color.White.copy(alpha = 0.9f), radius = pupilR * 0.2f,
                center = Offset(ex - pupilR * 0.25f, eyeY - pupilR * 0.28f))
        } else {
            drawLine(Color.White, Offset(ex - eyeW, eyeY), Offset(ex + eyeW, eyeY),
                unit * 0.2f, cap = StrokeCap.Round)
        }

        // Gotas de sudor si HOT/CRITICAL
        if (mood == AvatarMood.HOT || mood == AvatarMood.CRITICAL) {
            val dropX = ex + eyeW * 0.75f
            val dropY = eyeY + eyeH + unit * 0.35f
            val dropPath = Path().apply {
                moveTo(dropX, dropY + unit * 0.6f)
                cubicTo(dropX - unit * 0.25f, dropY, dropX - unit * 0.25f, dropY - unit * 0.3f, dropX, dropY - unit * 0.3f)
                cubicTo(dropX + unit * 0.25f, dropY - unit * 0.3f, dropX + unit * 0.25f, dropY, dropX, dropY + unit * 0.6f)
            }
            drawPath(dropPath, Color(0xFF81D4FA).copy(alpha = 0.9f))
            drawPath(dropPath, Color(0xFF81D4FA).copy(alpha = 0.3f), style = Stroke(unit * 0.08f))
        }
    }

    // ── NARIZ (puntito neón) ──────────────────────────────────────────────
    drawCircle(bodyColor.copy(alpha = 0.7f), unit * 0.15f, Offset(cx, headCy + unit * 0.15f))

    // ── BOCA ─────────────────────────────────────────────────────────────
    val mouthY = headCy + unit * 0.8f
    val mouthW = unit * 1.4f
    val ctrl = mouthCurve * unit * 0.75f
    val mouthPath = Path().apply {
        moveTo(cx - mouthW, mouthY)
        quadraticBezierTo(cx, mouthY + ctrl, cx + mouthW, mouthY)
    }
    // Glow boca
    drawPath(mouthPath, bodyColor.copy(alpha = 0.35f), style = Stroke(unit * 0.45f, cap = StrokeCap.Round))
    // Boca principal
    drawPath(mouthPath, Color.White.copy(alpha = 0.9f), style = Stroke(unit * 0.22f, cap = StrokeCap.Round))

    // ── PANEL PECHO ──────────────────────────────────────────────────────
    val panelTop = bodyTop + unit * 0.8f
    val panelH   = unit * 2.2f
    val panelW   = unit * 3.8f
    // Panel bg
    drawRoundRect(
        color = Color(0xFF050510).copy(alpha = 0.7f),
        topLeft = Offset(cx - panelW / 2f, panelTop),
        size = Size(panelW, panelH),
        cornerRadius = CornerRadius(unit * 0.7f)
    )
    // Borde neón panel
    drawRoundRect(
        color = bodyColor.copy(alpha = 0.6f),
        topLeft = Offset(cx - panelW / 2f, panelTop),
        size = Size(panelW, panelH),
        cornerRadius = CornerRadius(unit * 0.7f),
        style = Stroke(unit * 0.12f)
    )
    // Líneas de datos en el panel
    val lineConfigs = listOf(
        Pair(panelTop + panelH * 0.28f, 0.75f),
        Pair(panelTop + panelH * 0.52f, 0.50f),
        Pair(panelTop + panelH * 0.76f, 0.62f)
    )
    lineConfigs.forEach { (ly, fraction) ->
        val lw = panelW * fraction
        drawLine(bodyColor.copy(alpha = 0.55f),
            Offset(cx - lw / 2f, ly), Offset(cx + lw / 2f, ly),
            unit * 0.12f, cap = StrokeCap.Round)
    }
    // Indicador LED en esquina del panel
    drawCircle(
        color = if (mood == AvatarMood.CRITICAL) Color.Red else bodyColor,
        radius = unit * 0.18f,
        center = Offset(cx + panelW / 2f - unit * 0.4f, panelTop + unit * 0.4f)
    )

    // ── BRAZOS ────────────────────────────────────────────────────────────
    val armY = bodyTop + unit * 1.6f
    val armLiftL = when (mood) { AvatarMood.COOL -> -unit * 2f; AvatarMood.CRITICAL -> unit * 0.6f; else -> 0f }
    val armLiftR = when (mood) { AvatarMood.COOL -> -unit * 1.3f; AvatarMood.CRITICAL -> unit * 0.6f; else -> 0f }

    // Brazo izquierdo
    drawLine(
        brush = Brush.linearGradient(listOf(bodyColor, glowColor.copy(alpha = 0.7f)),
            start = Offset(bodyLeft, armY), end = Offset(bodyLeft - unit * 1.7f, armY + armLiftL)),
        start = Offset(bodyLeft, armY), end = Offset(bodyLeft - unit * 1.7f, armY + armLiftL),
        strokeWidth = unit * 0.75f, cap = StrokeCap.Round
    )
    // Mano izquierda — con glow
    drawCircle(bodyColor.copy(alpha = 0.4f), unit * 0.65f, Offset(bodyLeft - unit * 1.7f, armY + armLiftL))
    drawCircle(bodyColor, unit * 0.42f, Offset(bodyLeft - unit * 1.7f, armY + armLiftL))

    // Brazo derecho
    drawLine(
        brush = Brush.linearGradient(listOf(bodyColor, glowColor.copy(alpha = 0.7f)),
            start = Offset(bodyRight, armY), end = Offset(bodyRight + unit * 1.7f, armY + armLiftR)),
        start = Offset(bodyRight, armY), end = Offset(bodyRight + unit * 1.7f, armY + armLiftR),
        strokeWidth = unit * 0.75f, cap = StrokeCap.Round
    )
    drawCircle(bodyColor.copy(alpha = 0.4f), unit * 0.65f, Offset(bodyRight + unit * 1.7f, armY + armLiftR))
    drawCircle(bodyColor, unit * 0.42f, Offset(bodyRight + unit * 1.7f, armY + armLiftR))

    // ── PIERNAS ───────────────────────────────────────────────────────────
    val legTop = bodyBottom
    val legH   = unit * 1.5f
    listOf(-1f, 1f).forEach { side ->
        val lx = cx + side * unit * 1.3f
        drawRoundRect(
            brush = Brush.linearGradient(listOf(bodyColor, glowColor.copy(alpha = 0.6f)),
                start = Offset(lx, legTop), end = Offset(lx, legTop + legH)),
            topLeft = Offset(lx - unit * 0.65f, legTop),
            size = Size(unit * 1.3f, legH),
            cornerRadius = CornerRadius(unit * 0.5f)
        )
        // Pie con glow
        drawRoundRect(
            color = glowColor.copy(alpha = 0.4f),
            topLeft = Offset(lx - unit * 0.95f, legTop + legH - unit * 0.15f),
            size = Size(unit * 1.9f, unit * 0.65f),
            cornerRadius = CornerRadius(unit * 0.45f)
        )
        drawRoundRect(
            color = glowColor,
            topLeft = Offset(lx - unit * 0.9f, legTop + legH - unit * 0.1f),
            size = Size(unit * 1.8f, unit * 0.55f),
            cornerRadius = CornerRadius(unit * 0.4f)
        )
    }

    // ── VAPOR EN CRÍTICO ─────────────────────────────────────────────────
    if (mood == AvatarMood.CRITICAL) {
        listOf(-unit * 1.6f, 0f, unit * 1.6f).forEachIndexed { i, dx ->
            val vaporPath = Path().apply {
                val vx = cx + dx
                val vy = headCy - headR - unit * 0.2f
                moveTo(vx, vy)
                quadraticBezierTo(vx + unit * 0.5f, vy - unit * 0.9f, vx, vy - unit * 1.8f)
                quadraticBezierTo(vx - unit * 0.5f, vy - unit * 2.5f, vx, vy - unit * 3.2f)
            }
            drawPath(vaporPath, bodyColor.copy(alpha = 0.2f + i * 0.06f),
                style = Stroke(unit * 0.22f, cap = StrokeCap.Round))
        }
    }

    // ── HEXÁGONOS decorativos en esquinas (neón) ─────────────────────────
    if (mood == AvatarMood.COOL || mood == AvatarMood.NORMAL) {
        val hexPositions = listOf(Offset(unit * 0.5f, unit * 0.5f), Offset(w - unit * 1f, h - unit * 1f))
        hexPositions.forEach { hp ->
            val hR = unit * 0.5f
            val hexPath = Path().apply {
                for (i in 0..5) {
                    val angle = Math.toRadians((60.0 * i - 30)).toFloat()
                    if (i == 0) moveTo(hp.x + hR * cos(angle), hp.y + hR * sin(angle))
                    else lineTo(hp.x + hR * cos(angle), hp.y + hR * sin(angle))
                }
                close()
            }
            drawPath(hexPath, bodyColor.copy(alpha = 0.25f), style = Stroke(unit * 0.08f))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  CARD PARA EL DASHBOARD
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun AvatarCard(
    level: ThermalLevel,
    temp: Float,
    modifier: Modifier = Modifier
) {
    val mood      = level.toMood()
    val glowColor = Color(mood.glowColor)
    val bodyColor = Color(mood.bodyColor)
    val inf       = rememberInfiniteTransition(label = "card_glow")
    val cardGlow by inf.animateFloat(
        initialValue = 0.06f, targetValue = 0.14f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOut), RepeatMode.Reverse),
        label = "card_glow_alpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF04040F))
            .padding(0.dp),
        contentAlignment = Alignment.Center
    ) {
        // Fondo holográfico con doble gradiente
        Box(
            modifier = Modifier.fillMaxWidth().height(260.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = cardGlow * 1.5f), Color.Transparent),
                        radius = 500f
                    )
                )
        )
        // Grid sutil de fondo
        Canvas(modifier = Modifier.fillMaxWidth().height(260.dp)) {
            val step = 28f
            val gridColor = bodyColor.copy(alpha = 0.04f)
            var x = 0f
            while (x <= size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 0.8f)
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 0.8f)
                y += step
            }
        }
        ThermaAvatar(
            level = level,
            temp = temp,
            modifier = Modifier.padding(16.dp),
            size = 160.dp
        )
    }
}
