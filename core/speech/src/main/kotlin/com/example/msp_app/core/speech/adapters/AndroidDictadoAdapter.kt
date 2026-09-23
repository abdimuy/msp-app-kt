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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * **El dictado con el reconocedor que el teléfono ya trae.**
 *
 * Gratis, sin descargar nada, disponible desde Android 13
 * (`createOnDeviceSpeechRecognizer`) — y peor con apodos y con habla de barrio,
 * que es exactamente lo que el cobrador dicta. Por eso existe el otro modo; por
 * eso éste es el piso y no el techo.
 *
 * ## El audio se graba AUNQUE el reconocedor falle
 *
 * [terminar] cierra la grabación **antes** de mirar cómo le fue al
 * reconocedor. Es la regla dura del dictado escrita como orden de ejecución: si
 * el reconocedor devuelve `ERROR_NO_MATCH`, el cobrador igual se queda con los
 * ocho segundos de audio y el hecho no se perdió. Al revés —mirar el texto
 * primero y salir temprano— es como se pierde una grabación.
 *
 * ## Los doce errores del reconocedor, mapeados a seis motivos
 *
 * `SpeechRecognizer` tiene una docena de constantes `ERROR_*` y la UI solo
 * puede decir cuatro cosas útiles. El mapeo ([motivoDe]) es la parte que sí se
 * prueba en JVM, y es donde estaba el defecto que importa: `ERROR_NO_MATCH` y
 * `ERROR_SPEECH_TIMEOUT` **no son fallos** —son silencio— y pintarlos como
 * error convertiría "no dijo nada" en "algo se rompió".
 */
class AndroidDictadoAdapter(
    private val reconocedor: ReconocedorDeAndroid,
    private val grabadora: GrabadoraDeAudio,
    private val permiso: PermisoDeMicrofono,
    private val telemetry: Telemetry,
    private val ahoraMs: () -> Long
) : DictadoPort {

    private val estado = MutableStateFlow<EstadoDelDictado>(EstadoDelDictado.Reposo)

    /** Lo confirmado por el reconocedor en esta sesión. `null` fuera de sesión. */
    private var resultado: CompletableDeferred<EventoDelReconocedor>? = null

    private var comenzoEnMs: Long = 0L

    override suspend fun disponibilidad(): DisponibilidadDelDictado = DisponibilidadDelDictado(
        motor = if (reconocedor.disponible) MotorDeDictado.ANDROID else null,
        permisoConcedido = permiso.concedido()
    )

    override fun estado(): Flow<EstadoDelDictado> = estado.asStateFlow()

    override suspend fun comenzar(): Result<Unit> {
        // Las dos condiciones previas se preguntan por la MISMA función que la
        // pantalla usa para decidir si pinta el micrófono. Con dos `if` sueltos
        // acá, la pantalla y el adaptador podían desacordar sobre qué falta.
        disponibilidad().falta?.let { return fallo(it) }

        val grabando = grabadora.comenzar()
        if (grabando.isFailure) {
            // El micrófono no abrió. Se reporta con el nombre de la clase de
            // excepción —nunca su `message`, que puede arrastrar la ruta del
            // archivo y con ella el `filesDir` del dispositivo.
            telemetry.error(
                code = SpeechTelemetria.CODE_MICROFONO_NO_ABRIO,
                message = "el microfono no abrio para el reconocedor de android",
                props = mapOf(
                    SpeechTelemetria.PROP_MOTOR to MotorDeDictado.ANDROID.name,
                    SpeechTelemetria.PROP_EXCEPCION to nombreDe(grabando.exceptionOrNull())
                )
            )
            return fallo(FalloDelDictado.MICROFONO_OCUPADO)
        }

        val espera = CompletableDeferred<EventoDelReconocedor>()
        resultado = espera
        comenzoEnMs = ahoraMs()
        estado.value = EstadoDelDictado.Escuchando()

        if (!reconocedor.comenzar(grabadora.fuenteCompartida(), ::alEvento)) {
            telemetry.error(
                code = SpeechTelemetria.CODE_ANDROID_NO_ARRANCO,
                message = "createOnDeviceSpeechRecognizer contesto disponible y no arranco",
                props = mapOf(SpeechTelemetria.PROP_MOTOR to MotorDeDictado.ANDROID.name)
            )
            // La grabación ya está abierta: se cierra y se conserva, porque la
            // regla del audio no depende de que el reconocedor arranque.
            grabadora.cancelar()
            cerrarSesion()
            return fallo(FalloDelDictado.MOTOR_FALLO)
        }
        return Result.success(Unit)
    }

    override suspend fun terminar(): Result<DictadoTerminado> {
        val espera = resultado ?: return fallo(FalloDelDictado.MOTOR_FALLO)
        estado.value = EstadoDelDictado.Transcribiendo
        reconocedor.detener()

        // PRIMERO el audio. Ver el KDoc de la clase: el orden es la regla.
        val grabacion = grabadora.terminar()
        val evento = espera.await()
        cerrarSesion()

        if (grabacion.isFailure) {
            telemetry.error(
                code = SpeechTelemetria.CODE_AUDIO_NO_SE_GUARDO,
                message = "la grabacion no se pudo cerrar; el texto se conserva",
                props = mapOf(
                    SpeechTelemetria.PROP_MOTOR to MotorDeDictado.ANDROID.name,
                    SpeechTelemetria.PROP_EXCEPCION to nombreDe(grabacion.exceptionOrNull())
                )
            )
        }

        return when (evento) {
            is EventoDelReconocedor.Final -> Result.success(
                DictadoTerminado(
                    texto = evento.texto,
                    grabacion = grabacion.getOrNull(),
                    motor = MotorDeDictado.ANDROID
                )
            )

            is EventoDelReconocedor.Fallo -> {
                val motivo = motivoDe(evento.codigo)
                if (motivo != FalloDelDictado.SIN_HABLA) {
                    telemetry.error(
                        code = SpeechTelemetria.CODE_ANDROID_FALLO,
                        message = "el reconocedor de android contesto un error",
                        props = mapOf(
                            SpeechTelemetria.PROP_MOTOR to MotorDeDictado.ANDROID.name,
                            SpeechTelemetria.PROP_ERROR to evento.codigo.toString()
                        )
                    )
                }
                // Silencio NO es un fallo: se entrega un dictado vacío con su
                // grabación, y el campo queda como estaba. El resto sí falla,
                // pero la grabación ya quedó guardada de todos modos.
                if (motivo == FalloDelDictado.SIN_HABLA) {
                    Result.success(
                        DictadoTerminado("", grabacion.getOrNull(), MotorDeDictado.ANDROID)
                    )
                } else {
                    fallo(motivo)
                }
            }

            is EventoDelReconocedor.Parcial -> fallo(FalloDelDictado.MOTOR_FALLO)
        }
    }

    override suspend fun cancelar() {
        reconocedor.cancelar()
        // Se conserva: cancelar es sobre el TEXTO, no sobre el hecho.
        grabadora.cancelar()
        cerrarSesion()
    }

    private fun alEvento(evento: EventoDelReconocedor) {
        when (evento) {
            is EventoDelReconocedor.Parcial -> estado.update {
                EstadoDelDictado.Escuchando(
                    parcial = evento.texto,
                    nivel = grabadora.nivel(),
                    transcurridoMs = ahoraMs() - comenzoEnMs
                )
            }

            else -> resultado?.complete(evento)
        }
    }

    private fun cerrarSesion() {
        resultado = null
        estado.value = EstadoDelDictado.Reposo
    }

    private fun <T> fallo(motivo: FalloDelDictado): Result<T> =
        Result.failure(DictadoFallido(motivo))

    private companion object {

        /**
         * Los códigos de `SpeechRecognizer`, traducidos.
         *
         * Se escriben como enteros y NO como `SpeechRecognizer.ERROR_*` a
         * propósito: importar `android.speech` acá ataría el mapeo —que es lo
         * único probable en JVM— a una clase que Robolectric tiene que cargar.
         * Los valores son estables desde API 8 y están anotados uno por uno.
         */
        @Suppress("MagicNumber") // son las constantes de SpeechRecognizer, anotadas al lado.
        fun motivoDe(codigo: Int): FalloDelDictado = when (codigo) {
            // ERROR_NO_MATCH (7) y ERROR_SPEECH_TIMEOUT (6): NO son fallos.
            // Nadie dijo nada. Pintarlos en rojo sería inventar un problema.
            6, 7 -> FalloDelDictado.SIN_HABLA
            // ERROR_AUDIO (3) y ERROR_RECOGNIZER_BUSY (8).
            3, 8 -> FalloDelDictado.MICROFONO_OCUPADO
            // ERROR_INSUFFICIENT_PERMISSIONS (9): el permiso se revocó mientras
            // se dictaba. Pasa de verdad — el usuario lo quita desde ajustes.
            9 -> FalloDelDictado.SIN_PERMISO
            // ERROR_CANNOT_CHECK_SUPPORT (14) / ERROR_LANGUAGE_NOT_SUPPORTED
            // (12) / ERROR_LANGUAGE_UNAVAILABLE (13): no hay motor para este
            // idioma en este teléfono, que es lo mismo que no haber motor.
            12, 13, 14 -> FalloDelDictado.SIN_MOTOR
            // El resto (NETWORK, SERVER, CLIENT, TOO_MANY_REQUESTS…) es el motor.
            else -> FalloDelDictado.MOTOR_FALLO
        }

        fun nombreDe(error: Throwable?): String = error?.javaClass?.simpleName ?: "desconocida"
    }
}
