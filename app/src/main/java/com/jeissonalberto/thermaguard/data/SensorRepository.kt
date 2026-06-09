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

    // Cola de logs — últimas 500 entradas (circular)
    private val _sensorLogs = ArrayDeque<SensorLog>(500)
    val sensorLogs: List<SensorLog> get() = _sensorLogs.toList()

    private fun log(tag: String, source: String, field: String,
                    raw: String, parsed: String, unit: String = "", estimated: Boolean = false) {
        val entry = SensorLog(tag = tag, source = source, field = field,
            rawValue = raw, parsedValue = parsed, unit = unit, isEstimated = estimated)
        if (_sensorLogs.size >= 500) _sensorLogs.removeFirst()
        _sensorLogs.addLast(entry)
    }


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
cpuTemp          = run {
                val raw = allZones["cpu"] ?: 0f
                // Límite duro: ningún SoC de gama media reporta >65°C en uso normal
                // Si supera ese límite, la zona es de control del kernel → ignorar
                if (raw > 65f) {
                    // Fallback: usar GPU temp o batería * factor
                    val gpu = allZones["gpu"] ?: 0f
                    val bat = batteryTemp
                    when {
                        gpu in 20f..65f -> gpu + 2f   // GPU suele estar ~2°C más fría que CPU
                        bat in 20f..50f -> bat * 1.25f // batería ~80% de la temp del SoC
                        else -> 0f
                    }
                } else raw
            },
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

        // ── Emitir logs de trazabilidad ─────────────────────────────────
        val ts = System.currentTimeMillis()
        log("THERMAL", "BatteryManager.EXTRA_TEMPERATURE", "batteryTemp",
            "${(batteryTemp * 10).toInt()}", "${batteryTemp}", "°C")
        log("THERMAL", "/sys/class/thermal → cpu", "cpuTemp",
            "${(allZones["cpu"] ?: 0f)}", "${snap.cpuTemp}", "°C",
            estimated = snap.cpuTemp == 0f)
        log("THERMAL", "/sys/class/thermal → gpu", "gpuTemp",
            "${(allZones["gpu"] ?: 0f)}", "${snap.gpuTemp}", "°C",
            estimated = snap.gpuTemp == 0f)
        log("THERMAL", "/sys/class/thermal → skin", "skinTemp",
            "${(allZones["skin"] ?: allZones["surface"] ?: 0f)}", "${snap.skinTemp}", "°C",
            estimated = snap.skinTemp == 0f)
        log("THERMAL", "/sys/class/thermal → modem", "modemTemp",
            "${(allZones["modem"] ?: allZones["wlan"] ?: 0f)}", "${snap.modemTemp}", "°C",
            estimated = snap.modemTemp == 0f)
        log("THERMAL", "/sys/class/thermal → display", "displayTemp",
            "${(allZones["display"] ?: 0f)}", "${snap.displayTemp}", "°C",
            estimated = snap.displayTemp == 0f)
        log("CPU", "/proc/stat (EMA 800ms)", "cpuUsage",
            "raw_diff", "${snap.cpuUsage}",  "%",
            estimated = snap.cpuUsage == 15f)
        log("CPU", "/proc/stat cpu[0..N]", "perCoreUsage",
            "${snap.perCoreUsage.size} cores", snap.perCoreUsage.joinToString { "${it.toInt()}%" }, "%")
        log("BATTERY", "BatteryManager", "batteryLevel", "${snap.batteryLevel}", "${snap.batteryLevel}", "%")
        log("BATTERY", "BatteryManager", "isCharging", "${snap.isCharging}", "${snap.isCharging}")
        log("RAM", "ActivityManager.MemoryInfo", "ramFreeMb",
            "${snap.ramUsageMb}", "${snap.ramUsageMb}", "MB")
        log("SENSOR", "Settings.System.SCREEN_BRIGHTNESS", "brightness",
            "${snap.brightnessLevel}", "${(snap.brightnessLevel / 255f * 100).toInt()}", "%")
        log("SENSOR", "ConnectivityManager", "wifiActive", "${snap.wifiActive}", "${snap.wifiActive}")
        log("SENSOR", "BluetoothManager", "bluetoothActive", "${snap.bluetoothActive}", "${snap.bluetoothActive}")
        // Zonas raw completas
        allZones.filter { it.key.startsWith("raw_") }.forEach { (k, v) ->
            log("RAW", "/sys/class/thermal/${k.removePrefix("raw_")}", "zone_temp",
                "${(v * 1000).toInt()}", "${v}", "°C")
        }

        snap
    }

    // ============================================================
    //  ZONAS TERMICAS — lectura exhaustiva de /sys/class/thermal
    // ============================================================

    // Mapeo hardcoded para dispositivos con zonas genéricas (sin nombres descriptivos).
    // Derivado del análisis real del hardware — zonas 25-33 = CPU/SoC,
    // zonas 35-44 = GPU/Big cores, zonas 46-55 = Módem/NPU, zona 58/73 = skin/display.
    // Zonas 4,6,22,23,56,57,60,61,62 siempre en 0 — ignoradas.
    // Zonas 7,12,13,14 constantes en ~27°C — referencia ambiente.
    private val ZONE_OVERRIDE = mapOf(
        // CPU / SoC cluster (las más calientes del núcleo)
        25 to "cpu", 26 to "cpu", 27 to "cpu", 28 to "cpu", 29 to "cpu",
        30 to "cpu", 31 to "cpu", 32 to "cpu", 33 to "cpu",
        // GPU / Big cores
        35 to "gpu", 36 to "gpu", 37 to "gpu", 38 to "gpu", 39 to "gpu",
        40 to "gpu", 41 to "gpu", 42 to "gpu", 43 to "gpu", 44 to "gpu",
        // Módem / NPU / conectividad
        46 to "modem", 47 to "modem", 48 to "modem", 49 to "modem", 50 to "modem",
        51 to "modem", 52 to "modem", 53 to "modem", 54 to "modem", 55 to "modem",
        // Skin / display
        58 to "skin", 73 to "skin",
        70 to "display", 72 to "display",
        // Batería / carcasa
        74 to "battery_zone", 75 to "battery_zone", 76 to "battery_zone", 77 to "battery_zone",
        // Referencia ambiente — ignorar para temperaturas de componentes
        7 to "ambient", 12 to "ambient", 13 to "ambient", 14 to "ambient",
        // Siempre 0 — ignorar
        4 to "ignore", 6 to "ignore", 22 to "ignore", 23 to "ignore",
        56 to "ignore", 57 to "ignore", 60 to "ignore", 61 to "ignore", 62 to "ignore"
    )

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
                        if (!tempFile.exists()) return@forEach

                        val rawTemp = tempFile.readText().trim().toLongOrNull() ?: return@forEach
                        val temp    = if (rawTemp > 1000) rawTemp / 1000f else rawTemp.toFloat()

                        // Ignorar valores imposibles o siempre-cero
                        if (temp <= 0f || temp > 120f) return@forEach

                        // Extraer número de zona
                        val zoneNum = zone.name.removePrefix("thermal_zone").toIntOrNull()

                        // Clasificar:
                        // REGLA DE ORO: para CPU/GPU/Modem solo aceptar zonas del ZONE_OVERRIDE
                        // Las zonas clasificadas por nombre de tipo pueden ser zonas de control
                        // del kernel (ej: "cpu-1-0" con valor 75°C = límite de throttle, NO temp real)
                        val key = if (zoneNum != null && ZONE_OVERRIDE.containsKey(zoneNum)) {
                            val mappedKey = ZONE_OVERRIDE[zoneNum]!!
                            // Para zonas conocidas: rechazar valores > 65°C (son límites de throttle)
                            if (temp > 65f) {
                                result["raw_${zone.name}"] = temp  // guardar para diagnóstico
                                return@forEach  // ignorar para el cálculo
                            }
                            mappedKey
                        } else if (typeFile.exists()) {
                            val type = typeFile.readText().trim().lowercase()
                            val classified = classifyZone(type)
                            // Zonas NO mapeadas que sean CPU/GPU/Modem: ignorar si > 65°C
                            // (muy probable que sean zonas de control del kernel)
                            if (classified in listOf("cpu", "gpu", "modem") && temp > 65f) {
                                result["raw_${zone.name}"] = temp
                                return@forEach
                            }
                            classified
                        } else {
                            "unknown"
                        }

                        // Ignorar zonas marcadas explícitamente o desconocidas
                        if (key == "ignore" || key == "ambient" || key == "unknown") {
                            result["raw_${zone.name}"] = temp
                            return@forEach
                        }

                        // Para zonas múltiples del mismo tipo: guardar el PROMEDIO acumulado
                        val sumKey = "sum_$key"
                        val cntKey = "cnt_$key"
                        result[sumKey] = (result[sumKey] ?: 0f) + temp
                        result[cntKey] = (result[cntKey] ?: 0f) + 1f

                        // Guardar zona original para diagnóstico
                        result["raw_${zone.name}"] = temp

                    } catch (e: Exception) { }
                }
            // Calcular promedio simple de zonas válidas para cada componente
            listOf("cpu", "gpu", "modem", "skin", "display", "battery_zone", "board").forEach { key ->
                val sum = result.remove("sum_$key") ?: return@forEach
                val cnt = result.remove("cnt_$key") ?: return@forEach
                if (cnt > 0f) result[key] = sum / cnt
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
        // Status por temperatura Y uso — el que sea más grave gana
        val cpuStatusByTemp = when {
            cpuTemp >= 65f -> ComponentStatus.CRITICAL  // emergencia real
            cpuTemp >= 55f -> ComponentStatus.HOT       // intervenir ya
            cpuTemp >= 47f -> ComponentStatus.WARM      // ojo
            else           -> ComponentStatus.NORMAL    // <47°C = ok en este SoC
        }
        val cpuStatusByUsage = when {
            snapshot.cpuUsage >= 90f -> ComponentStatus.CRITICAL
            snapshot.cpuUsage >= 70f -> ComponentStatus.HOT
            snapshot.cpuUsage >= 45f -> ComponentStatus.WARM
            else                     -> ComponentStatus.NORMAL
        }
        // Tomar el peor de los dos (mayor ordinal = más grave)
        val cpuStatus = if (cpuStatusByTemp.ordinal >= cpuStatusByUsage.ordinal)
            cpuStatusByTemp else cpuStatusByUsage
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
                snapshot.gpuTemp >= 55f -> ComponentStatus.CRITICAL  // emergencia GPU
                snapshot.gpuTemp >= 48f -> ComponentStatus.HOT
                snapshot.gpuTemp >= 43f -> ComponentStatus.WARM      // 40-42°C = normal en uso
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
                snapshot.modemTemp >= 50f -> ComponentStatus.CRITICAL
                snapshot.modemTemp >= 45f -> ComponentStatus.HOT
                snapshot.modemTemp >= 39f -> ComponentStatus.WARM
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
                snapshot.displayTemp >= 52f -> ComponentStatus.CRITICAL
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

        // --- SKIN (zona 58/73 disponible en este hardware) ---
        val skinT = snapshot.skinTemp.takeIf { it > 10f }
        if (skinT != null) {
            val skinStatus = when {
                skinT >= 48f -> ComponentStatus.CRITICAL
                skinT >= 43f -> ComponentStatus.HOT
                skinT >= 39f -> ComponentStatus.WARM
                else         -> ComponentStatus.NORMAL
            }
            if (skinStatus != ComponentStatus.NORMAL) {
                diagnoses.add(ComponentDiagnosis(
                    component = ThermalComponent.SKIN,
                    temp      = skinT,
                    usagePct  = -1f,
                    status    = skinStatus,
                    cause     = when (skinStatus) {
                        ComponentStatus.CRITICAL -> "La carcasa está muy caliente al tacto."
                        ComponentStatus.HOT      -> "La carcasa está caliente. Considera poner el teléfono en reposo."
                        else                     -> "Temperatura superficial elevada."
                    },
                    advice    = when (skinStatus) {
                        ComponentStatus.CRITICAL -> "Quita la funda, deja el teléfono sobre superficie fría y cierra todas las apps."
                        ComponentStatus.HOT      -> "Reduce el brillo y cierra apps pesadas para enfriar la carcasa."
                        else                     -> "Monitorea la temperatura."
                    }
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
                    val parts = line.trim().split(" ").filter { it.isNotEmpty() }.drop(1).mapNotNull { it.toLongOrNull() }
                    val total = parts.sum()
                    val idle  = if (parts.size > 3) parts[3] else 0L
                    Pair(total, idle)
                }
        } catch (e: Exception) { emptyList() }
    }

    private var cpuEma: Float = -1f  // Suavizado exponencial (EMA) entre lecturas

    private suspend fun readCpuUsage(): Float = withContext(Dispatchers.IO) {
        // ── MÉTODO 1: /proc/stat — lectura diferencial con 800ms ──────────
        // El S22 (Exynos 2200, 8 núcleos) necesita más tiempo para captura
        // estable. La línea "cpu " es la suma de todos los núcleos.
        // idle está en columna [3], iowait en [4] — ambos son tiempo no activo.
        try {
            fun parseStat(): Pair<Long, Long>? {
                val line = File("/proc/stat").readLines()
                    .firstOrNull { it.startsWith("cpu ") } ?: return null
                val parts = line.trim().split(" ").filter { it.isNotEmpty() }.drop(1)
                    .mapNotNull { it.toLongOrNull() }
                if (parts.size < 5) return null
                val total = parts.sum()
                val idle  = parts[3] + parts[4]   // idle + iowait = tiempo real libre
                return Pair(total, idle)
            }
            val s1 = parseStat()
            if (s1 != null) {
                delay(800L)   // 800ms: captura estable en SoC de 8 núcleos
                val s2 = parseStat()
                if (s2 != null) {
                    val totalDiff = s2.first  - s1.first
                    val idleDiff  = s2.second - s1.second
                    if (totalDiff > 50) {   // mínimo de actividad para ser válido
                        val raw = ((totalDiff - idleDiff).toFloat() / totalDiff * 100f)
                            .coerceIn(0f, 100f)
                        // EMA α=0.3 — suaviza spikes sin ocultar subidas reales
                        cpuEma = if (cpuEma < 0f) raw else cpuEma * 0.7f + raw * 0.3f
                        return@withContext cpuEma
                    }
                }
            }
        } catch (_: Exception) {}

        // ── MÉTODO 2: /proc/loadavg — CORREGIDO para Android/Samsung ──────
        // loadavg ≠ % de CPU. En Linux/Android cuenta procesos en run-queue
        // incluyendo I/O wait. Factor corrector empírico para Samsung: /2.5
        // Un loadavg de 1.0 por núcleo = CPU saturado = 100%
        // Pero Android idle NORMAL tiene loadavg 4-7 → sin corrección = 80%
        try {
            val parts = File("/proc/loadavg").readText().trim().split(" ")
            val load1m = parts[0].toFloat()   // promedio 1 minuto
            val cores  = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            // Corrección: loadavg en Android incluye D-state (I/O), dividir por 2
            // para aproximar solo CPU. Cap al 95% para no mostrar saturación falsa.
            val corrected = (load1m / (cores * 2f) * 100f).coerceIn(1f, 95f)
            return@withContext corrected
        } catch (_: Exception) {}

        // ── MÉTODO 3: Fallback conservador ────────────────────────────────
        // Si no hay acceso a /proc, retornar valor neutro (no 80%)
        15f
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
