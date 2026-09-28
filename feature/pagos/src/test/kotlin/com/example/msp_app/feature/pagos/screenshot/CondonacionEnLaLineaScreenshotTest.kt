package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.BitacoraDelCliente
import com.example.msp_app.feature.pagos.domain.model.CondonacionDelHistorial
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import com.example.msp_app.feature.pagos.ui.components.ContactoEnLinea
import java.math.BigDecimal
import java.time.Instant
import org.junit.Test

/**
 * **El renglón de la condonación**, junto a un abono, en claro y oscuro.
 *
 * Tres renglones armados por la MISMA mezcla que usan las pantallas
 * ([BitacoraDelCliente.de]), no contactos escritos a mano: un abono (el punto
 * verde de siempre, como referencia), una condonación aplicada (etiqueta
 * "Condonación", punto informativo, sin método) y una rechazada por el servidor
 * ("Condonación no aplicada", punto y monto apagados).
 *
 * Golden propio para no mover los de las pantallas completas.
 */
class CondonacionEnLaLineaScreenshotTest : PagosScreenshotTest() {

    @Test
    fun `condonacion light`() = renglones(dark = false)

    @Test
    fun `condonacion dark`() = renglones(dark = true)

    private fun renglones(dark: Boolean) = capture(
        name = "pagos_contacto_condonacion_${if (dark) "dark" else "light"}",
        dark = dark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MspTheme.spacing.md)
        ) {
            contactos().forEach { ContactoEnLinea(contacto = it, deEstaVenta = true) }
        }
    }

    private fun contactos() = BitacoraDelCliente.de(
        visitas = emptyList(),
        pagos = listOf(
            PagoDelHistorial(
                pagoId = "abono",
                ventaId = VENTA,
                fecha = Instant.parse("2026-09-01T15:05:00Z"),
                importe = dinero("400"),
                formaCobroId = MetodoDeCobro.EFECTIVO.formaCobroId,
                metodo = MetodoDeCobro.EFECTIVO,
                nota = null,
                cobrador = PagosFixtures.COBRADOR
            )
        ),
        cuentas = mapOf(VENTA to "Recámara Venecia"),
        condonaciones = listOf(
            condonacion("aplicada", "2026-09-01T17:20:00Z", aplicada = true),
            condonacion("rechazada", "2026-09-01T17:21:00Z", aplicada = false)
        )
    )

    private fun condonacion(id: String, fecha: String, aplicada: Boolean) = CondonacionDelHistorial(
        condonacionId = id,
        ventaId = VENTA,
        fecha = Instant.parse(fecha),
        importe = dinero("2300"),
        cobrador = PagosFixtures.COBRADOR,
        aplicada = aplicada
    )

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))

    private companion object {
        const val VENTA = 12_845_224
    }
}
