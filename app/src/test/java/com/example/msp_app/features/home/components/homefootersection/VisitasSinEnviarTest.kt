package com.example.msp_app.features.home.components.homefootersection

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.diagnosticoenvio.MotivoDeFila
import com.example.msp_app.core.visitas.envio.ROTULO_EN_REVISION
import com.example.msp_app.core.visitas.envio.ROTULO_PENDIENTE
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Plan visitas P1, Tarea 14; spec §4.4: la pantalla de pendientes muestra
 * cada visita con su rótulo, la resguardada como "en revisión de oficina", y
 * la fila que se reintenta para siempre con su motivo e intentos. Se afirma
 * texto y callbacks; además se guarda un PNG en `build/outputs/roborazzi`
 * para mirarlo (no se compara contra un golden: `:app` no aplica Roborazzi).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class VisitasSinEnviarTest {

    @get:Rule
    val compose = createComposeRule()

    private val envios = listOf(
        EnvioDeVisita("v1", "15/10/2026 09:00", ROTULO_PENDIENTE),
        EnvioDeVisita("v2", "15/10/2026 10:00", ROTULO_EN_REVISION),
        EnvioDeVisita("v3", "16/10/2026 11:00", ROTULO_PENDIENTE)
    )
    private val motivos = mapOf(
        "v1" to MotivoDeFila("Último motivo: Datos rechazados (visita_body_json_invalido)", 37)
    )

    /** Solo para mirar: `:app` no aplica el plugin de Roborazzi, así que se graba a mano. */
    private fun guardarPng(nombre: String) {
        val antes = System.getProperty("roborazzi.test.record")
        System.setProperty("roborazzi.test.record", "true")
        try {
            compose.onRoot().captureRoboImage(File("build/outputs/roborazzi/$nombre.png").path)
        } finally {
            if (antes == null) {
                System.clearProperty(
                    "roborazzi.test.record"
                )
            } else {
                System.setProperty("roborazzi.test.record", antes)
            }
        }
    }

    @Test
    fun `H14 cada fila con su rotulo y la atorada con motivo e intentos`() {
        compose.setContent {
            MaterialTheme { VisitasSinEnviar(envios = envios, motivos = motivos, onEnviar = {}) }
        }
        compose.onNodeWithText(
            "2 pendientes de envío · 1 en revisión de oficina"
        ).assertIsDisplayed()
        compose.onNodeWithText("15/10/2026 10:00 · en revisión de oficina").assertIsDisplayed()
        compose.onNodeWithText("15/10/2026 09:00 · pendiente de envío").assertIsDisplayed()
        compose.onNodeWithText(
            "Último motivo: Datos rechazados (visita_body_json_invalido) · 37 intentos"
        ).assertIsDisplayed()
        guardarPng("visitas_sin_enviar_con_filas")
    }

    @Test
    fun `H14 la accion sin caja manda las pendientes`() {
        var enviadas = 0
        compose.setContent {
            MaterialTheme {
                VisitasSinEnviar(
                    envios = envios,
                    motivos = emptyMap(),
                    onEnviar = { enviadas++ }
                )
            }
        }
        compose.onNodeWithText("enviar ahora").performClick()
        assertEquals(1, enviadas)
    }

    @Test
    fun `H14 solo resguardadas no ofrece enviar`() {
        compose.setContent {
            MaterialTheme {
                VisitasSinEnviar(envios = listOf(envios[1]), motivos = emptyMap(), onEnviar = {})
            }
        }
        compose.onNodeWithText("1 en revisión de oficina").assertIsDisplayed()
        compose.onNodeWithText("enviar ahora").assertDoesNotExist()
    }

    @Test
    fun `H14 sin filas dice que no hay`() {
        compose.setContent {
            MaterialTheme {
                VisitasSinEnviar(
                    envios = emptyList(),
                    motivos = emptyMap(),
                    onEnviar = {}
                )
            }
        }
        compose.onNodeWithText("No hay visitas pendientes").assertIsDisplayed()
        compose.onNodeWithText("enviar ahora").assertDoesNotExist()
        guardarPng("visitas_sin_enviar_vacia")
    }

    /** H15 (abierta): el aviso de "no retires este teléfono" cabe en la ranura sin tocar la pantalla. */
    @Test
    fun `H14 la ranura de aviso se pinta arriba cuando alguien la llena`() {
        compose.setContent {
            MaterialTheme {
                VisitasSinEnviar(envios = envios, motivos = emptyMap(), onEnviar = {
                }, aviso = { Text("aviso de prueba") })
            }
        }
        compose.onNodeWithText("aviso de prueba").assertIsDisplayed()
    }

    @Test
    fun `H14 con muchas filas se muestran las primeras y cuantas faltan`() {
        val muchas = (1..25).map {
            EnvioDeVisita("v$it", "16/10/2026 %02d:00".format(it % 24), ROTULO_PENDIENTE)
        }
        compose.setContent {
            MaterialTheme { VisitasSinEnviar(envios = muchas, motivos = emptyMap(), onEnviar = {}) }
        }
        compose.onNodeWithText("y 5 más").assertExists()
    }

    @Test
    fun `H14 resumen de envio`() {
        assertEquals("2 pendientes de envío · 1 en revisión de oficina", resumenDeEnvio(envios))
        assertEquals("1 pendiente de envío", resumenDeEnvio(listOf(envios[0])))
        assertEquals("", resumenDeEnvio(emptyList()))
    }

    // ─── estado de la lectura (revisión de T14) ───────────────────────────

    /** Una lectura que falla no convierte "hay 3" en "no hay": se conserva la lista y se avisa. */
    @Test
    fun `H14 una lectura fallida conserva la lista anterior y marca el fallo`() {
        val antes = EstadoDeEnvios().tras(Result.success(envios))
        val despues = antes.tras(Result.failure(IllegalStateException("db")))
        assertEquals(EstadoDeEnvios(envios = envios, fallo = true), despues)
        assertEquals(
            EstadoDeEnvios(envios = envios, fallo = false),
            despues.tras(Result.success(envios))
        )
    }

    @Test
    fun `H14 una lectura fallida sin lista previa no inventa una vacia`() {
        assertEquals(
            EstadoDeEnvios(envios = null, fallo = true),
            EstadoDeEnvios().tras(Result.failure(IllegalStateException("db")))
        )
    }

    @Test
    fun `H14 mientras carga no dice que no hay`() {
        compose.setContent {
            MaterialTheme { VisitasSinEnviar(envios = null, motivos = emptyMap(), onEnviar = {}) }
        }
        compose.onNodeWithText("Cargando visitas").assertIsDisplayed()
        compose.onNodeWithText("No hay visitas pendientes").assertDoesNotExist()
        guardarPng("visitas_sin_enviar_cargando")
    }

    @Test
    fun `H14 con fallo de lectura avisa y sigue mostrando lo que sabia`() {
        val estado = EstadoDeEnvios().tras(
            Result.success(envios)
        ).tras(Result.failure(IllegalStateException("db")))
        compose.setContent {
            MaterialTheme {
                VisitasSinEnviar(
                    envios = estado.envios,
                    motivos = emptyMap(),
                    onEnviar = {},
                    fallo = estado.fallo
                )
            }
        }
        compose.onNodeWithText("No se pudieron leer las visitas").assertIsDisplayed()
        compose.onNodeWithText("15/10/2026 09:00 · pendiente de envío").assertIsDisplayed()
        compose.onNodeWithText("No hay visitas pendientes").assertDoesNotExist()
        guardarPng("visitas_sin_enviar_fallo_de_lectura")
    }

    @Test
    fun `H14 fallo de lectura sin lista previa solo avisa`() {
        compose.setContent {
            MaterialTheme {
                VisitasSinEnviar(
                    envios = null,
                    motivos = emptyMap(),
                    onEnviar = {},
                    fallo = true
                )
            }
        }
        compose.onNodeWithText("No se pudieron leer las visitas").assertIsDisplayed()
        compose.onNodeWithText("Cargando visitas").assertDoesNotExist()
        compose.onNodeWithText("No hay visitas pendientes").assertDoesNotExist()
        guardarPng("visitas_sin_enviar_fallo_sin_lista")
    }
}
