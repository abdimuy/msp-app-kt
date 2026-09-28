package com.example.msp_app.feature.pagos.domain.model

import com.example.msp_app.core.common.money.Money
import java.time.Instant

/**
 * **Una condonación de la venta**, para que el historial la cuente.
 *
 * Existe aparte de [PagoDelHistorial] a propósito: una condonación **no es un
 * abono**. No es dinero que entró, así que no entra al conjunto
 * `VentanaCobro.FORMAS_COBRO_COBRANZA` y, con eso, no toca el ritmo, el último
 * pago, el estado del periodo, la cartera ni la guarda anti-duplicado del abono
 * — todos leen [PagoDelHistorial]. Si viajara como un pago con otra etiqueta,
 * cualquiera de esos consumidores la contaría como cobro el día que alguien
 * relajara un filtro.
 *
 * Lo que sí hace falta es que el cobrador **la vea**: sin ella, la venta baja de
 * saldo en pantalla sin ningún renglón que lo explique, y el cobrador vuelve a
 * condonar (E-APP-029).
 *
 * @property aplicada `false` cuando el servidor la **rechazó** y el teléfono la
 *   marcó así (`Payment.DOCTO_CC_ID = DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR`).
 *   Se sigue enseñando —es un hecho: se intentó y la oficina la tiene
 *   resguardada— pero como **no aplicada**, nunca como una condonación que bajó
 *   la deuda. Una pendiente de subir cuenta como aplicada, igual que un abono
 *   pendiente se pinta como abono: el historial no distingue pendientes en
 *   ningún renglón.
 */
data class CondonacionDelHistorial(
    val condonacionId: String,
    val ventaId: Int,
    val fecha: Instant,
    val importe: Money,
    val cobrador: String,
    val aplicada: Boolean,
    val ubicacion: UbicacionDelCobro? = null
)
