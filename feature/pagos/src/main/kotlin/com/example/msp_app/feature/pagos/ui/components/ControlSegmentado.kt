package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * **El control segmentado, compartido.**
 *
 * `SegmentadoDeCobranza` (`PiezasDeLaLista.kt` — el filtro de la lista de
 * clientes) y `FiltrosDeContacto` (`LineaDeContactos.kt` — el filtro de la
 * bitácora y del detalle de venta) delegan aquí.
 *
 * ## Una sola fila, siempre — decisión del dueño, 2026-09-22
 *
 * Antes este control medía y, si los rótulos no cabían, pasaba a una **rejilla
 * de dos renglones**. Con cuatro opciones eso era un 2×2 que se comía un bloque
 * entero de la pantalla antes de la primera tarjeta. El dueño lo vio en el
 * teléfono y pidió una fila de cuatro con deslizamiento, y esa decisión no se
 * revisa aquí.
 *
 * Tres cosas hacen que el deslizamiento casi nunca haga falta a escala normal:
 *
 * 1. **El conteo va al lado del rótulo, no debajo.** Apilados, cada segmento
 *    medía dos renglones de texto; en línea mide uno. El control pasó de ~110dp
 *    de alto a ~50.
 * 2. **Cada chip mide lo que su texto necesita**, no una porción igual. Antes
 *    "Pagados" recibía el mismo ancho que "Volver a visitar" y sobraba de un
 *    lado mientras faltaba del otro.
 * 3. **La `Surface` sigue llenando el ancho** ([fillMaxWidth] incondicional) y
 *    lo que rueda es la fila **dentro** de ella. Ese era el defecto real de la
 *    primera versión con scroll: la `Surface` tomaba el ancho de la fila
 *    completa —más ancha que la pantalla— y su borde derecho quedaba fuera de
 *    vista. Aquí el marco es fijo y sólo se mueve el contenido.
 *
 * ## El aviso contado, y por qué el degradado no era uno
 *
 * A letra grande las cuatro **no** caben, y comprimir no alcanza a salvarlo.
 * Medido a `w360dp-xhdpi` con las etiquetas reales de la lista: a `GRANDE`
 * (1.5) la fila pide 428dp contra 325dp de ventana, y a `MUY_GRANDE` (2.0) pide
 * 542dp. Bajar el conteo debajo del rótulo —la forma anterior— ahorra ~12dp por
 * chip, o sea ~48dp de los 103dp que faltan a 1.5, y a cambio duplica el alto
 * del control, que es justo el bulto que el dueño mandó quitar. Así que el
 * deslizamiento se queda y lo que hay que resolver es la **señal**.
 *
 * Hubo un degradado en la orilla derecha que pretendía ser esa señal y no lo
 * era. No es que no se pintara: se pinta, y se midió — el nodo existe en cuanto
 * hay algo que deslizar, y en `pagos_lista_light_2_0.png` sus 48px de rampa
 * están ahí, aclarando el trazo de "Des" de 92 a 250 de luminancia. El defecto
 * es otro: va de `Transparent` a `colors.surface` **sobre** `colors.surface`,
 * así que no tiene contra qué contrastar. Sólo puede atenuar el glifo que caiga
 * bajo sus últimos 24dp; si ahí no cae ninguno —lo normal en cuanto el chip
 * cortado queda un poco más a la izquierda, como a 1.5— pinta exactamente cero
 * píxeles de diferencia. Un aviso que la mitad de las veces no existe no es un
 * aviso.
 *
 * Así que el degradado se queda en lo único que sí hace bien: **suavizar el
 * corte**, para que la palabra que no cabe se desvanezca en lugar de que el
 * borde duro del marco la rebane a media letra. La señal es otra cosa:
 *
 * - **Un aviso contado** ([AvisoDeOcultos]) en el costado donde hay opciones
 *   escondidas, con **cuántas** son. Va **fuera** del área deslizable, no
 *   encima: así no tapa ningún chip y su propio ancho entra en la cuenta de lo
 *   que cabe. Tocarlo trae **una** opción escondida —la primera de ese costado,
 *   no el extremo: saltar al final le quita al cobrador la referencia de dónde
 *   estaba—, que es lo que un degradado nunca pudo ofrecer.
 * - **El chip elegido se trae solo a la vista** cuando cambia la selección, así
 *   que elegir uno a medias fuera de cuadro no deja su conteo a medias. Esto
 *   existía, se perdió en la reescritura a fila única, y vuelve aquí.
 *
 * El invariante que se promete —y que
 * `ElControlSegmentadoNoSeRompeALetraGrandeTest` cobra midiendo bordes
 * **recortados** contra el tamaño sin recortar— es: *a cualquier escala de
 * letra, o las opciones se leen completas, o el aviso dice exactamente cuántas
 * no*. "Se lee completa" incluye **no estar debajo del degradado**: ver
 * [Recuento], donde se cuenta con la ventana ya descontada la orilla, porque un
 * conteo desvanecido a blanco está tan escondido como uno fuera de cuadro.
 *
 * `internal`: sólo lo usan los dos filtros de `:feature:pagos`.
 */
@Composable
internal fun <T> ControlSegmentado(
    opciones: List<T>,
    seleccionado: T,
    conteos: Map<T, Int>,
    etiquetaDe: (T) -> String,
    tagDe: (T) -> String,
    onElegir: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val alcance = rememberCoroutineScope()
    val colorDelMarco = MspTheme.colors.surface
    // El padding horizontal que `Segmento` le pone a CADA lado de su contenido —
    // se lee aquí, en contexto `@Composable`, para sumarlo al ancho natural
    // medido dentro del `SubcomposeLayout`, que no puede leer `MspTheme`.
    val margenDelSegmento = MspTheme.spacing.sm
    val densidad = LocalDensity.current
    val holgura = with(densidad) { HOLGURA_DE_REDONDEO.toPx() }
    val orillaPx = with(densidad) { ANCHO_DE_LA_ORILLA.toPx() }

    // La verdad de "¿se ve este chip?" se lee del LAYOUT, no de una aritmética
    // paralela: la caja SIN recortar de cada chip y la de la ventana, ambas en
    // coordenadas de raíz. Una cuenta propia de anchos podría despegarse de
    // dónde quedaron colocados de verdad; estas cajas no pueden, y además
    // incluyen el desplazamiento del scroll, que la medición no conoce.
    var ventana by remember { mutableStateOf(Rect.Zero) }
    val cajas = remember(opciones) { mutableStateMapOf<Int, Rect>() }
    val recuento by remember(opciones) {
        derivedStateOf { recuenta(opciones.size, cajas, ventana, holgura, orillaPx) }
    }
    val ocultos = recuento.ilegibles

    TraeALaVistaLoElegido(
        indice = opciones.indexOf(seleccionado),
        cajas = cajas,
        ventana = { recuento.legible },
        scroll = scroll
    )

    Surface(
        modifier = modifier.fillMaxWidth().testTag(CONTROL_SEGMENTADO_TAG),
        color = colorDelMarco,
        shape = MspTheme.shapes.control,
        border = BorderStroke(GROSOR_DEL_BORDE, MspTheme.colors.outline)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvisoDeOcultos(
                cuantos = ocultos.izquierda,
                tag = AVISO_IZQUIERDA_TAG,
                onTocar = {
                    alcance.launch {
                        traeALaVista(
                            indice = primeroEscondido(
                                opciones.size,
                                cajas,
                                recuento.legible,
                                false
                            ),
                            scroll = scroll,
                            cajas = cajas,
                            ventana = { recuento.legible }
                        )
                    }
                }
            )
            // El ancho real disponible se mide AQUÍ y no adentro: dentro de un
            // `horizontalScroll` las restricciones son infinitas, así que la
            // fila ya no puede saber cuánto espacio había ni, por tanto, si le
            // sobra. Y se mide DESPUÉS de descontar los avisos, porque el aviso
            // ocupa ancho real: fingir que no lo ocupa escondería un chip más
            // sin contarlo.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { ventana = it.cajaEnRaiz() }
            ) {
                val anchoDisponible = constraints.maxWidth
                Box(
                    modifier = Modifier
                        .horizontalScroll(scroll)
                        .selectableGroup()
                ) {
                    FilaDeSegmentos(
                        opciones = opciones,
                        seleccionado = seleccionado,
                        conteos = conteos,
                        etiquetaDe = etiquetaDe,
                        tagDe = tagDe,
                        onElegir = onElegir,
                        anchoDisponible = anchoDisponible,
                        margenDelSegmento = margenDelSegmento,
                        onColocado = { indice, caja -> cajas[indice] = caja }
                    )
                }
                OrillaSuave(
                    visible = recuento.cortados.izquierda > 0,
                    alFinal = false,
                    color = colorDelMarco
                )
                OrillaSuave(
                    visible = recuento.cortados.derecha > 0,
                    alFinal = true,
                    color = colorDelMarco
                )
            }
            AvisoDeOcultos(
                cuantos = ocultos.derecha,
                tag = AVISO_DERECHA_TAG,
                onTocar = {
                    alcance.launch {
                        traeALaVista(
                            indice = primeroEscondido(
                                opciones.size,
                                cajas,
                                recuento.legible,
                                true
                            ),
                            scroll = scroll,
                            cajas = cajas,
                            ventana = { recuento.legible }
                        )
                    }
                }
            )
        }
    }
}

/**
 * **El aviso: cuántas opciones no se están viendo de ese lado.**
 *
 * Es la respuesta a lo que el degradado no podía dar. Un degradado, aun bien
 * pintado, dice "hay más" y nada más; esto dice **cuántas más**, que es el dato
 * con el que el cobrador decide si vale la pena deslizar. Y es tocable: lleva el
 * deslizamiento hasta ese extremo, así que la opción escondida está a un toque y
 * no a un gesto que hay que adivinar.
 *
 * Va en `brandTint` y no en gris: el degradado fracasó justamente por pintarse
 * del color sobre el que se pinta. Un aviso tiene que verse contra su fondo o no
 * es un aviso.
 *
 * Con [cuantos] en cero no se compone nada — ni un hueco de ancho cero: el `Row`
 * padre no debe reservar espacio por un aviso que no existe.
 */
@Composable
private fun AvisoDeOcultos(cuantos: Int, tag: String, onTocar: () -> Unit) {
    if (cuantos <= 0) return
    Box(
        modifier = Modifier
            .heightIn(min = ALTO_TOCABLE_DEL_SEGMENTO)
            .padding(horizontal = SANGRIA_DEL_SEGMENTADO)
            .clip(MspTheme.shapes.chip9)
            .background(MspTheme.colors.brandTint)
            .clickable(onClick = onTocar)
            .padding(horizontal = MspTheme.spacing.xs)
            .testTag(tag)
            .semantics { contentDescription = "$cuantos opciones más" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+$cuantos",
            style = MspTheme.type.captionStrong,
            color = MspTheme.colors.brand,
            maxLines = 1
        )
    }
}

/**
 * **La orilla difuminada: un corte suave, no una señal.**
 *
 * Se midió lo que este degradado puede y no puede hacer (ver el KDoc de
 * [ControlSegmentado]): como va del transparente al color de la `Surface` sobre
 * la `Surface`, no puede anunciar nada por sí mismo. Lo que sí hace, y bien, es
 * que la palabra que queda cortada se desvanezca en lugar de que el borde del
 * marco la rebane a media letra. Quien anuncia es [AvisoDeOcultos].
 *
 * Los dos aparecen y desaparecen juntos, pero **no cuentan lo mismo**: la orilla
 * se pinta por `Recuento.cortados` (lo que el recorte dejó fuera) y el aviso
 * dice `Recuento.ilegibles`, que además incluye lo que quedó debajo de esta
 * orilla. Ver el KDoc de `Recuento` en `OcultosDelSegmentado.kt`: es la orilla
 * misma la que crea parte de lo que el aviso tiene que contar.
 */
@Composable
private fun BoxScope.OrillaSuave(visible: Boolean, alFinal: Boolean, color: Color) {
    if (!visible) return
    val colores = if (alFinal) {
        listOf(Color.Transparent, color)
    } else {
        listOf(color, Color.Transparent)
    }
    Box(
        modifier = Modifier
            .matchParentSize()
            .wrapContentWidth(if (alFinal) Alignment.End else Alignment.Start)
            .width(ANCHO_DE_LA_ORILLA)
            .background(Brush.horizontalGradient(colores))
            .testTag(if (alFinal) ORILLA_CON_MAS_TAG else ORILLA_ATRAS_TAG)
    )
}

/**
 * **Elegir una opción la trae entera a la vista.**
 *
 * Existía antes de la reescritura a fila única y se perdió en ella. Sin esto,
 * tocar el chip que asoma a medias lo pinta de azul y lo deja igual de cortado:
 * el conteo del filtro que el cobrador acaba de elegir es justo el que no puede
 * leer.
 *
 * La clave del efecto es **sólo el índice**. Las cajas cambian en cada frame del
 * deslizamiento, así que tenerlas de clave relanzaría —y cancelaría— la
 * animación a media carrera. Por eso la primera medición se espera con un
 * `snapshotFlow` DENTRO del efecto en vez de reaccionar a ella desde fuera: en
 * el primer frame la caja del chip todavía no existe y el desplazamiento se
 * calcularía contra cero.
 */
@Composable
private fun TraeALaVistaLoElegido(
    indice: Int,
    cajas: Map<Int, Rect>,
    ventana: () -> Rect,
    scroll: ScrollState
) {
    LaunchedEffect(indice) {
        if (indice < 0) return@LaunchedEffect
        snapshotFlow { cajas[indice] to ventana() }
            .first { (chip, hueco) -> chip != null && !hueco.isEmpty }
        traeALaVista(indice, scroll, cajas, ventana)
    }
}

/**
 * Desliza hasta que la opción [indice] se vea **entera**.
 *
 * Reintenta hasta [INTENTOS_DE_ACOMODO] veces y eso **no** es defensivo: el
 * destino es un blanco en movimiento. Al deslizar hacia adelante aparece el
 * aviso del costado contrario, que le quita su ancho a la ventana y vuelve a
 * dejar corto el cálculo hecho antes de deslizar. Medido en
 * `w360dp-xhdpi`/`MUY_GRANDE`: un solo `animateScrollTo(maxValue)` dejaba la
 * última opción **74px cortada**, porque el `maxValue` que leyó era el de una
 * ventana 68px más ancha que la que quedó. Dos vueltas bastan hoy; el tope
 * existe para que un caso raro —una opción más ancha que la ventana entera— no
 * gire para siempre.
 *
 * El `withFrameNanos` entre vueltas no es una espera arbitraria: sin él se leen
 * las cajas del frame anterior, o sea las de antes de deslizar.
 */
private suspend fun traeALaVista(
    indice: Int,
    scroll: ScrollState,
    cajas: Map<Int, Rect>,
    ventana: () -> Rect
) {
    if (indice < 0) return
    repeat(INTENTOS_DE_ACOMODO) {
        // "Nada que hacer" y "ya no se puede hacer más" se dicen igual: el
        // destino calculado coincide con dónde ya está el scroll.
        val destino = destinoPara(cajas[indice], ventana(), scroll)
        if (destino == scroll.value) return
        scroll.animateScrollTo(destino)
        withFrameNanos { }
    }
}

/**
 * La fila, con el reparto del ancho decidido por **medición**.
 *
 * Mide cada chip con su texto en una línea, sin comprimir, y compara la suma
 * contra [anchoDisponible]:
 *
 * - **Si sobra espacio**, el sobrante se reparte **en partes iguales** entre los
 *   chips y la fila llena el ancho completo. Sin esto, los cuatro chips se
 *   apelotonan a la izquierda y queda un hueco muerto a la derecha que hace ver
 *   el control a medio terminar. El sobrante se reparte, en vez de forzar anchos
 *   iguales, porque anchos iguales le darían a "Pagados" tanto espacio como a
 *   "Sin visitar 268" y apretarían al más largo.
 * - **Si no cabe** —letra del sistema grande, sobre todo—, cada chip se queda
 *   con su ancho natural y la fila sale más ancha que la ventana. El
 *   `horizontalScroll` de quien la contiene se encarga del resto, y el aviso
 *   contado dice cuántos quedaron fuera.
 *
 * El `SubcomposeLayout` es lo que permite las dos pasadas: la primera mide sin
 * marco ni `testTag` ([ContenidoDelSegmento], que nunca se coloca — ver su
 * KDoc), la segunda compone los chips de verdad con el ancho ya decidido.
 *
 * [onColocado] publica la caja sin recortar de cada chip. Sale de
 * `onGloballyPositioned` y no de la aritmética de esta medición **a propósito**:
 * el desplazamiento del scroll no vive aquí, así que sólo el layout sabe dónde
 * quedó de verdad cada chip respecto de la ventana.
 */
@Composable
private fun <T> FilaDeSegmentos(
    opciones: List<T>,
    seleccionado: T,
    conteos: Map<T, Int>,
    etiquetaDe: (T) -> String,
    tagDe: (T) -> String,
    onElegir: (T) -> Unit,
    anchoDisponible: Int,
    margenDelSegmento: Dp,
    onColocado: (Int, Rect) -> Unit
) {
    SubcomposeLayout(
        // Sin padding vertical a propósito: el alto del control ES el alto
        // tocable de sus segmentos, no una franja pintada alrededor.
        modifier = Modifier.padding(horizontal = SANGRIA_DEL_SEGMENTADO)
    ) { restricciones ->
        val separacionPx = SEPARACION_DE_SEGMENTOS.roundToPx()
        val altoMinimoPx = ALTO_TOCABLE_DEL_SEGMENTO.roundToPx()
        val sangriaPx = SANGRIA_DEL_SEGMENTADO.roundToPx() * 2
        val margenPx = margenDelSegmento.roundToPx() * 2
        val cuantos = opciones.size.coerceAtLeast(1)
        val separacionTotal = separacionPx * (opciones.size - 1).coerceAtLeast(0)

        // Paso 1: lo que cada chip pide para su rótulo y su conteo en una línea,
        // sin comprimir. Nunca se coloca: sólo mide.
        val naturales = subcompose(SLOT_DE_MEDICION) {
            opciones.forEach { opcion ->
                ContenidoDelSegmento(
                    etiqueta = etiquetaDe(opcion),
                    cuantos = conteos[opcion] ?: 0,
                    activo = false
                )
            }
        }.map { it.measure(Constraints()).width + margenPx }

        val utilizable = (anchoDisponible - sangriaPx - separacionTotal).coerceAtLeast(0)
        val sobrante = utilizable - naturales.sum()
        // El sobrante se reparte parejo; el residuo de la división entera va al
        // primero, para que la fila llene el ancho EXACTO y no quede un pelo de
        // hueco por redondeo.
        val extra = if (sobrante > 0) sobrante / cuantos else 0
        val residuo = if (sobrante > 0) sobrante % cuantos else 0

        val anchos = naturales.mapIndexed { i, natural ->
            natural + extra + if (i == 0) residuo else 0
        }

        val colocables = subcompose(SLOT_DE_LA_FILA) {
            opciones.forEachIndexed { indice, opcion ->
                Segmento(
                    etiqueta = etiquetaDe(opcion),
                    cuantos = conteos[opcion] ?: 0,
                    activo = opcion == seleccionado,
                    onElegir = { onElegir(opcion) },
                    tag = tagDe(opcion),
                    modifier = Modifier.onGloballyPositioned {
                        onColocado(indice, it.cajaEnRaiz())
                    }
                )
            }
        }.mapIndexed { i, medible ->
            val ancho = anchos[i]
            medible.measure(
                Constraints(minWidth = ancho, maxWidth = ancho, minHeight = altoMinimoPx)
            )
        }

        val alto = colocables.maxOfOrNull { it.height } ?: altoMinimoPx
        val anchoTotal = anchos.sum() + separacionTotal
        // `coerceAtMost` sólo contra el máximo de la restricción del padre, que
        // dentro del scroll es infinito: cuando no cabe, la fila SÍ sale más
        // ancha que la ventana, que es justo lo que hace posible deslizar.
        layout(anchoTotal.coerceAtMost(restricciones.maxWidth), alto) {
            var x = 0
            colocables.forEach { colocable ->
                colocable.placeRelative(x, 0)
                x += colocable.width + separacionPx
            }
        }
    }
}

/**
 * El rótulo y el conteo de un segmento, **en la misma línea**.
 *
 * Apilados medían dos renglones de texto por segmento y eran la mitad del bulto
 * que el dueño vio en el teléfono. En línea, el conteo sigue igual de legible y
 * el control mide la mitad de alto.
 *
 * `maxLines = 1` y sin elipsis por diseño: el chip mide lo que su texto
 * necesita, así que no hay nada que recortar. Si algún día un rótulo creciera
 * tanto que no cupiera ni deslizando, se vería entero y desbordaría — un
 * problema visible, que es mejor que unos puntos suspensivos escondiendo cuál
 * filtro es.
 */
@Composable
private fun ContenidoDelSegmento(
    etiqueta: String,
    cuantos: Int,
    activo: Boolean,
    tagDeLaEtiqueta: String? = null
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = etiqueta,
            style = MspTheme.type.chipLabel,
            color = if (activo) MspTheme.colors.onBrand else MspTheme.colors.onSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            modifier = if (tagDeLaEtiqueta != null) Modifier.testTag(tagDeLaEtiqueta) else Modifier
        )
        Text(
            text = cuantos.toString(),
            style = MspTheme.type.captionStrong,
            // El conteo acompaña al rótulo, no compite con él: sobre el relleno
            // de marca baja a tres cuartos de opacidad.
            color = if (activo) {
                MspTheme.colors.onBrand.copy(alpha = OPACIDAD_DEL_CONTEO)
            } else {
                MspTheme.colors.onSurfaceMuted
            },
            maxLines = 1
        )
    }
}

/**
 * Un segmento del control.
 *
 * `.selectable(selected = activo, role = Role.RadioButton, …)`: el contenedor
 * entero es un `selectableGroup`, así que cada segmento se anuncia con su rol y
 * su estado elegido — un lector de pantalla sabe cuál opción está activa, no
 * sólo que hay algo que tocar. `selectable` fusiona el texto de sus
 * descendientes en un solo nodo: el lector lee "Pagados, 3", no dos fragmentos
 * sueltos.
 */
@Composable
private fun Segmento(
    etiqueta: String,
    cuantos: Int,
    activo: Boolean,
    onElegir: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = ALTO_TOCABLE_DEL_SEGMENTO)
            .clip(MspTheme.shapes.chip9)
            // El segmento activo va en `brand` — es el control protagónico de la
            // pantalla. Nunca en `statusPaid`: el verde es solo estado.
            .background(if (activo) MspTheme.colors.brand else Color.Transparent)
            .selectable(
                selected = activo,
                role = Role.RadioButton,
                onClick = onElegir
            )
            .padding(horizontal = MspTheme.spacing.sm)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        ContenidoDelSegmento(
            etiqueta = etiqueta,
            cuantos = cuantos,
            activo = activo,
            tagDeLaEtiqueta = ETIQUETA_DEL_SEGMENTO_TAG
        )
    }
}

/**
 * **50dp, y los pone el toque.** El control pinta exactamente esto de alto
 * porque el contenedor no agrega padding vertical.
 */
private val ALTO_TOCABLE_DEL_SEGMENTO = 50.dp

private val SANGRIA_DEL_SEGMENTADO = 3.dp

private val SEPARACION_DE_SEGMENTOS = 2.dp

private val GROSOR_DEL_BORDE = 1.dp

/**
 * Lo que se le perdona a un borde antes de llamarlo "fuera de la ventana": el
 * redondeo a píxel, y nada más. Media palabra no cabe en un dp.
 */
private val HOLGURA_DE_REDONDEO = 1.dp

/** Slot de [SubcomposeLayout] para la pasada de medición — nunca se coloca. */
private const val SLOT_DE_MEDICION = "control_segmentado_medicion"

/** Slot de [SubcomposeLayout] de los chips reales. */
private const val SLOT_DE_LA_FILA = "control_segmentado_fila"

/** Vueltas máximas de [traeALaVista] — ver su KDoc: el destino se mueve. */
private const val INTENTOS_DE_ACOMODO = 3

/** Ancho del degradado que suaviza el corte. */
private val ANCHO_DE_LA_ORILLA = 24.dp

private const val OPACIDAD_DEL_CONTEO = 0.75f

/**
 * `testTag` del control entero — existe para medir sus bordes y cobrar que
 * ningún segmento quede fuera de ellos.
 */
const val CONTROL_SEGMENTADO_TAG: String = "control_segmentado"

/**
 * `testTag` del rótulo de un segmento. Existe para leer su `TextLayoutResult` y
 * cobrar que nunca lleve elipsis.
 */
const val ETIQUETA_DEL_SEGMENTO_TAG: String = "control_segmentado_etiqueta"

/**
 * `testTag` del degradado de la orilla derecha. **No** es la señal de "hay más"
 * —ver el KDoc de [OrillaSuave]—, sólo el corte suave.
 */
const val ORILLA_CON_MAS_TAG: String = "control_segmentado_orilla"

/** `testTag` del degradado de la orilla izquierda. */
const val ORILLA_ATRAS_TAG: String = "control_segmentado_orilla_atras"

/**
 * `testTag` del aviso contado del lado derecho — **ésta sí** es la señal, y es
 * la que cobra `ElControlSegmentadoNoSeRompeALetraGrandeTest`.
 */
const val AVISO_DERECHA_TAG: String = "control_segmentado_aviso_derecha"

/** `testTag` del aviso contado del lado izquierdo. */
const val AVISO_IZQUIERDA_TAG: String = "control_segmentado_aviso_izquierda"
