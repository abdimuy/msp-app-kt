package com.example.msp_app.feature.garantias.data.files

import com.example.msp_app.feature.garantias.domain.port.ArchivosDeGarantiaPort
import java.io.IOException
import javax.inject.Inject

/**
 * Lee y borra las fotos de la captura desde `filesDir/garantias/`.
 *
 * [leer] devuelve `null` cuando el archivo ya no está o no se puede leer: la
 * cola lo trata como un rechazo visible y nunca como una subida exitosa. No
 * lanza para no tumbar el trabajador por una foto perdida.
 *
 * [borrar] sólo debe llamarse **después** de que el servidor confirmó la
 * subida (D3, mutante A7). El adaptador no decide eso; obedece al llamador.
 */
internal class ArchivosDeGarantiaAdapter @Inject constructor(
    private val directorio: DirectorioDeGarantias
) : ArchivosDeGarantiaPort {

    override suspend fun leer(ruta: String): ByteArray? = try {
        val archivo = directorio.archivo(ruta)
        if (archivo.isFile) archivo.readBytes() else null
    } catch (_: IOException) {
        null
    }

    override suspend fun borrar(ruta: String) {
        directorio.archivo(ruta).delete()
    }
}
