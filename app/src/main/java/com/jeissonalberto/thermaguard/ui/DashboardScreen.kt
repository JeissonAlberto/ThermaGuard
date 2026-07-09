package com.jeissonalberto.thermaguard.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.domain.ThermalViewModel

@Composable
fun DashboardScreen(viewModel: ThermalViewModel) {
    val temp by viewModel.batteryTemp.collectAsState()
    val status by viewModel.engineStatus.collectAsState()
    val opt by viewModel.optimizationLevel.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF010204))) {
        Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
            Text("THERMAGUARD v4.5.1", color = Color(0xFF00F2FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("ULTIMATE EDITION", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
            
            Spacer(modifier = Modifier.height(60.dp))
            
            // Núcleo Reactor
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${"%.1f".format(temp)}°C", fontSize = 72.sp, color = Color.White, fontWeight = FontWeight.ExtraLight)
                Text("ENGINE: $status", color = Color(0xFF00F2FF), fontSize = 12.sp, letterSpacing = 2.sp)
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Stats Panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("OPTIMIZATION", color = Color.Gray, fontSize = 12.sp)
                    Text("${(opt * 100).toInt()}%", color = Color(0xFFBB86FC), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
