package com.example.msp_app.features.guarantees.data

import com.example.msp_app.core.database.entities.GuaranteeEntity
import com.example.msp_app.core.database.entities.GuaranteeImageEntity
import com.example.msp_app.core.utils.Constants.NOTIFICADO
import com.google.gson.Gson
import java.util.UUID

/**
 * El `RESUMEN` del marcador de una garantía (E-APP-119, D15): lo que el
 * cobrador capturó, para que una garantía CAPTURADA con 0 escrituras se pueda
 * reanudar al volver a la app con el MISMO id.
 *
 * - [fuentes]: las fotos tal como las eligió (`content://`), por si la pantalla
 *   se cerró mientras se comprimían.
 * - [imagenes]: las fotos ya comprimidas en `filesDir`, en orden. Cuando están,
 *   la reanudación no vuelve a comprimir.
 *
 * Gson ignora los defaults de Kotlin: todo se lee como anulable y se valida.
 */
internal data class ResumenDeLaGarantia(
    val descripcionFalla: String?,
    val observaciones: String?,
    val clienteNombre: String?,
    val productoNombre: String?,
    val fechaSolicitud: String?,
    val fuentes: List<String>?,
    val imagenes: List<ImagenResumida>?
) {
    internal data class ImagenResumida(val ruta: String?, val mime: String?)

    /** La garantía a escribir, o `null` si falta un dato obligatorio. */
    fun garantia(externalId: String): GuaranteeEntity? {
        val descripcion = descripcionFalla ?: return null
        val fecha = fechaSolicitud ?: return null
        return GuaranteeEntity(
            ID = 0,
            EXTERNAL_ID = externalId,
            DOCTO_CC_ID = null,
            ESTADO = NOTIFICADO,
            DESCRIPCION_FALLA = descripcion,
            OBSERVACIONES = observaciones?.takeIf { it.isNotBlank() },
            UPLOADED = 0,
            FECHA_SOLICITUD = fecha,
            NOMBRE_CLIENTE = clienteNombre ?: return null,
            NOMBRE_PRODUCTO = productoNombre ?: return null
        )
    }

    /** Las imágenes ya comprimidas, con ids deterministas: el reintento no las duplica. */
    fun imagenesDe(externalId: String): List<GuaranteeImageEntity> {
        val fecha = fechaSolicitud ?: return emptyList()
        return imagenes.orEmpty().mapIndexedNotNull { indice, imagen ->
            val ruta = imagen.ruta ?: return@mapIndexedNotNull null
            GuaranteeImageEntity(
                ID = idDeImagenDeGarantia(externalId, indice),
                GARANTIA_ID = externalId,
                IMG_PATH = ruta,
                IMG_MIME = imagen.mime ?: "image/jpeg",
                IMG_DESC = descripcionFalla,
                FECHA_SUBIDA = fecha
            )
        }
    }

    fun conImagenes(guardadas: List<Pair<String, String>>) =
        copy(imagenes = guardadas.map { (ruta, mime) -> ImagenResumida(ruta, mime) })

    fun json(): String = GSON.toJson(this)

    companion object {
        private val GSON = Gson()

        fun leer(json: String): ResumenDeLaGarantia? =
            runCatching { GSON.fromJson(json, ResumenDeLaGarantia::class.java) }.getOrNull()
    }
}

/** Id determinista de la imagen de una garantía (E-APP-091): el reintento no la duplica. */
internal fun idDeImagenDeGarantia(externalId: String, indice: Int): String =
    UUID.nameUUIDFromBytes("$externalId/$indice".toByteArray(Charsets.UTF_8)).toString()
