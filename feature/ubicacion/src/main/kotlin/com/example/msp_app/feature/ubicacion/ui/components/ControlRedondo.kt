package com.example.msp_app.feature.ubicacion.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme

/**
 * Un control circular flotando sobre el mapa.
 *
 * [TOQUE_MINIMO] son los 50 dp del repo, más estrictos que los 48 de Material,
 * y aquí importan el doble: sobre un mapa un toque que falla no hace nada
 * visible y el cobrador no sabe si tocó mal o si la app se colgó.
 */
@Composable
fun ControlRedondo(
    icono: ImageVector,
    descripcion: String,
    onClick: () -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = MspTheme.colors.surface,
        shape = RoundedCornerShape(percent = 50),
        shadowElevation = SOMBRA_DEL_CONTROL,
        modifier = modifier.size(TOQUE_MINIMO).testTag(etiqueta)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icono,
                contentDescription = descripcion,
                tint = MspTheme.colors.onSurface,
                modifier = Modifier.size(GLIFO_DEL_CONTROL)
            )
        }
    }
}

private val TOQUE_MINIMO = 50.dp
private val GLIFO_DEL_CONTROL = 22.dp
private val SOMBRA_DEL_CONTROL = 3.dp

private fun glifo(vararg trazos: String): ImageVector = ImageVector.Builder(
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
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
}.build()

/**
 * La mira de "ver todos los puntos".
 *
 * Se dibuja a mano, como el resto de los glifos de la app, y no se toma de los
 * iconos extendidos de Material: ésos son una dependencia entera (miles de
 * vectores) por dos dibujos, y el catálogo básico no trae ninguna mira.
 */
val CRUZ_DE_CENTRAR: ImageVector = glifo(
    "M6,12 A6,6 0 1,1 18,12 A6,6 0 1,1 6,12",
    "M12,2 L12,5 M12,19 L12,22 M2,12 L5,12 M19,12 L22,12"
)

/** La flecha de volver, por el mismo criterio que la mira. */
val FLECHA_DE_VOLVER: ImageVector = glifo("M19,12 L5,12 M11,6 L5,12 L11,18")
