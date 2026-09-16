package com.example.msp_app.core.mapas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme

/** `testTag` del pin que pinta el mapa. */
const val PIN_DEL_MAPA_TAG: String = "mapa_pin"

/**
 * **El pin, pintado por quien manda la cámara.**
 *
 * ## Por qué vive acá y no en la pantalla que usa el mapa
 *
 * Porque el pin dice *"es aquí"* y eso solo es verdad si cae exactamente sobre
 * el punto que la cámara centró. El golden de `MapaConAtribucionScreenshotTest`
 * midió la primera versión —el pin lo pintaba `:feature:pagos`, centrado en su
 * banda superior— y estaba **21 dp por encima** del centro de la vista. A zoom
 * 17 eso son ~24 metros: media cuadra. Un pin que señala media cuadra de más es
 * un dato FALSO, no uno impreciso.
 *
 * Con el pin acá, el centro de la caja del lienzo **es** el objetivo de la
 * cámara, y la coincidencia deja de depender de que dos módulos mantengan la
 * misma aritmética de bandas. No hay desplazamiento que calcular, así que no hay
 * desplazamiento que equivocar.
 *
 * Es el mismo dibujo que `PinDelCobro` de `:feature:pagos` —mismo halo, mismo
 * punto, mismos tokens— porque es el mismo pin: aquélla sigue existiendo para
 * cuando NO hay mapa, donde el pin es un símbolo y no una coordenada.
 */
@Composable
fun PinDelMapa(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .testTag(PIN_DEL_MAPA_TAG)
            .size(HALO_DEL_PIN)
            .clip(MspTheme.shapes.chip)
            .background(MspTheme.colors.brandTint),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(PUNTO_DEL_PIN)
                .clip(MspTheme.shapes.chip)
                .background(MspTheme.colors.brand)
        )
    }
}

/** El halo del pin. Los mismos 34 dp de `PinDelCobro`. */
private val HALO_DEL_PIN = 34.dp

/** El punto de marca dentro del halo. */
private val PUNTO_DEL_PIN = 14.dp
