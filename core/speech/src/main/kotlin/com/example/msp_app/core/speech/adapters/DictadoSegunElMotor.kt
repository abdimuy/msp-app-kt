package com.example.msp_app.core.speech.adapters

import com.example.msp_app.core.speech.application.ElegirElMotorDeDictado
import com.example.msp_app.core.speech.domain.DictadoTerminado
import com.example.msp_app.core.speech.domain.EstadoDelDictado
import com.example.msp_app.core.speech.domain.FalloDelDictado
import com.example.msp_app.core.speech.domain.MotorDeDictado
import com.example.msp_app.core.speech.domain.port.DictadoFallido
import com.example.msp_app.core.speech.domain.port.DictadoPort
import com.example.msp_app.core.speech.domain.port.DisponibilidadDelDictado
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * **El único `DictadoPort` que la app inyecta.** Elige motor y delega.
 *
 * Es la pieza que hace verdad la frase "la UI nunca sabe cuál corre": lo que el
 * `:feature:visitas` recibe es esto, y esto no tiene ningún método que diga qué
 * motor va a usar antes de usarlo.
 *
 * ## La elección se hace en cada arranque de dictado, no al construir
 *
 * A propósito. El modelo puede terminar de bajar **mientras** el cobrador está
 * parado en una puerta, y decidir una sola vez al construir el grafo dejaría a
 * ese teléfono en el motor viejo hasta que la app se reinicie. También al revés:
 * el cobrador borra el modelo para liberar espacio y el siguiente dictado tiene
 * que caer solo al reconocedor de Android.
 *
 * ## La degradación es una sola línea y está probada
 *
 * [ElegirElMotorDeDictado] es pura y vive en `application/`; acá solo se le
 * pregunta. Si contesta `null` no hay dictado y **no se lanza nada**: el campo
 * se pinta sin micrófono y la nota se escribe a mano.
 */
class DictadoSegunElMotor(
    private val android: DictadoPort,
    private val whisper: DictadoPort,
    private val nativo: MotorWhisperNativo,
    private val almacen: AlmacenDelModelo,
    private val reconocedorDeAndroid: ReconocedorDeAndroid,
    private val permiso: PermisoDeMicrofono
) : DictadoPort {

    /**
     * El motor de la sesión **en curso**. Se fija al llamar a `comenzar` y se
     * suelta al terminar: cambiar de motor a media grabación entregaría el audio
     * de uno a la transcripción del otro.
     */
    private var enCurso: DictadoPort? = null

    /**
     * Cuál de los dos flujos de estado está enchufado al campo. Arranca en el
     * de Android porque es el que siempre existe; [comenzar] lo cambia si la
     * elección de hoy dice whisper.
     */
    private val enchufado = MutableStateFlow(android)

    override suspend fun disponibilidad(): DisponibilidadDelDictado = DisponibilidadDelDictado(
        motor = motorDeHoy(),
        permisoConcedido = permiso.concedido()
    )

    /**
     * El estado de los dos, aplanado.
     *
     * `flatMapLatest` sobre el motor en curso y no una suma de los dos flujos:
     * hay un micrófono, y mezclar los dos estados dejaría al campo pintando el
     * "Reposo" del motor apagado encima del "Escuchando" del que sí corre.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun estado(): Flow<EstadoDelDictado> = enchufado.flatMapLatest { it.estado() }

    override suspend fun comenzar(): Result<Unit> {
        val elegido = when (motorDeHoy()) {
            MotorDeDictado.WHISPER -> whisper
            MotorDeDictado.ANDROID -> android
            null -> return Result.failure(DictadoFallido(FalloDelDictado.SIN_MOTOR))
        }
        enCurso = elegido
        enchufado.value = elegido
        val arranque = elegido.comenzar()
        if (arranque.isFailure) enCurso = null
        return arranque
    }

    override suspend fun terminar(): Result<DictadoTerminado> {
        val motor = enCurso ?: return Result.failure(DictadoFallido(FalloDelDictado.MOTOR_FALLO))
        val terminado = motor.terminar()
        enCurso = null
        return terminado
    }

    override suspend fun cancelar() {
        enCurso?.cancelar()
        enCurso = null
    }

    private fun motorDeHoy(): MotorDeDictado? = ElegirElMotorDeDictado(
        libreriaNativaCargada = nativo.cargada,
        modeloListo = almacen.rutaDelModeloListo() != null,
        reconocedorDeAndroidDisponible = reconocedorDeAndroid.disponible
    )
}
