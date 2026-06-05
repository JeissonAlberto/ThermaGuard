package com.jeissonalberto.thermaguard.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.R

@Composable
fun PermissionsScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current

    var hasNotifications by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            else true
        )
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasNotifications = it }

    val allGranted = hasNotifications
    LaunchedEffect(allGranted) { if (allGranted) onAllGranted() }

    // Animación del orb de fondo
    val inf = rememberInfiniteTransition(label = "splash")
    val orbScale by inf.animateFloat(1f, 1.15f,
        infiniteRepeatable(tween(3000, easing = EaseInOut), RepeatMode.Reverse), label = "orb")
    val logoAlpha by inf.animateFloat(0.8f, 1f,
        infiniteRepeatable(tween(2000, easing = EaseInOut), RepeatMode.Reverse), label = "la")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TG.bg),
        contentAlignment = Alignment.Center
    ) {
        // Orb decorativo
        Box(
            modifier = Modifier
                .size(400.dp)
                .scale(orbScale)
                .blur(100.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF00E5FF).copy(alpha = 0.12f), Color.Transparent)),
                    CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(Modifier.height(60.dp))

            // Logo Jasol
            Surface(
                modifier = Modifier.size(90.dp).alpha(logoAlpha),
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.06f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    try {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.jasol_logo),
                            contentDescription = "Jasol Group",
                            modifier = Modifier.size(70.dp)
                        )
                    } catch (e: Exception) {
                        Text("🌡️", fontSize = 36.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "ThermaGuard",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TG.textPri,
                letterSpacing = (-1).sp
            )
            Text(
                "Motor térmico inteligente",
                fontSize = 14.sp,
                color = TG.textSec,
                letterSpacing = 0.3.sp
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "by Jasol Group",
                fontSize = 11.sp,
                color = TG.textDim,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(40.dp))

            // Cards de permisos
            if (!hasNotifications) {
                PermCard(
                    icon        = Icons.Default.Notifications,
                    title       = "Notificaciones",
                    description = "Para alertarte cuando el dispositivo se calienta y mostrarte el estado en tiempo real.",
                    isGranted   = false,
                    isRequired  = true,
                    onGrant     = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onSettings  = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        })
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            // Info card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.04f),
                modifier = Modifier.fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    FeatureRow(Icons.Default.Memory,          "Monitor de CPU y temperatura", "Lectura directa de sensores del sistema")
                    FeatureRow(Icons.Default.AutoFixHigh,     "Motor de optimización autónomo", "Actúa automáticamente cuando detecta calor")
                    FeatureRow(Icons.Default.Psychology,      "Aprendizaje adaptativo",        "Aprende tu patrón de uso con el tiempo")
                    FeatureRow(Icons.Default.Science,         "Diagnóstico de componentes",    "Identifica qué parte genera más calor")
                    FeatureRow(Icons.Default.BarChart,        "Estadísticas avanzadas",        "Risk score, heatmap horario, historial")
                }
            }

            Spacer(Modifier.height(28.dp))

            // Botón continuar (si ya tiene permisos)
            if (hasNotifications) {
                Button(
                    onClick = onAllGranted,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Icon(Icons.Default.RocketLaunch, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Iniciar motor", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TG.bg)
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "No requiere root · Sin acceso a datos personales",
                fontSize = 10.sp,
                color = TG.textDim,
                textAlign = TextAlign.Center,
                letterSpacing = 0.3.sp
            )
        }
    }
}

@Composable
fun PermCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isRequired: Boolean,
    onGrant: () -> Unit,
    onSettings: () -> Unit
) {
    val accent = if (isGranted) TG.green else if (isRequired) Color(0xFFFF6D00) else TG.textSec
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = 0.07f),
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (isGranted) Icons.Default.Check else icon, null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
                    if (isRequired && !isGranted) {
                        Surface(shape = RoundedCornerShape(4.dp), color = accent.copy(alpha = 0.2f)) {
                            Text("Requerido", modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                fontSize = 9.sp, color = accent)
                        }
                    }
                }
                Text(description, fontSize = 11.sp, color = TG.textSec, lineHeight = 15.sp)
            }
            if (!isGranted) {
                Button(
                    onClick = onGrant,
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("Permitir", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TG.bg)
                }
            }
        }
    }
}

@Composable
fun FeatureRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(TG.green.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = TG.green, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TG.textPri)
            Text(subtitle, fontSize = 10.sp, color = TG.textSec)
        }
    }
}
