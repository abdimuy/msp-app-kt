package com.example.msp_app.feature.compartirproductos.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.common.ventas.precios.TipoDePrecio
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.compartirproductos.domain.ArmadorDeTarjeta
import com.example.msp_app.feature.compartirproductos.domain.ArticuloParaCompartir
import com.example.msp_app.feature.compartirproductos.domain.PrecioDeCatalogo
import com.example.msp_app.feature.compartirproductos.domain.SeleccionDePrecios
import com.example.msp_app.feature.compartirproductos.domain.TarjetaDeProducto
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.Instant
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Base de las pruebas de dibujo: Robolectric con gráficos NATIVOS (Skia real), el
 * mismo bring-up que los goldens de `:feature:ubicacion`. Sin esto `StaticLayout`
 * no mediría texto de verdad y «nada se sale del área» no probaría nada.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
abstract class RenderBase : RobolectricTestBase() {

    protected val contexto: Context get() = ApplicationProvider.getApplicationContext()
    protected val dibujante by lazy { DibujanteDeTarjeta(contexto) }
    protected val ahora: Instant = Instant.parse("2026-11-10T18:00:00Z")

    protected fun articulo(
        nombre: String = "SALA VALENCIA TERCIOPELO",
        categoria: String? = "SALAS",
        contado: String? = "8990",
        corto: String? = "10450",
        credito: String? = "13200"
    ) = ArticuloParaCompartir(
        id = 1,
        nombre = nombre,
        categoria = categoria,
        precios = listOfNotNull(
            contado?.let { PrecioDeCatalogo(TipoDePrecio.CONTADO, BigDecimal(it)) },
            corto?.let { PrecioDeCatalogo(TipoDePrecio.CORTO_PLAZO, BigDecimal(it)) },
            credito?.let { PrecioDeCatalogo(TipoDePrecio.LARGO_PLAZO, BigDecimal(it)) }
        ),
        fotoPath = null
    )

    protected fun tarjeta(
        articulo: ArticuloParaCompartir = articulo(),
        seleccion: SeleccionDePrecios = SeleccionDePrecios()
    ): TarjetaDeProducto = ArmadorDeTarjeta.armar(articulo, seleccion, ahora)

    /** Foto de prueba: franjas de colores distintos, para ver QUÉ parte se recorta. */
    protected fun fotoConFranjas(
        ancho: Int,
        alto: Int,
        vertical: Boolean,
        franjas: Int = 10
    ): Bitmap {
        val b = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint()
        for (k in 0 until franjas) {
            p.color = colorDeFranja(k)
            if (vertical) {
                c.drawRect(
                    0f,
                    alto * k / franjas.toFloat(),
                    ancho.toFloat(),
                    alto * (k + 1) / franjas.toFloat(),
                    p
                )
            } else {
                c.drawRect(
                    ancho * k / franjas.toFloat(),
                    0f,
                    ancho * (k + 1) / franjas.toFloat(),
                    alto.toFloat(),
                    p
                )
            }
        }
        return b
    }

    protected fun colorDeFranja(k: Int): Int = Color.rgb(
        30 + k * 22,
        200 - k * 17,
        60 + (k % 3) * 70
    )

    /** Foto con ruido: la peor para el JPEG, sirve para acotar el tamaño por arriba. */
    protected fun fotoConRuido(ancho: Int, alto: Int): Bitmap {
        val b = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val r = java.util.Random(7)
        val px =
            IntArray(ancho * alto) { Color.rgb(r.nextInt(256), r.nextInt(256), r.nextInt(256)) }
        b.setPixels(px, 0, ancho, 0, 0, ancho, alto)
        return b
    }

    protected fun jpeg(b: Bitmap): ByteArray = ByteArrayOutputStream().also {
        dibujante.comprimir(b, it)
    }.toByteArray()

    protected fun cerca(a: Int, b: Int, tolerancia: Int = 6): Boolean =
        Math.abs(Color.red(a) - Color.red(b)) <= tolerancia &&
            Math.abs(Color.green(a) - Color.green(b)) <= tolerancia &&
            Math.abs(Color.blue(a) - Color.blue(b)) <= tolerancia
}
