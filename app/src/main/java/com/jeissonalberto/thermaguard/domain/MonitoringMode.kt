package com.jeissonalberto.thermaguard.domain

/** User-controlled cadence for background thermal monitoring. */
enum class MonitoringMode(
    val label: String,
    val intervalMinutes: Long,
    val description: String
) {
    SAVER("AHORRO", 60L, "Menos ejecuciones; conserva alertas cuando Android lo permita."),
    BALANCED("EQUILIBRADO", 30L, "Vigilancia periódica con consumo moderado."),
    PREVENTIVE("PREVENTIVO", 15L, "Monitoreo más frecuente únicamente mientras carga.");

    companion object {
        const val PREFS_NAME = "therma_monitoring_preferences"
        const val MODE_KEY = "monitoring_mode"

        fun fromStored(value: String?): MonitoringMode =
            values().firstOrNull { it.name == value } ?: BALANCED
    }
}

/** Low battery pauses local persistence, while thermal alerts remain evaluated. */
internal fun shouldPauseNonEssentialWork(levelPercent: Int?, isCharging: Boolean?): Boolean =
    levelPercent != null && levelPercent <= 15 && isCharging != true
