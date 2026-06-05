package com.jeissonalberto.thermaguard.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PermissionItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val isOptional: Boolean = false,
    val onRequest: () -> Unit,
    val onOpenSettings: (() -> Unit)? = null
)

@Composable
fun PermissionsScreen(
    onAllGranted: () -> Unit
) {
    val context = LocalContext.current

    // Estados de permisos
    var hasNotifications by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            else true
        )
    }
    var hasUsageStats by remember {
        mutableStateOf(
            try {
                val am = context.getSystemService(android.app.AppOpsManager::class.java)
                val mode = am.unsafeCheckOpNoThrow(
                    "android:get_usage_stats",
                    android.os.Process.myUid(), context.packageName
                )
                mode == android.app.AppOpsManager.MODE_ALLOWED
            } catch (e: Exception) { false }
        )
    }
    var hasBatteryOptimization by remember {
        mutableStateOf(
            try {
                val pm = context.getSystemService(android.os.PowerManager::class.java)
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } catch (e: Exception) { false }
        )
    }

    // Launchers
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotifications = granted }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasBatteryOptimization = try {
            val pm = context.getSystemService(android.os.PowerManager::class.java)
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) { false }
    }

    val usageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasUsageStats = try {
            val am = context.getSystemService(android.app.AppOpsManager::class.java)
            val mode = am.unsafeCheckOpNoThrow(
                "android:get_usage_stats",
                android.os.Process.myUid(), context.packageName
            )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }
    }

    val requiredGranted = hasNotifications && hasBatteryOptimization

    LaunchedEffect(requiredGranted) {
        if (requiredGranted) {
            kotlinx.coroutines.delay(800)
            onAllGranted()
        }
    }

    val permissions = listOf(
        PermissionItem(
            icon = Icons.Default.Notifications,
            title = "Notificaciones",
            description = "Para mostrarte la temperatura en la barra de notificaciones en tiempo real.",
            isGranted = hasNotifications,
            isOptional = false,
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        ),
        PermissionItem(
            icon = Icons.Default.BatterySaver,
            title = "Sin restriccion de bateria",
            description = "Permite que el monitor corra en segundo plano sin que Samsung lo mate.",
            isGranted = hasBatteryOptimization,
            isOptional = false,
            onRequest = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                batteryLauncher.launch(intent)
            }
        ),
        PermissionItem(
            icon = Icons.Default.Analytics,
            title = "Estadisticas de uso (opcional)",
            description = "Para detectar que app esta causando el calentamiento. Puedes saltarlo.",
            isGranted = hasUsageStats,
            isOptional = true,
            onRequest = {
                usageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF1a1a2e))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(32.dp))

            // Logo / Icono animado
            val pulse = rememberInfiniteTransition(label = "pulse")
            val scale by pulse.animateFloat(
                initialValue = 0.9f, targetValue = 1.1f,
                animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOut), RepeatMode.Reverse),
                label = "scale"
            )
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFFFF6D00), Color(0xFFD50000)))),
                contentAlignment = Alignment.Center
            ) {
                Text("🌡️", fontSize = 44.sp)
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "ThermaGuard", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White
            )
            Text(
                "Necesitamos algunos permisos\npara proteger tu dispositivo",
                fontSize = 15.sp, color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center, lineHeight = 22.sp
            )

            // Progreso
            val grantedCount = permissions.count { it.isGranted }
            val totalCount = permissions.size
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Permisos concedidos", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                    Text("$grantedCount/$totalCount", fontSize = 12.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = grantedCount.toFloat() / totalCount,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF00E676),
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }

            // Cards de permisos
            permissions.forEach { perm ->
                PermissionCard(item = perm)
            }

            // Boton continuar (si requeridos OK)
            AnimatedVisibility(visible = requiredGranted) {
                Button(
                    onClick = onAllGranted,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Todo listo — Entrar a ThermaGuard", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Saltar si solo falta el opcional
            if (!requiredGranted) {
                val requiredPending = permissions.filter { !it.isGranted && !it.isOptional }
                if (requiredPending.isEmpty()) {
                    TextButton(onClick = onAllGranted) {
                        Text("Saltar opcionales y continuar", color = Color.White.copy(alpha = 0.5f))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun PermissionCard(item: PermissionItem) {
    val borderColor = when {
        item.isGranted -> Color(0xFF00E676)
        item.isOptional -> Color(0xFFFFD600).copy(alpha = 0.5f)
        else -> Color(0xFFFF6D00).copy(alpha = 0.6f)
    }
    val bgColor = when {
        item.isGranted -> Color(0xFF00E676).copy(alpha = 0.05f)
        else -> Color.White.copy(alpha = 0.05f)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icono con estado
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (item.isGranted) Color(0xFF00E676).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.isGranted) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00E676), modifier = Modifier.size(26.dp))
                } else {
                    Icon(item.icon, null, tint = if (item.isOptional) Color(0xFFFFD600) else Color(0xFFFF6D00),
                        modifier = Modifier.size(26.dp))
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    if (item.isOptional) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFFD600).copy(alpha = 0.15f)) {
                            Text("Opcional", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp, color = Color(0xFFFFD600))
                        }
                    }
                }
                Text(item.description, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), lineHeight = 17.sp)

                if (!item.isGranted) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = item.onRequest,
                        modifier = Modifier.height(34.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (item.isOptional) Color(0xFFFFD600).copy(alpha = 0.2f)
                                            else Color(0xFFFF6D00).copy(alpha = 0.25f)
                        )
                    ) {
                        Text(
                            "Conceder permiso",
                            fontSize = 12.sp,
                            color = if (item.isOptional) Color(0xFFFFD600) else Color(0xFFFF8A65),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
