# ThermaGuard

Monitor térmico para Android basado en señales que el sistema realmente expone. La aplicación observa la temperatura de batería publicada por `BatteryManager`, el estado térmico agregado de Android, metadatos de batería y, únicamente en la pantalla de diagnóstico, las zonas térmicas del kernel que sean legibles sin root.

## Estado implementado

- Una tubería compartida de política y alertas para la interfaz y `WorkManager`.
- Contrato de diagnóstico de solo lectura (`ThermalDiagnosticContract`): cada señal disponible conserva su valor real y cada señal ausente incluye una razón explícita.
- Historial local Room de lecturas de batería, con retención configurable de 6, 24 o 72 horas y borrado manual.
- Medición opcional y local del tiempo de trabajo propio del monitor. Solo guarda conteo y duración; no mide ni modifica CPU/GPU.
- Cadencia seleccionable: Ahorro, Equilibrado o Preventivo. La política puede ampliarla por batería baja o por costo local observado; explica el motivo en la interfaz.
- Alertas por transición real a `ALERT` o `CRITICAL`, condicionadas a la disponibilidad de notificaciones.
- Accesos para abrir ajustes Android. La app no cambia opciones protegidas.

La temperatura de batería no equivale a una temperatura de CPU o GPU. Android puede aplazar el trabajo periódico y algunos fabricantes no exponen sensores o zonas térmicas. La app muestra esas ausencias; no las rellena con ceros, valores simulados ni predicciones.

## Datos y permisos

El historial y la medición opcional se conservan localmente. No se envía telemetría térmica a Internet. `INTERNET` se usa únicamente para la comprobación de versión publicada por el actualizador existente. Los únicos permisos declarados son notificaciones, Internet y estado de red.

## Construir

Requisitos: JDK 17, Android SDK API 35 y acceso a dependencias Gradle.

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

El workflow de GitHub Actions ejecuta tests Debug/Release, lint, verificación de manifiesto, APK Debug y AAB Release. Un APK solo se considera disponible cuando un workflow exitoso publica y permite descargar el artefacto.

## Límites explícitos

ThermaGuard no requiere ni usa root, accesibilidad, overlay, estadísticas de uso, wakelocks permanentes, alarmas exactas, bypass de batería, escritura `sysfs`, control de CPU/GPU, terminación de procesos ni sensores privados. No afirma ahorro porcentual, diagnóstico de componentes, IA, aprendizaje automático ni protección garantizada del hardware.
