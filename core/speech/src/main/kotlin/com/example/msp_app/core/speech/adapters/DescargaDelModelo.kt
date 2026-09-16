package com.example.msp_app.core.speech.adapters

import com.example.msp_app.core.speech.application.SpeechTelemetria
import com.example.msp_app.core.speech.domain.AvanceDeLaDescarga
import com.example.msp_app.core.speech.domain.ModeloDeDictado
import com.example.msp_app.core.telemetry.Telemetry
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request

/** Cómo terminó un intento de bajar el modelo. */
sealed interface DesenlaceDeLaDescarga {

    /** Bajó entero y el checksum coincide. A partir de acá el dictado es whisper. */
    data object Listo : DesenlaceDeLaDescarga

    /**
     * Se cortó. **Lo bajado se conserva** y el siguiente intento reanuda con
     * `Range`. Decirle al cobrador que no perdió los 48 MB es lo que hace que
     * vuelva a intentar.
     */
    data class Cortada(val motivo: String) : DesenlaceDeLaDescarga

    /**
     * Bajó entero y el SHA-256 no coincide. El parcial **ya se borró**: no hay
     * nada que reanudar, se baja de cero.
     */
    data object Corrupta : DesenlaceDeLaDescarga
}

/**
 * **Baja el `.bin`, reanudable y verificado.** Copia deliberada de la receta de
 * `ApkDownloader` de `:core:appgate`, que ya pagó los tres caminos:
 *
 * - `206` → se **agrega** a lo que ya había;
 * - `200` (el servidor ignoró el `Range`) → se **trunca** y se baja de cero;
 * - ya está completo → ni se pide, se pasa a verificar.
 *
 * No se importa aquél y no es por descuido: `ApkDownloader` está atado a
 * `UpdatePackage` y a la carpeta del APK, y hacer que `:core:speech` dependa de
 * `:core:appgate` ataría la descarga del modelo a la compuerta de versión de la
 * app — dos cosas que no tienen por qué moverse juntas.
 *
 * **Esta clase no decide CUÁNDO se baja.** Eso es política de red y vive en
 * [PlanificadorDeLaDescarga], que es quien cumple el "solo con wifi".
 */
class DescargaDelModelo(
    private val llamadas: Call.Factory,
    private val almacen: AlmacenDelModelo,
    private val telemetry: Telemetry,
    private val dispatcher: CoroutineDispatcher
) {

    suspend fun bajar(
        modelo: ModeloDeDictado,
        alAvanzar: (AvanceDeLaDescarga) -> Unit = {}
    ): DesenlaceDeLaDescarga = withContext(dispatcher) {
        try {
            intentar(modelo, alAvanzar)
        } catch (e: IOException) {
            telemetry.error(
                code = SpeechTelemetria.CODE_DESCARGA_CORTADA,
                message = "la descarga del modelo se corto; lo bajado se conserva",
                props = mapOf(SpeechTelemetria.PROP_EXCEPCION to e.javaClass.simpleName)
            )
            DesenlaceDeLaDescarga.Cortada(e.javaClass.simpleName)
        }
    }

    private fun intentar(
        modelo: ModeloDeDictado,
        alAvanzar: (AvanceDeLaDescarga) -> Unit
    ): DesenlaceDeLaDescarga {
        almacen.prepararCarpeta()
        val destino = almacen.parcial
        val yaTengo = almacen.bytesBajados()

        if (yaTengo >= modelo.tamanoBytes) {
            alAvanzar(AvanceDeLaDescarga(modelo.tamanoBytes, modelo.tamanoBytes))
            return verificar(modelo)
        }

        llamadas.newCall(peticionConRango(modelo.url, yaTengo)).execute().use { respuesta ->
            if (!respuesta.isSuccessful) {
                telemetry.error(
                    code = SpeechTelemetria.CODE_DESCARGA_CORTADA,
                    message = "el servidor del modelo no contesto 2xx",
                    props = mapOf(SpeechTelemetria.PROP_HTTP to respuesta.code.toString())
                )
                return DesenlaceDeLaDescarga.Cortada("http ${respuesta.code}")
            }
            val cuerpo = respuesta.body ?: return DesenlaceDeLaDescarga.Cortada("sin cuerpo")
            val reanudado = respuesta.code == CONTENIDO_PARCIAL
            val desde = if (reanudado) yaTengo else 0L
            var escritos = desde
            alAvanzar(AvanceDeLaDescarga(escritos, modelo.tamanoBytes))
            // `append = false` TRUNCA, que es justo lo que se quiere cuando el
            // servidor ignoró el `Range` y mandó el archivo entero desde cero.
            FileOutputStream(destino, reanudado).use { salida ->
                val entrada = cuerpo.byteStream()
                val buffer = ByteArray(BUFFER)
                var leidos = entrada.read(buffer)
                while (leidos != -1) {
                    salida.write(buffer, 0, leidos)
                    escritos += leidos
                    alAvanzar(AvanceDeLaDescarga(escritos, modelo.tamanoBytes))
                    leidos = entrada.read(buffer)
                }
            }
        }
        return verificar(modelo)
    }

    private fun verificar(modelo: ModeloDeDictado): DesenlaceDeLaDescarga =
        if (almacen.verificarYPromover(modelo)) {
            DesenlaceDeLaDescarga.Listo
        } else {
            DesenlaceDeLaDescarga.Corrupta
        }

    private fun peticionConRango(url: String, yaTengo: Long): Request {
        val constructor = Request.Builder().url(url)
        if (yaTengo > 0L) constructor.header("Range", "bytes=$yaTengo-")
        return constructor.build()
    }

    private companion object {
        const val CONTENIDO_PARCIAL = 206
        const val BUFFER = 64 * 1024
    }
}
