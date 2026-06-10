package com.jeissonalberto.thermaguard.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.jeissonalberto.thermaguard.domain.ThermalViewModel

@Composable
fun RootControlScreen(viewModel: ThermalViewModel) {
    val snap     = viewModel.latestSnapshot.collectAsState().value
    val rootOk   = viewModel.rootAvailable.collectAsState().value
    val superActive = viewModel.superCoolActive.collectAsState().value
    val ultraActive = viewModel.ultraCoolActive.collectAsState().value
    val lastResult  = viewModel.superCoolResult.collectAsState().value

    val neonBlue  = Color(0xFF00B4FF)
    val neonGreen = Color(0xFF00FF9C)
    val neonRed   = Color(0xFFFF3B5C)
    val neonPurple= Color(0xFFB044FF)
    val bgDark    = Color(0xFF050A14)
    val cardBg    = Color(0xFF0D1828)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = if (rootOk) neonGreen else neonRed,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "ROOT CONTROL",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        if (rootOk) "✓ Root activo — Snapdragon 8 Gen 1"
                        else "✗ Sin root — funciones limitadas",
                        color = if (rootOk) neonGreen else Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            // ── Temperatura actual ────────────────────────────────────────
            snap?.let { s ->
                val mainTemp = when {
                    s.cpuTemp > 20f -> s.cpuTemp
                    s.modemTemp > 20f -> s.modemTemp
                    else -> s.batteryTemp
                }
                val tempColor = when {
                    mainTemp >= 50f -> neonRed
                    mainTemp >= 42f -> Color(0xFFFF8C00)
                    else -> neonGreen
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, tempColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TempChip("CPU",   s.cpuTemp,   neonBlue)
                        TempChip("GPU",   s.gpuTemp,   neonPurple)
                        TempChip("Modem", s.modemTemp, neonRed)
                        TempChip("Bat",   s.batteryTemp, neonGreen)
                    }
                }
            }

            if (!rootOk) {
                // Sin root — mostrar info
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0A0A)),
                    border = BorderStroke(1.dp, neonRed.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⚠ Root no detectado", color = neonRed, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Para usar el control avanzado de CPU/GPU necesitas Magisk o KernelSU instalado. " +
                            "Las funciones de monitoreo siguen disponibles sin root.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                // ── SUPER COOL ────────────────────────────────────────────
                SuperCoolButton(
                    active   = superActive,
                    ultra    = ultraActive,
                    onSuper  = { viewModel.activateSuperCool(ultra = false) },
                    onUltra  = { viewModel.activateSuperCool(ultra = true) },
                    onStop   = { viewModel.deactivateSuperCool() },
                    neonBlue = neonBlue,
                    neonRed  = neonRed,
                    cardBg   = cardBg
                )

                // ── Último resultado ──────────────────────────────────────
                lastResult?.let { res ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, neonGreen.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("ÚLTIMO CICLO", color = neonGreen, fontSize = 11.sp, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            ResultRow("CPU throttle",     res.cpuThrottled,  neonBlue)
                            ResultRow("GPU throttle",     res.gpuThrottled,  neonPurple)
                            ResultRow("Brillo reducido",  res.brightnessSet, neonGreen)
                            ResultRow("Datos móviles off",res.dataDisabled,  neonRed)
                            ResultRow("Apps cerradas",    res.appsKilled > 0, neonGreen,
                                extra = "${res.appsKilled} procesos")
                        }
                    }
                }

                // ── Controles individuales ────────────────────────────────
                Text(
                    "CONTROLES INDIVIDUALES",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                IndividualControl(
                    icon  = Icons.Default.Memory,
                    title = "CPU — Frecuencia máx",
                    desc  = "Silver 1.2GHz / Gold 1.8GHz / Prime 1.8GHz",
                    color = neonBlue,
                    cardBg = cardBg,
                    onAction = { viewModel.rootCpuThrottle() }
                )
                IndividualControl(
                    icon  = Icons.Default.Gamepad,
                    title = "GPU — Frecuencia máx",
                    desc  = "490 MHz (normal: 818 MHz)",
                    color = neonPurple,
                    cardBg = cardBg,
                    onAction = { viewModel.rootGpuThrottle() }
                )
                IndividualControl(
                    icon  = Icons.Default.SignalCellularOff,
                    title = "Datos móviles",
                    desc  = "Cortar radio 5G/4G completamente",
                    color = neonRed,
                    cardBg = cardBg,
                    onAction = { viewModel.rootDisableData() }
                )
                IndividualControl(
                    icon  = Icons.Default.BrightnessLow,
                    title = "Brillo al 30%",
                    desc  = "Reducir consumo de pantalla",
                    color = Color(0xFFFFD700),
                    cardBg = cardBg,
                    onAction = { viewModel.rootSetBrightness(30) }
                )
                IndividualControl(
                    icon  = Icons.Default.CleaningServices,
                    title = "Matar apps fondo",
                    desc  = "Terminar procesos no visibles con kill -9",
                    color = neonGreen,
                    cardBg = cardBg,
                    onAction = { viewModel.rootKillBg() }
                )

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun TempChip(label: String, temp: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (temp > 0f) "${temp.toInt()}°" else "—",
            color = if (temp > 0f) color else Color.Gray,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(label, color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
private fun SuperCoolButton(
    active: Boolean, ultra: Boolean,
    onSuper: () -> Unit, onUltra: () -> Unit, onStop: () -> Unit,
    neonBlue: Color, neonRed: Color, cardBg: Color
) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "a"
    )
    val borderColor = when {
        ultra  -> neonRed
        active -> neonBlue
        else   -> Color.Gray.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(2.dp, if (active || ultra) borderColor.copy(alpha = pulse) else borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AcUnit,
                    contentDescription = null,
                    tint = if (ultra) neonRed else if (active) neonBlue else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "SUPER COOLING",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        when {
                            ultra  -> "🔴 ULTRA activo — CPU/GPU al mínimo, datos off"
                            active -> "🔵 NORMAL activo — throttle moderado"
                            else   -> "Intervención total de hardware vía root"
                        },
                        color = if (active || ultra) borderColor else Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!active && !ultra) {
                    Button(
                        onClick = onSuper,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = neonBlue)
                    ) { Text("ACTIVAR", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = onUltra,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = neonRed)
                    ) { Text("ULTRA", fontWeight = FontWeight.Bold) }
                } else {
                    Button(
                        onClick = onStop,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1A1A))
                    ) { Text("⬛ DESACTIVAR", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, ok: Boolean, color: Color, extra: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(
            if (extra.isNotEmpty()) extra else if (ok) "✓" else "✗",
            color = if (ok) color else Color.Gray,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun IndividualControl(
    icon: ImageVector, title: String, desc: String,
    color: Color, cardBg: Color, onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(desc,  color = Color.Gray,  fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onAction,
                border = BorderStroke(1.dp, color),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("APLICAR", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
