# ThermaGuard — Arquitectura Técnica

## 1. Visión General

ThermaGuard sigue el patrón **MVVM + Repository** con una interfaz construida en **Jetpack Compose** y persistencia local en **Room**. Esta separación garantiza que la lógica de negocio (Motor v5, motor de aprendizaje) sea completamente independiente de la capa de presentación.

```
┌────────────────────────────────────────────────────────┐
│                    Jetpack Compose UI                  │
│              (ThermalScreen, DashboardScreen)          │
└───────────────────────┬────────────────────────────────┘
                        │ observa StateFlow
┌───────────────────────▼────────────────────────────────┐
│               ThermalViewModel (MVVM)                  │
│    • Expone UiState via StateFlow/LiveData             │
│    • Orquesta Engine + Repository                      │
└──────────┬──────────────────────┬──────────────────────┘
           │                      │
┌──────────▼──────────┐  ┌───────▼──────────────────────┐
│  ThermalLearning    │  │      SensorRepository         │
│  Engine (Motor v5)  │  │  • BatteryManager             │
│  • Fórmula P=C·V²·F │  │  • UsageStatsManager          │
│  • EMA adaptativo   │  │  • ThermalZone / /sys/class   │
│  • Regresión online │  └───────┬──────────────────────┘
└──────────┬──────────┘          │
           │                     │
┌──────────▼─────────────────────▼──────────────────────┐
│                    Room Database                       │
│   ThermalSnapshot │ LearnedProfile │ ComponentDiagnosis│
└────────────────────────────────────────────────────────┘
```

---

## 2. Motor v5 — Fórmula de Potencia Térmica

### Fundamento físico

El Motor v5 modela la disipación de potencia dinámica de un procesador CMOS mediante la ecuación:

```
P = C · V² · F
```

| Símbolo | Significado | Rango típico |
|---------|-------------|--------------|
| `C`     | Constante de capacitancia efectiva del chip | 1.0 (normalizado) |
| `V`     | Proxy de voltaje (función del uso de CPU) | 0.6 – 1.0 |
| `F`     | Proxy de frecuencia (uso de CPU / 100) | 0.0 – 1.0 |
| `P`     | Potencia disipada estimada (escala 0–100) | 0 – 100 |

### Implementación: `computePowerFromCpu(cpuUsage: Float)`

```kotlin
fun computePowerFromCpu(cpuUsage: Float): Float {
    val f = cpuUsage / 100f          // proxy de frecuencia ∈ [0, 1]
    val v = 0.6f + 0.4f * f         // proxy de voltaje ∈ [0.6, 1.0]
    val power = v * v * f * 100f    // P = V² · F · 100
    return power.coerceIn(0f, 100f)
}
```

**Razonamiento del proxy de voltaje:**
Los procesadores modernos escalan su voltaje junto con la frecuencia (DVFS). Se modela linealmente: en reposo (f=0) el voltaje es 0.6 V normalizado; a plena carga (f=1) llega a 1.0 V normalizado.

**Ejemplos:**

| CPU Usage | f    | v    | P (aprox.) |
|-----------|------|------|------------|
| 0 %       | 0.00 | 0.60 | 0.0        |
| 25 %      | 0.25 | 0.70 | 12.3       |
| 50 %      | 0.50 | 0.80 | 32.0       |
| 75 %      | 0.75 | 0.90 | 60.8       |
| 100 %     | 1.00 | 1.00 | 100.0      |

---

## 3. ThermalLearningEngine

El motor de aprendizaje adapta el modelo al dispositivo específico a lo largo del tiempo.

### 3.1 EMA Adaptativo

Se usa una **Media Móvil Exponencial (EMA)** con factor `α` variable para suavizar las métricas:

```
EMA_t = α · X_t + (1 − α) · EMA_{t−1}
```

El factor α se ajusta dinámicamente según la varianza reciente:

| Condición de carga | α       |
|--------------------|---------|
| Carga estable      | 0.08    |
| Carga moderada     | ~0.20   |
| Carga volátil      | 0.45    |

Esto permite reaccionar rápido ante picos térmicos y ser conservador en estado estacionario.

### 3.2 Regresión Online para la Constante `k`

La constante de capacitancia `k` (análoga a `C` en la fórmula) se actualiza incrementalmente usando regresión de mínimos cuadrados online:

```
k_nuevo = k_actual + η · (P_medido − k_actual · V² · F)
```

Donde `η` es la tasa de aprendizaje. Esto permite que el modelo se personalice sin reentrenamiento completo.

### 3.3 Cálculo del Risk Score

```
riskScore = w1·(tempNorm) + w2·(powerNorm) + w3·(batteryStress)
```

| Componente      | Peso | Descripción |
|-----------------|------|-------------|
| `tempNorm`      | 0.45 | Temperatura normalizada respecto al umbral crítico del dispositivo |
| `powerNorm`     | 0.35 | Potencia estimada (Motor v5) normalizada 0–100 |
| `batteryStress` | 0.20 | Tensión de batería (temperatura + corriente de carga) |

El `riskScore` ∈ [0.0, 1.0] alimenta el sistema de alertas y el diagnóstico de componentes.

---

## 4. Flujo de Datos

```
Sensores del dispositivo
  │  BatteryManager (temp, nivel, voltaje)
  │  UsageStatsManager (app activa)
  │  /sys/class/thermal/thermal_zone* (temperatura CPU/GPU)
  ▼
SensorRepository
  │  Recolecta cada N segundos (configurable, default 5s)
  │  Emite SensorReading como Flow<SensorReading>
  ▼
ThermalLearningEngine
  │  computePowerFromCpu(cpuUsage)
  │  EMA adaptativo sobre temperatura/potencia
  │  Actualiza k por regresión online
  │  Calcula riskScore
  │  Genera ThermalSnapshot + ComponentDiagnosis
  ▼
ThermalViewModel
  │  Recibe ThermalState (StateFlow)
  │  Persiste snapshots en Room
  │  Dispara notificaciones si riskScore > umbral
  ▼
Jetpack Compose UI
     DashboardScreen, HistoryScreen, AlertsScreen
```

---

## 5. Entidades Room

### 5.1 `ThermalSnapshot` (18 campos)

Snapshot completo de estado térmico por cada ciclo de medición.

| # | Campo | Tipo | Descripción |
|---|-------|------|-------------|
| 1 | `id` | Long (PK, autoincrement) | Identificador único |
| 2 | `timestamp` | Long | Epoch ms |
| 3 | `cpuTemp` | Float | Temperatura CPU (°C) |
| 4 | `gpuTemp` | Float | Temperatura GPU estimada (°C) |
| 5 | `batteryTemp` | Float | Temperatura batería (°C) |
| 6 | `ambientTemp` | Float | Temperatura ambiente estimada (°C) |
| 7 | `cpuUsage` | Float | Uso de CPU (%) |
| 8 | `batteryLevel` | Int | Nivel de batería (%) |
| 9 | `batteryVoltage` | Int | Voltaje de batería (mV) |
| 10 | `isCharging` | Boolean | Estado de carga |
| 11 | `activeApp` | String | App activa en primer plano |
| 12 | `powerEstimate` | Float | P calculado por Motor v5 |
| 13 | `voltageProxy` | Float | V usado en fórmula |
| 14 | `frequencyProxy` | Float | F usado en fórmula |
| 15 | `emaTemperature` | Float | EMA temperatura suavizada |
| 16 | `emaPower` | Float | EMA potencia suavizada |
| 17 | `riskScore` | Float | Score de riesgo [0–1] |
| 18 | `sessionId` | String | UUID de sesión activa |

### 5.2 `LearnedProfile`

Perfil aprendido por dispositivo.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `deviceId` | String (PK) | Identificador del dispositivo |
| `kConstant` | Float | Constante C calibrada por regresión |
| `baselineTemp` | Float | Temperatura base del dispositivo |
| `criticalThreshold` | Float | Umbral crítico aprendido |
| `samplesCount` | Int | Muestras procesadas para calibración |
| `lastUpdated` | Long | Timestamp última actualización |

### 5.3 `ComponentDiagnosis`

Diagnóstico periódico de salud de componentes.

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | Long (PK) | Identificador |
| `timestamp` | Long | Epoch ms |
| `component` | String | CPU / GPU / BATTERY / SYSTEM |
| `healthScore` | Float | Score de salud [0–1] |
| `diagnosis` | String | Texto diagnóstico generado |
| `recommendation` | String | Acción recomendada |

---

## 6. Archivos Clave y Responsabilidades

| Archivo | Capa | Responsabilidad |
|---------|------|----------------|
| `ThermalViewModel.kt` | ViewModel | Orquesta Engine + Repository, expone StateFlow a UI |
| `ThermalLearningEngine.kt` | Domain | Motor v5, EMA, regresión, risk score |
| `SensorRepository.kt` | Data | Lectura de sensores del SO, emisión de Flow |
| `ThermalDatabase.kt` | Data | Inicialización Room, DAOs |
| `ThermalSnapshotDao.kt` | Data | CRUD y queries de snapshots |
| `LearnedProfileDao.kt` | Data | Lectura/escritura del perfil aprendido |
| `ComponentDiagnosisDao.kt` | Data | Registro de diagnósticos |
| `ThermalMonitorService.kt` | Service | ForegroundService, ciclo de medición |
| `DashboardScreen.kt` | UI | Pantalla principal Compose |
| `HistoryScreen.kt` | UI | Historial y gráficas Compose |
| `NotificationManager.kt` | Util | Gestión de alertas térmicas |

---

## 7. Dependencias Principales

```kotlin
// build.gradle (app)
implementation "androidx.room:room-runtime:2.6.x"
implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.7.x"
implementation "androidx.compose.ui:ui:1.6.x"
implementation "androidx.compose.material3:material3:1.2.x"
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.x"
```

---

*Documento generado: Junio 2025 — ThermaGuard by Jasol Group*
