package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.core.designsystem.theme.MspTheme

/**
 * **Los dos buscadores de la lista, con una sola forma** (decisión del dueño,
 * 2026-10-07, tras verlo en el teléfono: "el relleno se ve grande y el texto igual de
 * chico").
 *
 * `BasicTextField` con decoración propia — nada de `OutlinedTextField` ni de
 * `OutlinedTextFieldDefaults.DecorationBox`, que imponen 56 dp de alto mínimo y su
 * relleno, así que achicar el relleno no se notaba. Aquí:
 * - texto escrito y placeholder en [ESTILO_DEL_BUSCADOR] (20 sp);
 * - la caja VISIBLE (fondo `surface`, borde `outline`, `brand` con foco) mide lo que
 *   pide el texto más 4 dp arriba y abajo y 12 dp a los lados;
 * - el ÁREA TOCABLE sigue midiendo [BOTON_MENU_SIZE] (50 dp, la compuerta del repo),
 *   pero es invisible: la caja se centra dentro de ella. La ✕ también tiene su área de
 *   50 dp superpuesta, sin agrandar la caja.
 *
 * Mismo nodo siempre: el campo no cambia de rama al aparecer la ✕ ni al teclear, así
 * que no pierde el foco (ver `ElBuscadorNoPierdeElFocoTest`). La acción "buscar" del
 * teclado sólo lo esconde: la lista ya está filtrada.
 */
@Composable
internal fun CampoDeBuscarCliente(
    query: String,
    onBuscar: (String) -> Unit,
    conLupa: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    tagDeBorrar: String? = null,
    alBorrar: () -> Unit = {}
) {
    val interaccion = remember { MutableInteractionSource() }
    val enfocado by interaccion.collectIsFocusedAsState()
    val teclado = LocalSoftwareKeyboardController.current
    val estilo = ESTILO_DEL_BUSCADOR
    BasicTextField(
        value = query,
        onValueChange = onBuscar,
        modifier = modifier
            .heightIn(min = BOTON_MENU_SIZE)
            .testTag(tag),
        singleLine = true,
        textStyle = estilo.copy(color = MspTheme.colors.onSurface),
        cursorBrush = SolidColor(MspTheme.colors.brand),
        interactionSource = interaccion,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(onSearch = { teclado?.hide() }),
        decorationBox = { campo ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = BOTON_MENU_SIZE),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MspTheme.shapes.field)
                        .background(MspTheme.colors.surface)
                        .border(
                            width = BORDE_DEL_BUSCADOR,
                            color = if (enfocado) MspTheme.colors.brand else MspTheme.colors.outline,
                            shape = MspTheme.shapes.field
                        )
                        // Antes del relleno: los límites del nodo son los de la caja visible.
                        .testTag(tag + SUFIJO_CAJA_VISIBLE)
                        .padding(
                            horizontal = MspTheme.spacing.sm + MspTheme.spacing.xs,
                            vertical = MspTheme.spacing.xs
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
                ) {
                    if (conLupa) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MspTheme.colors.onSurfaceMuted,
                            modifier = Modifier.size(MspTheme.spacing.lg)
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Buscar cliente",
                                style = estilo,
                                color = MspTheme.colors.onSurfaceMuted,
                                maxLines = 1
                            )
                        }
                        campo()
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MspTheme.colors.onSurfaceMuted,
                            modifier = Modifier.size(MspTheme.spacing.lg)
                        )
                    }
                }
                if (query.isNotEmpty()) {
                    // El área de la ✕: 50 dp encima del glifo, invisible, sin agrandar la caja.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(BOTON_MENU_SIZE)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Borrar búsqueda"
                            ) {
                                onBuscar("")
                                alBorrar()
                            }
                            .semantics { contentDescription = "Borrar búsqueda" }
                            .then(
                                if (tagDeBorrar != null) Modifier.testTag(tagDeBorrar) else Modifier
                            )
                    )
                }
            }
        }
    )
}

/**
 * El texto de los dos buscadores (escrito y placeholder): 20 sp. `MspType` no tiene un
 * estilo de entrada de ese tamaño, así que se deriva de [MspTheme.type.input] en este
 * único lugar (decisión del dueño, 2026-10-07: 15 sp seguía viéndose chico).
 */
private val ESTILO_DEL_BUSCADOR: TextStyle
    @Composable get() = MspTheme.type.input.copy(fontSize = 20.sp, lineHeight = 26.sp)

/** Grosor del borde de la caja visible de los buscadores. */
private val BORDE_DEL_BUSCADOR = 1.dp

/** Sufijo del `testTag` de la caja visible de un buscador (la que se mide en las pruebas). */
const val SUFIJO_CAJA_VISIBLE: String = "_caja"
