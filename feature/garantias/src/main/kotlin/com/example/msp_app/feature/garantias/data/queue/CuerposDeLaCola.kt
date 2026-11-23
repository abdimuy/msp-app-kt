package com.example.msp_app.feature.garantias.data.queue

import com.example.msp_app.feature.garantias.data.http.CuerpoDeGarantia
import com.example.msp_app.feature.garantias.data.http.DatosDeEntrega
import com.example.msp_app.feature.garantias.domain.model.ComandoDeGarantia
import com.example.msp_app.feature.garantias.domain.model.CuerpoHttp
import com.example.msp_app.feature.garantias.domain.model.OperacionDeGarantia
import com.example.msp_app.feature.garantias.domain.model.RutasDeGarantia
import com.example.msp_app.feature.garantias.domain.port.ArchivosDeGarantiaPort
import javax.inject.Inject

/**
 * Arma, en cada intento, la ruta y el cuerpo que el comando ya tenía guardados.
 *
 * No decide qué mandar: eso lo hizo la captura al encolar. Sólo traduce la
 * plantilla de la ruta con los ids que el servidor devolvió y convierte el
 * texto de `CUERPO` en los mismos bytes de siempre. Para la entrega lee el PNG
 * de la firma del disco: el archivo no cambia entre reintentos, así que el
 * multipart tampoco (D3, mutantes A1, A2 y A9).
 */
internal class CuerposDeLaCola @Inject constructor(
    private val archivos: ArchivosDeGarantiaPort
) {

    /**
     * La ruta del comando con sus ids resueltos, o `null` si todavía falta
     * alguno. `abrir` no lleva marcadores y se devuelve tal cual.
     */
    fun rutaDe(comando: ComandoDeGarantia, referencias: Referencias): String? {
        if (comando.operacion == OperacionDeGarantia.ABRIR_FOLIO) return comando.ruta
        val folioId = referencias.folioIdDe(comando) ?: return null
        return try {
            RutasDeGarantia.resolver(
                plantilla = comando.ruta,
                folioId = folioId,
                articuloId = referencias.articuloIdDe(comando),
                eventoId = referencias.eventoIdDe(comando)
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * El cuerpo congelado, ya convertido a los bytes que viajan.
     *
     * `SinCuerpo` y `FirmaIlegible` son rechazos visibles: el dato no puede
     * aparecer solo y reintentar no lo arregla.
     */
    suspend fun cuerpoDe(comando: ComandoDeGarantia): CuerpoDelComando {
        val texto = comando.cuerpo ?: return CuerpoDelComando.SinCuerpo
        val tipo = comando.contentType ?: CuerpoDeGarantia.TIPO_JSON
        return if (tipo.startsWith(TIPO_MULTIPART)) {
            cuerpoDeEntrega(texto)
        } else {
            CuerpoDelComando.Listo(CuerpoHttp.deTexto(texto, tipo))
        }
    }

    private suspend fun cuerpoDeEntrega(texto: String): CuerpoDelComando {
        val datos = DatosDeEntrega.deJson(texto) ?: return CuerpoDelComando.SinCuerpo
        val firma = archivos.leer(datos.rutaDeLaFirma) ?: return CuerpoDelComando.FirmaIlegible
        return CuerpoDelComando.Listo(CuerpoDeGarantia.entrega(datos, firma))
    }

    private companion object {
        const val TIPO_MULTIPART = "multipart/form-data"
    }
}

/** El resultado de armar el cuerpo de un comando encolado. */
internal sealed interface CuerpoDelComando {

    /** Los bytes que viajan, con su `Content-Type`. */
    data class Listo(val cuerpo: CuerpoHttp) : CuerpoDelComando

    /** La fila no tiene un cuerpo con la forma esperada. */
    data object SinCuerpo : CuerpoDelComando

    /** La firma de la entrega ya no está en el teléfono. */
    data object FirmaIlegible : CuerpoDelComando
}
