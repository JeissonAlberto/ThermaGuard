# ThermaGuard — Arquitectura implementada

## Alcance

ThermaGuard es una aplicación Android normal con Compose, un `ViewModel`, Room y un `CoroutineWorker` de WorkManager. Su alcance actual es observar señales públicas del sistema, conservar un historial local opcional y presentar decisiones explicables de cadencia y alerta. No controla el hardware.

## Flujo real

```text
BatteryManager + PowerManager
        │
        ├── ThermalMonitoringPolicy (dominio puro)
        │       ├── clasificación NOMINAL / ALERT / CRITICAL
        │       ├── sensor ausente explícito
        │       ├── pausa de persistencia con batería baja
        │       └── cadencia foreground explicable
        │
        ├── ThermalAlertNotifier
        │       └── transición persistente compartida entre UI y Worker
        │
        └── ThermalDiagnosticContract (solo lectura)
                │
                ├── valores disponibles del sistema
                ├── razones de ausencia
                └── conteo de historial/zona térmica

ThermalViewModel ──► Compose Dashboard / Alerts / Diagnosis
        │
        └── ThermalDatabase (Room, historial local con retención)

ThermalMonitorWorker ──► misma política + misma notificación + Room
```

## Contrato de diagnóstico

`ThermalDiagnosticContract` es un `StateFlow` inmutable expuesto por `ThermalViewModel`. No acepta comandos ni modifica configuraciones. Cada señal usa `DiagnosticValue`:

- `Available(value)`: valor leído de Android o estado local confirmado.
- `Unavailable(reason)`: no se recibió todavía, Android no la expone, el estado agregado no está disponible, Room falló o no hay zonas térmicas legibles.

El contrato incluye temperatura de batería, estado térmico agregado de Android, nivel/carga/voltaje/corriente de batería, cantidad de lecturas locales y cantidad de zonas térmicas legibles. Deliberadamente no incluye temperatura CPU/GPU, uso de procesos, frecuencia, potencia estimada ni un `risk score` inventado.

## Persistencia y privacidad

`ThermalSnapshot` guarda únicamente lecturas de batería disponibles y su timestamp. Room está en versión 2 con migración explícita desde la versión 1. La retención se limita a 6, 24 o 72 horas y el usuario puede borrar el historial. La medición opcional del costo propio solo conserva conteo y duración de lecturas foreground; se elimina junto con el historial.

No se sube telemetría térmica. El permiso `INTERNET` corresponde al comprobador de versiones existente, no al monitoreo.

## Scheduler y consumo

WorkManager ejecuta el monitoreo periódico con el intervalo del modo seleccionado y puede ser diferido por Android. En foreground, la política amplía la cadencia ante batería ≤15% sin carga o después de cinco muestras locales lentas. Esto cambia la frecuencia de comprobación, no la CPU/GPU, y no permite afirmar un ahorro porcentual.

## Límites

No hay root, escritura `sysfs`, governors, accesibilidad, overlay, estadísticas de uso, wakelocks permanentes, alarmas exactas, bypass de batería, terminación de procesos ni control de CPU/GPU. Las zonas térmicas del kernel son un detalle diagnóstico opcional; su ausencia es normal y se muestra explícitamente.
