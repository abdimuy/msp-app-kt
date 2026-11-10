package com.example.msp_app.feature.compartirproductos.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

/** Carga la foto local del producto, reducida a lo que la tarjeta necesita. */
object FotoDeProducto {
    /** Con 1080×1920 de tarjeta no hace falta más de ~2200 px de lado largo. */
    const val LADO_MAXIMO = 2200

    /** `null` si no hay ruta, el archivo no existe o no es una imagen: la tarjeta sale sin foto. */
    fun cargar(ruta: String?): Bitmap? {
        if (ruta.isNullOrBlank()) return null
        val archivo = File(ruta)
        if (!archivo.isFile) return null
        return try {
            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(ruta, medidas)
            if (medidas.outWidth <= 0 || medidas.outHeight <= 0) return null
            var muestra = 1
            while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= LADO_MAXIMO) muestra *= 2
            BitmapFactory.decodeFile(ruta, BitmapFactory.Options().apply { inSampleSize = muestra })
        } catch (e: OutOfMemoryError) {
            Log.w("FotoDeProducto", "foto demasiado grande: la tarjeta sale sin foto", e)
            null
        }
    }
}
