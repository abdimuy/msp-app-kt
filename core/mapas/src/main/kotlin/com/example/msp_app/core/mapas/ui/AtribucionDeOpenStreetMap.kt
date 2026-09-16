package com.example.msp_app.core.mapas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.mapas.domain.ATRIBUCION_DE_OSM

/** `testTag` de la atribución. La usan los tests que prueban que no se puede quitar. */
const val ATRIBUCION_TAG: String = "mapa_atribucion_osm"

/**
 * **La atribución de OpenStreetMap. Obligatoria, no decorativa.**
 *
 * Los datos del extracto son de OpenStreetMap bajo **ODbL**, que exige el
 * crédito visible donde se muestran. No es una cortesía ni un detalle de
 * cumplimiento que se pueda dejar para después: es la condición bajo la cual
 * este módulo puede existir **sin llave, sin servidor y sin proveedor**. Quitar
 * esta línea no ahorra 14 dp, convierte el mapa en un uso que la licencia no
 * permite.
 *
 * ## Por qué vive dentro del suelo y no en quien lo llama
 *
 * Si la pintara la pantalla que usa el mapa, habría tantas atribuciones como
 * pantallas y bastaría olvidarse una vez. Acá está pegada al dibujo: **no hay
 * forma de pintar el mapa sin pintarla**, y `SueloDeLaRutaTest` lo prueba
 * capturando el suelo con un lienzo de mentira.
 *
 * ## Por qué lleva su propio fondo
 *
 * Porque debajo hay un mapa, y un mapa no tiene un color: puede haber un lago
 * azul, un parque verde o una manzana de edificios. Un texto sin fondo se
 * volvería ilegible según lo que hubiera detrás, y una atribución que no se lee
 * no atribuye nada. La pastilla opaca del tema es lo que hace que el crédito se
 * lea sobre cualquier tesela.
 */
@Composable
fun AtribucionDeOpenStreetMap(modifier: Modifier = Modifier) {
    Text(
        text = ATRIBUCION_DE_OSM,
        style = MspTheme.type.caption,
        color = MspTheme.colors.onSurfaceMuted,
        maxLines = 1,
        modifier = modifier
            .testTag(ATRIBUCION_TAG)
            .clip(MspTheme.shapes.chip)
            .background(MspTheme.colors.surface)
            .padding(horizontal = MspTheme.spacing.xs + 2.dp, vertical = 2.dp)
    )
}
