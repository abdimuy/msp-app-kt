package com.example.msp_app.core.appgate.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.msp_app.core.appgate.AppBuildInfo
import com.example.msp_app.core.appgate.AppVersionGate
import com.example.msp_app.core.appgate.DeviceIdProvider
import com.example.msp_app.core.appgate.download.ApkInstaller
import com.example.msp_app.core.appgate.download.ApkVersionReader
import com.example.msp_app.core.appgate.download.NetworkStatus
import com.example.msp_app.core.appgate.download.NetworkStatusProvider
import com.example.msp_app.core.appgate.download.UpdateDownloadScheduler
import com.example.msp_app.core.appgate.download.UpdateDownloadStateHolder
import com.example.msp_app.core.appgate.download.UpdateFileLocator
import com.example.msp_app.core.appgate.fake.FakeMinVersionConfigSource
import com.example.msp_app.core.appgate.fake.FakeVersionGateCache
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import java.io.File
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * **El punto de entrada real, montado SIN tema.**
 *
 * `VersionBlockedScreenTest` —el único compose-test que este módulo tenía—
 * monta [VersionBlockedContent] y le **REGALA** un `MspTheme { }`. O sea prueba
 * la premisa que producción viola, que es exactamente el harness que dejó pasar
 * el crash de `MspTheme ausente` en las siete pantallas de cobranza. Aquí no se
 * aporta tema: se monta el `*Screen`, que es lo que `AppNavigation` compone
 * (`AppEntryStep.VERSION_BLOCKED`), en el árbol pelado que la app construye a su
 * alrededor.
 *
 * ## Por qué hace falta, y por qué acá
 *
 * Las otras redes no llegan:
 *
 * - **La sonda del `NavHost`** (`CadaDestinoDeCobranzaSeMontaTest`, `:app`) solo
 *   compone lo que `destinosDeCobranza` registre, y esta pantalla no es un
 *   destino de cobranza: se llega a ella desde `AppEntry`, antes del `NavHost`.
 * - **El escáner de fuente** (`CadaPantallaMspProveeSuTemaTest`, `:app`) decide por
 *   **presencia** de la llamada a `MspTheme { }`, no por cobertura del subárbol.
 *   Medido: sembrando en [VersionBlockedScreen] una lectura de `MspTheme.colors`
 *   **fuera** de su propio envoltorio —la forma exacta del crash original— el
 *   escáner y los **134** tests de este módulo pasaban **todos en verde**.
 *
 * Esta prueba es la que se pone roja ahí: sin tema alrededor, una lectura fuera
 * del envoltorio revienta con `IllegalStateException("MspTheme ausente")`, la
 * excepción exacta del crash de producción.
 *
 * ## El ViewModel es el REAL
 *
 * [VersionGateViewModel] es una clase concreta y el `hiltViewModel()` del
 * default no aplica cuando se lo pasa explícito, así que se arma a mano con
 * fakes de sus puertos (política del repo: fakes, nunca mocks). No es
 * ceremonia: el crash ocurre en el modificador más externo, **antes** de que
 * ningún dato importe — con la caché vacía el estado es el inicial, que es el
 * estado en el que la app crasheó en el emulador.
 */
class ElTemaLoPoneLaPantallaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: VersionGateViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // WorkManager de verdad bajo Robolectric: `UpdateDownloadScheduler` lo
        // exige para construirse. Con la caché vacía no hay `UpdatePackage`, así
        // que el `init` del ViewModel no encola nada.
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .build()
        )
        viewModel = VersionGateViewModel(
            gate = AppVersionGate(
                cache = FakeVersionGateCache(),
                remote = FakeMinVersionConfigSource(),
                buildInfo = AppBuildInfo(
                    versionCode = VERSION_CODE,
                    versionName = VERSION_NAME,
                    debugBuild = false
                ),
                deviceIdProvider = DeviceIdProvider { null }
            ),
            downloadState = UpdateDownloadStateHolder(),
            networkStatus = NetworkStatusProvider { flowOf(NetworkStatus.OFFLINE) },
            locator = UpdateFileLocator(context, ApkVersionReader { null }),
            scheduler = UpdateDownloadScheduler(WorkManager.getInstance(context)),
            installer = ApkInstaller(context)
        )
    }

    @Test
    fun `la pantalla de bloqueo por version se monta sin que nadie le de el tema`() {
        montarSinTema { VersionBlockedScreen(viewModel = viewModel) }
    }

    /**
     * **Control positivo.** Sin esto, un verde de arriba también sería
     * compatible con "este harness no detecta una lectura fuera del envoltorio":
     * se prueba que el método SÍ ve el defecto cuando el defecto está.
     */
    @Test
    fun `el harness ve una lectura de MspTheme fuera del envoltorio`() {
        val error = runCatching {
            montarSinTema { LeeElTemaSinEnvolverse() }
        }.exceptionOrNull()

        assertTrue(
            "montar sin tema una pantalla que lee MspTheme fuera del envoltorio tenía " +
                "que reventar, y no reventó: esta prueba no estaría midiendo nada",
            raiz(error).let { it is IllegalStateException && MSP_AUSENTE in it.message.orEmpty() }
        )
    }

    /**
     * **La compuerta.** Las pantallas cubiertas no son una lista escrita a mano:
     * tienen que ser EXACTAMENTE las que declara `src/main`. Una pantalla nueva
     * en este módulo pone rojo este test el día que se escribe.
     *
     * Control positivo: el escaneo tiene que encontrar algo, o no probaría nada.
     */
    @Test
    fun cadaPantallaDelModuloTieneSuPrueba() {
        val declaradas = pantallasDeclaradasEn(File(raizDelModulo(), "src/main/kotlin"))
        assertTrue(
            "el escaneo no encontró ninguna pantalla: el método no probaría nada",
            declaradas.isNotEmpty()
        )
        assertEquals(declaradas, PANTALLAS_PROBADAS)
    }

    /**
     * Monta [pantalla] **sin `MspTheme` y sin ningún `CompositionLocalProvider`
     * de andamiaje**: el entorno de la app, no el del harness.
     */
    private fun montarSinTema(pantalla: @Composable () -> Unit) {
        composeTestRule.setContent { pantalla() }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().assertExists()
    }

    /** El defecto que esta prueba existe para atrapar, en su forma mínima. */
    @Composable
    private fun LeeElTemaSinEnvolverse() {
        @Suppress("UNUSED_VARIABLE")
        val fondo = MspTheme.colors.background
    }

    private companion object {

        const val VERSION_CODE = 56
        const val VERSION_NAME = "2.16.0"

        const val MSP_AUSENTE = "MspTheme ausente"

        /** Lo que esta clase monta. Se compara contra lo que `src/main` declara. */
        val PANTALLAS_PROBADAS = sortedSetOf("VersionBlockedScreen")

        /** `fun XxxScreen(` de nivel superior, que es la forma que el grafo monta. */
        val PANTALLA = Regex("""^(?:internal )?fun ([A-Z][A-Za-z0-9]*Screen)\(""")

        /**
         * El working dir de los tests es el del módulo cuando Gradle lo corre
         * solo, y la raíz del repo en algunos IDE. Se resuelven las dos.
         */
        fun raizDelModulo(): File =
            if (File("src/main/kotlin").isDirectory) File(".") else File("core/appgate")

        fun pantallasDeclaradasEn(raiz: File) = raiz.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { archivo ->
                archivo.readLines().mapNotNull { PANTALLA.find(it)?.groupValues?.get(1) }
            }
            .toSortedSet()

        fun raiz(error: Throwable?): Throwable? {
            var actual: Throwable? = error
            while (actual?.cause != null && actual.cause !== actual) actual = actual.cause
            return actual
        }
    }
}
