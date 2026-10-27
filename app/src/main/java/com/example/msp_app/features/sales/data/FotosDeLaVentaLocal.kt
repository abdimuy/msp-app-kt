package com.example.msp_app.features.sales.data

import android.content.Context
import android.net.Uri
import com.example.msp_app.core.database.entities.LocalSaleImageEntity
import com.example.msp_app.core.utils.ImageCompressor
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Las fotos de una venta local, comprimidas ANTES de la transacción (E-APP-094,
 * diseño D5 r5: las fotos de la venta se quedan dentro de la venta).
 *
 * - Nombre fijo `sale_<saleId>_<imageId>.jpg`, con `imageId` derivado del
 *   `saleId` y de la foto del borrador: reintentar el mismo borrador escribe el
 *   mismo archivo en vez de dejar otro (antes el nombre llevaba la hora).
 * - Se comprime a un temporal y se renombra: un archivo a medias nunca lleva el
 *   nombre final.
 * - **Una foto que falla no se traga**: lanza [FotoNoProcesada] y la venta no se
 *   escribe. Antes se saltaba, la venta salía "guardada" sin la foto y el
 *   subidor la dejaba atorada por "NO_IMAGES" (MEDIDO por la compuerta ANTES).
 */
class FotosDeLaVentaLocal(
    private val directorio: File,
    private val compresor: Compresor
) {
    constructor(context: Context) : this(
        context.filesDir,
        Compresor { uri, destino ->
            require(ImageCompressor.isValidImage(context, uri)) { "no es una imagen válida" }
            ImageCompressor.compressImage(context, uri, destino.name).outputFile
        }
    )

    /** Escribe la foto [uri] comprimida en [destino] (dentro de [directorio]) y lo devuelve. */
    fun interface Compresor {
        fun comprimir(uri: Uri, destino: File): File
    }

    /** La foto [numero] (desde 1) no se pudo procesar. */
    class FotoNoProcesada(val numero: Int, causa: Throwable) :
        Exception("No se pudo procesar la foto $numero", causa)

    @Suppress("TooGenericExceptionCaught") // cualquier falla de una foto detiene la venta
    suspend fun preparar(
        saleId: String,
        uris: List<Uri>,
        fecha: String
    ): List<LocalSaleImageEntity> = withContext(Dispatchers.IO) {
        uris.mapIndexed { indice, uri ->
            val imageId = imageIdDe(saleId, indice, uri)
            val final = File(directorio, "sale_${saleId}_$imageId.jpg")
            val temporal = File(directorio, "${final.name}.tmp")
            try {
                val escrito = compresor.comprimir(uri, temporal)
                check(escrito.isFile && escrito.length() > 0) { "la foto comprimida quedó vacía" }
                check(escrito.renameTo(final)) { "no se pudo renombrar la foto comprimida" }
            } catch (cancelada: CancellationException) {
                temporal.delete()
                throw cancelada
            } catch (falla: Exception) {
                temporal.delete()
                throw FotoNoProcesada(indice + 1, falla)
            }
            LocalSaleImageEntity(
                LOCAL_SALE_IMAGE_ID = imageId,
                LOCAL_SALE_ID = saleId,
                IMAGE_URI = final.absolutePath,
                FECHA_SUBIDA = fecha
            )
        }
    }

    companion object {
        /** Estable para el mismo borrador: mismo `saleId`, misma foto, mismo lugar. */
        fun imageIdDe(saleId: String, indice: Int, uri: Uri): String =
            UUID.nameUUIDFromBytes("$saleId|$indice|$uri".toByteArray(Charsets.UTF_8)).toString()
    }
}
