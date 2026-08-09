package com.example.msp_app.core.designsystem.component

import androidx.compose.runtime.Immutable

/**
 * Un día del ciclo de [MspWeeklyBarsCard] — [fraction] normalizada en
 * `[0,1]` (el caller ya la calcula contra el máximo del ciclo; el componente
 * no conoce el dominio de "cobrado").
 */
@Immutable
data class MspWeeklyBar(val label: String, val fraction: Float)
