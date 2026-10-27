package com.example.msp_app.feature.visitas.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.visitas.domain.model.PendientesDelTelefono
import com.example.msp_app.feature.visitas.ui.components.AVISO_DE_PENDIENTES_TAG
import com.example.msp_app.feature.visitas.ui.components.AvisoDePendientes
import com.example.msp_app.feature.visitas.ui.components.irAPendientes
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test

/**
 * E-APP-096: tocar el aviso lleva a la pantalla de pendientes, que en Inicio es
 * el pie de la lista ("VISITAS SIN ENVIAR"). Se arma la misma forma que
 * `HomeScreen` —el aviso arriba, secciones altas en medio, el pie al final— con
 * estado falso y el mismo [irAPendientes] que cablea `Home.kt`, sin montar
 * Inicio ni la navegación de :app.
 *
 * Antes vivía en :app y, en el mismo proceso que las pruebas de navegación,
 * las dejaba en AppNotIdle (E-APP-111); aquí corre aislada.
 */
class AvisoDePendientesLlevaAPendientesTest : RobolectricTestBase() {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `E-APP-096 tocar el aviso baja hasta los pendientes`() {
        compose.setContent {
            val lista = rememberLazyListState()
            val alcance = rememberCoroutineScope()
            LazyColumn(state = lista) {
                item {
                    AvisoDePendientes(
                        pendientes = PendientesDelTelefono(visitas = 1),
                        onClick = { alcance.launch { lista.irAPendientes() } }
                    )
                }
                items(SECCIONES_ALTAS) { Spacer(Modifier.height(600.dp)) }
                item { Text(PIE) }
            }
        }

        // Control positivo: antes del toque el pie no está a la vista.
        compose.onNodeWithText(PIE).assertIsNotDisplayed()
        compose.onNodeWithTag(AVISO_DE_PENDIENTES_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(PIE).assertIsDisplayed()
    }

    private companion object {
        const val SECCIONES_ALTAS = 6
        const val PIE = "VISITAS SIN ENVIAR"
    }
}
