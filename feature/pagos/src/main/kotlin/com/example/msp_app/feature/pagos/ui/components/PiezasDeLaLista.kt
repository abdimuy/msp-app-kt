package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.example.msp_app.core.designsystem.component.MspInitialsAvatar
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.ui.EstadoCuentaUi
import com.example.msp_app.feature.pagos.ui.SegmentoDeCobranza
import com.example.msp_app.feature.pagos.ui.estadoVisualDe

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
 * Un CLIENTE en la lista: su encabezado y **sus ventas dentro**, cada una con
 * el mismo `FilaDeVenta` que ya pinta el detalle de cliente.
 *
 * Se reusa ese componente a propósito: las dos pantallas hablan del mismo
 * estado del catálogo de ocho y una segunda presentación del mismo estado es
 * exactamente cómo dos pantallas empiezan a decir cosas distintas del mismo
 * dato.
 */
@Composable
fun FilaDeCliente(
    cliente: ClienteEnLista,
    onAbrirCliente: () -> Unit,
    onAbrirVenta: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Tarjeta(modifier = Modifier.testTag(FILA_DE_CLIENTE_TAG), onClick = onAbrirCliente) {
            EncabezadoDeCliente(cliente)
        }
        Spacer(Modifier.height(MspTheme.spacing.sm))
        cliente.ventas.forEach { enLista ->
            FilaDeVenta(
                venta = enLista.venta,
                onAbrir = { onAbrirVenta(enLista.venta.ventaId) },
                modifier = Modifier.padding(start = SANGRIA_DE_LA_VENTA)
            )
            Spacer(Modifier.height(MspTheme.spacing.sm))
        }
    }
}

@Composable
private fun EncabezadoDeCliente(cliente: ClienteEnLista) {
    // A escala grande la cifra no cabe junto al nombre en 360dp y se baja a su
    // propio renglón — mismo criterio que `TresDatos`, y por la misma razón: un
    // dato que se sale de la pantalla es información perdida.
    val enUnaFila = LocalFontSizeLevel.current == FontSizeLevel.NORMAL
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs)
        ) {
            MspInitialsAvatar(initials = cliente.iniciales)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cliente.nombre,
                    style = MspTheme.type.listTitle,
                    color = MspTheme.colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(cliente.telefono, cliente.zona)
                        .filter { it.isNotBlank() }
                        .joinToString(SEPARADOR_DE_META)
                        .ifBlank { SIN_DATO },
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (enUnaFila) SaldoDelCliente(cliente)
        }
        if (!enUnaFila) {
            Spacer(Modifier.height(MspTheme.spacing.sm))
            SaldoDelCliente(cliente)
        }
        Spacer(Modifier.height(MspTheme.spacing.sm))
        Text(
            text = cliente.direccion.ifBlank { SIN_DATO },
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(MspTheme.spacing.sm))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            // El racimo de cuadros: un estado por cuenta, con su ícono. Es lo
            // que hace visible de un vistazo que esta puerta tiene dos deudas
            // en situaciones distintas — el caso que la lista por venta partía
            // en dos personas.
            cliente.ventas.take(CUADROS_EN_EL_RACIMO).forEach { enLista ->
                CuadroDeEstado(estadoVisualDe(enLista.venta.estado), lado = LADO_DEL_CUADRO)
            }
            Spacer(Modifier.weight(1f))
            Aviso(cliente)
        }
    }
}

@Composable
private fun SaldoDelCliente(cliente: ClienteEnLista) {
    MspMoneyText(
        amount = cliente.saldoTotal.amount,
        style = MspTheme.type.amountRow,
        color = MspTheme.colors.onSurface
    )
}

/**
 * "faltan 2 de 3" cuando quedan cuentas por trabajar, "N cuentas" cuando no.
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

/** Las ventas van sangradas: la puerta manda, sus deudas cuelgan de ella. */
private val SANGRIA_DE_LA_VENTA = 12.dp

private val LADO_DEL_CUADRO = 22.dp

private const val CUADROS_EN_EL_RACIMO = 4

private const val SEPARADOR_DE_META = " · "
