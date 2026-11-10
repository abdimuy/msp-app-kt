package com.example.msp_app.feature.compartirproductos.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import com.example.msp_app.feature.compartirproductos.R
import com.example.msp_app.feature.compartirproductos.domain.TarjetaDeProducto
import java.io.File
import java.io.OutputStream

/**
 * Dibuja la tarjeta «1 · Editorial» en un `Bitmap` de 1080×1920 con `Canvas` +
 * `StaticLayout`. Se eligió esto y no Compose fuera de pantalla porque es lo más
 * determinista bajo JVM: sin recomposición, sin temas, sin escala de fuente del
 * sistema (la tarjeta es una imagen fija), y el texto se mide con las mismas
 * fuentes empaquetadas que lo pintan.
 *
 * Es CPU pura: llamarla fuera del hilo principal (el `Bitmap` pesa ~8 MB).
 */
class DibujanteDeTarjeta(private val context: Context) {

    private val fuentes = FuentesDeTarjeta.cargar(context.assets)
    private val armador = ArmadorDeDisposicion(fuentes)

    /** Se decodifica UNA vez por dibujante (no por tarjeta); con 30 tarjetas se evitan 29 decodificaciones. */
    private val logo: Bitmap? by lazy {
        BitmapFactory.decodeResource(context.resources, R.drawable.logo_msp)
    }

    /** Para las pruebas: ¿se cargaron las cinco fuentes empaquetadas (sin caer al sistema)? */
    internal val fuentesCompletas: Boolean get() = fuentes.completas

    internal fun disposicion(tarjeta: TarjetaDeProducto, tieneFoto: Boolean): Disposicion =
        armador.calcular(tarjeta, tieneFoto)

    fun dibujar(tarjeta: TarjetaDeProducto, foto: Bitmap?): Bitmap {
        val d = armador.calcular(tarjeta, foto != null)
        val bitmap = Bitmap.createBitmap(Medidas.ANCHO, Medidas.ALTO, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Medidas.FONDO)
        dibujarFoto(canvas, d, foto)
        dibujarMarca(canvas)
        dibujarTextos(canvas, d)
        dibujarPrecios(canvas, d)
        return bitmap
    }

    /** JPEG calidad 88 (≈ 200–400 KB a 1080×1920). */
    fun comprimir(bitmap: Bitmap, salida: OutputStream) {
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, Medidas.JPEG_CALIDAD, salida)) {
            "no se pudo comprimir la tarjeta"
        }
    }

    private fun dibujarFoto(canvas: Canvas, d: Disposicion, foto: Bitmap?) {
        val destino = Rect(0, 0, Medidas.ANCHO, d.fotoAlto)
        if (foto == null) {
            canvas.drawRect(destino, Paint().apply { color = Medidas.FONDO_SIN_FOTO })
            dibujarMonograma(canvas, d)
            return
        }
        val recorte = RecorteDeFoto.cubrir(
            foto.width,
            foto.height,
            destino.width(),
            destino.height()
        )
        val origen = Rect(recorte.izquierda, recorte.arriba, recorte.derecha, recorte.abajo)
        canvas.drawBitmap(
            foto,
            origen,
            destino,
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        )
    }

    private fun dibujarMonograma(canvas: Canvas, d: Disposicion) {
        val pintura = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fuentes.serifCursiva
            textSize = Medidas.MONOGRAMA_TAMANO
            color = Medidas.MONOGRAMA
            textAlign = Paint.Align.CENTER
        }
        val metricas = pintura.fontMetrics
        val centroY = d.fotoAlto / 2f
        canvas.drawText(
            MONOGRAMA,
            Medidas.ANCHO / 2f,
            centroY - (metricas.ascent + metricas.descent) / 2f,
            pintura
        )
    }

    private fun dibujarMarca(canvas: Canvas) {
        val lado = Medidas.LOGO_LADO.toFloat()
        val izq = Medidas.MARGEN_LATERAL.toFloat()
        val arriba = Medidas.LOGO_ARRIBA.toFloat()
        val imagenLogo = logo
        if (imagenLogo != null) {
            val circulo = Path().apply {
                addCircle(izq + lado / 2, arriba + lado / 2, lado / 2, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(circulo)
            canvas.drawBitmap(
                imagenLogo,
                null,
                RectF(izq, arriba, izq + lado, arriba + lado),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            )
            canvas.restore()
        }
        val texto = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fuentes.sansSemiNegrita
            textSize = Medidas.MARCA_TAMANO
            color = Medidas.TINTA
            letterSpacing = Medidas.MARCA_ESPACIADO_EM
        }
        val metricas = texto.fontMetrics
        val base = arriba + lado / 2 - (metricas.ascent + metricas.descent) / 2
        canvas.drawText(MARCA, izq + lado + Medidas.LOGO_SEPARACION, base, texto)
    }

    private fun dibujarTextos(canvas: Canvas, d: Disposicion) {
        fun layoutEn(layout: android.text.StaticLayout, y: Int) {
            canvas.save()
            canvas.translate(Medidas.MARGEN_LATERAL.toFloat(), y.toFloat())
            layout.draw(canvas)
            canvas.restore()
        }
        d.categoria?.let { layoutEn(it, d.posiciones.categoriaY) }
        layoutEn(d.titulo, d.posiciones.tituloY)
        d.detalle?.let { layoutEn(it, d.posiciones.detalleY) }
        layoutEn(d.pie, d.posiciones.pieY)
    }

    private fun dibujarPrecios(canvas: Canvas, d: Disposicion) {
        val bloque = d.bloqueDePrecios ?: return
        val izq = Medidas.MARGEN_LATERAL.toFloat()
        val der = (Medidas.ANCHO - Medidas.MARGEN_LATERAL).toFloat()
        val relleno = if (bloque.conCaja) Medidas.BLOQUE_RELLENO_LATERAL.toFloat() else 0f
        if (bloque.conCaja) {
            val caja =
                RectF(izq, bloque.arriba.toFloat(), der, (bloque.arriba + bloque.alto).toFloat())
            canvas.drawRoundRect(
                caja,
                Medidas.BLOQUE_RADIO,
                Medidas.BLOQUE_RADIO,
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                ).apply {
                    color = Medidas.BLANCO
                }
            )
        } else {
            canvas.drawRect(
                izq,
                bloque.arriba.toFloat(),
                der,
                bloque.arriba + Medidas.SEPARADOR_GRUESO,
                Paint().apply {
                    color = Medidas.REGLA_SOLA
                }
            )
        }
        val regla = Paint().apply { color = Medidas.SEPARADOR_FILAS }
        d.filas.forEachIndexed { i, fila ->
            if (bloque.conCaja && i > 0) {
                val y = fila.arriba - Medidas.SEPARADOR_GRUESO
                canvas.drawRect(
                    izq + relleno,
                    y,
                    der - relleno,
                    y + Medidas.SEPARADOR_GRUESO,
                    regla
                )
            }
            val relleno1 = if (bloque.conCaja) Medidas.FILA_RELLENO else Medidas.SOLA_RELLENO_ARRIBA
            val base = fila.arriba + relleno1 - fila.montoPaint.fontMetrics.ascent
            canvas.drawText(fila.precio.fila.etiqueta, izq + relleno, base, fila.etiquetaPaint)
            canvas.drawText(
                fila.precio.texto,
                der - relleno - fila.montoPaint.measureText(fila.precio.texto),
                base,
                fila.montoPaint
            )
        }
    }

    /** Escribe la tarjeta en [archivo] como JPEG y libera el bitmap. */
    fun escribir(tarjeta: TarjetaDeProducto, foto: Bitmap?, archivo: File) {
        val bitmap = dibujar(tarjeta, foto)
        try {
            archivo.outputStream().use { comprimir(bitmap, it) }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val MARCA = "MUEBLES SAN PABLO"
        const val MONOGRAMA = "MSP"
    }
}
