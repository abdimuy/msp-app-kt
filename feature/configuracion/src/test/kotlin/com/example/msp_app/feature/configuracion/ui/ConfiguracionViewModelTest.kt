package com.example.msp_app.feature.configuracion.ui

import app.cash.turbine.test
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.speech.domain.AvanceDeLaDescarga
import com.example.msp_app.core.speech.domain.EstadoDelModelo
import com.example.msp_app.core.speech.domain.ModeloDeDictado
import com.example.msp_app.core.testing.MainDispatcherRule
import com.example.msp_app.feature.configuracion.data.fake.FakeAppThemePort
import com.example.msp_app.feature.configuracion.data.fake.FakeModeloDeDictadoPort
import com.example.msp_app.feature.configuracion.data.fake.FakeSettingsRepository
import com.example.msp_app.feature.configuracion.domain.port.AppThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Cobertura del `@HiltViewModel` de Configuración: el estado observable refleja 1:1 los tres
 * `Flow` de [FakeSettingsRepository] + el modo de tema de [FakeAppThemePort], y cada setter
 * delega en el puerto/repositorio correcto (spy) sin lógica propia — el ViewModel es un simple
 * puente `Flow` combinado + setters suspend/directos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfiguracionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
        themePort: FakeAppThemePort = FakeAppThemePort(),
        modeloPort: FakeModeloDeDictadoPort = FakeModeloDeDictadoPort()
    ) = ConfiguracionViewModel(
        settingsRepository,
        themePort,
        modeloPort,
        MODELO
    )

    /**
     * **Sin motor no se ofrece la descarga.** Es el caso REAL de hoy: whisper.cpp
     * no se vendoró, así que `MotorWhisperNativo.cargada` es `false` en toda la
     * flota y el renglón de 43.5 MB no se pinta.
     *
     * Ofrecerlo sería ofrecer una mentira: el cobrador gastaría sus datos para un
     * motor que no puede cargar nada. El dictado sigue funcionando sin eso, con el
     * reconocedor que Android ya trae.
     *
     * **Control de reversión:** quitar el `if (modeloPort.motorDisponible)` de
     * `ConfiguracionViewModel.descargas` pone este test en ROJO.
     */
    @Test
    fun `sin motor de whisper no se ofrece bajar el modelo`() = runTest {
        val viewModel = viewModel(modeloPort = FakeModeloDeDictadoPort(motorDisponible = false))

        viewModel.state.test {
            assertEquals(
                "no se puede ofrecer una descarga para un motor que no existe",
                emptyList<Any>(),
                awaitItem().descargas
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * **Control positivo del de arriba.** Con el mismo montaje y el motor
     * presente, el renglón SÍ aparece. Sin esto, un `descargas` roto que siempre
     * saliera vacío dejaría pasar el test anterior sin probar nada.
     */
    @Test
    fun `con motor de whisper si se ofrece bajar el modelo`() = runTest {
        val viewModel = viewModel(modeloPort = FakeModeloDeDictadoPort(motorDisponible = true))

        viewModel.state.test {
            assertEquals(1, awaitItem().descargas.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `el estado inicial refleja los defaults de los fakes`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(FontSizeLevel.NORMAL, state.fontSizeLevel)
            assertEquals(false, state.privacyMasked)
            assertEquals(false, state.reduceMotion)
            assertEquals(AppThemeMode.LIGHT, state.themeMode)
        }
    }

    @Test
    fun `el estado inicial refleja valores no-default de los fakes`() = runTest {
        val settingsRepository = FakeSettingsRepository(
            initialFontSizeLevel = FontSizeLevel.MUY_GRANDE,
            initialPrivacyMasked = true,
            initialReduceMotion = true
        )
        val themePort = FakeAppThemePort(initialThemeMode = AppThemeMode.DARK)
        val viewModel = viewModel(settingsRepository, themePort)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(FontSizeLevel.MUY_GRANDE, state.fontSizeLevel)
            assertEquals(true, state.privacyMasked)
            assertEquals(true, state.reduceMotion)
            assertEquals(AppThemeMode.DARK, state.themeMode)
        }
    }

    @Test
    fun `selectFontSizeLevel escribe en el repositorio y el estado se actualiza`() = runTest {
        val settingsRepository = FakeSettingsRepository()
        val viewModel = viewModel(settingsRepository)

        viewModel.state.test {
            assertEquals(FontSizeLevel.NORMAL, awaitItem().fontSizeLevel)

            viewModel.selectFontSizeLevel(FontSizeLevel.GRANDE)

            assertEquals(FontSizeLevel.GRANDE, awaitItem().fontSizeLevel)
        }
        assertEquals(listOf(FontSizeLevel.GRANDE), settingsRepository.setFontSizeLevelCalls)
    }

    @Test
    fun `selectThemeMode delega en el puerto de tema`() = runTest {
        val themePort = FakeAppThemePort()
        val viewModel = viewModel(themePort = themePort)

        viewModel.state.test {
            assertEquals(AppThemeMode.LIGHT, awaitItem().themeMode)

            viewModel.selectThemeMode(AppThemeMode.SYSTEM)

            assertEquals(AppThemeMode.SYSTEM, awaitItem().themeMode)
        }
        assertEquals(listOf(AppThemeMode.SYSTEM), themePort.setThemeModeCalls)
    }

    @Test
    fun `setPrivacyMasked alterna la preferencia global`() = runTest {
        val settingsRepository = FakeSettingsRepository()
        val viewModel = viewModel(settingsRepository)

        viewModel.state.test {
            assertEquals(false, awaitItem().privacyMasked)

            viewModel.setPrivacyMasked(true)

            assertEquals(true, awaitItem().privacyMasked)
        }
        assertEquals(listOf(true), settingsRepository.setPrivacyMaskedCalls)
    }

    @Test
    fun `setReduceMotion alterna la preferencia global`() = runTest {
        val settingsRepository = FakeSettingsRepository()
        val viewModel = viewModel(settingsRepository)

        viewModel.state.test {
            assertEquals(false, awaitItem().reduceMotion)

            viewModel.setReduceMotion(true)

            assertEquals(true, awaitItem().reduceMotion)
        }
        assertEquals(listOf(true), settingsRepository.setReduceMotionCalls)
    }

    // -----------------------------------------------------------------------
    // La sección "Descargas"
    // -----------------------------------------------------------------------

    /**
     * **El primer frame ya trae el renglón.** Una lista vacía haría que la
     * sección entera apareciera de golpe un frame después, y un encabezado
     * "Descargas" sobre nada se lee como algo que no cargó.
     */
    @Test
    fun `el estado inicial ya trae el renglon de descarga`() = runTest {
        val viewModel = viewModel()

        val descargas = viewModel.state.value.descargas
        assertEquals(listOf(DescargaOpcional.DICTADO), descargas.map { it.cual })
    }

    /** El peso del dictado sale del paquete del módulo, no de un número escrito. */
    @Test
    fun `el renglon del dictado anuncia los megas del paquete`() = runTest {
        val viewModel = viewModel()

        viewModel.state.test {
            val dictado = awaitItem().descargas.single { it.cual == DescargaOpcional.DICTADO }
            assertEquals("43.5", dictado.megas)
            assertEquals(EstadoDeLaDescarga.AUSENTE, dictado.estado)
        }
    }

    /** Lo que mueve el worker llega al renglón sin que nadie vuelva a preguntar. */
    @Test
    fun `el renglon sigue al puerto cuando la descarga avanza`() = runTest {
        val modeloPort = FakeModeloDeDictadoPort()
        val viewModel = viewModel(modeloPort = modeloPort)

        viewModel.state.test {
            assertEquals(
                EstadoDeLaDescarga.AUSENTE,
                awaitItem().descargas.single { it.cual == DescargaOpcional.DICTADO }.estado
            )

            modeloPort.emite(
                EstadoDelModelo.Descargando(AvanceDeLaDescarga(20_000_000L, 43_537_433L))
            )

            assertEquals(
                EstadoDeLaDescarga.DESCARGANDO,
                awaitItem().descargas.single { it.cual == DescargaOpcional.DICTADO }.estado
            )
        }
    }

    private companion object {

        /** El paquete real del módulo de voz: 43 537 433 B, o sea 43.5 MB. */
        val MODELO = ModeloDeDictado(
            url = "https://ejemplo.invalido/ggml-tiny-q8_0.bin",
            tamanoBytes = 43_537_433L,
            sha256 = "c2085835d3f50733e2ff6e4b41ae8a2b8d8110461e18821b09a15c40c42d1cca"
        )
    }
}
