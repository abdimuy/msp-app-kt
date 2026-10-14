package com.example.msp_app.core.common.cobranza.domain

/**
 * Cobertura de la semana: ¿la venta tiene "algo"? Spec de desempeño de
 * cobradores §2: al menos un pago de CUALQUIER forma —rechazado (E-APP-057) y
 * condonación incluidos— o una visita que le aplique.
 *
 * "Que le aplique" es la misma regla de [EstadoCuentaDeriver], vía el mismo
 * [IndiceVisitas]: las visitas de alcance venta con esa venta y las de alcance
 * cliente de su cliente. Una visita de alcance venta sin venta ligada no le
 * aplica a ninguna.
 *
 * Es distinto del estado: un pago rechazado da cobertura y no da dinero, así que
 * una venta puede estar en [EstadoCuenta.SIN_TOCAR] y "con algo" a la vez. Dominio
 * puro, como el deriver.
 */
object CoberturaDelPeriodo {

    fun ventasConAlgo(
        cuentas: List<CuentaDelPeriodo>,
        pagos: List<PagoEnVentana>,
        visitas: List<VisitaEnVentana>,
        ventana: VentanaCobro
    ): Set<Int> {
        val conPago = pagos
            .filter { ventana.contiene(it.fechaHora) }
            .mapTo(HashSet()) { it.ventaId }
        val indice = IndiceVisitas.de(visitas.filter { ventana.contiene(it.fechaHora) })
        return cuentas
            .filter { it.ventaId in conPago || indice.masRecienteFor(it) != null }
            .mapTo(LinkedHashSet()) { it.ventaId }
    }
}
