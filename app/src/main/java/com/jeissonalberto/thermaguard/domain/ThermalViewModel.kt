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
    val optimizationPlan: List<OptimizationAction> = emptyList(),
    val smartTips: List<SmartTip> = emptyList(),
    val profile: LearnedProfile? = null,
    val isMonitoring: Boolean = false,
    val autoMode: Boolean = false,
    val alertThreshold: Float = 43f,
    val isLoading: Boolean = true
)

class ThermalViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorRepo = SensorRepository(application)
    private val optimizationRepo = OptimizationRepository(application)
    private val learningEngine = ThermalLearningEngine(application)
    private val db = ThermalDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(ThermalUiState())
    val uiState: StateFlow<ThermalUiState> = _uiState.asStateFlow()

    init {
        observeHistory()
        startLiveReading()
    }

    private fun startLiveReading() {
        viewModelScope.launch {
            delay(1500L)
            while (true) {
                try {
                    val snapshot = sensorRepo.readSnapshot()
                    val causes = sensorRepo.analyzeHeatCauses(snapshot)
                    val plan = optimizationRepo.buildOptimizationPlan(snapshot)
                    val profile = learningEngine.learn(snapshot)
                    val tips = learningEngine.generateSmartTips(profile, snapshot)

                    _uiState.update { state ->
                        state.copy(
                            latest = snapshot,
                            causes = causes,
                            optimizationPlan = plan,
                            smartTips = tips,
                            profile = profile,
                            isLoading = false
                        )
                    }

                    if (_uiState.value.autoMode && snapshot.batteryTemp >= _uiState.value.alertThreshold) {
                        executeAutoOptimization(plan)
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isLoading = false) }
                }
                delay(10_000L)
            }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            try {
                db.thermalDao().getHistory(200).collect { history ->
                    _uiState.update { it.copy(history = history) }
                }
            } catch (e: Exception) { }
        }
    }

    /** Llamado desde MainActivity tras conceder permisos */
    fun startMonitor() {
        val context = getApplication<Application>()
        try {
            context.startForegroundService(Intent(context, ThermalMonitorService::class.java))
            _uiState.update { it.copy(isMonitoring = true) }
        } catch (e: Exception) { }
    }

    fun toggleMonitorService() {
        val context = getApplication<Application>()
        val intent = Intent(context, ThermalMonitorService::class.java)
        try {
            if (ThermalMonitorService.isRunning) {
                intent.action = ThermalMonitorService.ACTION_STOP
                context.startService(intent)
                _uiState.update { it.copy(isMonitoring = false) }
            } else {
                context.startForegroundService(intent)
                _uiState.update { it.copy(isMonitoring = true) }
            }
        } catch (e: Exception) { }
    }

    fun toggleAutoMode() {
        _uiState.update { it.copy(autoMode = !it.autoMode) }
    }

    fun setAlertThreshold(temp: Float) {
        _uiState.update { it.copy(alertThreshold = temp) }
    }

    private suspend fun executeAutoOptimization(plan: List<OptimizationAction>) {
        plan.forEach { action ->
            when (action) {
                is OptimizationAction.KillBackgroundApps -> {
                    try { optimizationRepo.killBackgroundApps() } catch (e: Exception) { }
                }
                else -> { }
            }
        }
    }
}
