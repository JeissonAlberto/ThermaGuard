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
    val engineStatus by viewModel.engineStatus.collectAsState()
    val fleetStatus by viewModel.fleetbaseStatus.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF020408))) {
        Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF00F2FF)))
                Spacer(modifier = Modifier.width(12.dp))
                Text("THERMAGUARD v4.3.32", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            
            Text("SYSTEM CORE MONITOR", color = Color.Gray, fontSize = 10.sp, letterSpacing = 2.sp)
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Monitor Circular de Reactor
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(1000.dp))
                    .border(1.dp, Color(0xFF00F2FF).copy(alpha = 0.3f), RoundedCornerShape(1000.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF00F2FF).copy(alpha = glowAlpha), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${"%.1f".format(temp)}°C", fontSize = 72.sp, color = Color.White, fontWeight = FontWeight.ExtraLight)
                    Text("SILICON ENGINE: $engineStatus", color = Color(0xFF00F2FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Stats Panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StatusRow("FLEETBASE LOGISTICS", fleetStatus, Color(0xFF00FFCC))
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))
                    StatusRow("PROJECT ARCHITECT", "STABLE", Color(0xFFBB86FC))
                }
            }
        }
    }
}

@Composable
fun StatusRow(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.LightGray, fontSize = 12.sp)
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}
