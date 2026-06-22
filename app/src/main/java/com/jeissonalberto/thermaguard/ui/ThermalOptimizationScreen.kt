package com.jeissonalberto.thermaguard.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeissonalberto.thermaguard.domain.ThermalUiState
import com.jeissonalberto.thermaguard.ui.theme.LocalTgColors

// ─────────────────────────────────────────────────────────────────────────────
//  ThermalOptimizationScreen — Guía de optimización térmica
//  Niveles: Software → Re-paste → Mod Hardware
//  Detecta SoC automáticamente para mostrar tips específicos
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ThermalOptimizationScreen(uiState: ThermalUiState) {
val tg = LocalTgColors.current

    // Detección de dispositivo
    val deviceInfo = remember { detectDevice() }
    val currentTemp = uiState.latest.batteryTemp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tg.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Header
        OptHeader(deviceInfo = deviceInfo, currentTemp = currentTemp)

        // ── NIVEL 1: Software ──────────────────────────────────────────────
        OptSection(
            level       = 1,
            color       = Color(0xFF00E5FF),
            icon        = Icons.Default.PhoneAndroid,
            title       = "Software",
            subtitle    = "Impacto inmediato · Sin abrir el equipo",
            tips        = softwareTips(deviceInfo)
        )

        // ── NIVEL 2: Re-paste ──────────────────────────────────────────────
        OptSection(
            level       = 2,
            color       = Color(0xFFFFD740),
            icon        = Icons.Default.Build,
            title       = "Re-paste Térmico",
            subtitle    = "Requiere abrir el equipo · Reversible",
            tips        = repasteTips(deviceInfo)
        )

        // ── NIVEL 3: Mod Hardware ──────────────────────────────────────────
        OptSection(
            level       = 3,
            color       = Color(0xFFFF5252),
            icon        = Icons.Default.Warning,
            title       = "Mod Hardware",
            subtitle    = "Avanzado · Riesgo moderado · Lee antes de ejecutar",
            tips        = hardwareTips(deviceInfo)
        )

        Spacer(Modifier.height(24.dp))
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────
@Composable
private fun OptHeader(deviceInfo: DeviceInfo, currentTemp: Float) {
    val tg = LocalTgColors.current
    val tempColor = when {
        currentTemp >= 45f -> Color(0xFFFF1744)
        currentTemp >= 40f -> Color(0xFFFFD740)
        else               -> Color(0xFF00E5FF)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0F1623))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🌡", fontSize = 32.sp)
                Column {
                    Text(
                        "Optimización Térmica",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = tg.textPri
                    )
                    Text(
                        deviceInfo.fullName,
                        fontSize = 12.sp,
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1C2A3A), thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Temp. actual",
                    fontSize = 12.sp,
                    color = tg.textSec
                )
                Text(
                    if (currentTemp > 0f) "%.1f°C".format(currentTemp) else "—",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = tempColor
                )
            }

            if (deviceInfo.isSnapdragon8Gen1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFF5252).copy(alpha = 0.1f))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Snapdragon 8 Gen 1 detectado — SoC con alta densidad térmica. " +
                        "Aplica especialmente los tips de nivel 2.",
                        fontSize = 11.sp,
                        color = Color(0xFFFF5252).copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

// ─── Sección colapsable ───────────────────────────────────────────────────────
@Composable
private fun OptSection(
    level: Int,
    color: Color,
    icon: ImageVector,
    title: String,
    subtitle: String,
    tips: List<OptTip>
) {
    val tg = LocalTgColors.current
    var expanded by remember { mutableStateOf(level == 1) } // nivel 1 abierto por defecto

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0F1623))
    ) {
        Column {
            // Header de sección — clickeable
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Badge nivel
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$level",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = color
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tg.textPri)
                    Text(subtitle, fontSize = 11.sp, color = tg.textSec)
                }

                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = tg.textSec,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Contenido colapsable
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit  = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = Color(0xFF1C2A3A), thickness = 1.dp)
                    Spacer(Modifier.height(4.dp))
                    tips.forEach { tip ->
                        OptTipCard(tip = tip, accentColor = color)
                    }
                }
            }
        }
    }
}

// ─── Card de tip individual ───────────────────────────────────────────────────
@Composable
private fun OptTipCard(tip: OptTip, accentColor: Color) {
    val tg = LocalTgColors.current
    var showDetail by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF141E2E))
            .clickable(enabled = tip.detail != null) { showDetail = !showDetail }
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(tip.emoji, fontSize = 20.sp, modifier = Modifier.padding(top = 2.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tip.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = tg.textPri,
                            modifier = Modifier.weight(1f)
                        )
                        if (tip.impact != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accentColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    tip.impact,
                                    fontSize = 10.sp,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(tip.description, fontSize = 12.sp, color = tg.textSec, lineHeight = 17.sp)
                }
            }

            // Detalle expandible
            if (tip.detail != null) {
                AnimatedVisibility(visible = showDetail, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.07f))
                            .padding(10.dp)
                    ) {
                        Text(tip.detail, fontSize = 11.sp, color = tg.textSec, lineHeight = 16.sp)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        if (showDetail) "▲ Menos" else "▼ Ver pasos",
                        fontSize = 10.sp,
                        color = accentColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ─── Modelos de datos ─────────────────────────────────────────────────────────
data class OptTip(
    val emoji: String,
    val title: String,
    val description: String,
    val impact: String? = null,
    val detail: String? = null
)

data class DeviceInfo(
    val brand: String,
    val model: String,
    val soc: String,
    val fullName: String,
    val isSnapdragon8Gen1: Boolean,
    val isExynos2200: Boolean,
    val isSamsung: Boolean,
    val isXiaomi: Boolean,
    val isHighEnd: Boolean
)

fun detectDevice(): DeviceInfo {
    val profile      = HardwareProfiler.getProfile()
    val brand        = android.os.Build.BRAND.lowercase()
    val model        = android.os.Build.MODEL
    val brandDisplay = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    return DeviceInfo(
        brand             = brand,
        model             = model,
        soc               = profile.chipset,
        fullName          = "$brandDisplay $model · ${profile.chipset}",
        isSnapdragon8Gen1 = profile.isQualcomm && profile.chipset.contains("8 Gen 1", ignoreCase = true),
        isExynos2200      = profile.isExynos    && profile.chipset.contains("2200",   ignoreCase = true),
        isSamsung         = brand.contains("samsung"),
        isXiaomi          = brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco"),
        isHighEnd         = (profile.cpuClusters.maxOfOrNull { it.maxFreqKhz } ?: 0L) > 2_500_000L
    )
}

// ─── Tips por nivel ───────────────────────────────────────────────────────────

fun softwareTips(d: DeviceInfo): List<OptTip> = buildList {

    // Tips universales
    add(OptTip(
        emoji = "📶",
        title = "Baja de 5G a LTE/4G",
        description = "El módem 5G genera entre 1–2°C extra constantes. En espacios cerrados con cobertura inestable el impacto es mayor.",
        impact = "−2°C",
        detail = if (d.isSamsung)
            "Ajustes → Conexiones → Redes móviles → Modo de red → LTE/3G/2G.\nReactívalo solo cuando necesites descargas rápidas."
        else
            "Ajustes → Red e internet → SIM → Tipo de red preferida → LTE.\nReactívalo solo cuando necesites descargas rápidas."
    ))

    add(OptTip(
        emoji = "💡",
        title = "Reduce el brillo y refresco de pantalla",
        description = "Pantalla a máximo brillo + 120Hz consume hasta 1.8W extra — todo se convierte en calor.",
        impact = "−1.5°C",
        detail = "Activa el brillo adaptativo y configura el refresco en 60Hz cuando no juegues:\n" +
            if (d.isSamsung) "Ajustes → Pantalla → Suavidad de movimiento → Estándar (60Hz)."
            else "Ajustes → Pantalla → Frecuencia de actualización → 60Hz."
    ))

    add(OptTip(
        emoji = "🔋",
        title = "No uses el teléfono cargando",
        description = "Carga rápida + uso intensivo es la combinación más destructiva. La batería genera calor al cargarse y el SoC al procesar — se acumula.",
        impact = "−4°C"
    ))

    add(OptTip(
        emoji = "📱",
        title = "Suspende apps en segundo plano",
        description = "Apps activas sin que las uses generan ciclos de CPU innecesarios.",
        impact = "−1°C",
        detail = if (d.isSamsung)
            "Ajustes → Mantenimiento del dispositivo → Batería → Límites de uso en segundo plano → mueve apps poco usadas a 'Saturación profunda'."
        else
            "Ajustes → Apps → selecciona la app → Batería → Restringida."
    ))

    // Tips específicos Samsung
    if (d.isSamsung) {
        add(OptTip(
            emoji = "⚡",
            title = "Activa Perfil de Rendimiento Ligero",
            description = "Reduce la frecuencia de reloj en tareas secundarias sin que notes lentitud en el día a día.",
            impact = "−2°C",
            detail = "Ajustes → Mantenimiento del dispositivo → Batería → Perfil de rendimiento → Ligero."
        ))
    }

    // Tips específicos Snapdragon 8 Gen 1
    if (d.isSnapdragon8Gen1) {
        add(OptTip(
            emoji = "🎮",
            title = "Limita FPS en juegos",
            description = "El Snapdragon 8 Gen 1 fue fabricado en proceso Samsung 4nm de primera generación — es conocido por alta densidad de potencia. Limitar FPS a 60 en juegos baja la temperatura hasta 8°C.",
            impact = "−8°C",
            detail = "Usa Game Booster (Samsung) o los ajustes in-game para fijar 60FPS.\n" +
                     "El rendimiento visual sigue siendo excelente y el SoC trabaja un 40% menos caliente."
        ))

        add(OptTip(
            emoji = "🌀",
            title = "Thermal Guardian (Samsung Good Guardians)",
            description = "Suite oficial Samsung que permite ajustar el umbral de throttle térmico del procesador.",
            detail = "1. Busca 'Samsung Good Guardians' en Galaxy Store.\n" +
                     "2. Instala el módulo Thermal Guardian.\n" +
                     "3. Mueve el deslizador −2°C para que el teléfono disipe antes."
        ))
    }

    // Tip universal final
    add(OptTip(
        emoji = "🧹",
        title = "Limpia la caché del sistema",
        description = "Apps con caché corrupta generan ciclos de CPU innecesarios en segundo plano.",
        detail = "Ajustes → Almacenamiento → Caché → Borrar.\nHazlo cada 2–3 semanas."
    ))
}

fun repasteTips(d: DeviceInfo): List<OptTip> = buildList {

    add(OptTip(
        emoji = "🔬",
        title = "¿Qué es el re-paste?",
        description = "La pasta térmica de fábrica se degrada con el tiempo y el calor. Reemplazarla puede bajar 4–8°C en carga sostenida.",
        impact = "−4 a −8°C"
    ))

    // Tabla de pastas ordenada por rendimiento
    add(OptTip(
        emoji = "🥇",
        title = "PTM7950 (recomendada)",
        description = "Almohadilla de cambio de fase — 17 W/m·K. Se funde a temperatura de operación y hace contacto perfecto. La que usa Apple internamente.",
        impact = "17 W/m·K",
        detail = "Corta un trozo del tamaño exacto del IHS/blindaje del SoC.\n" +
                 "No necesitas aplicar presión extra — se fusiona sola al calentar.\n" +
                 "Disponible en AliExpress o Amazon (~$8 USD la lámina)."
    ))

    add(OptTip(
        emoji = "🥈",
        title = "Thermal Grizzly Kryonaut",
        description = "12.5 W/m·K. La más usada por profesionales para re-paste de móviles. Excelente relación precio/rendimiento.",
        impact = "12.5 W/m·K",
        detail = "Aplica un punto del tamaño de un grano de arroz en el centro del IHS.\n" +
                 "No la extiendas — la presión del ensamble lo hace.\n" +
                 "Disponible en tiendas de tecnología (~$12 USD)."
    ))

    add(OptTip(
        emoji = "🥉",
        title = "Shin-Etsu X-23 (stock)",
        description = "9.8 W/m·K. Es la pasta que Samsung usa de fábrica. Si ya está degradada, cualquier reemplazo mejora.",
        impact = "9.8 W/m·K"
    ))

    if (d.isSnapdragon8Gen1 || d.isExynos2200) {
        add(OptTip(
            emoji = "⚠️",
            title = "Nota S22: el IHS está soldado",
            description = "El Exynos 2200 / Snapdragon 8 Gen 1 en el S22 tiene el blindaje metálico fijo. El re-paste se aplica entre el IHS y el escudo superior, no directamente al die.",
            detail = "No intentes remover el IHS soldado — riesgo de dañar el SoC.\n" +
                     "Aplica la pasta nueva sobre el blindaje superior al reensamblar."
        ))
    }

    add(OptTip(
        emoji = "🛠️",
        title = "Herramientas necesarias",
        description = "iOpener o pistola de calor, palanca de plástico, destornillador T5 Torx, cinta Kapton, pasta térmica elegida.",
        detail = "Kit iFixit (~$25) incluye todas las herramientas necesarias.\n" +
                 "Usa la pistola de calor a 80–90°C máximo sobre la tapa trasera para ablandar el adhesivo.\n" +
                 "Guía de teardown S22: ifixit.com/Teardown/Samsung+Galaxy+S22"
    ))
}

fun hardwareTips(d: DeviceInfo): List<OptTip> = buildList {

    add(OptTip(
        emoji = "⚠️",
        title = "Lee esto antes de continuar",
        description = "Las modificaciones de hardware son irreversibles si no se ejecutan con precisión. Requieren desmontaje completo y conocimiento de la placa base.",
        detail = "Si no has hecho re-paste antes, haz primero el Nivel 2.\n" +
                 "Estos mods anulan la garantía."
    ))

    add(OptTip(
        emoji = "🟠",
        title = "Láminas de cobre — El problema del RF",
        description = "El cobre tiene 400 W/m·K de conductividad pero actúa como jaula de Faraday. Bloquea NFC, carga inalámbrica y antenas Wi-Fi/BT.",
        impact = "400 W/m·K",
        detail = "Solución: recorta la lámina de 0.1–0.3mm dejando 'ventanas' sobre:\n" +
                 "• Bobina de carga inalámbrica (tercio inferior trasero)\n" +
                 "• Chip NFC (esquina superior izquierda)\n" +
                 "• Antenas secundarias Wi-Fi/BT (marcos laterales)\n\n" +
                 "Sin estas ventanas perderás carga inalámbrica y NFC permanentemente."
    ))

    add(OptTip(
        emoji = "⚡",
        title = "Riesgo eléctrico — Aislamiento obligatorio",
        description = "El cobre conduce electricidad. Un desplazamiento de 1mm sobre la PCB puede causar cortocircuito permanente.",
        detail = "Solución: recubre la cara interna de la lámina con cinta Kapton (poliamida).\n" +
                 "La Kapton resiste hasta 400°C y no transmite electricidad.\n" +
                 "Disponible en AliExpress ($3–5 USD el rollo).\n\n" +
                 "Usa una almohadilla térmica de silicona entre la lámina y el blindaje del SoC — NO pasta directamente sobre el cobre."
    ))

    add(OptTip(
        emoji = "🔋",
        title = "Protege la batería — Zona de corte",
        description = "No extiendas el cobre sobre la batería. El calor redistribuido puede degradarla aceleradamente si opera sobre 45°C.",
        detail = "Cubre solo el TERCIO SUPERIOR del teléfono:\n" +
                 "• ✅ Zona del SoC (cámaras / parte superior)\n" +
                 "• ❌ Zona de la batería (parte inferior)\n\n" +
                 "En el S22: la batería ocupa los 2/3 inferiores del chasis."
    ))

    add(OptTip(
        emoji = "✅",
        title = "Alternativa recomendada: Copper Foil Tape",
        description = "La cinta de cobre adhesiva ya viene con aislante en la cara adhesiva. Más fácil de aplicar y recortar que láminas sueltas.",
        detail = "Busca 'copper foil tape EMI shielding 0.1mm' en Amazon o AliExpress.\n" +
                 "Pega directamente sobre el blindaje del SoC — el adhesivo ya es aislante.\n" +
                 "Costo: ~$6 USD por rollo."
    ))

    add(OptTip(
        emoji = "🏆",
        title = "El mejor resultado: Vapor Chamber externo",
        description = "Para modificaciones extremas, algunos fabricantes de accesorios venden planchas de vapor (vapor chambers) del tamaño del S22 que reemplazan la tapa trasera.",
        detail = "Busca 'Galaxy S22 vapor chamber back cover' en AliExpress.\n" +
                 "Estas tapas incluyen una cámara de vapor integrada — disipación pasiva máxima sin riesgo de RF ni cortocircuito."
    ))
}
