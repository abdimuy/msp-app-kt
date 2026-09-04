package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.PlanDeAbonos
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.DatosDeVenta
import com.example.msp_app.feature.pagos.domain.model.DetalleCliente
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente
import com.example.msp_app.feature.pagos.domain.model.VisitaDelCliente
import javax.inject.Inject

/**
 * Arma el detalle del cliente: **el nombre del cliente es el título** y sus
 * ventas van dentro, cada una con su propio estado del catálogo de ocho
 * (decisiones del `task-16-brief.md`).
 *
 * Devuelve `null` cuando el teléfono no tiene ninguna venta de ese cliente —
 * no hay pantalla que pintar y el ViewModel lo dice, en vez de mostrar un
 * cascarón vacío que parece un cliente sin deuda.
 */
class CargarDetalleCliente @Inject constructor(
    private val reunirCobranzaDelCliente: ReunirCobranzaDelCliente
) {

    suspend operator fun invoke(clienteId: Int): DetalleCliente? {
        val cobranza = reunirCobranzaDelCliente(clienteId)
        val primera = cobranza.ventas.firstOrNull() ?: return null
        val contactos = bitacora(cobranza.visitas, cobranza.pagos)
        return DetalleCliente(
            clienteId = clienteId,
            nombre = primera.clienteNombre,
            telefono = primera.telefono,
            direccion = primera.direccion,
            zona = primera.zona,
            aval = primera.aval,
            telefonoAval = primera.telefonoAval,
            saldoTotal = Money.sum(cobranza.ventas.map { it.saldo }),
            ventas = cobranza.ventas.map { it.aVentaDelCliente(cobranza.estados[it.ventaId]) },
            contactos = contactos.take(CONTACTOS_VISIBLES),
            totalContactos = contactos.size,
            ficha = primera.notas.takeIf { it.isNotBlank() },
            liquidacion = cobranza.liquidacionTotal(),
            ultimaVisita = cobranza.visitas.maxByOrNull { it.fecha }?.fecha
        )
    }

    /**
     * La bitácora "últimos contactos": visitas y abonos del mismo domicilio, en
     * una sola línea de tiempo. Se mezclan a propósito — el cobrador recuerda
     * "la vez pasada me dijo que el viernes, y antes sí me pagó", no dos listas.
     *
     * El estado de cada visita sale de [TipoVisitaCatalogo.estadoDe] (Task 14),
     * que es consumirlo, no re-derivarlo: la clasificación literal→estado tiene
     * un solo dueño en el repo y es ese objeto.
     */
    private fun bitacora(
        visitas: List<VisitaDelCliente>,
        pagos: List<PagoDelHistorial>
    ): List<ContactoDeCobranza> {
        val deVisitas = visitas.map { visita ->
            ContactoDeCobranza(
                fecha = visita.fecha,
                etiqueta = visita.tipoVisita.lowercase(),
                nota = visita.nota?.takeIf { it.isNotBlank() },
                estado = TipoVisitaCatalogo.estadoDe(visita.tipoVisita),
                importe = null
            )
        }
        val dePagos = pagos.map { pago ->
            ContactoDeCobranza(
                fecha = pago.fecha,
                etiqueta = ETIQUETA_COBRE,
                nota = pago.nota,
                estado = EstadoCuenta.PAGO,
                importe = pago.importe
            )
        }
        return (deVisitas + dePagos).sortedByDescending { it.fecha }
    }

    private companion object {
        /** Cuántos contactos se pintan antes del "ver los N contactos" del mock. */
        const val CONTACTOS_VISIBLES = 3

        /** Etiqueta estática del abono en la bitácora. Minúsculas, sin punto final. */
        const val ETIQUETA_COBRE = "cobré"
    }
}

/** Proyecta una venta a su fila en "sus ventas", con su estado ya derivado. */
internal fun DatosDeVenta.aVentaDelCliente(estado: EstadoDelPeriodo?): VentaDelCliente {
    val plan = PlanDeAbonos.de(
        totalVenta = totalVenta,
        abonado = abonado,
        parcialidad = parcialidad
    )
    return VentaDelCliente(
        ventaId = ventaId,
        folio = folio,
        descripcion = descripcion,
        saldo = saldo,
        parcialidad = parcialidad,
        abonosPagados = plan.pagados,
        abonosTotales = plan.totales,
        avance = plan.avance,
        estado = estado ?: EstadoDelPeriodo.sinTocar(parcialidad)
    )
}
