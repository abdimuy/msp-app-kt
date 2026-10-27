package com.example.msp_app.features.home.components.avisos

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.avisos.PasoDePermiso
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** E-APP-134 · El diálogo explica, abre el ajuste pedido y se cierra sin bloquear. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class AvisosPermisosDialogTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `explica por que, abre el ajuste elegido y Ahora no cierra - E-APP-134`() {
        val abiertos = mutableListOf<PasoDePermiso>()
        var cerrado = 0
        compose.setContent {
            MaterialTheme {
                AvisosPermisosDialog(
                    pasos = PasoDePermiso.entries,
                    onAbrir = { abiertos += it },
                    onAhoraNo = { cerrado++ }
                )
            }
        }
        compose.onNodeWithText("Avisos de tus visitas").assertIsDisplayed()
        compose.onNodeWithText("Alarmas exactas").assertIsDisplayed()
        compose.onNodeWithText("Batería sin restricción").assertIsDisplayed()
        val antes = System.getProperty("roborazzi.test.record")
        System.setProperty("roborazzi.test.record", "true")
        try {
            compose.onAllNodes(
                isRoot()
            )[1].captureRoboImage(File("build/outputs/roborazzi/avisos_permisos.png").path)
        } finally {
            if (antes == null) {
                System.clearProperty(
                    "roborazzi.test.record"
                )
            } else {
                System.setProperty("roborazzi.test.record", antes)
            }
        }
        compose.onAllNodesWithText("Permitir")[1].performClick()
        compose.onNodeWithText("Ahora no").performClick()
        assertEquals(listOf(PasoDePermiso.BATERIA), abiertos)
        assertEquals(1, cerrado)
    }
}
