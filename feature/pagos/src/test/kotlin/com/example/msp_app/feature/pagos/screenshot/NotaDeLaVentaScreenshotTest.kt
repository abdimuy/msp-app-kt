package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.ui.components.TarjetaDeNotaDestacada
import org.junit.Test

/**
 * La tarjeta de nota del detalle de CLIENTE con una nota que no cabe.
 *
 * Los goldens `pagos_venta_nota_*` se fueron con la nota de la venta: el dueño
 * la quitó de esa pantalla (era la nota del cliente, no de la cuenta).
 */
class NotaDeLaVentaScreenshotTest : PagosScreenshotTest() {

    /**
     * La tarjeta del detalle de CLIENTE con una nota que no cabe: botón de
     * editar en el renglón del rótulo, dos renglones asomados y *"Ver más"*.
     *
     * Existe porque es el estado que ningún golden de pantalla completa retrata
     * —el fixture del detalle trae una nota de dos renglones que cabe— y es
     * justamente el que paga el adelgazamiento de la tarjeta. Un assert cuenta
     * que el indicador está; esto enseña que la tarjeta sigue leyéndose.
     */
    @Test
    fun `nota recortada light`() = recortada(dark = false)

    @Test
    fun `nota recortada dark`() = recortada(dark = true)

    private fun recortada(dark: Boolean) = capture(
        name = "pagos_nota_recortada_${if (dark) "dark" else "light"}",
        dark = dark
    ) {
        NotaQueNoCabe()
    }
}

@Composable
private fun NotaQueNoCabe() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MspTheme.spacing.md)
    ) {
        TarjetaDeNotaDestacada(
            rotulo = "lo que anotaste",
            nota = "El cliente pidió que pasen el viernes porque cobra ese día en la " +
                "fábrica y no llega antes de las siete. El portón negro está abierto " +
                "pero hay que tocar fuerte porque la señora no oye bien desde el patio.",
            antiguedad = "hace 1 semana",
            onEditar = {}
        )
    }
}
