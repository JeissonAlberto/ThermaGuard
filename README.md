<!-- build: v3.9.35 -->
# 🌡️ ThermaGuard

> **App Android de monitoreo térmico y optimización autónoma** · by [Jasol Group](https://site.zapia.com/t2ible74)

<p align="center">
  <img src="https://img.shields.io/badge/version-v3.7.0-orange?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Android-5.0%2B-green?style=for-the-badge&logo=android" />
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-blue?style=for-the-badge&logo=kotlin" />
  <img src="https://img.shields.io/badge/Motor-v5%20Moore-red?style=for-the-badge" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/privacidad-solo%20local-brightgreen?style=flat-square" alt="Privacy" />
  <img src="https://img.shields.io/badge/root-no%20requerido-blue?style=flat-square" alt="No Root" />
  <img src="https://img.shields.io/badge/publicidad-ninguna-orange?style=flat-square" alt="No Ads" />
  <img src="https://img.shields.io/badge/tama%C3%B1o-%3C10MB-lightgrey?style=flat-square" alt="Size" />
</p>

---

## ⬇️ Descarga

| Versión | Descripción | APK |
|---|---|---|
| **v3.7.0** *(última)* | Motor v5 — Ley de Moore | [Descargar](https://github.com/JeissonAlberto/ThermaGuard/releases/download/v3.5.0/ThermaGuard-v3.5.0.apk) |
| v3.3.1 | Bugfix gauge + filtros | [Descargar](https://github.com/JeissonAlberto/ThermaGuard/releases/download/v3.3.1/ThermaGuard-v3.3.1.apk) |
| v3.3.0 | Gauge velocímetro 240° | [Descargar](https://github.com/JeissonAlberto/ThermaGuard/releases/download/v3.3.0/ThermaGuard-v3.3.0.apk) |
| v3.2.0 | Modo Juego + Stats 24h | [Descargar](https://github.com/JeissonAlberto/ThermaGuard/releases/download/v3.2.0/ThermaGuard-v3.2.0.apk) |

---

## 🚀 ¿Qué es ThermaGuard?

ThermaGuard es una app Android nativa que **monitorea en tiempo real la temperatura** de tu dispositivo y toma decisiones automáticas para mantenerlo frío y eficiente. Sin root. Sin permisos especiales.

Construida sobre un **Motor de Aprendizaje Adaptativo** que aprende el comportamiento térmico específico de tu teléfono con el uso real.

---

## ✨ Funciones principales

### 🌡️ Dashboard
- **Gauge velocímetro 240°** con aguja animada y gradiente verde→amarillo→rojo
- **Temperatura en tiempo real** con predicción de subida
- **Barra de estado contextual** — "Todo bien", "Caliente", "Crítico"
- **Banners inteligentes** — Modo Juego, Carga Segura, Enfriando

### ⚙️ Motor v5 — Ley de Moore
- **Modelo P = V²·F** — estima la potencia térmica del procesador
- **Predicción pre-throttle** — avisa ~3 min antes de que el sistema frene el CPU
- **Barras de frecuencia por núcleo** — visualiza qué núcleos están al límite
- **Eficiencia energética** — ratio de trabajo útil vs calor generado
- **Regresión online de k** — aprende la constante térmica de tu chip con el uso real
- **Recomendaciones automáticas** — acciones según nivel de carga

### 📊 Estadísticas
- Gráfico de temperatura de las últimas **24 horas**
- **Ranking de apps** que más calientan el equipo
- **Perfil horario** — a qué horas te calienta más el teléfono
- **Score de salud de batería** con factores de desgaste

### 🎮 Modo Juego
- Detección automática (ML, Free Fire, Roblox, CoD, etc.)
- Sube el umbral de alerta a **46°C** para no interrumpir

### 🔋 Carga Segura
- Monitor de temperatura al cargar
- Alerta si la temperatura de carga es riesgosa

### 🔔 Alertas y Diagnóstico
- Historial de acciones automáticas
- Diagnóstico de componentes (CPU, GPU, modem, batería)
- Causas de calor identificadas en tiempo real
- Exportar historial a **CSV**

### 📱 Acerca de
- Créditos a **Jasol Group** e **Ing. Jeisson Alberto Sarmiento Cabrera**

---

## 📐 Motor de Aprendizaje Adaptativo

```
Versión   Innovación principal
─────────────────────────────────────────────────
v1        Lectura básica de temperatura
v2        EMA simple para suavizar lecturas
v3        Baseline personalizado por dispositivo  
v4        EMA adaptativo (α 0.08–0.45) + histéresis
v5        Modelo P=V²·F (Ley de Moore) + regresión online
```

El motor aprende y mejora con cada lectura. La constante térmica `k` de tu chip se calibra automáticamente con el historial real de tu teléfono.

---

## 🛠️ Stack técnico

- **Lenguaje:** Kotlin 100%
- **UI:** Jetpack Compose + Material 3
- **Arquitectura:** MVVM + Repository
- **Base de datos:** Room (SQLite)
- **Gráficos:** Canvas API nativo (sin librerías externas)
- **Servicios:** ForegroundService para monitoreo continuo
- **Mínimo Android:** 5.0 (API 21)

---

## 📁 Estructura del proyecto

```
app/src/main/java/com/jeissonalberto/thermaguard/
├── data/
│   ├── ThermalData.kt           # Modelos y entidades Room
│   ├── ThermalLearningEngine.kt # Motor v5 — cerebro de la app
│   ├── SensorRepository.kt      # Lectura de sensores del sistema
│   └── OptimizationRepository.kt
├── domain/
│   └── ThermalViewModel.kt      # Estado y lógica de negocio
├── service/
│   └── ThermalMonitorService.kt # Servicio en primer plano
└── ui/
    ├── DashboardScreen.kt       # Pantalla principal + Gauge Moore
    ├── StatsScreen.kt           # Estadísticas y gráficos 24h
    ├── DiagnosisScreen.kt       # Diagnóstico de componentes
    ├── AlertsScreen.kt          # Alertas y log de acciones
    └── AboutScreen.kt           # Créditos Jasol Group
```

---

## 📸 Capturas

> *Pantallas del Dashboard con gauge velocímetro, panel Motor v5 y estadísticas 24h.*

---

## 🗺️ Roadmap

- [x] Motor v1–v4: EMA adaptativo + histéresis
- [x] Motor v5: Ley de Moore P=V²·F
- [x] Gauge velocímetro 240° animado
- [x] Modo Juego automático
- [x] Estadísticas 24h + Ranking apps
- [ ] Soporte root (control directo de frecuencia CPU/GPU)
- [ ] Widget para pantalla de inicio
- [ ] Perfiles de temperatura personalizados
- [ ] Modo oscuro/claro manual
- [ ] Publicación en Google Play Store

---

## 📚 Documentación

- [Arquitectura del Motor v5](docs/ARCHITECTURE.md)
- [Política de Privacidad](docs/PRIVACY.md)
- [Ficha Play Store](docs/PLAY_STORE.md)
- [Landing Page](https://site.zapia.com/t2ible74)

---

## 🤝 Contribuir

¡Las contribuciones son bienvenidas! Sigue estos pasos para colaborar:

1. **Fork** este repositorio haciendo clic en el botón "Fork" de GitHub.
2. **Clona** tu fork en tu máquina local:
   ```bash
   git clone https://github.com/TU_USUARIO/ThermaGuard.git
   cd ThermaGuard
   ```
3. **Abre el proyecto en Android Studio** (versión Hedgehog o superior recomendada). Espera a que Gradle sincronice las dependencias.
4. **Crea una rama** para tu mejora o corrección:
   ```bash
   git checkout -b feature/mi-mejora
   ```
5. **Realiza tus cambios**, asegurándote de que el proyecto compila y los tests pasan.
6. **Haz commit** con un mensaje descriptivo:
   ```bash
   git commit -m "feat: descripción breve del cambio"
   ```
7. **Empuja** tu rama a GitHub:
   ```bash
   git push origin feature/mi-mejora
   ```
8. **Abre un Pull Request** desde tu fork hacia la rama `main` de este repositorio, describiendo qué cambiaste y por qué.

> Para cambios grandes, abre primero un **Issue** para discutir la propuesta.

---

## 👤 Autor

**Ing. Jeisson Alberto Sarmiento Cabrera**  
🇨🇴 Saravena, Arauca, Colombia  
📱 322 379 8725  
📧 jetfixer03@gmail.com  
🌐 [Jasol Group](https://site.zapia.com/t2ible74)

---

## 📄 Licencia

MIT License © 2025 Jasol Group

---

<p align="center">
  Hecho con ❤️ desde Saravena, Arauca, Colombia 🇨🇴<br/>
  <sub>Motor v5 — ThermaGuard v3.5.0</sub>
</p>
