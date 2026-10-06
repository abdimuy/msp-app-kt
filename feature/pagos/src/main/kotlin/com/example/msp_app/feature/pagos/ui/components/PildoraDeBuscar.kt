package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme

/** `testTag` de la píldora flotante "Buscar cliente". */
const val PILDORA_DE_BUSCAR_TAG: String = "pagos_pildora_buscar"

/** El texto de la píldora. */
const val BUSCAR_CLIENTE: String = "Buscar cliente"

/**
 * La píldora flotante "Buscar cliente" que aparece abajo, a la altura del
 * pulgar, cuando el buscador de arriba salió de la pantalla al bajar la lista
 * (mock "Buscador" opción 3, decisión del dueño 2026-10-05).
 *
 * **Colores invertidos a propósito** (decisión del dueño, revisión de
 * goldens): `color = MspTheme.colors.onSurface` / contenido =
 * `MspTheme.colors.background`, al revés de cualquier tarjeta de la pantalla.
 * Medido: con los colores "normales" (`surface`/`onSurface`), la píldora se
 * perdía sobre las tarjetas en tema oscuro —fondo casi idéntico al de la
 * tarjeta de abajo— y apenas se distinguía en claro. Invertida, es SIEMPRE
 * el único elemento oscuro sobre fondo claro (claro) o claro sobre fondo
 * oscuro (oscuro) de toda la pantalla, con sombra para despegarla de la
 * lista.
 *
 * `AnimatedVisibility` con fade+slide corto cuando hay movimiento; con
 * [reducedMotion] cae a aparecer/desaparecer sin desplazamiento — mismo
 * contrato que el resto del módulo (`rememberMspReducedMotion()`).
 *
 * [modifier] lleva `navigationBarsPadding()` para no quedar debajo de la
 * barra de navegación de Android, con el mismo criterio medido en
 * `HojaDelContacto`/`HojaDeAbono`: sin el inset, el gesto de systembar se come
 * el toque de abajo.
 */
@Composable
fun PildoraDeBuscar(
    visible: Boolean,
    reducedMotion: Boolean,
    onTocar: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) fadeIn() else fadeIn() + slideInVertically { it / 2 },
        exit = if (reducedMotion) fadeOut() else fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = MspTheme.spacing.md)
                .wrapContentSize(Alignment.BottomCenter)
        ) {
            Surface(
                onClick = onTocar,
                shape = MspTheme.shapes.chip,
                color = MspTheme.colors.onSurface,
                shadowElevation = ELEVACION_DE_LA_PILDORA,
                modifier = Modifier
                    .heightIn(min = MspTheme.spacing.touchTarget)
                    .testTag(PILDORA_DE_BUSCAR_TAG)
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = MspTheme.spacing.lg,
                        vertical = MspTheme.spacing.sm
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MspTheme.colors.background
                    )
                    Text(
                        text = BUSCAR_CLIENTE,
                        style = MspTheme.type.bodyStrong,
                        color = MspTheme.colors.background
                    )
                }
            }
        }
    }
}

/**
 * Sombra de la píldora — más que la [MspTheme.spacing.xs] que llevaba antes:
 * con colores invertidos el contraste de color ya la separa de las tarjetas,
 * pero una sombra visible es lo que dice "esto flota encima", no "es la
 * primera tarjeta de la lista".
 */
private val ELEVACION_DE_LA_PILDORA = 6.dp
