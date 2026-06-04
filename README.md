# 🌡️ ThermaGuard

**App Android para detectar y optimizar el calentamiento del teléfono**

![Min SDK](https://img.shields.io/badge/Min%20SDK-API%2026-blue)
![Language](https://img.shields.io/badge/Language-Kotlin-orange)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-green)

---

## ¿Qué hace?

ThermaGuard monitorea en tiempo real todos los sensores térmicos de tu dispositivo Android, detecta las causas de calentamiento y ejecuta optimizaciones automáticas o manuales para reducir la temperatura.

## Funcionalidades

- 🌡️ **Monitor en tiempo real** — temperatura de batería, CPU, GPU y superficie
- 📊 **Historial** — registro completo con estadísticas (promedio, máximo, eventos)
- ⚠️ **Detección de causas** — identifica qué está generando calor
- 🤖 **Modo automático** — optimiza solo cuando detecta temperatura alta
- 🕹️ **Modo manual** — control total de cada acción
- 🚨 **Alertas configurables** — notificación cuando supera el umbral que definas
- 🔄 **Foreground Service** — monitoreo continuo en segundo plano
- 📱 **Arranca con el sistema** — activo desde que enciendes el teléfono

## Arquitectura

```
Clean Architecture + MVVM + Repository Pattern
├── UI Layer: Jetpack Compose
├── Domain Layer: ViewModel + Use Cases
├── Data Layer: Repository + Room DB
└── Service Layer: Foreground Service
```

## Stack técnico

| Componente | Tecnología |
|---|---|
| Lenguaje | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Base de datos | Room |
| Concurrencia | Coroutines + Flow |
| Arquitectura | Clean Architecture + MVVM |
| Sensores | PowerManager + BatteryManager + /sys/class/thermal |

## Sensores utilizados

| Sensor | API | Requiere root |
|---|---|---|
| Temperatura batería | `BatteryManager` | No |
| Estado térmico sistema | `PowerManager.currentThermalStatus` | No |
| Zonas térmicas (CPU/GPU/skin) | `/sys/class/thermal/` | No |
| Uso de CPU | `/proc/stat` | No |
| Apps activas | `ActivityManager` | No |
| WiFi/BT activos | `ConnectivityManager` + `BluetoothManager` | No |

## Setup

1. Clona el repositorio
2. Abre en IntelliJ IDEA con plugin Android o Android Studio
3. Conecta tu dispositivo con **Depuración USB** activada
4. `Run > Run 'app'`

## Permisos requeridos

- `FOREGROUND_SERVICE` — monitoreo en background
- `PACKAGE_USAGE_STATS` — detectar app activa (requiere activación manual en ajustes)
- `POST_NOTIFICATIONS` — alertas de temperatura
- `RECEIVE_BOOT_COMPLETED` — arranque automático

## Roadmap

- [ ] Fase 2: Gráficas de temperatura en tiempo real (MPAndroidChart)
- [ ] Fase 3: ML para predicción de calentamiento (TFLite)
- [ ] Fase 4: Control de kernel con root (CPU governor)
- [ ] Fase 5: Widget en pantalla de inicio
- [ ] Fase 6: Publicación en Play Store

---

Desarrollado por JeissonAlberto · Saravena, Arauca, Colombia
