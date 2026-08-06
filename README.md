# ThermaGuard

Monitor térmico para Android, con una interfaz Compose orientada a mostrar lecturas reales del dispositivo.

## Estado actual

- La pantalla principal consulta la temperatura de batería publicada por Android mediante `BatteryManager`.
- La lectura se actualiza cada 5 segundos mientras la pantalla está activa.
- Si el dispositivo no expone la temperatura, la app muestra `—` y el estado `SENSOR UNAVAILABLE`; no genera valores simulados.
- Se muestran la fuente de la lectura, la hora de actualización y un umbral de alerta de 40 °C.
- Las lecturas disponibles se guardan en una base de datos Room local, aproximadamente una vez por minuto.
- La pantalla muestra las lecturas recientes persistidas; se conservan como máximo 24 horas y no se envían a ningún servicio externo.

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
- El historial es local y tiene una retención de 24 horas; aún no incluye gráficos, exportación ni sincronización.
- Las alertas del sistema y el diagnóstico avanzado aún requieren integración adicional.
- No se deben interpretar las lecturas como consejo médico ni como garantía de protección térmica.
