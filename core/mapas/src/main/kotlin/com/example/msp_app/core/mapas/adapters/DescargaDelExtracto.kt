package com.example.msp_app.core.mapas.adapters

import com.example.msp_app.core.mapas.application.MapasTelemetria
import com.example.msp_app.core.mapas.domain.AvanceDeLaDescarga
import com.example.msp_app.core.mapas.domain.ExtractoDeMapa
import com.example.msp_app.core.telemetry.Telemetry
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request

/** Cómo terminó un intento de bajar el extracto. */
sealed interface DesenlaceDeLaDescarga {

    /** Bajó entero y la cabecera verifica. A partir de acá el suelo dibuja calles. */
    data object Listo : DesenlaceDeLaDescarga

    /**
     * Se cortó. **Lo bajado se conserva** y el siguiente intento reanuda con
     * `Range`. Decirle al cobrador que no perdió los 12 MB es lo que hace que
     * vuelva a intentar.
     */
    data class Cortada(val motivo: String) : DesenlaceDeLaDescarga

    /**
     * Bajó y el archivo no es un `.pmtiles` completo. El parcial **ya se borró**:
     * no hay nada que reanudar, se baja de cero.
     */
    data object Invalida : DesenlaceDeLaDescarga
}

/**
 * **Baja el `.pmtiles`, reanudable y verificado.** Misma receta que
 * `DescargaDelModelo` de `:core:speech`, que a su vez copia la de `ApkDownloader`
 * de `:core:appgate`, con los tres caminos ya pagados:
 *
 * - `206` → se **agrega** a lo que ya había;
 * - `200` (el servidor ignoró el `Range`) → se **trunca** y se baja de cero;
 * - ya está completo → ni se pide, se pasa a verificar.
 *
 * No se importa ninguno de aquellos: están atados a su propio paquete y a su
 * propia carpeta, y acoplar `:core:mapas` a `:core:speech` ataría el mapa al
 * dictado.
 *
 * **Esta clase no decide CUÁNDO se baja.** Eso es política de red y vive en
 * [PlanificadorDeLaDescarga], que es quien cumple el "solo con wifi".
 */
class DescargaDelExtracto(
    private val llamadas: Call.Factory,
    private val almacen: AlmacenDelExtracto,
    private val telemetry: Telemetry,
    private val dispatcher: CoroutineDispatcher
) {

    suspend fun bajar(
        extracto: ExtractoDeMapa,
        alAvanzar: (AvanceDeLaDescarga) -> Unit = {}
    ): DesenlaceDeLaDescarga = withContext(dispatcher) {
        try {
            intentar(extracto, alAvanzar)
        } catch (e: IOException) {
            telemetry.error(
                code = MapasTelemetria.CODE_DESCARGA_CORTADA,
                message = "la descarga del extracto se corto; lo bajado se conserva",
                props = mapOf(MapasTelemetria.PROP_EXCEPCION to e.javaClass.simpleName)
            )
            DesenlaceDeLaDescarga.Cortada(e.javaClass.simpleName)
        }
    }

    private fun intentar(
        extracto: ExtractoDeMapa,
        alAvanzar: (AvanceDeLaDescarga) -> Unit
    ): DesenlaceDeLaDescarga {
        almacen.prepararCarpeta()
        val destino = almacen.parcial
        val yaTengo = almacen.bytesBajados()

        // El tamaño anunciado es orientativo (ver `ExtractoDeMapa`): si el
        // parcial ya lo alcanzó, se pasa a verificar y es la CABECERA la que
        // decide. Así un tamaño desactualizado no puede rechazar un archivo
        // bueno ni aceptar uno malo.
        if (yaTengo >= extracto.tamanoBytes) {
            alAvanzar(AvanceDeLaDescarga(extracto.tamanoBytes, extracto.tamanoBytes))
            return verificar()
        }

        llamadas.newCall(peticionConRango(extracto.url, yaTengo)).execute().use { respuesta ->
            if (!respuesta.isSuccessful) {
                telemetry.error(
                    code = MapasTelemetria.CODE_DESCARGA_CORTADA,
                    message = "el servidor del extracto no contesto 2xx",
                    props = mapOf(MapasTelemetria.PROP_HTTP to respuesta.code.toString())
                )
                return DesenlaceDeLaDescarga.Cortada("http ${respuesta.code}")
            }
            val cuerpo = respuesta.body ?: return DesenlaceDeLaDescarga.Cortada("sin cuerpo")
            val reanudado = respuesta.code == CONTENIDO_PARCIAL
            var escritos = if (reanudado) yaTengo else 0L
            alAvanzar(AvanceDeLaDescarga(escritos, extracto.tamanoBytes))
            // `append = false` TRUNCA, que es justo lo que se quiere cuando el
            // servidor ignoró el `Range` y mandó el archivo entero desde cero.
            FileOutputStream(destino, reanudado).use { salida ->
                val entrada = cuerpo.byteStream()
                val buffer = ByteArray(BUFFER)
                var leidos = entrada.read(buffer)
                while (leidos != -1) {
                    salida.write(buffer, 0, leidos)
                    escritos += leidos
                    alAvanzar(AvanceDeLaDescarga(escritos, extracto.tamanoBytes))
                    leidos = entrada.read(buffer)
                }
            }
        }
        return verificar()
    }

    private fun verificar(): DesenlaceDeLaDescarga = if (almacen.verificarYPromover()) {
        DesenlaceDeLaDescarga.Listo
    } else {
        DesenlaceDeLaDescarga.Invalida
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
