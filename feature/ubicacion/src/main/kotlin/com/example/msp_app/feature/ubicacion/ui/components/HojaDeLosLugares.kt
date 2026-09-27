// Una pieza privada por bloque del mock (card, fila, detalle en sus tres alturas):
// muchas funciones chicas en vez de pocas largas.
@file:Suppress("TooManyFunctions")

package com.example.msp_app.feature.ubicacion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.time.BUSINESS_ZONE
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.feature.ubicacion.domain.ClaseDeLugar
import com.example.msp_app.feature.ubicacion.domain.LugarClasificado
import com.example.msp_app.feature.ubicacion.domain.ResumenDeLugar
import com.example.msp_app.feature.ubicacion.domain.Textos
import com.example.msp_app.feature.ubicacion.domain.distanciaLegible
import com.example.msp_app.feature.ubicacion.domain.mediciones
import com.example.msp_app.feature.ubicacion.ui.PaletaDelMapa
import com.example.msp_app.feature.ubicacion.ui.UbicacionUiState
import com.example.msp_app.feature.ubicacion.ui.estilo
import com.example.msp_app.feature.ubicacion.ui.paletaDelMapa

/** `testTag` del aviso de cambio de lugar. */
const val MUDANZA_TAG: String = "ubicacion_mudanza"

/** `testTag` de "Cómo llegar" (en la card o en el detalle). */
const val COMO_LLEGAR_TAG: String = "ubicacion_como_llegar"

/** `testTag` de la card de un lugar (se le suma la clase). */
const val CARD_TAG: String = "ubicacion_card_"

/** `testTag` de la fila de puntos sueltos. */
const val SUELTOS_TAG: String = "ubicacion_sueltos"

/** `testTag` del regreso del detalle a la lista. */
const val VOLVER_A_LA_LISTA_TAG: String = "ubicacion_volver_a_la_lista"

/**
 * **El contenido de la hoja**: la lista de lugares (reposo o arrastrada) o el
 * detalle del lugar tocado. La altura y el arrastre los pone la pantalla.
 *
 * [asa] es el modificador del arrastre: va sobre el asa y el encabezado, no
 * sobre la lista, para que la lista siga desplazándose con el dedo.
 */
@Composable
fun HojaDeLosLugares(
    state: UbicacionUiState,
    expandida: Boolean,
    onTocarLugar: (LugarClasificado?) -> Unit,
    onComoLlegar: (LugarClasificado) -> Unit,
    onAbrirFiltros: () -> Unit,
    modifier: Modifier = Modifier,
    asa: Modifier = Modifier,
    alturaDelDetalle: AlturaDelDetalle = AlturaDelDetalle.MEDIA,
    onAlturaDelDetalle: (AlturaDelDetalle) -> Unit = {}
) {
    val p = paletaDelMapa()
    Column(
        // **La hoja se queda con TODOS los gestos de su superficie**, también el
        // fondo vacío bajo la última card: arrastrar ahí arrastra la hoja, nunca
        // el mapa de atrás (defecto medido en el aparato el 2026-09-27). Por eso
        // el `asa` va también en el contenedor entero, y el fondo se pinta con
        // `background(color, forma)` y no con `clip`: un `clip` de esquinas
        // desiguales rompe el hit-testing bajo Robolectric. Lo cobra
        // `LaHojaSeQuedaConLosGestosTest`.
        modifier = modifier
            .fillMaxWidth()
            .background(p.c(p.sheet), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .then(asa)
            .padding(horizontal = 16.dp)
    ) {
        Box(asa.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(
                    36.dp,
                    4.dp
                ).alpha(0.6f).clip(RoundedCornerShape(2.dp)).background(p.c(p.faint))
            )
        }
        val tocado = state.lugarTocado
        if (tocado != null) {
            DetalleDelLugar(
                state,
                tocado,
                p,
                DetalleAcciones(onTocarLugar, onComoLlegar, onAbrirFiltros, onAlturaDelDetalle),
                alturaDelDetalle,
                asa
            )
        } else {
            ListaDeLugares(state, expandida, p, asa, onTocarLugar, onComoLlegar)
        }
    }
}

@Composable
private fun ListaDeLugares(
    state: UbicacionUiState,
    expandida: Boolean,
    p: PaletaDelMapa,
    asa: Modifier,
    onTocarLugar: (LugarClasificado?) -> Unit,
    onComoLlegar: (LugarClasificado) -> Unit
) {
    val mapa = state.mapa
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = asa.fillMaxWidth().height(40.dp)
    ) {
        Text(
            buildAnnotatedString {
                val n = mapa.cuantosLugares
                append(if (n == 1) "1 lugar " else "$n lugares ")
                withStyle(SpanStyle(color = p.c(p.mut), fontWeight = FontWeight(600))) {
                    append("· ${Textos.cobros(mapa.cobrosEnLugares)}")
                }
            },
            style = estilo(16f, 700, 22f, tabular = true, tracking = -0.01f),
            color = p.c(p.ink),
            maxLines = 1
        )
        if (!expandida && mapa.cambioDeLugar) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .height(26.dp)
                    .clip(CircleShape)
                    .background(p.c(p.partialTint))
                    .padding(horizontal = 10.dp)
                    .testTag(MUDANZA_TAG)
            ) {
                Icon(GLIFO_CAMBIO, null, tint = p.c(p.partial), modifier = Modifier.size(13.dp))
                Text("Cambió de lugar", style = estilo(12f, 700, 16f), color = p.c(p.partial))
            }
        }
    }
    if (state.sinNingunPunto) {
        Text("Sin ubicación medida", style = estilo(13f, 500, 18f), color = p.c(p.mut))
        return
    }
    var transferenciasAbiertas by remember { mutableStateOf(false) }
    var sueltosAbiertos by remember { mutableStateOf(false) }
    LazyColumn(
        userScrollEnabled = expandida,
        verticalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(top = if (expandida) 4.dp else 6.dp, bottom = 16.dp)
    ) {
        val anterior = mapa.lugarAnterior
        val principal = mapa.principal
        if (expandida && anterior != null) {
            item(key = "mudanza") { AvisoDeCambio(state, anterior, principal, p) }
        }
        mapa.lugares.forEach { l ->
            item(key = "l-${l.centro.lat}-${l.centro.lon}") {
                CardDeLugar(state, l, p, onTocarLugar, onComoLlegar)
            }
        }
        if (mapa.transferencias.isNotEmpty() || mapa.sueltos.isNotEmpty()) {
            item(key = "grupo") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(p.c(p.card))
                        .border(1.dp, p.c(p.cardLine), RoundedCornerShape(16.dp))
                ) {
                    if (mapa.transferencias.isNotEmpty()) {
                        FilaDeGrupo(
                            marcador = {
                                Miniatura(
                                    FormaDelMarcador(
                                        Figura.HUECO,
                                        p.mkTran,
                                        mapa.transferencias.sumOf { it.conteo },
                                        diametroDp = 22f
                                    )
                                )
                            },
                            titulo = "Transferencias",
                            detalle = "Marcan dónde estaba el cobrador",
                            p = p,
                            onClick = { transferenciasAbiertas = !transferenciasAbiertas }
                        )
                        if (transferenciasAbiertas) {
                            mapa.transferencias.forEach { t ->
                                FilaDePunto(state, t, p) { onTocarLugar(t) }
                            }
                        }
                    }
                    if (mapa.sueltos.isNotEmpty()) {
                        if (mapa.transferencias.isNotEmpty()) {
                            HorizontalDivider(
                                color = p.c(p.cardLine)
                            )
                        }
                        FilaDeGrupo(
                            marcador = { CirculoPunteado(mapa.sueltos.size, p) },
                            titulo = "Puntos sueltos (${mapa.sueltos.size})",
                            detalle = detalleDeSueltos(mapa.sueltos),
                            p = p,
                            etiqueta = SUELTOS_TAG,
                            onClick = { sueltosAbiertos = !sueltosAbiertos }
                        )
                        if (sueltosAbiertos) {
                            mapa.sueltos.forEach { s ->
                                FilaDePunto(state, s, p) { onTocarLugar(s) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "1 cobro cada uno" o "1 o 2 cobros cada uno". Sin etiqueta de error. */
internal fun detalleDeSueltos(sueltos: List<LugarClasificado>): String =
    if (sueltos.all { it.conteo == 1 }) "1 cobro cada uno" else "1 o 2 cobros cada uno"

@Composable
private fun AvisoDeCambio(
    state: UbicacionUiState,
    anterior: LugarClasificado,
    principal: LugarClasificado?,
    p: PaletaDelMapa
) {
    val hasta = Textos.mes(anterior.lugar.masReciente, BUSINESS_ZONE, state.ahora)
    val donde = anterior.distanciaAlPrincipalM?.let { " a ${distanciaLegible(it)}" }.orEmpty()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.c(p.partialTint))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(MUDANZA_TAG)
    ) {
        Icon(
            GLIFO_CAMBIO,
            null,
            tint = p.c(p.partial),
            modifier = Modifier.padding(top = 1.dp).size(18.dp)
        )
        Column {
            Text("Cambió de lugar de pago", style = estilo(13.5f, 700, 18f), color = p.c(p.partial))
            Text(
                if (principal != null) "Hasta $hasta pagaba$donde" else "Hasta $hasta pagaba en otro lugar",
                style = estilo(12.5f, 400, 17f),
                color = p.c(p.ink).copy(alpha = 0.85f)
            )
        }
    }
}

/**
 * El marcador de la card: el mismo dibujo que el mapa, con su ancla en
 * (15, 14) dp (la gota, con la punta en y = 30). Se pinta con `drawBehind` y
 * no con `Image`: el bitmap trae margen para la sombra y es más grande que la
 * celda de 32 × 30 dp; un `Image` lo encogería.
 */
@Composable
private fun Miniatura(forma: FormaDelMarcador, alfa: Float = 1f) {
    val p = paletaDelMapa()
    val dibujo = recordarMiniaturas(p).de(forma)
    val anclaY = if (forma.figura == Figura.PIN) 30.dp else 14.dp
    Box(
        Modifier
            .size(32.dp, 30.dp)
            .drawBehind {
                val img = dibujo.icono
                drawImage(
                    image = img,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        15.dp.toPx() - img.width * dibujo.anclaX,
                        anclaY.toPx() - img.height * dibujo.anclaY
                    ),
                    alpha = alfa
                )
            }
    )
}

/** La forma de la miniatura según la clase (tamaños del mock: gota 26×34, discos 26/24). */
internal fun formaDeMiniatura(l: LugarClasificado, p: PaletaDelMapa): FormaDelMarcador =
    when (l.clase) {
        ClaseDeLugar.DONDE_MAS_PAGA -> FormaDelMarcador(Figura.PIN, p.mkCasa)
        ClaseDeLugar.COMPARTIDO -> FormaDelMarcador(
            Figura.DISCO,
            p.mkComp,
            l.conteo,
            diametroDp = 24f
        )
        ClaseDeLugar.TRANSFERENCIAS -> FormaDelMarcador(
            Figura.HUECO,
            p.mkTran,
            l.conteo,
            diametroDp = 22f
        )
        else -> FormaDelMarcador(Figura.DISCO, p.deClase(l.clase), l.conteo, diametroDp = 26f)
    }

@Composable
private fun CardDeLugar(
    state: UbicacionUiState,
    l: LugarClasificado,
    p: PaletaDelMapa,
    onTocarLugar: (LugarClasificado?) -> Unit,
    onComoLlegar: (LugarClasificado) -> Unit
) {
    val r = ResumenDeLugar.de(l, state.nombresDeVenta, BUSINESS_ZONE, state.ahora)
    val antes = l.clase == ClaseDeLugar.PAGABA_ANTES
    val forma = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forma)
            .then(if (antes) Modifier else Modifier.background(p.c(p.card)))
            .then(
                if (antes) {
                    Modifier.bordePunteado(p.c(p.cardLine))
                } else {
                    Modifier.border(1.dp, p.c(p.cardLine), forma)
                }
            )
            .clickable { onTocarLugar(l) }
            .padding(start = 10.dp, end = 12.dp, top = 11.dp, bottom = 11.dp)
            .testTag(CARD_TAG + l.clase.name)
    ) {
        Box(Modifier.padding(top = 1.dp)) {
            Miniatura(formaDeMiniatura(l, p), alfa = if (antes) 0.6f else 1f)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(20.dp)) {
                Text(
                    r.titulo,
                    style = estilo(15f, 650, 20f, tracking = -0.01f),
                    color = p.c(p.ink),
                    maxLines = 1
                )
                r.etiqueta?.let {
                    Spacer(Modifier.width(7.dp))
                    Text(
                        it,
                        style = estilo(11f, 700, 14f),
                        color = p.c(p.partial),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(p.c(p.partialTint))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (l.ofreceComoLlegar) {
                    Box(
                        contentAlignment = Alignment.CenterEnd,
                        modifier = Modifier
                            .size(36.dp, 20.dp)
                            .clickable { onComoLlegar(l) }
                            .testTag(COMO_LLEGAR_TAG)
                    ) {
                        Icon(
                            GLIFO_NAVEGAR,
                            "Cómo llegar",
                            tint = p.c(p.brand),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Text(
                r.lineaA,
                style = estilo(12.5f, 400, 18f, tabular = true),
                color = p.c(p.mut),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
            val piezas = listOfNotNull(r.suele, r.abono?.let(::formatMoneyMxn), r.cobrador)
            if (piezas.isNotEmpty() && !antes && l.clase != ClaseDeLugar.COMPARTIDO) {
                RenglonDeCostumbre(r, p)
            }
            Text(
                r.lineaL,
                style = estilo(12f, 400, 17f, tabular = true),
                color = p.c(p.mut),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

/** "🕑 Sáb 10–12 · $220 · Marisol Vega": cada pieza sólo si existe. */
@Composable
private fun RenglonDeCostumbre(r: ResumenDeLugar, p: PaletaDelMapa) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 3.dp).height(18.dp)
    ) {
        Icon(GLIFO_RELOJ, null, tint = p.c(p.mut), modifier = Modifier.size(14.dp))
        val fuertes = listOfNotNull(r.suele, r.abono?.let(::formatMoneyMxn))
        fuertes.forEachIndexed { i, t ->
            if (i > 0) Text("·", style = estilo(12.5f, 400, 18f), color = p.c(p.faint))
            Text(
                t,
                style = estilo(12.5f, 650, 18f, tabular = true),
                color = p.c(p.ink),
                maxLines = 1
            )
        }
        r.cobrador?.let {
            if (fuertes.isNotEmpty()) {
                Text(
                    "·",
                    style = estilo(12.5f, 400, 18f),
                    color = p.c(p.faint)
                )
            }
            Text(
                it,
                style = estilo(12.5f, 400, 18f),
                color = p.c(p.ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun Modifier.bordePunteado(color: androidx.compose.ui.graphics.Color): Modifier =
    drawBehind {
        val g = 1.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(g / 2, g / 2),
            size = androidx.compose.ui.geometry.Size(size.width - g, size.height - g),
            cornerRadius = CornerRadius(16.dp.toPx()),
            style = Stroke(
                width = g,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
            )
        )
    }

@Composable
private fun FilaDeGrupo(
    marcador: @Composable () -> Unit,
    titulo: String,
    detalle: String,
    p: PaletaDelMapa,
    onClick: () -> Unit,
    etiqueta: String = ""
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 12.dp)
            .then(if (etiqueta.isNotEmpty()) Modifier.testTag(etiqueta) else Modifier)
    ) {
        Box(Modifier.size(32.dp, 28.dp)) { marcador() }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, style = estilo(14f, 650, 18f), color = p.c(p.ink))
            Text(detalle, style = estilo(12f, 400, 16f), color = p.c(p.mut))
        }
        Spacer(Modifier.width(10.dp))
        Icon(GLIFO_PLEGADO, null, tint = p.c(p.faint), modifier = Modifier.size(18.dp))
    }
}

/** El círculo punteado con el número de sueltos (`.far-dot`). */
@Composable
private fun CirculoPunteado(n: Int, p: PaletaDelMapa) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset(x = 4.dp, y = 3.dp)
            .size(22.dp)
            .drawBehind {
                val g = 2.dp.toPx()
                drawCircle(
                    color = p.c(p.mkAnt),
                    radius = size.minDimension / 2 - g / 2,
                    style = Stroke(
                        g,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(g * 1.5f, g * 1.5f))
                    )
                )
            }
    ) {
        Text("$n", style = estilo(11f, 700, 12f, tabular = true), color = p.c(p.mut))
    }
}

/** Un punto suelto o de transferencia abierto: fecha, monto y cobrador. */
@Composable
private fun FilaDePunto(
    state: UbicacionUiState,
    l: LugarClasificado,
    p: PaletaDelMapa,
    onClick: () -> Unit
) {
    val m = l.lugar.mediciones.first()
    val partes = listOfNotNull(
        Textos.dia(m.fecha, BUSINESS_ZONE, state.ahora),
        m.importe?.takeIf { it.signum() > 0 }?.let(::formatMoneyMxn),
        Textos.nombre(m.cobrador)
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clickable(onClick = onClick)
            .padding(start = 52.dp, end = 12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (l.conteo > 1) {
                    Textos.cobros(
                        l.conteo
                    )
                } else {
                    partes.joinToString(ResumenDeLugar.SEP)
                },
                style = estilo(13f, 600, 18f, tabular = true),
                color = p.c(p.ink)
            )
            l.distanciaAlPrincipalM?.let {
                Text(
                    "a ${distanciaLegible(it)}",
                    style = estilo(12f, 400, 16f, tabular = true),
                    color = p.c(p.mut)
                )
            }
        }
    }
}

/**
 * **Las tres alturas del detalle de un lugar**, como el mock aprobado
 * (`docs/design/mocks/detalle-del-lugar.html`). Tocar un lugar abre la
 * [MEDIA]; arrastrar hacia abajo la baja a [MINIMA], que nunca desaparece;
 * arrastrar hacia arriba abre el historial [COMPLETA]. Cerrar es la flecha "<"
 * o el atrás del teléfono, nunca el arrastre.
 */
enum class AlturaDelDetalle { MINIMA, MEDIA, COMPLETA }

/** `testTag` de "Ver todos" (sube el detalle al historial completo). */
const val VER_TODOS_TAG: String = "ubicacion_ver_todos"

/** `testTag` del encabezado del detalle (tocarlo en la mínima sube a la media). */
const val ENCABEZADO_DEL_DETALLE_TAG: String = "ubicacion_encabezado_detalle"

/** Lo que el detalle le pide a la pantalla. */
internal class DetalleAcciones(
    val onTocarLugar: (LugarClasificado?) -> Unit,
    val onComoLlegar: (LugarClasificado) -> Unit,
    val onAbrirFiltros: () -> Unit,
    val onAltura: (AlturaDelDetalle) -> Unit
)

/** El detalle del lugar tocado, en la altura que tenga la hoja. */
@Composable
private fun DetalleDelLugar(
    state: UbicacionUiState,
    l: LugarClasificado,
    p: PaletaDelMapa,
    acciones: DetalleAcciones,
    altura: AlturaDelDetalle,
    asa: Modifier
) {
    val r = ResumenDeLugar.de(l, state.nombresDeVenta, BUSINESS_ZONE, state.ahora)
    EncabezadoDelDetalle(l, r, p, acciones, altura, asa)
    when (altura) {
        AlturaDelDetalle.MINIMA -> LineaDeDatos(l, r, p)
        AlturaDelDetalle.MEDIA -> {
            FichasDelLugar(r, p)
            Text(
                "Último cobro",
                style = estilo(11.5f, 650, 15f),
                color = p.c(p.mut),
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 10.dp)
            )
            val ultimo = l.lugar.mediciones.maxByOrNull { it.fecha }
            if (ultimo != null) {
                RenglonDeCobro(
                    state,
                    ultimo,
                    p,
                    Modifier.padding(top = 4.dp).bordeDeRenglon(p, primero = true, ultimo = true)
                )
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { acciones.onAltura(AlturaDelDetalle.COMPLETA) }
                    .padding(start = 2.dp, end = 2.dp, top = 10.dp, bottom = 4.dp)
                    .testTag(VER_TODOS_TAG)
            ) {
                Text(
                    "${Textos.cobros(l.conteo)} · ${r.rango}",
                    style = estilo(13f, 600, 18f, tabular = true),
                    color = p.c(p.mut)
                )
                Text("Ver todos", style = estilo(13f, 700, 18f), color = p.c(p.brand))
            }
        }
        AlturaDelDetalle.COMPLETA -> HistorialCompleto(state, l, r, p, acciones.onAbrirFiltros)
    }
}

@Composable
private fun EncabezadoDelDetalle(
    l: LugarClasificado,
    r: ResumenDeLugar,
    p: PaletaDelMapa,
    acciones: DetalleAcciones,
    altura: AlturaDelDetalle,
    asa: Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = asa
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = altura == AlturaDelDetalle.MINIMA
            ) { acciones.onAltura(AlturaDelDetalle.MEDIA) }
            .testTag(ENCABEZADO_DEL_DETALLE_TAG)
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .size(40.dp)
                .clickable { acciones.onTocarLugar(null) }
                .testTag(VOLVER_A_LA_LISTA_TAG)
        ) {
            Icon(
                GLIFO_ATRAS,
                "Volver a la lista",
                tint = p.c(p.ink),
                modifier = Modifier.size(22.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            // Dos renglones como tope: Manrope es más ancha que la fuente del mock y
            // "Otro lugar donde paga" no cabe junto a "Cómo llegar" en 360 dp.
            Text(
                r.titulo,
                style = estilo(17f, 700, 21f, tracking = -0.015f),
                color = p.c(p.ink),
                maxLines = 2
            )
            val sub = l.distanciaAlPrincipalM?.let { "A ${distanciaLegible(it)} del principal" } ?: r.lineaA
            Text(
                sub,
                style = estilo(12.5f, 400, 17f, tabular = true),
                color = p.c(p.mut),
                maxLines = 1
            )
        }
        if (l.ofreceComoLlegar) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clickable { acciones.onComoLlegar(l) }
                    .padding(horizontal = 2.dp, vertical = 14.dp)
                    .testTag(COMO_LLEGAR_TAG)
            ) {
                Icon(GLIFO_NAVEGAR, null, tint = p.c(p.brand), modifier = Modifier.size(18.dp))
                Text("Cómo llegar", style = estilo(13.5f, 700, 18f), color = p.c(p.brand))
            }
        }
    }
}

/** La mínima: cuándo, cuánto y cuántos cobros, en un renglón bajo el título. */
@Composable
private fun LineaDeDatos(l: LugarClasificado, r: ResumenDeLugar, p: PaletaDelMapa) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 46.dp, top = 2.dp)
    ) {
        Icon(GLIFO_RELOJ, null, tint = p.c(p.mut), modifier = Modifier.size(14.dp))
        val fuertes = listOfNotNull(r.suele, r.abono?.let { formatMoneyMxn(it) })
        Text(
            buildAnnotatedString {
                fuertes.forEach { f ->
                    val fuerte = SpanStyle(fontWeight = FontWeight(650), color = p.c(p.ink))
                    withStyle(fuerte) { append(f) }
                    withStyle(SpanStyle(color = p.c(p.faint))) { append("  ·  ") }
                }
                append(Textos.cobros(l.conteo))
            },
            style = estilo(13f, 400, 20f, tabular = true),
            color = p.c(p.mut),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Las cuatro fichas en dos renglones apretados. */
@Composable
private fun FichasDelLugar(r: ResumenDeLugar, p: PaletaDelMapa) {
    val datos = listOfNotNull(
        r.suele?.let { "Suele pagar" to it },
        r.abono?.let { "Abono típico" to formatMoneyMxn(it) },
        r.venta?.let { "Venta" to it },
        r.cobrador?.let { "Cobra aquí" to it }
    )
    datos.chunked(2).forEachIndexed { i, par ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(top = if (i == 0) 4.dp else 6.dp)
        ) {
            par.forEach { (k, v) ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.c(p.card))
                        .border(1.dp, p.c(p.cardLine), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(k, style = estilo(11f, 400, 15f), color = p.c(p.mut))
                    Text(
                        v,
                        style = estilo(14f, 650, 19f, tabular = true),
                        color = p.c(p.ink),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (par.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** La completa: fichas, resumen y el historial entero, perezoso. */
@Composable
private fun HistorialCompleto(
    state: UbicacionUiState,
    l: LugarClasificado,
    r: ResumenDeLugar,
    p: PaletaDelMapa,
    onAbrirFiltros: () -> Unit
) {
    val cobros = l.lugar.mediciones.sortedByDescending { it.fecha }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "fichas") { Column { FichasDelLugar(r, p) } }
        item(key = "resumen") {
            Text(
                "${Textos.cobros(l.conteo)} · ${r.rango}",
                style = estilo(12.5f, 650, 17f, tabular = true),
                color = p.c(p.ink),
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 10.dp, bottom = 8.dp)
            )
        }
        itemsIndexed(cobros, key = { i, _ -> "c-$i" }) { i, m ->
            RenglonDeCobro(
                state,
                m,
                p,
                Modifier.bordeDeRenglon(p, primero = i == 0, ultimo = i == cobros.lastIndex)
            )
        }
        if (!state.filtro.verVisitas && state.totalVisitas > 0) {
            item(key = "visitas") {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAbrirFiltros)
                        .padding(start = 2.dp, end = 2.dp, top = 10.dp)
                ) {
                    Text("Visitas ocultas", style = estilo(13f, 600, 18f), color = p.c(p.mut))
                    Text("Filtrar", style = estilo(13f, 700, 18f), color = p.c(p.brand))
                }
            }
        }
    }
}

@Composable
private fun RenglonDeCobro(
    state: UbicacionUiState,
    m: com.example.msp_app.core.geo.MedicionDelCobro,
    p: PaletaDelMapa,
    modifier: Modifier
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(Modifier.width(52.dp), horizontalAlignment = Alignment.End) {
            Text(
                Textos.dia(m.fecha, BUSINESS_ZONE, state.ahora),
                style = estilo(12.5f, 700, 18f, tabular = true),
                color = p.c(p.ink),
                maxLines = 1
            )
            Text(
                Textos.hora(m.fecha, BUSINESS_ZONE),
                style = estilo(11f, 500, 14f, tabular = true),
                color = p.c(p.mut)
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.padding(top = 6.dp).size(7.dp).clip(CircleShape).background(p.c(p.paid)))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text("Abono", style = estilo(14f, 600, 18f), color = p.c(p.ink))
            Text(
                "${Textos.forma(m.formaCobroId)} · ${Textos.nombre(m.cobrador)}",
                style = estilo(11.5f, 500, 15f),
                color = p.c(p.mut),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        m.importe?.takeIf { it.signum() > 0 }?.let {
            Spacer(Modifier.width(10.dp))
            Text(
                formatMoneyMxn(it),
                style = estilo(14f, 750, 18f, tabular = true),
                color = p.c(p.ink)
            )
        }
    }
}

/**
 * Un renglón de la tarjeta del historial, dibujado por pedazos para que la
 * tarjeta pueda ser perezosa: cada renglón pinta su fondo, sus orillas y su
 * línea de abajo (divisor o borde); el primero y el último, las esquinas.
 */
private fun Modifier.bordeDeRenglon(p: PaletaDelMapa, primero: Boolean, ultimo: Boolean): Modifier =
    this.drawBehind {
        val radio = 14.dp.toPx()
        val grosor = 1.dp.toPx()
        val arriba = if (primero) grosor / 2 else -radio * 2
        val abajo = if (ultimo) size.height - grosor / 2 else size.height + radio * 2
        clipRect {
            drawRoundRect(
                color = p.c(p.card),
                topLeft = Offset(0f, arriba),
                size = Size(size.width, abajo - arriba),
                cornerRadius = CornerRadius(radio)
            )
            drawRoundRect(
                color = p.c(p.cardLine),
                topLeft = Offset(grosor / 2, arriba),
                size = Size(size.width - grosor, abajo - arriba),
                cornerRadius = CornerRadius(radio),
                style = Stroke(grosor)
            )
            if (!ultimo) {
                drawLine(
                    p.c(p.cardLine),
                    Offset(0f, size.height - grosor / 2),
                    Offset(size.width, size.height - grosor / 2),
                    grosor
                )
            }
        }
    }

/** Todas las mediciones de la lista (para contar lo que se ve). */
internal fun UbicacionUiState.cobrosVisibles(): Int = mapa.todos.mediciones().size
