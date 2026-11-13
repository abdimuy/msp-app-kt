package com.example.msp_app.feature.garantias.data.files

import com.example.msp_app.feature.garantias.domain.ReglasDeEntrega
import com.example.msp_app.feature.garantias.domain.model.FirmaDeConformidad
import com.example.msp_app.feature.garantias.domain.port.FirmaDeConformidadPort
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * Escribe la firma del cliente en `filesDir/garantias/firmas/`.
 *
 * El PNG se escribe **antes** de encolar la entrega: el comando guarda su ruta,
 * y si el archivo no existe la entrega no se puede armar. Por eso [guardarPng]
 * devuelve `null` cuando no pudo escribir, en vez de devolver una ruta que
 * apunta a la nada.
 *
 * La regla del megabyte es la misma del servidor ([ReglasDeEntrega]): si el
 * teléfono aceptara más, el comando se encolaría para morir en 422.
 */
internal class FirmaDeConformidadAdapter @Inject constructor(
    private val directorio: DirectorioDeGarantias
) : FirmaDeConformidadPort {

    override suspend fun guardarPng(bytes: ByteArray): FirmaDeConformidad? {
        if (!ReglasDeEntrega.firmaValida(bytes)) return null
        val destino = directorio.nuevoArchivo(
            DirectorioDeGarantias.FIRMAS,
            "${UUID.randomUUID()}.png"
        )
        return try {
            destino.writeBytes(bytes)
            FirmaDeConformidad(
                rutaArchivo = directorio.relativa(destino),
                tamanoBytes = bytes.size
            )
        } catch (_: IOException) {
            destino.delete()
            null
        }
    }

    override suspend fun borrar(ruta: String) {
        directorio.archivo(ruta).delete()
    }
}
