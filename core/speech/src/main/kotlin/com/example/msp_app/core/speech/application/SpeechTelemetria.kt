package com.example.msp_app.core.speech.application

/**
 * Los `code` de este módulo, en **un solo lugar** y como constantes: la norma de
 * errores pide que sean grepeables, y un literal escrito en el `catch` no lo es
 * cuando alguien lo escribe distinto la segunda vez.
 *
 * ## El texto dictado NO viaja a telemetría. Nunca.
 *
 * Es texto libre tecleado —dictado— por el usuario, y el KDoc de `Telemetry` lo
 * prohíbe con todas sus letras. Lo que puede salir de acá es: el nombre de la
 * clase de excepción, el nombre del motor, un código HTTP y una cuenta de
 * bytes. Ni una palabra de lo que el cliente dijo en la puerta, ni el nombre
 * del cliente, ni la ruta del archivo de audio — que lleva un UUID pero también
 * lleva el `filesDir` del dispositivo.
 */
object SpeechTelemetria {

    /** El reconocedor de Android contestó un error propio. */
    const val CODE_ANDROID_FALLO: String = "dictado_android_fallo"

    /** El reconocedor de Android no se pudo crear en un teléfono que decía soportarlo. */
    const val CODE_ANDROID_NO_ARRANCO: String = "dictado_android_no_arranco"

    /** La librería nativa de whisper no está: el puerto degrada al de Android. */
    const val CODE_WHISPER_SIN_NATIVA: String = "dictado_whisper_sin_libreria_nativa"

    /** El modelo no está en disco (o está a medias): el puerto degrada. */
    const val CODE_WHISPER_SIN_MODELO: String = "dictado_whisper_sin_modelo"

    /** whisper corrió y falló transcribiendo el lote. */
    const val CODE_WHISPER_FALLO: String = "dictado_whisper_fallo"

    /** El micrófono no se pudo abrir, o lo tiene otra app. */
    const val CODE_MICROFONO_NO_ABRIO: String = "dictado_microfono_no_abrio"

    /**
     * El audio no se pudo escribir a disco. **Se emite aunque el texto haya
     * salido bien**: perder la grabación es perder la red de seguridad de la
     * transcripción, y eso se tiene que ver desde afuera.
     */
    const val CODE_AUDIO_NO_SE_GUARDO: String = "dictado_audio_no_se_guardo"

    /** La descarga del modelo se cortó. Lo bajado se conserva. */
    const val CODE_DESCARGA_CORTADA: String = "dictado_modelo_descarga_cortada"

    /** El `.bin` bajó entero y su SHA-256 no coincide. Se borra y se rebaja. */
    const val CODE_MODELO_CORRUPTO: String = "dictado_modelo_checksum_no_coincide"

    /** No se pudo borrar el modelo que el cobrador pidió borrar. */
    const val CODE_MODELO_NO_SE_BORRO: String = "dictado_modelo_no_se_borro"

    /** Qué motor estaba corriendo. Nombre del enum, nunca texto del usuario. */
    const val PROP_MOTOR: String = "motor"

    /** El nombre de la clase de excepción. Nunca su `message`. */
    const val PROP_EXCEPCION: String = "excepcion"

    /** El código de error del reconocedor de Android (`SpeechRecognizer.ERROR_*`). */
    const val PROP_ERROR: String = "error"

    /** El código HTTP de la descarga. */
    const val PROP_HTTP: String = "http"

    /**
     * El idioma que este teléfono rechazó y hubo que cambiar.
     *
     * Se emite como error —no como evento— a propósito: que el aparato no acepte el
     * español que la app pide es el defecto que dejó el dictado muerto en el
     * SM-A256E, y si vuelve a pasar en otro modelo hay que enterarse sin que nadie
     * lo reporte a mano.
     *
     * **No es PII**: una etiqueta BCP-47 es un valor de catálogo del sistema, no un
     * dato del cliente. El texto dictado no viaja nunca.
     */
    const val CODE_ANDROID_IDIOMA_CAMBIADO: String = "speech_android_idioma_cambiado"

    /** La etiqueta de idioma elegida (`es-US`, `es-MX`…). */
    const val PROP_IDIOMA: String = "idioma"
}
