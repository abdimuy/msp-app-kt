package com.example.msp_app.core.speech.adapters

import com.example.msp_app.core.speech.application.SpeechTelemetria
import com.example.msp_app.core.speech.domain.DictadoTerminado
import com.example.msp_app.core.speech.domain.EstadoDelDictado
import com.example.msp_app.core.speech.domain.FalloDelDictado
import com.example.msp_app.core.speech.domain.MotorDeDictado
import com.example.msp_app.core.speech.domain.port.DictadoFallido
import com.example.msp_app.core.speech.domain.port.DictadoPort
import com.example.msp_app.core.speech.domain.port.DisponibilidadDelDictado
import com.example.msp_app.core.telemetry.Telemetry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * **El dictado con whisper: se graba, se suelta, se transcribe.**
 *
 * Por lotes, y eso no es una limitación que se arrastra sino el modo en que un
 * `tiny` int8 rinde en gama media. Mientras se habla no hay CPU sobrante en un
 * SM-A256E para transcribir Y pintar el campo; al soltar, sí. Por eso
 * [EstadoDelDictado.Transcribiendo] existe: es un estado real de este motor y no
 * del otro, y el campo lo pinta igual en los dos —el cobrador no tiene por qué
 * enterarse de cuál corre.
 *
 * ## No hay parciales, y el campo no miente por eso
 *
 * `Escuchando.parcial` llega siempre vacío acá. El borde vivo y las barritas
 * siguen diciendo "te oigo", que es lo único que el cobrador necesita ver
 * mientras habla. Inventar un parcial —mostrar lo anterior, o un "…"— sería una
 * forma que sugiere algo que el comportamiento no hace.
 *
 * ## Degrada sin lanzar, siempre
 *
 * Si la librería nativa no está, o el modelo no está en disco, [disponibilidad]
 * contesta `motor = null` y **quien manda es el delegador**
 * ([DictadoSegunElMotor]), que se va al reconocedor de Android. Este adaptador
 * nunca decide que el cobrador se queda sin dictar.
 */
class WhisperDictadoAdapter(
    private val nativo: MotorWhisperNativo,
    private val almacen: AlmacenDelModelo,
    private val grabadora: GrabadoraDeAudio,
    private val permiso: PermisoDeMicrofono,
    private val telemetry: Telemetry,
    private val dispatcher: CoroutineDispatcher
) : DictadoPort {

    private val estado = MutableStateFlow<EstadoDelDictado>(EstadoDelDictado.Reposo)

    /**
     * ¿Hay sesión abierta? Un `Boolean` y no un instante, y eso es una carencia
     * declarada: **este motor no puede mover el cronómetro ni las barritas
     * mientras graba**, porque transcribe por lotes y no produce ningún evento
     * entre `comenzar` y `terminar`. El campo pinta el borde vivo, que sí dice
     * "te oigo", y el cronómetro se queda en cero.
     *
     * No se tapa con un latido artificial porque un latido infinito dentro de
     * un adaptador es una corrutina que ningún test con `advanceUntilIdle`
     * puede terminar — y una que se cuelga en producción si alguien olvida
     * cancelarla. Se arregla el día que el `.so` exista y whisper pueda
     * reportar avance de verdad.
     */
    private var abierta: Boolean = false

    override suspend fun disponibilidad(): DisponibilidadDelDictado = DisponibilidadDelDictado(
        motor = if (puedeCorrer()) MotorDeDictado.WHISPER else null,
        permisoConcedido = permiso.concedido()
    )

    override fun estado(): Flow<EstadoDelDictado> = estado.asStateFlow()

    override suspend fun comenzar(): Result<Unit> {
        val mitadQueFalta = when {
            !nativo.cargada -> SpeechTelemetria.CODE_WHISPER_SIN_NATIVA
            almacen.rutaDelModeloListo() == null -> SpeechTelemetria.CODE_WHISPER_SIN_MODELO
            else -> null
        }
        if (mitadQueFalta != null) {
            telemetry.error(
                code = mitadQueFalta,
                message = "se pidio dictar con whisper y le falta una de sus dos mitades",
                props = mapOf(SpeechTelemetria.PROP_MOTOR to MotorDeDictado.WHISPER.name)
            )
            return fallo(FalloDelDictado.SIN_MOTOR)
        }
        if (!permiso.concedido()) return fallo(FalloDelDictado.SIN_PERMISO)

        val grabando = grabadora.comenzar()
        if (grabando.isFailure) {
            telemetry.error(
                code = SpeechTelemetria.CODE_MICROFONO_NO_ABRIO,
                message = "el microfono no abrio para whisper",
                props = mapOf(
                    SpeechTelemetria.PROP_MOTOR to MotorDeDictado.WHISPER.name,
                    SpeechTelemetria.PROP_EXCEPCION to nombreDe(grabando.exceptionOrNull())
                )
            )
            return fallo(FalloDelDictado.MICROFONO_OCUPADO)
        }
        abierta = true
        estado.value = EstadoDelDictado.Escuchando(nivel = grabadora.nivel())
        return Result.success(Unit)
    }

    override suspend fun terminar(): Result<DictadoTerminado> {
        if (!abierta) return fallo(FalloDelDictado.MOTOR_FALLO)
        estado.value = EstadoDelDictado.Transcribiendo
        val grabacion = grabadora.terminar()
        abierta = false

        val audio = grabacion.getOrNull()
        if (audio == null) {
            // Sin archivo no hay nada que transcribir Y no hay respaldo: es el
            // único camino donde el dictado se pierde entero, y por eso es el
            // que más se reporta.
            telemetry.error(
                code = SpeechTelemetria.CODE_AUDIO_NO_SE_GUARDO,
                message = "la grabacion no se pudo cerrar; whisper no tiene que transcribir",
                props = mapOf(
                    SpeechTelemetria.PROP_MOTOR to MotorDeDictado.WHISPER.name,
                    SpeechTelemetria.PROP_EXCEPCION to nombreDe(grabacion.exceptionOrNull())
                )
            )
            estado.value = EstadoDelDictado.Reposo
            return fallo(FalloDelDictado.AUDIO_NO_SE_GUARDO)
        }

        val modelo = almacen.rutaDelModeloListo()
        if (modelo == null) {
            // El modelo se borró MIENTRAS se dictaba (el cobrador liberó
            // espacio). El audio ya está guardado: se entrega sin texto en vez
            // de fallar, porque el hecho no se perdió.
            telemetry.error(
                code = SpeechTelemetria.CODE_WHISPER_SIN_MODELO,
                message = "el modelo desaparecio mientras se dictaba; queda el audio",
                props = mapOf(SpeechTelemetria.PROP_MOTOR to MotorDeDictado.WHISPER.name)
            )
            estado.value = EstadoDelDictado.Reposo
            return Result.success(DictadoTerminado("", audio, MotorDeDictado.WHISPER))
        }

        val texto = withContext(dispatcher) { nativo.transcribir(modelo, audio.archivo) }
        estado.value = EstadoDelDictado.Reposo

        return texto.fold(
            onSuccess = {
                Result.success(DictadoTerminado(it.trim(), audio, MotorDeDictado.WHISPER))
            },
            onFailure = { error ->
                telemetry.error(
                    code = SpeechTelemetria.CODE_WHISPER_FALLO,
                    message = "whisper fallo transcribiendo el lote; queda el audio",
                    props = mapOf(
                        SpeechTelemetria.PROP_MOTOR to MotorDeDictado.WHISPER.name,
                        SpeechTelemetria.PROP_EXCEPCION to nombreDe(error)
                    )
                )
                // Falla la transcripción, NO el dictado: se entrega la
                // grabación con texto vacío. Es la razón entera por la que el
                // audio se guarda siempre — un `tiny` que se equivoca (o que
                // revienta) no puede costar el hecho.
                Result.success(DictadoTerminado("", audio, MotorDeDictado.WHISPER))
            }
        )
    }

    override suspend fun cancelar() {
        grabadora.cancelar()
        abierta = false
        estado.value = EstadoDelDictado.Reposo
    }

    private fun puedeCorrer(): Boolean = nativo.cargada && almacen.rutaDelModeloListo() != null

    private fun <T> fallo(motivo: FalloDelDictado): Result<T> =
        Result.failure(DictadoFallido(motivo))

    private companion object {
        fun nombreDe(error: Throwable?): String = error?.javaClass?.simpleName ?: "desconocida"
    }
}
