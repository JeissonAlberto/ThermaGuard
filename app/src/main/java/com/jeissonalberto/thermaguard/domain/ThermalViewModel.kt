package com.jeissonalberto.thermaguard.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.root.RootEngine
import com.jeissonalberto.thermaguard.service.ThermalMonitorService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class ThermalUiState(
    val latest: ThermalSnapshot = ThermalSnapshot(),
    val history: List<ThermalSnapshot> = emptyList(),
    val causes: List<HeatCause> = emptyList(),
    val smartTips: List<SmartTip> = emptyList(),
    val profile: LearnedProfile? = null,
    val prediction: TempPrediction? = null,
    val batteryHealth: BatteryHealthScore? = null,
    val hourlyProfile: List<HourlyDataPoint> = emptyList(),
    val componentDiagnoses: List<ComponentDiagnosis> = emptyList(),
    val autoActionsLog: List<AutoAction> = emptyList(),
    val siliconAnalysis: SiliconAnalysis? = null,
    val operationMode: OperationMode = OperationMode.LEARNING,
    val coolingRecs: List<CoolingRecommendation> = emptyList(),
    val sensorLogs: List<SensorLog> = emptyList(),
    val isMonitoring: Boolean = false,
    val alertThreshold: Float = 43f,
    val isLoading: Boolean = true,
    // v3.2 nuevas funciones
    val gameModeState: GameModeState = GameModeState(),
    val safeChargeState: SafeChargeState = SafeChargeState(),
    val isCoolingDown: Boolean = false,         // animación de enfriamiento
    val appHeatRanking: List<Pair<String,Float>> = emptyList(),  // ranking apps
    val smartAlerts: List<SmartAlert> = emptyList(),
    // v3.9 — tema e idioma
    val appTheme: AppTheme = AppTheme.DARK,
    val appLanguage: AppLanguage = AppLanguage.SPANISH,
    val isGamerMode: Boolean = false
)

data class AutoAction(
    val timestamp: Long = System.currentTimeMillis(),
    val description: String,
    val trigger: String,
    val appsKilled: Int = 0
)

class ThermalViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorRepo     = SensorRepository(application)
    private val optRepo        = OptimizationRepository(application)
    private val learningEngine = ThermalLearningEngine(application)
    private val db             = ThermalDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(ThermalUiState())
    val uiState: StateFlow<ThermalUiState> = _uiState.asStateFlow()

    // ── Root StateFlows ───────────────────────────────────────────────
    private val _rootAvailable   = MutableStateFlow(false)
    val rootAvailable: StateFlow<Boolean> = _rootAvailable.asStateFlow()

    private val _superCoolActive = MutableStateFlow(false)
    val superCoolActive: StateFlow<Boolean> = _superCoolActive.asStateFlow()

    private val _ultraCoolActive = MutableStateFlow(false)
    val ultraCoolActive: StateFlow<Boolean> = _ultraCoolActive.asStateFlow()

    private val _superCoolResult = MutableStateFlow<RootEngine.SuperCoolResult?>(null)
    val superCoolResult: StateFlow<RootEngine.SuperCoolResult?> = _superCoolResult.asStateFlow()

    // latestSnapshot — acceso directo al último snapshot (sin necesidad de uiState)
    val latestSnapshot: StateFlow<ThermalSnapshot?> = _uiState.map { it.latest }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Tiempo del último momento en que se detectó temperatura alta (para medir cooldown)
    private var heatStartTime   = 0L
    private var lastAutoTime    = 0L
    private var wasHot          = false

    // Modo de operación — persiste entre sesiones
    private var _operationMode = OperationMode.LEARNING

    fun setOperationMode(mode: OperationMode) {
        _operationMode = mode
        _uiState.update { it.copy(operationMode = mode) }
    }

    // Intervalo adaptativo: LEARNING=60s(bajo consumo), AUTO=30s, ACTIVE=15s
    private val intervalMs: Long get() = when (_operationMode) {
        OperationMode.LEARNING -> 60_000L  // Bajo consumo
        OperationMode.AUTO     -> 30_000L  // Balanceado
        OperationMode.ACTIVE   -> 15_000L  // Alto seguimiento
        OperationMode.GAMER    ->  5_000L  // Máxima reactividad anti-throttle (S22 throttlea cada 4-6s)
    }

    init {
        observeHistory()
        startLiveReading()
        viewModelScope.launch { try { _rootAvailable.value = RootEngine.isRootAvailable() } catch (_:Exception) { _rootAvailable.value = false } }
    }

    private fun startLiveReading() {
        viewModelScope.launch {
            delay(1500L)
            while (true) {
                try {
                    val snapshot   = sensorRepo.readSnapshot()
                    // En modo LEARNING con temp normal: saltar análisis pesados
                    val isLowImpact = _operationMode == OperationMode.LEARNING && snapshot.batteryTemp < 38f
                    val causes     = if (isLowImpact) emptyList() else sensorRepo.analyzeHeatCauses(snapshot)
                    val diagnoses  = sensorRepo.diagnoseComponents(snapshot)
                    val profile    = learningEngine.learn(snapshot)
                    val silicon    = learningEngine.analyzeSilicon(snapshot)
                    val coolingRecs = if (_operationMode != OperationMode.LEARNING || snapshot.batteryTemp > 40f)
                        learningEngine.generateCoolingRecommendations(snapshot, silicon, _operationMode)
                    else emptyList()
                    val prediction = learningEngine.predictNextTemp()
                    val health     = learningEngine.computeBatteryHealthScore()
                    val hourly     = learningEngine.getHourlyProfile()
                    val tips       = learningEngine.generateSmartTips(profile, snapshot, prediction)
                    val gameMode   = detectGameMode(snapshot.topApp)
                    val safeCharge = evalSafeCharge(snapshot)
                    val appRanking = computeAppRanking()
                    val wasCooling = _uiState.value.isCoolingDown
                    val isCooling  = !snapshot.batteryTemp.toThermalLevel().let {
                        it == ThermalLevel.HOT || it == ThermalLevel.CRITICAL || it == ThermalLevel.EMERGENCY
                    } && wasHot

                    // Umbral dinámico desde el perfil aprendido
                    val dynThreshold = profile.dynamicThreshold.takeIf { it > 35f } ?: 43f

                    // Tracking de sesión de calor para medir cooldown
                    val isHotNow = snapshot.batteryTemp >= dynThreshold
                    if (isHotNow && !wasHot) {
                        heatStartTime = System.currentTimeMillis()
                    } else if (!isHotNow && wasHot && heatStartTime > 0L) {
                        val cooldownMin = (System.currentTimeMillis() - heatStartTime) / 60000f
                        learningEngine.recordCooldown(cooldownMin)
                    }
                    wasHot = isHotNow

                    // Publicar en el Service para que no repita la lectura
                    ThermalMonitorService.lastSnapshot = snapshot
                    ThermalMonitorService.lastProfile  = profile

                    _uiState.update {
                        it.copy(
                            latest             = snapshot,
                            causes             = causes,
                            profile            = profile,
                            prediction         = prediction,
                            batteryHealth      = health,
                            hourlyProfile      = hourly,
                            smartTips          = tips,
                            componentDiagnoses = diagnoses,
                            alertThreshold     = dynThreshold,
                            isLoading          = false,
                            isMonitoring       = true,
                            siliconAnalysis    = silicon,
                            operationMode      = _operationMode,
                            coolingRecs        = coolingRecs,
                            sensorLogs         = sensorRepo.sensorLogs,
                            gameModeState      = gameMode,
                            safeChargeState    = safeCharge,
                            isCoolingDown      = isCooling,
                            appHeatRanking     = appRanking
                        )
                    }

                    executeAutoOptimization(snapshot, profile)

                } catch (_: Exception) { }

                delay(intervalMs)
            }
        }
    }

    /**
     * Motor de optimización automática v3.
     * Usa el riskScore y cooldown aprendido para decidir cuándo actuar.
     */
    private fun executeAutoOptimization(snap: ThermalSnapshot, profile: LearnedProfile) {
        val now = System.currentTimeMillis()
        // Cooldown inteligente: usa el tiempo promedio de enfriamiento aprendido
        val cooldownMs = (profile.avgCooldownMinutes * 60_000f).toLong().coerceAtLeast(
            if (_operationMode == OperationMode.GAMER) 2 * 60_000L else 3 * 60_000L
        )
        if (now - lastAutoTime < cooldownMs) return

        val level = snap.batteryTemp.toThermalLevel()
        // También actuar si hay anomalía detectada aunque la temp sea moderada
        val shouldAct = level == ThermalLevel.HOT || level == ThermalLevel.CRITICAL ||
                        level == ThermalLevel.EMERGENCY || profile.isAnomaly

        if (!shouldAct) return

        viewModelScope.launch {
            val actions = mutableListOf<AutoAction>()
            try {
                when {
                    level == ThermalLevel.EMERGENCY || level == ThermalLevel.CRITICAL -> {
                        val killed = optRepo.killBackgroundApps()
                        val freed  = optRepo.freeRam()
                        if (killed > 0 || freed > 0) {
                            actions.add(AutoAction(
                                description = "Modo cooling: cerré $killed apps • liberé RAM",
                                trigger     = "${snap.batteryTemp}°C — ${level.label}",
                                appsKilled  = killed
                            ))
                        }
                        if (snap.isCharging && snap.batteryTemp >= 46f) {
                            actions.add(AutoAction(
                                description = "⚠️ Temperatura crítica mientras carga — desconecta el cargador",
                                trigger     = "Carga + ${snap.batteryTemp}°C"
                            ))
                        }
                    }
                    level == ThermalLevel.HOT -> {
                        val killed = optRepo.killBackgroundApps()
                        if (killed > 0) {
                            actions.add(AutoAction(
                                description = "Cerré $killed apps en background",
                                trigger     = "${snap.batteryTemp}°C — ${level.label}",
                                appsKilled  = killed
                            ))
                        }
                    }
                    profile.isAnomaly -> {
                        // Temperatura moderada pero anómala — acción preventiva suave
                        val killed = optRepo.killBackgroundApps()
                        actions.add(AutoAction(
                            description = "Acción preventiva: anomalía detectada • cerré $killed apps",
                            trigger     = "Anomalía — ${snap.batteryTemp}°C fuera del patrón normal"
                        ))
                    }
                }
            } catch (_: Exception) { }

            if (actions.isNotEmpty()) {
                lastAutoTime = now
                _uiState.update { state ->
                    state.copy(autoActionsLog = (actions + state.autoActionsLog).take(20))
                }
            }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            db.thermalDao().getHistory(200).distinctUntilChanged().collect { rows ->
                _uiState.update { it.copy(history = rows) }
            }
        }
    }

    fun startMonitor()              { _uiState.update { it.copy(isMonitoring = true) } }
    fun setAlertThreshold(t: Float) { _uiState.update { it.copy(alertThreshold = t) } }
    fun resetLearning()             { learningEngine.reset() }
    fun clearAutoLog()              { _uiState.update { it.copy(autoActionsLog = emptyList()) } }
    // ── Modo Juego ──────────────────────────────────────────────────────
    private val gamingPackages = listOf("com.mobile.legends", "com.tencent.ig", "com.activision.callofduty", "com.garena.game", "com.dts.freefireth", "com.king.candycrushsaga", "com.supercell.clashofclans", "com.epicgames", "com.roblox.client", "com.mojang.minecraftpe")

    fun detectGameMode(topApp: String): GameModeState {
        val isGame = gamingPackages.any { pkg -> topApp.contains(pkg, ignoreCase = true) }
                  || topApp.lowercase().let { it.contains("game") || it.contains("arena") || it.contains("clash") || it.contains("legend") }
        return if (isGame) GameModeState(
            isActive             = true,
            detectedGame         = topApp,
            permissiveThreshold  = 46f,
            startedAt            = System.currentTimeMillis()
        ) else GameModeState()
    }

    // ── Modo Carga Segura ────────────────────────────────────────────────
    fun evalSafeCharge(snap: ThermalSnapshot): SafeChargeState {
        if (!snap.isCharging) return SafeChargeState()
        val overheating = snap.batteryTemp > 38f
        val rec = when {
            snap.batteryTemp > 42f -> "Retira el cargador temporalmente"
            snap.batteryTemp > 39f -> "Carga lenta recomendada — evita usar el teléfono"
            else                   -> "Temperatura de carga normal"
        }
        return SafeChargeState(isCharging = true, chargingTemp = snap.batteryTemp, isOverheating = overheating, recommendation = rec)
    }

    // ── Exportar CSV ─────────────────────────────────────────────────────
    fun exportHistoryToCsv(): String {
        val ctx = getApplication<android.app.Application>()
        val sb = StringBuilder()
        sb.appendLine("timestamp,batteryTemp,cpuUsage,batteryLevel,topApp,riskLevel")
        _uiState.value.history.forEach { snap ->
            val dt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(snap.timestamp))
            val risk = snap.batteryTemp.toThermalLevel().name
            sb.appendLine("$dt,${snap.batteryTemp},${snap.cpuUsage.toInt()},${snap.batteryLevel},${snap.topApp},$risk")
        }
        val file = java.io.File(ctx.getExternalFilesDir(null), "thermaguard_${System.currentTimeMillis()}.csv")
        file.writeText(sb.toString())
        return file.absolutePath
    }

    // ── Ranking apps ─────────────────────────────────────────────────────
    fun computeAppRanking(): List<Pair<String, Float>> {
        val scores = mutableMapOf<String, MutableList<Float>>()
        _uiState.value.history
            .filter { it.topApp.isNotEmpty() && it.batteryTemp >= 38f }
            .forEach { snap -> scores.getOrPut(snap.topApp) { mutableListOf() }.add(snap.batteryTemp) }
        return scores.map { (app, temps) -> app to temps.average().toFloat() }
            .sortedByDescending { it.second }.take(8)
    }

    // ── Alerta inteligente: no molestar si ya avisó en los últimos 10 min ─
    private val alertSuppressMap = mutableMapOf<String, Long>()
    fun shouldFireAlert(id: String, cooldownMs: Long = 10 * 60 * 1000L): Boolean {
        val now  = System.currentTimeMillis()
        val last = alertSuppressMap[id] ?: 0L
        return if (now - last > cooldownMs) {
            alertSuppressMap[id] = now
            true
        } else false
    }



    // ── TEMA ────────────────────────────────────────────────────────────────
    fun setAppTheme(theme: AppTheme) {
        _uiState.update { it.copy(appTheme = theme) }
    }

    // ── IDIOMA ───────────────────────────────────────────────────────────────
    fun setAppLanguage(lang: AppLanguage) {
        _uiState.update { it.copy(appLanguage = lang) }
    }

    // ── MODO GAMER ────────────────────────────────────────────────────────────
    fun enableGamerMode() {
        _operationMode = OperationMode.GAMER
        _uiState.update { it.copy(operationMode = OperationMode.GAMER, isGamerMode = true) }
    }
    fun disableGamerMode() {
        _operationMode = OperationMode.AUTO
        _uiState.update { it.copy(operationMode = OperationMode.AUTO, isGamerMode = false) }
    }


    // ── Acciones manuales ──────────────────────────────────────────────────
    fun killAppsNow() = viewModelScope.launch {
        try {
            val killed = optRepo.killBackgroundApps()
            val freed  = optRepo.freeRam()
            val action = AutoAction(
                description = "Manual: cerré $killed apps • liberé ${freed}MB RAM",
                trigger     = "Botón usuario"
            )
            _uiState.update { it.copy(autoActionsLog = (listOf(action) + it.autoActionsLog).take(20)) }
        } catch (e: Exception) { android.util.Log.e("ThermaGuard", "killAppsNow: ${e.message}") }
    }

        // ── Métodos Root ──────────────────────────────────────────────────
    fun checkRoot() = viewModelScope.launch {
        try {
        _rootAvailable.value = RootEngine.isRootAvailable()
        } catch (e: Exception) { android.util.Log.e("ThermaGuard","checkRoot error",e) }
    }

    fun activateSuperCool(ultra: Boolean = false) = viewModelScope.launch {
        try {
        if (!_rootAvailable.value) return@launch
        val result = RootEngine.activateSuperCool(getApplication(), ultra)
        _superCoolResult.value = result
        _superCoolActive.value = !ultra
        _ultraCoolActive.value = ultra
        } catch (e: Exception) { android.util.Log.e("ThermaGuard","activateSuperCool error",e) }
    }

    fun deactivateSuperCool() = viewModelScope.launch {
        try {
        RootEngine.deactivateSuperCool()
        _superCoolActive.value = false
        _ultraCoolActive.value = false
        } catch (e: Exception) { android.util.Log.e("ThermaGuard","deactivateSuperCool error",e) }
    }

    fun rootCpuThrottle() = viewModelScope.launch {
        try {
        if (!_rootAvailable.value) return@launch
        RootEngine.setCpuMaxFreq(RootEngine.CpuLevel.THROTTLE)
        } catch (e: Exception) { android.util.Log.e("ThermaGuard","rootCpuThrottle error",e) }
    }

    fun rootGpuThrottle() = viewModelScope.launch {
        try {
        if (!_rootAvailable.value) return@launch
        RootEngine.setGpuMaxFreq(RootEngine.GpuLevel.THROTTLE)
        } catch (e: Exception) { android.util.Log.e("ThermaGuard","rootGpuThrottle error",e) }
    }

    fun rootDisableData() = viewModelScope.launch {
        try {
        if (!_rootAvailable.value) return@launch
        RootEngine.disableMobileData()
        } catch (e: Exception) { android.util.Log.e("ThermaGuard","rootDisableData error",e) }
    }

    fun rootSetBrightness(percent: Int) = viewModelScope.launch {
        try {
        if (!_rootAvailable.value) return@launch
        RootEngine.setBrightness((percent / 100f * 255).toInt())
        } catch (e: Exception) { android.util.Log.e("ThermaGuard","rootSetBrightness error",e) }
    }

    fun rootKillBg() = viewModelScope.launch {
        try {
        if (!_rootAvailable.value) return@launch
        RootEngine.killBackgroundApps(getApplication())
        } catch (e: Exception) { android.util.Log.e("ThermaGuard","rootKillBg error",e) }
    }
}
