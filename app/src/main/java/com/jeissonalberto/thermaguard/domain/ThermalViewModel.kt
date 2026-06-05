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

    private var lastAutoActionTime = 0L
    private var autoThreshold = 42f

    init {
        observeHistory()
        startLiveReading()
    }

    private fun startLiveReading() {
        viewModelScope.launch {
            delay(1500L)
            while (true) {
                try {
                    val snapshot  = sensorRepo.readSnapshot()
                    val causes    = sensorRepo.analyzeHeatCauses(snapshot)
                    val diagnoses = sensorRepo.diagnoseComponents(snapshot)
                    val profile   = learningEngine.learn(snapshot)
                    val prediction= learningEngine.predictNextTemp()
                    val health    = learningEngine.computeBatteryHealthScore()
                    val hourly    = learningEngine.getHourlyProfile()
                    val tips      = learningEngine.generateSmartTips(profile, snapshot, prediction)

                    // Umbral dinámico: usa el del perfil aprendido si existe
                    val dynThreshold = profile.dynamicThreshold.takeIf { it > 35f } ?: 43f
                    autoThreshold = dynThreshold

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

                    executeAutoOptimization(snapshot)

                } catch (_: Exception) { }

                delay(30_000L)
            }
        }
    }

    private fun executeAutoOptimization(snap: ThermalSnapshot) {
        val now = System.currentTimeMillis()
        val cooldownMs = 5 * 60 * 1000L
        if (now - lastAutoActionTime < cooldownMs) return

        val level = snap.batteryTemp.toThermalLevel()
        if (level == ThermalLevel.NORMAL || level == ThermalLevel.WARM) return

        viewModelScope.launch {
            val actions = mutableListOf<AutoAction>()
            try {
                when (level) {
                    ThermalLevel.HOT -> {
                        val killed = optRepo.killBackgroundApps()
                        if (killed > 0) actions.add(AutoAction(
                            description = "Cerré $killed apps en segundo plano",
                            trigger = "Temperatura ${snap.batteryTemp}°C — HOT",
                            appsKilled = killed
                        ))
                    }
                    ThermalLevel.CRITICAL, ThermalLevel.EMERGENCY -> {
                        val killed = optRepo.killBackgroundApps()
                        val freed  = optRepo.freeRam()
                        if (killed > 0 || freed > 0) actions.add(AutoAction(
                            description = "Modo cooling: cerré $killed apps y liberé RAM",
                            trigger = "Temperatura ${snap.batteryTemp}°C — CRÍTICO",
                            appsKilled = killed
                        ))
                        if (snap.isCharging && snap.batteryTemp >= 46f) {
                            actions.add(AutoAction(
                                description = "⚠️ Cargando con calor crítico — desconecta el cargador",
                                trigger = "Cargando + ${snap.batteryTemp}°C"
                            ))
                        }
                    }
                    else -> {}
                }
            } catch (_: Exception) { }

            if (actions.isNotEmpty()) {
                lastAutoActionTime = now
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

    fun startMonitor() { _uiState.update { it.copy(isMonitoring = true) } }

    fun setAlertThreshold(t: Float) {
        autoThreshold = t
        _uiState.update { it.copy(alertThreshold = t) }
    }

    fun resetLearning() { learningEngine.reset() }

    fun clearAutoLog() { _uiState.update { it.copy(autoActionsLog = emptyList()) } }
}
