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
import com.jeissonalberto.thermaguard.data.SiliconPhysicsEngine

@Composable
fun DashboardScreen(viewModel: ThermalViewModel) {
    val temp by viewModel.temperature.collectAsState(initial = 32.0f)
    val usage by viewModel.cpuUsage.collectAsState(initial = 15.0f)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E14)) // Deep Navy
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "THERMAGUARD v4.3",
                color = Color(0xFF00F2FF), // Cyber Blue
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Glassmorphic Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${temp}°C", fontSize = 64.sp, color = Color.White, fontWeight = FontWeight.Thin)
                    Text("SILICON TEMP", color = Color(0xFF00F2FF).copy(alpha = 0.6f))
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatusBadge("CPU: ${usage.toInt()}%", Color(0xFF00F2FF))
                val throttle = SiliconPhysicsEngine.predictThrottling(temp)
                StatusBadge(if (throttle) "THROTTLING" else "STABLE", if (throttle) Color.Red else Color.Green)
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(text, color = color, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp)
    }
}
