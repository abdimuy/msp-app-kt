package com.example.msp_app.feature.visitas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.msp_app.core.designsystem.theme.MspColors
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita

/**
 * La terna que pinta un desenlace: **color + ícono + texto**, nunca color solo.
 *
 * Los colores salen de `docs/design/paleta-mocks-a-mspcolors.md` §4 —la tabla
 * de los cinco estados del semáforo—, **no** de la esmeralda del mock, que se
 * quedó como registro de lo acordado (Ruling C).
 */
@Immutable
data class ResultadoVisual(
    val icono: ImageVector,
    val contenido: Color,
    val fondo: Color,
    /** `.st.negado{background:var(--red)}`: el único que va en sólido invertido. */
    val relleno: Boolean
)

/** El mapa desenlace → presentación. Puro: se prueba sin Robolectric. */
internal object ResultadoUi {

    fun iconoDe(resultado: ResultadoDeVisita): ImageVector = when (resultado) {
        ResultadoDeVisita.NO_ESTABA -> VisitasIconos.NoEstaba
        ResultadoDeVisita.VISITE_VUELVO -> VisitasIconos.Vuelvo
        ResultadoDeVisita.PROMETIO -> VisitasIconos.Prometio
        ResultadoDeVisita.CITA -> VisitasIconos.Cita
        ResultadoDeVisita.SE_NEGO -> VisitasIconos.Negado
    }

    /**
     * Color de contenido. §4 de la tabla: "vuelvo" es el ámbar de
     * `statusPartial` (trampa de nombre: ese token NO es el parcial del dinero),
     * "cita" es el violeta de `promise`, y "se negó" va invertido sobre rojo
     * sólido, con `onDanger` de contenido — el precedente ya cableado de
     * `PrimaryFieldButtonVariant.Danger`.
     */
    fun contenidoDe(resultado: ResultadoDeVisita, colors: MspColors): Color = when (resultado) {
        ResultadoDeVisita.NO_ESTABA -> colors.statusPending
        ResultadoDeVisita.VISITE_VUELVO -> colors.statusPartial
        ResultadoDeVisita.PROMETIO -> colors.statusOverdue
        ResultadoDeVisita.CITA -> colors.promise
        ResultadoDeVisita.SE_NEGO -> colors.onDanger
    }

    /** Fondo (tint, o relleno sólido en "se negó"). */
    fun fondoDe(resultado: ResultadoDeVisita, colors: MspColors): Color = when (resultado) {
        ResultadoDeVisita.NO_ESTABA -> colors.statusPendingTint
        ResultadoDeVisita.VISITE_VUELVO -> colors.statusPartialTint
        ResultadoDeVisita.PROMETIO -> colors.statusOverdueTint
        ResultadoDeVisita.CITA -> colors.promiseTint
        ResultadoDeVisita.SE_NEGO -> colors.statusOverdue
    }

    fun esRelleno(resultado: ResultadoDeVisita): Boolean = resultado == ResultadoDeVisita.SE_NEGO
}

/**
 * La presentación completa de [resultado], resuelta contra el tema vigente.
 *
 * El anillo de selección toma el color **del desenlace elegido**, no azul: es la
 * regla de "selección con estado" de §3 de la tabla de paleta — el azul `brand`
 * queda para la selección genérica y para el CTA, que es lo protagónico.
 */
@Composable
internal fun resultadoVisualDe(resultado: ResultadoDeVisita): ResultadoVisual {
    val colors = MspTheme.colors
    return ResultadoVisual(
        icono = ResultadoUi.iconoDe(resultado),
        contenido = ResultadoUi.contenidoDe(resultado, colors),
        fondo = ResultadoUi.fondoDe(resultado, colors),
        relleno = ResultadoUi.esRelleno(resultado)
    )
}
