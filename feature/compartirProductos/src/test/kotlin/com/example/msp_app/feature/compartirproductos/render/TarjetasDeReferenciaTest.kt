package com.example.msp_app.feature.compartirproductos.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.example.msp_app.feature.compartirproductos.domain.SeleccionDePrecios
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Imágenes de referencia 1080×1920 (`src/test/screenshots/tarjeta_*.png`).
 *
 * Ojo, como en los goldens de `:feature:ubicacion`: con `./gradlew test` a secas
 * Roborazzi NO compara nada (solo `verifyRoborazziDebug` compara y
 * `recordRoborazziDebug` graba). Lo que sí protege siempre es `DibujanteDeTarjetaTest`
 * (píxeles y geometría). Estas imágenes son para MIRARLAS y para detectar cambios
 * visuales con `verifyRoborazziDebug`. Las fotos son sintéticas (no hay fotos reales
 * en el repo): un degradado con una silueta, con proporción horizontal o vertical.
 */
class TarjetasDeReferenciaTest : RenderBase() {

    /** Una «foto» con luz y una silueta de sofá, para que se note el recorte. */
    private fun fotoSala(ancho: Int, alto: Int): Bitmap {
        val b = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, alto.toFloat(), Color.parseColor("#c9d6cf"), Color.parseColor("#5b7a6a"), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, ancho.toFloat(), alto.toFloat(), p)
        p.shader = null
        p.color = Color.parseColor("#2f5d48")
        val w = ancho * 0.7f
        val x0 = (ancho - w) / 2
        c.drawRoundRect(x0, alto * 0.52f, x0 + w, alto * 0.82f, 40f, 40f, p)
        c.drawRoundRect(x0 - ancho * 0.04f, alto * 0.46f, x0 + w * 0.12f, alto * 0.86f, 40f, 40f, p)
        c.drawRoundRect(
            x0 + w * 0.88f,
            alto * 0.46f,
            x0 + w + ancho * 0.04f,
            alto * 0.86f,
            40f,
            40f,
            p
        )
        p.color = Color.parseColor("#e8d9b5")
        c.drawCircle(ancho * 0.82f, alto * 0.2f, alto * 0.07f, p)
        return b
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun referencia(
        nombre: String,
        t: com.example.msp_app.feature.compartirproductos.domain.TarjetaDeProducto,
        foto: Bitmap?
    ) {
        val bitmap = dibujante.dibujar(t, foto)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
        bitmap.captureRoboImage(
            filePath = "src/test/screenshots/tarjeta_$nombre.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
            )
        )
    }

    @Test fun normal() = referencia(
        "normal",
        tarjeta(articulo(nombre = "SOFA VALENCIA TERCIOPELO", categoria = "SALAS")),
        fotoSala(1600, 1000)
    )

    @Test fun nombreLargoConDetalleSoloContado() = referencia(
        "nombre_largo_solo_contado",
        tarjeta(
            articulo(
                nombre = "RECAMARA TOSCANA MATRIMONIAL C/CABECERA TAPIZADA, BURO DOBLE Y COMODA DE 6 CAJONES",
                categoria = "RECAMARAS",
                contado = "124900"
            ),
            SeleccionDePrecios(true, false, false)
        ),
        fotoSala(1600, 1000)
    )

    @Test fun sinPrecios() = referencia(
        "sin_precios",
        tarjeta(
            articulo(nombre = "SALA ESQUINERA GRIS OXFORD C/PUFF", categoria = "SALAS"),
            SeleccionDePrecios(false, false, false)
        ),
        fotoSala(1600, 1000)
    )

    @Test fun sinFoto() = referencia(
        "sin_foto",
        tarjeta(
            articulo(
                nombre = "COMEDOR ROBLE NATURAL 6 SILLAS",
                categoria = "COMEDORES",
                contado = "15600",
                corto = null,
                credito = "22900"
            )
        ),
        null
    )

    @Test fun fotoHorizontal() = referencia(
        "foto_horizontal",
        tarjeta(articulo(nombre = "SALA VALENCIA 3 PZAS")),
        fotoSala(2400, 1200)
    )

    @Test fun fotoVertical() = referencia(
        "foto_vertical",
        tarjeta(articulo(nombre = "SALA VALENCIA 3 PZAS")),
        fotoSala(1000, 2000)
    )

    @Test fun nombreExtremo() = referencia(
        "nombre_extremo",
        tarjeta(
            articulo(
                nombre = "COMEDOR ROBLE NATURAL 6 SILLAS C/CRISTAL TEMPLADO, BASE METALICA, TAPIZADO EN VINIL NEGRO, " +
                    "ACABADO MATE ANTIDERRAPANTE, ENVIO E INSTALACION SIN COSTO EN LA ZONA METROPOLITANA DE LA CIUDAD",
                categoria = "COMEDORES"
            )
        ),
        fotoSala(1600, 1000)
    )

    @Test fun preciosGrandes() = referencia(
        "precios_grandes",
        tarjeta(
            articulo(
                nombre = "ISLA DE COCINA GRANITO",
                contado = "1250000",
                corto = "1450000",
                credito = "1650000"
            )
        ),
        fotoSala(1600, 1000)
    )
}
