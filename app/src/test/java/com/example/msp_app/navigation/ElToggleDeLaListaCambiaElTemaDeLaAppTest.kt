package com.example.msp_app.navigation

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.designsystem.component.DESCRIPCION_A_CLARO
import com.example.msp_app.core.designsystem.component.DESCRIPCION_A_OSCURO
import com.example.msp_app.core.designsystem.theme.LocalAppDarkTheme
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.mspDarkColors
import com.example.msp_app.core.designsystem.theme.mspLightColors
import com.example.msp_app.feature.pagos.domain.port.TemaDeLaAppPort
import com.example.msp_app.feature.pagos.ui.ListaDeClientesScreen
import com.example.msp_app.feature.pagos.ui.ListaDeClientesViewModel
import com.example.msp_app.ui.theme.ThemeController
import com.example.msp_app.ui.theme.ThemeMode
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **Que el toggle funcione de verdad, no que se pinte.**
 *
 * El compose-test del módulo (`ElToggleDeTemaEnLaListaTest`, `:feature:pagos`)
 * llega hasta el puerto y ahí se detiene: dentro del feature no existe
 * `ThemeController`, así que ahí "el tema de la app" es un fake. **Esta prueba
 * es la que cierra el circuito**: toca el botón real, en la pantalla real, con
 * el ViewModel armado por el **grafo Hilt real** —o sea con el
 * `ThemeControllerTemaDeLaAppAdapter` que `PagosPortsModule` cablea, no un
 * fake— y afirma tres cosas sobre `ThemeController`, que ES el tema de la app:
 *
 * 1. **Cambió**: `isDarkMode`/`themeMode` se movieron.
 * 2. **Persistió**: la preferencia quedó escrita en `theme_prefs`, que es lo que
 *    sobrevive a navegar, a que la pantalla se destruya y a que muera el
 *    proceso.
 * 3. **Se pinta**: un `MspTheme { }` montado bajo la MISMA expresión que provee
 *    `MainActivity` (`LocalAppDarkTheme provides ThemeController.isDarkMode`)
 *    resuelve la paleta oscura después del tap. Es la sonda de lo que le pasa a
 *    **cualquier** pantalla Msp a la que el cobrador navegue después, que es
 *    justo la pregunta "si lo pongo en oscuro y navego, ¿sigue en oscuro?".
 *
 * ## Lo que esta prueba NO cubre, dicho
 *
 * No atraviesa la lambda `composable{}` de la ruta —el `hiltViewModel()` de
 * `PagosRutas.kt`—: eso lo cubre [CadaDestinoDeCobranzaSeMontaTest], que compone
 * los siete destinos dentro del `NavHost` real. Acá el ViewModel se pide al
 * mismo `defaultViewModelProviderFactory` de una Activity `@AndroidEntryPoint`
 * contra el que ese `hiltViewModel()` resuelve (precedente
 * `WarehouseHiltGraphTest`), porque lo que hace falta es **tocar** el botón, y
 * para eso hace falta una `ComposeTestRule`.
 *
 * ## El estado global de `ThemeController`, y por qué se normaliza en `@Before`
 *
 * `ThemeController` es un `object`: su estado vive en la clase, no en la
 * instancia de la app, así que puede sobrevivir de un `@Test` al siguiente
 * dentro del mismo classloader de Robolectric. Cada prueba lo deja en Claro
 * antes de empezar y el `@After` lo restituye, así que ninguna depende del orden
 * de ejecución. La lectura de `theme_prefs` trae **su propio control positivo**
 * (ver [temaPersistido]).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33], qualifiers = "w360dp-h2400dp-xhdpi")
class ElToggleDeLaListaCambiaElTemaDeLaAppTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    /**
     * El puerto tal como el grafo real lo entrega. Se inyecta para afirmar que
     * el binding **existe** —sin él, `ListaDeClientesViewModel` no se puede
     * construir y todo lo demás sería humo— y de qué clase es.
     */
    @Inject
    lateinit var tema: TemaDeLaAppPort

    private var fondoDeUnaPantallaMsp: Color? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        reengancharThemeControllerAlContextoDeEstaPrueba()
        ThemeController.init(contexto())
        ThemeController.applyThemeMode(ThemeMode.LIGHT)
    }

    /**
     * **Esto lo encontró el control positivo de [temaPersistido], en la primera
     * corrida.** `ThemeController` es un `object` con una compuerta
     * `prefsInitialized` que convierte a `init` en un no-op a partir de la
     * segunda llamada. Bajo Robolectric el `object` sobrevive de un `@Test` al
     * siguiente, pero la `Application` **no**: sin este reset, `prefs` sigue
     * apuntando al `SharedPreferences` de la app del test anterior y la lectura
     * de disco de este test devolvía `null` —medido: `expected:<LIGHT> but
     * was:<null>`— mientras `ThemeController.isDarkMode` decía la verdad. Sin el
     * control positivo esa mitad de la prueba habría quedado midiendo el archivo
     * equivocado.
     *
     * **No es un defecto de producción**: ahí `init` corre una vez por proceso,
     * que es justo para lo que la compuerta existe. Es un artefacto de correr
     * varios tests contra el mismo classloader, y por eso se arregla acá y no en
     * `ThemeController`.
     */
    private fun reengancharThemeControllerAlContextoDeEstaPrueba() {
        val compuerta = ThemeController::class.java.getDeclaredField("prefsInitialized")
        compuerta.isAccessible = true
        compuerta.setBoolean(ThemeController, false)
    }

    @After
    fun tearDown() {
        ThemeController.applyThemeMode(ThemeMode.LIGHT)
    }

    private fun contexto(): Context = ApplicationProvider.getApplicationContext()

    /**
     * Lo que quedó escrito en disco, leído desde el contexto de la app y **no**
     * desde `ThemeController`.
     *
     * Control positivo incorporado: `@Before` acaba de hacer
     * `applyThemeMode(LIGHT)`, así que esta lectura tiene que decir `"LIGHT"`
     * antes de cualquier tap. Si dijera `null` sería la señal de que se está
     * mirando el archivo equivocado, y la afirmación de persistencia de abajo no
     * probaría nada.
     */
    private fun temaPersistido(): String? = contexto()
        .getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        .getString("theme_mode", null)

    /**
     * La lista de clientes con su ViewModel **del grafo real**, dentro del árbol
     * que monta `MainActivity`: el `CompositionLocalProvider` con
     * `LocalAppDarkTheme` y nada más — sin `MspTheme` alrededor, porque la
     * pantalla se envuelve sola (Ruling BJ).
     */
    private fun listaDeClientes() {
        val host = Robolectric.buildActivity(DestinosDeCobranzaTestActivity::class.java)
            .setup()
            .get()
        val viewModel = ViewModelProvider(host)[ListaDeClientesViewModel::class.java]
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppDarkTheme provides ThemeController.isDarkMode) {
                Column {
                    ListaDeClientesScreen(
                        viewModel = viewModel,
                        onAtras = {},
                        onAbrirCliente = {},
                        onAbrirVenta = {}
                    )
                    // La sonda: el MISMO default de `MspTheme` que usa toda
                    // pantalla Msp a la que se navegue después. No emite nada.
                    MspTheme(animateColors = false) {
                        fondoDeUnaPantallaMsp = MspTheme.colors.background
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `el binding del puerto de tema es el adaptador real sobre ThemeController`() {
        assertEquals(
            "ThemeControllerTemaDeLaAppAdapter",
            tema::class.java.simpleName
        )
        // Y de verdad lee el objeto global, no un espejo propio.
        assertEquals(ThemeController.isDarkMode, tema.oscuroAhora())
    }

    @Test
    fun `el tap en el toggle cambia el tema de la app, lo persiste y lo pinta`() {
        listaDeClientes()

        assertEquals("la app arranca en claro", false, ThemeController.isDarkMode)
        assertEquals("control positivo del archivo", "LIGHT", temaPersistido())
        assertEquals(mspLightColors().background, fondoDeUnaPantallaMsp)

        composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_OSCURO).performClick()
        composeTestRule.waitForIdle()

        assertEquals("el tema de la APP cambió", true, ThemeController.isDarkMode)
        assertEquals(ThemeMode.DARK, ThemeController.themeMode)
        assertEquals("y quedó escrito: sobrevive a navegar y al proceso", "DARK", temaPersistido())
        assertEquals(
            "cualquier pantalla Msp que se monte ahora pinta oscuro",
            mspDarkColors().background,
            fondoDeUnaPantallaMsp
        )
        assertNotEquals(mspLightColors().background, mspDarkColors().background)
    }

    /**
     * **"Si lo pongo en oscuro y navego, ¿sigue en oscuro?" — la parte que un
     * `MspTheme` recién montado no contesta.**
     *
     * Volver a entrar a la lista construye un `ListaDeClientesViewModel` NUEVO,
     * y ése es exactamente donde el reporte de cobranza tenía su defecto: su
     * espejo local arrancaba en claro cada vez. Acá se toca el botón, se pide al
     * grafo real un ViewModel nuevo —como haría el `hiltViewModel()` de la ruta
     * al volver— y se afirma que **su estado inicial ya viene oscuro**, sin
     * esperar un solo frame ni la primera emisión del `Flow` (el seed de
     * `TemaDeLaAppPort.oscuroAhora()` es síncrono a propósito: sin él habría un
     * flash blanco al entrar con la app en oscuro).
     */
    @Test
    fun `al volver a entrar, la lista arranca ya en oscuro`() {
        listaDeClientes()

        composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_OSCURO).performClick()
        composeTestRule.waitForIdle()

        val otroHost = Robolectric.buildActivity(DestinosDeCobranzaTestActivity::class.java)
            .setup()
            .get()
        val alVolver = ViewModelProvider(otroHost)[ListaDeClientesViewModel::class.java]

        assertEquals(
            "un ViewModel nuevo tiene que nacer con el tema de la app, no en claro",
            true,
            alVolver.state.value.temaOscuro
        )
    }

    /**
     * **La vuelta.** Sin esta mitad, un cableado que solo supiera encender
     * pasaría el test de arriba.
     */
    @Test
    fun `el segundo tap devuelve la app a claro y tambien lo persiste`() {
        listaDeClientes()

        composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_OSCURO).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_CLARO).performClick()
        composeTestRule.waitForIdle()

        assertEquals(false, ThemeController.isDarkMode)
        assertEquals(ThemeMode.LIGHT, ThemeController.themeMode)
        assertEquals("LIGHT", temaPersistido())
        assertEquals(mspLightColors().background, fondoDeUnaPantallaMsp)
    }
}
