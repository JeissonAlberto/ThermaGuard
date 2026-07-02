package com.jeissonalberto.thermaguard.ui

import androidx.compose.animation.core.*
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
    val temp by viewModel.batteryTemp.collectAsState()
    val optLevel by viewModel.optimizationLevel.collectAsState()
    val status by viewModel.engineStatus.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF010204))) {
        Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
            Text("THERMAGUARD EVOLUTION v4.4.0", color = Color(0xFF00F2FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("AUTO-LEARNING ACTIVE", color = Color.White.copy(alpha = 0.5f), fontSize = 18.sp, fontWeight = FontWeight.Thin)
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Monitor Central
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${"%.1f".format(temp)}°C", fontSize = 80.sp, color = Color.White, fontWeight = FontWeight.ExtraLight)
                    Text(status, color = Color(0xFF00F2FF), fontSize = 12.sp, letterSpacing = 3.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Barra de Optimización Inteligente
            Text("LEARNING ENGINE OPTIMIZATION", color = Color.Gray, fontSize = 9.sp)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = optLevel,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Color(0xFF00F2FF),
                trackColor = Color.White.copy(alpha = 0.1f)
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Footer con IA Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("NEURAL BIAS", color = Color.Gray, fontSize = 11.sp)
                    Text("AUTO-ADJUSTING", color = Color(0xFFBB86FC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
