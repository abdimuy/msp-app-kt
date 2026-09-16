package com.example.msp_app.core.speech.adapters

import android.os.ParcelFileDescriptor

/**
 * **El reconocedor del teléfono, como una costura.**
 *
 * Detrás vive `android.speech.SpeechRecognizer`, que es una clase con hilo
 * propio, callbacks, y un ciclo de vida que Robolectric no simula. Dejarlo
 * detrás de esta interfaz mueve lo que SÍ se puede probar —el mapeo de sus
 * doce códigos de error a los seis motivos del dominio, y qué pasa con el audio
 * cuando el reconocedor falla— a un test de JVM con un fake.
 *
 * Lo que queda sin cubrir es la implementación real
 * ([SpeechRecognizerDeAndroid]) y se dice en su KDoc, no se disimula.
 */
interface ReconocedorDeAndroid {

    /**
     * ¿Existe el reconocedor **en-dispositivo** en este teléfono?
     *
     * En-dispositivo y no el de red: el cobrador está en una puerta, muchas
     * veces sin señal, y un reconocedor que necesita internet fallaría justo
     * cuando hace falta. Es API 33+; el SM-A256E del dueño lo trae.
     */
    val disponible: Boolean

    /**
     * Empieza a escuchar. Los eventos llegan por [alEvento], en el hilo
     * principal (es donde `SpeechRecognizer` los entrega).
     *
     * [fuente] es el audio que **esta app** ya está capturando y guardando; con
     * él, el reconocedor no abre un segundo micrófono y la grabación se
     * conserva. `null` lo manda a abrir el suyo, y entonces no hay grabación.
     *
     * @return `false` si el reconocedor no se pudo crear.
     */
    fun comenzar(fuente: ParcelFileDescriptor?, alEvento: (EventoDelReconocedor) -> Unit): Boolean

    /** Deja de escuchar y pide el resultado final. */
    fun detener()

    /** Corta sin pedir resultado y suelta los recursos. */
    fun cancelar()
}

/** Lo que el reconocedor va diciendo mientras escucha. */
sealed interface EventoDelReconocedor {

    /** Lo que lleva entendido y **todavía puede cambiar**. */
    data class Parcial(val texto: String) : EventoDelReconocedor

    /** El resultado definitivo. Puede venir vacío: eso es silencio, no un fallo. */
    data class Final(val texto: String) : EventoDelReconocedor

    /** El código crudo de `SpeechRecognizer.ERROR_*`. Lo traduce el adaptador. */
    data class Fallo(val codigo: Int) : EventoDelReconocedor
}
