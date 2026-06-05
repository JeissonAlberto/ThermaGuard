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
    var step by remember { mutableStateOf(0) } // 0=bienvenida, 1=permisos

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
    LaunchedEffect(allGranted) { if (allGranted && step == 1) onAllGranted() }

    val inf = rememberInfiniteTransition(label = "perm")
    val orbScale by inf.animateFloat(0.95f, 1.05f,
        infiniteRepeatable(tween(2000, easing = EaseInOut), RepeatMode.Reverse), label = "os")
    val orbAlpha by inf.animateFloat(0.15f, 0.30f,
        infiniteRepeatable(tween(1800, easing = EaseInOut), RepeatMode.Reverse), label = "oa")

    Box(
        modifier = Modifier.fillMaxSize().background(TG.bg),
        contentAlignment = Alignment.Center
    ) {
        // Orb
        Box(
            modifier = Modifier
                .size(420.dp)
                .scale(orbScale)
                .blur(80.dp)
                .background(Color(0xFF00E5FF).copy(alpha = orbAlpha), CircleShape)
                .align(Alignment.TopCenter)
                .offset(y = (-60).dp)
        )

        if (step == 0) {
            // ── PANTALLA BIENVENIDA ───────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.jasol_logo),
                        contentDescription = "Jasol Group",
                        modifier = Modifier.size(85.dp).clip(RoundedCornerShape(18.dp))
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text("ThermaGuard", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                    color = TG.textPri, letterSpacing = (-1).sp)
                Text("Motor térmico inteligente", fontSize = 14.sp, color = TG.textSec,
                    modifier = Modifier.padding(top = 4.dp))

                Spacer(Modifier.height(40.dp))

                // Features
                val features = listOf(
                    Triple(Icons.Default.Thermostat,  "Monitoreo continuo", "Temperatura, CPU y componentes en tiempo real"),
                    Triple(Icons.Default.Psychology,   "Motor adaptativo",    "Aprende tus patrones de uso y actúa solo"),
                    Triple(Icons.Default.AutoFixHigh,  "Optimización auto",   "Enfría tu dispositivo sin que hagas nada"),
                    Triple(Icons.Default.Notifications,"Alertas inteligentes","Te avisa solo cuando realmente importa"),
                )
                features.forEach { (icon, title, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp).clip(CircleShape)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
                            Text(desc, fontSize = 11.sp, color = TG.textSec, lineHeight = 15.sp)
                        }
                    }
                }

                Spacer(Modifier.height(40.dp))

                Button(
                    onClick = {
                        if (allGranted) onAllGranted() else step = 1
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("Comenzar", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFF080C14))
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, null,
                        tint = Color(0xFF080C14), modifier = Modifier.size(18.dp))
                }

                Spacer(Modifier.height(12.dp))
                Text("by Jasol Group", fontSize = 11.sp, color = TG.textDim, letterSpacing = 1.sp)
            }
        } else {
            // ── PANTALLA PERMISOS ─────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape)
                        .background(Color(0xFF00E5FF).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Security, null, tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(34.dp))
                }

                Spacer(Modifier.height(20.dp))

                Text("Un permiso necesario", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = TG.textPri, letterSpacing = (-0.5).sp)
                Text("Para enviarte alertas cuando detecte temperatura crítica",
                    fontSize = 13.sp, color = TG.textSec, textAlign = TextAlign.Center,
                    lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp, bottom = 32.dp))

                PermissionItem(
                    icon        = Icons.Default.Notifications,
                    title       = "Notificaciones",
                    description = "Alertas de temperatura crítica y acciones del motor",
                    granted     = hasNotifications,
                    color       = Color(0xFF00E5FF),
                    onGrant     = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else hasNotifications = true
                    }
                )

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = { if (allGranted) onAllGranted() },
                    enabled = allGranted,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        disabledContainerColor = Color(0xFF00E5FF).copy(alpha = 0.3f)
                    )
                ) {
                    Text("Continuar", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFF080C14))
                }

                TextButton(onClick = { onAllGranted() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Omitir por ahora", fontSize = 12.sp, color = TG.textDim)
                }
            }
        }
    }
}

@Composable
fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    color: Color,
    onGrant: () -> Unit
) {
    val borderColor = if (granted) color.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f)
    Surface(
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = if (granted) color.copy(alpha = 0.07f) else TG.glass
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TG.textPri)
                Text(description, fontSize = 11.sp, color = TG.textSec, lineHeight = 15.sp)
            }
            if (granted) {
                Icon(Icons.Default.CheckCircle, null, tint = color, modifier = Modifier.size(22.dp))
            } else {
                TextButton(onClick = onGrant, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("Activar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
                }
            }
        }
    }
}
