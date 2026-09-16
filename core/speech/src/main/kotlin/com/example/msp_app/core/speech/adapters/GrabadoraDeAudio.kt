package com.example.msp_app.core.speech.adapters

import android.os.ParcelFileDescriptor
import com.example.msp_app.core.speech.domain.GrabacionDictada

/**
 * **El micrófono, como una costura.**
 *
 * Los DOS adaptadores graban con esto y por el mismo motivo: la regla dura del
 * dictado es que *el audio se guarda siempre*, y una regla que se cumple en un
 * motor y no en el otro no es una regla. Que sea una interfaz deja la lógica de
 * los adaptadores —qué se guarda, qué se reporta, qué se pierde— probable con
 * un fake, sin `AudioRecord` y sin un teléfono.
 *
 * ## Por qué esta interfaz vive en `adapters/` y no es un puerto de `domain/`
 *
 * Porque no cruza hacia afuera: nadie fuera de este módulo graba audio, y la UI
 * no la ve nunca. Un puerto de dominio para esto sería el defecto que
 * `DISPATCH-CONVENTIONS.md` nombra — abstracción sin consumidor real. Acá la
 * abstracción sí tiene consumidor: dos adaptadores hermanos.
 */
interface GrabadoraDeAudio {

    /**
     * Abre el micrófono y empieza a escribir a disco.
     *
     * Falla —con `Result`, sin lanzar— cuando el micrófono está ocupado o el
     * permiso no está. No distingue los dos casos: quien llama ya preguntó por
     * el permiso antes de pintar el botón, y un micrófono que no abre se dice
     * igual de las dos formas.
     */
    suspend fun comenzar(): Result<Unit>

    /**
     * Cierra el archivo y lo entrega.
     *
     * Devuelve `failure` cuando el archivo no se pudo cerrar o quedó vacío. Un
     * archivo vacío se descarta **a propósito**: un adjunto de cero bytes en la
     * visita es peor que ninguno, porque parece evidencia.
     */
    suspend fun terminar(): Result<GrabacionDictada>

    /**
     * Corta y **conserva** lo grabado, exactamente igual que [terminar].
     *
     * No es un descuido: cancelar el dictado significa "no quiero esta
     * transcripción", y la grabación es el respaldo de que el hecho existió.
     * Borrarla al cancelar convertiría un arrepentimiento sobre el texto en la
     * pérdida del audio.
     */
    suspend fun cancelar(): Result<GrabacionDictada>

    /**
     * El nivel de volumen actual, `0f..1f`, para las barritas.
     *
     * Se **consulta** en vez de emitirse por un `Flow` propio porque quien manda
     * el pulso del estado es el adaptador, y dos relojes para el mismo campo
     * producen barritas que no van con el borde.
     */
    fun nivel(): Float

    /**
     * El extremo de **lectura** del mismo audio que se está escribiendo a
     * disco, para que el reconocedor de Android no abra un segundo micrófono.
     *
     * ## Por qué existe: hay UN micrófono
     *
     * `SpeechRecognizer` toma el micrófono en exclusiva. Grabar a la vez con
     * otra fuente no funciona en la mayoría de los teléfonos, así que cumplir
     * "el audio se guarda siempre" con el motor de Android exige **una sola
     * captura** compartida: esta app abre el micrófono, escribe el WAV, y le
     * pasa al reconocedor este descriptor como `EXTRA_AUDIO_SOURCE` (API 31+).
     *
     * `null` significa "no puedo compartirlo": el reconocedor abre el micrófono
     * por su cuenta y **la grabación de esta sesión no existirá**. Es un
     * desenlace real en fabricantes que ignoran el extra, y el adaptador lo
     * reporta como [com.example.msp_app.core.speech.domain
     * .FalloDelDictado.AUDIO_NO_SE_GUARDO] sin perder el texto.
     */
    fun fuenteCompartida(): ParcelFileDescriptor?
}
