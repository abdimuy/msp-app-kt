package com.example.msp_app.feature.ubicacion.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.ubicacion.domain.FiltroDeLugares
import com.example.msp_app.feature.ubicacion.domain.VentanaDelFiltro

/** `testTag` del botón que abre los filtros. */
const val ABRIR_FILTROS_TAG: String = "ubicacion_abrir_filtros"

/** `testTag` de la opción "ver todos los puntos sueltos". */
const val SIN_AGRUPAR_TAG: String = "ubicacion_sin_agrupar"

/**
 * Los filtros, detrás de un solo control.
 *
 * ## Por qué van plegados y el mapa no nace con una barra de chips encima
 *
 * Porque **por omisión no filtran nada**, y eso es deliberado: la señal más
 * valiosa de esta pantalla —dos grupos de edades distintas, o sea una mudanza—
 * desaparece en cuanto se recorta el tiempo. Una barra de chips siempre visible
 * invita a tocarla, y el primer toque tapa justo lo que había que ver.
 *
 * El control muestra un punto cuando hay algún filtro puesto, para que nadie se
 * quede mirando un mapa recortado creyendo que es todo lo que hay.
 *
 * **"Ver todos los puntos sueltos"** es la salida de emergencia que pidió el
 * dueño: apaga el agrupamiento y dibuja cada medición por su cuenta. Existir es
 * más importante que ser bonita — es lo que queda cuando el agrupamiento no
 * convence.
 */
@Composable
fun BarraDeFiltros(
    filtro: FiltroDeLugares,
    ventas: List<Int>,
    cobradores: List<String>,
    onFiltro: (FiltroDeLugares) -> Unit,
    modifier: Modifier = Modifier
) {
    var abierto by remember { mutableStateOf(false) }
    Surface(
        onClick = { abierto = true },
        color = MspTheme.colors.surface,
        shape = MspTheme.shapes.control,
        shadowElevation = SOMBRA,
        // **El alto mínimo tocable del repo, y va en el contenedor.**
        // Con sólo el padding del texto el chip medía 75 px (37.5 dp) y
        // `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest` lo cazó: por debajo
        // de 100 px el cobrador toca y el tap no entra — sobre un mapa, además,
        // un toque que falla no hace nada visible y no sabe si tocó mal o si la
        // app se colgó. El mínimo NO se baja; se sube la implementación.
        modifier = modifier
            .defaultMinSize(minHeight = TOQUE_MINIMO)
            .testTag(ABRIR_FILTROS_TAG)
    ) {
        Text(
            text = if (filtro.estaLimpio) FILTRAR else FILTRADO,
            style = MspTheme.type.buttonSmall,
            color = if (filtro.estaLimpio) MspTheme.colors.onSurface else MspTheme.colors.brand,
            modifier = Modifier
                .padding(horizontal = MspTheme.spacing.md)
                .defaultMinSize(minHeight = TOQUE_MINIMO)
                .wrapContentHeight(Alignment.CenterVertically)
        )
    }
    DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)) {
            Encabezado(CUANDO)
            VentanaDelFiltro.entries.forEach { v ->
                Opcion(v.etiqueta, filtro.ventana == v) {
                    onFiltro(filtro.copy(ventana = v))
                }
            }
            if (ventas.size > 1) {
                Encabezado(CUAL_CUENTA)
                Opcion(TODAS, filtro.ventaId == null) { onFiltro(filtro.copy(ventaId = null)) }
                ventas.forEach { v ->
                    Opcion("Cuenta $v", filtro.ventaId == v) { onFiltro(filtro.copy(ventaId = v)) }
                }
            }
            if (cobradores.size > 1) {
                Encabezado(QUIEN_COBRO)
                Opcion(TODOS, filtro.cobrador == null) { onFiltro(filtro.copy(cobrador = null)) }
                cobradores.forEach { c ->
                    Opcion(c, filtro.cobrador == c) { onFiltro(filtro.copy(cobrador = c)) }
                }
            }
            Encabezado(COMO_SE_VE)
            DropdownMenuItem(
                text = { Text(if (filtro.sinAgrupar) AGRUPADOS else SUELTOS) },
                onClick = { onFiltro(filtro.copy(sinAgrupar = !filtro.sinAgrupar)) },
                modifier = Modifier.testTag(SIN_AGRUPAR_TAG)
            )
        }
    }
}

@Composable
private fun Encabezado(texto: String) {
    Text(
        text = texto,
        style = MspTheme.type.caption,
        color = MspTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(
            horizontal = MspTheme.spacing.md,
            vertical = MspTheme.spacing.xs
        )
    )
}

@Composable
private fun Opcion(texto: String, puesto: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = texto,
                color = if (puesto) MspTheme.colors.brand else MspTheme.colors.onSurface
            )
        },
        onClick = onClick
    )
}

private val SOMBRA = 3.dp

/** El área tocable mínima del repo: 50 dp, más estricto que los 48 de Material. */
private val TOQUE_MINIMO = 50.dp

private const val FILTRAR = "Filtrar"
private const val FILTRADO = "Filtrado"
private const val CUANDO = "Cuándo"
private const val CUAL_CUENTA = "Cuál cuenta"
private const val QUIEN_COBRO = "Quién cobró"
private const val COMO_SE_VE = "Cómo se ve"
private const val TODAS = "Todas"
private const val TODOS = "Todos"
private const val SUELTOS = "Ver puntos sueltos"
private const val AGRUPADOS = "Ver puntos agrupados"
