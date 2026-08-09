package com.example.msp_app.core.designsystem.component

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Contenedor plano del booleano `darkTheme` — el dueño del estado de tema que
 * el bridge de reveal ([ThemeRevealController], en este mismo archivo hermano
 * `ThemeRevealController.kt`) flipea vía [toggle] al completar/arrancar una
 * transición. 1:1 `CampoThemeState` (kollect §7.2) — sin lógica adicional: la
 * elección de si el flip dispara la reveal circular o el crossfade fallback
 * de [com.example.msp_app.core.designsystem.theme.MspTheme] vive en
 * [MspThemeToggle], no aquí.
 */
@Stable
class ThemeState(darkTheme: Boolean) {
    var darkTheme: Boolean by mutableStateOf(darkTheme)

    /** Invierte [darkTheme]. Lo dispara el composition root (`ThemeRevealRoot`, Plan 5) tras snapshotear la pantalla vieja, o directo [MspThemeToggle] cuando no hay host de reveal instalado. */
    fun toggle() {
        darkTheme = !darkTheme
    }
}
