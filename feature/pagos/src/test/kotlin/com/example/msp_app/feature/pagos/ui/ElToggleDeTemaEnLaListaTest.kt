package com.example.msp_app.feature.pagos.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.designsystem.component.DESCRIPCION_A_CLARO
import com.example.msp_app.core.designsystem.component.DESCRIPCION_A_OSCURO
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalAppDarkTheme
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
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
import com.example.msp_app.feature.pagos.ui.components.CHIP_DE_SEGMENTO_TAG
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **El botón de modo oscuro que el dueño no encontraba, en la única pantalla de
 * cobranza de nivel superior.**
 *
 * > *"No veo los botones para cambiar a modo oscuro como están en muchas
 * > pantallas de kollect."*
 *
 * Lo que se mide acá es la mitad de la afirmación que vive dentro del módulo:
 * que el control **está**, que su tap **llega al puerto del tema de la app** (no
 * a un espejo local) y que lo que el puerto contesta **vuelve a la pantalla**.
 * La otra mitad —que ese puerto mueve y persiste `ThemeController`, el tema real
 * de la app— se mide en `:app`
 * (`ElToggleDeLaListaCambiaElTemaDeLaAppTest`), donde `ThemeController` existe.
 *
 * ## Por qué el árbol de este test trae `LocalAppDarkTheme`
 *
 * Es el árbol de producción, no andamiaje: `MainActivity` provee
 * `LocalAppDarkTheme provides ThemeController.isDarkMode` y el `MspTheme { }`
 * con el que esta pantalla se envuelve lo lee por default (ver
 * `crash-mspTheme-report.md` §9.3). Acá ese local lo alimenta el MISMO fake que
 * el ViewModel consume, que es exactamente el reparto del teléfono: un solo
 * booleano, dos lectores.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ElToggleDeTemaEnLaListaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)
    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()
    private val temaPort = FakeTemaDeLaAppPort()

    /**
     * **Reduce-motion forzado, y no es cosmético.** Desde Ruling BP la pantalla instala
     * `MspThemeRevealHost`, y su rama animada graba un `GraphicsLayer` por frame y llama
     * `toImageBitmap()` — respaldado por `RenderNode`/`Picture`, que Robolectric no soporta de
     * forma confiable fuera de `GraphicsMode.NATIVE` (le costó ~40 min de cuelgue al Plan 3, ver
     * KDoc de `ThemeRevealRootTest`). Además, en esa rama el tap **no** llama a `onToggle`: pide
     * una reveal. Sin este `@Before` estos tests medirían otra cosa o colgarían. Es la misma
     * disciplina que `PagosScreenshotTest` y `ThemeRevealRootTest`.
     *
     * Que la pantalla instale el host cuando el movimiento **no** está reducido lo mide
     * `LaListaInstalaLaRevealDeTemaTest`, que es la red de la reveal.
     */
    @Before
    fun sembrarLaRutaYApagarAnimaciones() {
        ventasPort.ventas = ListaFixtures.datosDeLaRuta()
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
    }

    private fun viewModel() = ListaDeClientesViewModel(
        savedStateHandle = SavedStateHandle(),
        reunirCartera = ReunirCartera(
            ventasPort = ventasPort,
            pagosPort = pagosPort,
            visitasPort = visitasPort,
            resolverVentanaDeCobro = ResolverVentanaDeCobro(FakePeriodoDeCobroPort(), clock),
            derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria),
            telemetry = telemetria
        ),
        tema = temaPort,
        privacidad = FakePrivacidadPort(),
        telemetry = telemetria,
        // `Dispatchers.Unconfined` y no el del `MainDispatcherRule`: la carga de
        // la cartera tiene que haber terminado ANTES de que el test toque algo,
        // y acá no hay `advanceUntilIdle` que llamar dentro de una composición.
        io = Dispatchers.Unconfined
    )

    /** La pantalla real —`*Screen`, no `*Content`— dentro del árbol de `MainActivity`. */
    private fun lista(nivel: FontSizeLevel = FontSizeLevel.NORMAL) {
        val vm = viewModel()
        composeTestRule.setContent {
            val density = LocalDensity.current
            val oscuro by temaPort.oscuro.collectAsState(initial = temaPort.oscuroAhora())
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel,
                LocalAppDarkTheme provides oscuro
            ) {
                Pantalla(vm)
            }
        }
    }

    @Composable
    private fun Pantalla(vm: ListaDeClientesViewModel) {
        ListaDeClientesScreen(
            viewModel = vm,
            onAbrirCliente = {}
        )
    }

    @Test
    fun `el encabezado de la lista trae el boton de modo oscuro`() {
        lista()

        composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_OSCURO).assertIsDisplayed()
    }

    /**
     * **El tap mueve el tema de la app, no un espejo de la pantalla.** Tres
     * afirmaciones sobre el mismo gesto: el puerto recibió exactamente un
     * `alternar()`, el estado de la pantalla quedó en oscuro, y el glifo cambió
     * de acción anunciada — que es la señal de que lo que el puerto contestó
     * volvió hasta el encabezado.
     *
     * Su **control de reversión** está medido en el reporte: cableando
     * `onToggle` a una lambda vacía (o `alternarTema()` a un `copy` local del
     * estado) este `@Test` se pone rojo por el conteo del puerto.
     */
    @Test
    fun `el tap alterna el tema de la app por el puerto y la pantalla lo refleja`() {
        lista()
        assertEquals("nadie tocó nada todavía", 0, temaPort.alternaciones)
        assertEquals("la app arranca en claro", false, temaPort.oscuroAhora())

        composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_OSCURO).performClick()
        composeTestRule.waitForIdle()

        assertEquals("un tap, un alternar — ni cero ni dos", 1, temaPort.alternaciones)
        assertEquals("el tema de la app quedó en oscuro", true, temaPort.oscuroAhora())
        composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_CLARO).assertIsDisplayed()
    }

    /** Y vuelve: las dos direcciones, no solo la de encender. */
    @Test
    fun `el segundo tap regresa a claro`() {
        lista()

        composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_OSCURO).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_CLARO).performClick()
        composeTestRule.waitForIdle()

        assertEquals(2, temaPort.alternaciones)
        assertEquals(false, temaPort.oscuroAhora())
        composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_OSCURO).assertIsDisplayed()
    }

    /**
     * **El defecto que `temaOscuro` traía puesto por vivir en el `UiState`.**
     *
     * `ListaDeClientesViewModel.proyectar()` corre en cada tecla del buscador y
     * en cada toque de chip, y construía el estado **desde cero**
     * (`ListaDeClientesUiState(...)`), así que devolvía al default todo campo que
     * no nombrara. Con el tema ahí dentro eso significaba: el cobrador pone
     * oscuro, teclea una letra y el encabezado vuelve a claro sin que nadie
     * haya tocado el tema. Se arregló con `copy`; esta prueba es la que impide
     * que vuelva.
     */
    @Test
    fun `el tema sobrevive a teclear en el buscador y a cambiar de chip`() {
        lista()
        composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_OSCURO).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(BUSCADOR_TAG).performTextInput("Flores")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_CLARO).assertIsDisplayed()

        // Y el chip, que este `@Test` prometía en el nombre y no tocaba. Los dos disparan
        // `proyectar()`, que es el escritor que pisaba el tema.
        composeTestRule
            .onNodeWithTag(CHIP_DE_SEGMENTO_TAG + SegmentoDeCobranza.PAGADOS.name.lowercase())
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            "ni teclear ni cambiar de chip pueden apagar el tema: nadie pidió alternar de nuevo",
            1,
            temaPort.alternaciones
        )
        composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_CLARO).assertIsDisplayed()
    }

    /**
     * **El encabezado no se estira con la escala de fuente.**
     *
     * La primera versión de este test afirmaba que el toggle nunca era más alto
     * que el botón de atrás, y se apoyaba en que ambos viajaban en la fila de
     * `BarraDeDetalle`. Esa fila se retiró —la lista es pantalla de nivel
     * superior y se llega desde el cajón—, así que los dos toggles bajaron al
     * renglón del título.
     *
     * Y al medirlo cayó la suposición con la que se reescribió: a `MUY_GRANDE` el
     * título mide 17.5dp y el toggle 40, o sea que **son los toggles los que
     * fijan el alto del renglón**, no el título. Lo que sí se sostiene —y es lo
     * que de verdad protege a la cabecera— es que ese alto es CONSTANTE: el
     * toggle es un icon-surface de 40dp sin texto, así que no crece con la
     * escala. El renglón mide lo mismo a 1.0 que a 2.0.
     *
     * Antes la misma franja costaba 56dp por el botón de atrás. Ahora cuesta 40.
     */
    @Test
    fun `el encabezado no se estira con la escala de fuente`() {
        // `setContent` admite una sola llamada por prueba, así que se mide la
        // escala donde un control con texto crecería primero.
        lista(nivel = FontSizeLevel.MUY_GRANDE)

        val toggle = composeTestRule
            .onNodeWithContentDescription(DESCRIPCION_A_OSCURO)
            .getUnclippedBoundsInRoot()

        assertEquals(
            "el toggle creció con la escala de fuente: la cabecera ya no tiene alto fijo",
            ALTO_DEL_TOGGLE,
            toggle.height
        )
        assertTrue(
            "el renglón de los toggles (${toggle.height}) no puede costar más que la " +
                "fila de atrás que reemplazó ($ALTO_DE_LA_FILA_DE_ATRAS)",
            toggle.height <= ALTO_DE_LA_FILA_DE_ATRAS
        )
    }

    private companion object {
        /** El icon-surface del design system, sin texto: no escala con la fuente. */
        val ALTO_DEL_TOGGLE = 40.dp

        /** Lo que medía la fila de `BarraDeDetalle` por su botón de atrás. */
        val ALTO_DE_LA_FILA_DE_ATRAS = 56.dp
    }
}
