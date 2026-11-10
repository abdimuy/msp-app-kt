package com.example.msp_app.feature.compartirproductos.render

import android.content.res.AssetManager
import android.graphics.Typeface
import android.util.Log

/**
 * Las fuentes de la tarjeta, empaquetadas en `assets/fonts/` (licencia OFL, ver
 * `LEEME.txt` ahí). La tarjeta es una IMAGEN: no usa las fuentes del sistema ni
 * la escala de fuente del teléfono, así que se ve igual en cualquier aparato.
 *
 * Si un archivo no se pudiera abrir se cae a la serif/sans del sistema en vez de
 * no mandar la tarjeta; [completas] lo dice para que las pruebas no lo dejen pasar.
 */
internal class FuentesDeTarjeta private constructor(
    val serif: Typeface,
    val serifCursiva: Typeface,
    val sans: Typeface,
    val sansMedia: Typeface,
    val sansSemiNegrita: Typeface,
    val completas: Boolean
) {
    companion object {
        @Suppress(
            "TooGenericExceptionCaught"
        ) // Typeface.createFromAsset lanza RuntimeException pelada si falta el archivo
        fun cargar(assets: AssetManager): FuentesDeTarjeta {
            var todas = true
            fun fuente(archivo: String, respaldo: Typeface): Typeface = try {
                Typeface.createFromAsset(assets, "fonts/$archivo")
            } catch (e: RuntimeException) {
                Log.w(
                    "FuentesDeTarjeta",
                    "no se pudo cargar fonts/$archivo; se usa la del sistema",
                    e
                )
                todas = false
                respaldo
            }
            val serif = fuente("InstrumentSerif-Regular.ttf", Typeface.SERIF)
            val cursiva =
                fuente(
                    "InstrumentSerif-Italic.ttf",
                    Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                )
            val sans = fuente("Geist-Regular.ttf", Typeface.SANS_SERIF)
            val media = fuente("Geist-Medium.ttf", Typeface.SANS_SERIF)
            val semi = fuente("Geist-SemiBold.ttf", Typeface.DEFAULT_BOLD)
            return FuentesDeTarjeta(serif, cursiva, sans, media, semi, todas)
        }
    }
}
