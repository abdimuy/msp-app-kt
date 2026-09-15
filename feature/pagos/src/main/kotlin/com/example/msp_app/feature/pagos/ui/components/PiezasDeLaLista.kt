package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.component.MspProgressBar
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente
import com.example.msp_app.feature.pagos.ui.EstadoCuentaUi
import com.example.msp_app.feature.pagos.ui.SegmentoDeCobranza
import java.time.format.DateTimeFormatter

/** Prefijo de `testTag` de cada chip: `"${CHIP_DE_SEGMENTO_TAG}todos"`, etc. */
const val CHIP_DE_SEGMENTO_TAG: String = "pagos_chip_"

/** `testTag` de la tarjeta de un cliente en la lista. */
const val FILA_DE_CLIENTE_TAG: String = "pagos_fila_cliente"

/**
 * El filtro de la lista, como **un solo control de cuatro estados**.
 *
 * ## Por qué un segmentado y no cuatro pastillas
 *
 * La forma dice la verdad. Cuatro pastillas sueltas sugieren que se pueden
 * prender varias a la vez, y el comportamiento real es que **solo una puede
 * estar activa**. El segmentado lo dice sin gastar una palabra de texto.
 *
 * ## De dónde salen los 6dp de tinta que se recuperan
 *
 * La pastilla anterior fijaba `heightIn(min = touchTarget)` —el token de 56dp
 * del tema— con padding vertical 0: **pintaba sus 56dp tocables enteros**. Aquí
 * el alto lo pone el toque y no la tinta: cada segmento fija
 * [ALTO_TOCABLE_DEL_SEGMENTO] y el contenedor no agrega padding vertical, así
 * que el control mide 50dp. La misma accesibilidad, 6dp menos de franja, y
 * cuatro pastillas sueltas menos.
 *
 * **50 y no 48.** El piso de Material son 48dp, pero este repo pide **≥50**
 * (`plans/2026-09-01-pagos-y-visitas.md` §Global Constraints) y ya lo cobra
 * `ListaSeVeYSeTocaTest`: la Task 16 shipeó un control de 49.5dp y tuvo que
 * corregirlo. Se toma el número del repo, que es el más estricto.
 *
 * ## Por qué a escala grande vuelve a rodar
 *
 * Repartir los 328dp de la pantalla entre cuatro segmentos da 79dp a cada uno.
 * A `MUY_GRANDE` (2.0) "sin visitar" no cabe ni de lejos, así que el control
 * deja de repartir el ancho y **rueda en horizontal**. Es el mismo criterio que
 * [EncabezadoDeCliente] aplica al monto, y por la misma razón: un dato que se
 * sale de la pantalla es información perdida, y el rótulo que se corta es justo
 * el que más trabajo esconde.
 *
 * El área tocable **no** se puede probar con un golden. La cobran dos tests, y
 * miden cosas distintas: `ListaSeVeYSeTocaTest` mide el componente suelto, y
 * `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest` lo mide dentro del
 * `NavHost` real y con el inset de la barra despachado — que es donde el
 * defecto del toggle se escondió, porque en Robolectric ese inset vale cero.
 */
@Composable
fun SegmentadoDeCobranza(
    seleccionado: SegmentoDeCobranza,
    conteos: Map<SegmentoDeCobranza, Int>,
    onElegir: (SegmentoDeCobranza) -> Unit,
    modifier: Modifier = Modifier
) {
    val reparteElAncho = LocalFontSizeLevel.current == FontSizeLevel.NORMAL
    val rueda = if (reparteElAncho) {
        Modifier
    } else {
        Modifier.horizontalScroll(rememberScrollState())
    }
    Box(modifier = modifier.then(rueda)) {
        Surface(
            modifier = if (reparteElAncho) Modifier.fillMaxWidth() else Modifier,
            color = MspTheme.colors.surface,
            shape = MspTheme.shapes.control,
            border = BorderStroke(GROSOR_DEL_BORDE, MspTheme.colors.outline)
        ) {
            Row(
                // Sin padding vertical a propósito: el alto del control ES el
                // alto tocable del segmento, no una franja pintada alrededor.
                modifier = Modifier.padding(horizontal = SANGRIA_DEL_SEGMENTADO),
                horizontalArrangement = Arrangement.spacedBy(SEPARACION_DE_SEGMENTOS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                segmentosVisibles().forEach { segmento ->
                    Segmento(
                        segmento = segmento,
                        cuantos = conteos[segmento] ?: 0,
                        activo = segmento == seleccionado,
                        onElegir = { onElegir(segmento) },
                        modifier = if (reparteElAncho) Modifier.weight(1f) else Modifier
                    )
                }
            }
        }
    }
}

/**
 * Los chips que se pintan hoy — **los cuatro, desde la Task 21**.
 *
 * ## Por qué "hoy" ya está
 *
 * [SegmentoDeCobranza.HOY] solo puede contener cuentas con `PROMESA_FECHA` o
 * `CITA_FECHA`. La Task 19 construyó la captura estructurada que las escribe y
 * la **Task 21 la volvió alcanzable**: el dock de las dos pantallas de detalle
 * navega a `visitas/registrar`, y el `NewVisitDialog` —que escribía la fecha
 * dentro del texto libre de `NOTA` y por lo tanto no llenaba ninguna de las dos
 * columnas— quedó retirado en la misma tarea. Con eso el chip deja de ser
 * estructuralmente incapaz de marcar otra cosa que 0.
 *
 * Encenderlo antes habría sido peor que no tenerlo: un chip que dice "hoy 0" al
 * lado de "vencidos 34" durante semanas le enseña al cobrador que la fila de
 * filtros miente, y cuando deja de leer la fila se pierden también los chips que
 * sí sirven. Por eso el interruptor se movió el día en que el dato se volvió
 * alcanzable, y no antes.
 */
private fun segmentosVisibles(): List<SegmentoDeCobranza> =
    SegmentoDeCobranza.entries.filter { HOY_VISIBLE || it != SegmentoDeCobranza.HOY }

/**
 * El interruptor del párrafo de arriba. Lo encendió la **Task 21**, junto con el
 * punto de entrada que hizo alcanzable la captura de la Task 19.
 *
 * Se conserva como constante en vez de borrarse porque es el único lugar donde
 * el chip se apaga sin tocar el enum, sus ramas ni sus goldens — y porque el
 * test que lo afirma es lo que ata "el chip se pinta" a "el dato es alcanzable".
 */
const val HOY_VISIBLE: Boolean = true

/**
 * Un segmento del control.
 *
 * El rótulo admite **dos renglones**: a escala NORMAL "sin visitar" ocupa casi
 * los 79dp que le tocan, y cortarlo con puntos suspensivos escondería justo el
 * filtro que más trabajo agrupa. Con el conteo debajo, dos renglones de rótulo
 * siguen cabiendo dentro de los 48dp tocables, así que partir no agranda el
 * control.
 */
@Composable
private fun Segmento(
    segmento: SegmentoDeCobranza,
    cuantos: Int,
    activo: Boolean,
    onElegir: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = ALTO_TOCABLE_DEL_SEGMENTO)
            .clip(MspTheme.shapes.chip9)
            // El segmento activo va en `brand` — es el control protagónico de la
            // pantalla. Nunca en `statusPaid`: el verde es solo estado.
            .background(if (activo) MspTheme.colors.brand else Color.Transparent)
            .clickable(onClick = onElegir)
            .padding(horizontal = MspTheme.spacing.xs)
            .testTag(CHIP_DE_SEGMENTO_TAG + segmento.name.lowercase()),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = segmento.etiqueta,
                style = MspTheme.type.chipLabel,
                color = if (activo) MspTheme.colors.onBrand else MspTheme.colors.onSurfaceMuted,
                maxLines = RENGLONES_DEL_ROTULO,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
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
}

/**
 * **50dp, y los pone el toque.** El control pinta exactamente esto de alto
 * porque el contenedor no agrega padding vertical; la pastilla anterior pintaba
 * 56 por fijar el `touchTarget` del tema (56dp) y no quitarse el padding.
 */
private val ALTO_TOCABLE_DEL_SEGMENTO = 50.dp

private val SANGRIA_DEL_SEGMENTADO = 3.dp

private val SEPARACION_DE_SEGMENTOS = 2.dp

private val GROSOR_DEL_BORDE = 1.dp

private const val RENGLONES_DEL_ROTULO = 2

private const val OPACIDAD_DEL_CONTEO = 0.75f

/**
 * Un CLIENTE en la lista: su encabezado y **sus ventas dentro de la misma
 * tarjeta**.
 *
 * ## Por qué una sola tarjeta y no una por venta
 *
 * Antes el cliente era una tarjeta y cada venta OTRA tarjeta colgando de ella.
 * Dos problemas, y el segundo está medido sobre el golden: la forma seguía sin
 * decir la verdad —una puerta eran tres tarjetas— y un cliente con dos ventas
 * gastaba **378.5dp** de los 513 que quedan de lista bajo la cabecera, o sea
 * **1.27 clientes por pantalla**.
 *
 * Con las ventas dentro, la tarjeta ES la puerta. Es lo que hace kollect, y es
 * la forma diciendo por fin lo que esta pantalla existe para arreglar: la lista
 * vieja partía a un cliente con dos ventas en dos personas distintas.
 *
 * Lo que se fue del encabezado, y por qué:
 *
 * | Se quitó | Por qué |
 * |---|---|
 * | Avatar de iniciales | El dueño lo pidió fuera. `MethodPill` ya había hecho lo mismo en el reporte |
 * | Saldo total del cliente | Amontonaba el encabezado, y el monto que importa es el de cada venta |
 * | Teléfono | No se cobra por teléfono; la zona y la calle sí ubican la puerta |
 * | Racimo de estados | **Redundante**: cada venta ya trae su chip a unos píxeles |
 * | Folio en cada venta | El cobrador identifica el mueble por su nombre |
 */
@Composable
fun FilaDeCliente(
    cliente: ClienteEnLista,
    onAbrirCliente: () -> Unit,
    onAbrirVenta: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** "Esconder cantidades": el monto de cada venta se pinta enmascarado. */
    montosOcultos: Boolean = false
) {
    Tarjeta(modifier = modifier.testTag(FILA_DE_CLIENTE_TAG), onClick = onAbrirCliente) {
        Column {
            EncabezadoDeCliente(cliente)
            Spacer(Modifier.height(AIRE_ANTES_DE_LA_PRIMERA_VENTA))
            cliente.ventas.forEach { enLista ->
                RenglonDeVenta(
                    venta = enLista.venta,
                    montosOcultos = montosOcultos,
                    onAbrir = { onAbrirVenta(enLista.venta.ventaId) }
                )
            }
        }
    }
}

/**
 * Nombre a la izquierda, veredicto del cliente a la derecha.
 *
 * ## El ritmo vertical, que es la mitad del arreglo
 *
 * El primer intento puso el mismo aire en todos lados y el dueño lo rechazó:
 * "el nombre y la dirección están muy amontonados". La regla que lo arregla es
 * que **el aire antes de un separador es mayor que el aire entre dos líneas del
 * mismo bloque** — [AIRE_BAJO_EL_NOMBRE] contra
 * [AIRE_ANTES_DE_LA_PRIMERA_VENTA]. Así el ojo agrupa nombre y dirección como
 * una unidad y no confunde la dirección con la primera venta.
 */
@Composable
private fun EncabezadoDeCliente(cliente: ClienteEnLista) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            NombreDelCliente(cliente.nombre)
            Spacer(Modifier.height(AIRE_BAJO_EL_NOMBRE))
            Text(
                // Zona y calle en un renglón. El teléfono salió: no se cobra por
                // teléfono, y su hueco es lo que deja respirar al nombre.
                text = listOf(cliente.zona, cliente.direccion)
                    .filter { it.isNotBlank() }
                    .joinToString(SEPARADOR_DE_META)
                    .ifBlank { SIN_DATO },
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Aviso(cliente)
            cliente.ultimoPago?.let { dia ->
                Spacer(Modifier.height(MspTheme.spacing.xs))
                Text(
                    text = PREFIJO_ULTIMO_PAGO + DIA_Y_MES_DEL_PAGO.format(dia),
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * **Siempre un renglón, y si no cabe, corre.**
 *
 * Cortar un nombre con puntos suspensivos esconde justo el apellido que
 * distingue a dos clientes de la misma familia, que en una ruta es lo normal.
 * `basicMarquee` es la forma idiomática: solo anima cuando el texto NO cabe, y
 * `LazyColumn` solo compone lo visible, así que con los 270 clientes de la ruta
 * real corren dos o tres, no 270.
 *
 * Con movimiento reducido no se anima: ahí sí se corta, porque para quien pidió
 * que nada se moviera un texto en movimiento es peor que un texto incompleto.
 */
@Composable
private fun NombreDelCliente(nombre: String) {
    val quieto = LocalReduceMotion.current
    Text(
        text = nombre,
        style = MspTheme.type.listTitle,
        color = MspTheme.colors.onSurface,
        maxLines = 1,
        softWrap = false,
        overflow = if (quieto) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = if (quieto) Modifier else Modifier.basicMarquee()
    )
}

/**
 * Una venta DENTRO de la tarjeta del cliente: nombre y monto arriba; estado y
 * atrasos abajo; la barra de avance al pie, de lado a lado.
 *
 * La barra va en su propio renglón **a propósito**. Puesta al lado del chip, su
 * largo dependería del largo del chip —"Pagó esta semana" deja menos pista que
 * "No estaba"—, así que dos ventas con el mismo avance se verían distintas, y
 * una con menos avance podría verse más llena. Con renglón propio la pista mide
 * siempre lo mismo y dos barras se pueden comparar de un vistazo, que es lo
 * único que se les pide.
 */
@Composable
private fun RenglonDeVenta(venta: VentaDelCliente, montosOcultos: Boolean, onAbrir: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(GROSOR_DEL_HAIRLINE)
            .background(MspTheme.colors.outline)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAbrir)
            .padding(vertical = AIRE_DEL_RENGLON)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs)
        ) {
            Text(
                text = venta.descripcion.ifBlank { venta.folio },
                style = MspTheme.type.saleTitle,
                color = MspTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            MspMoneyText(
                amount = venta.saldo.amount,
                masked = montosOcultos,
                style = MspTheme.type.amountRow,
                color = MspTheme.colors.onSurface
            )
        }
        Spacer(Modifier.height(AIRE_SOBRE_EL_ESTADO))
        // A `MUY_GRANDE` (2.0) el chip se come el ancho y la pastilla se cortaba a
        // "Al" — medido en `pagos_lista_light_2_0`. Un número de atrasos a medias
        // no es un dato incompleto, es un dato FALSO, así que en esa escala la
        // pastilla baja a su propio renglón en vez de encogerse. A 1.5 caben las
        // dos en una fila, también medido, y ahí no se gasta alto de más.
        val enUnaFila = LocalFontSizeLevel.current != FontSizeLevel.MUY_GRANDE
        if (enUnaFila) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChipDeEstado(venta.estado)
                Spacer(Modifier.weight(1f))
                PastillaDeAtrasos(venta.atrasos)
            }
        } else {
            ChipDeEstado(venta.estado)
            Spacer(Modifier.height(MspTheme.spacing.xs))
            PastillaDeAtrasos(venta.atrasos)
        }
        Spacer(Modifier.height(AIRE_SOBRE_LA_BARRA))
        MspProgressBar(
            progress = venta.avance,
            height = ALTO_DE_LA_BARRA,
            fillColor = MspTheme.colors.heroProgressFill,
            trackColor = MspTheme.colors.progressTrack
        )
    }
}

/**
 * Cuántos pagos lleva atrasada esta venta, en los MISMOS tres tramos que usaba
 * la pantalla vieja (`PrimarySaleItem.kt:79-88`): 0 verde, 1-4 ámbar, 5+ rojo.
 *
 * Se conservan los tramos y se cambian los textos, que decían `"No tiene
 * atrasa."` y `"2 pag atrasa"` — recortados a la fuerza y con punto final.
 */
@Composable
private fun PastillaDeAtrasos(atrasos: Int) {
    val colors = MspTheme.colors
    val contenido: Color
    val fondo: Color
    when {
        atrasos < 1 -> {
            contenido = colors.statusPaid
            fondo = colors.statusPaidTint
        }
        atrasos < ATRASOS_GRAVES -> {
            contenido = colors.statusPartial
            fondo = colors.statusPartialTint
        }
        else -> {
            contenido = colors.statusOverdue
            fondo = colors.statusOverdueTint
        }
    }
    Box(
        modifier = Modifier
            .background(color = fondo, shape = MspTheme.shapes.chip)
            .padding(horizontal = MspTheme.spacing.sm, vertical = MspTheme.spacing.xs)
            .testTag(PASTILLA_DE_ATRASOS_TAG)
    ) {
        Text(
            text = textoDeAtrasos(atrasos),
            style = MspTheme.type.captionStrong,
            color = contenido,
            maxLines = 1
        )
    }
}

/** "Al corriente" · "1 atraso" · "N atrasos". */
internal fun textoDeAtrasos(atrasos: Int): String = when {
    atrasos < 1 -> "Al corriente"
    atrasos == 1 -> "1 atraso"
    else -> "$atrasos atrasos"
}

/**
 * "Faltan 2 de 3" cuando quedan cuentas por trabajar, "N cuentas" cuando no.
 * El primer texto lo calcula [EstadoCuentaUi.avisoDeCuentas], el mismo que usa
 * el encabezado del detalle: el número que dice cuántas puertas quedan abiertas
 * tiene un solo dueño.
 */
@Composable
private fun Aviso(cliente: ClienteEnLista) {
    val pendientes = EstadoCuentaUi.avisoDeCuentas(cliente.ventas.map { it.venta.estado })
    Box(
        modifier = Modifier
            .background(
                color = if (pendientes == null) {
                    MspTheme.colors.surface2
                } else {
                    MspTheme.colors.statusPartialTint
                },
                shape = MspTheme.shapes.chip
            )
            .padding(horizontal = MspTheme.spacing.sm, vertical = MspTheme.spacing.xs)
    ) {
        Text(
            text = pendientes ?: "${cliente.cuentas} cuentas",
            style = MspTheme.type.chipLabel,
            color = if (pendientes == null) {
                MspTheme.colors.onSurfaceMuted
            } else {
                MspTheme.colors.statusPartial
            },
            maxLines = 1
        )
    }
}

/** `testTag` de la pastilla de atrasos — la localiza el test que la afirma. */
const val PASTILLA_DE_ATRASOS_TAG: String = "pagos_atrasos"

/** Aire corto: el nombre y su dirección son el MISMO bloque. */
private val AIRE_BAJO_EL_NOMBRE = 4.dp

/**
 * Aire largo, más del triple que [AIRE_BAJO_EL_NOMBRE]: es lo que despega al
 * cliente del primer separador y lo que arregla el "muy amontonado".
 */
private val AIRE_ANTES_DE_LA_PRIMERA_VENTA = 14.dp

private val AIRE_DEL_RENGLON = 12.dp

private val AIRE_SOBRE_EL_ESTADO = 9.dp

private val AIRE_SOBRE_LA_BARRA = 11.dp

private val ALTO_DE_LA_BARRA = 4.dp

private val GROSOR_DEL_HAIRLINE = 1.dp

/** Desde cuántos atrasos la pastilla pasa de ámbar a rojo (tramo heredado). */
private const val ATRASOS_GRAVES = 5

private const val PREFIJO_ULTIMO_PAGO = "Últ. pago "

private val DIA_Y_MES_DEL_PAGO: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", BUSINESS_LOCALE)

private const val SEPARADOR_DE_META = " · "
