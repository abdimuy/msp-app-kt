package com.example.msp_app.feature.garantias.data.files

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * La raíz de los archivos de la captura: `filesDir/garantias/`.
 *
 * ## Por qué un subdirectorio y rutas relativas
 *
 * `filesDir` es compartido con los comprobantes de visitas y de pagos, y cada
 * barrido de huérfanos consulta las rutas vivas de **su** tabla. Si las fotos
 * de garantías vivieran sueltas en `filesDir`, el barrido de pagos las vería
 * como "archivos que ninguna fila referencia" y las borraría.
 *
 * La columna `RUTA_ARCHIVO` guarda la ruta **relativa** (`fotos/<clave>.jpg`),
 * no la absoluta: `filesDir` cambia de raíz entre instalaciones y entre
 * usuarios del teléfono, así que una ruta absoluta guardada ayer puede apuntar
 * hoy a la nada. El adaptador resuelve contra esta raíz en cada lectura.
 */
internal class DirectorioDeGarantias @Inject constructor(
    @ApplicationContext context: Context
) {

    private val raiz: File = File(context.filesDir, CARPETA_RAIZ)

    /** El archivo de una ruta relativa guardada en Room. */
    fun archivo(ruta: String): File = File(raiz, ruta)

    /** Un archivo nuevo dentro de [carpeta], creando la carpeta si no está. */
    fun nuevoArchivo(carpeta: String, nombre: String): File {
        val destino = File(raiz, carpeta)
        destino.mkdirs()
        return File(destino, nombre)
    }

    /** La ruta relativa que se guarda en Room para [archivo]. */
    fun relativa(archivo: File): String = archivo.relativeTo(raiz).invariantSeparatorsPath

    companion object {
        const val CARPETA_RAIZ: String = "garantias"
        const val FOTOS: String = "fotos"
        const val FIRMAS: String = "firmas"
    }
}
