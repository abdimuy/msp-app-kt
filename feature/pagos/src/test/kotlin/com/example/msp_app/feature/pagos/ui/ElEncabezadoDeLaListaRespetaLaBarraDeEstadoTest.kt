package com.example.msp_app.feature.pagos.ui

import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.designsystem.component.DESCRIPCION_A_OSCURO
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.ResolverVentanaDeCobro
import com.example.msp_app.feature.pagos.application.ReunirCartera
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakeTemaDeLaAppPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **El defecto que 3743 tests verdes no podían ver: el control está, su tap
 * llega al puerto, y en el teléfono no pasa nada.**
 *
 * Medido en el emulador (1280x2856, densidad 480): la ventana `StatusBar` del
 * sistema mide **156 px** de alto y la caja tocable del toggle de tema de esta
 * pantalla ocupa `y ∈ [36, 180]`. Todo tap a `y ≤ 155` —el centro del botón
 * incluido, que es donde aterriza el dedo— **lo consume la barra de estado y no
 * llega nunca a Compose**; solo la rebanada de 24 px de abajo funciona (barrido
 * de taps: 100/140/150/155 perdidos, 157/160/164/168/174/179 llegados). Eso es
 * un botón muerto, y el mismo encabezado le hace lo mismo al "atrás".
 *
 * La causa no está en el toggle: la app corre `enableEdgeToEdge()` y **las
 * pantallas de `:feature:pagos` son las únicas del repo que no consumen el
 * inset de la barra de estado** — las siete legadas, el reporte de cobranza y
 * Configuración todas aplican `statusBarsPadding()`.
 *
 * ## Por qué ningún test existente lo alcanzaba
 *
 * `performClick()` no inyecta un evento en el sistema de ventanas: despacha
 * directo sobre el nodo de semántica. En una composición de test **no hay
 * ventana `StatusBar` encima** y `WindowInsets.statusBars` vale cero, así que
 * un encabezado dibujado en `y = 0` es perfectamente tocable en el mundo del
 * test. La cadena que `ElToggleDeTemaEnLaListaTest` y
 * `ElToggleDeLaListaCambiaElTemaDeLaAppTest` afirman —tap → puerto →
 * `ThemeController` → `theme_prefs`— **es verdadera**: lo que faltaba medir no
 * era la cadena, era **la geometría del control contra las barras del sistema**.
 *
 * Ésa es la clase de prueba que no existía: ninguna de las 3743 mide dónde cae
 * un control respecto de un inset. **Sí se puede en JVM** —y por eso no vive en
 * la compuerta manual de `DEPLOY.md`—: basta **despachar** un inset de barra de
 * estado al `AndroidComposeView`, que es lo que el sistema hace en el teléfono,
 * y exigir que el control quede completo por debajo.
 *
 * ## El control positivo
 *
 * [laBarraDeEstadoLlegaAHastaCompose] afirma que el inset despachado **sí**
 * llegó a la composición, leyéndolo con una sonda `WindowInsets.statusBars`
 * dentro del mismo árbol. Sin él, "el inset nunca llegó" y "la pantalla lo
 * consume" producen el mismo número y este archivo quedaría verde por la razón
 * equivocada — que es exactamente el modo de falla que esta rama ya pagó dos
 * veces.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ElEncabezadoDeLaListaRespetaLaBarraDeEstadoTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)
    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()
    private val temaPort = FakeTemaDeLaAppPort()

    /** Lo que la sonda dentro de la composición vio de `WindowInsets.statusBars`, en px. */
    private var insetVistoPorCompose: Int = -1

    /**
     * Reduce-motion forzado por la misma razón que en `ElToggleDeTemaEnLaListaTest`: la rama
     * animada de `MspThemeRevealHost` graba un `GraphicsLayer` que Robolectric no sostiene.
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
        telemetry = telemetria,
        io = Dispatchers.Unconfined
    )

    /**
     * La pantalla real con un inset de barra de estado **despachado al árbol de vistas**, que
     * es lo que el sistema hace en el teléfono y lo que una composición de test nunca recibe.
     * Se despacha sobre el `AndroidComposeView` y no sobre el `DecorView` porque es ahí donde
     * Compose instala su listener (`WindowInsetsHolder`).
     */
    private fun listaConBarraDeEstado() {
        val vm = viewModel()
        composeTestRule.setContent {
            Box {
                ListaDeClientesScreen(
                    viewModel = vm,
                    onAtras = {},
                    onAbrirCliente = {},
                    onAbrirVenta = {}
                )
                // Sonda del control positivo: lo que ESTA composición ve del inset. No pinta.
                val visto = WindowInsets.statusBars.getTop(LocalDensity.current)
                SideEffect { insetVistoPorCompose = visto }
            }
        }
        composeTestRule.waitForIdle()
        val alto = with(composeTestRule.density) { ALTO_DE_LA_BARRA.roundToPx() }
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, alto, 0, 0))
            .build()
        composeTestRule.runOnUiThread {
            vistasDeCompose(composeTestRule.activity.window.decorView).forEach { vista ->
                ViewCompat.dispatchApplyWindowInsets(vista, insets)
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun vistasDeCompose(raiz: View): List<View> {
        val encontradas = mutableListOf<View>()
        if (raiz.javaClass.simpleName == "AndroidComposeView") encontradas += raiz
        if (raiz is ViewGroup) {
            for (i in 0 until raiz.childCount) encontradas += vistasDeCompose(raiz.getChildAt(i))
        }
        return encontradas
    }

    private fun topeDel(descripcion: String): Dp = composeTestRule
        .onNodeWithContentDescription(descripcion)
        .getUnclippedBoundsInRoot()
        .top

    /**
     * **Control positivo.** Antes de creerle a las dos mediciones de abajo: el inset
     * despachado tiene que haber llegado a la composición. Si este test se pone rojo, el
     * problema es el despacho —no la pantalla— y las mediciones de abajo no prueban nada.
     */
    @Test
    fun `la barra de estado llega a hasta compose`() {
        listaConBarraDeEstado()

        val esperado = with(composeTestRule.density) { ALTO_DE_LA_BARRA.roundToPx() }
        assertEquals(
            "el inset despachado no llegó a Compose: las mediciones de abajo no prueban nada",
            esperado,
            insetVistoPorCompose
        )
    }

    /**
     * **El defecto.** La caja tocable completa del botón de modo oscuro tiene que caer por
     * debajo de la barra de estado: mientras se meta bajo la ventana `StatusBar`, el sistema
     * se come el tap del centro del botón y la pantalla nunca se entera.
     */
    @Test
    fun `el toggle de tema cae completo debajo de la barra de estado`() {
        listaConBarraDeEstado()

        val tope = topeDel(DESCRIPCION_A_OSCURO)
        assertTrue(
            "el toggle empieza en $tope, encima de la barra de estado de $ALTO_DE_LA_BARRA: " +
                "la ventana StatusBar se come el tap y el botón queda muerto",
            tope >= ALTO_DE_LA_BARRA
        )
    }

    /**
     * El mismo encabezado, el otro control: el "atrás" de `BarraDeDetalle` comparte la fila y
     * por lo tanto el defecto. Se mide aparte porque es la mitad del encabezado que ninguna
     * pantalla de cobranza puede perder.
     */
    @Test
    fun `el boton de atras cae completo debajo de la barra de estado`() {
        listaConBarraDeEstado()

        val tope = topeDel(DESCRIPCION_DE_ATRAS)
        assertTrue(
            "el atrás empieza en $tope, encima de la barra de estado de $ALTO_DE_LA_BARRA",
            tope >= ALTO_DE_LA_BARRA
        )
    }

    private companion object {
        /**
         * Alto de barra de estado con el que se despacha el inset. El emulador donde se midió
         * el defecto tiene 156 px a densidad 480 (52 dp); acá se usa el valor clásico de
         * 24 dp porque lo que este test exige es que la pantalla consuma **el** inset, no que
         * acierte un número.
         */
        val ALTO_DE_LA_BARRA = 24.dp

        /** El `contentDescription` que `BarraDeDetalle` le pone a su botón circular. */
        const val DESCRIPCION_DE_ATRAS = "atrás"
    }
}
