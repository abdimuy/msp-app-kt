package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Immutable
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.MontosSugeridos
import com.example.msp_app.feature.pagos.domain.VeredictoDelAbono
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro

/**
 * Por qué el abono no quedó. Cuatro ramas y no un texto: al cobrador se le dice
 * una cosa distinta en cada caso, y aplanarlas fue el defecto D5.
 */
enum class FalloDelAbono {
    /** El teléfono ya no tiene la venta. */
    VENTA_NO_ESTA,

    /** No se supo qué cobrador está cobrando. */
    SIN_COBRADOR,

    /** El guardado falló. Se puede reintentar. */
    NO_SE_PUDO_GUARDAR,

    /**
     * El cinturón de `application/` rechazó el monto. Es un defecto de la
     * pantalla, no del cobrador; se dice igual para que no quede un botón que
     * no hace nada.
     */
    BLOQUEADO,

    /**
     * No se pudo comprobar si el abono quedó. **No es reintentable desde aquí**:
     * el guard sigue puesto a propósito, y la duda se resuelve al volver a
     * abrir la pantalla, mirando el historial.
     */
    NO_SE_PUDO_VERIFICAR
}

/**
 * El **paso dos** de la confirmación, congelado.
 *
 * Que sea un objeto aparte y no un `Boolean` es lo que hace que la
 * confirmación enseñe exactamente lo que se va a registrar: el monto, el método
 * y el veredicto (saldo anterior → saldo nuevo, y las rarezas) quedan fijos en
 * el instante del primer toque, aunque el estado de abajo siga moviéndose.
 *
 * Un [ConfirmacionPendiente] no nulo **es** la petición de enseñar la hoja, y
 * nunca escribe nada por sí mismo: el dinero se mueve solo en
 * [RegistrarAbonoViewModel.confirmar].
 */
@Immutable
data class ConfirmacionPendiente(
    val importe: Money,
    val metodo: MetodoDeCobro,
    val veredicto: VeredictoDelAbono
)

/**
 * Estado observable de la pantalla de abono.
 *
 * [registrado] no nulo es el final del camino: el abono ya está escrito y la
 * pantalla no vuelve a ofrecer registrar. Convive con el guard persistido del
 * ViewModel — el estado se pierde al morir el proceso, el guard no.
 */
@Immutable
data class RegistrarAbonoUiState(
    val cargando: Boolean = true,
    val venta: DetalleVenta? = null,
    val error: ErrorDeDetalle? = null,
    val monto: MontoCapturado = MontoCapturado(),
    val metodo: MetodoDeCobro = MetodoDeCobro.EFECTIVO,
    val sugeridos: List<MontosSugeridos.Sugerido> = emptyList(),
    val veredicto: VeredictoDelAbono = VeredictoDelAbono.SIN_VENTA,
    val confirmacion: ConfirmacionPendiente? = null,
    val guardando: Boolean = false,
    val registrado: String? = null,
    val fallo: FalloDelAbono? = null
) {
    /**
     * ¿El CTA puede dispararse? Solo con venta cargada, veredicto limpio, nada
     * en vuelo y nada ya registrado. Es la ÚNICA fuente del `enabled` del botón.
     */
    val sePuedeRegistrar: Boolean
        get() = venta != null && veredicto.sePuedeRegistrar && !guardando && registrado == null
}
