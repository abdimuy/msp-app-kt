package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.theme.MspColors
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.ui.DayChipUi
import com.example.msp_app.feature.collectionreport.ui.theme.REPORT_STANDARD_DURATION_MS
import com.example.msp_app.feature.collectionreport.ui.theme.ReportStandardEasing
import com.example.msp_app.feature.collectionreport.ui.theme.rememberReportReducedMotion
import java.time.LocalDate

/**
 * Ancho mínimo de un chip. No es un token de [MspTheme.spacing] (que es escala de MÁRGENES, no
 * de tamaños de control) por el mismo criterio que `TIER2_DOT_SIZE`/`DAY_AVATAR_SIZE`: es una
 * medida propia de este control. Da a "lun/1" y a "mié/13" el mismo ancho, así la tira lee como
 * una fila pareja y no como texto suelto de anchos distintos.
 */
private val DAY_CHIP_MIN_WIDTH = 52.dp

/** Anillo de foco (D-pad/teclado) — mismo grosor y criterio que [DetailListToggle]. */
private val DAY_CHIP_FOCUS_RING = 2.dp

/**
 * Contorno permanente del chip. Es lo que hace que "hoy" se lea como *hoy* incluso cuando no
 * está seleccionado: relleno tenue (`statusPaidTint`) + contorno pleno (`statusPaid`). Sin él,
 * el chip de hoy queda como una mancha clara indistinguible de un día cualquiera.
 */
private val DAY_CHIP_BORDER = 1.5.dp

/**
 * Alto visible del chip, DISTINTO de su área táctil.
 *
 * El área táctil sigue siendo [MspTheme.spacing.touchTarget] (56dp, "las manos de un cobrador
 * caminando") porque esa es una regla dura del design system y la tira se usa de pie y en la
 * calle. Pero pintar 56dp de recuadro convierte la tira en una fila de botones que se come el
 * tablero. Se separan: el contenedor externo recibe el toque con el piso de 56dp, el interno
 * pinta compacto. El usuario toca lo mismo de siempre y ve una tira discreta.
 */
private val DAY_CHIP_PADDING_VERTICAL = 6.dp

/** `EEE` es-MX -> "lun".."dom" (el mismo formato corto que usa la sparkline de Semana). */
private const val WEEKDAY_SHORT_FORMAT = "EEE"

/** `EEEE d 'de' MMMM` es-MX -> "jueves 6 de agosto" (solo para `contentDescription`). */
private const val WEEKDAY_LONG_FORMAT = "EEEE d 'de' MMMM"

private const val EMPTY_DAY_LABEL = "Sin cobros"

/**
 * Colores resueltos de un chip: fondo + contenido (texto). Par, no dos valores sueltos — la
 * legibilidad depende de la COMBINACIÓN, y separarlos invita a mezclar un fondo con el contenido
 * de otro estado.
 */
internal data class DayChipPalette(
    val background: Color,
    val content: Color,
    val border: Color = Color.Transparent
)

/**
 * Estado visual de un chip, resuelto SOLO desde tokens de [MspColors] (regla dura: ningún
 * `Color(0xFF…)` fuera del design system).
 *
 * Es una función pura y `internal` a propósito: los tres estados y su combinación son la parte
 * del diseño que no puede fallar, y así se verifican en un test de JVM (colores exactos y
 * distintos entre sí) además del golden — un golden solo dice "cambió", no "cambió a lo
 * correcto".
 *
 * | estado | fondo | contenido |
 * |---|---|---|
 * | seleccionado **y** hoy | `statusPaid` (verde lleno) | `surface` |
 * | seleccionado | `brand` (azul lleno) | `onBrand` |
 * | hoy | `statusPaidTint` | `statusPaid` |
 * | sin cobros | `surface` | `onSurfaceMuted` (atenuado) |
 * | normal | `surface` | `onSurface` |
 *
 * **Por qué el orden es ese:** *seleccionado* manda sobre *hoy* en el relleno porque la
 * selección es lo que gobierna el resto de la pantalla (total, lista, Compartir/Imprimir/PDF);
 * y *hoy* manda sobre *sin cobros* porque "hoy" es la referencia temporal del cobrador y no
 * debe apagarse por no haber cobrado todavía. Un día sin cobros SÍ se atenúa, pero nunca
 * desaparece — decisión de transparencia del dueño (un día ausente se lee como dato faltante;
 * uno en gris, como un día sin cobrar).
 *
 * **Por qué `surface` como color de CONTENIDO del chip verde lleno:** el design system no tiene
 * un token `onStatusPaid`, y `onBrand` (blanco en ambos temas) sobre el `statusPaid` claro del
 * tema oscuro (`#40CB84`) daría ~2:1, ilegible. `surface` es el único token que invierte con el
 * tema (blanco en claro, casi negro en oscuro) y por lo tanto contrasta con AMBOS extremos de
 * `statusPaid` — verificado en `ContrastAAATest`, no supuesto.
 */
internal fun dayChipPalette(colors: MspColors, chip: DayChipUi): DayChipPalette = when {
    chip.isSelected && chip.isToday ->
        DayChipPalette(colors.statusPaid, colors.surface, colors.statusPaid)
    chip.isSelected ->
        DayChipPalette(colors.brand, colors.onBrand, colors.brand)
    chip.isToday ->
        DayChipPalette(colors.statusPaidTint, colors.statusPaid, colors.statusPaid)
    !chip.hasCollections ->
        DayChipPalette(colors.surface, colors.onSurfaceMuted)
    else ->
        DayChipPalette(colors.surface, colors.onSurface)
}

/**
 * Tira horizontal de los días del ciclo del cobrador (periodo Día): de la carga de ruta a hoy,
 * inclusive. Elegir un día cambia el total, la lista de pagos y — porque las tres acciones de
 * salida se arman desde el estado — también lo que se comparte, se imprime y se exporta.
 *
 * **Desplazable, no envolvente:** `Row` + `horizontalScroll`, no `FlowRow` ni `LazyRow`. Un
 * ciclo puede pasar de 7 días y envolver a una segunda línea convertiría la tira en un
 * calendario improvisado; `LazyRow` compondría solo los chips visibles, lo que además de
 * complicar los tests dejaría a un lector de pantalla sin los días de la derecha hasta
 * desplazarse. Con `horizontalScroll` todos los chips existen siempre en el árbol.
 *
 * **`fontScale` grande:** cada chip es una `Column` (día de semana arriba, número abajo), nunca
 * un `Row` con pesos repartidos — el defecto documentado en [RangeSubRow] (texto colapsando
 * letra por letra al quedarse sin ancho) no puede ocurrir aquí porque ningún hijo compite por
 * el ancho: la tira crece a lo largo y se desplaza.
 *
 * [emptyDay] y [note] son las dos líneas honestas bajo la tira: "Sin cobros" cuando el día
 * mostrado cerró en cero, y la hora de arranque cuando ese día es el de la carga ("desde las
 * 7:33 p.m. · inicio del ciclo"). Van en la misma `Column`, una por línea, cortas — sin ellas un
 * cero se lee como falla; con ellas se lee como corte.
 *
 * **No se reusó [RangeSubRow]** (se evaluó): esa subfila vive ARRIBA del `TabTransition`, es
 * independiente del periodo y su `Row` tiene un reparto de pesos calibrado contra un defecto
 * real a `fontScale = 2.0`. Estas dos líneas son específicas del día elegido, tienen que
 * deslizarse junto al contenido de Día y no compiten por ancho con nada — meterlas en
 * [RangeSubRow] obligaría a volver a tocar ese reparto sin ganar nada.
 */
@Composable
fun DayStrip(
    days: List<DayChipUi>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    emptyDay: Boolean = false,
    note: String = ""
) {
    if (days.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        DayChipRow(days = days, onSelect = onSelect)
        if (emptyDay) {
            Text(
                text = EMPTY_DAY_LABEL,
                style = MspTheme.type.captionStrong,
                color = MspTheme.colors.onSurfaceMuted
            )
        }
        if (note.isNotBlank()) {
            Text(
                text = note,
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
        }
    }
}

/**
 * Fila desplazable de chips.
 *
 * El `LaunchedEffect` ancla la tira al final la primera vez que se conoce su ancho desplazable y
 * el chip seleccionado es el ÚLTIMO (el caso por omisión: hoy). Sin esto, un ciclo largo abre
 * mostrando los días viejos y el día que el cobrador está viendo queda fuera de pantalla — un
 * control seleccionado que no se ve es tan malo como uno que no existe. Se hace una sola vez
 * (bandera [anchored]) para no pelearse con el desplazamiento manual del usuario, y con
 * `scrollTo` (instantáneo): la posición INICIAL de un contenedor no se anima, ni con
 * reduce-motion ni sin él.
 */
@Composable
private fun DayChipRow(days: List<DayChipUi>, onSelect: (LocalDate) -> Unit) {
    val scrollState = rememberScrollState()
    var anchored by rememberSaveable { mutableStateOf(false) }
    val selectedIsLast = days.lastOrNull()?.isSelected == true
    LaunchedEffect(scrollState.maxValue, selectedIsLast) {
        if (!anchored && selectedIsLast && scrollState.maxValue > 0) {
            scrollState.scrollTo(scrollState.maxValue)
            anchored = true
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        days.forEach { chip -> DayChip(chip = chip, onClick = { onSelect(chip.date) }) }
    }
}

/**
 * Un día del ciclo. Control REAL, no una etiqueta pintada: `Role.Button`, `onClickLabel` y
 * `contentDescription` con el día y TODOS sus estados ("jueves 6 de agosto, hoy, seleccionado,
 * sin cobros"), `selected` en semántica para los lectores de pantalla, alto mínimo
 * [MspTheme.spacing.touchTarget] (56dp — el mismo piso curado que exige Tier 2, así el mismo
 * componente sirve a los dos tiers sin una variante propia) y anillo de foco visible, porque el
 * `indication` por default solo cubre el press.
 *
 * El color transiciona con los tokens de movimiento de la casa
 * ([REPORT_STANDARD_DURATION_MS]/[ReportStandardEasing]); con reduce-motion NO se compone la
 * rama animada — el color se lee directo (cambio ESTRUCTURAL, mismo criterio que
 * [StaggeredEntrance] y [TabTransition], no una animación de 0 ms).
 */
@Composable
private fun DayChip(chip: DayChipUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = dayChipPalette(MspTheme.colors, chip)
    val background = animatedChipColor(palette.background)
    val content = animatedChipColor(palette.content)
    val borderColor = animatedChipColor(palette.border)
    val label = dayChipDescription(chip)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .heightIn(min = MspTheme.spacing.touchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            )
            .semantics {
                contentDescription = label
                selected = chip.isSelected
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(MspTheme.shapes.chip9)
                .background(background)
                .border(
                    width = if (focused) DAY_CHIP_FOCUS_RING else DAY_CHIP_BORDER,
                    color = if (focused) MspTheme.colors.brand else borderColor,
                    shape = MspTheme.shapes.chip9
                )
                .widthIn(min = DAY_CHIP_MIN_WIDTH)
                .padding(
                    horizontal = MspTheme.spacing.sm,
                    vertical = DAY_CHIP_PADDING_VERTICAL
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = AppTime
                    .formatDate(chip.date, WEEKDAY_SHORT_FORMAT)
                    .uppercase(BUSINESS_LOCALE),
                style = MspTheme.type.caption,
                color = content,
                textAlign = TextAlign.Center
            )
            Text(
                text = chip.date.dayOfMonth.toString(),
                style = MspTheme.type.captionStrong,
                color = content,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Color con transición estándar del reporte; instantáneo (sin `Animatable`) con reduce-motion. */
@Composable
private fun animatedChipColor(target: Color): Color {
    if (rememberReportReducedMotion()) return target
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = tween(REPORT_STANDARD_DURATION_MS, easing = ReportStandardEasing),
        label = "day_chip_color"
    )
    return animated
}

/**
 * Descripción accesible del chip: el día completo en es-MX más sus estados, en el mismo orden en
 * que se leen visualmente. Los estados van en TEXTO, no solo en color — el color nunca es el
 * único portador de significado (regla dura del design system).
 */
private fun dayChipDescription(chip: DayChipUi): String = buildList {
    add(AppTime.formatDate(chip.date, WEEKDAY_LONG_FORMAT))
    if (chip.isToday) add("hoy")
    if (chip.isSelected) add("seleccionado")
    if (!chip.hasCollections) add("sin cobros")
}.joinToString(", ")
