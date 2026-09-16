package com.example.msp_app.e2e

import android.content.Context
import android.os.Build
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **¿Este teléfono sabe dictar en español SIN señal, sin whisper?**
 *
 * No es una compuerta: es una **medición**. Existe para contestar con un hecho una
 * pregunta que se estaba contestando con un folleto.
 *
 * ## La pregunta, y por qué importa
 *
 * El mock vende el modelo whisper descargable con *"funciona sin señal para
 * siempre"*. Pero `SpeechRecognizer.createOnDeviceSpeechRecognizer()` —el que la
 * app ya usa, gratis y sin descargar nada— **también** es en el dispositivo, o sea
 * que también funciona sin señal. Si eso alcanza en ESTE teléfono, whisper cuesta
 * ~200 mil líneas de C++ de terceros en un repo público, una compuerta que exige
 * NDK y más peso de APK, a cambio de poco.
 *
 * La ventaja real de whisper es más estrecha y depende del aparato: el
 * reconocedor de Android necesita que el fabricante haya instalado el modelo de
 * español, y whisper no depende de nadie. **Cuál de los dos casos es el del
 * SM-A256E es un hecho medible**, y esto lo mide.
 *
 * ## Qué contesta, exactamente
 *
 * 1. ¿Hay algún reconocedor?
 * 2. ¿Hay uno EN EL DISPOSITIVO? (API 31+)
 * 3. ¿Qué idiomas tiene **instalados** en el dispositivo, cuáles **soporta** y
 *    cuáles están **pendientes de descarga**? (API 33+, `checkRecognitionSupport`)
 *
 * El 3 es el que decide: "soporta español" y "tiene español instalado" son cosas
 * distintas, y solo la segunda sirve parado en una puerta sin señal.
 *
 * ## Cómo se corre
 *
 * ```
 * export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
 * ./gradlew :app:connectedDevlocalDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.example.msp_app.e2e.QueSabeDictarEsteTelefonoTest
 * ```
 *
 * **Ninguna compuerta lo corre** —`build.gradle.kts` excluye las tareas
 * `connected*`— y está bien que así sea: mide el aparato que tiene enfrente, así
 * que su resultado es distinto en cada teléfono y no puede ser un criterio de
 * aceptación.
 *
 * **No toca la app de producción**: no abre `MainActivity`, no lee la sesión, no
 * escribe un solo dato. Solo pregunta por las capacidades del sistema.
 */
@RunWith(AndroidJUnit4::class)
class QueSabeDictarEsteTelefonoTest {

    /**
     * `checkRecognitionSupport` consulta al servicio de reconocimiento, y en
     * varios OEM eso exige el permiso aunque no se grabe nada.
     */
    @get:Rule
    val permisoDeMicrofono: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    // Sin espacios ni acentos: en `androidTest` el nombre viaja a DEX, y DEX < 040
    // rechaza espacios en un SimpleName. Los backticks solo sirven en la JVM.
    @Test
    fun queSabeDictarEsteTelefono() {
        val reporte = StringBuilder()
        reporte.line("════════════════════════════════════════════════")
        reporte.line("  DICTADO EN VIDRIO — ${Build.MANUFACTURER} ${Build.MODEL}")
        reporte.line("  Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        reporte.line("════════════════════════════════════════════════")

        val hayAlguno = SpeechRecognizer.isRecognitionAvailable(context)
        reporte.line("¿Hay algún reconocedor?           $hayAlguno")

        val hayEnElDispositivo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            } else {
                false
            }
        reporte.line("¿Hay uno EN EL DISPOSITIVO?       $hayEnElDispositivo")

        val idiomas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Los DOS: el que la app pide hoy (`SpeechRecognizerDeAndroid.IDIOMA`) y el
            // que el teléfono trae. Si el primero falla y el segundo no, el dictado está
            // roto en este aparato por pedir un idioma que no tiene.
            reporte.line("--- lo que la app pide hoy ---")
            val conMx = idiomasDelDispositivo(reporte, ESPANOL_MX)
            reporte.line("--- lo que el teléfono trae ---")
            val conUs = idiomasDelDispositivo(reporte, ESPANOL_US)
            conMx ?: conUs
        } else {
            reporte.line("checkRecognitionSupport exige API 33; este aparato no llega.")
            null
        }

        // La prueba que SÍ distingue. `checkRecognitionSupport` contesta lo mismo
        // para `es-MX` y para `es-US` —comprobado—, así que no puede decir si el
        // idioma que la app pide sirve en este aparato. Abrir la sesión sí: o
        // arranca a escuchar, o contesta ERROR_LANGUAGE_*. No hay que hablarle.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            reporte.line("--- ¿arranca la sesión? ---")
            reporte.line("es-MX (lo que se pedía antes)  →  " + arranca(ESPANOL_MX))
            reporte.line("es-US (lo que el teléfono trae) →  " + arranca(ESPANOL_US))

            // El arreglo: qué elige `IdiomaDelDictado` con lo que este aparato
            // reporta, y si ESE idioma sí arranca. Es la prueba de que el
            // reintento del adaptador aterriza en algo que funciona.
            val instalados = idiomas?.installedOnDeviceLanguages.orEmpty()
            val elegido = com.example.msp_app.core.speech.domain.IdiomaDelDictado.de(instalados)
            reporte.line("--- el arreglo ---")
            reporte.line("IdiomaDelDictado.de($instalados) = $elegido")
            reporte.line("$elegido  →  " + arranca(elegido))
        }

        reporte.line("────────────────────────────────────────────────")
        reporte.line(veredicto(hayEnElDispositivo, idiomas))
        reporte.line("════════════════════════════════════════════════")

        // Por `Log.i` y renglón por renglón: `println` desde instrumentación no
        // aparece ni en el XML de resultados ni en logcat (comprobado), y un
        // `Log` de varias líneas se trunca. Con el tag se pesca con un solo grep.
        reporte.toString().trim().lines().forEach { android.util.Log.i(TAG, it) }

        // Control positivo: lo único que se afirma es que la MEDICIÓN ocurrió.
        // Que el resultado sea sí o no son los dos datos válidos que se vino a
        // buscar; afirmar uno convertiría una medición en un prejuicio.
        assertTrue(
            "no se pudo interrogar al reconocedor: la medición no dice nada",
            reporte.contains("¿Hay algún reconocedor?")
        )
    }

    /**
     * Los tres conjuntos que `RecognitionSupport` distingue, y que **no son lo
     * mismo**: soportado (podría bajarse), pendiente (se está bajando) e
     * **instalado** (sirve hoy, sin señal). Solo el tercero contesta la pregunta.
     */
    private fun idiomasDelDispositivo(reporte: StringBuilder, idioma: String): RecognitionSupport? {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            reporte.line("Sin reconocedor en el dispositivo: no hay idiomas que listar.")
            return null
        }
        var resultado: RecognitionSupport? = null
        var error: Int? = null
        val listo = CountDownLatch(1)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val reconocedor = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            reconocedor.checkRecognitionSupport(
                android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, idioma)
                },
                context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        resultado = support
                        listo.countDown()
                    }

                    override fun onError(code: Int) {
                        error = code
                        listo.countDown()
                    }
                }
            )
        }

        if (!listo.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS)) {
            reporte.line("[$idioma] el reconocedor no contestó en $ESPERA_SEGUNDOS s.")
            return null
        }
        error?.let {
            reporte.line("[$idioma] checkRecognitionSupport FALLÓ con código $it.")
            return null
        }
        val support = resultado ?: return null
        reporte.line("[$idioma] instalados:   ${support.installedOnDeviceLanguages}")
        reporte.line("[$idioma] soportados:   ${support.supportedOnDeviceLanguages.size} idiomas")
        reporte.line("[$idioma] pendientes:   ${support.pendingOnDeviceLanguages}")
        return support
    }

    /**
     * Abre una sesión de dictado en [idioma] y contesta qué pasó, **sin hablarle**.
     *
     * `onReadyForSpeech` significa que el motor aceptó el idioma y ya está
     * escuchando: eso es lo que el cobrador necesita. `ERROR_LANGUAGE_NOT_SUPPORTED`
     * (12) o `ERROR_LANGUAGE_UNAVAILABLE` (13) significan que pedir ese idioma en
     * este teléfono no dicta nada.
     *
     * La sesión se cancela enseguida: no graba, no transcribe, no deja rastro.
     */
    private fun arranca(idioma: String): String {
        var veredicto: String? = null
        val listo = CountDownLatch(1)
        var reconocedor: SpeechRecognizer? = null

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            reconocedor = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
                setRecognitionListener(object : android.speech.RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {
                        if (veredicto == null) veredicto = "SÍ arranca (escuchando)"
                        listo.countDown()
                    }

                    override fun onError(error: Int) {
                        if (veredicto == null) veredicto = "NO arranca — error $error" + nombreDelError(error)
                        listo.countDown()
                    }

                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onResults(results: android.os.Bundle?) = Unit
                    override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
                })
                startListening(
                    android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, idioma)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    }
                )
            }
        }

        val contesto = listo.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            reconocedor?.cancel()
            reconocedor?.destroy()
        }
        return if (contesto) veredicto.orEmpty() else "no contestó en $ESPERA_SEGUNDOS s"
    }

    private fun nombreDelError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> " (IDIOMA NO SOPORTADO)"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> " (IDIOMA NO DISPONIBLE)"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> " (sin permiso)"
        SpeechRecognizer.ERROR_NO_MATCH -> " (no entendió — pero arrancó)"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> " (silencio — pero arrancó)"
        else -> ""
    }

    /**
     * La lectura, dicha en una frase, para que quien corra esto no tenga que
     * interpretar tres listas.
     */
    private fun veredicto(hayEnElDispositivo: Boolean, support: RecognitionSupport?): String {
        if (!hayEnElDispositivo) {
            return "VEREDICTO: este teléfono NO dicta en el dispositivo. " +
                "Sin whisper no hay dictado sin señal."
        }
        val instalados = support?.installedOnDeviceLanguages.orEmpty()
        val conEspanol = instalados.any { it.startsWith("es", ignoreCase = true) }
        return if (conEspanol) {
            "VEREDICTO: dicta en español SIN señal y SIN whisper. " +
                "Whisper solo compraría precisión en nombres locales."
        } else {
            "VEREDICTO: hay reconocedor en el dispositivo pero SIN español instalado " +
                "($instalados). Whisper sí compra la función, no solo precisión."
        }
    }

    private fun StringBuilder.line(texto: String) {
        append(texto).append('\n')
    }

    private companion object {
        const val TAG = "DICTADO_EN_VIDRIO"
        const val ESPANOL_MX = "es-MX"
        const val ESPANOL_US = "es-US"
        const val ESPERA_SEGUNDOS = 20L
    }
}
