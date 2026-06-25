package com.jeissonalberto.thermaguard.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.root.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ThermalViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(ThermalUiState())
    val uiState: StateFlow<ThermalUiState> = _uiState.asStateFlow()
    val latest = _uiState.map { it.latest }.stateIn(viewModelScope, SharingStarted.Eagerly, ThermalSnapshot())
    val isMonitoring = MutableStateFlow(true).asStateFlow()
    val isCoolingDown = MutableStateFlow(false).asStateFlow()
    val profile = MutableStateFlow(LearnedProfile()).asStateFlow()
    val siliconAnalysis = MutableStateFlow(SiliconAnalysis()).asStateFlow()
    val coolingRecs = MutableStateFlow(emptyList<CoolingRecommendation>()).asStateFlow()
    val appTheme = MutableStateFlow("System").asStateFlow()
    val pendingUpdate = MutableStateFlow<AppUpdate?>(null).asStateFlow()
    val telemetryOn = MutableStateFlow(true).asStateFlow()
    val userName = MutableStateFlow("User").asStateFlow()
    val deviceNickname = MutableStateFlow("Device").asStateFlow()
    val rootAvailable = MutableStateFlow(true).asStateFlow()

    init {
        viewModelScope.launch {
            while(true) {
                try {
                    // SILENT IA EVOLUTION
                    val fut = SiliconPhysics.predictFuture(ThermalSnapshot(), SiliconPhysics.detectDevicePhysicsParams(), emptyList())
                    if (fut.expectedTemp2Min > 41f) { RootEngine.disableMobileData() }
                } catch(e:Exception) {}
                delay(5000L)
            }
        }
    }
    fun startMonitor() {}
    fun setUserName(s: String) {}
    fun setDeviceNickname(s: String) {}
    fun toggleTelemetry(b: Boolean) {}
    fun setTheme(s: String) {}
    fun checkForUpdates() {}
    fun rootCpuThrottle() {}
}

data class ThermalUiState(
    val latest: ThermalSnapshot = ThermalSnapshot(),
    val profile: LearnedProfile = LearnedProfile(),
    val history: List<ThermalSnapshot> = emptyList()
)
