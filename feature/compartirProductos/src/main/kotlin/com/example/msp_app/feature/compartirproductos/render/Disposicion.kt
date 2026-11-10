package com.example.msp_app.feature.compartirproductos.render

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.MetricAffectingSpan
import com.example.msp_app.feature.compartirproductos.domain.FilaDePrecio
import com.example.msp_app.feature.compartirproductos.domain.PrecioDeTarjeta
import com.example.msp_app.feature.compartirproductos.domain.TarjetaDeProducto
import java.util.Locale

/** Un rectángulo de lo dibujado, para que las pruebas midan que nada se sale del área. */
internal data class Caja(
    val nombre: String,
    val izquierda: Float,
    val arriba: Float,
    val derecha: Float,
    val abajo: Float
)

internal class FilaDibujada(
    val precio: PrecioDeTarjeta,
    val etiquetaPaint: TextPaint,
    val montoPaint: TextPaint,
    val alto: Int,
    var arriba: Int = 0
)

/**
 * TODO lo que se va a dibujar, ya medido y colocado. El dibujo ([DibujanteDeTarjeta])
 * solo recorre esto; así las pruebas miden la misma geometría que se pinta.
 */
@Suppress(
    "LongParameterList"
) // retrato plano de todo lo que se pinta; partirlo solo movería los campos de sitio
internal class Disposicion(
    val tieneFoto: Boolean,
    val fotoAlto: Int,
    val titulo: StaticLayout,
    val tituloTamano: Float,
    val categoria: StaticLayout?,
    val detalle: StaticLayout?,
    val pie: StaticLayout,
    val filas: List<FilaDibujada>,
    val bloqueDePrecios: BloqueDePrecios?,
    val posiciones: Posiciones,
    val cajas: List<Caja>,
    /** ¿Todo cupo respetando la foto mínima y las separaciones mínimas? */
    val cabe: Boolean
)

internal class Posiciones(val categoriaY: Int, val tituloY: Int, val detalleY: Int, val pieY: Int)

internal class BloqueDePrecios(val arriba: Int, val alto: Int, val conCaja: Boolean)

private class CursivaSpan(private val tipografia: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(paint: TextPaint) {
        paint.typeface = tipografia
    }

    override fun updateMeasureState(paint: TextPaint) {
        paint.typeface = tipografia
    }
}

/** Mide el texto con `StaticLayout` real y coloca cada pieza de la tarjeta. */
@Suppress(
    "TooManyFunctions"
) // una función por pieza de la tarjeta (título, categoría, detalle, pie, filas…)
internal class ArmadorDeDisposicion(private val fuentes: FuentesDeTarjeta) {

    private val anchoTexto = Medidas.ANCHO - 2 * Medidas.MARGEN_LATERAL

    /**
     * El peor caso posible —título de 2 renglones, detalle de 3, categoría, las 3 filas de precios y el pie—
     * cabe con la foto en su mínimo (40 %) y 33 px de sobra (medido en `DibujanteDeTarjetaTest`, E-APP-181);
     * [Disposicion.cabe] lo vuelve a comprobar en cada tarjeta para que una medida cambiada lo grite en pruebas.
     */
    fun calcular(tarjeta: TarjetaDeProducto, tieneFoto: Boolean): Disposicion {
        val (titulo, tamano) = armarTitulo(tarjeta.titulo)
        val categoria = tarjeta.categoria?.let { armarCategoria(it) }
        val detalle = tarjeta.detalle?.let { armarDetalle(it, Medidas.DETALLE_LINEAS) }
        val pie = armarPie(tarjeta.vigencia)
        val filas = tarjeta.precios.map { armarFila(it, esSola = tarjeta.precios.size == 1) }
        val bloque = armarBloque(filas)

        val cabecera = altoDeCabecera(categoria, titulo, detalle)
        val piezas = listOfNotNull(cabecera, bloque?.alto, pie.height)
        val total = piezas.sum()
        val minimas = (piezas.size - 1) * Medidas.SEPARACION_MINIMA

        var fotoAlto = if (tieneFoto) Medidas.FOTO_ALTO_MAXIMO else Medidas.FOTO_ALTO_SIN_FOTO
        val disponibleTotal = Medidas.ALTO - Medidas.RELLENO_ARRIBA - Medidas.RELLENO_ABAJO
        if (total + minimas > disponibleTotal - fotoAlto) {
            fotoAlto = (disponibleTotal - total - minimas).coerceAtLeast(Medidas.FOTO_ALTO_MINIMO)
        }
        val disponible = disponibleTotal - fotoAlto
        val cabe = disponible - total >= minimas
        val separacion = ((disponible - total) / (piezas.size - 1)).coerceAtLeast(0)
        val inicio = fotoAlto + Medidas.RELLENO_ARRIBA

        var y = inicio
        val categoriaY = y
        val tituloY = categoriaY + (categoria?.let { it.height + Medidas.CATEGORIA_A_TITULO } ?: 0)
        val detalleY = tituloY + titulo.height + Medidas.TITULO_A_DETALLE
        y += cabecera + separacion
        val bloqueFinal = bloque?.let {
            BloqueDePrecios(
                y,
                it.alto,
                it.conCaja
            ).also { colocado -> colocarFilas(filas, colocado) }
        }
        if (bloque != null) y += bloque.alto + separacion
        val pieY = inicio + disponible - pie.height

        val posiciones = Posiciones(categoriaY, tituloY, detalleY, pieY)
        val sinCajas = Disposicion(
            tieneFoto, fotoAlto, titulo, tamano, categoria, detalle, pie, filas, bloqueFinal, posiciones, emptyList(), cabe
        )
        return Disposicion(
            tieneFoto, fotoAlto, titulo, tamano, categoria, detalle, pie, filas, bloqueFinal, posiciones,
            medirCajas(sinCajas), cabe
        )
    }

    // region — piezas

    private fun paint(tipografia: Typeface, tamano: Float, color: Int, espaciadoEm: Float = 0f) =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = tipografia
            textSize = tamano
            this.color = color
            letterSpacing = espaciadoEm
        }

    /**
     * `StaticLayout` multiplica la altura NATURAL de la fuente, y la de Instrument Serif y
     * Geist es ~1.3 em. El diseño fija el interlineado en em (título 1.0, detalle 1.4), así
     * que el multiplicador se saca de la fuente real: alto deseado / alto natural.
     */
    private fun interlineaEn(pintura: TextPaint, em: Float): Float {
        val natural = pintura.fontMetrics.descent - pintura.fontMetrics.ascent
        return em * pintura.textSize / natural
    }

    private fun layout(
        texto: CharSequence,
        pintura: TextPaint,
        ancho: Int,
        maxLineas: Int,
        interlinea: Float = 1f
    ): StaticLayout = StaticLayout.Builder.obtain(texto, 0, texto.length, pintura, ancho)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setLineSpacing(0f, interlinea)
        .setMaxLines(maxLineas)
        .setEllipsize(TextUtils.TruncateAt.END)
        .setEllipsizedWidth(ancho)
        .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        .build()

    private fun tituloConCursiva(texto: String): SpannableString {
        val spannable = SpannableString(texto)
        PalabraEnCursiva.rango(texto)?.let {
            spannable.setSpan(
                CursivaSpan(fuentes.serifCursiva),
                it.first,
                it.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return spannable
    }

    private fun armarTitulo(texto: String): Pair<StaticLayout, Float> {
        val conCursiva = tituloConCursiva(texto)
        fun pintura(tamano: Float) =
            paint(fuentes.serif, tamano, Medidas.TINTA, Medidas.TITULO_ESPACIADO_EM)
        val tamano = AjusteDeTexto.tamanoDeTitulo { candidato ->
            // Sin tope de renglones: se mide cuántos pediría el texto completo a ese tamaño.
            val libre = StaticLayout.Builder.obtain(
                conCursiva,
                0,
                conCursiva.length,
                pintura(candidato),
                anchoTexto
            )
                .setIncludePad(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build()
            libre.lineCount
        }
        val elegida = pintura(tamano)
        return layout(conCursiva, elegida, anchoTexto, Medidas.TITULO_LINEAS, interlineaEn(elegida, Medidas.TITULO_INTERLINEA)) to tamano
    }

    private fun armarCategoria(texto: String): StaticLayout = layout(
        texto.uppercase(Locale("es", "MX")),
        paint(
            fuentes.sansMedia,
            Medidas.CATEGORIA_TAMANO,
            Medidas.TINTA_TENUE,
            Medidas.CATEGORIA_ESPACIADO_EM
        ),
        anchoTexto,
        1
    )

    private fun armarDetalle(texto: String, maxLineas: Int): StaticLayout {
        val pintura = paint(fuentes.sans, Medidas.DETALLE_TAMANO, Medidas.TINTA_SUAVE)
        return layout(
            texto,
            pintura,
            anchoTexto,
            maxLineas,
            interlineaEn(pintura, Medidas.DETALLE_INTERLINEA)
        )
    }

    private fun armarPie(texto: String): StaticLayout = layout(
        texto,
        paint(fuentes.sansMedia, Medidas.PIE_TAMANO, Medidas.TINTA_TENUE),
        anchoTexto,
        1
    )

    private fun armarFila(precio: PrecioDeTarjeta, esSola: Boolean): FilaDibujada {
        val contado = precio.fila == FilaDePrecio.CONTADO
        val etiqueta: TextPaint
        val monto: TextPaint
        when {
            esSola -> {
                etiqueta = paint(fuentes.sansMedia, Medidas.SOLA_ETIQUETA, Medidas.TINTA_SUAVE)
                monto = paint(fuentes.sansSemiNegrita, Medidas.SOLA_CIFRAS, Medidas.TINTA, Medidas.SOLA_CIFRAS_ESPACIADO_EM)
            }
            contado -> {
                etiqueta = paint(fuentes.sansSemiNegrita, Medidas.FILA_ETIQUETA, Medidas.TINTA)
                monto = paint(fuentes.sansSemiNegrita, Medidas.FILA_CONTADO_CIFRAS, Medidas.TINTA, Medidas.FILA_CONTADO_CIFRAS_ESPACIADO_EM)
            }
            else -> {
                etiqueta = paint(fuentes.sansMedia, Medidas.FILA_ETIQUETA, Medidas.TINTA_SUAVE)
                monto = paint(fuentes.sansMedia, Medidas.FILA_OTRA_CIFRAS, Medidas.TINTA, Medidas.FILA_OTRA_CIFRAS_ESPACIADO_EM)
            }
        }
        monto.fontFeatureSettings = "tnum" // cifras tabulares: los montos alinean por dígito
        val altoMonto = (monto.fontMetrics.descent - monto.fontMetrics.ascent).toInt() + 1
        val relleno = if (esSola) Medidas.SOLA_RELLENO_ARRIBA else Medidas.FILA_RELLENO
        val alto = if (esSola) relleno + altoMonto else 2 * relleno + altoMonto
        return FilaDibujada(precio, etiqueta, monto, alto)
    }

    private fun armarBloque(filas: List<FilaDibujada>): BloqueDePrecios? {
        if (filas.isEmpty()) return null
        if (filas.size == 1) {
            return BloqueDePrecios(
                0,
                filas[0].alto + Medidas.SEPARADOR_GRUESO.toInt(),
                conCaja = false
            )
        }
        val separadores = (filas.size - 1) * Medidas.SEPARADOR_GRUESO.toInt()
        return BloqueDePrecios(
            0,
            2 * Medidas.BLOQUE_RELLENO_VERTICAL + filas.sumOf {
                it.alto
            } + separadores,
            conCaja = true
        )
    }

    private fun colocarFilas(filas: List<FilaDibujada>, bloque: BloqueDePrecios) {
        var y = bloque.arriba + if (bloque.conCaja) Medidas.BLOQUE_RELLENO_VERTICAL else Medidas.SEPARADOR_GRUESO.toInt()
        filas.forEach {
            it.arriba = y
            y += it.alto + Medidas.SEPARADOR_GRUESO.toInt()
        }
    }

    private fun altoDeCabecera(
        categoria: StaticLayout?,
        titulo: StaticLayout,
        detalle: StaticLayout?
    ): Int = (categoria?.let { it.height + Medidas.CATEGORIA_A_TITULO } ?: 0) +
        titulo.height +
        (detalle?.let { it.height + Medidas.TITULO_A_DETALLE } ?: 0)

    // endregion

    // region — cajas para medir

    private fun medirCajas(d: Disposicion): List<Caja> {
        val cajas = mutableListOf<Caja>()
        val izq = Medidas.MARGEN_LATERAL.toFloat()

        fun deLayout(nombre: String, layout: StaticLayout, y: Int) {
            var derecha = 0f
            for (i in 0 until layout.lineCount) derecha = maxOf(derecha, layout.getLineRight(i))
            cajas += Caja(nombre, izq, y.toFloat(), izq + derecha, (y + layout.height).toFloat())
        }
        d.categoria?.let { deLayout("categoria", it, d.posiciones.categoriaY) }
        deLayout("titulo", d.titulo, d.posiciones.tituloY)
        d.detalle?.let { deLayout("detalle", it, d.posiciones.detalleY) }
        deLayout("pie", d.pie, d.posiciones.pieY)
        d.bloqueDePrecios?.let {
            cajas += Caja("precios", izq, it.arriba.toFloat(), (Medidas.ANCHO - Medidas.MARGEN_LATERAL).toFloat(), (it.arriba + it.alto).toFloat())
        }
        return cajas
    }
    // endregion
}

/** Qué palabra del título va en cursiva (opcional en el diseño): la última «significativa». */
internal object PalabraEnCursiva {
    private val SIN_PESO = setOf(
        "de", "del", "la", "las", "el", "los", "un", "una", "y", "e", "o", "u", "a", "al", "con", "sin", "para", "por", "en"
    )
    private const val LARGO_MINIMO = 4

    /** Rango de caracteres de la última palabra con letras, ≥ 4 letras y que no sea de relleno; `null` si no hay o solo hay una palabra. */
    fun rango(titulo: String): IntRange? {
        val palabras = Regex("\\S+").findAll(titulo).toList()
        if (palabras.size < 2) return null
        val ultima = palabras.last()
        val limpia = ultima.value.trim { !it.isLetter() }
        val soloLetras = limpia.isNotEmpty() && limpia.all { it.isLetter() }
        if (!soloLetras || limpia.length < LARGO_MINIMO || limpia.lowercase() in SIN_PESO) return null
        val inicio = ultima.range.first + ultima.value.indexOf(limpia)
        return inicio until inicio + limpia.length
    }
}
