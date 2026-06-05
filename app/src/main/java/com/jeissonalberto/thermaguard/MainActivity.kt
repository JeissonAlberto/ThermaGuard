package com.jeissonalberto.thermaguard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import com.jeissonalberto.thermaguard.service.ThermalMonitorService
import com.jeissonalberto.thermaguard.data.toThermalLevel
import com.jeissonalberto.thermaguard.ui.*
import com.jeissonalberto.thermaguard.ui.theme.ThermaGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThermaGuardTheme {
                ThermaGuardApp(onStartService = {
                    try { startForegroundService(Intent(this, ThermalMonitorService::class.java)) }
                    catch (e: Exception) { }
                })
            }
        }
    }
}

data class NavItem(val label: String, val icon: ImageVector)

@Composable
fun ThermaGuardApp(onStartService: () -> Unit) {
    val viewModel: ThermalViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var permissionsGranted by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    val navItems = listOf(
        NavItem("Inicio",       Icons.Default.Home),
        NavItem("Diagnóstico",  Icons.Default.Science),
        NavItem("Stats",        Icons.Default.BarChart),
        NavItem("Alertas",      Icons.Default.Notifications),
    )

    if (!permissionsGranted) {
        PermissionsScreen(onAllGranted = {
            permissionsGranted = true
            onStartService()
            viewModel.startMonitor()
        })
    } else {
        Scaffold(
            containerColor = TG.bg,
            bottomBar = {
                // NavBar personalizado — flotante con glassmorphism
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF080C14))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0x1AFFFFFF))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        navItems.forEachIndexed { idx, item ->
                            val selected = selectedTab == idx
                            val accent   = if (selected) TG.accentFor(uiState.latest.batteryTemp.toThermalLevel()) else Color.Transparent
                            val iconTint = if (selected) TG.accentFor(uiState.latest.batteryTemp.toThermalLevel()) else TG.textSec

                            // Badge en Alertas si hay acciones recientes
                            val showBadge = idx == 3 && uiState.autoActionsLog.isNotEmpty()

                            IconButton(
                                onClick = { selectedTab = idx },
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        AnimatedContent(
                                            targetState = selected,
                                            transitionSpec = { scaleIn() togetherWith scaleOut() },
                                            label = "tab"
                                        ) { isSel ->
                                            if (isSel) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(accent.copy(alpha = 0.15f))
                                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(item.icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
                                                }
                                            } else {
                                                Icon(item.icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        Text(
                                            item.label,
                                            fontSize   = 9.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color      = iconTint,
                                            letterSpacing = 0.3.sp
                                        )
                                    }
                                    if (showBadge) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(TG.accentFor(uiState.latest.batteryTemp.toThermalLevel()))
                                                .offset(x = 2.dp, y = (-2).dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                when (selectedTab) {
                    0 -> DashboardScreen(
                        uiState          = uiState,
                        onToggleMonitor  = viewModel::startMonitor,
                        onToggleAutoMode = {}
                    )
                    1 -> DiagnosisScreen(uiState = uiState)
                    2 -> StatsScreen(uiState = uiState, onResetLearning = viewModel::resetLearning)
                    3 -> AlertsScreen(
                        uiState           = uiState,
                        onThresholdChange = viewModel::setAlertThreshold,
                        onClearLog        = viewModel::clearAutoLog
                    )
                }
            }
        }
    }
}
