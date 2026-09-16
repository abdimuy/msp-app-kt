package com.example.msp_app.core.mapas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.mapas.domain.PaletaDelMapa
import java.util.Locale

/**
 * La paleta del mapa, **derivada del design system**.
 *
 * Ningún hexadecimal escrito a mano: si el cobrador cambia la app a oscuro, el
 * mapa cambia con ella, porque los cinco colores salen de los mismos tokens que
 * pintan la tarjeta que lo rodea. Una tabla de colores propia habría dejado al
 * mapa como la única superficie que no obedece al toggle.
 *
 * El reparto de tokens:
 * - **tierra** `surface2` — el mismo suelo liso que se pinta sin extracto, así
 *   que el mapa no "aparece" con un fondo de otro color;
 * - **suelo** (parques, `landuse`) `statusPaidTint`, el verde tenue;
 * - **agua** `statusInfoTint`, el azul tenue;
 * - **edificios** `outline`, que es el gris de separación del tema;
 * - **calles** `onSurfaceMuted` al 55 % de opacidad (la pone el estilo): el
 *   único token que contrasta en los dos temas sin gritar.
 */
@Composable
@ReadOnlyComposable
fun paletaDelMapa(): PaletaDelMapa = PaletaDelMapa(
    tierra = MspTheme.colors.surface2.enHex(),
    suelo = MspTheme.colors.statusPaidTint.enHex(),
    agua = MspTheme.colors.statusInfoTint.enHex(),
    edificios = MspTheme.colors.outline.enHex(),
    calles = MspTheme.colors.onSurfaceMuted.enHex()
)

/** `#RRGGBB` — el formato que entiende la especificación de estilos de MapLibre. */
private fun Color.enHex(): String = String.format(Locale.US, "#%06X", toArgb() and SOLO_RGB)

private const val SOLO_RGB = 0xFFFFFF
