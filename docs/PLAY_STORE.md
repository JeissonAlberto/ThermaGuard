# ThermaGuard — Ficha de Play Store verificada

## Descripción corta

```text
Monitor térmico local con lecturas reales de Android
```

## Descripción completa

ThermaGuard muestra señales térmicas y de batería que Android realmente expone, sin root y sin modificar el sistema.

### Qué puedes comprobar

- Temperatura real de batería cuando `BatteryManager` la publica.
- Estado térmico agregado de Android en dispositivos compatibles.
- Nivel, carga, voltaje y corriente cuando el fabricante los expone.
- Historial local con retención configurable y borrado manual.
- Zonas térmicas legibles desde la pantalla de diagnóstico, cuando están disponibles.
- Alertas cuando una lectura real entra en `ALERT` o `CRITICAL`.
- Cadencia explicable en modos Ahorro, Equilibrado y Preventivo.
- Medición opcional y local del tiempo que tarda la propia lectura foreground.

### Ausencias explícitas

Android no ofrece una API pública uniforme para temperatura de CPU/GPU, uso de otras aplicaciones o potencia térmica. Cuando una señal no existe, ThermaGuard la marca como no disponible y explica la razón. No muestra valores simulados ni promete precisión de laboratorio.

### Privacidad

El historial y la medición opcional se quedan en el dispositivo. No se envía telemetría térmica ni se requieren cuentas. El permiso de Internet solo se usa para comprobar la versión publicada por el actualizador existente.

### Permisos

- `POST_NOTIFICATIONS`, si quieres recibir alertas del sistema.
- `INTERNET`, para comprobar versiones.
- `ACCESS_NETWORK_STATE`, para restricciones del actualizador.

ThermaGuard no usa root, accesibilidad, overlay, wakelocks permanentes, alarmas exactas, bypass de batería, escritura `sysfs`, control CPU/GPU ni terminación de procesos.

### Limitaciones

WorkManager puede ser aplazado por Android. La disponibilidad depende del fabricante y del modelo. La app informa sobre las señales observadas; no controla el hardware, no garantiza ahorro porcentual y no sustituye las protecciones térmicas del sistema.

## Capturas que deben representar la app real

1. Inicio con temperatura de batería o ausencia explícita.
2. Alertas con el estado agregado de Android y la tendencia local.
3. Diagnóstico con contrato de señales disponibles/no disponibles.
4. Retención, borrado y consentimiento de medición local.

*No publicar capturas o textos de CPU/GPU, IA, aprendizaje, governors, potencia estimada o riesgo unificado hasta que exista una implementación demostrable y pruebas de dispositivo.*
