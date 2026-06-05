package com.jeissonalberto.thermaguard.domain

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.service.ThermalMonitorService
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
    // Acciones automáticas ejecutadas por el motor
    val autoActionsLog: List<AutoAction> = emptyList(),
    val isMonitoring: Boolean = false,
    val alertThreshold: Float = 43f,
    val isLoading: Boolean = true
)

/** Registro de cada acción que el motor ejecutó automáticamente */
data class AutoAction(
    val timestamp: Long = System.currentTimeMillis(),
    val description: String,
    val trigger: String,          // qué la disparó
    val appsKilled: Int = 0
)

class ThermalViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorRepo     = SensorRepository(application)
    private val optRepo        = OptimizationRepository(application)
    private val learningEngine = ThermalLearningEngine(application)
    private val db             = ThermalDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(ThermalUiState())
    val uiState: StateFlow<ThermalUiState> = _uiState.asStateFlow()

    // Umbral de temperatura para que el motor actúe automáticamente
    private var autoActThreshold = 42f
    // Control de frecuencia: no actuar más de 1 vez cada 5 minutos
    private var lastAutoActionTime = 0L

    init {
        observeHistory()
        startLiveReading()
    }

    private fun startLiveReading() {
        viewModelScope.launch {
            delay(1500L)
            while (true) {
                try {
                    val snapshot    = sensorRepo.readSnapshot()
                    val causes      = sensorRepo.analyzeHeatCauses(snapshot)
                    val profile     = learningEngine.learn(snapshot)
                    val prediction  = learningEngine.predictNextTemp()
                    val health      = learningEngine.computeBatteryHealthScore()
                    val hourly      = learningEngine.getHourlyProfile()
                    val tips        = learningEngine.generateSmartTips(profile, snapshot, prediction)
                    val diagnoses   = sensorRepo.getComponentDiagnoses()
                    val dynThreshold = learningEngine.getDynamicThreshold()

                    // Actualizar umbral dinámico aprendido
                    autoActThreshold = dynThreshold

                    _uiState.update {
                        it.copy(
                            latest            = snapshot,
                            causes            = causes,
                            profile           = profile,
                            prediction        = prediction,
                            batteryHealth     = health,
                            hourlyProfile     = hourly,
                            smartTips         = tips,
                            componentDiagnoses= diagnoses,
                            alertThreshold    = dynThreshold,
                            isLoading         = false,
                            isMonitoring      = true
                        )
                    }

                    // Motor automático: actúa si supera el umbral aprendido
                    executeAutoOptimization(snapshot, causes)

                } catch (e: Exception) { /* continuar */ }

                delay(30_000L) // cada 30 segundos
            }
        }
    }

    /**
     * Motor de optimización automática.
     * Se activa cuando el aprendizaje detecta temperatura anormal.
     * Respeta un cooldown de 5 minutos entre acciones para no ser invasivo.
     */
    private fun executeAutoOptimization(snap: ThermalSnapshot, causes: List<HeatCause>) {
        val now = System.currentTimeMillis()
        val cooldownMs = 5 * 60 * 1000L
        if (now - lastAutoActionTime < cooldownMs) return

        val level = snap.batteryTemp.toThermalLevel()
        if (level == ThermalLevel.NORMAL || level == ThermalLevel.WARM) return

        viewModelScope.launch {
            val actions = mutableListOf<AutoAction>()

            when (level) {
                ThermalLevel.HOT -> {
                    val killed = optRepo.killBackgroundApps()
                    if (killed > 0) {
                        actions.add(AutoAction(
                            description = "Cerré $killed apps en segundo plano",
                            trigger     = "Temperatura ${snap.batteryTemp}°C (HOT)",
                            appsKilled  = killed
                        ))
                    }
                }
                ThermalLevel.CRITICAL, ThermalLevel.EMERGENCY -> {
                    val killed = optRepo.killBackgroundApps()
                    val freed  = optRepo.freeRam()
                    if (killed > 0 || freed > 0) {
                        actions.add(AutoAction(
                            description = "Modo cooling: cerré $killed apps y liberé RAM",
                            trigger     = "Temperatura ${snap.batteryTemp}°C (CRÍTICO)",
                            appsKilled  = killed
                        ))
                    }
                    // Acción adicional: si el aprendizaje detecta patrón de carga caliente
                    if (snap.isCharging && snap.batteryTemp >= 46f) {
                        actions.add(AutoAction(
                            description = "⚠️ Cargando con calor crítico — considera desconectar el cargador",
                            trigger     = "Cargando + ${snap.batteryTemp}°C"
                        ))
                    }
                }
                else -> { /* NORMAL / WARM: no actuar */ }
            }

            if (actions.isNotEmpty()) {
                lastAutoActionTime = now
                _uiState.update { state ->
                    val updated = (actions + state.autoActionsLog).take(20) // máx 20 entradas
                    state.copy(autoActionsLog = updated)
                }
            }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            db.snapshotDao().getAll().collect { rows ->
                _uiState.update { it.copy(history = rows.map { r -> r.toSnapshot() }.takeLast(120)) }
            }
        }
    }

    fun startMonitor() {
        _uiState.update { it.copy(isMonitoring = true) }
    }

    fun setAlertThreshold(t: Float) {
        autoActThreshold = t
        _uiState.update { it.copy(alertThreshold = t) }
    }

    fun resetLearning() { learningEngine.reset() }

    fun clearAutoLog() { _uiState.update { it.copy(autoActionsLog = emptyList()) } }
}
