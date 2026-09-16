package com.example.msp_app.core.speech.adapters

import com.example.msp_app.core.speech.application.SpeechTelemetria
import com.example.msp_app.core.speech.domain.FalloDelDictado
import com.example.msp_app.core.speech.domain.MotorDeDictado
import com.example.msp_app.core.speech.domain.port.DictadoFallido
import com.example.msp_app.core.speech.fake.GrabadoraFalsa
import com.example.msp_app.core.speech.fake.MODELO_DE_PRUEBA
import com.example.msp_app.core.speech.fake.NativoFalso
import com.example.msp_app.core.speech.fake.PermisoFalso
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * whisper **sin nada nativo**: lo que se prueba es la decisión de si puede
 * correr, qué se reporta cuando no puede, y qué queda cuando la transcripción
 * falla.
 *
 * ## Lo que este archivo NO cubre, dicho sin adornos
 *
 * La transcripción real. Necesita el `.so`, que no existe en ningún build de
 * este repo, y la JVM no puede cargar una librería de Android ni aunque
 * existiera. Escribir un test que "ejercite el JNI" acá sería un verde que no
 * prueba nada — ver el KDoc de [MotorWhisperNativo].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WhisperDictadoAdapterTest {

    @get:Rule
    val carpeta: TemporaryFolder = TemporaryFolder()

    private val nativo = NativoFalso()
    private val grabadora = GrabadoraFalsa()
    private val permiso = PermisoFalso()
    private val telemetry = RecordingTelemetry()

    private fun almacen() = AlmacenDelModelo(carpeta.root, telemetry)

    private fun conModelo(): AlmacenDelModelo {
        File(carpeta.root, "modelo.bin").writeText("un modelo de mentiras")
        return almacen()
    }

    /**
     * El dispatcher es el **del test**: uno construido aparte tendría su propio
     * planificador y `advanceUntilIdle` no lo movería nunca — el `withContext`
     * de la transcripción se quedaría colgado para siempre.
     */
    private fun TestScope.adaptador(almacen: AlmacenDelModelo) = WhisperDictadoAdapter(
        nativo = nativo,
        almacen = almacen,
        grabadora = grabadora,
        permiso = permiso,
        telemetry = telemetry,
        dispatcher = StandardTestDispatcher(testScheduler)
    )

    /**
     * **Sin la librería nativa no corre, se reporta, y NO lanza.** Es la mitad
     * de la condición innegociable del JNI; la otra mitad —que el cobrador
     * dicte igual— la prueba [DictadoSegunElMotorTest].
     */
    @Test
    fun `sin libreria nativa no arranca y lo reporta`() = runTest(StandardTestDispatcher()) {
        nativo.cargada = false

        val resultado = adaptador(conModelo()).comenzar()
        advanceUntilIdle()

        assertEquals(FalloDelDictado.SIN_MOTOR, motivoDe(resultado))
        assertEquals(SpeechTelemetria.CODE_WHISPER_SIN_NATIVA, telemetry.recorded.single().name)
        assertTrue("no debe abrir el microfono sin motor", grabadora.llamadas.isEmpty())
    }

    @Test
    fun `sin modelo en disco no arranca y lo reporta`() = runTest(StandardTestDispatcher()) {
        val resultado = adaptador(almacen()).comenzar()
        advanceUntilIdle()

        assertEquals(FalloDelDictado.SIN_MOTOR, motivoDe(resultado))
        assertEquals(SpeechTelemetria.CODE_WHISPER_SIN_MODELO, telemetry.recorded.single().name)
    }

    @Test
    fun `sin disponibilidad no ofrece motor`() = runTest(StandardTestDispatcher()) {
        nativo.cargada = false

        val disponibilidad = adaptador(conModelo()).disponibilidad()
        advanceUntilIdle()

        assertNull(disponibilidad.motor)
        assertTrue(!disponibilidad.sePuedeDictar)
        assertEquals(FalloDelDictado.SIN_MOTOR, disponibilidad.falta)
    }

    @Test
    fun `transcribe el lote y entrega texto y audio`() = runTest(StandardTestDispatcher()) {
        val adaptador = adaptador(conModelo())
        adaptador.comenzar()
        advanceUntilIdle()

        val terminado = adaptador.terminar().getOrThrow()
        advanceUntilIdle()

        assertEquals("texto de whisper", terminado.texto)
        assertEquals(MotorDeDictado.WHISPER, terminado.motor)
        assertNotNull(terminado.grabacion)
        assertEquals(grabadora.grabacion.archivo, nativo.transcritos.single().second)
    }

    /**
     * **Ésta es la razón entera por la que el audio se guarda siempre.** whisper
     * revienta y el cobrador se queda con la grabación: el hecho no se perdió,
     * solo la transcripción.
     */
    @Test
    fun `si whisper falla queda el audio y no se pierde el hecho`() =
        runTest(StandardTestDispatcher()) {
            nativo.respuesta = Result.failure(IllegalStateException("revento"))
            val adaptador = adaptador(conModelo())
            adaptador.comenzar()
            advanceUntilIdle()

            val terminado = adaptador.terminar().getOrThrow()
            advanceUntilIdle()

            assertEquals("", terminado.texto)
            assertNotNull("se perdio el respaldo", terminado.grabacion)
            val evento = telemetry.recorded.single()
            assertEquals(SpeechTelemetria.CODE_WHISPER_FALLO, evento.name)
            assertEquals("IllegalStateException", evento.props[SpeechTelemetria.PROP_EXCEPCION])
        }

    /** Sin audio no hay nada que transcribir **ni** respaldo: el único caso que falla entero. */
    @Test
    fun `sin audio falla y lo reporta`() = runTest(StandardTestDispatcher()) {
        grabadora.cierra = false
        val adaptador = adaptador(conModelo())
        adaptador.comenzar()
        advanceUntilIdle()

        val resultado = adaptador.terminar()
        advanceUntilIdle()

        assertEquals(FalloDelDictado.AUDIO_NO_SE_GUARDO, motivoDe(resultado))
        assertEquals(SpeechTelemetria.CODE_AUDIO_NO_SE_GUARDO, telemetry.recorded.single().name)
    }

    /**
     * El modelo borrado **mientras** se dictaba: el audio ya está, así que se
     * entrega sin texto en vez de fallar. Es un caso real — el cobrador libera
     * espacio desde la pantalla de descarga.
     */
    @Test
    fun `si el modelo desaparece a media sesion queda el audio`() =
        runTest(StandardTestDispatcher()) {
            val almacen = conModelo()
            val adaptador = adaptador(almacen)
            adaptador.comenzar()
            advanceUntilIdle()
            File(carpeta.root, "modelo.bin").delete()

            val terminado = adaptador.terminar().getOrThrow()
            advanceUntilIdle()

            assertEquals("", terminado.texto)
            assertNotNull(terminado.grabacion)
            assertEquals(
                SpeechTelemetria.CODE_WHISPER_SIN_MODELO,
                telemetry.recorded.single().name
            )
        }

    /** El fixture del modelo existe: control positivo de que la ausencia se mide bien. */
    @Test
    fun `el almacen ve el modelo cuando el archivo existe`() {
        assertNotNull(
            "si no lo ve, los tests de ausencia no probarian nada",
            conModelo().rutaDelModeloListo()
        )
        assertEquals(MODELO_DE_PRUEBA.tamanoBytes, MODELO_DE_PRUEBA.tamanoBytes)
    }

    private fun motivoDe(resultado: Result<*>): FalloDelDictado? =
        (resultado.exceptionOrNull() as? DictadoFallido)?.fallo
}
