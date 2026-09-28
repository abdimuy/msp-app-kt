package com.example.msp_app.features.forgiveness

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.sale.toDomain
import com.example.msp_app.data.pagos.CondonacionFixtures
import com.example.msp_app.data.pagos.CondonacionFixtures.condonacionesEn
import com.example.msp_app.data.pagos.CondonacionFixtures.venta
import com.example.msp_app.data.pagos.RegistroDeCondonacion
import com.example.msp_app.features.forgiveness.components.CONFIRMAR_CONDONACION_TAG
import com.example.msp_app.features.forgiveness.components.CondonacionEnPantalla
import com.example.msp_app.features.forgiveness.components.GUARDAR_CONDONACION_TAG
import com.example.msp_app.features.forgiveness.components.MONTO_CONDONACION_TAG
import com.example.msp_app.features.forgiveness.viewmodels.CondonacionViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.GraphicsMode

/**
 * **Tras guardar, el formulario de condonación queda terminado mientras navega.**
 *
 * Hallazgo del dueño en el aparato (devlocal, 2026-09-27): *"durante un instante
 * se queda en la pantalla del formulario ... ¿no hay posibilidad de que le den
 * doble click y se meta más de una vez?"*. Sí la había: tras guardar, el
 * diálogo limpiaba el campo y le decía al ViewModel que volviera a aceptar
 * ANTES de navegar, y durante la transición el formulario seguía vivo. En una
 * condonación parcial el resto quedaba condonable con un toque.
 *
 * Se congela esa ventana con un `onGuardada` que NO navega.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ElFormularioGuardadoQuedaTerminadoTest : RoomTestBase() {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tras guardar una parcial, el formulario no deja condonar otra vez`() {
        runBlocking { db.saleDao().insertAll(listOf(venta(saldo = 2300.0))) }
        val vm = CondonacionViewModel(RegistroDeCondonacion(db))
        val guardadas = mutableListOf<String>()
        val sale = venta(saldo = 2300.0).toDomain()

        compose.setContent {
            CondonacionEnPantalla(
                sale = sale,
                userData = ResultState.Success(
                    User(ID = "u-1", NOMBRE = "Rosa Elena", COBRADOR_ID = 7)
                ),
                condonacionViewModel = vm,
                isDark = false,
                onDismissRequest = {},
                onGuardada = { guardadas += it },
                efectosTrasGuardar = {}
            )
        }

        compose.waitUntil(ESPERA_MS) { vm.estado.value.saldo != null }
        compose.onNodeWithTag(MONTO_CONDONACION_TAG).performTextReplacement("1000")
        compose.onNodeWithTag(GUARDAR_CONDONACION_TAG).performClick()
        compose.onNodeWithTag(CONFIRMAR_CONDONACION_TAG).performClick()
        compose.waitUntil(ESPERA_MS) { guardadas.isNotEmpty() }
        compose.waitForIdle()

        // La ventana congelada: no se navegó, el formulario sigue ahí.
        compose.onNodeWithTag(MONTO_CONDONACION_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(GUARDAR_CONDONACION_TAG).assertIsNotEnabled()
        runBlocking {
            assertEquals(listOf(guardadas.single()), condonacionesEn(db).map { it.ID })
            assertEquals(1300.0, CondonacionFixtures.saldoDe(db), 1e-9)
        }
    }

    private companion object {
        const val ESPERA_MS = 5_000L
    }
}
