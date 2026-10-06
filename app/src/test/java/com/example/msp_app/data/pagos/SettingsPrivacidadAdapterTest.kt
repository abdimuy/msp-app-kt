package com.example.msp_app.data.pagos

import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.navegacion.domain.port.PrivacidadDelMenuPort
import com.example.msp_app.core.settings.SettingsRepository
import com.example.msp_app.feature.pagos.domain.port.PrivacidadPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ruling R2 (controlador, 2026-10-06): esta clase implementa `PrivacidadPort`
 * (`:feature:pagos`) Y `PrivacidadDelMenuPort` (`:core:navegacion`) a la vez,
 * con `@Singleton` en la clase — a diferencia del tema, aquí SÍ importa que
 * Dagger entregue la MISMA instancia a los dos puertos, porque el estado real
 * vive en el `cache` del adaptador (ver su KDoc), no en `SettingsRepository`.
 */
class SettingsPrivacidadAdapterTest {

    private class FakeSettingsRepositoryParaPrivacidad : SettingsRepository {
        override val fontSizeLevel: Flow<FontSizeLevel> = MutableStateFlow(FontSizeLevel.NORMAL)
        override suspend fun setFontSizeLevel(level: FontSizeLevel) = Unit

        private val oculto = MutableStateFlow(false)
        override val privacyMasked: Flow<Boolean> = oculto
        override suspend fun setPrivacyMasked(masked: Boolean) {
            oculto.value = masked
        }

        override val reduceMotion: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setReduceMotion(enabled: Boolean) = Unit
    }

    @Test
    fun `implementa los dos puertos a la vez`() {
        val adaptador = SettingsPrivacidadAdapter(FakeSettingsRepositoryParaPrivacidad())
        assertTrue(adaptador is PrivacidadPort)
        assertTrue(adaptador is PrivacidadDelMenuPort)
    }

    @Test
    fun `alternar desde la vista del menu se ve desde la vista de la lista, mismo singleton`() =
        runTest {
            val adaptador = SettingsPrivacidadAdapter(FakeSettingsRepositoryParaPrivacidad())
            val comoMenu: PrivacidadDelMenuPort = adaptador
            val comoLista: PrivacidadPort = adaptador

            assertEquals(false, comoLista.ocultosAhora())

            comoMenu.alternar()

            // Mismo objeto, mismo `cache`: la vista de la lista ve el cambio que hizo el menú
            // sin que nadie las haya coordinado a mano.
            assertEquals(true, comoLista.ocultos.first { it })
            assertEquals(true, comoLista.ocultosAhora())
        }
}
