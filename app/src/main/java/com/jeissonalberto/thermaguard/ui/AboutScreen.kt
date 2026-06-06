package com.jeissonalberto.thermaguard.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.R

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val scroll  = rememberScrollState()

    val inf = rememberInfiniteTransition(label = "about")
    val orbAlpha by inf.animateFloat(0.08f, 0.18f,
        infiniteRepeatable(tween(2500, easing = EaseInOut), RepeatMode.Reverse), label = "oa")
    val orbScale by inf.animateFloat(0.95f, 1.05f,
        infiniteRepeatable(tween(3000, easing = EaseInOut), RepeatMode.Reverse), label = "os")

    Box(
        modifier = Modifier.fillMaxSize().background(TG.bg)
    ) {
        // Orb de fondo
        Box(
            modifier = Modifier
                .size(350.dp)
                .scale(orbScale)
                .blur(80.dp)
                .background(Color(0xFF00E5FF).copy(alpha = orbAlpha), CircleShape)
                .align(Alignment.TopCenter)
                .offset(y = 40.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Logo Jasol ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(28.dp))
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.2f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.jasol_logo),
                    contentDescription = "Jasol Group",
                    modifier = Modifier.size(84.dp).clip(RoundedCornerShape(20.dp))
                )
            }

            // ── Nombre app ───────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "ThermaGuard",
                    fontSize     = 26.sp,
                    fontWeight   = FontWeight.ExtraBold,
                    color        = TG.textPri,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    "v3.4.0",
                    fontSize = 12.sp,
                    color    = Color(0xFF00E5FF).copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // ── Divider ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )

            // ── Tarjeta "Pertenece a" ────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(20.dp),
                color    = Color(0xFF00E5FF).copy(alpha = 0.07f),
                border   = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Business, null,
                            tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                        Text(
                            "Propiedad de",
                            fontSize  = 11.sp,
                            color     = TG.textSec,
                            letterSpacing = 1.5.sp
                        )
                    }
                    Text(
                        "Jasol Group",
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color(0xFF00E5FF),
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        "Esta aplicación pertenece al ecosistema de productos\ndesarrollados por Jasol Group.",
                        fontSize  = 12.sp,
                        color     = TG.textSec,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }

            // ── Tarjeta Creador ──────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(20.dp),
                color    = Color.White.copy(alpha = 0.04f),
                border   = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f))
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Person, null,
                            tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                        Text("Creador", fontSize = 11.sp, color = TG.textSec, letterSpacing = 1.5.sp)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Ing. Jeisson Alberto Sarmiento Cabrera",
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color      = TG.textPri
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🇨🇴", fontSize = 14.sp)
                            Text("Colombia", fontSize = 12.sp, color = TG.textSec)
                        }
                    }

                    // Botón llamar
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:3223798725"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
                        border   = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.Phone, null,
                            modifier = Modifier.size(16.dp).padding(end = 6.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "3223798725",
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // ── Info técnica ─────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                color    = Color.White.copy(alpha = 0.03f),
                border   = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Información técnica", fontSize = 11.sp, color = TG.textSec, letterSpacing = 1.sp)
                    listOf(
                        "Versión"    to "3.4.0",
                        "Motor"      to "v5 — Ley de Moore (P=V²·F)",
                        "Plataforma" to "Android (Kotlin + Jetpack Compose)",
                        "Mínimo"     to "Android 5.0 (API 21)",
                    ).forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, fontSize = 13.sp, color = TG.textSec)
                            Text(value,  fontSize = 13.sp, color = TG.textPri, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // ── Footer ───────────────────────────────────────────────────
            Text(
                "© 2026 Jasol Group · Todos los derechos reservados",
                fontSize  = 10.sp,
                color     = TG.textDim,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}
