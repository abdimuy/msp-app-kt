package com.example.msp_app.core.speech.domain

/**
 * **Qué motor de dictado está corriendo.**
 *
 * La UI **no** lee esto para decidir nada — ésa es la razón de ser de
 * [com.example.msp_app.core.speech.domain.port.DictadoPort]. Existe para dos
 * cosas y solo dos: la telemetría (saber con qué motor se produjo un fallo sin
 * mandar una sola letra de lo dictado) y la pantalla de descarga, que sí tiene
 * que poder decir la verdad sobre cuál está activo.
 */
enum class MotorDeDictado {
    /**
     * `SpeechRecognizer.createOnDeviceSpeechRecognizer()`, el que ya trae el
     * teléfono desde Android 13. Gratis, sin descargar nada, y se equivoca más
     * con apodos y con habla de la calle.
     */
    ANDROID,

    /** whisper.cpp con el modelo `tiny` int8 descargado. Por lotes, no en vivo. */
    WHISPER
}

/**
 * Lo que puede salir mal al dictar. Cerrado a propósito: cada caso tiene un
 * texto propio en la UI y un `code` propio en telemetría, y un `OTRO` cajón de
 * sastre convertiría los dos en "algo falló".
 */
enum class FalloDelDictado {
    /** El cobrador no dio (o revocó) `RECORD_AUDIO`. La nota se escribe a mano. */
    SIN_PERMISO,

    /** Ningún motor puede correr en este teléfono. La nota se escribe a mano. */
    SIN_MOTOR,

    /** Se escuchó, pero no había palabras. Ni se pierde nada ni se agrega nada. */
    SIN_HABLA,

    /** El micrófono no se pudo abrir o lo tiene otra app. */
    MICROFONO_OCUPADO,

    /** El motor contestó un error propio. Lo dictado hasta ahí se conserva. */
    MOTOR_FALLO,

    /** El audio no se pudo escribir a disco. **Lo transcrito NO se pierde.** */
    AUDIO_NO_SE_GUARDO
}

/**
 * La grabación que quedó en el teléfono, **siempre**.
 *
 * ## Por qué el audio importa más que la transcripción
 *
 * Un motor `tiny` se equivoca con los apodos, y un apodo mal transcrito en una
 * nota de cobranza es un dato falso, no uno incompleto. Con la grabación
 * guardada, el error de transcripción deja de costar el hecho: el cobrador —o
 * quien lea la visita después— puede oír lo que realmente se dijo.
 *
 * Por eso se guarda aunque el motor falle, aunque el texto salga vacío, y
 * aunque el cobrador borre el texto a mano.
 *
 * [archivo] es una **ruta absoluta** en el almacenamiento privado de la app, no
 * un `content://`: igual que
 * `com.example.msp_app.feature.visitas.domain.model.ComprobanteDeVisita`, tiene
 * que seguir siendo legible en un reintento tres horas después, cuando el
 * permiso de lectura de un `FileProvider` ya caducó.
 */
data class GrabacionDictada(
    val id: String,
    val archivo: String,
    val duracionMs: Long
) {
    init {
        require(id.isNotBlank()) { "una grabacion sin id no se puede adjuntar" }
        require(archivo.isNotBlank()) { "una grabacion sin archivo no existe" }
        require(duracionMs >= 0L) { "una grabacion no puede durar menos que nada" }
    }
}

/**
 * El resultado de un dictado: **texto** y **grabación**, y los dos son
 * independientes.
 *
 * [texto] puede venir vacío con [grabacion] presente (el motor no entendió pero
 * el audio quedó) y [grabacion] puede venir `null` con [texto] lleno (el motor
 * entendió pero el disco falló). Los dos casos son reales y ninguno de los dos
 * pierde el otro lado.
 */
data class DictadoTerminado(
    val texto: String,
    val grabacion: GrabacionDictada?,
    val motor: MotorDeDictado
)

/**
 * Qué está pasando ahora mismo con el micrófono. Lo pinta el campo con borde
 * vivo, y **no dice qué motor corre**: el campo se ve igual con los dos.
 */
sealed interface EstadoDelDictado {

    /** Nadie está dictando. El campo es un campo de texto y nada más. */
    data object Reposo : EstadoDelDictado

    /**
     * El micrófono está abierto.
     *
     * [parcial] es lo que el motor lleva entendido y **todavía puede cambiar**;
     * se pinta en gris detrás del texto confirmado, como el mock. Con whisper
     * —que transcribe por lotes al soltar— llega siempre vacío, y eso está
     * bien: el borde vivo y las barritas ya dicen que se está escuchando.
     *
     * [nivel] es `0f..1f` y lo dibujan las barritas. Es **volumen**, no forma de
     * onda: nueve barritas no describen una señal, dicen "te oigo".
     */
    data class Escuchando(
        val parcial: String = "",
        val nivel: Float = 0f,
        val transcurridoMs: Long = 0L
    ) : EstadoDelDictado {
        init {
            require(nivel in 0f..1f) { "el nivel del microfono es una fraccion" }
        }
    }

    /**
     * Se soltó el micrófono y el motor está transcribiendo el lote. Solo
     * whisper pasa por acá; el de Android entrega mientras escucha.
     */
    data object Transcribiendo : EstadoDelDictado
}
