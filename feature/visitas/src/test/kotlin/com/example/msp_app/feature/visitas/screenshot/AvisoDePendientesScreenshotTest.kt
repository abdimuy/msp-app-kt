package com.example.msp_app.feature.visitas.screenshot

import androidx.compose.runtime.CompositionLocalProvider
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalAppDarkTheme
import com.example.msp_app.feature.visitas.domain.model.PendientesDelTelefono
import com.example.msp_app.feature.visitas.ui.components.AvisoDePendientes
import org.junit.Test

/**
 * E-APP-096: el aviso de Inicio en claro, oscuro y letra muy grande (2.0).
 *
 * [LocalAppDarkTheme] se provee porque el aviso trae su propio `MspTheme`
 * (Inicio todavía no vive dentro de él) y ese lee el tema de la app, no el de
 * la envoltura de [capture].
 */
class AvisoDePendientesScreenshotTest : VisitasScreenshotTest() {

    private val pendientes = PendientesDelTelefono(visitas = 2, fotos = 3, pagos = 1, ventas = 1)

    @Test
    fun `aviso light normal`() = foto(dark = false, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `aviso dark normal`() = foto(dark = true, nivel = FontSizeLevel.NORMAL)

    @Test
    fun `aviso light muy grande`() = foto(dark = false, nivel = FontSizeLevel.MUY_GRANDE)

    @Test
    fun `aviso dark muy grande`() = foto(dark = true, nivel = FontSizeLevel.MUY_GRANDE)

    private fun foto(dark: Boolean, nivel: FontSizeLevel) =
        capture("visitas_aviso_pendientes_${tema(dark)}_${sufijoDe(nivel)}", dark, nivel) {
            CompositionLocalProvider(LocalAppDarkTheme provides dark) {
                AvisoDePendientes(pendientes = pendientes, onClick = {})
            }
        }
}
