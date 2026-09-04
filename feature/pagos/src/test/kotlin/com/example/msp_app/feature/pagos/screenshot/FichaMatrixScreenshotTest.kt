package com.example.msp_app.feature.pagos.screenshot

import androidx.compose.runtime.Composable
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.feature.pagos.domain.model.FichaDelCliente
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import com.example.msp_app.feature.pagos.ui.AccionesDeLaFicha
import com.example.msp_app.feature.pagos.ui.DetalleClienteContent
import com.example.msp_app.feature.pagos.ui.DetalleClienteUiState
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import com.example.msp_app.feature.pagos.ui.components.CuerpoDeLaFicha
import org.junit.Test

/**
 * La matriz de la ficha: `hoja × {light, dark} × {1.0, 1.5, 2.0}` = 6 goldens,
 * más los **estados que esta tarea existe para no aplanar** — sin ficha, ficha
 * ilegible y guardado fallido — en claro y oscuro.
 *
 * Se captura [CuerpoDeLaFicha] y no `HojaDeLaFicha`: `captureRoboImage` toma la
 * ventana raíz y no el `Popup` donde Material monta el `ModalBottomSheet`, así
 * que capturar la hoja completa daría un golden en blanco (mismo motivo que
 * `PrintSheetBody` en `:feature:collectionReport`).
 */
class FichaMatrixScreenshotTest : PagosScreenshotTest() {

    @Test
    fun `hoja light normal`() = hoja(dark = false, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `hoja light grande`() = hoja(dark = false, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `hoja light muy grande`() = hoja(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `hoja dark normal`() = hoja(dark = true, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `hoja dark grande`() = hoja(dark = true, nivel = FontSizeLevel.GRANDE)

    @Test
    fun `hoja dark muy grande`() = hoja(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `hoja con fallo light`() = hojaConFallo(dark = false)

    @Test
    fun `hoja con fallo dark`() = hojaConFallo(dark = true)

    @Test
    fun `cliente sin ficha light`() = seccion(
        dark = false,
        ficha = FichaDelCliente(),
        sufijo = "sin"
    )

    @Test
    fun `cliente sin ficha dark`() = seccion(dark = true, ficha = FichaDelCliente(), sufijo = "sin")

    @Test
    fun `cliente con ficha ilegible light`() = seccion(
        dark = false,
        ficha = null,
        sufijo = "ilegible"
    )

    @Test
    fun `cliente con ficha ilegible dark`() = seccion(
        dark = true,
        ficha = null,
        sufijo = "ilegible"
    )

    private fun hoja(dark: Boolean, nivel: FontSizeLevel) = capture(
        name = "pagos_ficha_hoja_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Hoja(fallo = false)
    }

    private fun hojaConFallo(dark: Boolean) = capture(
        name = "pagos_ficha_hoja_fallo_${tema(dark)}",
        dark = dark
    ) {
        Hoja(fallo = true)
    }

    private fun seccion(dark: Boolean, ficha: FichaDelCliente?, sufijo: String) = capture(
        name = "pagos_ficha_${sufijo}_${tema(dark)}",
        dark = dark
    ) {
        Cliente(ficha)
    }

    private fun tema(dark: Boolean) = if (dark) "dark" else "light"
}

@Composable
private fun Hoja(fallo: Boolean) {
    CuerpoDeLaFicha(
        senales = setOf(SenalDeFicha.ESTA_EN_LA_NOCHE, SenalDeFicha.ATIENDE_OTRA_PERSONA),
        nota = "atiende la suegra, doña Remedios.\ncasa azul, portón negro.",
        guardando = false,
        fallo = fallo,
        onSenal = {},
        onNota = {},
        onGuardar = {}
    )
}

@Composable
private fun Cliente(ficha: FichaDelCliente?) {
    DetalleClienteContent(
        state = DetalleClienteUiState(
            cargando = false,
            detalle = PagosFixtures.detalleCliente().copy(ficha = ficha)
        ),
        onAtras = {},
        onAbrirVenta = {},
        onRegistrarAbono = {},
        onRegistrarVisita = {},
        onMasAcciones = {},
        onUsarLiquidacion = {},
        onVerContactos = {},
        fichaDelCliente = AccionesDeLaFicha()
    )
}
