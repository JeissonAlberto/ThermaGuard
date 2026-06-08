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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.ThermalLevel
import kotlin.math.*

// ════════════════════════════════════════════════════════════════════════════
//  AVATAR MOOD — estado de ánimo según temperatura
// ════════════════════════════════════════════════════════════════════════════
enum class AvatarMood(
    val emoji: String,
    val label: String,
    val bodyColor: Long,
    val glowColor: Long,
    val message: String
) {
    COOL    ("😎", "Fresco",      0xFF00E5FF, 0xFF00B8D4, "Tu dispositivo está fresco y feliz"),
    NORMAL  ("🙂", "Normal",      0xFF69F0AE, 0xFF00C853, "Todo en orden, monitoreando..."),
    WARM    ("😅", "Tibio",       0xFFFFD740, 0xFFFF8F00, "Calentando un poco, ojo..."),
    HOT     ("😰", "Caliente",    0xFFFF6E40, 0xFFDD2C00, "¡Está caliente! Cierra apps pesadas"),
    CRITICAL("🚨", "¡Crítico!",   0xFFFF1744, 0xFF9B0000, "¡EMERGENCIA! El dispositivo está al límite")
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
    val mood = level.toMood()
    val bodyColor  = Color(mood.bodyColor)
    val glowColor  = Color(mood.glowColor)

    val inf = rememberInfiniteTransition(label = "avatar")

    // ── Animaciones base ───────────────────────────────────────────────────
    // Float corporal (siempre)
    val floatY by inf.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(2200, easing = EaseInOut), RepeatMode.Reverse),
        label = "float"
    )

    // Parpadeo — más rápido si está en pánico
    val blinkSpeed = when (mood) {
        AvatarMood.CRITICAL -> 600
        AvatarMood.HOT      -> 900
        else                -> 3500
    }
    val blinkProgress by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = blinkSpeed
                0f   at 0
                0f   at (blinkSpeed * 0.85f).toInt()
                1f   at (blinkSpeed * 0.92f).toInt()
                0f   at blinkSpeed
            },
            RepeatMode.Restart
        ), label = "blink"
    )

    // Temblor en crítico
    val shakeX by inf.animateFloat(
        initialValue = if (mood == AvatarMood.CRITICAL) -3f else 0f,
        targetValue  = if (mood == AvatarMood.CRITICAL)  3f else 0f,
        animationSpec = infiniteRepeatable(tween(80), RepeatMode.Reverse),
        label = "shake"
    )

    // Escala de respiración
    val breathScale by inf.animateFloat(
        initialValue = 1f, targetValue = if (mood == AvatarMood.CRITICAL) 1.06f else 1.02f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOut), RepeatMode.Reverse),
        label = "breath"
    )

    // Brillo del glow
    val glowAlpha by inf.animateFloat(
        initialValue = 0.3f, targetValue = if (mood == AvatarMood.CRITICAL) 0.9f else 0.55f,
        animationSpec = infiniteRepeatable(tween(1100, easing = EaseInOut), RepeatMode.Reverse),
        label = "glow"
    )

    // Ojos: expresión por mood
    // 0 = abierto, 1 = cerrado (blink)
    val eyeOpenRatio by animateFloatAsState(
        targetValue = when (mood) {
            AvatarMood.HOT      -> 0.6f   // entrecerrado
            AvatarMood.CRITICAL -> 0.5f   // muy entrecerrado
            AvatarMood.COOL     -> 1.2f   // grande y feliz
            else                -> 1.0f
        },
        animationSpec = tween(500), label = "eye"
    )

    // Ceja — ángulo de inclinación
    val browAngle by animateFloatAsState(
        targetValue = when (mood) {
            AvatarMood.WARM     -> 8f    // preocupado
            AvatarMood.HOT      -> 14f   // muy preocupado
            AvatarMood.CRITICAL -> 20f   // pánico
            AvatarMood.COOL     -> -5f   // relajado
            else                -> 0f
        },
        animationSpec = tween(600, easing = EaseOutCubic), label = "brow"
    )

    // Boca — curvatura (positivo = sonrisa, negativo = tristeza)
    val mouthCurve by animateFloatAsState(
        targetValue = when (mood) {
            AvatarMood.COOL     -> 0.8f
            AvatarMood.NORMAL   -> 0.3f
            AvatarMood.WARM     -> -0.1f
            AvatarMood.HOT      -> -0.5f
            AvatarMood.CRITICAL -> -0.8f
        },
        animationSpec = tween(700, easing = EaseOutCubic), label = "mouth"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Glow exterior
            Box(
                modifier = Modifier
                    .size(size * 1.4f)
                    .clip(CircleShape)
                    .background(glowColor.copy(alpha = glowAlpha * 0.25f))
            )
            // Canvas del robot
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

                drawAvatar(
                    cx = cx, cy = cy, w = w, h = h,
                    bodyColor = bodyColor,
                    glowColor = glowColor,
                    mood = mood,
                    blinkProgress = blinkProgress,
                    eyeOpenRatio = eyeOpenRatio,
                    browAngle = browAngle,
                    mouthCurve = mouthCurve
                )
            }
        }

        // Label de estado
        Text(
            text = "${mood.emoji} ${mood.label}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(mood.bodyColor)
        )
        Text(
            text = mood.message,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  DIBUJADO DEL ROBOT EN CANVAS
// ════════════════════════════════════════════════════════════════════════════
private fun DrawScope.drawAvatar(
    cx: Float, cy: Float, w: Float, h: Float,
    bodyColor: Color, glowColor: Color,
    mood: AvatarMood,
    blinkProgress: Float,
    eyeOpenRatio: Float,
    browAngle: Float,
    mouthCurve: Float
) {
    val unit = w * 0.08f   // unidad base de escala

    // ── CUERPO ────────────────────────────────────────────────────────────
    val bodyTop    = cy - unit * 1.8f
    val bodyBottom = cy + unit * 3.2f
    val bodyLeft   = cx - unit * 2.8f
    val bodyRight  = cx + unit * 2.8f

    // Sombra/glow del cuerpo
    drawRoundRect(
        color = glowColor.copy(alpha = 0.3f),
        topLeft = Offset(bodyLeft - unit * 0.3f, bodyTop - unit * 0.3f),
        size = Size(bodyRight - bodyLeft + unit * 0.6f, bodyBottom - bodyTop + unit * 0.6f),
        cornerRadius = CornerRadius(unit * 2.5f),
        style = Fill
    )

    // Cuerpo principal
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(bodyColor, glowColor),
            start = Offset(cx, bodyTop), end = Offset(cx, bodyBottom)
        ),
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyRight - bodyLeft, bodyBottom - bodyTop),
        cornerRadius = CornerRadius(unit * 2f)
    )

    // Borde metálico
    drawRoundRect(
        color = Color.White.copy(alpha = 0.25f),
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyRight - bodyLeft, bodyBottom - bodyTop),
        cornerRadius = CornerRadius(unit * 2f),
        style = Stroke(width = unit * 0.15f)
    )

    // ── CABEZA ────────────────────────────────────────────────────────────
    val headR = unit * 2.5f
    val headCy = bodyTop - headR * 0.6f
    val headTop   = headCy - headR
    val headLeft  = cx - headR
    val headRight = cx + headR

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(bodyColor.copy(alpha = 0.9f), glowColor.copy(alpha = 0.7f)),
            start = Offset(cx, headCy - headR), end = Offset(cx, headCy + headR)
        ),
        topLeft = Offset(headLeft, headCy - headR),
        size = Size(headR * 2f, headR * 2f),
        cornerRadius = CornerRadius(headR * 0.7f)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.2f),
        topLeft = Offset(headLeft, headCy - headR),
        size = Size(headR * 2f, headR * 2f),
        cornerRadius = CornerRadius(headR * 0.7f),
        style = Stroke(width = unit * 0.15f)
    )

    // ── ANTENA ────────────────────────────────────────────────────────────
    val antY = headCy - headR
    drawLine(Color.White.copy(alpha = 0.7f), Offset(cx, antY), Offset(cx, antY - unit * 1.5f), unit * 0.18f)
    drawCircle(
        if (mood == AvatarMood.CRITICAL) Color.Red else Color.White,
        radius = unit * 0.45f,
        center = Offset(cx, antY - unit * 1.8f)
    )

    // ── OREJAS/ALTAVOCES ──────────────────────────────────────────────────
    listOf(-1f, 1f).forEach { side ->
        val ex = cx + side * headR * 0.95f
        drawCircle(glowColor.copy(alpha = 0.5f), unit * 0.5f, Offset(ex, headCy))
        drawCircle(Color.White.copy(alpha = 0.3f), unit * 0.5f, Offset(ex, headCy), style = Stroke(unit * 0.1f))
    }

    // ── OJOS ─────────────────────────────────────────────────────────────
    val eyeY = headCy - unit * 0.3f
    val eyeSpacing = unit * 1.1f
    val eyeW = unit * 0.85f * eyeOpenRatio.coerceIn(0.5f, 1.3f)
    val eyeH = unit * 0.85f * (1f - blinkProgress * 0.95f) * eyeOpenRatio.coerceIn(0.5f, 1.3f)

    listOf(-1f, 1f).forEach { side ->
        val ex = cx + side * eyeSpacing

        // Ceja
        val browX1 = ex - eyeW * 0.9f
        val browX2 = ex + eyeW * 0.9f
        val browYBase = eyeY - eyeH - unit * 0.5f
        val browLift = if (side < 0) browAngle * 0.4f else -browAngle * 0.4f  // cejas asimétricas en pánico
        drawLine(
            Color.White.copy(alpha = 0.9f),
            Offset(browX1, browYBase + browLift),
            Offset(browX2, browYBase - browLift),
            strokeWidth = unit * 0.22f,
            cap = StrokeCap.Round
        )

        // Ojo (rectángulo redondeado)
        if (eyeH > 1f) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color(0xFFB3E5FC)),
                    center = Offset(ex, eyeY),
                    radius = eyeW
                ),
                topLeft = Offset(ex - eyeW, eyeY - eyeH),
                size = Size(eyeW * 2f, eyeH * 2f),
                cornerRadius = CornerRadius(eyeW * 0.6f)
            )
            // Pupila
            val pupilSize = eyeH * 0.55f
            drawCircle(Color(0xFF1A237E), radius = pupilSize, center = Offset(ex, eyeY))
            // Brillo
            drawCircle(Color.White.copy(alpha = 0.8f), radius = pupilSize * 0.35f,
                center = Offset(ex - pupilSize * 0.2f, eyeY - pupilSize * 0.2f))
        } else {
            // Parpadeo — línea horizontal
            drawLine(Color.White, Offset(ex - eyeW, eyeY), Offset(ex + eyeW, eyeY), unit * 0.18f, cap = StrokeCap.Round)
        }

        // Gotas de sudor si está caliente
        if (mood == AvatarMood.HOT || mood == AvatarMood.CRITICAL) {
            val dropX = ex + eyeW * 0.7f
            val dropY = eyeY + eyeH + unit * 0.4f
            val dropPath = Path().apply {
                moveTo(dropX, dropY + unit * 0.6f)
                cubicTo(dropX - unit * 0.25f, dropY, dropX - unit * 0.25f, dropY - unit * 0.3f, dropX, dropY - unit * 0.3f)
                cubicTo(dropX + unit * 0.25f, dropY - unit * 0.3f, dropX + unit * 0.25f, dropY, dropX, dropY + unit * 0.6f)
            }
            drawPath(dropPath, Color(0xFF81D4FA).copy(alpha = 0.85f))
        }
    }

    // ── BOCA ─────────────────────────────────────────────────────────────
    val mouthY = headCy + unit * 0.8f
    val mouthW = unit * 1.4f
    val mouthPath = Path().apply {
        val ctrl = mouthCurve * unit * 0.7f
        moveTo(cx - mouthW, mouthY)
        quadraticBezierTo(cx, mouthY + ctrl, cx + mouthW, mouthY)
    }
    drawPath(mouthPath, Color.White.copy(alpha = 0.85f), style = Stroke(unit * 0.22f, cap = StrokeCap.Round))

    // ── PANEL PECHO (display de temperatura) ─────────────────────────────
    val panelTop  = bodyTop + unit * 0.8f
    val panelH    = unit * 2.2f
    val panelW    = unit * 3.8f
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.35f),
        topLeft = Offset(cx - panelW / 2f, panelTop),
        size = Size(panelW, panelH),
        cornerRadius = CornerRadius(unit * 0.6f)
    )
    drawRoundRect(
        color = bodyColor.copy(alpha = 0.4f),
        topLeft = Offset(cx - panelW / 2f, panelTop),
        size = Size(panelW, panelH),
        cornerRadius = CornerRadius(unit * 0.6f),
        style = Stroke(unit * 0.1f)
    )
    // Líneas de "datos" decorativas
    val lineY1 = panelTop + panelH * 0.35f
    val lineY2 = panelTop + panelH * 0.65f
    listOf(lineY1, lineY2).forEachIndexed { i, ly ->
        val lw = panelW * (if (i == 0) 0.7f else 0.45f)
        drawLine(bodyColor.copy(alpha = 0.6f), Offset(cx - lw / 2f, ly), Offset(cx + lw / 2f, ly), unit * 0.12f, cap = StrokeCap.Round)
    }

    // ── BRAZOS ────────────────────────────────────────────────────────────
    val armY = bodyTop + unit * 1.5f
    // Brazo izquierdo — levantado si está feliz, caído si caliente
    val armLiftL = when (mood) {
        AvatarMood.COOL -> -unit * 1.8f
        AvatarMood.CRITICAL -> unit * 0.5f
        else -> 0f
    }
    val armLiftR = when (mood) {
        AvatarMood.COOL -> -unit * 1.2f
        AvatarMood.CRITICAL -> unit * 0.5f
        else -> 0f
    }
    // Brazo izq
    drawLine(
        brush = Brush.linearGradient(listOf(bodyColor, glowColor),
            start = Offset(bodyLeft, armY), end = Offset(bodyLeft - unit * 1.6f, armY + armLiftL)),
        start = Offset(bodyLeft, armY),
        end = Offset(bodyLeft - unit * 1.6f, armY + armLiftL),
        strokeWidth = unit * 0.7f, cap = StrokeCap.Round
    )
    // Mano izq
    drawCircle(bodyColor, unit * 0.45f, Offset(bodyLeft - unit * 1.6f, armY + armLiftL))

    // Brazo der
    drawLine(
        brush = Brush.linearGradient(listOf(bodyColor, glowColor),
            start = Offset(bodyRight, armY), end = Offset(bodyRight + unit * 1.6f, armY + armLiftR)),
        start = Offset(bodyRight, armY),
        end = Offset(bodyRight + unit * 1.6f, armY + armLiftR),
        strokeWidth = unit * 0.7f, cap = StrokeCap.Round
    )
    drawCircle(bodyColor, unit * 0.45f, Offset(bodyRight + unit * 1.6f, armY + armLiftR))

    // ── PIERNAS ───────────────────────────────────────────────────────────
    val legTop = bodyBottom
    val legH   = unit * 1.4f
    listOf(-1f, 1f).forEach { side ->
        val lx = cx + side * unit * 1.3f
        drawRoundRect(
            brush = Brush.linearGradient(listOf(bodyColor, glowColor),
                start = Offset(lx, legTop), end = Offset(lx, legTop + legH)),
            topLeft = Offset(lx - unit * 0.65f, legTop),
            size = Size(unit * 1.3f, legH),
            cornerRadius = CornerRadius(unit * 0.5f)
        )
        // Pie
        drawRoundRect(
            color = glowColor,
            topLeft = Offset(lx - unit * 0.85f, legTop + legH - unit * 0.1f),
            size = Size(unit * 1.7f, unit * 0.6f),
            cornerRadius = CornerRadius(unit * 0.4f)
        )
    }

    // ── VAPOR en modo CRITICAL ────────────────────────────────────────────
    if (mood == AvatarMood.CRITICAL) {
        listOf(-unit * 1.5f, 0f, unit * 1.5f).forEachIndexed { i, dx ->
            val vaporPath = Path().apply {
                val vx = cx + dx
                val vy = headCy - headR - unit * 0.3f
                moveTo(vx, vy)
                quadraticBezierTo(vx + unit * 0.4f, vy - unit * 0.8f, vx, vy - unit * 1.6f)
                quadraticBezierTo(vx - unit * 0.4f, vy - unit * 2.2f, vx, vy - unit * 3f)
            }
            drawPath(vaporPath, Color.White.copy(alpha = 0.25f + i * 0.05f),
                style = Stroke(unit * 0.2f, cap = StrokeCap.Round))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  CARD CON EL AVATAR PARA EL DASHBOARD
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun AvatarCard(
    level: ThermalLevel,
    temp: Float,
    modifier: Modifier = Modifier
) {
    val mood = level.toMood()
    val glowColor = Color(mood.glowColor)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0A0A1A))
            .then(Modifier.padding(0.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Fondo con gradiente
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = 0.15f), Color.Transparent),
                        radius = 400f
                    )
                )
        )
        ThermaAvatar(
            level = level,
            temp = temp,
            modifier = Modifier.padding(16.dp),
            size = 160.dp
        )
    }
}
