package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista

/** `testTag` del cuerpo de la hoja "Ordenar por". */
const val HOJA_DE_ORDEN_TAG: String = "pagos_hoja_orden"

/** `testTag` de cada opción de la hoja, por orden. */
const val OPCION_DE_ORDEN_TAG: String = "pagos_hoja_orden_opcion"

/** Título de la hoja. */
const val ORDENAR_POR: String = "Ordenar por"

/**
 * **La hoja "Ordenar por": las siete formas de ordenar la lista** (mock
 * "Orden" opción D, decisión del dueño 2026-10-05).
 *
 * `ModalBottomSheet`, igual que [HojaDeAbono] y [HojaDeLaFicha] en este mismo
 * paquete — y por la misma razón que ellas: el dueño pidió que todas las
 * hojas nuevas animen igual. El cuerpo vive en [CuerpoDeOrdenarPor], fuera del
 * `ModalBottomSheet`, por el mismo motivo documentado ahí:
 * `captureRoboImage`/`performClick()` no cruzan al `Popup` donde M3 lo monta,
 * así que los goldens y las pruebas de toque montan el cuerpo directo.
 *
 * Elegir una opción cierra la hoja de un toque — no hay un segundo "Continuar"
 * como en [HojaDeAbono]: aquí no hay nada que deshacer, cada opción sólo
 * reordena lo que ya se ve.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaDeOrdenarPor(
    abierta: Boolean,
    seleccionado: OrdenDeLaLista,
    onElegir: (OrdenDeLaLista) -> Unit,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!abierta) return
    ModalBottomSheet(
        onDismissRequest = onCerrar,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MspTheme.colors.surface,
        contentColor = MspTheme.colors.onSurface
    ) {
        CuerpoDeOrdenarPor(
            seleccionado = seleccionado,
            onElegir = {
                onElegir(it)
                onCerrar()
            }
        )
    }
}

/** El cuerpo de [HojaDeOrdenarPor], sin el `ModalBottomSheet` — ver su KDoc. */
@Composable
fun CuerpoDeOrdenarPor(
    seleccionado: OrdenDeLaLista,
    onElegir: (OrdenDeLaLista) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MspTheme.spacing.md)
            .testTag(HOJA_DE_ORDEN_TAG)
    ) {
        Text(text = ORDENAR_POR, style = MspTheme.type.cardTitle, color = MspTheme.colors.onSurface)
        Spacer(Modifier.height(MspTheme.spacing.md))
        OPCIONES.forEach { opcion ->
            FilaDeOpcion(
                opcion = opcion,
                seleccionada = opcion.orden == seleccionado,
                onElegir = { onElegir(opcion.orden) }
            )
            Spacer(Modifier.height(MspTheme.spacing.sm))
        }
    }
}

@Composable
private fun FilaDeOpcion(opcion: OpcionDeOrden, seleccionada: Boolean, onElegir: () -> Unit) {
    Surface(
        onClick = onElegir,
        shape = MspTheme.shapes.field,
        color = if (seleccionada) MspTheme.colors.brandTint else MspTheme.colors.surface2,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MspTheme.spacing.touchTarget)
            .testTag("${OPCION_DE_ORDEN_TAG}_${opcion.orden.name}")
    ) {
        Row(
            modifier = Modifier.padding(MspTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs)
        ) {
            Anillo(seleccionada)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = opcion.orden.etiqueta,
                        style = MspTheme.type.listTitle,
                        color = MspTheme.colors.onSurface
                    )
                    if (opcion.deSiempre) {
                        Spacer(Modifier.width(MspTheme.spacing.sm))
                        DeSiempre()
                    }
                }
                Spacer(Modifier.height(MspTheme.spacing.xs))
                Text(
                    text = opcion.descripcion,
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
        }
    }
}

/** La pastilla "DE SIEMPRE" junto a "Cobranza" — el único orden marcado así. */
@Composable
private fun DeSiempre() {
    Text(
        text = "DE SIEMPRE",
        style = MspTheme.type.chipLabel,
        color = MspTheme.colors.brand,
        modifier = Modifier
            .clip(MspTheme.shapes.chip)
            .background(MspTheme.colors.brandTint)
            .padding(horizontal = MspTheme.spacing.sm, vertical = MspTheme.spacing.xs)
    )
}

/**
 * El anillo del radio — 1:1 del patrón ya usado por `HojaDeAbono.Anillo` en
 * este mismo paquete (no se reusa porque allá es `private`; se prefiere
 * copiar un composable de 12 líneas a exponerlo sólo para esta hoja).
 */
@Composable
private fun Anillo(seleccionada: Boolean) {
    Box(
        modifier = Modifier
            .size(ANILLO)
            .clip(CircleShape)
            .background(if (seleccionada) MspTheme.colors.brand else MspTheme.colors.progressTrack),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (seleccionada) PUNTO else ANILLO - BORDE)
                .clip(CircleShape)
                .background(if (seleccionada) MspTheme.colors.onBrand else MspTheme.colors.surface2)
        )
    }
}

/** Una opción de la hoja: el [orden] que elige, su descripción corta y si lleva la pastilla. */
private data class OpcionDeOrden(
    val orden: OrdenDeLaLista,
    val descripcion: String,
    val deSiempre: Boolean = false
)

/** Las siete opciones, en el orden exacto del mock. */
private val OPCIONES = listOf(
    OpcionDeOrden(
        OrdenDeLaLista.COBRANZA,
        "Sin abonos primero, luego las más viejas",
        deSiempre = true
    ),
    OpcionDeOrden(OrdenDeLaLista.VENTAS_NUEVAS, "Las vendidas más recientemente"),
    OpcionDeOrden(OrdenDeLaLista.MAS_ATRASOS, "Las semanas sin pagar, de más a menos"),
    OpcionDeOrden(OrdenDeLaLista.SIN_PAGAR_HACE_MAS, "Último pago más antiguo primero"),
    OpcionDeOrden(OrdenDeLaLista.MAYOR_SALDO, "Lo que más deben, primero"),
    OpcionDeOrden(OrdenDeLaLista.NOMBRE, "De la A a la Z"),
    OpcionDeOrden(OrdenDeLaLista.MAS_CERCA, "Según dónde estás ahora")
)

private val ANILLO = 22.dp
private val BORDE = 3.dp
private val PUNTO = 8.dp
