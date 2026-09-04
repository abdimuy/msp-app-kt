package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.runtime.Composable
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.feature.pagos.ui.AbonoFixtures
import com.example.msp_app.feature.pagos.ui.RegistrarAbonoContent
import com.example.msp_app.feature.pagos.ui.RegistrarAbonoUiState
import org.junit.Test

/**
 * Los **cuatro estados del mock** `registrar-abono.html` —captura, bloqueo
 * duro, confirmar y monto raro— en claro y oscuro, y la captura además en las
 * TRES escalas reales de `FontSizeLevel` (1.0 / 1.5 / 2.0).
 *
 * El `1.3` de `CollectionReportMatrixScreenshotTest` no corresponde a ningún
 * nivel que un usuario pueda elegir y no se replica (ver el KDoc de
 * [PagosScreenshotTest]).
 *
 * Son doce goldens: los tres estados que solo dependen del tema van en NORMAL,
 * y la captura —la pantalla donde el cobrador pasa el tiempo y donde el texto
 * grande aprieta de verdad— recorre la matriz completa.
 */
class AbonoMatrixScreenshotTest : PagosScreenshotTest() {

    @Test
    fun `captura light normal`() = captura(dark = false, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `captura light grande`() = captura(dark = false, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `captura light muy grande`() = captura(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `captura dark normal`() = captura(dark = true, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `captura dark grande`() = captura(dark = true, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `captura dark muy grande`() = captura(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `bloqueo light`() = estado("bloqueo", AbonoFixtures.enBloqueo(), dark = false)

    @Test
    fun `bloqueo dark`() = estado("bloqueo", AbonoFixtures.enBloqueo(), dark = true)

    @Test
    fun `confirmar light`() = estado("confirmar", AbonoFixtures.enConfirmacion(), dark = false)

    @Test
    fun `confirmar dark`() = estado("confirmar", AbonoFixtures.enConfirmacion(), dark = true)

    @Test
    fun `monto raro light`() = estado("monto_raro", AbonoFixtures.enMontoRaro(), dark = false)

    @Test
    fun `monto raro dark`() = estado("monto_raro", AbonoFixtures.enMontoRaro(), dark = true)

    private fun captura(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_abono_captura_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Abono(AbonoFixtures.enCaptura())
    }

    private fun estado(nombre: String, state: RegistrarAbonoUiState, dark: Boolean) = capture(
        name = "pagos_abono_${nombre}_${tema(dark)}",
        dark = dark
    ) {
        Abono(state)
    }

    private fun tema(dark: Boolean) = if (dark) "dark" else "light"
}

@Composable
private fun Abono(state: RegistrarAbonoUiState) {
    RegistrarAbonoContent(
        state = state,
        onAtras = {},
        onDigito = {},
        onPunto = {},
        onBorrar = {},
        onMetodo = {},
        onSugerido = {},
        onRegistrar = {},
        onConfirmar = {},
        onEditar = {}
    )
}
