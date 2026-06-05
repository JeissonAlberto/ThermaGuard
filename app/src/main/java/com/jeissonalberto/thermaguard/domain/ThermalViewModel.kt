package com.jeissonalberto.thermaguard.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jeissonalberto.thermaguard.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
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
    val isMonitoring: Boolean = false,
    val alertThreshold: Float = 43f,
    val isLoading: Boolean = true
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

    // Tiempo del último momento en que se detectó temperatura alta (para medir cooldown)
    private var heatStartTime   = 0L
    private var lastAutoTime    = 0L
    private var wasHot          = false

    init {
        observeHistory()
        startLiveReading()
    }

    private fun startLiveReading() {
        viewModelScope.launch {
            delay(1500L)
            while (true) {
                try {
                    val snapshot   = sensorRepo.readSnapshot()
                    val causes     = sensorRepo.analyzeHeatCauses(snapshot)
                    val diagnoses  = sensorRepo.diagnoseComponents(snapshot)
                    val profile    = learningEngine.learn(snapshot)
                    val prediction = learningEngine.predictNextTemp()
                    val health     = learningEngine.computeBatteryHealthScore()
                    val hourly     = learningEngine.getHourlyProfile()
                    val tips       = learningEngine.generateSmartTips(profile, snapshot, prediction)

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
                            isMonitoring       = true
                        )
                    }

                    executeAutoOptimization(snapshot, profile)

                } catch (_: Exception) { }

                delay(30_000L)
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
        val cooldownMs = (profile.avgCooldownMinutes * 60_000f).toLong().coerceAtLeast(3 * 60_000L)
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
            db.thermalDao().getHistory(120).collect { rows ->
                _uiState.update { it.copy(history = rows) }
            }
        }
    }

    fun startMonitor()              { _uiState.update { it.copy(isMonitoring = true) } }
    fun setAlertThreshold(t: Float) { _uiState.update { it.copy(alertThreshold = t) } }
    fun resetLearning()             { learningEngine.reset() }
    fun clearAutoLog()              { _uiState.update { it.copy(autoActionsLog = emptyList()) } }
}
