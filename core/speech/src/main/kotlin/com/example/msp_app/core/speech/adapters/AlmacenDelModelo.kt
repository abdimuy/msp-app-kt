package com.example.msp_app.core.speech.adapters

import com.example.msp_app.core.speech.application.SpeechTelemetria
import com.example.msp_app.core.speech.domain.AvanceDeLaDescarga
import com.example.msp_app.core.speech.domain.ModeloDeDictado
import com.example.msp_app.core.telemetry.Telemetry
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * **Dónde vive el `.bin` y cómo se sabe que sirve.**
 *
 * Dos archivos, no uno, y ésa es la pieza que evita el peor defecto posible:
 *
 * - `modelo.parcial` — lo que se está bajando. Reanudable, y **nunca** se abre
 *   como modelo.
 * - `modelo.bin` — el que ya pasó el checksum. Existir **es** estar listo.
 *
 * Con un solo archivo, un `.bin` a medias sería indistinguible de uno completo
 * y whisper lo abriría para producir **basura plausible** — que nadie detecta
 * leyéndola, porque parece español. El renombre atómico al final de la
 * verificación es lo que hace que "el archivo existe" signifique "el archivo
 * sirve".
 *
 * ## `rutaDelModeloListo()` NO valida el checksum
 *
 * A propósito: se llama en cada arranque de dictado, y volver a leer 75 MB para
 * calcular un SHA-256 antes de cada nota tendría al cobrador esperando. El
 * checksum se comprueba **una vez**, al terminar de bajar, y el renombre es la
 * prueba que queda.
 */
class AlmacenDelModelo(
    private val carpeta: File,
    private val telemetry: Telemetry
) {

    /** El archivo verificado. Solo existe si pasó el checksum. */
    private val listo: File get() = File(carpeta, "modelo.bin")

    /** Lo que se está bajando. Puede existir a medias, y eso es correcto. */
    val parcial: File get() = File(carpeta, "modelo.parcial")

    /** La ruta del modelo utilizable, o `null`. Es la pregunta que hace el puerto. */
    fun rutaDelModeloListo(): String? = listo.takeIf { it.isFile && it.length() > 0L }?.absolutePath

    /** Cuántos bytes lleva bajados el parcial. Cero si no hay nada. */
    fun bytesBajados(): Long = if (parcial.isFile) parcial.length() else 0L

    /** El avance para pintar, sin inventar el total: lo pone [modelo]. */
    fun avance(modelo: ModeloDeDictado): AvanceDeLaDescarga =
        AvanceDeLaDescarga(bytesBajados(), modelo.tamanoBytes)

    /**
     * Comprueba el parcial y lo promueve a definitivo.
     *
     * @return `true` si quedó listo. `false` borra el parcial: un `.bin` con el
     *   checksum mal no se puede arreglar reanudando, solo bajando de cero.
     */
    fun verificarYPromover(modelo: ModeloDeDictado): Boolean {
        val real = runCatching { sha256De(parcial) }.getOrElse { error ->
            telemetry.error(
                code = SpeechTelemetria.CODE_MODELO_CORRUPTO,
                message = "no se pudo leer el parcial para verificarlo",
                props = mapOf(SpeechTelemetria.PROP_EXCEPCION to error.javaClass.simpleName)
            )
            return false
        }
        if (!real.equals(modelo.sha256, ignoreCase = true)) {
            telemetry.error(
                code = SpeechTelemetria.CODE_MODELO_CORRUPTO,
                message = "el sha256 del modelo bajado no coincide; se descarta",
                props = emptyMap()
            )
            parcial.delete()
            return false
        }
        listo.delete()
        // `renameTo` dentro de la misma carpeta es atómico en cualquier sistema
        // de archivos de Android: no existe un instante en el que `modelo.bin`
        // esté a medias.
        if (!parcial.renameTo(listo)) {
            telemetry.error(
                code = SpeechTelemetria.CODE_MODELO_CORRUPTO,
                message = "el parcial verificado no se pudo renombrar a definitivo",
                props = emptyMap()
            )
            return false
        }
        return true
    }

    /** Borra las dos mitades. Lo pide el cobrador para recuperar los megas. */
    fun borrarTodo() {
        val borrados = listOf(listo, parcial).filter { it.exists() }.map { it.delete() }
        if (borrados.any { !it }) {
            telemetry.error(
                code = SpeechTelemetria.CODE_MODELO_NO_SE_BORRO,
                message = "el modelo no se pudo borrar; los megas siguen ocupados",
                props = emptyMap()
            )
        }
    }

    /** Crea la carpeta si falta. Idempotente. */
    fun prepararCarpeta() {
        if (!carpeta.isDirectory && !carpeta.mkdirs()) {
            telemetry.error(
                code = SpeechTelemetria.CODE_DESCARGA_CORTADA,
                message = "no se pudo crear la carpeta del modelo",
                props = emptyMap()
            )
        }
    }

    private fun sha256De(archivo: File): String {
        if (!archivo.isFile) throw IOException("no hay parcial que verificar")
        val digest = MessageDigest.getInstance("SHA-256")
        archivo.inputStream().use { entrada ->
            val buffer = ByteArray(BUFFER)
            var leidos = entrada.read(buffer)
            while (leidos != -1) {
                digest.update(buffer, 0, leidos)
                leidos = entrada.read(buffer)
            }
        }
        return digest.digest().joinToString("") { byte ->
            // `and 0xFF` para que un byte negativo no salga con signo; el
            // `+ 0x100` fuerza los dos dígitos y luego se recorta el "1".
            ((byte.toInt() and MASCARA) + RELLENO).toString(BASE).substring(1)
        }
    }

    private companion object {
        const val BUFFER = 64 * 1024

        @Suppress("MagicNumber") // aritmética del hexadecimal, no cifras de negocio.
        const val MASCARA = 0xFF

        @Suppress("MagicNumber")
        const val RELLENO = 0x100

        @Suppress("MagicNumber")
        const val BASE = 16
    }
}
