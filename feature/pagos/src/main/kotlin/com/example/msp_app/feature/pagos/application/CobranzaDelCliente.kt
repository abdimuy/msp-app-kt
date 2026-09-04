package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.model.DatosDeVenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.VisitaDelCliente
import com.example.msp_app.feature.pagos.domain.port.LiquidacionPort
import com.example.msp_app.feature.pagos.domain.port.PagosPort
import com.example.msp_app.feature.pagos.domain.port.VentasPort
import com.example.msp_app.feature.pagos.domain.port.VisitasPort
import javax.inject.Inject

/**
 * Todo lo que las dos pantallas de detalle necesitan de un cliente, reunido una
 * sola vez.
 *
 * Las dos pantallas comparten esta lectura porque la derivación del periodo lo
 * exige: una visita de alcance CLIENTE ("no estaba", "cita") se propaga a
 * TODAS las ventas del cliente (Task 13), así que derivar el estado de UNA
 * venta requiere el cliente completo. Cargar solo la venta abierta daría un
 * estado distinto según por dónde se entró a la pantalla, y ese es exactamente
 * el tipo de incoherencia que este plan vino a quitar.
 */
data class CobranzaDelCliente(
    val ventas: List<DatosDeVenta>,
    val pagos: List<PagoDelHistorial>,
    val visitas: List<VisitaDelCliente>,
    val estados: Map<Int, EstadoDelPeriodo>,
    val liquidaciones: Map<Int, Liquidacion>
) {
    /** Los abonos de UNA venta, del más reciente al más viejo. */
    fun pagosDe(ventaId: Int): List<PagoDelHistorial> =
        pagos.filter { it.ventaId == ventaId }.sortedByDescending { it.fecha }

    /**
     * "Hoy liquida todo con": la suma de las liquidaciones vigentes de sus
     * ventas, con la fecha de vigencia MÁS CERCANA. La más cercana y no la más
     * lejana porque una cifra que ya venció en una de las ventas deja de cerrar
     * la conversación completa, que es justo lo que este número promete.
     */
    fun liquidacionTotal(): Liquidacion? {
        if (liquidaciones.isEmpty()) return null
        val vigencias = liquidaciones.values.mapNotNull { it.vigenteHasta }
        return Liquidacion(
            monto = Money.sum(liquidaciones.values.map { it.monto }),
            vigenteHasta = vigencias.minOrNull(),
            categoria = liquidaciones.values.first().categoria
        )
    }
}

/**
 * Reúne la cobranza del cliente: ventas, abonos, visitas, estado derivado del
 * periodo y liquidaciones.
 *
 * La derivación —y con ella el reenvío de incidencias a telemetría— ocurre
 * **una vez por invocación de este caso de uso**, o sea una vez por carga. Ver
 * [DerivarEstadoDelPeriodo].
 */
class ReunirCobranzaDelCliente @Inject constructor(
    private val ventasPort: VentasPort,
    private val pagosPort: PagosPort,
    private val visitasPort: VisitasPort,
    private val liquidacionPort: LiquidacionPort,
    private val resolverVentanaDeCobro: ResolverVentanaDeCobro,
    private val derivarEstadoDelPeriodo: DerivarEstadoDelPeriodo
) {

    suspend operator fun invoke(clienteId: Int): CobranzaDelCliente {
        val ventas = ventasPort.ventasDelCliente(clienteId)
        val pagos = ventas.flatMap { pagosPort.pagosDe(it.ventaId) }
        val visitas = visitasPort.visitasDelCliente(clienteId)
        val ventana = resolverVentanaDeCobro()
        val liquidaciones = ventas.mapNotNull { venta ->
            liquidacionPort.liquidacionDe(venta.ventaId)?.let { venta.ventaId to it }
        }.toMap()
        return CobranzaDelCliente(
            ventas = ventas,
            pagos = pagos,
            visitas = visitas,
            estados = derivarEstadoDelPeriodo(ventas, pagos, visitas, ventana),
            liquidaciones = liquidaciones
        )
    }
}
