package com.example.msp_app.core.speech.adapters

import com.example.msp_app.core.speech.application.SpeechTelemetria
import com.example.msp_app.core.speech.domain.FalloDelDictado
import com.example.msp_app.core.speech.domain.MotorDeDictado
import com.example.msp_app.core.speech.domain.port.DictadoFallido
import com.example.msp_app.core.speech.fake.GrabadoraFalsa
import com.example.msp_app.core.speech.fake.PermisoFalso
import com.example.msp_app.core.speech.fake.ReconocedorFalso
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que el reconocedor de Android hace **alrededor** del servicio del sistema:
 * el permiso, el audio y el mapeo de errores.
 *
 * El servicio en sí ([SpeechRecognizerDeAndroid]) no se toca acá, y eso está
 * dicho en su KDoc: Robolectric no lo simula y la compuerta no corre
 * `androidTest`. Lo que sí se prueba es todo lo que puede salir mal sin él.
 *
 * `StandardTestDispatcher` + `advanceUntilIdle`, nunca `UnconfinedTestDispatcher`
 * (global-constraints §Dispatchers).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidDictadoAdapterTest {

    private val reconocedor = ReconocedorFalso()
    private val grabadora = GrabadoraFalsa()
    private val permiso = PermisoFalso()
    private val telemetry = RecordingTelemetry()

    private fun adaptador() = AndroidDictadoAdapter(
        reconocedor = reconocedor,
        grabadora = grabadora,
        permiso = permiso,
        telemetry = telemetry,
        ahoraMs = { 0L }
    )

    /**
     * **Sin permiso no se abre el micrófono y no se pierde la nota.**
     *
     * Las dos mitades importan: el `failure` con `SIN_PERMISO` es lo que la
     * pantalla usa para pintar el aviso ámbar, y que la grabadora **no** se haya
     * llamado es lo que prueba que no se intentó grabar sin permiso.
     */
    @Test
    fun `sin permiso no graba y contesta sin permiso`() = runTest(StandardTestDispatcher()) {
        permiso.concedido = false

        val resultado = adaptador().comenzar()
        advanceUntilIdle()

        assertEquals(FalloDelDictado.SIN_PERMISO, motivoDe(resultado))
        assertTrue("se intento grabar sin permiso", grabadora.llamadas.isEmpty())
    }

    @Test
    fun `sin reconocedor contesta sin motor`() = runTest(StandardTestDispatcher()) {
        reconocedor.disponible = false

        val resultado = adaptador().comenzar()
        advanceUntilIdle()

        assertEquals(FalloDelDictado.SIN_MOTOR, motivoDe(resultado))
    }

    /** El micrófono ocupado se reporta con su `code`, sin el `message` crudo. */
    @Test
    fun `microfono ocupado emite telemetria con su code`() = runTest(StandardTestDispatcher()) {
        grabadora.abre = false

        val resultado = adaptador().comenzar()
        advanceUntilIdle()

        assertEquals(FalloDelDictado.MICROFONO_OCUPADO, motivoDe(resultado))
        val evento = telemetry.recorded.single()
        assertEquals(SpeechTelemetria.CODE_MICROFONO_NO_ABRIO, evento.name)
        assertEquals("IllegalStateException", evento.props[SpeechTelemetria.PROP_EXCEPCION])
    }

    /**
     * **La grabación se conserva aunque el reconocedor ni siquiera arranque.**
     * Es la regla dura del dictado en su caso más incómodo.
     */
    @Test
    fun `si el reconocedor no arranca la grabacion se cierra igual`() =
        runTest(StandardTestDispatcher()) {
            reconocedor.arranca = false

            val resultado = adaptador().comenzar()
            advanceUntilIdle()

            assertEquals(FalloDelDictado.MOTOR_FALLO, motivoDe(resultado))
            assertEquals(listOf("comenzar", "cancelar"), grabadora.llamadas)
            assertEquals(
                SpeechTelemetria.CODE_ANDROID_NO_ARRANCO,
                telemetry.recorded.single().name
            )
        }

    @Test
    fun `entrega el texto final con su grabacion`() = runTest(StandardTestDispatcher()) {
        val adaptador = adaptador()
        adaptador.comenzar()
        advanceUntilIdle()

        val esperando = async { adaptador.terminar() }
        advanceUntilIdle()
        reconocedor.emite(EventoDelReconocedor.Final("dice que el sabado paga"))
        advanceUntilIdle()

        val terminado = esperando.await().getOrThrow()
        assertEquals("dice que el sabado paga", terminado.texto)
        assertEquals(MotorDeDictado.ANDROID, terminado.motor)
        assertNotNull("se perdio la grabacion", terminado.grabacion)
    }

    /**
     * **`ERROR_NO_MATCH` no es un fallo.** Se entrega un dictado vacío con su
     * grabación, y **no** se emite telemetría: reportar el silencio como error
     * llenaría la cola de eventos que no son problemas de nadie.
     */
    @Test
    fun `el silencio entrega audio sin texto y sin telemetria`() =
        runTest(StandardTestDispatcher()) {
            val adaptador = adaptador()
            adaptador.comenzar()
            advanceUntilIdle()

            val esperando = async { adaptador.terminar() }
            advanceUntilIdle()
            reconocedor.emite(EventoDelReconocedor.Fallo(ERROR_NO_MATCH))
            advanceUntilIdle()

            val terminado = esperando.await().getOrThrow()
            assertEquals("", terminado.texto)
            assertNotNull("se perdio la grabacion en el silencio", terminado.grabacion)
            assertTrue("el silencio no es un error", telemetry.recorded.isEmpty())
        }

    /** Un error de verdad sí se reporta, con el código crudo del reconocedor. */
    @Test
    fun `un error del reconocedor se reporta con su code`() = runTest(StandardTestDispatcher()) {
        val adaptador = adaptador()
        adaptador.comenzar()
        advanceUntilIdle()

        val esperando = async { adaptador.terminar() }
        advanceUntilIdle()
        reconocedor.emite(EventoDelReconocedor.Fallo(ERROR_SERVER))
        advanceUntilIdle()

        assertEquals(FalloDelDictado.MOTOR_FALLO, motivoDe(esperando.await()))
        val evento = telemetry.recorded.single()
        assertEquals(SpeechTelemetria.CODE_ANDROID_FALLO, evento.name)
        assertEquals(ERROR_SERVER.toString(), evento.props[SpeechTelemetria.PROP_ERROR])
    }

    /**
     * **El audio que no se pudo cerrar se reporta y el texto NO se pierde.**
     * Es el caso que distingue "falló el dictado" de "falló el respaldo".
     */
    @Test
    fun `si el audio no se guarda el texto sobrevive y se reporta`() =
        runTest(StandardTestDispatcher()) {
            grabadora.cierra = false
            val adaptador = adaptador()
            adaptador.comenzar()
            advanceUntilIdle()

            val esperando = async { adaptador.terminar() }
            advanceUntilIdle()
            reconocedor.emite(EventoDelReconocedor.Final("no estaba"))
            advanceUntilIdle()

            val terminado = esperando.await().getOrThrow()
            assertEquals("no estaba", terminado.texto)
            assertEquals(null, terminado.grabacion)
            assertEquals(
                SpeechTelemetria.CODE_AUDIO_NO_SE_GUARDO,
                telemetry.recorded.single().name
            )
        }

    /** Cancelar NO borra la grabación: es sobre el texto, no sobre el hecho. */
    @Test
    fun `cancelar conserva la grabacion`() = runTest(StandardTestDispatcher()) {
        val adaptador = adaptador()
        adaptador.comenzar()
        advanceUntilIdle()

        adaptador.cancelar()
        advanceUntilIdle()

        assertEquals(listOf("comenzar", "cancelar"), grabadora.llamadas)
        assertTrue("cancelar no debe pedir el resultado", "detener" !in reconocedor.llamadas)
    }

    /** Terminar sin haber empezado no lanza: una recomposición no tumba la pantalla. */
    @Test
    fun `terminar sin comenzar falla sin lanzar`() = runTest(StandardTestDispatcher()) {
        val resultado = adaptador().terminar()
        advanceUntilIdle()

        assertTrue(resultado.isFailure)
        assertEquals(FalloDelDictado.MOTOR_FALLO, motivoDe(resultado))
    }

    /**
     * **El audio se comparte con el reconocedor.** Con esta fuente el
     * reconocedor no abre un segundo micrófono, que es lo único que permite que
     * la grabación exista en el modo de Android. El fake entrega `null`, así que
     * lo que se afirma es que el adaptador **pregunta** a la grabadora en vez de
     * mandar un valor propio.
     */
    @Test
    fun `le pasa al reconocedor la fuente de la grabadora`() = runTest(StandardTestDispatcher()) {
        adaptador().comenzar()
        advanceUntilIdle()

        assertEquals(grabadora.fuenteCompartida(), reconocedor.fuenteRecibida)
        assertFalse("no se llamo al reconocedor", reconocedor.llamadas.isEmpty())
    }

    private fun motivoDe(resultado: Result<*>): FalloDelDictado? =
        (resultado.exceptionOrNull() as? DictadoFallido)?.fallo

    private companion object {
        /** `SpeechRecognizer.ERROR_NO_MATCH`. */
        const val ERROR_NO_MATCH = 7

        /** `SpeechRecognizer.ERROR_SERVER`. */
        const val ERROR_SERVER = 4
    }
}
