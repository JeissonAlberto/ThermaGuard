package com.jeissonalberto.thermaguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.data.*
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import com.jeissonalberto.thermaguard.domain.ThermalUiState
import com.jeissonalberto.thermaguard.service.FloatingWidgetService

@Composable
fun SettingsScreen(
    uiState: ThermalUiState,
    onSetTheme: (AppTheme) -> Unit = {},
    onSetLanguage: (AppLanguage) -> Unit = {},
    onToggleWidget: (Boolean) -> Unit = {},
    telemetryEnabled: Boolean = true,
    onToggleTelemetry: (Boolean) -> Unit = {},
    autoUpdateEnabled: Boolean = true,
    onToggleAutoUpdate: (Boolean) -> Unit = {},
    onCheckUpdateNow: () -> Unit = {}
) {
    val accent = TG.blue
    val scroll = rememberScrollState()
    val context = LocalContext.current
    var widgetEnabled by remember { mutableStateOf(false) }
    var needsOverlayPermission by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(TG.bg)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Settings, null, tint = accent, modifier = Modifier.size(22.dp))
                Text("Ajustes", fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold, color = TG.textPri)
            }

            // ── APARIENCIA ──────────────────────────────────────────────────
            SettingsSection(title = "Apariencia", icon = Icons.Default.Palette, accent = accent) {
                val themes = listOf(
                    Triple(AppTheme.DARK,   "Modo oscuro",         Icons.Default.DarkMode),
                    Triple(AppTheme.LIGHT,  "Modo claro",          Icons.Default.LightMode),
                    Triple(AppTheme.SYSTEM, "Automático (sistema)", Icons.Default.Brightness4)
                )
                themes.forEach { (theme, label, icon) ->
                    val selected = uiState.appTheme == theme
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected) accent.copy(alpha = 0.4f) else TG.glass,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSetTheme(theme) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(icon, null,
                                tint = if (selected) accent else TG.textSec,
                                modifier = Modifier.size(18.dp))
                            Text(label, fontSize = 13.sp,
                                color = if (selected) TG.textPri else TG.textSec)
                        }
                        if (selected) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            // ── IDIOMA ───────────────────────────────────────────────────────
            SettingsSection(title = "Idioma / Language", icon = Icons.Default.Language, accent = accent) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(AppLanguage.SPANISH, AppLanguage.ENGLISH).forEach { lang ->
                        val selected = uiState.appLanguage == lang
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) accent.copy(alpha = 0.15f) else TG.glass)
                                .border(
                                    1.dp,
                                    if (selected) accent.copy(alpha = 0.5f) else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSetLanguage(lang) }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (lang == AppLanguage.SPANISH) "🇨🇴" else "🇺🇸",
                                    fontSize = 24.sp)
                                Text(lang.label, fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) accent else TG.textSec)
                            }
                        }
                    }
                }
            }

            // ── WIDGET FLOTANTE ───────────────────────────────────────────────
            SettingsSection(title = "Widget flotante", icon = Icons.Default.Widgets, accent = accent) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TG.glass)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Burbuja de temperatura", fontSize = 13.sp, color = TG.textPri)
                        Text("Muestra la temp. sobre cualquier app",
                            fontSize = 10.sp, color = TG.textDim)
                    }
                    Switch(
                        checked = widgetEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                // Verificar permiso SYSTEM_ALERT_WINDOW
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                                    !Settings.canDrawOverlays(context)) {
                                    needsOverlayPermission = true
                                } else {
                                    widgetEnabled = true
                                    FloatingWidgetService.start(context)
                                    onToggleWidget(true)
                                }
                            } else {
                                widgetEnabled = false
                                FloatingWidgetService.stop(context)
                                onToggleWidget(false)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent
                        )
                    )
                }
                if (widgetEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.08f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, null,
                            tint = accent, modifier = Modifier.size(14.dp))
                        Text("Requiere permiso de superposición de apps",
                            fontSize = 10.sp, color = TG.textSec)
                    }
                }
            }

            // ── ACERCA ────────────────────────────────────────────────────────
            SettingsSection(title = "Acerca de", icon = Icons.Default.Info, accent = accent) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TG.glass)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("🛡️", fontSize = 32.sp)
                        Column {
                            Text("ThermaGuard", fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold, color = TG.textPri)
                            Text("v3.9.0 · Jasol Group", fontSize = 11.sp, color = TG.textSec)
                        }
                    }
                    Divider(color = TG.glass, thickness = 1.dp)
                    Text("Ing. Jeisson Alberto Sarmiento Cabrera",
                        fontSize = 12.sp, color = TG.textSec)
                    Text("Coordinador NOC Líder · Avidtel S.A.S.",
                        fontSize = 11.sp, color = TG.textDim)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Diálogo: pedir permiso de superposición
    if (needsOverlayPermission) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { needsOverlayPermission = false },
            title = { Text("Permiso necesario", color = TG.textPri) },
            text = { Text("Para mostrar el widget flotante, ThermaGuard necesita permiso de superposición de apps.", color = TG.textSec, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    needsOverlayPermission = false
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                }) { Text("Permitir", color = TG.blue) }
            },
            dismissButton = {
                TextButton(onClick = { needsOverlayPermission = false }) {
                    Text("Cancelar", color = TG.textDim)
                }
            },
            containerColor = android.graphics.Color.argb(240, 10, 15, 30).let {
                androidx.compose.ui.graphics.Color(it)
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(14.dp))
            Text(title, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, color = accent)
        }
        content()
    }
}
