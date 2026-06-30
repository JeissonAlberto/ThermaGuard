package com.jeissonalberto.thermaguard.data

class FleetbaseAdapter(private val apiKey: String) {
    fun trackThermalUnit(unitId: String) {
        // Enlace lógico de logística (Versión Lite sin dependencias externas)
        val status = "UNIT_${unitId}_CONNECTED"
    }
}
