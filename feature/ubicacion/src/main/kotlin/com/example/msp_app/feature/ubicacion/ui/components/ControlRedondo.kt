package com.example.msp_app.feature.ubicacion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.feature.ubicacion.ui.PaletaDelMapa
import com.example.msp_app.feature.ubicacion.ui.estilo
import com.example.msp_app.feature.ubicacion.ui.paletaDelMapa

/** El botón redondo del mock (`.ctl`): fondo de control y sombra. */
@Composable
fun ControlRedondo(
    icono: ImageVector,
    descripcion: String,
    onClick: () -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    tinte: Color? = null
) {
    val p = paletaDelMapa()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(LADO)
            .shadow(SOMBRA, CircleShape)
            .clip(CircleShape)
            .background(p.c(p.ctl))
            .clickable(onClick = onClick)
            .testTag(etiqueta)
    ) {
        Icon(icono, descripcion, tint = tinte ?: p.c(p.ink), modifier = Modifier.size(GLIFO))
    }
}

/** `testTag` del botón "Filtrar". */
const val ABRIR_FILTROS_TAG: String = "ubicacion_abrir_filtros"

/** La píldora "Filtrar" de arriba a la derecha; enseña un punto si hay algo puesto. */
@Composable
fun BotonFiltrar(filtrado: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = paletaDelMapa()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .height(LADO)
            .shadow(SOMBRA, CircleShape)
            .clip(CircleShape)
            .background(p.c(p.ctl))
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 18.dp)
            .testTag(ABRIR_FILTROS_TAG)
    ) {
        Icon(GLIFO_FILTRO, null, tint = p.c(p.ink), modifier = Modifier.size(GLIFO))
        Text("Filtrar", style = estilo(14f, 650, 20f), color = p.c(p.ink))
        if (filtrado) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(p.c(p.brand)))
        }
    }
}

/** `testTag` del aviso de puntos lejos. */
const val PUNTOS_LEJOS_TAG: String = "ubicacion_puntos_lejos"

/** "2 puntos lejos · Ver": los sueltos que quedaron fuera de la vista. */
@Composable
fun AvisoDePuntosLejos(cuantos: Int, onVer: () -> Unit, modifier: Modifier = Modifier) {
    val p = paletaDelMapa()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .height(32.dp)
            .shadow(SOMBRA, CircleShape)
            .clip(CircleShape)
            .background(p.c(p.ctl))
            .clickable(onClick = onVer)
            .padding(start = 10.dp, end = 12.dp)
            .testTag(PUNTOS_LEJOS_TAG)
    ) {
        Icon(GLIFO_LEJOS, null, tint = p.c(p.mut), modifier = Modifier.size(15.dp))
        Text(
            buildAnnotatedString {
                append(if (cuantos == 1) "1 punto lejos · " else "$cuantos puntos lejos · ")
                withStyle(
                    SpanStyle(color = p.c(p.brand), fontWeight = FontWeight.Bold)
                ) { append("Ver") }
            },
            style = estilo(12.5f, 600, 16f, tabular = true),
            color = p.c(p.ink)
        )
    }
}

/** El chip de orilla "Principal · 440 m" del lugar tocado (no apunta a una coordenada exacta). */
@Composable
fun ChipDeOrilla(distancia: String, modifier: Modifier = Modifier) {
    val p = paletaDelMapa()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .height(30.dp)
            .shadow(SOMBRA, CircleShape)
            .clip(CircleShape)
            .background(p.c(p.ctl))
            .padding(start = 6.dp, end = 10.dp)
    ) {
        Icon(GLIFO_ORILLA, null, tint = p.c(p.mkCasa), modifier = Modifier.size(16.dp))
        Text(
            buildAnnotatedString {
                append("Principal ")
                withStyle(
                    SpanStyle(color = p.c(p.mut), fontWeight = FontWeight(600))
                ) { append(distancia) }
            },
            style = estilo(12f, 700, 16f, tabular = true),
            color = p.c(p.ink)
        )
    }
}

/** La regla de escala ("100 m" y su corchete), a la izquierda de "Mi ubicación". */
@Composable
fun ReglaDeEscala(etiqueta: String, anchoDp: Dp, modifier: Modifier = Modifier) {
    val color = grisDelMapa(paletaDelMapa())
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        Text(etiqueta, style = estilo(10f, 400, 13f), color = color)
        Box(
            Modifier
                .padding(top = 1.dp)
                .width(anchoDp)
                .height(5.dp)
                .drawBehind {
                    val g = 1.5.dp.toPx()
                    val y = size.height - g / 2
                    drawLine(color, Offset(g / 2, 0f), Offset(g / 2, size.height), g)
                    drawLine(color, Offset(0f, y), Offset(size.width, y), g)
                    drawLine(
                        color,
                        Offset(size.width - g / 2, 0f),
                        Offset(size.width - g / 2, size.height),
                        g
                    )
                }
        )
    }
}

/** El gris de las etiquetas del mapa del mock (`--mapLbl`). */
fun grisDelMapa(p: PaletaDelMapa): Color =
    if (p == PaletaDelMapa.OSCURO) Color(GRIS_MAPA_OSCURO) else Color(GRIS_MAPA_CLARO)

private const val GRIS_MAPA_OSCURO = 0xFF6C7872
private const val GRIS_MAPA_CLARO = 0xFF7A857F

/**
 * 50 dp y no los 48 del mock: el mínimo tocable del repo, que cobra
 * `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest`. El mínimo no se baja.
 */
private val LADO = 50.dp
private val GLIFO = 22.dp
private val SOMBRA = 4.dp

internal fun glifo(vararg trazos: String, grosor: Float = 2f): ImageVector = ImageVector.Builder(
    name = "glifo_de_ubicacion",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    trazos.forEach { trazo ->
        addPath(
            pathData = PathParser().parsePathString(trazo).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = grosor,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
}.build()

private fun circulo(cx: Float, cy: Float, r: Float) =
    "M${cx - r},$cy A$r,$r 0 1,1 ${cx + r},$cy A$r,$r 0 1,1 ${cx - r},$cy"

/** La flecha de volver del mock. */
val FLECHA_DE_VOLVER: ImageVector = glifo("M19 12H5M12 19l-7-7 7-7")
val GLIFO_FILTRO: ImageVector = glifo("M4 6h16M7 12h10M10 18h4")
val GLIFO_LEJOS: ImageVector = glifo("M7 17 17 7M9 7h8v8")
val GLIFO_ORILLA: ImageVector = glifo("M17 17 7 7M7 15V7h8", grosor = 2.4f)
val GLIFO_NAVEGAR: ImageVector = glifo("M3 11 22 2l-9 19-2-8-8-2Z")
val GLIFO_RELOJ: ImageVector = glifo(circulo(12f, 12f, 9f), "M12 7v5l3 2")
val GLIFO_CAMBIO: ImageVector = glifo("M3 12h14M13 6l6 6-6 6", grosor = 2.2f)
val GLIFO_PLEGADO: ImageVector = glifo("m6 9 6 6 6-6")
val GLIFO_ATRAS: ImageVector = glifo("m15 18-6-6 6-6")
val GLIFO_PALOMITA: ImageVector = glifo("m5 12 5 5L20 7", grosor = 2.6f)
val GLIFO_MI_UBICACION: ImageVector = glifo(
    circulo(12f, 12f, 7f),
    circulo(12f, 12f, 2.6f),
    "M12 2v3M12 19v3M2 12h3M19 12h3"
)
