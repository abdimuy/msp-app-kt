package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.designsystem.component.DESCRIPCION_A_OSCURO
import com.example.msp_app.core.designsystem.theme.LocalAppDarkTheme
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.ResolverVentanaDeCobro
import com.example.msp_app.feature.pagos.application.ReunirCartera
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakePrivacidadPort
import com.example.msp_app.feature.pagos.data.fake.FakeTemaDeLaAppPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **Que el mismo botón se sienta igual en toda la app — Ruling BP.**
 *
 * El reveal circular del tema estaba **solo** en el reporte de cobranza: el mecanismo vivía
 * dentro de `:feature:collectionReport` y ninguna otra pantalla podía instalar un host, así que
 * el mismo `MspThemeToggle` animaba una reveal en una pantalla y un crossfade en la otra. *No
 * tener* la animación sería aceptable; que el mismo control se comporte distinto según la
 * pantalla no lo es, y **ninguna captura lo muestra** — de ahí este test.
 *
 * ## Cómo se mide sin píxeles
 *
 * `MspThemeToggle` decide entre los dos mecanismos leyendo `LocalThemeReveal`
 * (`ThemeToggleTest`, `:core:designsystem`, prueba sus dos ramas): **con host instalado el tap
 * NO llama a `onToggle`** —pide la reveal, y el flip lo hace el host después de snapshotear el
 * frame viejo—, y **sin host cae directo a `onToggle`**. O sea que el mismo gesto, contado en el
 * puerto, distingue las dos ramas sin mirar un solo píxel.
 *
 * El interruptor que este test mueve es [LocalReduceMotion] —la preferencia propia de la app,
 * "Deshabilitar animaciones" de Configuración— y no `ANIMATOR_DURATION_SCALE`, porque es un
 * `CompositionLocal` y se controla desde el árbol del test sin escribir en `Settings.Global`.
 * Las dos señales entran por el mismo `||` en `ListaDeClientesScreen`.
 *
 * **Par discriminante:** los dos `@Test` afirman lo CONTRARIO sobre el MISMO gesto. Si el host
 * no estuviera cableado, o estuviera cableado al revés, uno de los dos enrojece.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaListaInstalaLaRevealDeTemaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)
    private val ventasPort = FakeVentasPort()
    private val temaPort = FakeTemaDeLaAppPort()

    @Before
    fun sembrarLaRuta() {
        ventasPort.ventas = ListaFixtures.datosDeLaRuta()
    }

    private fun viewModel() = ListaDeClientesViewModel(
        savedStateHandle = SavedStateHandle(),
        reunirCartera = ReunirCartera(
            ventasPort = ventasPort,
            pagosPort = FakePagosPort(),
            visitasPort = FakeVisitasPort(),
            resolverVentanaDeCobro = ResolverVentanaDeCobro(FakePeriodoDeCobroPort(), clock),
            derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria),
            telemetry = telemetria
        ),
        tema = temaPort,
        privacidad = FakePrivacidadPort(),
        telemetry = telemetria,
        io = Dispatchers.Unconfined
    )

    private fun listaConMovimiento(reducido: Boolean) {
        val vm = viewModel()
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalAppDarkTheme provides false,
                LocalReduceMotion provides reducido
            ) {
                ListaDeClientesScreen(
                    viewModel = vm,
                    onAbrirCliente = {},
                    onAbrirVenta = {}
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `con movimiento reducido no hay host, el tap va directo al puerto`() {
        listaConMovimiento(reducido = true)

        composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_OSCURO).performClick()

        assertEquals(
            "sin host instalado el toggle cae a su onToggle: el flip es inmediato",
            1,
            temaPort.alternaciones
        )
    }

    @Test
    fun `sin movimiento reducido la lista instala el host y el tap pide la reveal`() {
        listaConMovimiento(reducido = false)

        composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_OSCURO).performClick()

        assertEquals(
            "con host instalado el tap NO llama al onToggle del botón: pide la reveal, y el " +
                "flip lo hace el host tras snapshotear el frame viejo. Si esto da 1, la " +
                "pantalla no instaló el host y su toggle se siente distinto al del reporte",
            0,
            temaPort.alternaciones
        )
    }
}
