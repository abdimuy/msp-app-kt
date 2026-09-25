package com.example.msp_app.feature.ubicacion.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlin.math.max

/**
 * **La forma del marcador**: qué clase de lugar es y si va resaltado.
 *
 * Es la llave del caché. Son pocas por diseño —cuatro tipos × resaltado sí/no—
 * y de ahí sale el ahorro: **un bitmap por forma, no uno por marcador**.
 */
data class FormaDelMarcador(
    val relleno: Boolean,
    val resaltado: Boolean,
    val conteo: Int
)

/**
 * **Los iconos de los marcadores, dibujados una vez y reusados.**
 *
 * ## Por qué NO se usa `MarkerComposable`
 *
 * Porque infla un `ComposeView` **por cada marcador**: lo cuelga del `MapView`
 * con `addView`, lo mide, lo posiciona y lo dibuja a un `Canvas`, todo
 * sincrónico, en el hilo principal, durante la composición. Con 40 puntos —y
 * medido el 2026-09-24, el 44 % de los clientes tiene 21 o más— son 40 inflados
 * de vista para dibujar 40 cosas casi iguales.
 *
 * Peor para esta pantalla en concreto: ese bitmap está memorizado **por clave**,
 * y aquí la opacidad depende de la antigüedad, así que **cada cambio de filtro
 * los rehace todos**.
 *
 * Con el caché por forma son ~8 bitmaps, pase lo que pase con el número de
 * puntos. Y de paso desaparece `@MapsComposeExperimentalApi`.
 *
 * ## Y por qué esto NO contradice la decisión de los 21 dp
 *
 * `MapaDeLaPuerta.kt:135-141` deja escrito que el pin **lo tiene que pintar el
 * mapa**, porque cuando lo pintaba la feature el golden midió **21 dp de
 * corrimiento, unos 24 metros a zoom 17**.
 *
 * Lo que esa decisión prohíbe es **una capa de Compose encima del mapa
 * posicionada por geometría de pantalla** — un pin puesto "en el centro del
 * cuadro" suponiendo que ahí cae el objetivo de la cámara, que es lo que no
 * caía. Un `Marker(icon = …)` está **anclado a un `LatLng`** y lo posiciona el
 * SDK: es exactamente lo que la decisión exige, sólo que con nuestro dibujo en
 * vez del de Google. El KDoc de `PuntoDeLaPuerta` ya usaba el mismo criterio con
 * `MarkerComposable`; lo único que cambia aquí es de dónde sale el bitmap.
 *
 * ## El tema va en la clave, y no es un detalle
 *
 * Los colores salen del tema. Si el caché no llevara el tema en su clave, al
 * pasar de claro a oscuro los marcadores se quedarían con los colores
 * anteriores — un mapa nocturno con pines del tema claro. Por eso el `remember`
 * de [recordarVariantes] depende de los colores y no sólo del tipo.
 */
class VariantesDelMarcador<T : Any>(
    private val colorPuerta: Int,
    private val colorCompartido: Int,
    private val colorTransferencia: Int,
    private val colorTexto: Int,
    private val densidad: Float,
    /**
     * Cómo se envuelve el bitmap para el SDK de mapas.
     *
     * Entra por parámetro **porque `BitmapDescriptorFactory` exige que Play
     * Services esté inicializado** y bajo Robolectric no lo está: intentarlo
     * lanza `IBitmapDescriptorFactory is not initialized`. Sin esta costura, lo
     * único que de verdad importa medir —**cuántos bitmaps se dibujan**— sería
     * imposible de probar sin un aparato.
     *
     * La costura está en el borde exacto: **dibujar** es trabajo de Android
     * (`Canvas`, `Bitmap`) y se prueba; **envolver** es trabajo del SDK de
     * Google y no aporta nada a la afirmación.
     */
    private val envolver: (Bitmap) -> T
) {
    private val cache = mutableMapOf<FormaDelMarcador, T>()

    /**
     * Cuántos bitmaps se han dibujado de verdad.
     *
     * Es lo que hace **comprobable** la afirmación que justifica esta clase: que
     * el costo crece con el número de **formas**, no con el de marcadores. Sin
     * un contador, "el caché funciona" es una creencia sobre código que sí
     * compila.
     */
    var dibujados: Int = 0
        private set

    /** El icono de esta forma, dibujándolo la primera vez y reusándolo después. */
    fun de(forma: FormaDelMarcador, color: Int): T = cache.getOrPut(forma) {
        dibujados++
        dibujar(forma, color)
    }

    private fun dibujar(forma: FormaDelMarcador, color: Int): T = envolver(pintar(forma, color))

    private fun pintar(forma: FormaDelMarcador, color: Int): Bitmap {
        val lado = ((if (forma.resaltado) LADO_RESALTADO else LADO_NORMAL) * densidad).toInt()
        val bitmap = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(bitmap)
        val centro = lado / 2f
        val radio = centro - BORDE_PX * densidad

        val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
        if (forma.relleno) {
            pincel.color = color
            lienzo.drawCircle(centro, centro, radio, pincel)
        } else {
            // **Contorno y no relleno para las transferencias.** Un disco lleno
            // se lee como "aquí está"; un aro se lee como "aquí pasó algo".
            // La coordenada de una transferencia es cierta como hecho y falsa
            // como domicilio, y la forma tiene que decir esa diferencia sin una
            // leyenda que nadie va a leer.
            pincel.color = color
            pincel.style = Paint.Style.STROKE
            pincel.strokeWidth = GROSOR_DEL_ARO * densidad
            lienzo.drawCircle(centro, centro, radio - GROSOR_DEL_ARO * densidad / 2, pincel)
        }

        if (forma.resaltado) {
            pincel.style = Paint.Style.STROKE
            pincel.color = colorTexto
            pincel.strokeWidth = GROSOR_DEL_RESALTE * densidad
            lienzo.drawCircle(centro, centro, radio, pincel)
        }

        if (forma.conteo > 1) {
            val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = if (forma.relleno) colorTexto else color
                textAlign = Paint.Align.CENTER
                textSize = max(TAMANO_MINIMO_TEXTO, radio) * densidad * PROPORCION_DEL_TEXTO
                typeface = Typeface.DEFAULT_BOLD
            }
            val linea = (texto.descent() + texto.ascent()) / 2
            lienzo.drawText(forma.conteo.toString(), centro, centro - linea, texto)
        }
        return bitmap
    }

    companion object {
        private const val LADO_NORMAL = 30f
        private const val LADO_RESALTADO = 40f
        private const val BORDE_PX = 2f
        private const val GROSOR_DEL_ARO = 2.5f
        private const val GROSOR_DEL_RESALTE = 2f
        private const val PROPORCION_DEL_TEXTO = 0.9f
        private const val TAMANO_MINIMO_TEXTO = 8f
    }
}

/**
 * Recuerda el caché, **rehaciéndolo cuando cambian los colores del tema**.
 *
 * Las cuatro claves del `remember` son los cuatro colores: es lo que garantiza
 * que un cambio de claro a oscuro tire los bitmaps viejos. Ver el KDoc de
 * [VariantesDelMarcador].
 */
@Composable
fun recordarVariantes(
    puerta: Color,
    compartido: Color,
    transferencia: Color,
    texto: Color
): VariantesDelMarcador<BitmapDescriptor> {
    val densidad = LocalDensity.current.density
    return remember(puerta, compartido, transferencia, texto, densidad) {
        VariantesDelMarcador(
            envolver = BitmapDescriptorFactory::fromBitmap,
            colorPuerta = puerta.toArgb(),
            colorCompartido = compartido.toArgb(),
            colorTransferencia = transferencia.toArgb(),
            colorTexto = texto.toArgb(),
            densidad = densidad
        )
    }
}
