package com.jeissonalberto.thermaguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.domain.ThermalUiState
import com.jeissonalberto.thermaguard.service.FloatingWidgetService
import com.jeissonalberto.thermaguard.ui.theme.LocalTgColors

@Composable
fun SettingsScreen(
    uiState:            ThermalUiState,
    onSetTheme:         (AppTheme)    -> Unit = {},
    onSetLanguage:      (AppLanguage) -> Unit = {},
    onToggleWidget:     (Boolean)     -> Unit = {},
    telemetryEnabled:   Boolean               = true,
    onToggleTelemetry:  (Boolean)     -> Unit = {},
    onCheckUpdateNow:   ()            -> Unit = {},
    userName:           String                = "",
    deviceNickname:     String                = "Mi S22",
    usageProfile:       String                = "Gamer",
    onSetUserName:      (String) -> Unit      = {},
    onSetDeviceNickname:(String) -> Unit      = {},
    onSetUsageProfile:  (String) -> Unit      = {}
) {
    val tg      = LocalTgColors.current
    val accent  = tg.blue
    val scroll  = rememberScrollState()
    val context = LocalContext.current

    var widgetEnabled        by remember { mutableStateOf(false) }
    var needsOverlayPerm     by remember { mutableStateOf(false) }

    // Overlay permission dialog
    if (needsOverlayPerm) {
        AlertDialog(
            onDismissRequest = { needsOverlayPerm = false },
            containerColor   = Color(0xFF0A0F1E),
            title  = { Text("Permiso necesario", color = tg.textPri) },
            text   = {
                Text(
                    "Para mostrar el widget flotante, ThermaGuard necesita permiso de superposición de apps.",
                    color = tg.textSec, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    needsOverlayPerm = false
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }) { Text("Permitir", color = tg.blue) }
            },
            dismissButton = {
                TextButton(onClick = { needsOverlayPerm = false }) {
                    Text("Cancelar", color = tg.textDim)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(tg.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── HEADER ───────────────────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Settings, null, tint = accent, modifier = Modifier.size(22.dp))
                Text("Ajustes", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = tg.textPri)
            }

            // ── PERFIL DE USUARIO ─────────────────────────────────────────────
            SettingsSection(title = "Perfil de usuario", icon = Icons.Default.Person, accent = accent) {

                // Nombre
                var editingName by remember { mutableStateOf(false) }
                var nameInput   by remember(userName) { mutableStateOf(userName) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tu nombre", fontSize = 11.sp, color = tg.textSec, fontWeight = FontWeight.Medium)
                    if (editingName) {
                        OutlinedTextField(
                            value         = nameInput,
                            onValueChange = { nameInput = it },
                            modifier      = Modifier.fillMaxWidth(),
                            singleLine    = true,
                            placeholder   = { Text("Ej: Jeisson", color = tg.textDim, fontSize = 13.sp) },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction      = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                onSetUserName(nameInput.trim()); editingName = false
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = accent,
                                unfocusedBorderColor = tg.textDim.copy(alpha = 0.3f),
                                focusedTextColor     = tg.textPri,
                                unfocusedTextColor   = tg.textPri,
                                cursorColor          = accent
                            ),
                            trailingIcon = {
                                IconButton(onClick = { onSetUserName(nameInput.trim()); editingName = false }) {
                                    Icon(Icons.Default.Check, null, tint = accent)
                                }
                            }
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(tg.glass)
                                .border(1.dp, tg.textDim.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .clickable { editingName = true }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (userName.isEmpty()) "Toca para agregar tu nombre" else userName,
                                fontSize = 14.sp,
                                color    = if (userName.isEmpty()) tg.textDim else tg.textPri
                            )
                            Icon(Icons.Default.Edit, null,
                                tint = accent.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Nombre del dispositivo
                var editingDevice by remember { mutableStateOf(false) }
                var deviceInput   by remember(deviceNickname) { mutableStateOf(deviceNickname) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nombre del dispositivo", fontSize = 11.sp, color = tg.textSec, fontWeight = FontWeight.Medium)
                    if (editingDevice) {
                        OutlinedTextField(
                            value         = deviceInput,
                            onValueChange = { deviceInput = it },
                            modifier      = Modifier.fillMaxWidth(),
                            singleLine    = true,
                            placeholder   = { Text("Ej: Mi S22 Ultra", color = tg.textDim, fontSize = 13.sp) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                onSetDeviceNickname(deviceInput.trim()); editingDevice = false
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = accent,
                                unfocusedBorderColor = tg.textDim.copy(alpha = 0.3f),
                                focusedTextColor     = tg.textPri,
                                unfocusedTextColor   = tg.textPri,
                                cursorColor          = accent
                            ),
                            trailingIcon = {
                                IconButton(onClick = { onSetDeviceNickname(deviceInput.trim()); editingDevice = false }) {
                                    Icon(Icons.Default.Check, null, tint = accent)
                                }
                            }
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(tg.glass)
                                .border(1.dp, tg.textDim.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .clickable { editingDevice = true }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(deviceNickname, fontSize = 14.sp, color = tg.textPri)
                            Icon(Icons.Default.Edit, null,
                                tint = accent.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Perfil de uso
                val usageOptions = listOf("Gamer", "Trabajo", "Fotógrafo", "Casual", "Desarrollador")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Perfil de uso", fontSize = 11.sp, color = tg.textSec, fontWeight = FontWeight.Medium)
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(usageOptions) { option ->
                            val sel = option == usageProfile
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (sel) accent.copy(alpha = 0.20f) else tg.glass)
                                    .border(1.dp,
                                        if (sel) accent else tg.textDim.copy(alpha = 0.20f),
                                        RoundedCornerShape(20.dp))
                                    .clickable { onSetUsageProfile(option) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(option, fontSize = 12.sp,
                                    color      = if (sel) accent else tg.textSec,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            // ── APARIENCIA ───────────────────────────────────────────────────
            SettingsSection(title = "Apariencia", icon = Icons.Default.Palette, accent = accent) {
                val themes = listOf(
                    Triple(AppTheme.DARK,   "Modo oscuro",          Icons.Default.DarkMode),
                    Triple(AppTheme.LIGHT,  "Modo claro",           Icons.Default.LightMode),
                    Triple(AppTheme.SYSTEM, "Automático (sistema)", Icons.Default.Brightness4)
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    themes.forEach { (theme, label, icon) ->
                        val sel = uiState.appTheme == theme
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) accent.copy(alpha = 0.12f) else Color.Transparent)
                                .border(1.dp,
                                    if (sel) accent.copy(alpha = 0.4f) else tg.glass,
                                    RoundedCornerShape(12.dp))
                                .clickable { onSetTheme(theme) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(icon, null,
                                    tint = if (sel) accent else tg.textSec,
                                    modifier = Modifier.size(18.dp))
                                Text(label, fontSize = 13.sp,
                                    color = if (sel) tg.textPri else tg.textSec)
                            }
                            if (sel) Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                        }
                    }
                }
            }

            // ── IDIOMA ───────────────────────────────────────────────────────
            SettingsSection(title = "Idioma / Language", icon = Icons.Default.Language, accent = accent) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(AppLanguage.SPANISH, AppLanguage.ENGLISH).forEach { lang ->
                        val sel = uiState.appLanguage == lang
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) accent.copy(alpha = 0.15f) else tg.glass)
                                .border(1.dp,
                                    if (sel) accent.copy(alpha = 0.5f) else Color.Transparent,
                                    RoundedCornerShape(12.dp))
                                .clickable { onSetLanguage(lang) }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (lang == AppLanguage.SPANISH) "🇨🇴" else "🇺🇸",
                                    fontSize = 24.sp)
                                Text(lang.label, fontSize = 12.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (sel) accent else tg.textSec)
                            }
                        }
                    }
                }
            }

            // ── WIDGET FLOTANTE ──────────────────────────────────────────────
            SettingsSection(title = "Widget flotante", icon = Icons.Default.Widgets, accent = accent) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(tg.glass)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("Burbuja de temperatura", fontSize = 13.sp, color = tg.textPri)
                        Text("Muestra la temp. sobre cualquier app",
                            fontSize = 10.sp, color = tg.textDim)
                    }
                    Switch(
                        checked = widgetEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                                    !Settings.canDrawOverlays(context)) {
                                    needsOverlayPerm = true
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
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.08f))
                            .padding(12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, null, tint = accent, modifier = Modifier.size(14.dp))
                        Text("Requiere permiso de superposición de apps",
                            fontSize = 10.sp, color = tg.textSec)
                    }
                }
            }

            // ── DATOS Y ACTUALIZACIONES ──────────────────────────────────────
            SettingsSection(title = "Datos y Actualizaciones",
                icon = Icons.Default.CloudSync, accent = accent) {

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Telemetría de rendimiento",
                            fontSize = 13.sp, color = tg.textPri)
                        Text("Envía datos anónimos para mejorar ThermaGuard",
                            fontSize = 11.sp, color = tg.textSec)
                    }
                    Switch(
                        checked         = telemetryEnabled,
                        onCheckedChange = onToggleTelemetry,
                        colors          = SwitchDefaults.colors(checkedThumbColor = accent)
                    )
                }

                OutlinedButton(
                    onClick  = onCheckUpdateNow,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.Refresh, null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Verificar actualizaciones ahora", fontSize = 12.sp, color = accent)
                }
            }

            // ── ACERCA DE ────────────────────────────────────────────────────
            SettingsSection(title = "Acerca de", icon = Icons.Default.Info, accent = accent) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(tg.glass)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🛡️", fontSize = 32.sp)
                        Column {
                            Text("ThermaGuard", fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold, color = tg.textPri)
                            Text("v3.9.28 · Jasol Group", fontSize = 11.sp, color = tg.textSec)
                        }
                    }
                    HorizontalDivider(color = tg.glass, thickness = 1.dp)
                    Text("Ing. Jeisson Alberto Sarmiento Cabrera",
                        fontSize = 12.sp, color = tg.textSec)
                    Text("Coordinador NOC Líder · Avidtel S.A.S.",
                        fontSize = 11.sp, color = tg.textDim)
                }
            }

            Spacer(Modifier.height(24.dp))
        } // end Column
    } // end Box
}

// ════════════════════════════════════════════════════════════════════════════
//  SECCIÓN CON HEADER
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun SettingsSection(
    title:   String,
    icon:    ImageVector,
    accent:  Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(14.dp))
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent)
        }
        content()
    }
}
