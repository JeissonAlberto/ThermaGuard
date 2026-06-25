package com.jeissonalberto.thermaguard.domain
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.jeissonalberto.thermaguard.data.*

class ThermalViewModel(application: Application) : AndroidViewModel(application) {
    // Stubs para que compile MainActivity
    val latest = kotlinx.coroutines.flow.MutableStateFlow(ThermalSnapshot())
    val uiState = kotlinx.coroutines.flow.MutableStateFlow(0)
    val coolingRecs = kotlinx.coroutines.flow.MutableStateFlow(emptyList<String>())
    val isMonitoring = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isCoolingDown = kotlinx.coroutines.flow.MutableStateFlow(false)
    val profile = kotlinx.coroutines.flow.MutableStateFlow("")
    val siliconAnalysis = kotlinx.coroutines.flow.MutableStateFlow("")
    val appTheme = kotlinx.coroutines.flow.MutableStateFlow("System")
    val pendingUpdate = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val telemetryOn = kotlinx.coroutines.flow.MutableStateFlow(true)
    val userName = kotlinx.coroutines.flow.MutableStateFlow("User")
    val deviceNickname = kotlinx.coroutines.flow.MutableStateFlow("Device")
    val rootAvailable = kotlinx.coroutines.flow.MutableStateFlow(true)
    
    fun startMonitor() {}
    fun setUserName(s: String) {}
    fun setDeviceNickname(s: String) {}
    fun toggleTelemetry(b: Boolean) {}
    fun setTheme(s: String) {}
    fun checkForUpdates() {}
    fun rootCpuThrottle() {}
}
