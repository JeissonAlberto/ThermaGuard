package com.jeissonalberto.thermaguard.domain
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jeissonalberto.thermaguard.data.*
import com.jeissonalberto.thermaguard.root.RootEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ThermalViewModel(application: Application) : AndroidViewModel(application) {
    val uiState = MutableStateFlow(ThermalUiState()).asStateFlow()
    val latest = uiState.map { it.latest }.stateIn(viewModelScope, SharingStarted.Eagerly, ThermalSnapshot())
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
                    val snap = ThermalSnapshot(batteryTemp = 35f) // Dummy para que no falle el repo
                    val params = SiliconPhysics.detectDevicePhysicsParams()
                    val future = SiliconPhysics.predictFuture(snap, params, emptyList())
                    if (future.expectedTemp2Min > 41f) { RootEngine.setCpuMaxFreq(RootEngine.CpuLevel.THROTTLE) }
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
data class ThermalUiState(val latest: ThermalSnapshot = ThermalSnapshot(), val history: List<ThermalSnapshot> = emptyList())
