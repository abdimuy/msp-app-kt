package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme

/**
 * **El control segmentado, compartido.**
 *
 * `SegmentadoDeCobranza` (`PiezasDeLaLista.kt` — el filtro de la lista de
 * clientes) y `FiltrosDeContacto` (`LineaDeContactos.kt` — el filtro de la
 * bitácora y del detalle de venta, Task 4) delegan aquí. Lo que cambia entre
 * los dos filtros es **cuáles** opciones se enseñan —`SegmentoDeCobranza`
 * esconde "Hoy" mientras `HOY_VISIBLE` esté apagado; `FiltroDeContactos`
 * siempre enseña sus cuatro, cero incluido, por decisión del dueño— y
 * **cómo** se etiqueta y se marca cada una. La tinta, el alto tocable de
 * 50dp y el acomodo de fila-o-rejilla son UN solo control: compartirlo no
 * obliga a parametrizar nada que no fuera ya distinto entre los dos
 * filtros — es justo lo que ya recibían por parámetro (`seleccionado`,
 * `conteos`, `onElegir`).
 *
 * ## Fila si caben, rejilla de dos renglones si no — nunca scroll ni recorte
 *
 * La versión anterior repartía el ancho por igual a escala NORMAL y, para
 * cualquier otra escala, dejaba de repartir y montaba la fila entera dentro
 * de un `horizontalScroll`. A letra grande eso rompía el control: la
 * `Surface` ya no llenaba el ancho de la pantalla (su tamaño natural es el de
 * la fila completa, más ancha que la pantalla), así que el borde y los
 * márgenes del lado derecho quedaban fuera de vista y la foto —el golden— solo
 * enseñaba la posición inicial del scroll, con la última opción cortada a la
 * mitad. Que la opción siguiera alcanzable deslizando no bastaba: la que se
 * esconde es justo la que el conteo existe para avisar, y un dato a medias es
 * un dato falso.
 *
 * Ahora la decisión es de **medición**, no de escala de letra — una escala
 * grande en un teléfono ancho puede caber igual en una fila, y no hay forma
 * de saberlo sin medir: [SubcomposeLayout] compone cada opción una vez sin
 * marco ([ContenidoDelSegmento], sin `testTag` para no duplicar el nodo
 * semántico de [Segmento]) y la mide SIN restricción de ancho — su rótulo y su
 * conteo en una sola línea, sin comprimir. "Caben" no es que la SUMA de esos
 * anchos quepa: es que NINGUNA opción, ya medida, necesite más ancho del que
 * le tocaría al repartir el control entero entre todas — la suma cabiendo no
 * evita que la más larga se comprima y su rótulo baje a dos renglones
 * partiendo una palabra a la mitad. Si todas caben en su porción, las
 * opciones van en una sola fila, repartiendo el ancho por igual (el mismo
 * cálculo que antes hacía `weight(1f)`). Si no, el control pasa a una rejilla de
 * **dos renglones** —con cuatro opciones, 2×2; con otro número, las que quepan
 * por renglón, repartidas parejo (`ceil(n/2)` arriba, el resto abajo)— y cada
 * renglón reparte el ancho completo entre sus propias opciones, no entre las
 * cuatro. El control **nunca** rueda: la `Surface` siempre llena el ancho
 * disponible ([fillMaxWidth] incondicional), así que su borde y sus márgenes
 * quedan enteros en cualquier escala.
 *
 * Vive en su propio archivo — y no dentro de `PiezasDeLaLista.kt`, donde
 * nació— por dos razones: `TooManyFunctions` de detekt (el archivo de la
 * lista ya rozaba el techo de 11) y porque un control que usan DOS filtros
 * no es "una pieza de la lista", es un componente de nivel de módulo.
 *
 * `internal`: sólo lo usan los dos filtros de `:feature:pagos`; no hace
 * falta que salga del módulo, porque el mecanismo es de este feature, no del
 * design system (la decisión completa, con lo que se consideró y se
 * descartó, está en `task-4-report.md`).
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
    // El padding horizontal que [Segmento] le pone a CADA lado de
    // [ContenidoDelSegmento] (ver su `.padding(horizontal = ...)`) — se lee
    // aquí, en contexto `@Composable`, para poder sumarlo al ancho natural
    // medido más abajo, dentro del `SubcomposeLayout` (que no es
    // `@Composable` y no puede leer `MspTheme` directamente).
    val paddingDelSegmento = MspTheme.spacing.xs
    Surface(
        modifier = modifier.fillMaxWidth().testTag(CONTROL_SEGMENTADO_TAG),
        color = MspTheme.colors.surface,
        shape = MspTheme.shapes.control,
        border = BorderStroke(GROSOR_DEL_BORDE, MspTheme.colors.outline)
    ) {
        SubcomposeLayout(
            // Sin padding vertical a propósito: el alto del control ES el
            // alto tocable de sus segmentos, no una franja pintada alrededor.
            // `selectableGroup`: las opciones son mutuamente excluyentes,
            // como un grupo de radio — es lo que anuncia el lector de
            // pantalla al entrar al control (equivalente de `role="group"`
            // del mock).
            modifier = Modifier
                .padding(horizontal = SANGRIA_DEL_SEGMENTADO)
                .selectableGroup()
        ) { restricciones ->
            val separacionPx = SEPARACION_DE_SEGMENTOS.roundToPx()
            val altoMinimoPx = ALTO_TOCABLE_DEL_SEGMENTO.roundToPx()
            val anchoDisponible = restricciones.maxWidth
            // Lo que [Segmento] le resta al ancho útil de CADA opción con su
            // `.padding(horizontal = ...)` — un lado más el otro. La pasada
            // de medición mide [ContenidoDelSegmento] SIN ese marco (ver su
            // KDoc), así que su ancho natural no lo incluye por su cuenta.
            val paddingDelSegmentoPx = paddingDelSegmento.roundToPx() * 2

            // Paso 1: el ancho que cada opción pide para su rótulo y su
            // conteo en una sola línea, SIN comprimir — decide si caben en
            // una fila. Nunca se coloca: sólo mide.
            val medicion = subcompose(SLOT_DE_MEDICION) {
                opciones.forEach { opcion ->
                    ContenidoDelSegmento(
                        etiqueta = etiquetaDe(opcion),
                        cuantos = conteos[opcion] ?: 0,
                        activo = false
                    )
                }
            }
            // + el padding horizontal del segmento real: sin sumarlo, una
            // etiqueta cuyo ancho natural cae entre `porción − 2×xs` y
            // `porción` se juzga "cabe en fila" aquí, pero en el paso 2 —ya
            // con marco— sólo dispone de `porción − 2×xs` para su texto, se
            // comprime y su rótulo baja a dos renglones DENTRO de la fila:
            // exactamente el recorte silencioso que este criterio existe
            // para evitar (8dp de error a `MspTheme.spacing.xs` = 4dp).
            val anchosNaturales = medicion.map { it.measure(Constraints()).width + paddingDelSegmentoPx }
            // "Caben" quiere decir que NINGUNA opción necesitaría más ancho
            // del que le toca al repartir el control entero entre todas —no
            // que la suma quepa: la suma cabiendo no evita que la más larga
            // se comprima y su rótulo baje a dos renglones partiendo una
            // palabra a la mitad. Comparar contra la propia porción es lo que
            // hace que a GRANDE el control pase a rejilla en vez de apretar.
            val anchoPorSegmentoEnUnaFila =
                (anchoDisponible - separacionPx * (opciones.size - 1).coerceAtLeast(0)) /
                    opciones.size.coerceAtLeast(1)
            val cabenEnUnaFila = opciones.size <= 1 ||
                anchosNaturales.all { it <= anchoPorSegmentoEnUnaFila }

            val filas = if (cabenEnUnaFila) {
                listOf(opciones)
            } else {
                // "Las que quepan por renglón, repartidas parejo": la mitad
                // de arriba (redondeando hacia arriba) y el resto abajo. Con
                // cuatro opciones esto es 2×2.
                val primerRenglon = (opciones.size + 1) / 2
                listOf(opciones.take(primerRenglon), opciones.drop(primerRenglon))
            }

            // Paso 2: ya con el acomodo decidido, mide de verdad — esta vez
            // con marco, tag y el ancho fijo que le toca a cada opción dentro
            // de SU renglón (repartido entre las opciones de ese renglón, no
            // entre las cuatro).
            val placeablesPorFila = filas.mapIndexed { i, fila ->
                val opcionesEnElRenglon = fila.size.coerceAtLeast(1)
                val anchoPorSegmento =
                    (anchoDisponible - separacionPx * (fila.size - 1).coerceAtLeast(0)) /
                        opcionesEnElRenglon
                val restriccionDelSegmento = Constraints(
                    minWidth = anchoPorSegmento,
                    maxWidth = anchoPorSegmento,
                    minHeight = altoMinimoPx
                )
                subcompose(SLOT_DE_FILA_PREFIX + i) {
                    fila.forEach { opcion ->
                        Segmento(
                            etiqueta = etiquetaDe(opcion),
                            cuantos = conteos[opcion] ?: 0,
                            activo = opcion == seleccionado,
                            onElegir = { onElegir(opcion) },
                            tag = tagDe(opcion)
                        )
                    }
                }.map { it.measure(restriccionDelSegmento) }
            }

            val altosPorFila = placeablesPorFila.map { fila -> fila.maxOf { it.height } }
            val altoTotal = altosPorFila.sum() +
                SEPARACION_ENTRE_RENGLONES.roundToPx() * (filas.size - 1).coerceAtLeast(0)

            layout(anchoDisponible, altoTotal) {
                var y = 0
                filas.indices.forEach { i ->
                    var x = 0
                    placeablesPorFila[i].forEach { placeable ->
                        placeable.placeRelative(x, y)
                        x += placeable.width + separacionPx
                    }
                    y += altosPorFila[i] + SEPARACION_ENTRE_RENGLONES.roundToPx()
                }
            }
        }
    }
}

/**
 * El rótulo y el conteo de un segmento, apilados — sin marco, sin clic, sin
 * `testTag`. [ControlSegmentado] la usa dos veces con dos propósitos:
 * "midiendo" (aquí, nunca se coloca — decide fila-o-rejilla) y dentro de
 * [Segmento] (con marco y el ancho real que le tocó). El color no afecta el
 * ancho, así que la medición siempre usa `activo = false`.
 *
 * `tagDeLaEtiqueta` sólo lo pasa `Segmento` (la pasada real): el rótulo es el
 * único texto que se comprime cuando el acomodo se equivoca, así que es el
 * único que necesita su propio `testTag` para que un test lea su
 * `TextLayoutResult` y cobre "sin elipsis, sin recorte" — igual que
 * `RenglonPorSegmentos` con `SEGMENTO_DEL_RENGLON_TAG`.
 *
 * **La pasada de medición lo deja en `null`, y no por evitar dos nodos con el
 * mismo `testTag`** — eso ya pasa a propósito en la pasada real, donde las
 * hasta cuatro opciones comparten la MISMA constante
 * [ETIQUETA_DEL_SEGMENTO_TAG], y `onAllNodes` + `useUnmergedTree` lo resuelve
 * sin problema (medido: con las cuatro reales tageadas, `onAllNodesWithTag`
 * sobre el árbol MEZCLADO da CERO — `.selectable()` de [Segmento] absorbe la
 * etiqueta dentro de su propio nodo). La razón real es otra: los nodos de esta
 * pasada nunca se colocan pero SÍ existen en el árbol de semántica — a
 * diferencia de los reales, no tienen un `.selectable()` que los absorba, así
 * que si llevaran el tag aparecerían SUELTOS incluso en el árbol MEZCLADO (el
 * que usa cualquier consulta por default, sin `useUnmergedTree`) — fantasmas
 * que un test podría encontrar sin querer. Y como se miden con
 * `Constraints()` sin restricción, jamás se comprimen ni llevan elipsis:
 * mezclados con los reales, siempre "pasan", así que no prueban nada y sólo
 * añaden ruido a cualquier lectura de `TextLayoutResult`. `null` los deja
 * imposibles de encontrar por tag, que es lo correcto para un nodo que no es
 * UI real.
 */
@Composable
private fun ContenidoDelSegmento(
    etiqueta: String,
    cuantos: Int,
    activo: Boolean,
    tagDeLaEtiqueta: String? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = etiqueta,
            style = MspTheme.type.chipLabel,
            color = if (activo) MspTheme.colors.onBrand else MspTheme.colors.onSurfaceMuted,
            maxLines = RENGLONES_DEL_ROTULO,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = if (tagDeLaEtiqueta != null) Modifier.testTag(tagDeLaEtiqueta) else Modifier
        )
        Text(
            text = cuantos.toString(),
            style = MspTheme.type.captionStrong,
            // El conteo acompaña al rótulo, no compite con él: sobre el
            // relleno de marca baja a tres cuartos de opacidad.
            color = if (activo) {
                MspTheme.colors.onBrand.copy(alpha = OPACIDAD_DEL_CONTEO)
            } else {
                MspTheme.colors.onSurfaceMuted
            },
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Un segmento del control — compartido por `SegmentadoDeCobranza` y
 * `FiltrosDeContacto` a través de [ControlSegmentado].
 *
 * El rótulo admite **dos renglones**: a escala NORMAL "sin visitar" ocupa casi
 * los 79dp que le tocan, y cortarlo con puntos suspensivos escondería justo el
 * filtro que más trabajo agrupa. Con el conteo debajo, dos renglones de rótulo
 * siguen cabiendo dentro de los 48dp tocables, así que partir no agranda el
 * control.
 *
 * `.selectable(selected = activo, role = Role.RadioButton, …)` reemplaza el
 * `.clickable` de antes: el contenedor entero (`ControlSegmentado`) es un
 * `selectableGroup`, así que cada segmento se anuncia con su rol y su estado
 * elegido — un lector de pantalla ya sabe cuál opción está activa, no sólo
 * que hay algo que tocar. `selectable`, como `clickable`, fusiona el texto de
 * sus descendientes en un solo nodo: el lector lee "Promesas, 0", no dos
 * fragmentos sueltos.
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
            .padding(horizontal = MspTheme.spacing.xs)
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
 * porque el contenedor no agrega padding vertical; la pastilla anterior pintaba
 * 56 por fijar el `touchTarget` del tema (56dp) y no quitarse el padding.
 */
private val ALTO_TOCABLE_DEL_SEGMENTO = 50.dp

private val SANGRIA_DEL_SEGMENTADO = 3.dp

private val SEPARACION_DE_SEGMENTOS = 2.dp

/** Separación vertical entre los dos renglones de la rejilla — el mismo valor que entre columnas. */
private val SEPARACION_ENTRE_RENGLONES = SEPARACION_DE_SEGMENTOS

private val GROSOR_DEL_BORDE = 1.dp

private const val RENGLONES_DEL_ROTULO = 2

private const val OPACIDAD_DEL_CONTEO = 0.75f

/** Slot de [SubcomposeLayout] para la pasada de medición — nunca se coloca. */
private const val SLOT_DE_MEDICION = "control_segmentado_medicion"

/** Prefijo del slot de [SubcomposeLayout] de cada renglón real — `"…0"`, `"…1"`. */
private const val SLOT_DE_FILA_PREFIX = "control_segmentado_fila_"

/**
 * `testTag` del control entero — existe para medir sus bordes y cobrar que
 * ningún segmento quede fuera de ellos, en fila o en rejilla.
 */
const val CONTROL_SEGMENTADO_TAG: String = "control_segmentado"

/**
 * `testTag` del rótulo de un segmento — el único texto que se comprime
 * cuando el acomodo se equivoca. Existe para leer su `TextLayoutResult` y
 * cobrar que nunca lleve elipsis ni se desborde, el mismo patrón que
 * [SEGMENTO_DEL_RENGLON_TAG] en [RenglonPorSegmentos].
 */
const val ETIQUETA_DEL_SEGMENTO_TAG: String = "control_segmentado_etiqueta"
