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
 * Los tres estados que solo dependen del tema van en NORMAL; la captura —la
 * pantalla donde el cobrador pasa el tiempo y donde el texto grande aprieta de
 * verdad— recorre la matriz completa.
 *
 * ## El abono corto también recorre la matriz completa (ronda 4 de arreglo)
 *
 * `BandaDeAbonoCorto` entró en la ronda 3 y quedó sin un solo pixel de
 * cobertura: **ninguno** de los cuatro estados de arriba baja de `esperadoHoy`,
 * así que la banda solo estaba verificada por aserciones de Robolectric. Es una
 * pantalla de dinero y el contrato de tests del plan pide la matriz.
 *
 * Va con las tres escalas y no solo en NORMAL —a diferencia de sus hermanos de
 * estado— porque es la banda con **dos líneas de texto y dos cifras dentro**, o
 * sea la que más aprieta cuando la fuente crece. Probarla solo en 1.0 sería
 * probarla donde no puede fallar.
 *
 * ## Los dos estados nuevos de los avisos escalonados
 *
 * `aviso_vivo` es la banda que sale **mientras se teclea** —el lugar donde un
 * cero de más todavía cuesta un borrón—, `teclear` es el paso dos de nivel 3,
 * con el campo que pide el monto otra vez, y `cuota_dudosa` es la pantalla del
 * caso `Y00002184`: la parcialidad de la venta se ve mal, así que no se ofrece
 * ningún esperado y en su lugar se dice que hay que revisar el dato. Van con golden por la misma razón
 * que el abono corto: son piezas de la pantalla del dinero y sin un pixel de
 * cobertura sólo estarían verificadas por aserciones de Robolectric, que dicen
 * que el nodo existe pero no que se vea.
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

    @Test
    fun `abono corto light normal`() = abonoCorto(dark = false, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `abono corto light grande`() = abonoCorto(dark = false, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `abono corto light muy grande`() =
        abonoCorto(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `abono corto dark normal`() = abonoCorto(dark = true, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `abono corto dark grande`() = abonoCorto(dark = true, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `abono corto dark muy grande`() = abonoCorto(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `aviso en vivo light`() = avisoEnVivo(dark = false)

    @Test
    fun `aviso en vivo dark`() = avisoEnVivo(dark = true)

    @Test
    fun `cuota dudosa light`() = cuotaDudosa(dark = false)

    @Test
    fun `cuota dudosa dark`() = cuotaDudosa(dark = true)

    @Test
    fun `teclear el monto light`() = teclearElMonto(dark = false)

    @Test
    fun `teclear el monto dark`() = teclearElMonto(dark = true)

    @Test
    fun `confirmar con comprobante light`() =
        estado("confirmar_comprobante", AbonoFixtures.enConfirmacionConComprobante(), dark = false)

    @Test
    fun `confirmar con comprobante dark`() =
        estado("confirmar_comprobante", AbonoFixtures.enConfirmacionConComprobante(), dark = true)

    private fun avisoEnVivo(dark: Boolean) =
        estado("aviso_vivo", AbonoFixtures.enAvisoDeTeclear(), dark)

    private fun cuotaDudosa(dark: Boolean) =
        estado("cuota_dudosa", AbonoFixtures.conCuotaDudosa(), dark)

    private fun teclearElMonto(dark: Boolean) =
        estado("teclear", AbonoFixtures.tecleandoElMonto(), dark)

    private fun captura(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_abono_captura_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Abono(AbonoFixtures.enCaptura())
    }

    private fun abonoCorto(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_abono_corto_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Abono(AbonoFixtures.enAbonoCorto())
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
        onEditar = {},
        onRevisar = {},
        onAgregarFoto = {},
        onOrigen = {},
        onCerrarOrigenes = {},
        onQuitarFoto = {}
    )
}
