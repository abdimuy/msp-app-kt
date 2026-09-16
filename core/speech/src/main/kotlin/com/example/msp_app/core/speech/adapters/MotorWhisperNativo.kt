package com.example.msp_app.core.speech.adapters

/**
 * **whisper.cpp, visto desde Kotlin.**
 *
 * Una costura de dos miembros, y los dos existen por el mismo motivo: la JVM no
 * puede cargar un `.so` de Android, así que todo lo que cuelgue del binario
 * nativo es **incomprobable** en Robolectric. Lo que sí se prueba es lo que pasa
 * **alrededor** del binario, y eso es casi todo lo que puede salir mal: que la
 * librería no esté, que el modelo no esté, que la transcripción falle, y que en
 * los tres casos el dictado siga funcionando con el otro motor.
 *
 * ## Qué NO cubre ningún test de este repo, dicho en voz alta
 *
 * - que `System.loadLibrary` encuentre la librería en un teléfono real;
 * - que la firma JNI coincida con la del `.so`;
 * - que la transcripción de whisper sea correcta.
 *
 * Las tres necesitan un dispositivo, y **ninguna compuerta del repo corre
 * `androidTest`** (`CLAUDE.md` §2: las tareas `connected*` están excluidas). No
 * se escribe un test que finja ejercitarlas — un verde que no prueba nada es
 * peor que un hueco declarado.
 */
interface MotorWhisperNativo {

    /**
     * ¿La librería nativa está cargada?
     *
     * `false` no es un error: es el estado normal de cualquier build de este
     * repo hoy, y el puerto **degrada** al reconocedor de Android sin avisar a
     * nadie. Ésa es la condición innegociable del JNI escrita como un `Boolean`.
     */
    val cargada: Boolean

    /**
     * Transcribe [rutaAudio] con el modelo de [rutaModelo], **por lotes**: el
     * audio ya está completo en disco.
     *
     * Por lotes y no en vivo porque es el único modo en que un `tiny` int8 rinde
     * en gama media — transcribir mientras se habla en un SM-A256E deja al
     * teléfono sin CPU para pintar el propio campo.
     */
    fun transcribir(rutaModelo: String, rutaAudio: String): Result<String>
}

/**
 * **El motor nativo que hoy nunca está, y por qué eso está bien.**
 *
 * Esta implementación intenta cargar `libwhisper_msp.so` y, cuando no existe,
 * se declara ausente. Hoy no existe en ningún build de este repo: **no hay
 * `externalNativeBuild`, ni CMake, ni fuentes de whisper.cpp vendorizadas**, y
 * esa fue una decisión, no un olvido. Las razones están en el reporte de la
 * tarea; la corta es que meter el toolchain nativo haría que la compuerta
 * dependa del NDK sin que ninguna compuerta pueda ejecutar el resultado.
 *
 * Lo que esta clase garantiza —y lo que su test prueba— es que el día que el
 * `.so` exista, **nada más tenga que cambiar**: el puerto ya elige, ya degrada
 * y ya reporta.
 *
 * `UnsatisfiedLinkError` es un `Error`, no una `Exception`, y por eso se
 * atrapa explícitamente: un `catch (e: Exception)` lo dejaría pasar y tumbaría
 * la app al construir el grafo de Hilt. Es exactamente el fallo que este
 * `try/catch` existe para impedir.
 */
class WhisperJni(
    private val cargarLibreria: (String) -> Unit = { System.loadLibrary(it) }
) : MotorWhisperNativo {

    override val cargada: Boolean by lazy {
        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        try {
            cargarLibreria(LIBRERIA)
            true
        } catch (e: UnsatisfiedLinkError) {
            // NO se emite telemetría acá, y es deliberado: hoy la ausencia es
            // el estado normal de toda la flota, así que reportarla sería un
            // evento por arranque por teléfono diciendo lo que ya se sabe.
            // Quien SÍ reporta es `WhisperDictadoAdapter`, una sola vez y solo
            // si alguien intentó dictar con whisper creyendo que estaba.
            false
        } catch (e: SecurityException) {
            false
        }
    }

    override fun transcribir(rutaModelo: String, rutaAudio: String): Result<String> {
        if (!cargada) {
            return Result.failure(IllegalStateException("libreria nativa ausente"))
        }
        return runCatching { transcribirNativo(rutaModelo, rutaAudio) }
    }

    /**
     * La función JNI. `external` sin `.so` detrás compila y enlaza tarde: la
     * `UnsatisfiedLinkError` llegaría **al llamarla**, no al cargar la clase —
     * y por eso [cargada] se consulta primero y esta línea nunca se alcanza hoy.
     */
    private external fun transcribirNativo(rutaModelo: String, rutaAudio: String): String

    private companion object {
        const val LIBRERIA = "whisper_msp"
    }
}
