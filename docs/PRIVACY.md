# Política de Privacidad — ThermaGuard

**Aplicación:** ThermaGuard  
**Desarrollador:** Jasol Group / Jeisson Alberto Sarmiento Cabrera  
**Versión:** 3.0 — agosto de 2026

## 1. Qué hace la aplicación

ThermaGuard observa señales térmicas y de batería que Android expone públicamente. Presenta el resultado en la aplicación, mantiene un historial local opcional y puede mostrar una notificación cuando detecta una transición real a un estado de alerta.

La aplicación no ofrece control de CPU/GPU, no requiere root y no afirma medir sensores que el dispositivo no expone.

## 2. Datos que pueden permanecer en el dispositivo

| Dato | Fuente | Finalidad | Almacenamiento |
|---|---|---|---|
| Temperatura de batería | `BatteryManager` | Clasificación y alerta | Room local |
| Nivel, carga, voltaje y corriente de batería | extras públicos de `BatteryManager` | Contexto del diagnóstico | Room local / memoria |
| Estado térmico agregado | `PowerManager` en Android compatible | Contexto y alerta | Memoria de la app |
| Timestamp y estado de lectura | aplicación | Historial y UI | Room local |
| Conteo y duración de lecturas | medición opt-in del usuario | Ajustar cadencia visible | Preferencias locales |
| Zonas térmicas legibles | `/sys/class/thermal` cuando el sistema lo permite | Detalle de diagnóstico | Memoria de la app |

Si una señal no está disponible, se conserva la ausencia y una razón; no se fabrica un valor sustituto. La temperatura de batería no representa necesariamente la de CPU o GPU.

## 3. Telemetría y red

No se envía telemetría térmica, historial, identificadores ni datos de batería a Internet por defecto. No hay cuenta, analítica, publicidad ni SDK de seguimiento configurados.

El permiso `INTERNET` está declarado para el comprobador de versiones existente. Esa comprobación es independiente del historial y del diagnóstico térmico. El permiso `ACCESS_NETWORK_STATE` permite a WorkManager aplicar restricciones de red a ese trabajo.

## 4. Consentimiento, retención y borrado

La medición del costo propio está desactivada inicialmente. El usuario puede autorizarla en Diagnóstico; solo guarda conteo y duración de lecturas foreground. Puede desactivarla y borrarla desde el mismo control.

El historial térmico local tiene una retención elegible de 6, 24 o 72 horas y se purga automáticamente al aplicar la retención. El botón **Borrar historial** elimina lecturas, metadatos de batería y el agregado de costo local. Desinstalar la aplicación elimina sus datos locales conforme al comportamiento de Android.

## 5. Permisos

- `POST_NOTIFICATIONS`: opcional, para alertas del sistema.
- `INTERNET`: comprobación de versión.
- `ACCESS_NETWORK_STATE`: restricciones de red del trabajo de actualización.

No se solicitan ubicación, contactos, cámara, micrófono, Bluetooth, overlay, accesibilidad, estadísticas de uso, escritura de ajustes, root ni permisos de control del sistema.

## 6. Seguridad y límites

Los datos del historial se guardan en el almacenamiento privado de la aplicación. Android puede aplazar el trabajo periódico y los fabricantes pueden ocultar sensores. ThermaGuard no sustituye las protecciones térmicas del sistema ni garantiza evitar daños o ahorrar un porcentaje de batería.

Para preguntas: **jeissonsarmiento@avidtel.com.co** — Colombia.
