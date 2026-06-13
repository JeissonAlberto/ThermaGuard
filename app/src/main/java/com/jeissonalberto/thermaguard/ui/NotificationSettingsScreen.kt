package com.jeissonalberto.thermaguard.ui

import androidx.compose.animation.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
//  NotificationSettingsScreen — Configurar alertas push de ThermaGuard
// ─────────────────────────────────────────────────────────────────────────────

data class NotifConfig(
    val enableCritical: Boolean  = true,
    val enableHot: Boolean       = true,
    val enableWarm: Boolean      = false,
    val enableCooling: Boolean   = true,
    val enableVibration: Boolean = true,
    val criticalThresh: Float    = 50f,
    val hotThresh: Float         = 42f,
    val warmThresh: Float        = 36f,
    val cooldownMinutes: Int     = 5,
)

@Composable
fun NotificationSettingsScreen() {
    var cfg by remember { mutableStateOf(NotifConfig()) }
    var testFired by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TG.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Text("Notificaciones push", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TG.textPri)
        Text("Configura qué alertas recibirás y cuándo.",
            fontSize = 13.sp, color = TG.textSec, modifier = Modifier.padding(bottom = 4.dp))

        // ── Tipos de alerta ─────────────────────────────────────────────────
        NotifSectionCard(title = "Tipos de alerta") {
            NotifToggleRow(
                icon      = Icons.Default.LocalFireDepartment,
                iconColor = TG.red,
                title     = "🔴 Emergencia / Crítica",
                subtitle  = "Temperatura ≥ ${cfg.criticalThresh.toInt()}°C · Vibración máxima",
                checked   = cfg.enableCritical,
                onCheck   = { cfg = cfg.copy(enableCritical = it) }
            )
            NotifToggleRow(
                icon      = Icons.Default.Warning,
                iconColor = TG.amber,
                title     = "🟠 Temperatura alta",
                subtitle  = "Temperatura ≥ ${cfg.hotThresh.toInt()}°C · Aviso con vibración",
                checked   = cfg.enableHot,
                onCheck   = { cfg = cfg.copy(enableHot = it) }
            )
            NotifToggleRow(
                icon      = Icons.Default.ThermostatAuto,
                iconColor = Color(0xFFFFD740),
                title     = "🟡 Dispositivo tibio",
                subtitle  = "Temperatura ≥ ${cfg.warmThresh.toInt()}°C · Aviso silencioso",
                checked   = cfg.enableWarm,
                onCheck   = { cfg = cfg.copy(enableWarm = it) }
            )
            NotifToggleRow(
                icon      = Icons.Default.AcUnit,
                iconColor = TG.cyan,
                title     = "✅ Enfriamiento logrado",
                subtitle  = "Cuando baja de una alerta previa",
                checked   = cfg.enableCooling,
                onCheck   = { cfg = cfg.copy(enableCooling = it) }
            )
        }

        // ── Umbrales ─────────────────────────────────────────────────────────
        NotifSectionCard(title = "Umbrales de temperatura") {
            ThresholdSlider(
                label    = "Alerta WARM",
                value    = cfg.warmThresh,
                range    = 30f..45f,
                color    = Color(0xFFFFD740),
                onValue  = { cfg = cfg.copy(warmThresh = it) }
            )
            ThresholdSlider(
                label    = "Alerta HOT",
                value    = cfg.hotThresh,
                range    = 38f..55f,
                color    = TG.amber,
                onValue  = { cfg = cfg.copy(hotThresh = it) }
            )
            ThresholdSlider(
                label    = "Alerta CRÍTICA",
                value    = cfg.criticalThresh,
                range    = 45f..65f,
                color    = TG.red,
                onValue  = { cfg = cfg.copy(criticalThresh = it) }
            )
        }

        // ── Cooldown ──────────────────────────────────────────────────────────
        NotifSectionCard(title = "Anti-spam") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment   = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Intervalo mínimo entre alertas", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TG.textPri)
                        Text("Para no repetir la misma alerta", fontSize = 11.sp, color = TG.textSec)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(TG.glass)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("${cfg.cooldownMinutes} min", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TG.cyan)
                    }
                }
                Slider(
                    value         = cfg.cooldownMinutes.toFloat(),
                    onValueChange = { cfg = cfg.copy(cooldownMinutes = it.toInt()) },
                    valueRange    = 1f..30f,
                    steps         = 28,
                    colors        = SliderDefaults.colors(
                        thumbColor       = TG.cyan,
                        activeTrackColor = TG.cyan
                    )
                )
            }
        }

        // ── Vibración ─────────────────────────────────────────────────────────
        NotifSectionCard(title = "Vibración") {
            NotifToggleRow(
                icon      = Icons.Default.Vibration,
                iconColor = TG.purple,
                title     = "Vibración en alertas",
                subtitle  = "Patrón adaptativo según severidad",
                checked   = cfg.enableVibration,
                onCheck   = { cfg = cfg.copy(enableVibration = it) }
            )
        }

        // ── Test y guardar ────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Botón test
            OutlinedButton(
                onClick  = { testFired = true },
                modifier = Modifier.weight(1f).height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                border   = BorderStroke(1.dp, TG.cyan.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.Notifications, null, tint = TG.cyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Probar", color = TG.cyan, fontSize = 13.sp)
            }
            // Botón guardar
            Button(
                onClick  = { /* TODO: persistir cfg en DataStore/SharedPrefs */ },
                modifier = Modifier.weight(1f).height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = TG.cyan)
            ) {
                Icon(Icons.Default.Save, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Guardar", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Toast de test disparado
        AnimatedVisibility(visible = testFired, enter = fadeIn(), exit = fadeOut()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TG.green.copy(alpha = 0.12f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✅", fontSize = 18.sp)
                Column {
                    Text("Notificación de prueba enviada", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TG.green)
                    Text("Revisa el panel de notificaciones de tu S22", fontSize = 11.sp, color = TG.textSec)
                }
            }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(3000)
                testFired = false
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

// ── Componentes ───────────────────────────────────────────────────────────────

@Composable
private fun NotifSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TG.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = TG.textDim, letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun NotifToggleRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheck: (Boolean) -> Unit
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp)) }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TG.textPri)
            Text(subtitle, fontSize = 11.sp, color = TG.textSec)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onCheck,
            colors          = SwitchDefaults.colors(
                checkedThumbColor  = Color.Black,
                checkedTrackColor  = TG.cyan
            )
        )
    }
}

@Composable
private fun ThresholdSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    onValue: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, color = TG.textSec)
            Text("${value.toInt()}°C", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Slider(
            value         = value,
            onValueChange = onValue,
            valueRange    = range,
            colors        = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
        )
    }
}
