package com.jeissonalberto.thermaguard.data

object ProjectArchitect {
    fun generateStructure(version: String): Map<String, String> {
        return mapOf(
            "CORE" to "SiliconPhysicsEngine",
            "UI" to "Glassmorphism_CyberBlue",
            "LOGISTICS" to "Fleetbase_Integration",
            "VERSION" to version
        )
    }
}
