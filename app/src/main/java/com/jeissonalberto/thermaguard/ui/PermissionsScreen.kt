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
import androidx.compose.ui.draw.scale
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

    var hasNotifications by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            else true
        )
    }

    var hasBatteryOptimization by remember {
        mutableStateOf(
            try {
                val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } catch (e: Exception) { true }
        )
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotifications = granted }

    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(PermissionItem(
                icon = Icons.Default.Notifications,
                title = "Notificaciones",
                description = "Alertas de temperatura en tiempo real y resumen en la barra de estado",
                isGranted = hasNotifications,
                onRequest = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    })
                }
            ))
        }
        add(PermissionItem(
            icon = Icons.Default.BatteryFull,
            title = "Ejecucion en segundo plano",
            description = "Permite el monitoreo continuo sin que el sistema mate el servicio",
            isGranted = hasBatteryOptimization,
            isOptional = true,
            onRequest = {
                try {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    })
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        ))
    }

    val allRequired = permissions.filter { !it.isOptional }.all { it.isGranted }

    LaunchedEffect(allRequired) {
        if (allRequired) onAllGranted()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1628), Color(0xFF1A1A2E), Color(0xFF0F2027))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(48.dp))

            // Icono animado — termometro ThermaGuard (sin logo externo)
            val pulse = rememberInfiniteTransition(label = "pulse")
            val scale by pulse.animateFloat(
                initialValue = 0.92f, targetValue = 1.08f,
                animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOut), RepeatMode.Reverse),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(Color(0xFFFF6D00), Color(0xFFD50000)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("🌡️", fontSize = 48.sp)
            }

            Spacer(Modifier.height(4.dp))

            Text(
                "ThermaGuard",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "Necesitamos algunos permisos\npara proteger tu dispositivo",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            // Barra de progreso de permisos
            val grantedCount = permissions.count { it.isGranted }
            val totalCount   = permissions.size
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Permisos concedidos", fontSize = 12.sp, color = Color.White.copy(0.6f))
                    Text("$grantedCount / $totalCount", fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (totalCount > 0) grantedCount.toFloat() / totalCount else 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = Color(0xFF00E676),
                    trackColor = Color.White.copy(0.1f)
                )
            }

            // Tarjetas de permisos
            permissions.forEach { perm -> PermissionCard(perm) }

            // Boton continuar
            AnimatedVisibility(visible = allRequired) {
                Button(
                    onClick = onAllGranted,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Continuar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (!allRequired) {
                Text(
                    "Los permisos obligatorios son necesarios para el correcto funcionamiento",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun PermissionCard(perm: PermissionItem) {
    val borderColor = when {
        perm.isGranted  -> Color(0xFF00C853).copy(alpha = 0.5f)
        perm.isOptional -> Color.White.copy(alpha = 0.1f)
        else            -> Color(0xFFFF6D00).copy(alpha = 0.5f)
    }
    val bgColor = when {
        perm.isGranted  -> Color(0xFF00C853).copy(alpha = 0.05f)
        else            -> Color.White.copy(alpha = 0.03f)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (perm.isGranted) Color(0xFF00C853).copy(0.15f)
                        else Color.White.copy(0.06f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (perm.isGranted) Icons.Default.CheckCircle else perm.icon,
                    contentDescription = null,
                    tint = if (perm.isGranted) Color(0xFF00C853) else Color.White.copy(0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        perm.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    if (perm.isOptional) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White.copy(alpha = 0.08f)
                        ) {
                            Text(
                                "Opcional",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                fontSize = 9.sp,
                                color = Color.White.copy(0.5f)
                            )
                        }
                    }
                }
                Text(
                    perm.description,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    lineHeight = 17.sp
                )
            }

            if (!perm.isGranted) {
                Button(
                    onClick = perm.onRequest,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (perm.isOptional) Color.White.copy(0.1f) else Color(0xFFFF6D00)
                    )
                ) {
                    Text(
                        "Permitir",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
