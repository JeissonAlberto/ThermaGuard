package com.jeissonalberto.thermaguard

import android.content.Context
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
import com.jeissonalberto.thermaguard.data.OperationMode
import com.jeissonalberto.thermaguard.data.toThermalLevel
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import com.jeissonalberto.thermaguard.service.ThermalMonitorService
import com.jeissonalberto.thermaguard.ui.*
import com.jeissonalberto.thermaguard.ui.screens.RootControlScreen
import com.jeissonalberto.thermaguard.ui.theme.ThermaGuardTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private const val PREFS_ONBOARD = "tg_onboarding"
private const val KEY_ONBOARD   = "done"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThermaGuardApp(
                context       = this,
                onStartService = {
                    try { startForegroundService(Intent(this, ThermalMonitorService::class.java)) }
                    catch (_: Exception) {}
                }
            )
        }
    }
}

data class NavItem(val label: String, val icon: ImageVector)

// ─────────────────────────────────────────────────────────────────────────────
//  APP ROOT — Splash → Onboarding → Login → App
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ThermaGuardApp(context: Context, onStartService: () -> Unit) {
    val viewModel: ThermalViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    ThermaGuardTheme(appTheme = uiState.appTheme) {

        val prefs       = remember { context.getSharedPreferences(PREFS_ONBOARD, Context.MODE_PRIVATE) }
        val onboardDone = remember { prefs.getBoolean(KEY_ONBOARD, false) }

        var showSplash    by remember { mutableStateOf(true) }
        var showOnboard   by remember { mutableStateOf(!onboardDone) }
        var showLogin     by remember { mutableStateOf(false) }
        var appReady      by remember { mutableStateOf(false) }
        var permsGranted  by remember { mutableStateOf(false) }

        // Splash → Onboarding o Login
        AnimatedVisibility(visible = showSplash, enter = fadeIn(), exit = fadeOut(tween(400))) {
            SplashScreen(onFinished = { showSplash = false })
        }

        // Onboarding (primera vez)
        AnimatedVisibility(
            visible = !showSplash && showOnboard,
            enter = fadeIn(tween(300)) + slideInVertically { it / 4 },
            exit  = fadeOut(tween(300)) + slideOutVertically { -it / 4 }
        ) {
            OnboardingScreen(onFinish = {
                prefs.edit().putBoolean(KEY_ONBOARD, true).apply()
                showOnboard = false
                showLogin   = true
            })
        }

        // Login / Setup PIN
        AnimatedVisibility(
            visible = !showSplash && !showOnboard && showLogin,
            enter = fadeIn(tween(300)) + slideInVertically { it / 4 },
            exit  = fadeOut(tween(300)) + slideOutVertically { -it / 4 }
        ) {
            LoginScreen(onAuthenticated = {
                showLogin = false
                appReady  = true
            })
        }

        // Permisos (si todavía no los ha dado)
        AnimatedVisibility(
            visible = !showSplash && !showOnboard && !showLogin && !permsGranted,
            enter = fadeIn(tween(300)) + slideInVertically { it / 4 },
            exit  = fadeOut(tween(300)) + slideOutVertically { -it / 4 }
        ) {
            PermissionsScreen(onAllGranted = {
                permsGranted = true
                onStartService()
                viewModel.startMonitor()
            })
        }

        // App principal
        AnimatedVisibility(
            visible = !showSplash && (onboardDone || appReady) && permsGranted,
            enter   = fadeIn(tween(400)) + slideInVertically { it / 6 },
            exit    = fadeOut()
        ) {
            MainAppShell(
                viewModel      = viewModel,
                context        = context,
                onExportCsv    = { exportCsv(context, viewModel.uiState.value.history.map { it }) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SHELL PRINCIPAL — Bottom Nav + Contenido
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MainAppShell(
    viewModel:   ThermalViewModel,
    context:     Context,
    onExportCsv: () -> String,
) {
    val uiState     by viewModel.uiState.collectAsState()
    val rootAvail   by viewModel.rootAvailable.collectAsState()
    val level        = uiState.latest.batteryTemp.toThermalLevel()
    val accent       = TG.accentFor(level)
    var selectedTab  by remember { mutableStateOf(0) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val navItems = listOf(
        NavItem("Inicio",      Icons.Default.Home),
        NavItem("Diagnóstico", Icons.Default.Science),
        NavItem("Bestia",      Icons.Default.Bolt),
        NavItem("Optimizar",   Icons.Default.Tune),
        NavItem("Ajustes",     Icons.Default.Settings),
    )

    Scaffold(
        containerColor = TG.bg,
        bottomBar = {
            Column {
                // Barra principal de 5 tabs
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TG.bg)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0x16FFFFFF))
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        navItems.forEachIndexed { idx, item ->
                            val selected   = selectedTab == idx
                            val iconTint   = if (selected) accent else TG.textSec
                            val showBadge  = idx == 2 && uiState.operationMode == OperationMode.GAMER

                            Box(
                                modifier        = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(onClick = { selectedTab = idx }) {
                                    Box(contentAlignment = Alignment.TopEnd) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            val pad = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            if (selected) {
                                                Box(modifier = Modifier.clip(RoundedCornerShape(14.dp))
                                                    .background(accent.copy(alpha = 0.15f)).then(pad)) {
                                                    Icon(item.icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
                                                }
                                            } else {
                                                Box(modifier = pad) {
                                                    Icon(item.icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            Text(item.label, fontSize = 9.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                color = iconTint, letterSpacing = 0.3.sp)
                                        }
                                        if (showBadge) {
                                            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                                                .background(accent).offset(x = 2.dp, y = (-2).dp))
                                        }
                                    }
                                }
                            }
                        }

                        // Botón "Más"
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            IconButton(onClick = { showMoreMenu = !showMoreMenu }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(Icons.Default.MoreHoriz, null,
                                        tint = if (selectedTab >= 5) accent else TG.textSec,
                                        modifier = Modifier.size(20.dp))
                                    Text("Más", fontSize = 9.sp,
                                        color = if (selectedTab >= 5) accent else TG.textSec,
                                        fontWeight = if (selectedTab >= 5) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                modifier = Modifier.background(Color(0xFF0D1520))
                            ) {
                                (listOf(
                                    Triple(5, "Historial", Icons.Default.History),
                                    Triple(6, "Stats",     Icons.Default.BarChart),
                                    Triple(7, "Alertas",   Icons.Default.Notifications),
                                    Triple(8, "Logs",      Icons.Default.Terminal),
                                    Triple(9, "Acerca",    Icons.Default.Info),
                                ) + (if (rootAvail) listOf(Triple(10, "Root ⚡", Icons.Default.Security)) else emptyList())
                                ).forEach { (tabIdx, label, icon) ->
                                    DropdownMenuItem(
                                        text        = { Text(label, color = TG.textPri, fontSize = 13.sp) },
                                        leadingIcon = { Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp)) },
                                        onClick     = { selectedTab = tabIdx; showMoreMenu = false }
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
                    0  -> DashboardScreen(uiState = uiState, onToggleMonitor = viewModel::startMonitor, onToggleAutoMode = {}, onSetMode = { viewModel.setOperationMode(it) })
                    1  -> DiagnosisScreen(uiState = uiState)
                    2  -> BeastModeScreen(uiState = uiState, onSetMode = { viewModel.setOperationMode(it) })
                    3  -> OptimizeScreen(uiState = uiState, onSetMode = { viewModel.setOperationMode(it) })
                    4  -> SettingsScreen(uiState = uiState, onSetTheme = { viewModel.setAppTheme(it) }, onSetLanguage = { viewModel.setAppLanguage(it) })
                    5  -> HistoryScreen(uiState = uiState, onExportCsv = onExportCsv)
                    6  -> StatsScreen(uiState = uiState, onResetLearning = viewModel::resetLearning)
                    7  -> AlertsScreen(uiState = uiState, onThresholdChange = viewModel::setAlertThreshold, onClearLog = viewModel::clearAutoLog)
                    8  -> LogsScreen(uiState = uiState)
                    9  -> AboutScreen()
                    10 -> RootControlScreen(viewModel = viewModel)
                    else -> DashboardScreen(uiState = uiState, onToggleMonitor = viewModel::startMonitor, onToggleAutoMode = {}, onSetMode = { viewModel.setOperationMode(it) })
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Exportar CSV
// ─────────────────────────────────────────────────────────────────────────────
private fun exportCsv(context: Context, history: List<com.jeissonalberto.thermaguard.data.ThermalSnapshot>): String {
    val sb  = StringBuilder("timestamp,batteryTemp,cpuUsage,batteryLevel,isCharging,topApp\n")
    val dtf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    history.forEach { s ->
        sb.append("${dtf.format(Date(s.timestamp))},${s.batteryTemp},${s.cpuUsage.toInt()},${s.batteryLevel},${s.isCharging},\"${s.topApp}\"\n")
    }
    val file = File(context.getExternalFilesDir(null), "thermaguard_${System.currentTimeMillis()}.csv")
    file.writeText(sb.toString())
    return file.absolutePath
}
