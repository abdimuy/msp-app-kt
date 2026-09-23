package com.example.msp_app.core.speech.domain.port

import com.example.msp_app.core.speech.domain.DictadoTerminado
import com.example.msp_app.core.speech.domain.EstadoDelDictado
import com.example.msp_app.core.speech.domain.FalloDelDictado
import com.example.msp_app.core.speech.domain.MotorDeDictado
import kotlinx.coroutines.flow.Flow

/**
 * **El dictado, visto por quien lo usa.**
 *
 * ## Por qué existe este puerto (Ruling BF)
 *
 * Cumple **dos** de las tres justificaciones, no una:
 *
 * 1. **≥2 implementaciones reales** — `AndroidDictadoAdapter` (el reconocedor
 *    del teléfono) y `WhisperDictadoAdapter` (whisper.cpp con el modelo
 *    descargado), más el delegador que elige entre ellos.
 * 2. **Cruza módulo** — `:feature:visitas` dicta la nota de la visita y no
 *    puede ver `android.speech` ni un `.so`.
 *
 * ## La UI NUNCA sabe cuál corre
 *
 * Esa es la propiedad entera. El cobrador con el modelo descargado y el que no
 * lo descargó usan **la misma pantalla**, con las mismas barritas y el mismo
 * borde: lo único que cambia es qué tan bien sale el texto. Si algún día la UI
 * tiene que ramificar por motor, el puerto falló.
 *
 * ## Operaciones falibles con `Result<T>`, como `PrinterPort`
 *
 * [comenzar] y [terminar] pueden fallar por seis motivos distintos
 * ([FalloDelDictado]) y ninguno de los seis es excepcional: un permiso negado y
 * un micrófono ocupado son martes por la tarde en una puerta. Devolverlos como
 * valor obliga a quien llama a decidir qué hace, que es exactamente lo que se
 * quiere — **la nota nunca se pierde por un fallo del dictado**.
 */
interface DictadoPort {

    /**
     * Qué se puede hacer hoy, en este teléfono, con este permiso.
     *
     * Se consulta **antes** de pintar el micrófono: un afordante que no puede
     * hacer nada es la mentira que este repo ya arregló dos veces en el CTA de
     * la visita.
     */
    suspend fun disponibilidad(): DisponibilidadDelDictado

    /**
     * Lo que está pasando con el micrófono, para pintarlo.
     *
     * Hay **un** micrófono, así que hay **un** estado: el `Flow` es caliente y
     * lo comparten todos los suscriptores. Uno frío por llamada sugeriría que
     * dos campos pueden dictar a la vez, que es justo lo que el hardware no
     * permite.
     */
    fun estado(): Flow<EstadoDelDictado>

    /**
     * Abre el micrófono. Falla —sin lanzar— si no hay permiso, si no hay motor
     * o si el micrófono está ocupado.
     */
    suspend fun comenzar(): Result<Unit>

    /**
     * Suelta el micrófono y entrega lo que haya: el texto, la grabación, o
     * solo uno de los dos.
     *
     * Llamarlo sin haber llamado a [comenzar] devuelve
     * [FalloDelDictado.MOTOR_FALLO] en vez de lanzar: una recomposición a
     * destiempo no puede tumbar la pantalla del registro.
     */
    suspend fun terminar(): Result<DictadoTerminado>

    /**
     * Corta sin entregar texto. **La grabación se conserva igual** — cancelar
     * es "no quiero esta transcripción", no "no pasó nada".
     */
    suspend fun cancelar()
}

/**
 * Qué puede el dictado ahora mismo.
 *
 * [motor] es `null` cuando no hay ninguno: un teléfono anterior a Android 13
 * sin el modelo descargado. En ese caso el micrófono **no se pinta** y la nota
 * se escribe a mano, sin aviso rojo — no se perdió trabajo, nunca lo hubo.
 */
data class DisponibilidadDelDictado(
    val motor: MotorDeDictado?,
    val permisoConcedido: Boolean
) {
    /** ¿Tiene sentido pintar el micrófono? */
    val sePuedeDictar: Boolean get() = motor != null

    /** Lo que falta para dictar, o `null` si no falta nada. */
    val falta: FalloDelDictado?
        get() = when {
            motor == null -> FalloDelDictado.SIN_MOTOR
            !permisoConcedido -> FalloDelDictado.SIN_PERMISO
            else -> null
        }
}

/** El fallo como excepción, para que quepa en un `Result`. Nunca se lanza fuera. */
class DictadoFallido(val fallo: FalloDelDictado) : Exception(fallo.name)
