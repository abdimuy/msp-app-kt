package com.example.msp_app.core.common.cobranza.domain

/**
 * Las visitas del periodo, indexadas por cómo se propagan (Task 13).
 *
 * [porVenta] son las de alcance [VisitScope.VENTA] con venta ligada;
 * [porCliente] son las de alcance [VisitScope.CLIENTE]; [huerfanas] cuenta
 * las de alcance venta SIN venta ligada, que no se pueden aplicar a nada.
 *
 * Lo comparten [EstadoCuentaDeriver] y [CoberturaDelPeriodo]: una sola regla de
 * "qué visita le aplica a qué venta".
 */
internal class IndiceVisitas(
    val porVenta: Map<Int, List<VisitaEnVentana>>,
    val porCliente: Map<Int, List<VisitaEnVentana>>,
    val huerfanas: Int
) {
    /**
     * La visita más reciente aplicable a [cuenta]: las suyas propias más las
     * de alcance cliente de su cliente.
     *
     * Empate exacto de instante: `maxByOrNull` devuelve el PRIMER máximo y
     * las propias van primero en la concatenación, así que gana la de
     * alcance venta. Es lo correcto — ante el mismo instante, el hecho más
     * específico (sobre esta deuda) pesa más que el del domicilio.
     */
    fun masRecienteFor(cuenta: CuentaDelPeriodo): VisitaEnVentana? {
        val propias = porVenta[cuenta.ventaId].orEmpty()
        val delCliente = porCliente[cuenta.clienteId].orEmpty()
        return (propias + delCliente).maxByOrNull { it.fechaHora }
    }

    companion object {
        fun de(visitasDelPeriodo: List<VisitaEnVentana>): IndiceVisitas {
            val (deCliente, deVenta) = visitasDelPeriodo.partition {
                // El MISMO par (literal, ¿tiene día de cita?) que usa
                // `resolver`: si el estado de una visita puede ser
                // `CITA_A_UNA_HORA`, su alcance tiene que poder ser
                // CLIENTE, o la cita se aplicaría a una sola venta.
                val alcance =
                    TipoVisitaCatalogo.alcanceDe(it.tipoVisita, it.fechaCita != null)
                alcance == VisitScope.CLIENTE
            }
            val (ligadas, sueltas) = deVenta.partition { it.ventaId != null }
            return IndiceVisitas(
                porVenta = ligadas.groupBy { requireNotNull(it.ventaId) },
                porCliente = deCliente.groupBy { it.clienteId },
                huerfanas = sueltas.size
            )
        }
    }
}
