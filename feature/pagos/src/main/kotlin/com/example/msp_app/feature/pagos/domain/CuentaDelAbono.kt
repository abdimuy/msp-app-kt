package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente

/**
 * A qué cuenta entra el abono cuando el cobrador toca "registrar abono" en el
 * detalle del cliente.
 *
 * ## El defecto que esto cierra
 *
 * `DetalleClienteScreen.cuentaQueEncabeza` hacía esto:
 *
 * ```
 * state.detalle?.ventas?.firstOrNull()?.ventaId
 * ```
 *
 * O sea: el dinero entraba a la **primera venta de la lista**, sin decirlo en
 * ninguna parte. Con dos cuentas abiertas, el abono podía caer en la equivocada
 * y nadie se enteraba hasta que cuadraban. Estaba documentado como decisión —"la
 * fila de arriba de sus ventas"— pero el cobrador nunca eligió esa fila: la
 * eligió un `sortedWith` del caso de uso.
 *
 * No se arregla ordenando mejor. Se arregla **preguntando**, y preguntando solo
 * cuando hay algo que preguntar.
 *
 * Dominio PURO: sin reloj, sin puertos, sin Android.
 */
object CuentaDelAbono {

    /**
     * La cuenta a la que va el abono **sin preguntar**, o `null` si hay que
     * preguntar.
     *
     * Contesta la venta solo cuando hay **exactamente una** cobrable. Con dos o
     * más no adivina — ese era el defecto entero.
     *
     * "Cobrable" es con saldo: una cuenta ya saldada no admite abono, así que un
     * cliente con una venta viva y tres liquidadas sigue entrando directo, que es
     * el caso común de alguien que lleva años comprando.
     */
    fun unica(ventas: List<VentaDelCliente>): Int? = cobrables(ventas).singleOrNull()?.ventaId

    /**
     * Cuál viene marcada al abrir la hoja: **la de más atrasos**.
     *
     * No la primera de la lista. Si el cobrador viene a cobrar y el cliente le
     * da un billete, la cuenta que urge es la que trae atraso — es la que dispara
     * la visita y la que crece si no entra nada.
     *
     * Empate en atrasos —incluido el caso normal, todas en cero— se rompe por el
     * **orden en que se pintan**, que es el que fija `CargarDetalleCliente` y es
     * total. Así lo preseleccionado es siempre lo que está arriba en la hoja, que
     * es lo que un dedo espera.
     *
     * `null` solo cuando no hay ninguna cuenta cobrable.
     */
    fun preseleccionada(ventas: List<VentaDelCliente>): Int? =
        cobrables(ventas).maxByOrNull { it.atrasos }?.ventaId

    /**
     * Las cuentas que se pueden pintar en la hoja, en el orden en que llegaron.
     *
     * Una venta sin saldo no entra: ofrecerla sería ofrecer un abono que
     * `SeguridadDelAbono` va a bloquear — una pantalla que ofrece lo que ella
     * misma prohíbe.
     */
    fun cobrables(ventas: List<VentaDelCliente>): List<VentaDelCliente> =
        ventas.filter { it.saldo > com.example.msp_app.core.common.money.Money.ZERO }
}
