# Política de Privacidad — ThermaGuard

**Aplicación:** ThermaGuard  
**Desarrollador:** Jasol Group / Jeisson Alberto Sarmiento Cabrera  
**Fecha de vigencia:** Junio 2026  
**Versión:** 2.0  

---

## 1. Introducción

Jasol Group ("nosotros", "nuestro") es el desarrollador de **ThermaGuard**, aplicación nativa de monitoreo térmico avanzado para Android. Esta Política de Privacidad describe de forma transparente qué datos recopila la aplicación, cómo los utiliza, dónde los almacena y los derechos que tienes como usuario.

Al instalar y usar ThermaGuard, aceptas las prácticas descritas en este documento.

---

## 2. Datos que Recopilamos

ThermaGuard recopila **exclusivamente** información técnica del dispositivo. No recopilamos nombre, correo electrónico, número de teléfono ni ningún dato de identificación personal vinculable a una persona real.

| Dato | Descripción | Finalidad |
|------|-------------|-----------|
| Temperatura (CPU/GPU/Batería) | Lecturas térmicas del hardware | Análisis térmico con SiliconPhysicsEngine |
| Uso de CPU y frecuencias | Porcentaje y frecuencia por núcleo | Gobernanza CPU/GPU y cálculo de potencia |
| Nivel y salud de batería | Porcentaje, voltaje, temperatura | Evaluación de estrés térmico |
| Modelo del dispositivo | Solo `Build.MODEL` del sistema | Ajuste de umbrales específicos para el SoC |
| Nombre de perfil de usuario | Nombre/apodo introducido por el usuario | Personalización del dashboard (local) |
| Telemetría técnica anónima | Versión app, snapshots térmicos | Mejora de algoritmos vía GitHub (sin ID) |

### Lo que NO recopilamos
- Datos de ubicación (GPS)
- Contactos, calendario, fotos o archivos del usuario
- Historial de navegación o apps instaladas
- Identificadores publicitarios (GAID)
- Datos biométricos

---

## 3. Uso de la Información

Los datos recopilados se usan **únicamente** para:

1. Calcular análisis térmicos en tiempo real mediante el SiliconPhysicsEngine (19 leyes físicas).
2. Aplicar configuraciones óptimas de CPU/GPU a través del CpuGpuGovernor (requiere root).
3. Enviar telemetría técnica anónima al repositorio GitHub del desarrollador para mejora continua del algoritmo.
4. Generar alertas, notificaciones y diagnósticos dentro de la app.
5. Mostrar el perfil de usuario en el dashboard (almacenado localmente en SharedPreferences).

---

## 4. Almacenamiento y Transferencia de Datos

| Dato | Dónde se almacena | Se envía a terceros |
|------|------------------|---------------------|
| Datos térmicos en tiempo real | Solo en memoria RAM — no persisten | No |
| Historial térmico (sesión) | Memoria de la app (caché local) | No |
| Telemetría anónima | GitHub (repositorio del desarrollador) | Solo a GitHub |
| Nombre/apodo de perfil | SharedPreferences del dispositivo | No |

**Telemetría y GitHub:** La app puede enviar snapshots técnicos anónimos (temperatura, voltaje, frecuencias CPU) al repositorio GitHub del desarrollador. Estos datos no contienen ningún identificador personal. El servicio de GitHub se rige por la [Política de Privacidad de GitHub](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

---

## 5. Permisos de Android

ThermaGuard solicita los siguientes permisos del sistema:

| Permiso | Motivo |
|---------|--------|
| `FOREGROUND_SERVICE` | Mantener monitoreo activo en segundo plano |
| `POST_NOTIFICATIONS` | Enviar alertas térmicas al usuario |
| `RECEIVE_BOOT_COMPLETED` | Reiniciar monitoreo al encender el dispositivo |
| `PACKAGE_USAGE_STATS` | Identificar apps con mayor carga térmica |
| `INTERNET` / `NETWORK_STATE` | Enviar telemetría anónima y verificar actualizaciones |
| `WAKE_LOCK` | Evitar que el servicio se suspenda durante alertas críticas |
| `KILL_BACKGROUND_PROCESSES` | Aliviar carga térmica en emergencias (requiere root) |
| `WRITE_SETTINGS` | Ajustar configuración de rendimiento del sistema |
| `READ_PHONE_STATE` | Leer modelo del dispositivo para calibración |
| `HIGH_SAMPLING_RATE_SENSORS` | Muestreo de sensores térmicos a alta frecuencia |
| `DUMP` / `READ_LOGS` | Diagnóstico de hardware (requiere root/ADB) |
| `DEVICE_POWER` | Control avanzado de energía del SoC (requiere root) |

Los permisos marcados como *requiere root* solo funcionan en dispositivos con acceso root concedido. En dispositivos sin root, esas funciones quedan deshabilitadas automáticamente.

---

## 6. Uso de Inteligencia Artificial

ThermaGuard utiliza algoritmos propios de análisis térmico basados en leyes físicas reales (Fourier, Newton, Stefan-Boltzmann, Arrhenius, etc.) y heurísticas de hardware. **No se hacen promesas de precisión absoluta.** Los resultados son estimaciones orientativas para el monitoreo del dispositivo y no sustituyen el diagnóstico técnico profesional.

La app **no utiliza** modelos de IA de terceros (GPT, Gemini, etc.) ni envía datos a servicios de inteligencia artificial externos.

---

## 7. Menores de Edad

ThermaGuard no está dirigida a menores de 13 años y no recopila información de menores de forma intencionada.

---

## 8. Seguridad

Implementamos medidas técnicas razonables para proteger la información almacenada localmente. La telemetría enviada a GitHub no contiene identificadores personales y se transmite mediante HTTPS.

---

## 9. Tus Derechos

Puedes en cualquier momento:
- **Eliminar tu perfil local:** Ajustes → Perfil → Eliminar datos.
- **Desactivar la telemetría:** Ajustes → Telemetría → Desactivar.
- **Desinstalar la app**, lo cual elimina todos los datos locales del dispositivo.

---

## 10. Cambios en esta Política

Notificaremos cambios significativos mediante una actualización de la app en Google Play. La fecha de vigencia al inicio de este documento refleja la versión actual.

---

## 11. Contacto

Si tienes preguntas sobre esta política:

**Jasol Group / ThermaGuard**  
Correo: jeissonsarmiento@avidtel.com.co  
País: Colombia  

---

*Esta política fue elaborada con base en los requisitos de Google Play Data Safety, la DMCA (Sección 512) y las directrices de la FTC para claims de IA.*
