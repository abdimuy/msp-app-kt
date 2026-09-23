package com.example.msp_app.core.speech.adapters

import com.example.msp_app.core.speech.domain.DictadoTerminado
import com.example.msp_app.core.speech.domain.EstadoDelDictado
import com.example.msp_app.core.speech.domain.FalloDelDictado
import com.example.msp_app.core.speech.domain.MotorDeDictado
import com.example.msp_app.core.speech.domain.port.DictadoFallido
import com.example.msp_app.core.speech.domain.port.DictadoPort
import com.example.msp_app.core.speech.domain.port.DisponibilidadDelDictado
import com.example.msp_app.core.speech.fake.NativoFalso
import com.example.msp_app.core.speech.fake.PermisoFalso
import com.example.msp_app.core.speech.fake.ReconocedorFalso
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **La degradación, medida sobre el objeto que la app inyecta de verdad.**
 *
 * [ElegirElMotorDeDictadoTest][com.example.msp_app.core.speech.application
 * .ElegirElMotorDeDictadoTest] prueba la tabla; esto prueba que el delegador
 * **la usa** y que el motor elegido es el que recibe la llamada. Las dos cosas
 * hacen falta: una tabla correcta que nadie consulta no degrada nada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DictadoSegunElMotorTest {

    @get:Rule
    val carpeta: TemporaryFolder = TemporaryFolder()

    private val android = MotorEspia(MotorDeDictado.ANDROID)
    private val whisper = MotorEspia(MotorDeDictado.WHISPER)
    private val nativo = NativoFalso()
    private val reconocedor = ReconocedorFalso()
    private val permiso = PermisoFalso()

    private fun almacen() = AlmacenDelModelo(carpeta.root, RecordingTelemetry())

    private fun conModelo(): AlmacenDelModelo {
        File(carpeta.root, "modelo.bin").writeText("un modelo de mentiras")
        return almacen()
    }

    private fun delegador(almacen: AlmacenDelModelo) = DictadoSegunElMotor(
        android = android,
        whisper = whisper,
        nativo = nativo,
        almacen = almacen,
        reconocedorDeAndroid = reconocedor,
        permiso = permiso
    )

    /**
     * **Un teléfono SIN el modelo dicta igual.** Es la condición innegociable
     * del JNI, medida de punta a punta: el delegador manda al reconocedor de
     * Android y whisper ni se entera.
     */
    @Test
    fun `sin modelo dicta con el reconocedor de android`() = runTest(StandardTestDispatcher()) {
        val delegador = delegador(almacen())

        assertEquals(MotorDeDictado.ANDROID, delegador.disponibilidad().motor)
        assertTrue(delegador.comenzar().isSuccess)
        advanceUntilIdle()

        assertEquals(listOf("comenzar"), android.llamadas)
        assertTrue("whisper no debia correr", whisper.llamadas.isEmpty())
    }

    /** Con la librería y el modelo, gana whisper — y el de Android no se toca. */
    @Test
    fun `con modelo y libreria dicta con whisper`() = runTest(StandardTestDispatcher()) {
        val delegador = delegador(conModelo())

        assertEquals(MotorDeDictado.WHISPER, delegador.disponibilidad().motor)
        assertTrue(delegador.comenzar().isSuccess)
        advanceUntilIdle()

        assertEquals(listOf("comenzar"), whisper.llamadas)
        assertTrue("el de android no debia correr", android.llamadas.isEmpty())
    }

    /** La librería ausente con el modelo presente sigue degradando. */
    @Test
    fun `con modelo pero sin libreria degrada a android`() = runTest(StandardTestDispatcher()) {
        nativo.cargada = false
        val delegador = delegador(conModelo())

        assertEquals(MotorDeDictado.ANDROID, delegador.disponibilidad().motor)
        assertTrue(delegador.comenzar().isSuccess)
        advanceUntilIdle()

        assertEquals(listOf("comenzar"), android.llamadas)
    }

    /**
     * **Sin ningún motor no se lanza nada.** Se contesta `SIN_MOTOR` y la
     * pantalla escribe la nota a mano: el dictado es una capacidad degradable,
     * no un requisito.
     */
    @Test
    fun `sin ningun motor falla sin lanzar`() = runTest(StandardTestDispatcher()) {
        nativo.cargada = false
        reconocedor.disponible = false
        val delegador = delegador(almacen())

        val resultado = delegador.comenzar()
        advanceUntilIdle()

        assertEquals(
            FalloDelDictado.SIN_MOTOR,
            (resultado.exceptionOrNull() as? DictadoFallido)?.fallo
        )
        assertTrue(android.llamadas.isEmpty() && whisper.llamadas.isEmpty())
    }

    /**
     * **El modelo que termina de bajar a media jornada cambia el motor sin
     * reiniciar la app.** Se decide en cada `comenzar`, no al construir.
     */
    @Test
    fun `si el modelo aparece despues el siguiente dictado ya es whisper`() =
        runTest(StandardTestDispatcher()) {
            val almacen = almacen()
            val delegador = delegador(almacen)
            delegador.comenzar()
            delegador.terminar()
            advanceUntilIdle()
            assertEquals(listOf("comenzar", "terminar"), android.llamadas)

            File(carpeta.root, "modelo.bin").writeText("ya bajo")
            delegador.comenzar()
            advanceUntilIdle()

            assertEquals(listOf("comenzar"), whisper.llamadas)
        }

    /** Terminar sin haber empezado no lanza ni llama a ningún motor. */
    @Test
    fun `terminar sin comenzar falla sin tocar los motores`() = runTest(StandardTestDispatcher()) {
        val resultado = delegador(conModelo()).terminar()
        advanceUntilIdle()

        assertEquals(
            FalloDelDictado.MOTOR_FALLO,
            (resultado.exceptionOrNull() as? DictadoFallido)?.fallo
        )
        assertTrue(android.llamadas.isEmpty() && whisper.llamadas.isEmpty())
    }

    /** Un puerto que solo graba a quién llamaron. Estado público, sin mocks. */
    private class MotorEspia(private val motor: MotorDeDictado) : DictadoPort {

        val llamadas: MutableList<String> = mutableListOf()

        override suspend fun disponibilidad() = DisponibilidadDelDictado(motor, true)

        override fun estado(): Flow<EstadoDelDictado> = flowOf(EstadoDelDictado.Reposo)

        override suspend fun comenzar(): Result<Unit> {
            llamadas += "comenzar"
            return Result.success(Unit)
        }

        override suspend fun terminar(): Result<DictadoTerminado> {
            llamadas += "terminar"
            return Result.success(DictadoTerminado("", null, motor))
        }

        override suspend fun cancelar() {
            llamadas += "cancelar"
        }
    }
}
