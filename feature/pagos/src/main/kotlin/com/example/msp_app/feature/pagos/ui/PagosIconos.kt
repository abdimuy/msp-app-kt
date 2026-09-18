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

    /** Falta pasar → anillo vacío: la cuenta está, y todavía no se fue. */
    val SinTocar: ImageVector = trazo("sin_tocar", GROSOR_NORMAL, CIRCULO)
}

/**
 * Los glifos de **acción** del detalle del cliente: llamar, WhatsApp, ficha,
 * producto, el pin del mapa y el chevron de "ver todos".
 *
 * Van en un objeto aparte de [PagosIconos] a propósito: aquél es el catálogo de
 * **estado** —diez glifos, uno por estado de cobranza, y esa correspondencia
 * uno-a-uno es lo que lo hace verificable—. Un icono de teléfono ahí dentro
 * rompería la cuenta y el próximo lector tendría que decidir cuáles son estados
 * y cuáles no.
 *
 * Comparten el constructor [trazo] porque comparten la decisión que importa: son
 * de **trazo**, no de relleno, con los mismos grosores y los mismos remates
 * redondeados. Los paths salen del lienzo de mockups, verbatim.
 */
internal object AccionesIconos {

    /** Llamar → bocina de teléfono. */
    val Llamar: ImageVector = trazo("llamar", GROSOR_NORMAL, BOCINA)

    /**
     * WhatsApp → globo de conversación con la bocina dentro.
     *
     * La bocina va en [GROSOR_FINO] y no en el del globo: a 18dp, dos trazos de
     * 2.0 tan cerca se empastan en una mancha y el glifo deja de leerse como
     * WhatsApp. Es la misma razón por la que el calendario de "prometió" es fino.
     */
    val WhatsApp: ImageVector =
        trazo("whatsapp", GROSOR_NORMAL, GLOBO, finas = listOf(BOCINA_CHICA))

    /** Ficha → lápiz, porque la ficha se ESCRIBE. */
    val Ficha: ImageVector = trazo("ficha", GROSOR_NORMAL, LAPIZ)

    /** Un producto de la venta → caja. */
    val Producto: ImageVector = trazo("producto", GROSOR_NORMAL, CAJA, TAPA_DE_LA_CAJA)

    /** El pin del mapa. */
    val Pin: ImageVector = trazo("pin", GROSOR_NORMAL, GOTA_DEL_PIN, OJO_DEL_PIN)

    /**
     * La casa del respaldo del cuadro de ubicación — ver `SueloDibujado`.
     *
     * Son los MISMOS dos trazos de [PagosIconos.NoEstaba] sin la tachadura, y
     * eso es a propósito: allá la casa tachada dice "fui y no estaba"; acá la
     * casa sola dice "una puerta", que es todo lo que se sabe cuando ningún
     * abono trajo coordenadas. Sin pin y sin calles, porque no hay punto medido
     * que señalar ni traza que dibujar.
     */
    val Casa: ImageVector = trazo("casa", GROSOR_NORMAL, TECHO, PAREDES)

    /** "Ver los N contactos" → chevron; también cierra cada renglón de la hoja de orígenes. */
    val Chevron: ImageVector = trazo("chevron", GROSOR_NORMAL, PUNTA_DE_CHEVRON)

    /**
     * Los tres orígenes de la hoja del «+» del comprobante: cámara, galería y
     * archivo. Paths del lienzo de mockups, verbatim, igual que el resto de este
     * objeto.
     */
    val Camara: ImageVector = trazo("camara", GROSOR_NORMAL, CUERPO_DE_CAMARA, LENTE_DE_CAMARA)

    /** Galería → marco con paisaje. Es la que deja escoger varias. */
    val Galeria: ImageVector =
        trazo("galeria", GROSOR_NORMAL, MARCO, SOL_DEL_MARCO, CERROS_DEL_MARCO)

    /** Archivo → hoja con la esquina doblada. Es el único que alcanza un PDF. */
    val Archivo: ImageVector = trazo("archivo", GROSOR_NORMAL, HOJA, ESQUINA_DE_LA_HOJA)
}

private const val CUERPO_DE_CAMARA =
    "M3 8.5A2 2 0 0 1 5 6.5h2l1.2-2h7.6L17 6.5h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"

private const val LENTE_DE_CAMARA = "M8.4 13a3.6 3.6 0 1 0 7.2 0a3.6 3.6 0 1 0-7.2 0"

private const val MARCO =
    "M5.5 4.5h13a2.5 2.5 0 0 1 2.5 2.5v10a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17V7" +
        "a2.5 2.5 0 0 1 2.5-2.5z"

private const val SOL_DEL_MARCO = "M6.9 10a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0"

private const val CERROS_DEL_MARCO = "M3.5 17l5-5 4.5 4.5 3-2.5 4.5 4"

private const val HOJA = "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"

private const val ESQUINA_DE_LA_HOJA = "M14 3v5h5"

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
    relleno: String? = null,
    finas: List<String> = emptyList()
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
    lineas.forEach { linea -> builder.trazar(linea, grosor) }
    // Un segundo grosor para el detalle interior de un glifo compuesto; ver el
    // KDoc de `AccionesIconos.WhatsApp`.
    finas.forEach { linea -> builder.trazar(linea, GROSOR_FINO) }
    return builder.build()
}

private fun ImageVector.Builder.trazar(linea: String, grosor: Float) {
    addPath(
        pathData = PathParser().parsePathString(linea).toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = grosor,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    )
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

// --- Paths de los glifos de acción, verbatim del lienzo de mockups -----------

private const val BOCINA =
    "M5,4 h4 l2,5 -2.5,1.5 a12,12 0 0 0 5,5 L15,13 l5,2 v4 " +
        "a1.5,1.5 0 0 1 -1.6,1.5 A16.5,16.5 0 0 1 3.5,5.6 A1.5,1.5 0 0 1 5,4 z"

private const val GLOBO = "M12,3 a9,9 0 0 0 -7.7,13.6 L3,21 l4.5,-1.2 A9,9 0 1 0 12,3 z"

private const val BOCINA_CHICA =
    "M8.7,9.2 c0.2,1.9 2.2,3.9 4.1,4.1 l0.9,-1.1 2,0.9 v1.3 c-2.6,0.5 -5.9,-2.4 -6.4,-5 h1.3 z"

private const val LAPIZ = "M4,20 h4 L20,8 l-4,-4 L4,16 v4 z"

private const val CAJA = "M3,8 l9,-4 9,4 v8 l-9,4 -9,-4 z"

private const val TAPA_DE_LA_CAJA = "M3,8 l9,4 9,-4 M12,12 v8"

private const val GOTA_DEL_PIN = "M12,21 c0,0 7,-6.2 7,-11 a7,7 0 1 0 -14,0 c0,4.8 7,11 7,11 z"

private const val OJO_DEL_PIN = "M9.6,10 A2.4,2.4 0 1,1 14.4,10 A2.4,2.4 0 1,1 9.6,10"

private const val PUNTA_DE_CHEVRON = "M9,5 l7,7 -7,7"
