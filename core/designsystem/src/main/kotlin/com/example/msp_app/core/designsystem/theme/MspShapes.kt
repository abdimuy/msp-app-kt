package com.example.msp_app.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Tokens de forma del design system Msp. Fuente única de esquinas: ningún
 * otro archivo del módulo (ni de la app, una vez migrada) hardcodea un
 * `RoundedCornerShape(NNdp)` de marca — todo lector pasa por
 * `MspTheme.shapes` (`theme/MspTheme.kt`, Task 5), nunca declara su propio
 * radio.
 *
 * Transcrito 1:1 de `CampoShapes` (kollect-app, ver
 * `.superpowers/research/kollect-app-designsystem.md` §3) — nada aquí
 * depende del matiz de marca, no hay reskin.
 */
@Immutable
class MspShapes internal constructor() {
    /** Cards grandes — hero, próxima parada, estado, historial de pagos. */
    val card: Shape = RoundedCornerShape(20.dp)

    /** Tiles y filas de lista — bento tiles, filas de cliente. */
    val tile: Shape = RoundedCornerShape(16.dp)

    /** Chips de estado, segmentos, barras de progreso. Pill completo, NO un dp fijo. */
    val chip: Shape = RoundedCornerShape(percent = 50)

    /** Botones de campo — CTAs primarios/ghost grandes. */
    val button: Shape = RoundedCornerShape(16.dp)

    /** Inputs y cards de tamaño medio — búsqueda, card de venta, tiles KV. */
    val field: Shape = RoundedCornerShape(14.dp)

    /** Controles pequeños — mini-botones, teclas del keypad, toggle de ojo, pills de método. */
    val control: Shape = RoundedCornerShape(12.dp)

    /** Hero de detalle-de-venta (radio más suave/grande que [card]). */
    val heroCard: Shape = RoundedCornerShape(22.dp)

    /** Cards de sección/ledger — progreso, comportamiento, productos, ledger. */
    val sectionCard: Shape = RoundedCornerShape(18.dp)

    /** Chip de ícono en la fila de pago del historial ledger. */
    val payIcon: Shape = RoundedCornerShape(11.dp)

    /** Chip de ícono de producto en la card de cliente (soft-square). */
    val chip9: Shape = RoundedCornerShape(9.dp)
}
