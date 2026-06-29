package com.jeissonalberto.thermaguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import com.jeissonalberto.thermaguard.data.TG

@Composable
fun DashboardScreen(viewModel: ThermalViewModel) {
    val temp by viewModel.batteryTemp.collectAsState()
    
    val backgroundColor = Color(0xFF0F172A) // Dark Navy (Pro Style)
    val glassColor = Color(0xFF1E293B).copy(alpha = 0.8f)
    val accentColor = if (temp < 40f) Color(0xFF10B981) else Color(0xFFEF4444)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "THERMAGUARD EVOLUTION",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            // Glassmorphism Card para el Motor de Física
            Surface(
                color = glassColor,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${temp}°C",
                        color = accentColor,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Light
                    )
                    Text(
                        text = "CORE TEMPERATURE",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Barra de Flujo Termodinámico (Visual)
                    LinearProgressIndicator(
                        progress = (temp / 60f).coerceIn(0f, 1f),
                        color = accentColor,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "SILICON PHYSICS ENGINE ACTIVE",
                        color = Color(0xFF38BDF8), // Cyber Blue
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status Card
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatusMiniCard("STATUS", "NOMINAL", Modifier.weight(1f))
                StatusMiniCard("RISK", "LOW", Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatusMiniCard(label: String, value: String, modifier: Modifier) {
    Surface(
        color = Color(0xFF1E293B).copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
            Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
