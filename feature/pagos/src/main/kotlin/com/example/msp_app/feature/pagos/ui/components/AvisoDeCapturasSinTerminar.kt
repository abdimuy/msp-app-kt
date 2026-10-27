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
import com.example.msp_app.feature.pagos.domain.port.VentaLocalSinTerminar

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
 * El destino del toque (E-APP-119): la venta del abono sin guardar o del pago
 * que no coincide si la hay; si no, reanudar las visitas y garantías
 * capturadas que no se escribieron. Sin aviso, ninguno.
 */
internal sealed interface DestinoDelAviso {
    data class AbrirVenta(val ventaId: Int) : DestinoDelAviso

    /** La venta local: [saleId] si ya se escribió (abre la venta), `null` abre el borrador. */
    data class AbrirVentaLocal(val saleId: String?) : DestinoDelAviso
    data object Reanudar : DestinoDelAviso
}

internal fun destinoDelAviso(
    capturas: CapturasSinTerminar,
    ventaLocal: VentaLocalSinTerminar? = null
): DestinoDelAviso? {
    val venta = capturas.ventaId
    return when {
        venta != null -> DestinoDelAviso.AbrirVenta(venta)
        ventaLocal != null -> DestinoDelAviso.AbrirVentaLocal(
            ventaLocal.saleId.takeIf {
                ventaLocal.yaSeGuardo
            }
        )
        capturas.cuantas > 0 -> DestinoDelAviso.Reanudar
        else -> null
    }
}

/**
 * El aviso fijo de la cabecera: capturas que no terminaron su camino y envíos
 * que no coinciden con la oficina (E-APP-118). Toca y lleva a la venta del
 * abono sin guardar o del pago que no coincide más viejo, donde "Guardar" lo
 * termina; sin venta, reanuda las visitas y garantías capturadas (E-APP-119).
 * Sin capturas no pinta nada.
 */
@Composable
fun AvisoDeCapturasSinTerminar(
    capturas: CapturasSinTerminar,
    onAbrirVenta: (Int) -> Unit,
    onReanudar: () -> Unit = {},
    ventaLocal: VentaLocalSinTerminar? = null,
    onAbrirVentaLocal: (String?) -> Unit = {}
) {
    val lineas = lineasDelAviso(capturas)
    if (lineas.isEmpty()) return
    val destino = destinoDelAviso(capturas, ventaLocal)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AVISO_CAPTURAS_SIN_TERMINAR_TAG)
            .let { base ->
                when (destino) {
                    is DestinoDelAviso.AbrirVenta ->
                        base.clickable(role = Role.Button) { onAbrirVenta(destino.ventaId) }
                    is DestinoDelAviso.AbrirVentaLocal ->
                        base.clickable(role = Role.Button) { onAbrirVentaLocal(destino.saleId) }
                    DestinoDelAviso.Reanudar ->
                        base.clickable(role = Role.Button) { onReanudar() }
                    null -> base
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
