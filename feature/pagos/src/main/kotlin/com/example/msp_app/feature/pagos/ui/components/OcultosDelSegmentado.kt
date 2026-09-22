package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.roundToInt

/*
 * La geometría de `ControlSegmentado`: qué se ve, qué no, y hasta dónde hay que
 * deslizar.
 *
 * Vive aparte del componible por la convención del repo de dividir antes que
 * suprimir el umbral `TooManyFunctions` de detekt, y el corte cae donde hay una
 * costura de verdad: aquí no se compone nada ni se lee el tema, son cajas contra
 * cajas. Todo lo de este archivo es aritmética sobre lo que el layout ya midió,
 * así que se puede razonar —y, si hiciera falta, probar— sin levantar una
 * composición.
 */

/**
 * Las dos cuentas que el control necesita, y **no son la misma**.
 *
 * - [cortados]: lo que el recorte de la ventana deja fuera. Decide si se pinta
 *   el degradado, porque el degradado existe para suavizar ese corte.
 * - [ilegibles]: lo anterior **más** lo que queda debajo del degradado. Decide
 *   lo que anuncia el aviso.
 *
 * La diferencia se vio en un golden y no se dedujo. A `GRANDE`, en la lista, la
 * caja del chip "Después" terminaba **dentro** de la ventana —así que por
 * geometría "se veía"— pero sus últimos 24dp caían bajo el degradado y su conteo
 * se desvanecía a blanco. El aviso decía "+1" (por "Pagados") mientras en
 * pantalla había **dos** datos que nadie podía leer. Un conteo desvanecido está
 * tan escondido como uno fuera de cuadro, así que la ventana con la que se
 * cuenta descuenta la orilla.
 *
 * El orden importa y no es circular: primero [cortados], con la ventana entera,
 * decide si hay degradado; sólo entonces la ventana se encoge para contar
 * [ilegibles]. Encoger sólo puede subir la cuenta, nunca bajarla a cero, así que
 * "hay degradado" y "el aviso dice algo" nunca se contradicen.
 */
internal data class Recuento(
    val cortados: Ocultos,
    val ilegibles: Ocultos,
    /**
     * La ventana ya descontada la orilla — el hueco donde de verdad se puede
     * leer. Es la referencia de `traeALaVista` además de la del conteo: dejar un
     * chip "dentro de la ventana" pero bajo el degradado sería traerlo a la
     * vista para que siga sin leerse.
     */
    val legible: Rect
)

internal fun recuenta(
    cuantas: Int,
    cajas: Map<Int, Rect>,
    ventana: Rect,
    holgura: Float,
    orilla: Float
): Recuento {
    val cortados = cuentaLosOcultos(cuantas, cajas, ventana, holgura)
    if (ventana.isEmpty) return Recuento(cortados, cortados, ventana)
    val legible = Rect(
        left = if (cortados.izquierda > 0) ventana.left + orilla else ventana.left,
        top = ventana.top,
        right = if (cortados.derecha > 0) ventana.right - orilla else ventana.right,
        bottom = ventana.bottom
    )
    return Recuento(cortados, cuentaLosOcultos(cuantas, cajas, legible, holgura), legible)
}

/**
 * Cuántas opciones quedan **fuera de la ventana**, por costado.
 *
 * Un chip cuenta como escondido si su caja sin recortar se sale de la ventana
 * aunque sea un píxel: media palabra es un dato falso, así que "se ve a medias"
 * y "no se ve" valen lo mismo. [holgura] absorbe sólo el redondeo a píxel.
 *
 * Un chip que todavía no reportó su caja —el primer frame— no cuenta: el aviso
 * aparece un frame después, no con un número inventado.
 */
private fun cuentaLosOcultos(
    cuantas: Int,
    cajas: Map<Int, Rect>,
    ventana: Rect,
    holgura: Float
): Ocultos {
    if (ventana.isEmpty) return SIN_OCULTOS
    var izquierda = 0
    var derecha = 0
    for (indice in 0 until cuantas) {
        val caja = cajas[indice] ?: continue
        when {
            caja.left < ventana.left - holgura -> izquierda++
            caja.right > ventana.right + holgura -> derecha++
        }
    }
    return Ocultos(izquierda, derecha)
}

/** Las opciones escondidas a cada lado de la ventana. */
internal data class Ocultos(val izquierda: Int, val derecha: Int)

private val SIN_OCULTOS = Ocultos(0, 0)

/**
 * La primera opción escondida de ese costado — la que el aviso promete traer.
 *
 * Trae **una**, no todas: saltar hasta el extremo le quita al cobrador la
 * referencia de dónde estaba. Si quedan más, el aviso sigue ahí con el número
 * actualizado.
 */
internal fun primeroEscondido(
    cuantas: Int,
    cajas: Map<Int, Rect>,
    ventana: Rect,
    haciaLaDerecha: Boolean
): Int {
    val indices = if (haciaLaDerecha) 0 until cuantas else (cuantas - 1) downTo 0
    return indices.firstOrNull { indice ->
        val caja = cajas[indice] ?: return@firstOrNull false
        if (haciaLaDerecha) caja.right > ventana.right else caja.left < ventana.left
    } ?: -1
}

/** La caja **sin recortar** de un nodo, en coordenadas de la raíz. */
internal fun LayoutCoordinates.cajaEnRaiz(): Rect = Rect(
    offset = positionInRoot(),
    size = Size(size.width.toFloat(), size.height.toFloat())
)

/**
 * A cuánto scroll hay que ir para que [caja] quepa entera en [ventana].
 *
 * Devuelve el valor actual del scroll cuando no hay nada que mover —o todavía no
 * hay medidas—, que es como quien llama detecta "ya está" sin una bandera
 * aparte.
 */
internal fun destinoPara(caja: Rect?, ventana: Rect, scroll: ScrollState): Int {
    if (caja == null || ventana.isEmpty) return scroll.value
    val desplazamiento = when {
        caja.right > ventana.right -> caja.right - ventana.right
        caja.left < ventana.left -> caja.left - ventana.left
        else -> return scroll.value
    }
    return (scroll.value + desplazamiento).roundToInt().coerceIn(0, scroll.maxValue)
}
