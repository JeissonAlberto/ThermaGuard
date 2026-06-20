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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.ThermalLevel
import kotlin.math.*

// ════════════════════════════════════════════════════════════════════════════
//  ESTADO TÉRMICO
// ════════════════════════════════════════════════════════════════════════════
enum class AvatarMood(
    val emoji:     String,
    val label:     String,
    val bodyColor: Long,
    val glowColor: Long,
    val message:   String
) {
    COOL    ("❄️",  "Fresco",    0xFF00E5FF, 0xFF00B8D4, "Sistema fresco — rendimiento óptimo"),
    NORMAL  ("✅",  "Normal",    0xFF69F0AE, 0xFF00C853, "Temperatura normal — todo en orden"),
    WARM    ("⚠️",  "Tibio",     0xFFFFD740, 0xFFFF8F00, "Calentando — considera cerrar apps"),
    HOT     ("🔥",  "Caliente",  0xFFFF6E40, 0xFFDD2C00, "Temperatura alta — actúa ahora"),
    CRITICAL("🚨",  "¡Crítico!", 0xFFFF1744, 0xFF9B0000, "EMERGENCIA — al límite térmico")
}

fun ThermalLevel.toMood() = when (this) {
    ThermalLevel.NORMAL    -> AvatarMood.COOL
    ThermalLevel.WARM      -> AvatarMood.NORMAL
    ThermalLevel.HOT       -> AvatarMood.WARM
    ThermalLevel.CRITICAL  -> AvatarMood.HOT
    ThermalLevel.EMERGENCY -> AvatarMood.CRITICAL
}

// ════════════════════════════════════════════════════════════════════════════
//  TERMÓMETRO CIRCULAR — diseño premium tipo gauge
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun ThermaAvatar(
    level:    ThermalLevel,
    temp:     Float,
    modifier: Modifier = Modifier,
    size:     Dp = 180.dp
) {
    val mood      = level.toMood()
    val primary   = Color(mood.bodyColor)
    val secondary = Color(mood.glowColor)
    val inf       = rememberInfiniteTransition(label = "gauge")

    // Temperatura animada suavemente
    val animTemp by animateFloatAsState(
        targetValue   = temp.coerceIn(20f, 60f),
        animationSpec = tween(800, easing = EaseOutCubic),
        label         = "temp"
    )

    // Glow pulsante
    val glowAlpha by inf.animateFloat(
        initialValue  = 0.35f,
        targetValue   = if (mood == AvatarMood.CRITICAL) 0.95f else 0.65f,
        animationSpec = infiniteRepeatable(tween(1100, easing = EaseInOut), RepeatMode.Reverse),
        label         = "glow"
    )

    // Rotación lenta del anillo exterior decorativo
    val ringAngle by inf.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
        label         = "ring"
    )

    // Pulso de escala en estado crítico
    val pulseScale by inf.animateFloat(
        initialValue  = 1f,
        targetValue   = if (mood == AvatarMood.CRITICAL) 1.04f else 1.0f,
        animationSpec = infiniteRepeatable(tween(600, easing = EaseInOut), RepeatMode.Reverse),
        label         = "pulse"
    )

    // Progreso del arco (20°C = 0%, 60°C = 100%)
    val fraction  = ((animTemp - 20f) / 40f).coerceIn(0f, 1f)
    // El arco barre 240° (desde 150° hasta 390° = 30° pasando por abajo)
    val sweepDeg  = fraction * 240f

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Halo exterior de glow
            Box(
                modifier = Modifier
                    .size(size * 1.55f)
                    .clip(CircleShape)
                    .background(secondary.copy(alpha = glowAlpha * 0.10f))
            )
            Box(
                modifier = Modifier
                    .size(size * 1.25f)
                    .clip(CircleShape)
                    .background(secondary.copy(alpha = glowAlpha * 0.16f))
            )

            Canvas(modifier = Modifier.size(size)) {
                drawGauge(
                    fraction   = fraction,
                    sweepDeg   = sweepDeg,
                    temp       = animTemp,
                    primary    = primary,
                    secondary  = secondary,
                    mood       = mood,
                    glowAlpha  = glowAlpha,
                    ringAngle  = ringAngle,
                    pulseScale = pulseScale
                )
            }
        }

        // Badge de estado
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(secondary.copy(alpha = 0.14f))
                .padding(horizontal = 16.dp, vertical = 5.dp)
        ) {
            Text(
                text       = "${mood.emoji}  ${mood.label}",
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                color      = primary
            )
        }

        Text(
            text    = mood.message,
            fontSize = 10.sp,
            color   = Color.White.copy(alpha = 0.50f)
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  DIBUJADO DEL GAUGE
// ════════════════════════════════════════════════════════════════════════════
private fun DrawScope.drawGauge(
    fraction:   Float,
    sweepDeg:   Float,
    temp:       Float,
    primary:    Color,
    secondary:  Color,
    mood:       AvatarMood,
    glowAlpha:  Float,
    ringAngle:  Float,
    pulseScale: Float
) {
    val w   = size.width
    val h   = size.height
    val cx  = w / 2f
    val cy  = h / 2f
    val r   = w * 0.42f   // radio del arco principal
    val sw  = w * 0.055f  // stroke width arco

    // ── Anillo decorativo exterior giratorio ─────────────────────────────
    rotate(ringAngle, Offset(cx, cy)) {
        // Puntos decorativos en el anillo
        val dotCount = 24
        repeat(dotCount) { i ->
            val angle = (i * 360f / dotCount) * (PI / 180f).toFloat()
            val dotR  = w * 0.49f
            val alpha = if (i % 3 == 0) glowAlpha * 0.7f else glowAlpha * 0.25f
            val dSize = if (i % 3 == 0) w * 0.022f else w * 0.012f
            drawCircle(
                color  = primary.copy(alpha = alpha),
                radius = dSize,
                center = Offset(cx + dotR * cos(angle), cy + dotR * sin(angle))
            )
        }
    }

    // ── Track (arco de fondo) ────────────────────────────────────────────
    val startAngle = 150f
    val arcRect    = androidx.compose.ui.geometry.Rect(
        center = Offset(cx, cy),
        radius = r
    )
    drawArc(
        color      = Color.White.copy(alpha = 0.07f),
        startAngle = startAngle,
        sweepAngle = 240f,
        useCenter  = false,
        topLeft    = Offset(cx - r, cy - r),
        size       = Size(r * 2, r * 2),
        style      = Stroke(width = sw, cap = StrokeCap.Round)
    )

    // ── Ticks de temperatura ─────────────────────────────────────────────
    val tickTemps = listOf(20f, 30f, 40f, 50f, 60f)
    tickTemps.forEach { t ->
        val tickFrac  = (t - 20f) / 40f
        val tickAngle = (startAngle + tickFrac * 240f) * (PI / 180f).toFloat()
        val outerR    = r - sw * 0.5f
        val innerR    = r - sw * 1.8f
        val isMajor   = t.toInt() % 10 == 0
        drawLine(
            color       = primary.copy(alpha = if (isMajor) 0.5f else 0.25f),
            start       = Offset(cx + outerR * cos(tickAngle), cy + outerR * sin(tickAngle)),
            end         = Offset(cx + innerR * cos(tickAngle), cy + innerR * sin(tickAngle)),
            strokeWidth = if (isMajor) sw * 0.35f else sw * 0.18f,
            cap         = StrokeCap.Round
        )
    }

    // ── Arco de progreso con gradiente ───────────────────────────────────
    if (sweepDeg > 1f) {
        // Sombra/glow del arco
        drawArc(
            color      = secondary.copy(alpha = glowAlpha * 0.4f),
            startAngle = startAngle,
            sweepAngle = sweepDeg,
            useCenter  = false,
            topLeft    = Offset(cx - r, cy - r),
            size       = Size(r * 2, r * 2),
            style      = Stroke(width = sw * 2.2f, cap = StrokeCap.Round)
        )
        // Arco principal
        drawArc(
            brush      = Brush.sweepGradient(
                0f   to secondary,
                0.5f to primary,
                1f   to primary,
                center = Offset(cx, cy)
            ),
            startAngle = startAngle,
            sweepAngle = sweepDeg,
            useCenter  = false,
            topLeft    = Offset(cx - r, cy - r),
            size       = Size(r * 2, r * 2),
            style      = Stroke(width = sw, cap = StrokeCap.Round)
        )
        // Punta del arco — círculo brillante
        val tipAngle = (startAngle + sweepDeg) * (PI / 180f).toFloat()
        drawCircle(
            color  = Color.White,
            radius = sw * 0.7f,
            center = Offset(cx + r * cos(tipAngle), cy + r * sin(tipAngle))
        )
        drawCircle(
            color  = primary,
            radius = sw * 0.45f,
            center = Offset(cx + r * cos(tipAngle), cy + r * sin(tipAngle))
        )
    }

    // ── Centro — ícono de chip (cuadrado con pines) ──────────────────────
    val chipSize  = w * 0.28f * pulseScale
    val chipStroke = w * 0.015f
    val chipColor = primary

    // Cuerpo del chip
    drawRoundRect(
        color       = Color(0xFF0A1628),
        topLeft     = Offset(cx - chipSize / 2f, cy - chipSize / 2f),
        size        = Size(chipSize, chipSize),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(chipSize * 0.12f),
    )
    drawRoundRect(
        color        = chipColor.copy(alpha = 0.85f),
        topLeft      = Offset(cx - chipSize / 2f, cy - chipSize / 2f),
        size         = Size(chipSize, chipSize),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(chipSize * 0.12f),
        style        = Stroke(width = chipStroke)
    )

    // Pines del chip (3 por cada lado)
    val pinCount  = 3
    val pinLen    = chipSize * 0.20f
    val pinW      = chipStroke * 0.9f
    val pinSpacing = chipSize / (pinCount + 1)
    val pinColor  = chipColor.copy(alpha = 0.65f)

    repeat(pinCount) { i ->
        val offset = -chipSize / 2f + pinSpacing * (i + 1)
        // Superior
        drawLine(pinColor,
            Offset(cx + offset, cy - chipSize / 2f),
            Offset(cx + offset, cy - chipSize / 2f - pinLen),
            pinW, StrokeCap.Round)
        // Inferior
        drawLine(pinColor,
            Offset(cx + offset, cy + chipSize / 2f),
            Offset(cx + offset, cy + chipSize / 2f + pinLen),
            pinW, StrokeCap.Round)
        // Izquierdo
        drawLine(pinColor,
            Offset(cx - chipSize / 2f, cy + offset),
            Offset(cx - chipSize / 2f - pinLen, cy + offset),
            pinW, StrokeCap.Round)
        // Derecho
        drawLine(pinColor,
            Offset(cx + chipSize / 2f, cy + offset),
            Offset(cx + chipSize / 2f + pinLen, cy + offset),
            pinW, StrokeCap.Round)
    }

    // Grid interior del chip (4x4)
    val gridCells = 3
    val cellSize  = chipSize * 0.58f / gridCells
    val gridOff   = chipSize * 0.21f
    repeat(gridCells) { row ->
        repeat(gridCells) { col ->
            val gx = cx - chipSize / 2f + gridOff + col * cellSize + cellSize / 2f
            val gy = cy - chipSize / 2f + gridOff + row * cellSize + cellSize / 2f
            drawRoundRect(
                color        = chipColor.copy(alpha = 0.30f + fraction * 0.40f),
                topLeft      = Offset(gx - cellSize * 0.35f, gy - cellSize * 0.35f),
                size         = Size(cellSize * 0.7f, cellSize * 0.7f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cellSize * 0.15f),
                style        = Stroke(width = chipStroke * 0.7f)
            )
        }
    }

    // ── Temperatura — texto en el centro ─────────────────────────────────
    // Usamos drawContext para texto nativo de Canvas
    val paint = android.graphics.Paint().apply {
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }

    // Valor °C grande
    paint.textSize = w * 0.155f
    paint.color = android.graphics.Color.WHITE
    paint.isFakeBoldText = true
    drawContext.canvas.nativeCanvas.drawText(
        "${temp.toInt()}°",
        cx, cy + w * 0.07f,
        paint
    )

    // Label pequeño debajo
    paint.textSize = w * 0.065f
    paint.color = android.graphics.Color.argb(150, 200, 210, 230)
    paint.isFakeBoldText = false
    drawContext.canvas.nativeCanvas.drawText(
        "TEMP CPU/BAT",
        cx, cy + w * 0.155f,
        paint
    )
}
