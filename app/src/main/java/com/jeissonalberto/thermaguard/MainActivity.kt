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
import com.jeissonalberto.thermaguard.data.toThermalLevel
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import com.jeissonalberto.thermaguard.service.ThermalMonitorService
import com.jeissonalberto.thermaguard.ui.*
import com.jeissonalberto.thermaguard.ui.theme.ThermaGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThermaGuardApp(onStartService = {
                try { startForegroundService(Intent(this, ThermalMonitorService::class.java)) }
                catch (e: Exception) { }
            })
        }
    }
}

data class NavItem(val label: String, val icon: ImageVector)

@Composable
fun ThermaGuardApp(onStartService: () -> Unit) {
    val viewModel: ThermalViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    ThermaGuardTheme(appTheme = uiState.appTheme) {

    // Estados de navegación
    var showSplash by remember { mutableStateOf(true) }
    var permissionsGranted by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    val navItems = listOf(
        NavItem("Inicio",      Icons.Default.Home),
        NavItem("Diagnóstico", Icons.Default.Science),
        NavItem("Stats",       Icons.Default.BarChart),
        NavItem("Alertas",     Icons.Default.Notifications),
        NavItem("Optimizar",   Icons.Default.Tune),
        NavItem("Logs",        Icons.Default.Terminal),
        NavItem("Ajustes",      Icons.Default.Settings),
        NavItem("Acerca",      Icons.Default.Info),
    )

    // ── SPLASH ────────────────────────────────────────────────────────────
    AnimatedVisibility(
        visible = showSplash,
        enter   = fadeIn(),
        exit    = fadeOut(tween(400))
    ) {
        SplashScreen(onFinished = { showSplash = false })
    }

    // ── ONBOARDING / PERMISOS ─────────────────────────────────────────────
    AnimatedVisibility(
        visible = !showSplash && !permissionsGranted,
        enter   = fadeIn(tween(300)) + slideInVertically { it / 4 },
        exit    = fadeOut(tween(300)) + slideOutVertically { -it / 4 }
    ) {
        PermissionsScreen(onAllGranted = {
            permissionsGranted = true
            onStartService()
            viewModel.startMonitor()
        })
    }

    // ── APP PRINCIPAL ─────────────────────────────────────────────────────
    AnimatedVisibility(
        visible = !showSplash && permissionsGranted,
        enter   = fadeIn(tween(400)) + slideInVertically { it / 6 },
        exit    = fadeOut()
    ) {
        val level  = uiState.latest.batteryTemp.toThermalLevel()
        val accent = TG.accentFor(level)

        Scaffold(
            containerColor = TG.bg,
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TG.bg)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0x16FFFFFF))
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        navItems.forEachIndexed { idx, item ->
                            val selected   = selectedTab == idx
                            val itemAccent = if (selected) accent else Color.Transparent
                            val iconTint   = if (selected) accent else TG.textSec
                            val showBadge  = idx == 3 && uiState.autoActionsLog.isNotEmpty()

                            IconButton(
                                onClick  = { selectedTab = idx },
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        AnimatedContent(
                                            targetState = selected,
                                            transitionSpec = {
                                                (scaleIn(tween(200)) + fadeIn(tween(200))) togetherWith
                                                (scaleOut(tween(150)) + fadeOut(tween(150)))
                                            },
                                            label = "tab_$idx"
                                        ) { isSel ->
                                            if (isSel) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(itemAccent.copy(alpha = 0.15f))
                                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                                ) {
                                                    Icon(item.icon, null, tint = iconTint,
                                                        modifier = Modifier.size(20.dp))
                                                }
                                            } else {
                                                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) {
                                                    Icon(item.icon, null, tint = iconTint,
                                                        modifier = Modifier.size(20.dp))
                                                }
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
                                                .background(accent)
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
                // Transición animada entre tabs
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(280, easing = EaseOutCubic)) { dir * it / 3 } +
                         fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally(tween(220, easing = EaseInCubic)) { -dir * it / 3 } +
                         fadeOut(tween(220)))
                    },
                    label = "screen"
                ) { tab ->
                    when (tab) {
                        0 -> DashboardScreen(uiState = uiState, onToggleMonitor = viewModel::startMonitor, onToggleAutoMode = {}, onSetMode = { viewModel.setOperationMode(it) })
                        1 -> DiagnosisScreen(uiState = uiState)
                        2 -> StatsScreen(uiState = uiState, onResetLearning = viewModel::resetLearning)
                        3 -> AlertsScreen(uiState = uiState, onThresholdChange = viewModel::setAlertThreshold, onClearLog = viewModel::clearAutoLog)
                        4 -> OptimizeScreen(uiState = uiState, onSetMode = { viewModel.setOperationMode(it) })
                        5 -> LogsScreen(uiState = uiState)
                        6 -> SettingsScreen(
                            uiState = uiState,
                            onSetTheme = { viewModel.setAppTheme(it) },
                            onSetLanguage = { viewModel.setAppLanguage(it) }
                        )
                        7 -> AboutScreen()
                        else -> DashboardScreen(uiState = uiState, onToggleMonitor = viewModel::startMonitor, onToggleAutoMode = {}, onSetMode = { viewModel.setOperationMode(it) })
                    }
                }
            }
        }
    }
    } // end ThermaGuardTheme
}
