package com.jeissonalberto.thermaguard.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.jeissonalberto.thermaguard.domain.ThermalViewModel

@Composable
fun DashboardScreen(viewModel: ThermalViewModel) {
    // UI ULTRA-CYBER NEGRA/NEÓN (Inconfundible)
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF000000))) {
        Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
            // Versión Gigante para que no haya duda
            Text("THERMAGUARD v4.3.30", color = Color(0xFF00F2FF), fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text("ULTIMATE EVOLUTION", color = Color.White, fontWeight = FontWeight.Thin, fontSize = 24.sp)
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Monitor Principal estilo Reactor
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(200.dp))
                    .border(2.dp, Color(0xFF00F2FF), RoundedCornerShape(200.dp))
                    .background(Brush.radialGradient(listOf(Color(0xFF00F2FF).copy(alpha = 0.1f), Color.Transparent))),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("36.5°C", fontSize = 64.sp, color = Color.White, fontWeight = FontWeight.ExtraLight)
                    Text("SILICON ENGINE: ACTIVE", color = Color(0xFF00F2FF), fontSize = 10.sp)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Indicadores de Módulos
            ModuleStatus("FLEETBASE LOGISTICS", "ONLINE", Color(0xFF00FFCC))
            Spacer(modifier = Modifier.height(10.dp))
            ModuleStatus("PROJECT ARCHITECT", "V4.3.30 READY", Color(0xFFBB86FC))
        }
    }
}

@Composable
fun ModuleStatus(name: String, status: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, color = Color.Gray, fontSize = 11.sp)
        Text(status, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
