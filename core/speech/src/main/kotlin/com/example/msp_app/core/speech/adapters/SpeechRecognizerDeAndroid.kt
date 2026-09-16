package com.example.msp_app.core.speech.adapters

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.msp_app.core.speech.application.SpeechTelemetria
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
 * ## El idioma se fija en español de México
 *
 * `EXTRA_LANGUAGE` con `es-MX` y no el idioma del sistema: un teléfono en
 * inglés no cambia el idioma en que la gente habla en la puerta.
 */
@Singleton
class SpeechRecognizerDeAndroid @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telemetry: Telemetry
) : ReconocedorDeAndroid {

    private var reconocedor: SpeechRecognizer? = null

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
        creado.setRecognitionListener(escuchaQueReenvia(alEvento))
        creado.startListening(intencion(fuente))
        return true
    }

    override fun detener() {
        reconocedor?.stopListening()
    }

    override fun cancelar() {
        reconocedor?.cancel()
        reconocedor?.destroy()
        reconocedor = null
    }

    private fun intencion(fuente: ParcelFileDescriptor?) =
        android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, IDIOMA)
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

    private fun escuchaQueReenvia(alEvento: (EventoDelReconocedor) -> Unit) =
        object : RecognitionListener {
            override fun onResults(results: Bundle?) =
                alEvento(EventoDelReconocedor.Final(primeroDe(results)))

            override fun onPartialResults(partialResults: Bundle?) =
                alEvento(EventoDelReconocedor.Parcial(primeroDe(partialResults)))

            override fun onError(error: Int) = alEvento(EventoDelReconocedor.Fallo(error))

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
        const val IDIOMA = "es-MX"

        /** `AudioFormat.ENCODING_PCM_16BIT`. El formato que escribe la grabadora. */
        const val CODIFICACION_PCM_16 = 2

        const val UN_CANAL = 1

        /** 16 kHz: lo que whisper espera y lo que el reconocedor prefiere. */
        const val MUESTREO_HZ = 16_000
    }
}
