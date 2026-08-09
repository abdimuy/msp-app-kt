package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.component.PrimaryFieldButtonVariant
import com.example.msp_app.core.designsystem.theme.MspTheme
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0) de [MspPrimaryFieldButton]: los tres
 * variants (`Primary`/`Ghost`/`Danger`) + el estado `disabled` (fill plano
 * `outline`, sin sombra, sobre `Primary`), apilados a ancho completo. La
 * matriz Tier×escala completa llega en Task 10.
 */
class PrimaryFieldButtonScreenshotTest : MspScreenshotTest() {

    @Test
    fun `primary field button light`() {
        capture(
            name = "msp_primary_field_button_light",
            dark = false
        ) { SamplePrimaryFieldButtons() }
    }

    @Test
    fun `primary field button dark`() {
        capture(name = "msp_primary_field_button_dark", dark = true) { SamplePrimaryFieldButtons() }
    }
}

@Composable
private fun SamplePrimaryFieldButtons() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MspTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspPrimaryFieldButton(
            text = "Registrar pago",
            onClick = {},
            variant = PrimaryFieldButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth()
        )
        MspPrimaryFieldButton(
            text = "Ver detalle",
            onClick = {},
            variant = PrimaryFieldButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth()
        )
        MspPrimaryFieldButton(
            text = "Sí, el monto es correcto",
            onClick = {},
            variant = PrimaryFieldButtonVariant.Danger,
            modifier = Modifier.fillMaxWidth()
        )
        MspPrimaryFieldButton(
            text = "Registrar pago",
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
