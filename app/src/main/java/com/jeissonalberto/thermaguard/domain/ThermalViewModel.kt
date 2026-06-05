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
    val isMonitoring: Boolean = false,
    val autoMode: Boolean = false,
    val alertThreshold: Float = 43f
)

class ThermalViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorRepo = SensorRepository(application)
    private val optimizationRepo = OptimizationRepository(application)
    private val db = ThermalDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(ThermalUiState())
    val uiState: StateFlow<ThermalUiState> = _uiState.asStateFlow()

    init {
        observeHistory()
        startLiveReading()
    }

    private fun startLiveReading() {
        viewModelScope.launch {
            while (true) {
                try {
                    val snapshot = sensorRepo.readSnapshot()
                    val causes = sensorRepo.analyzeHeatCauses(snapshot)
                    val plan = optimizationRepo.buildOptimizationPlan(snapshot)

                    _uiState.update { state ->
                        state.copy(
                            latest = snapshot,
                            causes = causes,
                            optimizationPlan = plan
                        )
                    }

                    if (_uiState.value.autoMode && snapshot.batteryTemp >= _uiState.value.alertThreshold) {
                        executeAutoOptimization(plan)
                    }

                } catch (e: Exception) {
                    // Error leyendo sensores
                }
                // Refresco UI cada 10 segundos - mucho mas eficiente
                delay(10_000L)
            }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            db.thermalDao().getHistory(200).collect { history ->
                _uiState.update { it.copy(history = history) }
            }
        }
    }

    fun toggleMonitorService() {
        val context = getApplication<Application>()
        val intent = Intent(context, ThermalMonitorService::class.java)
        if (ThermalMonitorService.isRunning) {
            intent.action = ThermalMonitorService.ACTION_STOP
            context.startService(intent)
            _uiState.update { it.copy(isMonitoring = false) }
        } else {
            context.startForegroundService(intent)
            _uiState.update { it.copy(isMonitoring = true) }
        }
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
                is OptimizationAction.KillBackgroundApps -> optimizationRepo.killBackgroundApps()
                else -> { }
            }
        }
    }
}
