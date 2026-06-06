package com.jeissonalberto.thermaguard.data

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class SensorRepository(private val context: Context) {

    private val powerManager       = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val activityManager    = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val connectivityManager= context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // ============================================================
    //  SNAPSHOT PRINCIPAL
    // ============================================================

    suspend fun readSnapshot(): ThermalSnapshot = withContext(Dispatchers.IO) {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val batteryTemp  = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val batteryLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val isCharging   = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) == BatteryManager.BATTERY_STATUS_CHARGING

        val thermalStatus = readThermalStatus()
        val allZones      = readAllThermalZones()          // mapa completo tipo->temp
        val cpuUsage      = readCpuUsage()
        val perCoreUsage  = readPerCoreUsage()
        val topApp        = getTopApp()
        val topProcesses  = getTopProcesses()
        val wifiActive    = isWifiActive()
        val bluetoothActive = readBluetoothState()
        val brightness    = readBrightness()
        val ramUsage      = readRamUsage()

        val snap = ThermalSnapshot(
            batteryTemp      = batteryTemp,
            cpuTemp          = allZones["cpu"]  ?: 0f,
            gpuTemp          = allZones["gpu"]  ?: 0f,
            skinTemp         = allZones["skin"] ?: allZones["surface"] ?: 0f,
            boardTemp        = allZones["board"] ?: allZones["pcb"] ?: 0f,
            modemTemp        = allZones["modem"] ?: allZones["wlan"] ?: 0f,
            displayTemp      = allZones["display"] ?: allZones["ddr"] ?: 0f,
            cpuUsage         = cpuUsage,
            batteryLevel     = batteryLevel,
            isCharging       = isCharging,
            thermalStatus    = thermalStatus,
            topApp           = topApp,
            wifiActive       = wifiActive,
            bluetoothActive  = bluetoothActive,
            brightnessLevel  = brightness,
            ramUsageMb       = ramUsage
        )
        snap.allZones    = allZones
        snap.perCoreUsage = perCoreUsage
        snap.topProcesses = topProcesses
        snap
    }

    // ============================================================
    //  ZONAS TERMICAS — lectura exhaustiva de /sys/class/thermal
    // ============================================================

    fun readAllThermalZones(): Map<String, Float> {
        val result = mutableMapOf<String, Float>()
        return try {
            val thermalDir = File("/sys/class/thermal/")
            if (!thermalDir.exists()) return result

            thermalDir.listFiles { f -> f.name.startsWith("thermal_zone") }
                ?.forEach { zone ->
                    try {
                        val tempFile = File(zone, "temp")
                        val typeFile = File(zone, "type")
                        if (!tempFile.exists() || !typeFile.exists()) return@forEach

                        val rawTemp = tempFile.readText().trim().toLongOrNull() ?: return@forEach
                        val type    = typeFile.readText().trim().lowercase()
                        val temp    = if (rawTemp > 1000) rawTemp / 1000f else rawTemp.toFloat()

                        // Ignorar valores imposibles
                        if (temp < 0f || temp > 120f) return@forEach

                        val key = classifyZone(type)

                        // Para zonas multiples del mismo tipo, guardar la mayor
                        val existing = result[key] ?: 0f
                        if (temp > existing) result[key] = temp

                        // Guardar zona original tambien con nombre real (para diagnostico)
                        result["raw_${zone.name}"] = temp

                    } catch (e: Exception) { }
                }
            result
        } catch (e: Exception) { result }
    }

    private fun classifyZone(type: String): String = when {
        type.contains("cpu") || type.contains("processor") || type.contains("tsens_tz_sensor") -> "cpu"
        type.contains("gpu")                                    -> "gpu"
        type.contains("skin") || type.contains("surface")      -> "skin"
        type.contains("battery") || type.contains("batt")      -> "battery_zone"
        type.contains("board") || type.contains("pcb")         -> "board"
        type.contains("modem") || type.contains("mdm")         -> "modem"
        type.contains("wlan") || type.contains("wifi")         -> "wlan"
        type.contains("display") || type.contains("disp")      -> "display"
        type.contains("ddr") || type.contains("mem")           -> "ddr"
        type.contains("npu") || type.contains("dsp")           -> "npu"
        type.contains("charger") || type.contains("chg")       -> "charger"
        type.contains("pa") || type.contains("amplifier")      -> "pa"
        type.contains("camera") || type.contains("cam")        -> "camera"
        type.contains("usb")                                    -> "usb"
        else                                                    -> type.take(20)
    }

    // ============================================================
    //  DIAGNOSTICO DE COMPONENTES
    // ============================================================

    fun diagnoseComponents(snapshot: ThermalSnapshot): List<ComponentDiagnosis> {
        val diagnoses = mutableListOf<ComponentDiagnosis>()

        // --- CPU ---
        val cpuTemp = snapshot.cpuTemp.takeIf { it > 0f } ?: snapshot.batteryTemp
        val cpuStatus = when {
            snapshot.cpuUsage >= 90f -> ComponentStatus.CRITICAL
            snapshot.cpuUsage >= 70f -> ComponentStatus.HOT
            snapshot.cpuUsage >= 40f -> ComponentStatus.WARM
            else                     -> ComponentStatus.NORMAL
        }
        val cpuCause = buildCpuCause(snapshot)
        diagnoses.add(ComponentDiagnosis(
            component   = ThermalComponent.CPU,
            temp        = cpuTemp,
            usagePct    = snapshot.cpuUsage,
            status      = cpuStatus,
            cause       = cpuCause,
            advice      = cpuAdvice(cpuStatus, snapshot),
            perCore     = snapshot.perCoreUsage
        ))

        // --- GPU ---
        if (snapshot.gpuTemp > 0f) {
            val gpuStatus = when {
                snapshot.gpuTemp >= 55f -> ComponentStatus.CRITICAL
                snapshot.gpuTemp >= 48f -> ComponentStatus.HOT
                snapshot.gpuTemp >= 42f -> ComponentStatus.WARM
                else                    -> ComponentStatus.NORMAL
            }
            diagnoses.add(ComponentDiagnosis(
                component = ThermalComponent.GPU,
                temp      = snapshot.gpuTemp,
                usagePct  = -1f,
                status    = gpuStatus,
                cause     = if (gpuStatus != ComponentStatus.NORMAL)
                    "Posible uso intensivo de graficos: juegos, video 4K o apps de camara." else "Operacion normal.",
                advice    = if (gpuStatus == ComponentStatus.NORMAL) "Sin accion requerida."
                    else "Cierra juegos y apps de video. Reduce brillo de pantalla."
            ))
        }

        // --- BATERIA ---
        val batStatus = when {
            snapshot.batteryTemp >= 50f -> ComponentStatus.CRITICAL
            snapshot.batteryTemp >= 45f -> ComponentStatus.HOT
            snapshot.batteryTemp >= 40f -> ComponentStatus.WARM
            else                        -> ComponentStatus.NORMAL
        }
        val batCause = when {
            snapshot.isCharging && snapshot.batteryTemp >= 40f ->
                "Carga activa + uso simultaneo. La bateria genera calor al cargarse y al descargarse al mismo tiempo."
            snapshot.isCharging ->
                "Carga activa normal. Temperatura dentro de rango aceptable para carga."
            snapshot.batteryLevel < 15 ->
                "Bateria muy baja. El hardware trabaja mas duro con bateria critica."
            else -> "Descarga por uso normal."
        }
        diagnoses.add(ComponentDiagnosis(
            component = ThermalComponent.BATTERY,
            temp      = snapshot.batteryTemp,
            usagePct  = snapshot.batteryLevel.toFloat(),
            status    = batStatus,
            cause     = batCause,
            advice    = batAdvice(batStatus, snapshot)
        ))

        // --- MODEM / RADIO ---
        if (snapshot.modemTemp > 0f) {
            val modStatus = when {
                snapshot.modemTemp >= 55f -> ComponentStatus.CRITICAL
                snapshot.modemTemp >= 48f -> ComponentStatus.HOT
                snapshot.modemTemp >= 42f -> ComponentStatus.WARM
                else                      -> ComponentStatus.NORMAL
            }
            diagnoses.add(ComponentDiagnosis(
                component = ThermalComponent.MODEM,
                temp      = snapshot.modemTemp,
                usagePct  = -1f,
                status    = modStatus,
                cause     = "El modem 5G/4G genera calor significativo en zonas con señal debil o durante transferencias activas.",
                advice    = if (modStatus == ComponentStatus.NORMAL) "Señal y radio normales."
                    else "Activa modo avion 5 minutos si no necesitas datos. En zona de mala señal el modem trabaja mas."
            ))
        }

        // --- PANTALLA / DISPLAY ---
        if (snapshot.displayTemp > 0f) {
            val dispStatus = when {
                snapshot.displayTemp >= 45f -> ComponentStatus.HOT
                snapshot.displayTemp >= 38f -> ComponentStatus.WARM
                else                        -> ComponentStatus.NORMAL
            }
            diagnoses.add(ComponentDiagnosis(
                component = ThermalComponent.DISPLAY,
                temp      = snapshot.displayTemp,
                usagePct  = (snapshot.brightnessLevel * 100f / 255f),
                status    = dispStatus,
                cause     = "Pantalla AMOLED con brillo al ${(snapshot.brightnessLevel * 100 / 255)}%. Los paneles AMOLED generan calor notable al maximo brillo.",
                advice    = if (dispStatus == ComponentStatus.NORMAL) "Brillo en rango normal."
                    else "Reduce brillo al 50-60%. En interiores 30% es suficiente y reduce calor considerablemente."
            ))
        }

        // --- BOARD / PCB ---
        if (snapshot.boardTemp > 0f) {
            val brdStatus = when {
                snapshot.boardTemp >= 50f -> ComponentStatus.CRITICAL
                snapshot.boardTemp >= 44f -> ComponentStatus.HOT
                snapshot.boardTemp >= 38f -> ComponentStatus.WARM
                else                      -> ComponentStatus.NORMAL
            }
            diagnoses.add(ComponentDiagnosis(
                component = ThermalComponent.BOARD,
                temp      = snapshot.boardTemp,
                usagePct  = -1f,
                status    = brdStatus,
                cause     = "Temperatura de placa base. Refleja el calor acumulado de todos los componentes.",
                advice    = if (brdStatus == ComponentStatus.NORMAL) "Temperatura de placa normal."
                    else "Calor acumulado en placa. Apaga el telefono 10 minutos para disipar el calor del chasis."
            ))
        }

        // --- PROCESOS TOP ---
        if (snapshot.topProcesses.isNotEmpty()) {
            val hotProcess = snapshot.topProcesses.firstOrNull()
            if (hotProcess != null && snapshot.cpuUsage > 30f) {
                diagnoses.add(ComponentDiagnosis(
                    component = ThermalComponent.PROCESS,
                    temp      = snapshot.cpuTemp,
                    usagePct  = snapshot.cpuUsage,
                    status    = if (snapshot.cpuUsage > 70f) ComponentStatus.HOT else ComponentStatus.WARM,
                    cause     = "Proceso principal: '${hotProcess.name}' (PID ${hotProcess.pid}). ${hotProcess.description}",
                    advice    = "Si '${hotProcess.name}' no es esencial ahora mismo, forzar cierre en Ajustes > Apps.",
                    processes = snapshot.topProcesses
                ))
            }
        }

        return diagnoses.sortedByDescending { it.status.ordinal }
    }

    private fun buildCpuCause(snap: ThermalSnapshot): String {
        val sb = StringBuilder()
        if (snap.cpuUsage >= 70f) {
            sb.append("CPU al ${snap.cpuUsage.toInt()}% de capacidad. ")
            if (snap.topApp.isNotEmpty()) sb.append("App activa: '${snap.topApp}'. ")
            if (snap.perCoreUsage.isNotEmpty()) {
                val hotCores = snap.perCoreUsage.indices.filter { snap.perCoreUsage[it] > 80f }
                if (hotCores.isNotEmpty()) sb.append("Nucleos saturados: ${hotCores.map { "Core$it" }.joinToString(", ")}. ")
            }
        } else if (snap.cpuUsage >= 40f) {
            sb.append("Carga moderada de CPU (${snap.cpuUsage.toInt()}%). ")
            if (snap.topApp.isNotEmpty()) sb.append("Proceso activo: '${snap.topApp}'.")
        } else {
            sb.append("CPU en reposo relativo (${snap.cpuUsage.toInt()}%). El calor podria venir de otro componente.")
        }
        return sb.toString().trim()
    }

    private fun cpuAdvice(status: ComponentStatus, snap: ThermalSnapshot): String = when (status) {
        ComponentStatus.CRITICAL -> "Cierra inmediatamente todas las apps. Reinicia si el CPU no baja de 90% en 2 minutos. Posible proceso zombi o app maliciosa."
        ComponentStatus.HOT      -> "Cierra '${snap.topApp.ifEmpty { "apps pesadas" }}'. Desactiva sincronizacion automatica en Ajustes."
        ComponentStatus.WARM     -> "Monitorea. Cierra tabs de navegador y apps en background que no necesites."
        ComponentStatus.NORMAL   -> "CPU en rango normal. Sin accion requerida."
    }

    private fun batAdvice(status: ComponentStatus, snap: ThermalSnapshot): String = when (status) {
        ComponentStatus.CRITICAL -> "DESCONECTA el cargador YA. Apaga el telefono y dejalo enfriar en superficie fria. NO uses el telefono hasta que la bateria baje de 38C."
        ComponentStatus.HOT      -> if (snap.isCharging) "Desconecta el cargador. La bateria necesita enfriar antes de continuar la carga."
                                    else "Reduce la carga del sistema. Evita juegos o video hasta que la temperatura baje."
        ComponentStatus.WARM     -> if (snap.isCharging) "Temperatura de carga aceptable pero monitorea. Si sube de 43C desconecta."
                                    else "Temperatura elevada. Cierra apps exigentes."
        ComponentStatus.NORMAL   -> if (snap.isCharging) "Cargando normalmente. Temperatura saludable."
                                    else "Bateria en rango normal."
    }

    // ============================================================
    //  PROCESOS DEL SISTEMA
    // ============================================================

    private fun getTopProcesses(): List<ProcessInfo> {
        return try {
            val procs = activityManager.runningAppProcesses ?: return emptyList()
            procs.take(5).map { proc ->
                val name = proc.processName.split(".").last()
                val desc = when {
                    proc.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ->
                        "En primer plano (activo)"
                    proc.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE ->
                        "Visible / parcialmente activo"
                    proc.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE ->
                        "Servicio en background"
                    else -> "Background / cache"
                }
                ProcessInfo(
                    pid         = proc.pid,
                    name        = name,
                    fullName    = proc.processName,
                    importance  = proc.importance,
                    description = desc
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    // ============================================================
    //  LECTURA DE CPU POR NUCLEO
    // ============================================================

    private suspend fun readPerCoreUsage(): List<Float> = withContext(Dispatchers.IO) {
        try {
            val stats1 = readAllCoreStats()
            delay(300L)
            val stats2 = readAllCoreStats()
            stats1.zip(stats2).map { (s1, s2) ->
                val totalDiff = s2.first - s1.first
                val idleDiff  = s2.second - s1.second
                if (totalDiff <= 0) 0f
                else ((totalDiff - idleDiff).toFloat() / totalDiff.toFloat() * 100f).coerceIn(0f, 100f)
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun readAllCoreStats(): List<Pair<Long, Long>> {
        return try {
            File("/proc/stat").readLines()
                .filter { it.startsWith("cpu") && it.length > 4 && it[3].isDigit() }
                .map { line ->
                    val parts = line.trim().split("\\s+".toRegex()).drop(1).mapNotNull { it.toLongOrNull() }
                    val total = parts.sum()
                    val idle  = if (parts.size > 3) parts[3] else 0L
                    Pair(total, idle)
                }
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun readCpuUsage(): Float = withContext(Dispatchers.IO) {
        // Método 1: /proc/stat (funciona en la mayoría de dispositivos)
        try {
            val s1 = File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") }
            if (s1 != null) {
                delay(350L)
                val s2 = File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") } ?: return@withContext 0f
                val v1 = s1.trim().split(" ").filter { it.isNotEmpty() }.drop(1).mapNotNull { it.toLongOrNull() }
                val v2 = s2.trim().split(" ").filter { it.isNotEmpty() }.drop(1).mapNotNull { it.toLongOrNull() }
                if (v1.size >= 4 && v2.size >= 4) {
                    val totalDiff = v2.sum() - v1.sum()
                    val idleDiff  = v2[3] - v1[3]
                    if (totalDiff > 0) {
                        val pct = ((totalDiff - idleDiff).toFloat() / totalDiff.toFloat() * 100f).coerceIn(0f, 100f)
                        if (pct > 0f) return@withContext pct
                    }
                }
            }
        } catch (_: Exception) {}
        // Método 2: /proc/loadavg (más compatible con Samsung)
        try {
            val load = File("/proc/loadavg").readText().trim().split(" ")[0].toFloat()
            // load average de 1 minuto / núcleos = % aproximado
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            return@withContext (load / cores * 100f).coerceIn(0f, 100f)
        } catch (_: Exception) {}
        // Método 3: ActivityManager — uso de procesos
        try {
            val procs = activityManager.runningAppProcesses ?: return@withContext 0f
            val foreground = procs.count { it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE }
            val total = procs.size.coerceAtLeast(1)
            return@withContext (foreground.toFloat() / total * 80f).coerceIn(5f, 85f)
        } catch (_: Exception) {}
        0f
    }

    // ============================================================
    //  RESTO DE SENSORES
    // ============================================================

    private fun readThermalStatus(): Int = try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
            powerManager.currentThermalStatus else 0
    } catch (e: Exception) { 0 }

    private fun readBluetoothState(): Boolean = try {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled ?: false
    } catch (e: Exception) { false }

    private fun isWifiActive(): Boolean = try {
        val net = connectivityManager.activeNetwork ?: return false
        connectivityManager.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
    } catch (e: Exception) { false }

    private fun readBrightness(): Int = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 0)
    } catch (e: Exception) { 0 }

    private fun readRamUsage(): Int = try {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        // Devolver RAM LIBRE en MB (más útil para el usuario)
        (info.availMem / 1024 / 1024).toInt()
    } catch (e: Exception) { 0 }

    private fun getTopApp(): String = try {
        val ownPkg = context.packageName
        val systemPkgs = setOf("android","com.android","com.samsung","systemui","launcher","com.google.android.gms")
        activityManager.runningAppProcesses
            ?.filter { it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
            ?.firstOrNull { proc ->
                proc.processName != ownPkg &&
                systemPkgs.none { sys -> proc.processName.startsWith(sys) }
            }
            ?.processName
            ?.let { pkg ->
                // Intentar obtener nombre amigable de la app
                try {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (e: Exception) {
                    pkg.split(".").last().replaceFirstChar { it.uppercase() }
                }
            } ?: ""
    } catch (e: Exception) { "" }

    fun analyzeHeatCauses(snapshot: ThermalSnapshot): List<HeatCause> {
        val causes = mutableListOf<HeatCause>()
        if (snapshot.cpuUsage > 70f) causes.add(HeatCause("CPU saturada",
            "Uso al ${snapshot.cpuUsage.toInt()}%${if (snapshot.topApp.isNotEmpty()) " — app: ${snapshot.topApp}" else ""}",
            if (snapshot.cpuUsage > 90f) 5 else 3))
        if (snapshot.isCharging && snapshot.batteryTemp > 38f) causes.add(HeatCause(
            "Carga + uso simultaneo", "Genera calor doble: descarga y carga al mismo tiempo", 4))
        if (snapshot.batteryTemp > 40f) causes.add(HeatCause(
            "Bateria sobrecalentada", "${snapshot.batteryTemp}C — sobre limite seguro", 5))
        if (snapshot.brightnessLevel > 200) causes.add(HeatCause(
            "Brillo maximo", "Pantalla al maximo consume mas energia y genera calor", 2))
        if (snapshot.modemTemp > 45f) causes.add(HeatCause(
            "Modem caliente", "Radio 5G/4G elevado — posible señal debil o descarga de datos activa", 3))
        return causes.sortedByDescending { it.severity }
    }
}
