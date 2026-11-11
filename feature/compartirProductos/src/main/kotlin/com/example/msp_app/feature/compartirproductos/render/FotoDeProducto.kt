package com.example.msp_app.feature.compartirproductos.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException

/**
 * Carga la foto local del producto lista para la tarjeta:
 *  - **E-APP-184**: se decodifica reducida por potencias de 2 al mínimo que aún CABE a resolución completa en
 *    el área de la foto (1080×1008): basta con que UN lado alcance su medida (la foto se muestra entera, no
 *    recortada). Una foto de 8000×8000 queda en 2000×2000 (16 MB), no en 4000×4000 (64 MB); una panorámica
 *    de 10000×1500 queda en 1250×187.
 *  - **E-APP-185**: se respeta la orientación EXIF (1 a 8, con espejos): el teléfono guarda la foto «de lado»
 *    y la etiqueta dice cómo girarla.
 */
object FotoDeProducto {
    private const val ANCHO_NECESARIO = Medidas.ANCHO
    private const val ALTO_NECESARIO = Medidas.FOTO_ALTO_MAXIMO
    private const val GIRO_90 = 90f
    private const val GIRO_180 = 180f
    private const val GIRO_270 = 270f

    /**
     * Mayor potencia de 2 con la que la foto (ya girada: [ancho]×[alto]) sigue CABIENDO a resolución completa
     * en 1080×1008: al menos uno de los lados alcanza su medida. Ese lado es justo el que limita al escalarla
     * para que quepa, así que no se baja de la resolución que la tarjeta necesita.
     */
    fun muestraPara(ancho: Int, alto: Int): Int {
        var muestra = 1
        while (ancho / (muestra * 2) >= ANCHO_NECESARIO || alto / (muestra * 2) >= ALTO_NECESARIO) muestra *= 2
        return muestra
    }

    /** La matriz que endereza la orientación EXIF [orientacion]; `null` si ya está derecha. */
    fun matrizDe(orientacion: Int): Matrix? {
        val m = Matrix()
        when (orientacion) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(GIRO_180)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                m.postRotate(GIRO_90)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(GIRO_90)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                m.postRotate(GIRO_270)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(GIRO_270)
            else -> return null
        }
        return m
    }

    private fun giraLosLados(orientacion: Int) = orientacion in
        listOf(
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270
        )

    private fun orientacionDe(archivo: File): Int = try {
        ExifInterface(
            archivo.path
        ).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: IOException) {
        Log.w("FotoDeProducto", "sin EXIF legible; se usa la foto tal cual", e)
        ExifInterface.ORIENTATION_NORMAL
    }

    /** `null` si no hay ruta, el archivo no existe o no es una imagen: la tarjeta sale sin foto. */
    fun cargar(ruta: String?): Bitmap? {
        val archivo = ruta?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isFile }
        return archivo?.let { decodificar(it) }
    }

    private fun decodificar(archivo: File): Bitmap? = try {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(archivo.path, medidas)
        if (medidas.outWidth <= 0 || medidas.outHeight <= 0) {
            null
        } else {
            val orientacion = orientacionDe(archivo)
            val (ancho, alto) = if (giraLosLados(orientacion)) {
                medidas.outHeight to medidas.outWidth
            } else {
                medidas.outWidth to medidas.outHeight
            }
            val opciones = BitmapFactory.Options().apply { inSampleSize = muestraPara(ancho, alto) }
            BitmapFactory.decodeFile(archivo.path, opciones)?.let { enderezar(it, orientacion) }
        }
    } catch (e: OutOfMemoryError) {
        Log.w("FotoDeProducto", "foto demasiado grande: la tarjeta sale sin foto", e)
        null
    }

    private fun enderezar(crudo: Bitmap, orientacion: Int): Bitmap {
        val matriz = matrizDe(orientacion) ?: return crudo
        val girado = Bitmap.createBitmap(crudo, 0, 0, crudo.width, crudo.height, matriz, true)
        if (girado !== crudo) crudo.recycle()
        return girado
    }
}
