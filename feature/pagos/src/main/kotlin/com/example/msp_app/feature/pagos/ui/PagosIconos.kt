package com.example.msp_app.feature.pagos.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Los **diez** glifos de estado de la cobranza, dibujados a TRAZO.
 *
 * ## Por qué trazo y no relleno
 *
 * Los nueve glifos anteriores eran todos de relleno —`grep -c stroke` sobre este
 * archivo devolvía 0—, así que el aspecto de "contorno" de la cita y de "sin
 * trabajar" era en realidad un **dónut relleno**, no una línea. Kollect declara
 * `strokeLineWidth` ícono por ícono (`CampoIcons.kt:16-17,24`) y esa es la
 * diferencia estructural entre los dos juegos, no el dibujo.
 *
 * El grosor se declara **por glifo** y no con una constante única: un tache de
 * dos líneas aguanta más peso que un calendario con seis, y a 11.88dp esa
 * diferencia es la que decide si se lee o se emborrona.
 *
 * ## Por qué ahora son diez y no nueve
 *
 * `SinDatoQueLoSostenga` servía a DOS estados a la vez —"prometió sin fecha" y
 * "cita sin hora"— con el mismo triángulo de advertencia, así que en pantalla
 * los dos se veían idénticos. Se parten: [PrometioSinFecha] es un calendario sin
 * día y [CitaSinHora] un reloj sin manecillas. Cada estado tiene por fin su
 * glifo.
 *
 * Y [NoEstaba] pasa a ser una casa **tachada**: la casa sola decía "casa", no
 * "no había nadie". Kollect usa `HouseSlash` por la misma razón.
 *
 * ## Dónde se miden
 *
 * El tamaño más chico en que se pintan es **11.88dp** —`CuadroDeEstado` de 22dp
 * por `PiezasComunes.kt:61`, en las dos pantallas de detalle— y **14dp** en el
 * chip de estado de la lista. Un trazo de 2.0 sobre 24 unidades mide 0.99dp a
 * 11.88, por debajo de un píxel en densidad 1.0; por eso los glifos con más
 * líneas suben de grosor.
 *
 * ## Deuda conocida, y NO se toca aquí
 *
 * Hay tres objetos de íconos con paths copiados —`MspIcons` (internal al design
 * system), este y `VisitasIconos`—. La salida correcta es que un trabajo del
 * design system publique `MspIcons`; unificarlos desde aquí es trabajo propio y
 * está fuera de este cambio.
 */
internal object PagosIconos {

    /** Pagó → palomita. Dos trazos, aguanta el grosor alto. */
    val Pago: ImageVector = trazo("pago", GROSOR_GRUESO, CHECK)

    /** Abonó parcial → círculo con la mitad derecha llena. */
    val Parcial: ImageVector = trazo("parcial", GROSOR_NORMAL, CIRCULO, relleno = MEDIA_LUNA)

    /** Visité, vuelvo → flecha de regreso. */
    val Vuelvo: ImageVector = trazo("vuelvo", GROSOR_NORMAL, ARCO_DE_REGRESO, PUNTA_DE_REGRESO)

    /** Prometió CON fecha → calendario con su día marcado. */
    val Prometio: ImageVector =
        trazo("prometio", GROSOR_FINO, MARCO_CALENDARIO, GRAPAS, RENGLON, relleno = DIA_MARCADO)

    /**
     * Prometió SIN fecha → calendario **sin día**.
     *
     * Antes compartía el triángulo de advertencia con [CitaSinHora]. Un
     * compromiso al que le falta el dato que lo sostiene no es un pendiente
     * genérico: es una promesa a la que le falta el DÍA, y el glifo lo dice.
     */
    val PrometioSinFecha: ImageVector =
        trazo("prometio_sin_fecha", GROSOR_FINO, MARCO_CALENDARIO, GRAPAS, RENGLON, HUECO_DEL_DIA)

    /**
     * Cita sin hora → reloj **sin manecillas**.
     *
     * El otro mitad del triángulo que servía a dos estados. Aquí lo que falta es
     * la HORA, y un reloj con marcas pero sin manecillas lo dice sin texto.
     */
    val CitaSinHora: ImageVector =
        trazo("cita_sin_hora", GROSOR_NORMAL, CIRCULO, MARCAS_DEL_RELOJ)

    /** Se negó → tache. */
    val Negado: ImageVector = trazo("negado", GROSOR_GRUESO, TACHE)

    /** Cita → reloj con manecillas. */
    val Cita: ImageVector = trazo("cita", GROSOR_NORMAL, CIRCULO, MANECILLAS)

    /** No estaba → casa **tachada**. La casa sola decía "casa". */
    val NoEstaba: ImageVector =
        trazo("no_estaba", GROSOR_NORMAL, TECHO, PAREDES, TACHADURA)

    /** Sin trabajar → anillo vacío. */
    val SinTocar: ImageVector = trazo("sin_tocar", GROSOR_NORMAL, CIRCULO)
}

/**
 * Construye un glifo de 24×24 a trazo, con el grosor que le toca.
 *
 * [relleno] es la excepción medida: la media luna de "abonó parcial" es una
 * superficie, no una línea, y dibujarla con trazo la convertiría en dos arcos.
 */
private fun trazo(
    nombre: String,
    grosor: Float,
    vararg lineas: String,
    relleno: String? = null
): ImageVector {
    val builder = ImageVector.Builder(
        name = "pagos_$nombre",
        defaultWidth = ICON_DIMENSION,
        defaultHeight = ICON_DIMENSION,
        viewportWidth = ICON_VIEWPORT,
        viewportHeight = ICON_VIEWPORT
    )
    relleno?.let {
        builder.addPath(
            pathData = PathParser().parsePathString(it).toNodes(),
            fill = SolidColor(Color.Black)
        )
    }
    lineas.forEach { linea ->
        builder.addPath(
            pathData = PathParser().parsePathString(linea).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = grosor,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
    return builder.build()
}

/** Dimensión estándar de los íconos Material. */
private val ICON_DIMENSION = 24.dp

/** Viewport estándar de los paths Material 24×24. */
private const val ICON_VIEWPORT = 24f

/** Para glifos de muchas líneas (el calendario): más fino, o se emborrona. */
private const val GROSOR_FINO = 1.8f

/** El grosor de casa. */
private const val GROSOR_NORMAL = 2.0f

/** Para glifos de dos trazos sueltos (palomita y tache): aguantan más peso. */
private const val GROSOR_GRUESO = 2.4f

// ── Los trazos, en el viewport de 24×24 ──────────────────────────────────────

private const val CHECK = "M4,12.5 L9.5,18 L20,6.5"

private const val TACHE = "M6.5,6.5 L17.5,17.5 M17.5,6.5 L6.5,17.5"

/** Círculo completo, en dos arcos: es lo que `PathParser` acepta sin trucos. */
private const val CIRCULO = "M3,12 A9,9 0 1,1 21,12 A9,9 0 1,1 3,12"

/** La mitad derecha del mismo círculo, como superficie. */
private const val MEDIA_LUNA = "M12,3 A9,9 0 0,1 12,21 Z"

private const val ARCO_DE_REGRESO = "M20.5,12 A8.5,8.5 0 1,1 18.01,5.99"
private const val PUNTA_DE_REGRESO = "M20.5,3.5 L20.5,9 L15,9"

private const val MARCO_CALENDARIO =
    "M3,7.5 A2.5,2.5 0 0,1 5.5,5 L18.5,5 A2.5,2.5 0 0,1 21,7.5 " +
        "L21,18.5 A2.5,2.5 0 0,1 18.5,21 L5.5,21 A2.5,2.5 0 0,1 3,18.5 Z"

private const val GRAPAS = "M8,2.5 L8,7.5 M16,2.5 L16,7.5"

private const val RENGLON = "M3,10.5 L21,10.5"

/** El día marcado: un punto lleno. */
private const val DIA_MARCADO = "M12,14 A1.6,1.6 0 1,1 12,17.2 A1.6,1.6 0 1,1 12,14"

/** El día que falta: el renglón vacío donde iría el punto. */
private const val HUECO_DEL_DIA = "M9.5,15.6 L14.5,15.6"

/** Las cuatro marcas del reloj, sin manecillas. */
private const val MARCAS_DEL_RELOJ =
    "M12,6.4 L12,7.9 M17.6,12 L16.1,12 M12,17.6 L12,16.1 M6.4,12 L7.9,12"

private const val MANECILLAS = "M12,6.8 L12,12 L15.6,14.1"

private const val TECHO = "M4,11.2 L12,4.5 L20,11.2"
private const val PAREDES = "M6,12.8 L6,19.5 L18,19.5 L18,12.8"
private const val TACHADURA = "M3.2,3.2 L20.8,20.8"
