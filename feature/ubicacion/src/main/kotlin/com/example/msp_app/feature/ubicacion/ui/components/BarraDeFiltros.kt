package com.example.msp_app.feature.ubicacion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.msp_app.feature.ubicacion.domain.FiltroDeLugares
import com.example.msp_app.feature.ubicacion.domain.Textos
import com.example.msp_app.feature.ubicacion.domain.VentanaDelFiltro
import com.example.msp_app.feature.ubicacion.ui.PaletaDelMapa
import com.example.msp_app.feature.ubicacion.ui.UbicacionUiState
import com.example.msp_app.feature.ubicacion.ui.estilo
import com.example.msp_app.feature.ubicacion.ui.paletaDelMapa

/** `testTag` del switch "Ver cada punto". */
const val SIN_AGRUPAR_TAG: String = "ubicacion_sin_agrupar"

/** `testTag` del chip "Visitas". */
const val CHIP_VISITAS_TAG: String = "ubicacion_chip_visitas"

/** `testTag` del panel de filtros. */
const val PANEL_FILTROS_TAG: String = "ubicacion_panel_filtros"

/**
 * **El panel de filtros**: Venta, Tipo, Periodo, Cobrador, "Ver puntos
 * sueltos" y "Ver cada punto". Todo control mide 50 dp o más y **se aplica al
 * tocar**, sin botón de "Aplicar": el renglón de abajo dice cuánto queda.
 *
 * Nace en "Todo" y con las visitas apagadas: recortar el periodo borra la señal
 * más valiosa (el cambio de lugar), y el mapa nace con puros cobros.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("LongMethod")
fun PanelDeFiltros(
    state: UbicacionUiState,
    onFiltro: (FiltroDeLugares) -> Unit,
    modifier: Modifier = Modifier,
    asa: Modifier = Modifier
) {
    val p = paletaDelMapa()
    val f = state.filtro
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(p.c(p.sheet))
            .padding(horizontal = 16.dp)
            .testTag(PANEL_FILTROS_TAG)
    ) {
        Box(asa.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(
                    36.dp,
                    4.dp
                ).alpha(0.6f).clip(RoundedCornerShape(2.dp)).background(p.c(p.faint))
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = asa.fillMaxWidth().height(48.dp)
        ) {
            Text("Filtrar", style = estilo(18f, 700, 24f, tracking = -0.015f), color = p.c(p.ink))
            Text(
                "Limpiar",
                style = estilo(14f, 700, 20f),
                color = p.c(p.brand),
                modifier = Modifier.clickable {
                    onFiltro(
                        FiltroDeLugares()
                    )
                }.padding(vertical = 12.dp)
            )
        }
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
            if (state.cobrosPorVenta.isNotEmpty()) {
                Etiqueta("Venta", p)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Chip("Todas", null, f.ventaId == null, p) { onFiltro(f.copy(ventaId = null)) }
                    state.cobrosPorVenta.entries.sortedByDescending { it.value }.forEach { (venta, n) ->
                        Chip(
                            state.nombreDeVenta(venta),
                            n,
                            f.ventaId == venta,
                            p
                        ) { onFiltro(f.copy(ventaId = venta)) }
                    }
                }
            }
            Etiqueta("Tipo", p)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Chip(
                    "Cobros",
                    state.totalCobros,
                    f.verCobros,
                    p
                ) { onFiltro(f.copy(verCobros = !f.verCobros)) }
                Chip("Visitas", state.totalVisitas, f.verVisitas, p, CHIP_VISITAS_TAG) {
                    onFiltro(f.copy(verVisitas = !f.verVisitas))
                }
                Chip("Promesas", state.totalPromesas, f.verPromesas, p) {
                    onFiltro(f.copy(verPromesas = !f.verPromesas))
                }
            }
            Etiqueta("Periodo", p)
            Segmentado(
                opciones = VentanaDelFiltro.entries.map { it.etiqueta },
                elegida = VentanaDelFiltro.entries.indexOf(f.ventana),
                p = p
            ) { i -> onFiltro(f.copy(ventana = VentanaDelFiltro.entries[i])) }
            if (state.cobradoresDisponibles.size > 1) {
                Etiqueta("Cobrador", p)
                val nombres = state.cobradoresDisponibles
                if (nombres.size <= 2) {
                    Segmentado(
                        opciones = listOf("Todos") + nombres.map(Textos::nombre),
                        elegida = if (f.cobrador == null) 0 else nombres.indexOf(f.cobrador) + 1,
                        p = p
                    ) { i -> onFiltro(f.copy(cobrador = if (i == 0) null else nombres[i - 1])) }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Chip(
                            "Todos",
                            null,
                            f.cobrador == null,
                            p
                        ) { onFiltro(f.copy(cobrador = null)) }
                        nombres.forEach { c ->
                            Chip(
                                Textos.nombre(c),
                                null,
                                f.cobrador == c,
                                p
                            ) { onFiltro(f.copy(cobrador = c)) }
                        }
                    }
                }
            }
            Column(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(p.c(p.card))
                    .border(1.dp, p.c(p.cardLine), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp)
            ) {
                Interruptor("Ver puntos sueltos", "Lugares con 1 o 2 cobros", f.verSueltos, p) {
                    onFiltro(f.copy(verSueltos = !f.verSueltos))
                }
                HorizontalDivider(color = p.c(p.cardLine))
                Interruptor(
                    "Ver cada punto",
                    "Sin juntar los de 30 m",
                    f.sinAgrupar,
                    p,
                    SIN_AGRUPAR_TAG
                ) {
                    onFiltro(f.copy(sinAgrupar = !f.sinAgrupar))
                }
            }
            val n = state.mapa.cobrosEnLugares
            val l = state.mapa.cuantosLugares
            Text(
                buildAnnotatedString {
                    append("Se ven ")
                    withStyle(
                        SpanStyle(color = p.c(p.ink), fontWeight = FontWeight.Bold)
                    ) { append(Textos.cobros(n)) }
                    append(if (l == 1) " en 1 lugar" else " en $l lugares")
                },
                style = estilo(12.5f, 400, 18f, tabular = true),
                color = p.c(p.mut),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun Etiqueta(texto: String, p: PaletaDelMapa) {
    Text(
        texto,
        style = estilo(12.5f, 650, 17f),
        color = p.c(p.mut),
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 12.dp, bottom = 7.dp)
    )
}

@Composable
private fun Chip(
    texto: String,
    cuenta: Int?,
    puesto: Boolean,
    p: PaletaDelMapa,
    etiqueta: String = "",
    onClick: () -> Unit
) {
    val tinta = if (puesto) p.c(p.brand) else p.c(p.ink)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (puesto) p.c(p.brandTint) else p.c(p.card))
            .border(1.dp, if (puesto) p.c(p.brand) else p.c(p.cardLine), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
            .then(if (etiqueta.isNotEmpty()) Modifier.testTag(etiqueta) else Modifier)
    ) {
        if (puesto) Icon(GLIFO_PALOMITA, null, tint = tinta, modifier = Modifier.size(16.dp))
        Text(
            texto,
            style = estilo(14f, 600, 20f),
            color = tinta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        cuenta?.let {
            Text(
                "$it",
                style = estilo(12f, 600, 16f, tabular = true),
                color = if (puesto) p.c(p.brand).copy(alpha = 0.8f) else p.c(p.mut)
            )
        }
    }
}

@Composable
private fun Segmentado(
    opciones: List<String>,
    elegida: Int,
    p: PaletaDelMapa,
    onElegir: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(p.c(p.card))
            .border(1.dp, p.c(p.cardLine), RoundedCornerShape(14.dp))
            .padding(4.dp)
    ) {
        opciones.forEachIndexed { i, o ->
            val on = i == elegida
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) p.c(p.brand) else Color.Transparent)
                    .clickable { onElegir(i) }
            ) {
                Text(
                    o,
                    style = estilo(13.5f, 600, 18f),
                    color = if (on) Color.White else p.c(p.mut),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun Interruptor(
    titulo: String,
    detalle: String,
    encendido: Boolean,
    p: PaletaDelMapa,
    etiqueta: String = "",
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .then(if (etiqueta.isNotEmpty()) Modifier.testTag(etiqueta) else Modifier)
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = estilo(14.5f, 600, 19f), color = p.c(p.ink))
            Text(detalle, style = estilo(12f, 400, 16f), color = p.c(p.mut))
        }
        Box(
            Modifier
                .size(46.dp, 28.dp)
                .clip(CircleShape)
                .background(if (encendido) p.c(p.brand) else p.c(p.line))
        ) {
            Box(
                Modifier
                    .offset(x = if (encendido) 21.dp else 3.dp, y = 3.dp)
                    .size(22.dp)
                    .shadow(1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
