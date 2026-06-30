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
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF020408))) {
        Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
            // Header con versión explícita
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF00F2FF)))
                Spacer(modifier = Modifier.width(10.dp))
                Text("THERMAGUARD v4.3.25", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            
            Spacer(modifier = Modifier.height(30.dp))
            
            // Thermal Core Monitor (Glassmorphism)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(1.dp, Color(0xFF00F2FF).copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("36.8°C", fontSize = 52.sp, color = Color.White, fontWeight = FontWeight.Thin)
                    Text("SILICON PHYSICS ENGINE: ON", color = Color(0xFF00F2FF), fontSize = 10.sp, letterSpacing = 2.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(25.dp))
            
            Text("ECOSYSTEM TOOLS", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(15.dp))
            
            ToolRow("FLEETBASE LOGISTICS", "ACTIVE / CONNECTED", Color(0xFF00FFCC))
            Spacer(modifier = Modifier.height(10.dp))
            ToolRow("PROJECT ARCHITECT", "V4.3 STRUCTURE", Color(0xFFBB86FC))
            Spacer(modifier = Modifier.height(10.dp))
            ToolRow("EVOLUTION ENGINE", "STABLE", Color(0xFF00F2FF))
        }
    }
}

@Composable
fun ToolRow(name: String, status: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.05f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(status, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
