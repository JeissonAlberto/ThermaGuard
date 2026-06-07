# Política de Privacidad — ThermaGuard

**Aplicación:** ThermaGuard  
**Desarrollador:** Jasol Group  
**Fecha de vigencia:** Junio 2025  
**Versión:** 1.0  

---

## 1. Introducción

Jasol Group, desarrollador de **ThermaGuard**, se compromete a proteger la privacidad de los usuarios. Esta política describe qué datos recopila la aplicación, cómo los usa, dónde los almacena y cuáles son sus derechos como usuario.

Al instalar y usar ThermaGuard, usted acepta las prácticas descritas en este documento.

---

## 2. Datos que se Recopilan

ThermaGuard recopila **únicamente** la siguiente información técnica del dispositivo, **exclusivamente de forma local**:

| Dato | Descripción | Uso |
|------|-------------|-----|
| **Temperatura del dispositivo** | Temperatura de CPU, GPU y batería (°C) | Análisis térmico con Motor v5 |
| **Uso de CPU** | Porcentaje de uso del procesador | Cálculo de potencia estimada |
| **Nivel de batería** | Porcentaje de carga actual | Evaluación de estrés de batería |
| **Nombre de la app activa** | Nombre del paquete de la app en primer plano | Identificar aplicaciones que generan calor excesivo |

> ⚠️ **Todos estos datos se procesan y almacenan únicamente en su dispositivo. Nunca se transmiten a servidores externos.**

---

## 3. Datos que NO se Recopilan

ThermaGuard **NO** recopila, accede ni procesa en ningún momento:

- ❌ Información personal (nombre, edad, género, documento de identidad)
- ❌ Contactos del dispositivo o agenda telefónica
- ❌ Ubicación geográfica (GPS o red)
- ❌ Fotos, videos ni archivos multimedia
- ❌ Audio, micrófono ni grabaciones de voz
- ❌ Mensajes, llamadas ni historial de comunicaciones
- ❌ Contraseñas, credenciales ni datos financieros
- ❌ Datos biométricos
- ❌ Identificadores publicitarios

---

## 4. Almacenamiento y Transmisión de Datos

- **Almacenamiento:** Toda la información recopilada se guarda **localmente en el dispositivo** mediante una base de datos Room (SQLite).
- **Transmisión:** ThermaGuard **no envía ningún dato a servidores**, la nube ni terceros.
- **Sincronización:** No existe sincronización de datos con servicios externos.
- **Retención:** Los datos se conservan hasta que el usuario los elimine manualmente o restablezca la aplicación.

---

## 5. Permisos Requeridos

ThermaGuard solicita los siguientes permisos del sistema. A continuación se explica para qué se usa cada uno:

### `FOREGROUND_SERVICE`
**Para qué:** Permite que ThermaGuard monitoree la temperatura en segundo plano mientras usa su teléfono.  
**Por qué es necesario:** Sin este permiso, el monitoreo se detendría cada vez que cierre la app.  
**Qué NO hace:** No accede a su pantalla, cámara ni datos personales.

### `PACKAGE_USAGE_STATS`
**Para qué:** Permite detectar qué aplicación está activa en primer plano en cada momento.  
**Por qué es necesario:** Identificar qué apps generan mayor calor para ofrecer diagnósticos precisos.  
**Qué NO hace:** No lee el contenido de las apps, solo su nombre de paquete.

### `POST_NOTIFICATIONS`
**Para qué:** Permite enviar alertas térmicas cuando la temperatura supera umbrales de riesgo.  
**Por qué es necesario:** Notificar al usuario cuando el dispositivo está en riesgo de sobrecalentamiento.  
**Qué NO hace:** No envía notificaciones publicitarias ni de terceros.

### `RECEIVE_BOOT_COMPLETED`
**Para qué:** Permite que ThermaGuard se inicie automáticamente cuando enciende el dispositivo.  
**Por qué es necesario:** Para comenzar el monitoreo térmico de forma continua desde el arranque.  
**Qué NO hace:** No ejecuta procesos adicionales ni modifica configuraciones del sistema.

---

## 6. Compartición de Datos con Terceros

ThermaGuard **no comparte ningún dato con terceros**, incluyendo:

- Empresas de publicidad
- Plataformas de analítica
- Redes sociales
- Otras aplicaciones

No utilizamos SDKs de publicidad, rastreo ni analítica de terceros.

---

## 7. Seguridad de los Datos

Dado que todos los datos permanecen en el dispositivo:

- Están protegidos por el sistema de sandboxing de Android
- Solo son accesibles por ThermaGuard
- No están expuestos a vulnerabilidades de red o servidores externos

---

## 8. Derechos del Usuario

Como usuario de ThermaGuard, usted tiene derecho a:

- **Acceder a sus datos:** Puede revisar su historial térmico directamente en la aplicación.
- **Eliminar sus datos:** Puede borrar todos los datos almacenados desde la sección de configuración de la app (opción "Restablecer datos" o "Limpiar historial").
- **Desinstalar la app:** Al desinstalar ThermaGuard, todos los datos locales son eliminados automáticamente por Android.

No es necesario crear una cuenta ni comunicarse con nosotros para ejercer estos derechos.

---

## 9. Menores de Edad

ThermaGuard está diseñado como una herramienta técnica para usuarios de todas las edades. No recopila datos personales, por lo que no existe riesgo diferenciado para menores. Sin embargo, recomendamos que los menores de edad usen la aplicación bajo supervisión de un adulto responsable.

---

## 10. Cambios a esta Política

Si realizamos cambios significativos a esta política de privacidad, lo notificaremos mediante:

- Una actualización en la Play Store con notas de versión
- Una notificación dentro de la propia aplicación

Le recomendamos revisar esta política periódicamente.

---

## 11. Contacto

Si tiene preguntas, inquietudes o solicitudes relacionadas con esta política de privacidad, puede contactarnos en:

**Correo electrónico:** jetfixer03@gmail.com  
**Desarrollador:** Jasol Group  
**País:** Colombia  

Responderemos a su consulta en un plazo máximo de **15 días hábiles**.

---

*Esta política de privacidad fue redactada conforme a las directrices de Google Play y la normativa colombiana de protección de datos personales (Ley 1581 de 2012).*

*© 2025 Jasol Group — ThermaGuard*
