package com.example.msp_app.feature.garantias.data.http

import com.example.msp_app.feature.garantias.domain.ResultadoDeLaLlamada
import com.example.msp_app.feature.garantias.domain.model.CuerpoHttp
import com.example.msp_app.feature.garantias.domain.model.DetalleDeGarantia
import com.example.msp_app.feature.garantias.domain.model.RutasDeGarantia
import com.example.msp_app.feature.garantias.domain.port.GarantiasRemotasPort
import java.io.IOException
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * El API de garantías v2 sobre Retrofit.
 *
 * ## Qué NO hace
 *
 * No arma cuerpos ni claves. Recibe el comando ya serializado por la cola y lo
 * transporta: los bytes y la clave se congelaron al encolar, y este adaptador
 * los manda idénticos en cada reintento (D3, mutantes A1 y A2).
 *
 * ## Qué sí decide
 *
 * Traduce la respuesta HTTP a [ResultadoDeLaLlamada] **sin clasificarla**: lee
 * el status y el código de negocio con [CodigosDeErrorHuma] y deja que
 * `ClasificadorDeEnvio` decida si se reintenta o se rechaza (D6). Un fallo de
 * red se traduce a `FalloDeRed`, nunca a una excepción que tumbe al trabajador.
 *
 * Vive en el módulo y no en `:app` porque la interfaz y los DTO son parte del
 * contrato de la captura; `:app` sólo le da el `Retrofit` ya construido con la
 * baseURL v2 y el token de la sesión.
 */
class GarantiasRemotasAdapter @Inject constructor(
    private val api: V2GarantiasApi
) : GarantiasRemotasPort {

    override suspend fun enviar(
        clave: String,
        metodo: String,
        ruta: String,
        cuerpo: CuerpoHttp
    ): ResultadoDeLaLlamada {
        if (!metodo.equals(METODO_POST, ignoreCase = true)) {
            return ResultadoDeLaLlamada.Respuesta(
                status = METODO_NO_SOPORTADO,
                detalle = "Método no soportado"
            )
        }
        return llamar(clave, ruta, cuerpo)
    }

    override suspend fun subirImagen(
        clave: String,
        ruta: String,
        cuerpo: CuerpoHttp
    ): ResultadoDeLaLlamada = llamar(clave, ruta, cuerpo)

    override suspend fun detalle(folioId: String): DetalleDeGarantia =
        api.detalle(RutasDeGarantia.resolver(RutasDeGarantia.DETALLE, folioId)).toDomain()

    /**
     * Una llamada de escritura, con su lectura de status y cuerpo.
     *
     * El `catch` es de `IOException` y sólo de eso: un fallo de parseo o de
     * contrato no es "sin señal" y no debe reintentarse como si lo fuera.
     */
    private suspend fun llamar(
        clave: String,
        ruta: String,
        cuerpo: CuerpoHttp
    ): ResultadoDeLaLlamada = try {
        val respuesta = api.enviar(clave, ruta, cuerpo.aCuerpoDeOkHttp())
        CodigosDeErrorHuma.respuesta(respuesta.code(), respuesta.body()?.string())
    } catch (_: IOException) {
        ResultadoDeLaLlamada.FalloDeRed(SIN_RED)
    }

    /** Los mismos bytes con el tipo de contenido que viajó al encolar. */
    private fun CuerpoHttp.aCuerpoDeOkHttp() =
        bytes.toRequestBody(tipoDeContenido.toMediaTypeOrNull())

    private companion object {
        const val METODO_POST = "POST"
        const val METODO_NO_SOPORTADO = 405
        const val SIN_RED = "Sin conexión"
    }
}
