package com.example.msp_app.core.speech.adapters

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.msp_app.core.speech.application.SpeechTelemetria
import com.example.msp_app.core.speech.domain.IdiomaDelDictado
import com.example.msp_app.core.telemetry.Telemetry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **`SpeechRecognizer` de verdad. Lo que NINGÚN test de este repo cubre.**
 *
 * Esta clase habla con un servicio del sistema por callbacks en el hilo
 * principal. Robolectric no lo simula, y la compuerta no corre `androidTest`
 * (`CLAUDE.md` §2: las tareas `connected*` están excluidas). O sea que su
 * comportamiento real solo se puede comprobar en un teléfono, y este archivo se
 * escribió sabiéndolo — por eso todo lo que se puede decidir sin el servicio
 * (qué error significa qué, qué pasa con el audio) vive en
 * [AndroidDictadoAdapter], que sí tiene tests.
 *
 * ## En-dispositivo, no de red
 *
 * `createOnDeviceSpeechRecognizer` (API 33+). El de red fallaría justo donde el
 * cobrador lo necesita: parado en una puerta sin señal. Un teléfono anterior a
 * 13 contesta [disponible] `false` y el motor de Android no se ofrece — que es
 * la verdad, no una degradación silenciosa.
 *
 * ## El idioma: español, pero EL QUE ESTE TELÉFONO TENGA
 *
 * No se usa el idioma del sistema —un teléfono en inglés no cambia el idioma en
 * que la gente habla en la puerta— pero tampoco se fija `es-MX` a ciegas, que era
 * lo que hacía y estaba **roto**.
 *
 * Medido en el SM-A256E del dueño (Android 15, API 35):
 *
 * ```
 * es-MX  →  NO arranca — error 12 (ERROR_LANGUAGE_NOT_SUPPORTED)
 * es-US  →  SÍ arranca (escuchando)
 * ```
 *
 * El motor trae `[es-US]` instalado; `es-MX` no está ni entre los 30 que dice
 * soportar. O sea que el dictado **no habría funcionado ni una vez** en el
 * teléfono para el que se construyó.
 *
 * Se arregla reintentando: ante `ERROR_LANGUAGE_NOT_SUPPORTED` /
 * `ERROR_LANGUAGE_UNAVAILABLE` se le pregunta al motor qué español tiene
 * instalado, [IdiomaDelDictado] elige, y la sesión vuelve a arrancar con ése. El
 * idioma que sirvió queda cacheado, así que el rodeo se paga **una vez por
 * proceso** y el error llega antes de que nadie hable.
 *
 * **Por qué reintento y no preguntar primero:** `checkRecognitionSupport` es
 * asíncrono y `comenzar` contesta en el acto, cuando el dedo ya tocó el
 * micrófono. Preguntar antes obligaría a bloquear el hilo principal o a dejar la
 * primera sesión sin idioma conocido — que es el caso que se está arreglando.
 */
@Singleton
class SpeechRecognizerDeAndroid @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telemetry: Telemetry
) : ReconocedorDeAndroid {

    private var reconocedor: SpeechRecognizer? = null

    /**
     * El español que este teléfono sí acepta, una vez descubierto. `null` = todavía
     * no se sabe, y se pide [IdiomaDelDictado.PREFERIDO].
     */
    @Volatile
    private var idiomaQueSirve: String? = null

    override val disponible: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override fun comenzar(
        fuente: ParcelFileDescriptor?,
        alEvento: (EventoDelReconocedor) -> Unit
    ): Boolean {
        if (!disponible) return false
        val creado = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
            .getOrElse { error ->
                telemetry.error(
                    code = SpeechTelemetria.CODE_ANDROID_NO_ARRANCO,
                    message = "createOnDeviceSpeechRecognizer lanzo",
                    props = mapOf(SpeechTelemetria.PROP_EXCEPCION to error.javaClass.simpleName)
                )
                return false
            }
        reconocedor = creado
        creado.setRecognitionListener(escuchaQueReenvia(alEvento, fuente, yaReintento = false))
        creado.startListening(intencion(fuente, idiomaQueSirve ?: IdiomaDelDictado.PREFERIDO))
        return true
    }

    /**
     * El teléfono rechazó el idioma. Le pregunta cuál tiene instalado, lo cachea y
     * vuelve a arrancar la sesión con ése.
     *
     * [yaReintento] corta el rodeo en uno: si el segundo idioma también se rechaza,
     * el fallo sube tal cual en vez de quedarse dando vueltas.
     */
    private fun reintentarConElIdiomaDelTelefono(
        fuente: ParcelFileDescriptor?,
        alEvento: (EventoDelReconocedor) -> Unit,
        fallo: Int
    ) {
        val actual = reconocedor ?: return alEvento(EventoDelReconocedor.Fallo(fallo))
        actual.checkRecognitionSupport(
            intencion(fuente, idiomaQueSirve ?: IdiomaDelDictado.PREFERIDO),
            context.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    val elegido = IdiomaDelDictado.de(support.installedOnDeviceLanguages)
                    telemetry.error(
                        code = SpeechTelemetria.CODE_ANDROID_IDIOMA_CAMBIADO,
                        message = "el idioma pedido no esta en este telefono; se reintenta con el instalado",
                        props = mapOf(SpeechTelemetria.PROP_IDIOMA to elegido)
                    )
                    idiomaQueSirve = elegido
                    actual.setRecognitionListener(
                        escuchaQueReenvia(alEvento, fuente, yaReintento = true)
                    )
                    actual.startListening(intencion(fuente, elegido))
                }

                override fun onError(code: Int) {
                    // No se pudo ni preguntar: sube el fallo ORIGINAL, que es el que
                    // describe lo que le pasó al cobrador.
                    alEvento(EventoDelReconocedor.Fallo(fallo))
                }
            }
        )
    }

    override fun detener() {
        reconocedor?.stopListening()
    }

    override fun cancelar() {
        reconocedor?.cancel()
        reconocedor?.destroy()
        reconocedor = null
    }

    private fun intencion(fuente: ParcelFileDescriptor?, idioma: String) =
        android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, idioma)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // El audio que esta app ya está grabando. Sin esto el reconocedor
            // abre el micrófono por su cuenta y la grabación no existe — ver el
            // KDoc de `GrabadoraDeAudio.fuenteCompartida`.
            if (fuente != null) {
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, fuente)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, CODIFICACION_PCM_16)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, UN_CANAL)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, MUESTREO_HZ)
            }
        }

    private fun escuchaQueReenvia(
        alEvento: (EventoDelReconocedor) -> Unit,
        fuente: ParcelFileDescriptor?,
        yaReintento: Boolean
    ) = object : RecognitionListener {
        override fun onResults(results: Bundle?) =
            alEvento(EventoDelReconocedor.Final(primeroDe(results)))

        override fun onPartialResults(partialResults: Bundle?) =
            alEvento(EventoDelReconocedor.Parcial(primeroDe(partialResults)))

        override fun onError(error: Int) {
            if (!yaReintento && error in FALLOS_DE_IDIOMA) {
                reintentarConElIdiomaDelTelefono(fuente, alEvento, error)
            } else {
                alEvento(EventoDelReconocedor.Fallo(error))
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun primeroDe(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private companion object {
        /**
         * Los dos códigos con que el motor dice "ese idioma aquí no". Son los que
         * disparan el reintento; cualquier otro fallo sube tal cual.
         */
        val FALLOS_DE_IDIOMA = setOf(
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
        )

        /** `AudioFormat.ENCODING_PCM_16BIT`. El formato que escribe la grabadora. */
        const val CODIFICACION_PCM_16 = 2

        const val UN_CANAL = 1

        /** 16 kHz: lo que whisper espera y lo que el reconocedor prefiere. */
        const val MUESTREO_HZ = 16_000
    }
}
