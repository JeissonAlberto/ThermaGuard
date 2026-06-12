package com.jeissonalberto.thermaguard.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class OnboardingPage(
    val emoji: String,
    val title: String,
    val body: String,
    val accent: Color
)

private val PAGES = listOf(
    OnboardingPage("🌡️", "Tu teléfono, bajo control",
        "ThermaGuard monitorea la temperatura de tu S22 en tiempo real y actúa antes de que el calor cause daños.", Color(0xFF00D4FF)),
    OnboardingPage("🧠", "Motor de IA Silicio v6",
        "Aplica las leyes de Moore, Dennard y Pollack para analizar cuándo tu chip está trabajando de más y por qué.", Color(0xFF7C3AED)),
    OnboardingPage("🦁", "Modo Bestia",
        "Cuando la temperatura sube, ThermaGuard actúa: reduce brillo, cierra apps, desactiva redes y libera RAM automáticamente.", Color(0xFFFF6D00)),
    OnboardingPage("📊", "Historial y exportación",
        "Registra cada lectura y exporta el historial en CSV o JSON para analizar patrones térmicos.", Color(0xFF10B981)),
    OnboardingPage("🔐", "100% local y privado",
        "Todo funciona en tu dispositivo. Sin cuenta, sin nube, sin telemetría. Solo tú y tus datos.", Color(0xFFF59E0B)),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager  = rememberPagerState { PAGES.size }
    val scope  = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TG.bg)
    ) {
        // Fondo animado con color del slide actual
        val accentColor = PAGES[pager.currentPage].accent
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors  = listOf(accentColor.copy(alpha = 0.08f), Color.Transparent),
                        radius  = 600f
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Skip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(onClick = onFinish) {
                    Text("Omitir", color = TG.textSec, fontSize = 13.sp)
                }
            }

            // Pager
            HorizontalPager(
                state    = pager,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                OnboardingPageContent(PAGES[page])
            }

            // Indicators + Botón
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(PAGES.size) { i ->
                        val isActive = i == pager.currentPage
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isActive) 24.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) PAGES[pager.currentPage].accent else TG.textDim
                                )
                                .animateContentSize(tween(300))
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (pager.currentPage < PAGES.size - 1) {
                            scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        } else {
                            onFinish()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape  = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PAGES[pager.currentPage].accent
                    )
                ) {
                    if (pager.currentPage == PAGES.size - 1) {
                        Text("¡Empezar!", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                            color = if (PAGES[pager.currentPage].accent.luminance() > 0.5f) Color.Black else Color.White)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Siguiente", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                color = if (PAGES[pager.currentPage].accent.luminance() > 0.5f) Color.Black else Color.White)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.ArrowForward, null, tint =
                                if (PAGES[pager.currentPage].accent.luminance() > 0.5f) Color.Black else Color.White,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icono grande
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(page.accent.copy(alpha = 0.12f))
                .border(1.5.dp, page.accent.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(page.emoji, fontSize = 52.sp)
        }

        Spacer(Modifier.height(32.dp))

        Text(
            page.title,
            fontSize    = 26.sp,
            fontWeight  = FontWeight.ExtraBold,
            color       = TG.textPri,
            textAlign   = TextAlign.Center,
            lineHeight  = 32.sp
        )

        Spacer(Modifier.height(16.dp))

        Text(
            page.body,
            fontSize    = 15.sp,
            color       = TG.textSec,
            textAlign   = TextAlign.Center,
            lineHeight  = 22.sp
        )
    }
}
