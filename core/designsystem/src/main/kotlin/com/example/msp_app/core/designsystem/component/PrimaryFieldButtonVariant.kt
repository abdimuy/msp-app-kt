package com.example.msp_app.core.designsystem.component

import androidx.compose.ui.graphics.Color
import com.example.msp_app.core.designsystem.theme.MspColors

/**
 * Variante visual de [MspPrimaryFieldButton] (1:1 kollect §8.4). El default
 * del composable es [Primary] — [Danger] es una elección explícita del
 * caller, reservada a confirmaciones riesgosas (p. ej. "Sí, el monto es
 * correcto" sobre un monto marcado), nunca el estado inicial de un CTA.
 */
enum class PrimaryFieldButtonVariant {
    /** Fill sólido `brand` + sombra 8dp tintada al brand. El default. */
    Primary,

    /** Outline `brand`, sin relleno ni sombra — acción secundaria. */
    Ghost,

    /** Fill sólido `statusOverdue` + misma sombra que [Primary], tintada a rojo. */
    Danger
}

/** Color de relleno del variant cuando el botón está habilitado. */
internal fun PrimaryFieldButtonVariant.fillColor(colors: MspColors): Color = when (this) {
    PrimaryFieldButtonVariant.Primary -> colors.brand
    PrimaryFieldButtonVariant.Danger -> colors.statusOverdue
    PrimaryFieldButtonVariant.Ghost -> Color.Transparent
}

/** Color del texto del variant cuando el botón está habilitado. */
internal fun PrimaryFieldButtonVariant.contentColor(colors: MspColors): Color = when (this) {
    PrimaryFieldButtonVariant.Primary -> colors.onBrand
    PrimaryFieldButtonVariant.Danger -> colors.onDanger
    PrimaryFieldButtonVariant.Ghost -> colors.brand
}

/** Color base de la sombra tintada, o `null` si el variant no lleva sombra ([Ghost]). */
internal fun PrimaryFieldButtonVariant.shadowTintColor(colors: MspColors): Color? = when (this) {
    PrimaryFieldButtonVariant.Primary -> colors.brand
    PrimaryFieldButtonVariant.Danger -> colors.statusOverdue
    PrimaryFieldButtonVariant.Ghost -> null
}
