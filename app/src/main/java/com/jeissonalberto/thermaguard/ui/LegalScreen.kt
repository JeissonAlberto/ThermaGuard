package com.jeissonalberto.thermaguard.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * LegalScreen — Blindaje legal ThermaGuard v3.9.25
 *
 * 4 secciones según checklist:
 *   1. Claims de IA (FTC)
 *   2. Términos de Servicio + Arbitraje
 *   3. Política de Privacidad + Data Safety
 *   4. Política DMCA
 */
@Composable
fun LegalScreen() {
    val context = LocalContext.current
    val scroll  = rememberScrollState()

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    Box(modifier = Modifier.fillMaxSize().background(TG.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── HEADER ─────────────────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🛡️", fontSize = 36.sp)
                Text("Información Legal",
                    fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    color = TG.textPri, letterSpacing = (-0.6).sp)
                Text("ThermaGuard v3.9.25 · Jasol Group",
                    fontSize = 11.sp, color = TG.textSec)
            }

            HorizontalDivider(color = TG.textDim.copy(alpha = 0.15f))

            // ── 1. CLAIMS DE IA (FTC) ──────────────────────────────────────
            LegalSection(
                number = "1",
                title = "Sobre la IA en esta App (FTC)",
                icon = Icons.Default.Psychology,
                accent = Color(0xFF64B5F6)
            ) {
                LegalParagraph(
                    "ThermaGuard utiliza algoritmos propios basados en " +
                    "leyes físicas reales de termodinámica (Fourier, Newton, Stefan-Boltzmann, " +
                    "Arrhenius, etc.). Los resultados son estimaciones orientativas — " +
                    "no mediciones certificadas de laboratorio."
                )
                LegalParagraph(
                    "No utilizamos modelos de IA de terceros (GPT, Gemini, etc.) " +
                    "ni enviamos datos a servicios externos de inteligencia artificial."
                )
                LegalBullet("✅ Los análisis son calculados localmente en tu dispositivo.")
                LegalBullet("✅ No hacemos promesas de precisión absoluta del 100%.")
                LegalBullet("✅ El análisis térmico no sustituye diagnóstico técnico profesional.")
            }

            // ── 2. TÉRMINOS DE SERVICIO ────────────────────────────────────
            LegalSection(
                number = "2",
                title = "Términos de Servicio",
                icon = Icons.Default.Gavel,
                accent = Color(0xFFFFB74D)
            ) {
                LegalSubtitle("Uso permitido")
                LegalParagraph("Puedes usar ThermaGuard para monitorear y optimizar el estado " +
                    "térmico de tu dispositivo personal. Queda prohibida la ingeniería inversa, " +
                    "redistribución del APK o uso para dañar dispositivos de terceros.")

                LegalSubtitle("Funciones de Root")
                LegalParagraph("Las funciones de gobernanza de CPU/GPU requieren acceso root. " +
                    "Su uso es bajo tu exclusiva responsabilidad. Jasol Group no se hace " +
                    "responsable de daños al hardware, pérdida de garantía del fabricante " +
                    "ni comportamientos inesperados del kernel.")

                LegalSubtitle("Arbitraje · Renuncia a Demandas Colectivas")
                LegalParagraph("Cualquier disputa se resolverá mediante arbitraje individual " +
                    "vinculante. Tú y Jasol Group renuncian al derecho de participar en una " +
                    "demanda colectiva (class action) o acción representativa.")

                LegalSubtitle("Limitación de responsabilidad")
                LegalParagraph("ThermaGuard se proporciona como es (as is), sin garantías " +
                    "expresas ni implícitas. Jasol Group no será responsable por daños directos " +
                    "o indirectos derivados del uso de la aplicación.")

                OutlinedButton(
                    onClick = { openUrl("https://github.com/JeissonAlberto/ThermaGuard/blob/main/docs/TERMS.md") },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.OpenInNew, null,
                        tint = Color(0xFFFFB74D), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ver Términos completos", fontSize = 12.sp, color = Color(0xFFFFB74D))
                }
            }

            // ── 3. PRIVACIDAD + DATA SAFETY ────────────────────────────────
            LegalSection(
                number = "3",
                title = "Privacidad y Datos",
                icon = Icons.Default.Shield,
                accent = Color(0xFF81C784)
            ) {
                LegalSubtitle("¿Qué datos recopilamos?")
                LegalBullet("🌡️ Temperatura CPU / GPU / Batería (análisis local)")
                LegalBullet("⚡ Frecuencias y uso de CPU por núcleo")
                LegalBullet("🔋 Nivel, voltaje y salud de batería")
                LegalBullet("📱 Modelo del dispositivo (Build.MODEL — calibración)")
                LegalBullet("👤 Nombre de perfil (introducido por ti — almacenado local)")
                LegalBullet("📡 Telemetría técnica anónima (snapshots térmicos → GitHub)")

                LegalSubtitle("Lo que NO recopilamos")
                LegalBullet("❌ Ubicación GPS · Contactos · Fotos · Archivos")
                LegalBullet("❌ Correo electrónico ni datos personales identificables")
                LegalBullet("❌ Historial de navegación · Apps instaladas · GAID")

                LegalSubtitle("Google Play Data Safety")
                LegalParagraph("Declaramos todos los datos recopilados en la sección " +
                    "Seguridad de los datos de Google Play, conforme a los requisitos " +
                    "de Google para todas las apps publicadas.")

                OutlinedButton(
                    onClick = { openUrl("https://github.com/JeissonAlberto/ThermaGuard/blob/main/docs/PRIVACY.md") },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    border = BorderStroke(1.dp, Color(0xFF81C784).copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.OpenInNew, null,
                        tint = Color(0xFF81C784), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ver Política de Privacidad completa", fontSize = 12.sp,
                        color = Color(0xFF81C784))
                }
            }

            // ── 4. DMCA ────────────────────────────────────────────────────
            LegalSection(
                number = "4",
                title = "Derechos de Autor (DMCA)",
                icon = Icons.Default.Copyright,
                accent = Color(0xFFCE93D8)
            ) {
                LegalParagraph("ThermaGuard no permite la carga de contenido de terceros. " +
                    "Todo el código, interfaz y recursos gráficos son propiedad de Jasol Group " +
                    "o se usan bajo licencias compatibles.")
                LegalParagraph("Si consideras que algún elemento infringe tus derechos de autor, " +
                    "contacta a nuestro agente designado con la información requerida por la " +
                    "Sección 512 de la DMCA.")
                LegalSubtitle("Agente DMCA designado")
                LegalBullet("👤 Jasol Group / Jeisson A. Sarmiento")
                LegalBullet("✉️ jeissonsarmiento@avidtel.com.co")
            }

            // ── FOOTER ─────────────────────────────────────────────────────
            HorizontalDivider(color = TG.textDim.copy(alpha = 0.15f))
            Column(modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Jasol Group · Colombia · Junio 2026",
                    fontSize = 10.sp, color = TG.textDim, textAlign = TextAlign.Center)
                Text("Ley Aplicable: República de Colombia",
                    fontSize = 10.sp, color = TG.textDim, textAlign = TextAlign.Center)
                Text("Términos v1.0 · Privacidad v2.0",
                    fontSize = 10.sp, color = TG.textDim, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ── Componentes privados ───────────────────────────────────────────────────
@Composable
private fun LegalSection(
    number: String,
    title: String,
    icon: ImageVector,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(TG.glass)
        .border(1.dp, accent.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
        .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(26.dp)
                .background(accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center) {
                Text(number, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = accent)
            }
            Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TG.textPri)
        }
        HorizontalDivider(color = accent.copy(alpha = 0.1f))
        content()
    }
}

@Composable
private fun LegalSubtitle(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        color = TG.textPri, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun LegalParagraph(text: String) {
    Text(text, fontSize = 11.sp, color = TG.textSec, lineHeight = 16.sp)
}

@Composable
private fun LegalBullet(text: String) {
    Text(text, fontSize = 11.sp, color = TG.textSec, lineHeight = 15.sp,
        modifier = Modifier.padding(start = 4.dp))
}
