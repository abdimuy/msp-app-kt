package com.example.msp_app.feature.configuracion.ui

import app.cash.turbine.test
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.testing.MainDispatcherRule
import com.example.msp_app.feature.configuracion.data.fake.FakeAppThemePort
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
        themePort: FakeAppThemePort = FakeAppThemePort()
    ) = ConfiguracionViewModel(settingsRepository, themePort)

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
}
