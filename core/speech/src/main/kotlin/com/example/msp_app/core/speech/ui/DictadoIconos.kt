package com.example.msp_app.core.speech.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Los dos glifos del dictado, **a trazo**, transcritos del mock.
 *
 * ## Por qué no un punto ni un círculo
 *
 * La primera versión pintaba un punto de marca en reposo y un círculo blanco al
 * escuchar. El golden lo destapó: **un círculo es el glifo universal de
 * "grabar"**, o sea que el botón decía "empieza" justo cuando ya estaba
 * grabando — la forma sugiriendo lo contrario de lo que el comportamiento hace
 * (principio 2). Ahora reposo es un **micrófono** y escuchar es un **cuadrado**,
 * que son los dos glifos que el mock dibuja.
 *
 * Mismo método que `VisitasIconos` y `PagosIconos`: se transcribe el path en vez
 * de arrastrar `material-icons-extended`, que pesa por dos glifos.
 */
internal object DictadoIconos {

    /** El micrófono del mock: cápsula, arco y pie. */
    val Microfono: ImageVector = trazado("microfono", CAPSULA, ARCO, PIE)

    /**
     * El cuadrado de detener. Relleno, no a trazo: es la única forma de que se
     * lea sobre el fondo oscuro del botón encendido sin competir con las
     * barritas de al lado.
     */
    val Detener: ImageVector = relleno("detener", CUADRADO)
}

/** El grosor del trazo, el mismo `stroke-width="2"` del mock. */
private const val GROSOR_DEL_TRAZO = 2f

/** Dimensión estándar de los íconos Material. */
private val LADO = 24.dp

/** Viewport estándar de los paths Material 24×24. */
private const val VIEWPORT = 24f

private fun trazado(nombre: String, vararg lineas: String): ImageVector {
    val builder = ImageVector.Builder(
        name = "dictado_$nombre",
        defaultWidth = LADO,
        defaultHeight = LADO,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT
    )
    lineas.forEach { linea ->
        builder.addPath(
            pathData = PathParser().parsePathString(linea).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = GROSOR_DEL_TRAZO,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
    return builder.build()
}

private fun relleno(nombre: String, pathData: String): ImageVector = ImageVector.Builder(
    name = "dictado_$nombre",
    defaultWidth = LADO,
    defaultHeight = LADO,
    viewportWidth = VIEWPORT,
    viewportHeight = VIEWPORT
).addPath(
    pathData = PathParser().parsePathString(pathData).toNodes(),
    fill = SolidColor(Color.Black)
).build()

/** `<rect x="9" y="3" width="6" height="11" rx="3"/>` del mock, como path. */
private const val CAPSULA = "M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z"

/** `<path d="M5.5 11.5a6.5 6.5 0 0 0 13 0"/>` */
private const val ARCO = "M5.5 11.5a6.5 6.5 0 0 0 13 0"

/** `<path d="M12 18v3"/>` */
private const val PIE = "M12 18v3"

/** `<rect x="6" y="6" width="12" height="12" rx="3"/>` del mock, como path. */
private const val CUADRADO =
    "M9 6h6a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3H9a3 3 0 0 1-3-3V9a3 3 0 0 1 3-3z"
