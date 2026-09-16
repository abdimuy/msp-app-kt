package com.example.msp_app.core.speech.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.speech.domain.EstadoDelDictado
import com.example.msp_app.core.speech.domain.GrabacionDictada
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Lo que un golden no puede probar del campo con borde vivo**: que se toca,
 * que se escribe y que no se mueve.
 *
 * Los goldens ven color y posición; esto mide **áreas tocables** (>=50dp, regla
 * del repo) y **alto**, que es donde vive el principio 12 — "nada salta".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
class CampoDictadoTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * **El campo mide lo MISMO en reposo y dictando.** Es el principio 12
     * medido: si el borde vivo apareciera solo al dictar, este alto cambiaría y
     * el contenido de abajo saltaría.
     *
     * Se mide sobre **la misma composición**, cambiando el estado: dos
     * composiciones distintas podrían diferir por otra razón y el test estaría
     * midiendo otra cosa.
     */
    @Test
    fun `el campo no cambia de alto al empezar a dictar`() {
        val estado = mutableStateOf<EstadoDelDictado>(EstadoDelDictado.Reposo)
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                Campo(estado = estado.value)
            }
        }

        val enReposo = compose.onNodeWithTag(DICTADO_CAMPO_TAG).getUnclippedBoundsInRoot().height
        estado.value = EstadoDelDictado.Escuchando(nivel = 0.7f, transcurridoMs = 3_000L)
        compose.waitForIdle()
        val escuchando = compose.onNodeWithTag(DICTADO_CAMPO_TAG).getUnclippedBoundsInRoot().height

        assertEquals("el campo salta al encender el microfono", enReposo, escuchando)
    }

    /** El botón de micrófono cumple los 50dp del repo. */
    @Test
    fun `el microfono es tocable`() {
        compose.setContent { Campo(estado = EstadoDelDictado.Reposo) }

        val caja = compose.onNodeWithTag(DICTADO_MICROFONO_TAG).getUnclippedBoundsInRoot()
        assertTrue(
            "el microfono mide ${caja.width} x ${caja.height}, y el minimo es 50dp",
            caja.width >= MINIMO && caja.height >= MINIMO
        )
    }

    /** El aspa que quita el audio también. */
    @Test
    fun `el aspa del audio es tocable`() {
        compose.setContent {
            Campo(
                estado = EstadoDelDictado.Reposo,
                grabacion = GrabacionDictada("g-1", "/audio.wav", 8_000L)
            )
        }

        val caja = compose.onNodeWithTag(DICTADO_QUITAR_AUDIO_TAG).getUnclippedBoundsInRoot()
        assertTrue(
            "el aspa mide ${caja.width} x ${caja.height}, y el minimo es 50dp",
            caja.width >= MINIMO && caja.height >= MINIMO
        )
    }

    /**
     * **El texto es texto normal.** Se toca y se corrige: lo dictado no es un
     * resultado cerrado.
     */
    @Test
    fun `el texto se puede corregir a mano`() {
        var texto = "dice que el sabado"
        compose.setContent {
            Campo(estado = EstadoDelDictado.Reposo, texto = texto, onTexto = { texto = it })
        }

        compose.onNodeWithTag(DICTADO_TEXTO_TAG).performTextReplacement("dice que el domingo")

        assertEquals("dice que el domingo", texto)
    }

    /**
     * **Sin permiso no hay micrófono, y la nota se escribe igual.** Las dos
     * mitades: el afordante desaparece (un botón que no puede hacer nada es una
     * mentira) y el campo sigue recibiendo texto.
     */
    @Test
    fun `sin permiso no hay microfono pero si se escribe la nota`() {
        var texto = ""
        compose.setContent {
            Campo(
                estado = EstadoDelDictado.Reposo,
                puedeDictar = false,
                aviso = "Sin permiso del micrófono. Escribe la nota",
                texto = texto,
                onTexto = { texto = it }
            )
        }

        compose.onNodeWithTag(DICTADO_MICROFONO_TAG).assertDoesNotExist()
        compose.onNodeWithTag(DICTADO_AVISO_TAG).assertIsDisplayed()
        compose.onNodeWithTag(DICTADO_TEXTO_TAG).performTextReplacement("no estaba en la casa")

        assertEquals("se perdio la nota sin permiso", "no estaba en la casa", texto)
    }

    /** El micrófono avisa al ViewModel. */
    @Test
    fun `tocar el microfono avisa`() {
        var toques = 0
        compose.setContent {
            Campo(estado = EstadoDelDictado.Reposo, onMicrofono = { toques++ })
        }

        compose.onNodeWithTag(DICTADO_MICROFONO_TAG).performClick()

        assertEquals(1, toques)
    }

    /**
     * **Con movimiento reducido el campo se pinta igual de alto.** No se puede
     * medir el ángulo del gradiente desde acá —eso lo ven los goldens—, pero sí
     * que la rama sin animación no cambia la geometría ni revienta, que es el
     * defecto clásico de un `if` alrededor de un `rememberInfiniteTransition`.
     */
    @Test
    fun `con movimiento reducido el campo sigue midiendo lo mismo`() {
        val reducido = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides reducido.value) {
                Campo(estado = EstadoDelDictado.Escuchando(nivel = 0.6f))
            }
        }

        val quieto = compose.onNodeWithTag(DICTADO_CAMPO_TAG).getUnclippedBoundsInRoot().height
        reducido.value = false
        compose.waitForIdle()
        val animado = compose.onNodeWithTag(DICTADO_CAMPO_TAG).getUnclippedBoundsInRoot().height

        assertEquals(quieto, animado)
    }

    /** El cronómetro es una función pura: los bordes exactos. */
    @Test
    fun `el cronometro escribe minutos y segundos`() {
        assertEquals("0:00", cronometro(0L))
        assertEquals("0:06", cronometro(6_400L))
        assertEquals("0:59", cronometro(59_999L))
        assertEquals("1:00", cronometro(60_000L))
        assertEquals("2:05", cronometro(125_000L))
        // Un valor negativo no puede producir "-1:-1": se sujeta en cero.
        assertEquals("0:00", cronometro(-5_000L))
    }

    /**
     * Las barritas responden al volumen y **nunca desaparecen**: un piso mayor
     * que cero es lo que evita que el campo parezca apagado mientras escucha en
     * silencio.
     */
    @Test
    fun `las barritas tienen piso y crecen con el volumen`() {
        val callado = altoDeBarrita(indice = 4, nivel = 0f)
        val fuerte = altoDeBarrita(indice = 4, nivel = 1f)
        val orilla = altoDeBarrita(indice = 0, nivel = 1f)

        assertTrue("una barrita sin alto desaparece", callado > 0.dp)
        assertTrue("el volumen no mueve las barritas", fuerte > callado)
        assertTrue("el perfil esta invertido", fuerte > orilla)
    }

    @Composable
    @Suppress("LongParameterList") // es el campo entero, con sus ejes.
    private fun Campo(
        estado: EstadoDelDictado,
        texto: String = "",
        puedeDictar: Boolean = true,
        grabacion: GrabacionDictada? = null,
        aviso: String? = null,
        onTexto: (String) -> Unit = {},
        onMicrofono: () -> Unit = {}
    ) {
        MspTheme {
            Column(modifier = Modifier.fillMaxWidth()) {
                CampoDictado(
                    etiqueta = "Nota — opcional",
                    marcador = "Lo que dijo, en sus palabras",
                    texto = texto,
                    estado = estado,
                    puedeDictar = puedeDictar,
                    grabacion = grabacion,
                    aviso = aviso,
                    habilitado = true,
                    onTexto = onTexto,
                    onMicrofono = onMicrofono,
                    onQuitarAudio = {}
                )
            }
        }
    }

    private companion object {
        /** El mínimo del repo. Más estricto que los 48 de Material. */
        val MINIMO = 50.dp
    }
}
