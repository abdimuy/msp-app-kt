package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.port.CapturasSinTerminar

/** `testTag` del aviso "N capturas sin terminar" (E-APP-084). */
const val AVISO_CAPTURAS_SIN_TERMINAR_TAG: String = "pagos_aviso_capturas_sin_terminar"

/** "N capturas sin terminar" — texto aprobado por el dueño 2026-10-20 (E-APP-089). */
internal fun textoCapturasSinTerminar(cuantas: Int): String =
    if (cuantas == 1) "1 captura sin terminar" else "$cuantas capturas sin terminar"

/**
 * "Un envío no coincide con la oficina" — texto aprobado por el dueño 2026-10-20
 * (decisión 4): igual para ventas, pagos y condonaciones (E-APP-118). El de
 * ventas vive en `PendingLocalSalesWorker` (`TEXTO_ENVIO_NO_COINCIDE`).
 */
internal const val TEXTO_ENVIO_NO_COINCIDE: String = "Un envío no coincide con la oficina"

/** Las líneas del aviso, en orden. Vacía: el aviso no se pinta. */
internal fun lineasDelAviso(capturas: CapturasSinTerminar): List<String> = buildList {
    if (capturas.cuantas > 0) add(textoCapturasSinTerminar(capturas.cuantas))
    if (capturas.noCoinciden > 0) add(TEXTO_ENVIO_NO_COINCIDE)
}

/**
 * El aviso fijo de la cabecera: capturas que no terminaron su camino y envíos
 * que no coinciden con la oficina (E-APP-118). Toca y lleva a la venta del
 * abono sin guardar o del pago que no coincide más viejo, donde "Guardar" lo
 * termina. Sin capturas no pinta nada.
 */
@Composable
fun AvisoDeCapturasSinTerminar(capturas: CapturasSinTerminar, onAbrirVenta: (Int) -> Unit) {
    val lineas = lineasDelAviso(capturas)
    if (lineas.isEmpty()) return
    val venta = capturas.ventaId
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AVISO_CAPTURAS_SIN_TERMINAR_TAG)
            .let { base ->
                if (venta != null) {
                    base.clickable(
                        role = Role.Button
                    ) { onAbrirVenta(venta) }
                } else {
                    base
                }
            }
            .padding(vertical = MspTheme.spacing.xs)
    ) {
        lineas.forEach { linea ->
            Text(
                text = linea,
                style = MspTheme.type.caption,
                color = MspTheme.colors.danger
            )
        }
    }
}
