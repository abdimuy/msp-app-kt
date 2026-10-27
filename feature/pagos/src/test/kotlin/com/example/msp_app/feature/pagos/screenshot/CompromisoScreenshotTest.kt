package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.domain.model.CompromisoDeLaVenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDeCompromiso
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.TipoDeCompromiso
import com.example.msp_app.feature.pagos.ui.DetalleVentaContent
import com.example.msp_app.feature.pagos.ui.DetalleVentaUiState
import com.example.msp_app.feature.pagos.ui.ListaFixtures
import com.example.msp_app.feature.pagos.ui.components.FilaDeCliente
import java.time.LocalTime
import org.junit.Test

/**
 * El compromiso en pantalla (plan visitas P2, Tarea 20; spec §5.7 B, C, E, F;
 * E-APP-115): la tarjeta con abonos (B, cita sin hora: F), la tarjeta de una
 * venta nueva con promesa (C) y el renglón del detalle (E), en los dos temas;
 * la tarjeta también a letra muy grande.
 */
class CompromisoScreenshotTest : PagosScreenshotTest() {

    private val hoy = ListaFixtures.HOY

    private val citaSinHora = CompromisoDeLaVenta(
        TipoDeCompromiso.CITA,
        EstadoDeCompromiso.VIGENTE_FUTURA,
        hoy.plusDays(3)
    )

    private val promesa = CompromisoDeLaVenta(
        TipoDeCompromiso.PROMESA,
        EstadoDeCompromiso.VIGENTE_FUTURA,
        hoy.plusDays(3),
        montoCentavos = 50_000
    )

    /**
     * rev-t20: los fixtures pintan estados que la derivación SÍ puede dar
     * juntos. B es el chip de dinero ("Abonó parcial") con la cita debajo; el
     * detalle de la cita abonó parcial esta semana, y el de la promesa
     * incumplida no tiene nada esta semana ("Falta pasar").
     */
    private val abonoParcial = ListaFixtures.estado(
        EstadoCuenta.ABONO_PARCIAL,
        abonoDelPeriodo = ListaFixtures.dinero("100")
    )

    private val sinTocar = ListaFixtures.estado(EstadoCuenta.SIN_TOCAR)

    private fun ClienteEnLista.conEstado(e: EstadoDelPeriodo) =
        copy(ventas = ventas.map { it.copy(venta = it.venta.copy(estado = e)) })

    private fun ClienteEnLista.con(c: CompromisoDeLaVenta) =
        copy(ventas = ventas.map { it.copy(venta = it.venta.copy(compromiso = c)) })

    @Test
    fun `tarjeta con abonos y cita sin hora light`() = tarjeta(
        "abonos_cita",
        false,
        ListaFixtures.guadalupe().conEstado(abonoParcial).con(citaSinHora)
    )

    @Test
    fun `tarjeta con abonos y cita sin hora dark`() = tarjeta(
        "abonos_cita",
        true,
        ListaFixtures.guadalupe().conEstado(abonoParcial).con(citaSinHora)
    )

    @Test
    fun `tarjeta con abonos y cita sin hora muy grande`() = tarjeta(
        "abonos_cita",
        false,
        ListaFixtures.guadalupe().conEstado(abonoParcial).con(citaSinHora),
        FontSizeLevel.MUY_GRANDE
    )

    @Test
    fun `tarjeta nueva con promesa light`() = tarjeta(
        "nueva_promesa",
        false,
        ListaFixtures.conVentaNueva().con(promesa)
    )

    @Test
    fun `tarjeta nueva con promesa dark`() = tarjeta(
        "nueva_promesa",
        true,
        ListaFixtures.conVentaNueva().con(promesa)
    )

    /** rev-t20: el chip ya dice la promesa; la tarjeta no la repite. */
    @Test
    fun `tarjeta sin repetir la promesa del chip light`() = tarjeta(
        "promesa_del_chip",
        false,
        ListaFixtures.guadalupe().conEstado(
            ListaFixtures.estado(
                EstadoCuenta.PROMETIO_PROXIMA,
                fechaPromesa = promesa.dia,
                montoPrometido = ListaFixtures.dinero("500")
            )
        ).con(promesa)
    )

    @Test
    fun `detalle con cita light`() = detalle(
        "cita",
        false,
        citaSinHora.copy(hora = LocalTime.of(16, 30)),
        abonoParcial
    )

    @Test
    fun `detalle con cita dark`() = detalle(
        "cita",
        true,
        citaSinHora.copy(hora = LocalTime.of(16, 30)),
        abonoParcial
    )

    @Test
    fun `detalle con promesa incumplida light`() = detalle(
        "promesa_incumplida",
        false,
        promesa.copy(estado = EstadoDeCompromiso.INCUMPLIDA, dia = hoy.minusDays(2)),
        sinTocar
    )

    private fun tarjeta(
        nombre: String,
        dark: Boolean,
        cliente: ClienteEnLista,
        nivel: FontSizeLevel = FontSizeLevel.NORMAL
    ) = capture(
        name = "pagos_compromiso_tarjeta_${nombre}_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Box(modifier = Modifier.padding(MspTheme.spacing.md)) {
            FilaDeCliente(cliente = cliente, hoy = hoy, onAbrirCliente = {})
        }
    }

    private fun detalle(
        nombre: String,
        dark: Boolean,
        c: CompromisoDeLaVenta,
        estado: EstadoDelPeriodo
    ) = capture(
        name = "pagos_compromiso_detalle_${nombre}_${tema(dark)}",
        dark = dark
    ) {
        DetalleVentaContent(
            state = DetalleVentaUiState(
                cargando = false,
                detalle = ventaDelMock().copy(estado = estado, compromiso = c, hoy = hoy)
            ),
            onAtras = {},
            onRegistrarAbono = {},
            onRegistrarVisita = {},
            onUsarLiquidacion = {},
            onVerAbonos = {},
            onVerGarantia = {}
        )
    }

    private fun tema(dark: Boolean) = if (dark) "dark" else "light"
}
