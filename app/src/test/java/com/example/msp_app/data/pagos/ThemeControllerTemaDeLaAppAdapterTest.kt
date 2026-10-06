package com.example.msp_app.data.pagos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.navegacion.domain.port.TemaDelMenuPort
import com.example.msp_app.feature.pagos.domain.port.TemaDeLaAppPort
import com.example.msp_app.ui.theme.ThemeController
import com.example.msp_app.ui.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ruling R2 (controlador, 2026-10-06): esta clase implementa `TemaDeLaAppPort`
 * (`:feature:pagos`) Y `TemaDelMenuPort` (`:core:navegacion`) a la vez — no hay
 * una cuarta copia del adaptador. Lo que importa aquí NO es que sea la misma
 * instancia (ver [SettingsPrivacidadAdapterTest] para el caso donde sí
 * importa), sino que las dos vistas del puerto queden sincronizadas porque
 * las dos delegan en el MISMO `ThemeController`: una lista con su adaptador y
 * un menú con el suyo tienen que ver el mismo tema sin coordinarse entre sí.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThemeControllerTemaDeLaAppAdapterTest {

    @Before
    fun setUp() {
        reengancharThemeControllerAlContextoDeEstaPrueba()
        ThemeController.init(contexto())
        ThemeController.applyThemeMode(ThemeMode.LIGHT)
    }

    @After
    fun tearDown() {
        ThemeController.applyThemeMode(ThemeMode.LIGHT)
    }

    private fun contexto(): Context = ApplicationProvider.getApplicationContext()

    /** Mismo gotcha documentado en `ElToggleDeLaListaCambiaElTemaDeLaAppTest`: el `object` sobrevive entre `@Test`. */
    private fun reengancharThemeControllerAlContextoDeEstaPrueba() {
        val compuerta = ThemeController::class.java.getDeclaredField("prefsInitialized")
        compuerta.isAccessible = true
        compuerta.setBoolean(ThemeController, false)
    }

    @Test
    fun `implementa los dos puertos a la vez`() {
        val adaptador = ThemeControllerTemaDeLaAppAdapter()
        assertEquals(true, adaptador is TemaDeLaAppPort)
        assertEquals(true, adaptador is TemaDelMenuPort)
    }

    @Test
    fun `una instancia vista como lista y otra vista como menu comparten el mismo tema`() {
        val comoLista: TemaDeLaAppPort = ThemeControllerTemaDeLaAppAdapter()
        val comoMenu: TemaDelMenuPort = ThemeControllerTemaDeLaAppAdapter()

        assertEquals(false, comoLista.oscuroAhora())
        assertEquals(false, comoMenu.oscuroAhora())

        // Alternar desde la vista del MENÚ...
        comoMenu.alternar()

        // ...se ve desde la vista de la LISTA, con una instancia DISTINTA: las dos
        // delegan en el mismo ThemeController, no hay dos verdades.
        assertEquals(true, comoLista.oscuroAhora())
        assertEquals(ThemeMode.DARK, ThemeController.themeMode)
    }
}
