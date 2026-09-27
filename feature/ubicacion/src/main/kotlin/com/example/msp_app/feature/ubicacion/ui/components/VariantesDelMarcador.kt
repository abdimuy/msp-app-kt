package com.example.msp_app.feature.ubicacion.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import com.example.msp_app.feature.ubicacion.ui.PaletaDelMapa
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** La figura del marcador: cada clase de lugar tiene forma propia, no sólo color. */
enum class Figura {
    /** Disco lleno con aro blanco: un lugar donde se cobró. */
    DISCO,

    /** Aro hueco: transferencias (la coordenada es del cobrador, no vota). */
    HUECO,

    /** Gota: **sólo** el principal. La forma dice "donde más paga" antes que el color. */
    PIN,

    /** Rombo hueco: una visita (sólo con el filtro Tipo). */
    ROMBO
}

/** Dónde va la píldora respecto del marcador. */
enum class PosicionDeEtiqueta { DERECHA_DEL_PIN, ARRIBA, ARRIBA_DERECHA }

/** La píldora que viaja **dentro del bitmap** del marcador: "Principal · 21", "Último · 24 sep". */
data class EtiquetaDelMarcador(
    val texto: String,
    val dato: String,
    val colorDelPunto: Int?,
    val posicion: PosicionDeEtiqueta
)

/**
 * **La forma del marcador**: la llave del caché.
 *
 * El número va dentro de la forma porque se pinta dentro del bitmap; con ~20
 * mediciones por cliente los conteos distintos son pocos y el caché sigue
 * cortando por forma, no por marcador (lo cuenta `ElCacheDibujaPorFormaTest`).
 */
data class FormaDelMarcador(
    val figura: Figura,
    val color: Int,
    val conteo: Int? = null,
    val diametroDp: Float = diametroDe(conteo ?: 1),
    val resaltado: Boolean = false,
    val etiqueta: EtiquetaDelMarcador? = null
)

/**
 * **El tamaño del disco: 22 dp con 1 cobro, +4 dp cada vez que el conteo se
 * duplica, tope 38 dp** (la regla "Tamaño" del mock). 185 cobros caben.
 */
fun diametroDe(conteo: Int): Float = min(
    DIAMETRO_MAXIMO,
    DIAMETRO_MINIMO + CRECIMIENTO_POR_DUPLICAR * (ln(max(1, conteo).toDouble()) / ln(2.0)).toFloat()
)

/** Un icono ya dibujado y dónde cae su coordenada, como fracción del bitmap. */
data class DibujoDelMarcador<T>(val icono: T, val anclaX: Float, val anclaY: Float)

/**
 * **Los iconos de los marcadores, dibujados una vez y reusados.**
 *
 * ## Por qué NO se usa `MarkerComposable`
 *
 * Porque infla un `ComposeView` **por cada marcador**, sincrónico, en el hilo
 * principal. Con el caché por forma son unos pocos bitmaps pase lo que pase con
 * el número de puntos.
 *
 * ## Y por qué esto NO contradice la decisión de los 21 dp
 *
 * `MapaDeLaPuerta.kt` deja escrito que el pin **lo tiene que pintar el mapa**:
 * cuando lo pintaba la feature el golden midió **21 dp de corrimiento, unos 24
 * metros a zoom 17**. Lo prohibido es **Compose encima del mapa posicionado por
 * geometría de pantalla**. Un `Marker(icon = …)` está **anclado a un `LatLng`**
 * y lo posiciona el SDK. Por lo mismo, la píldora "Principal · 21" va **dentro
 * de este bitmap**, con el ancla en la punta de la gota.
 *
 * ## El número, y el defecto que tenía
 *
 * El número mide el **40 % del diámetro** (mínimo 11 sp), peso 700, cifras
 * tabulares. La versión anterior multiplicaba por la densidad **dos veces**
 * (`radio` ya estaba en píxeles): en el SM-A256E (densidad 3) salía un número
 * de ~105 px en un disco de 78 px. Lo guarda `ElNumeroCabeEnElDiscoTest`.
 *
 * ## El tema va en la clave
 *
 * Los colores salen de [PaletaDelMapa]; el `remember` de [recordarVariantes]
 * depende de la paleta, así que al pasar de claro a oscuro se tira el caché.
 */
// Un color por papel del marcador (aro, hueco, número, sombra…), todos de la paleta:
// agruparlos en un objeto sólo movería la lista larga a otro lado.
@Suppress("TooManyFunctions", "LongParameterList")
class VariantesDelMarcador<T : Any>(
    private val colorAro: Int,
    private val colorHueco: Int,
    private val colorNumero: Int,
    private val colorPildora: Int,
    private val colorTintaPildora: Int,
    private val colorDatoPildora: Int,
    private val densidad: Float,
    /**
     * Cómo se envuelve el bitmap para el SDK. Entra por parámetro porque
     * `BitmapDescriptorFactory` exige Play Services inicializado y bajo
     * Robolectric no lo está.
     */
    private val envolver: (Bitmap) -> T
) {
    private val cache = mutableMapOf<FormaDelMarcador, DibujoDelMarcador<T>>()

    /** Cuántos bitmaps se han dibujado de verdad: hace comprobable el caché. */
    var dibujados: Int = 0
        private set

    /** El icono de esta forma, dibujándolo la primera vez y reusándolo después. */
    fun de(forma: FormaDelMarcador): DibujoDelMarcador<T> = cache.getOrPut(forma) {
        dibujados++
        val (bitmap, ax, ay) = pintar(forma)
        DibujoDelMarcador(envolver(bitmap), ax, ay)
    }

    private fun dp(v: Float) = v * densidad

    /**
     * Pinta la forma y devuelve el bitmap con su ancla (en fracción).
     *
     * Visible para pruebas: `ElNumeroCabeEnElDiscoTest` mide la tinta del número.
     */
    internal fun pintar(forma: FormaDelMarcador): Triple<Bitmap, Float, Float> {
        val caja = cajaDeLaFigura(forma)
        val etiqueta = forma.etiqueta
        val pildora = etiqueta?.let { cajaDeLaPildora(it, caja, forma) }
        val margen = dp(MARGEN_DP)
        val izq = min(caja.left, pildora?.left ?: caja.left) - margen
        val arr = min(caja.top, pildora?.top ?: caja.top) - margen
        val der = max(caja.right, pildora?.right ?: caja.right) + margen
        val aba = max(caja.bottom, pildora?.bottom ?: caja.bottom) + margen
        val ancho = ceil(der - izq).toInt()
        val alto = ceil(aba - arr).toInt()
        val bitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(bitmap)
        lienzo.translate(-izq, -arr)
        when (forma.figura) {
            Figura.DISCO, Figura.HUECO -> pintarDisco(lienzo, forma)
            Figura.PIN -> pintarPin(lienzo, forma)
            Figura.ROMBO -> pintarRombo(lienzo, forma)
        }
        if (etiqueta != null && pildora != null) pintarPildora(lienzo, etiqueta, pildora)
        return Triple(bitmap, -izq / ancho, -arr / alto)
    }

    private fun diametroReal(forma: FormaDelMarcador) =
        if (forma.resaltado) DIAMETRO_RESALTADO else forma.diametroDp

    private fun cajaDeLaFigura(forma: FormaDelMarcador): RectF = when (forma.figura) {
        Figura.PIN -> RectF(-dp(PIN_ANCHO / 2), -dp(PIN_ALTO), dp(PIN_ANCHO / 2), 0f)
        Figura.ROMBO -> dp(
            ROMBO_LADO * sqrt(2f) / 2 + ARO_EXTERIOR
        ).let { r -> RectF(-r, -r, r, r) }
        else -> {
            val r = dp(diametroReal(forma) / 2 + if (forma.resaltado) HALO else ARO_EXTERIOR)
            RectF(-r, -r, r, r)
        }
    }

    private fun sombra(p: Paint, blurDp: Float, dyDp: Float, alfa: Int) {
        p.setShadowLayer(dp(blurDp), 0f, dp(dyDp), Color.argb(alfa, 0, 0, 0))
    }

    private fun pintarDisco(lienzo: Canvas, forma: FormaDelMarcador) {
        val d = diametroReal(forma)
        val r = dp(d / 2)
        val hueco = forma.figura == Figura.HUECO
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (forma.resaltado) {
            p.color = forma.color
            p.alpha = ALFA_HALO
            lienzo.drawCircle(0f, 0f, r + dp(HALO), p)
            p.alpha = OPACO
        }
        // Sombra suave: 0·4·10 al 18 % y 0·1·2 al 28 %.
        val exterior = if (hueco) r + dp(ARO_EXTERIOR) else r
        p.color = colorAro
        sombra(p, SOMBRA_LEJANA_BLUR, SOMBRA_LEJANA_DY, ALFA_SOMBRA_LEJANA)
        lienzo.drawCircle(0f, 0f, exterior, p)
        sombra(p, SOMBRA_CERCANA_BLUR, SOMBRA_CERCANA_DY, ALFA_SOMBRA_CERCANA)
        lienzo.drawCircle(0f, 0f, exterior, p)
        p.clearShadowLayer()
        if (hueco) {
            // Aro exterior blanco de 1.5 dp, borde de 2.5 dp del color, centro hueco.
            p.color = colorAro
            lienzo.drawCircle(0f, 0f, exterior, p)
            p.color = forma.color
            lienzo.drawCircle(0f, 0f, r, p)
            p.color = colorHueco
            lienzo.drawCircle(0f, 0f, r - dp(BORDE_HUECO), p)
        } else {
            // Aro blanco de 2 dp y relleno del color.
            p.color = colorAro
            lienzo.drawCircle(0f, 0f, r, p)
            p.color = forma.color
            lienzo.drawCircle(0f, 0f, r - dp(ARO), p)
        }
        val conteo = forma.conteo ?: return
        val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (hueco) forma.color else colorNumero
            textAlign = Paint.Align.CENTER
            // **Una sola multiplicación por la densidad.** El diámetro está en dp.
            textSize = max(TEXTO_MINIMO_SP, d * PROPORCION_DEL_TEXTO) * densidad
            typeface = Typeface.DEFAULT_BOLD
            fontFeatureSettings = "tnum"
        }
        val linea = (texto.descent() + texto.ascent()) / 2
        lienzo.drawText(conteo.toString(), 0f, -linea, texto)
    }

    private fun trazoDelPin(): Path = Path().apply {
        // El path del mock (viewBox 34×44), con el ancla en la punta inferior.
        moveTo(PIN_ANCHO / 2, PIN_PUNTA)
        cubicTo(PIN_ANCHO / 2, PIN_PUNTA, PIN_ORILLA, PIN_CURVA, PIN_ORILLA, PIN_CABEZA)
        arcTo(
            RectF(PIN_ORILLA, PIN_ORILLA, PIN_ANCHO - PIN_ORILLA, PIN_ANCHO - PIN_ORILLA),
            GRADOS_MEDIA_VUELTA,
            GRADOS_MEDIA_VUELTA,
            false
        )
        cubicTo(
            PIN_ANCHO - PIN_ORILLA,
            PIN_CURVA,
            PIN_ANCHO / 2,
            PIN_PUNTA,
            PIN_ANCHO / 2,
            PIN_PUNTA
        )
        close()
    }

    private fun pintarPin(lienzo: Canvas, forma: FormaDelMarcador) {
        lienzo.save()
        lienzo.translate(-dp(PIN_ANCHO / 2), -dp(PIN_ALTO))
        lienzo.scale(densidad, densidad)
        val path = trazoDelPin()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = forma.color }
        // drop-shadow 0·1·1.5 al 30 % y 0·4·6 al 18 %.
        val lejana = Color.argb(ALFA_SOMBRA_LEJANA, 0, 0, 0)
        p.setShadowLayer(PIN_SOMBRA_LEJANA_BLUR, 0f, PIN_SOMBRA_LEJANA_DY, lejana)
        lienzo.drawPath(path, p)
        p.setShadowLayer(PIN_SOMBRA_CERCANA_BLUR, 0f, 1f, Color.argb(ALFA_SOMBRA_PIN, 0, 0, 0))
        lienzo.drawPath(path, p)
        p.clearShadowLayer()
        lienzo.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = ARO
        p.color = colorAro
        lienzo.drawPath(path, p)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        lienzo.drawCircle(PIN_ANCHO / 2, PIN_CABEZA, PIN_OJO, p)
        lienzo.restore()
    }

    private fun pintarRombo(lienzo: Canvas, forma: FormaDelMarcador) {
        lienzo.save()
        lienzo.rotate(GRADOS_DEL_ROMBO)
        val m = dp(ROMBO_LADO / 2)
        val radio = dp(ROMBO_RADIO)
        val e = dp(ARO_EXTERIOR)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorAro }
        val exterior = RectF(-m - e, -m - e, m + e, m + e)
        sombra(
            p,
            SOMBRA_LEJANA_BLUR,
            SOMBRA_CERCANA_DY * FACTOR_SOMBRA_DEL_ROMBO,
            ALFA_SOMBRA_LEJANA
        )
        lienzo.drawRoundRect(exterior, radio + e, radio + e, p)
        p.clearShadowLayer()
        lienzo.drawRoundRect(exterior, radio + e, radio + e, p)
        p.color = forma.color
        lienzo.drawRoundRect(RectF(-m, -m, m, m), radio, radio, p)
        p.color = colorHueco
        val b = dp(BORDE_HUECO)
        lienzo.drawRoundRect(RectF(-m + b, -m + b, m - b, m - b), radio - b / 2, radio - b / 2, p)
        lienzo.restore()
    }

    private fun pincelDeTexto(color: Int, grueso: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = dp(PILDORA_TEXTO)
        typeface = if (grueso) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        fontFeatureSettings = "tnum"
    }

    private fun anchoDeLaPildora(e: EtiquetaDelMarcador): Float {
        val t = pincelDeTexto(colorTintaPildora, true).measureText(e.texto)
        val dato = pincelDeTexto(colorDatoPildora, false).measureText(e.dato)
        val punto = if (e.colorDelPunto != null) dp(PUNTO + HUECO_PILDORA) else 0f
        return dp(PILDORA_PADDING * 2) + punto + t + dp(HUECO_PILDORA) + dato
    }

    private fun cajaDeLaPildora(
        e: EtiquetaDelMarcador,
        figura: RectF,
        forma: FormaDelMarcador
    ): RectF {
        val ancho = anchoDeLaPildora(e)
        val alto = dp(PILDORA_ALTO)
        return when (e.posicion) {
            PosicionDeEtiqueta.DERECHA_DEL_PIN -> {
                val izq = figura.right + dp(SEPARACION_PILDORA)
                val centroY = -dp(PIN_CENTRO_PILDORA)
                RectF(izq, centroY - alto / 2, izq + ancho, centroY + alto / 2)
            }
            PosicionDeEtiqueta.ARRIBA -> {
                val abajo = -dp(diametroReal(forma) / 2) - dp(SEPARACION_PILDORA)
                RectF(-ancho / 2, abajo - alto, ancho / 2, abajo)
            }
            PosicionDeEtiqueta.ARRIBA_DERECHA -> {
                val izq = dp(DIAMETRO_RESALTADO / 2 + SEPARACION_PILDORA * 2)
                val abajo = -dp(PILDORA_ARRIBA_DERECHA)
                RectF(izq, abajo - alto, izq + ancho, abajo)
            }
        }
    }

    private fun pintarPildora(lienzo: Canvas, e: EtiquetaDelMarcador, caja: RectF) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorPildora }
        val radio = caja.height() / 2
        sombra(p, SOMBRA_PILDORA_BLUR, SOMBRA_PILDORA_DY, ALFA_SOMBRA_PILDORA)
        lienzo.drawRoundRect(caja, radio, radio, p)
        p.clearShadowLayer()
        lienzo.drawRoundRect(caja, radio, radio, p)
        var x = caja.left + dp(PILDORA_PADDING)
        val cy = caja.centerY()
        e.colorDelPunto?.let { c ->
            p.color = c
            lienzo.drawCircle(x + dp(PUNTO / 2), cy, dp(PUNTO / 2), p)
            x += dp(PUNTO + HUECO_PILDORA)
        }
        val tinta = pincelDeTexto(colorTintaPildora, true)
        val base = cy - (tinta.descent() + tinta.ascent()) / 2
        lienzo.drawText(e.texto, x, base, tinta)
        x += tinta.measureText(e.texto) + dp(HUECO_PILDORA)
        lienzo.drawText(e.dato, x, base, pincelDeTexto(colorDatoPildora, false))
    }

    companion object {
        private const val MARGEN_DP = 12f
        private const val ARO = 2f
        private const val ARO_EXTERIOR = 1.5f
        private const val BORDE_HUECO = 2.5f
        private const val HALO = 7f
        private const val ALFA_HALO = 66 // 26 %
        private const val OPACO = 255
        private const val DIAMETRO_RESALTADO = 40f

        /** 40 % del diámetro, como la regla "Número" del mock. */
        internal const val PROPORCION_DEL_TEXTO = 0.40f

        /** 11 sp de mínimo. */
        internal const val TEXTO_MINIMO_SP = 11f
        private const val SOMBRA_LEJANA_BLUR = 5f
        private const val SOMBRA_LEJANA_DY = 4f
        private const val ALFA_SOMBRA_LEJANA = 46 // 18 %
        private const val SOMBRA_CERCANA_BLUR = 1f
        private const val SOMBRA_CERCANA_DY = 1f
        private const val ALFA_SOMBRA_CERCANA = 71 // 28 %
        private const val ALFA_SOMBRA_PIN = 77 // 30 %
        private const val ALFA_SOMBRA_PILDORA = 46
        private const val SOMBRA_PILDORA_BLUR = 4f
        private const val SOMBRA_PILDORA_DY = 3f
        private const val PIN_ANCHO = 34f
        private const val PIN_ALTO = 44f
        private const val PIN_PUNTA = 42.5f
        private const val PIN_ORILLA = 3.5f
        private const val PIN_CURVA = 28.4f
        private const val PIN_CABEZA = 17f
        private const val PIN_OJO = 5f
        private const val PIN_CENTRO_PILDORA = 18f
        private const val ROMBO_LADO = 15f
        private const val ROMBO_RADIO = 4f
        private const val PILDORA_ALTO = 24f
        private const val PILDORA_PADDING = 9f
        private const val PILDORA_TEXTO = 12f
        private const val PILDORA_ARRIBA_DERECHA = 16f
        private const val PUNTO = 7f
        private const val HUECO_PILDORA = 5f
        private const val SEPARACION_PILDORA = 3f
    }
}

private const val DIAMETRO_MINIMO = 22f
private const val DIAMETRO_MAXIMO = 38f
private const val CRECIMIENTO_POR_DUPLICAR = 4f

private fun <T : Any> variantesCon(
    paleta: PaletaDelMapa,
    densidad: Float,
    envolver: (Bitmap) -> T
) = VariantesDelMarcador(
    colorAro = paleta.mkRing,
    colorHueco = paleta.mkHollow,
    colorNumero = Color.WHITE,
    colorPildora = paleta.ctl,
    colorTintaPildora = paleta.ink,
    colorDatoPildora = paleta.mut,
    densidad = densidad,
    envolver = envolver
)

/** Recuerda el caché, **rehaciéndolo cuando cambia la paleta** (claro/oscuro). */
@Composable
fun recordarVariantes(paleta: PaletaDelMapa): VariantesDelMarcador<BitmapDescriptor> {
    val densidad = LocalDensity.current.density
    return remember(paleta, densidad) {
        variantesCon(paleta, densidad, BitmapDescriptorFactory::fromBitmap)
    }
}

/** Lo mismo, como `ImageBitmap`, para las miniaturas de las cards (mismo dibujo que el mapa). */
@Composable
fun recordarMiniaturas(paleta: PaletaDelMapa): VariantesDelMarcador<ImageBitmap> {
    val densidad = LocalDensity.current.density
    return remember(paleta, densidad) { variantesCon(paleta, densidad) { it.asImageBitmap() } }
}
private const val GRADOS_MEDIA_VUELTA = 180f
private const val GRADOS_DEL_ROMBO = 45f
private const val PIN_SOMBRA_LEJANA_BLUR = 3f
private const val PIN_SOMBRA_LEJANA_DY = 4f
private const val PIN_SOMBRA_CERCANA_BLUR = 1.5f
private const val FACTOR_SOMBRA_DEL_ROMBO = 3
