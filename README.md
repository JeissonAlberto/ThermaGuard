# ThermaGuard

Monitor térmico para Android, con una interfaz Compose orientada a mostrar lecturas reales del dispositivo.

## Estado actual

- La pantalla principal consulta la temperatura de batería publicada por Android mediante `BatteryManager`.
- La lectura automática en pantalla respeta el modo elegido: cada 5 minutos en Ahorro, 2 minutos en Equilibrado y 30 segundos en Preventivo.
- Con batería de 15% o menos y sin carga, la actualización automática se extiende a 15 minutos para evitar actividad innecesaria; la evaluación térmica y las alertas no se desactivan. La actualización manual sigue siendo inmediata.
- Si el dispositivo no expone la temperatura, la app muestra `—` y el estado `SENSOR UNAVAILABLE`; no genera valores simulados.
- Se muestran la fuente de la lectura, la hora de actualización y un umbral de alerta de 40 °C.
- Las lecturas disponibles se guardan en una base de datos Room local, aproximadamente una vez por minuto.
- La pantalla muestra las lecturas recientes persistidas; se conservan como máximo 24 horas y no se envían a ningún servicio externo.
- La sección de alertas muestra el estado actual, la tendencia local y un gráfico accesible con mínimo, máximo y umbral.
- La sección de diagnóstico resume las señales comprobables: sensor, temperatura, batería, carga, actualización e historial; también lista las zonas térmicas crudas del kernel cuando Android permite leerlas.

La disponibilidad y precisión dependen del fabricante y del modelo del dispositivo. La temperatura de batería no equivale necesariamente a la temperatura de CPU o GPU.

## Construir localmente

Requisitos: JDK 17, Android SDK con API 35 y acceso a las dependencias de Gradle.

```bash
./gradlew assembleDebug
```

El APK de depuración se genera en `app/build/outputs/apk/debug/app-debug.apk` cuando el build termina correctamente. Este repositorio no incluye un APK precompilado.

## Validación continua

El workflow de GitHub Actions ejecuta `assembleDebug` y `bundleRelease` en cada push a `main`. Los artefactos solo deben considerarse disponibles después de que una ejecución exitosa los publique.

## Limitaciones conocidas

- La lectura implementada es la temperatura de batería proporcionada por el sistema; no se asume acceso root ni se inventan sensores CPU/GPU.
- El historial es local, tiene una retención de 24 horas e incluye una tendencia visual dentro de la app; aún no incluye exportación ni sincronización.
- Las alertas se muestran dentro de la app y, cuando el usuario concede el permiso de notificaciones, también como notificación del sistema al entrar en estado ALERT o CRITICAL. La app programa comprobaciones térmicas reales en segundo plano con WorkManager cada 15 minutos como mínimo; Android puede aplazar cada ejecución según batería y sus políticas.
- El diagnóstico interpreta únicamente las señales expuestas por Android y el historial local; no mide CPU/GPU.
- No se deben interpretar las lecturas como consejo médico ni como garantía de protección térmica.

