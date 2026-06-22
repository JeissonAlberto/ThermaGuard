package com.jeissonalberto.thermaguard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.jeissonalberto.thermaguard.data.AppUpdate
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
import com.jeissonalberto.thermaguard.ui.theme.LocalTgColors

private const val PREFS_ONBOARD = "tg_onboarding"
private const val KEY_ONBOARD   = "done"

class MainActivity : ComponentActivity() {

    // ── Lanzador de permisos en runtime ──────────────────────────────────────
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            results.forEach { (perm, granted) ->
                android.util.Log.d("ThermaGuard", "Permiso $perm: $granted")
            }
            // Solicitar permisos especiales que requieren navegación a Settings
            requestSpecialPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Solicitar permisos normales al iniciar
        requestAllPermissions()
        setContent {
            ThermaGuardApp(
                context       = this,
                onStartService = {
                    try {
                        val i = Intent(this, ThermalMonitorService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(i)
                        } else {
                            startService(i)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("ThermaGuard", "Service: ${e.message}")
                    }
                }
            )
        }
    }

    /** Solicita todos los permisos que el sistema puede pedir en runtime. */
    private fun requestAllPermissions() {
        val perms = buildList {
            // Notificaciones — Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            // Bluetooth — Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            // Estado del teléfono (temperatura modem)
            add(Manifest.permission.READ_PHONE_STATE)
        }
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        } else {
            requestSpecialPermissions()
        }
    }

    /**
     * Permisos especiales que requieren ir a pantalla de configuración.
     * Solo se abre la pantalla si el permiso todavía no fue otorgado.
     */
    private fun requestSpecialPermissions() {
        // 1. Ignorar optimizaciones de batería (Doze Mode)
        val pm = getSystemService(android.content.Context.POWER_SERVICE)
                as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                ))
            } catch (_: Exception) {}
        }
        // 2. Overlay / Ventana sobre otras apps (SYSTEM_ALERT_WINDOW)
        if (!Settings.canDrawOverlays(this)) {
            try {
                startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ))
            } catch (_: Exception) {}
        }
        // 3. Estadísticas de uso de apps (PACKAGE_USAGE_STATS)
        if (!hasUsageStatsPermission()) {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {}
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = getSystemService(android.content.Context.APP_OPS_SERVICE)
                    as android.app.AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), packageName
                )
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
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
//  TABS — índices únicos y consistentes
//  BARRA PRINCIPAL (visible): 0-4
//  MENÚ "MÁS"               : 5-11
//  SUBPANTALLAS (sin tab)   : 12=Legal (desde AboutScreen)
// ─────────────────────────────────────────────────────────────────────────────
private const val TAB_DASHBOARD  = 0
private const val TAB_DIAGNOSIS  = 1
private const val TAB_BEAST      = 2
private const val TAB_OPTIMIZE   = 3
private const val TAB_SETTINGS   = 4
private const val TAB_HISTORY    = 5
private const val TAB_STATS      = 6
private const val TAB_ALERTS     = 7
private const val TAB_LOGS       = 8
private const val TAB_ABOUT      = 9
private const val TAB_ROOT       = 10
private const val TAB_PHYSICS    = 11
private const val TAB_LEGAL      = 12   // subpantalla, no aparece en nav
private const val TAB_HARDWARE    = 13
private const val TAB_THERMAL_OPT = 14

// ─────────────────────────────────────────────────────────────────────────────
//  SHELL PRINCIPAL — Bottom Nav + Contenido
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MainAppShell(
    viewModel:   ThermalViewModel,
    context:     Context,
    onExportCsv: () -> String,
) {
    val tg            = LocalTgColors.current
    val uiState       by viewModel.uiState.collectAsState()
    val rootAvail     by viewModel.rootAvailable.collectAsState()
    val pendingUpdate by viewModel.pendingUpdate.collectAsState()
    val level         = uiState.latest.batteryTemp.toThermalLevel()
    val accent        = TG.accentFor(level)
    var selectedTab   by remember { mutableStateOf(TAB_DASHBOARD) }
    var showMoreMenu  by remember { mutableStateOf(false) }

    // Barra principal — solo 5 tabs siempre visibles
    val mainTabs = listOf(
        NavItem("Inicio",      Icons.Default.Home),
        NavItem("Diagnóstico", Icons.Default.Science),
        NavItem("Bestia",      Icons.Default.Bolt),
        NavItem("Optimizar",   Icons.Default.Tune),
        NavItem("Ajustes",     Icons.Default.Settings),
    )
    // Menú "Más"
    val moreTabs = remember(rootAvail) { buildList {
        add(Triple(TAB_HISTORY, "Historial",  Icons.Default.History))
        add(Triple(TAB_STATS,   "Estadísticas", Icons.Default.BarChart))
        add(Triple(TAB_ALERTS,  "Alertas",    Icons.Default.Notifications))
        add(Triple(TAB_LOGS,    "Registros",  Icons.Default.Terminal))
        add(Triple(TAB_PHYSICS, "Física",     Icons.Default.Science))
        add(Triple(TAB_HARDWARE,"Hardware",   Icons.Default.Memory))
        add(Triple(TAB_THERMAL_OPT, "Optimización", Icons.Default.Thermostat))
        add(Triple(TAB_ABOUT,   "Acerca de",  Icons.Default.Info))
        if (rootAvail) add(Triple(TAB_ROOT, "Root ⚡", Icons.Default.Security))
    } }
    val selectedInMore = selectedTab in moreTabs.map { it.first }

    Scaffold(
        containerColor = tg.bg,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tg.bg)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(tg.surface)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // ── 5 tabs principales ────────────────────────────────
                    mainTabs.forEachIndexed { idx, item ->
                        val selected  = selectedTab == idx
                        val iconTint  = if (selected) accent else TG.textSec
                        val showBadge = idx == TAB_BEAST && uiState.operationMode == OperationMode.GAMER

                        Box(
                            modifier         = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = { selectedTab = idx }) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent)
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Icon(
                                                item.icon, null,
                                                tint     = iconTint,
                                                modifier = Modifier.size(20.dp)
                                            )
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

                    // ── Botón "Más" ───────────────────────────────────────
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        IconButton(onClick = { showMoreMenu = !showMoreMenu }) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selectedInMore) accent.copy(alpha = 0.18f) else Color.Transparent)
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MoreHoriz, null,
                                        tint     = if (selectedInMore) accent else TG.textSec,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    "Más",
                                    fontSize   = 9.sp,
                                    color      = if (selectedInMore) accent else TG.textSec,
                                    fontWeight = if (selectedInMore) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                        DropdownMenu(
                            expanded          = showMoreMenu,
                            onDismissRequest  = { showMoreMenu = false },
                            modifier          = Modifier.background(Color(0xFF0D1520))
                        ) {
                            moreTabs.forEach { (tabIdx, label, icon) ->
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
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(260, easing = EaseOutCubic)) { dir * it / 3 } +
                     fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(200, easing = EaseInCubic)) { -dir * it / 3 } +
                     fadeOut(tween(200)))
                },
                label = "screen"
            ) { tab ->
                when (tab) {
                    TAB_DASHBOARD -> {
                        val uName by viewModel.userName.collectAsState()
                        DashboardScreen(
                            uiState          = uiState,
                            onToggleMonitor  = viewModel::startMonitor,
                            onToggleAutoMode = {},
                            onSetMode        = { viewModel.setOperationMode(it) },
                            pendingUpdate    = pendingUpdate,
                            onDismissUpdate  = { viewModel.dismissUpdate() },
                            userName         = uName
                        )
                    }
                    TAB_DIAGNOSIS -> DiagnosisScreen(uiState = uiState)
                    TAB_BEAST     -> BeastModeScreen(
                        uiState   = uiState,
                        onSetMode = {
                            viewModel.setOperationMode(it)
                            if (it == com.jeissonalberto.thermaguard.data.OperationMode.GAMER)
                                viewModel.unlockMaxPerformance()
                            else viewModel.applyPhysicsGovernor()
                        }
                    )
                    TAB_OPTIMIZE  -> OptimizeScreen(
                        uiState    = uiState,
                        onSetMode  = { viewModel.setOperationMode(it) },
                        onKillApps = { viewModel.killAppsNow() }
                    )
                    TAB_SETTINGS  -> {
                        val telOn   by viewModel.telemetryEnabled.collectAsState()
                        val uName   by viewModel.userName.collectAsState()
                        val devNick by viewModel.deviceNickname.collectAsState()
                        val uProf   by viewModel.usageProfile.collectAsState()
                        SettingsScreen(
                            uiState             = uiState,
                            onSetTheme          = { viewModel.setAppTheme(it) },
                            onSetLanguage       = { viewModel.setAppLanguage(it) },
                            telemetryEnabled    = telOn,
                            onToggleTelemetry   = { viewModel.setTelemetryEnabled(it) },
                            onCheckUpdateNow    = { viewModel.checkForUpdates() },
                            userName            = uName,
                            deviceNickname      = devNick,
                            usageProfile        = uProf,
                            onSetUserName       = { viewModel.setUserName(it) },
                            onSetDeviceNickname = { viewModel.setDeviceNickname(it) },
                            onSetUsageProfile   = { viewModel.setUsageProfile(it) }
                        )
                    }
                    TAB_HISTORY -> HistoryScreen(uiState = uiState, onExportCsv = onExportCsv)
                    TAB_STATS   -> StatsScreen(uiState = uiState, onResetLearning = viewModel::resetLearning)
                    TAB_ALERTS  -> AlertsScreen(
                        uiState           = uiState,
                        onThresholdChange = viewModel::setAlertThreshold,
                        onClearLog        = viewModel::clearAutoLog
                    )
                    TAB_LOGS    -> LogsScreen(uiState = uiState)
                    TAB_ABOUT   -> AboutScreen(onLegalClick = { selectedTab = TAB_LEGAL })
                    TAB_ROOT    -> RootControlScreen(viewModel = viewModel)
                    TAB_PHYSICS -> PhysicsScreen(
                        uiState             = uiState,
                        onApplyGovernor     = { viewModel.applyPhysicsGovernor() },
                        onEmergencyThrottle = { viewModel.emergencyThrottleIfNeeded() },
                        onUnlockMaxPerf     = { viewModel.unlockMaxPerformance() }
                    )
                    TAB_HARDWARE-> HardwareScreen()
                    TAB_THERMAL_OPT -> ThermalOptimizationScreen(uiState = uiState)
                    TAB_LEGAL   -> LegalScreen()
                    else        -> DashboardScreen(
                        uiState          = uiState,
                        onToggleMonitor  = viewModel::startMonitor,
                        onToggleAutoMode = {},
                        onSetMode        = { viewModel.setOperationMode(it) }
                    )
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
