package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme

/** `testTag` de [MspPrivacyEyeToggle] — localiza el control en compose-tests. */
internal const val PRIVACY_EYE_TOGGLE_TAG = "msp_privacy_eye_toggle"

private val PRIVACY_EYE_TOGGLE_SIZE = 40.dp

/**
 * Botón de ojo del design system (1:1 kollect §8.10, "mismo patrón 40dp
 * icon-surface que `ThemeToggle`"): icon-surface 40dp, shape
 * [MspTheme.shapes.control], glifo ojo/ojo-tachado según [masked].
 *
 * El caller es dueño de [masked] — este componente no lo persiste ni lo
 * deriva, solo lo refleja: el uso real (enmascarar montos) pasa por
 * [MspMoneyText] con `masked = ...`, no por este toggle.
 *
 * **Sin animación especial** (spec §8.10): un simple swap de glifo/color al
 * tocar, no hay nada que crossfadear ni gatear por
 * `rememberReducedMotionEnabled()` — coherente con que el chip de estado
 * ([MspStatusChip]) tampoco anima su ícono.
 */
@Composable
fun MspPrivacyEyeToggle(masked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    MspSurface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(PRIVACY_EYE_TOGGLE_SIZE)
            .testTag(PRIVACY_EYE_TOGGLE_TAG),
        shape = MspTheme.shapes.control,
        onClick = onToggle
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (masked) MspIcons.EyeOff else MspIcons.Eye,
                contentDescription = null,
                tint = if (masked) MspTheme.colors.brand else MspTheme.colors.onSurfaceMuted
            )
        }
    }
}
