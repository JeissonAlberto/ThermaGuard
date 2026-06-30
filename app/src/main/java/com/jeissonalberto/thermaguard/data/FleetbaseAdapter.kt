package com.jeissonalberto.thermaguard.data

import okhttp3.OkHttpClient
import okhttp3.Request

class FleetbaseAdapter(private val apiKey: String) {
    private val client = OkHttpClient()
    private val baseUrl = "https://api.fleetbase.io/v1"

    fun trackThermalUnit(unitId: String) {
        val request = Request.Builder()
            .url("$baseUrl/trackers/$unitId")
            .header("Authorization", "Bearer $apiKey")
            .build()
        // Implementación de tracking para logística de dispositivos
    }
}
