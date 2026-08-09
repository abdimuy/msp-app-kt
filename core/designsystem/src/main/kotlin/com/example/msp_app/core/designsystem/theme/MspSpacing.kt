package com.example.msp_app.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tokens de espaciado del design system Msp. Fuente única de márgenes/gaps:
 * ningún otro archivo del módulo (ni de la app, una vez migrada) hardcodea un
 * `NNdp` de layout — todo lector pasa por `MspTheme.spacing`
 * (`theme/MspTheme.kt`, Task 5).
 *
 * Transcrito 1:1 de `CampoSpacing` (kollect-app, ver
 * `.superpowers/research/kollect-app-designsystem.md` §4). No existe una
 * escala de elevación: se aplica ad hoc por componente (Task 5+).
 */
@Immutable
class MspSpacing internal constructor() {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp

    /**
     * Mínimo para targets táctiles primarios: uso a una mano, caminando, al
     * sol — defiende el acuerdo de accesibilidad de 48-56dp por el extremo
     * alto, no el mínimo legal.
     */
    val touchTarget: Dp = 56.dp
}
