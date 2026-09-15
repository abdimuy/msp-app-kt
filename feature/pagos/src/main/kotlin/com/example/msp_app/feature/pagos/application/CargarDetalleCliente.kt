package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.OrdenDeCobranza
import com.example.msp_app.feature.pagos.domain.PlanDeAbonos
import com.example.msp_app.feature.pagos.domain.RangoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.DatosDeVenta
import com.example.msp_app.feature.pagos.domain.model.DetalleCliente
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente
import com.example.msp_app.feature.pagos.domain.model.VisitaDelCliente
import com.example.msp_app.feature.pagos.domain.port.FichaDelClientePort
import javax.inject.Inject

/**
 * Arma el detalle del cliente: **el nombre del cliente es el título** y sus
 * ventas van dentro, cada una con su propio estado del catálogo de ocho
 * (decisiones del `task-16-brief.md`).
 *
 * Devuelve `null` cuando el teléfono no tiene ninguna venta de ese cliente —
 * no hay pantalla que pintar y el ViewModel lo dice, en vez de mostrar un
 * cascarón vacío que parece un cliente sin deuda.
 *
 * ## El orden de "sus ventas" es determinista, y por eso el dock también
 *
 * `RoomVentasAdapter.ventasDelCliente` sale de `SaleDao.getByClientId`, que
 * agrupa por `sales.DOCTO_CC_ID` y **no lleva `ORDER BY`**; nada entre el DAO y
 * la UI la ordenaba. En la práctica SQLite suele emitir en orden ascendente de
 * la llave del grupo, pero nada lo garantiza: ni el plan de consulta, ni otra
 * versión del motor, ni la misma base después de un `VACUUM`.
 *
 * Eso no era cosmético. La primera de esta lista es **dos cosas a la vez**: el
 * representante del cliente (nombre, teléfono, dirección, zona, aval, nota de
 * la venta) y
 * —vía `cuentaQueEncabeza`— **la cuenta a la que apunta el botón de dinero del
 * dock**. Sin orden, la cuenta que se cobra podía cambiar entre dos corridas
 * sin que cambiara un solo dato.
 *
 * Por eso se ordena **aquí, en el caso de uso**, y no en el dock ni en el DAO:
 * lo que se pinta en "sus ventas" y a dónde va el botón salen de la MISMA lista
 * ya ordenada, o sea que coinciden **por construcción** y no por coincidencia.
 * `SaleDao.getByClientId` no se toca porque `:feature:pagos` y
 * `:feature:visitas` también la consumen sin orden y un `ORDER BY` movería a
 * llamadores ajenos — el mismo razonamiento por el que la Task 19 puso su
 * desempate en `RegistroDeVisitaAdapter`.
 *
 * ## La ficha se lee aparte, y su fallo no tumba la pantalla
 *
 * [FichaDelClientePort.fichaDe] es **total**: contesta `null` cuando no se pudo
 * leer en vez de lanzar, así que un problema con la ficha nunca deja al cobrador
 * sin el saldo ni sin sus ventas. La ficha es conocimiento; el detalle es
 * dinero, y el dinero no depende de ella.
 *
 * No entra a [ReunirCobranzaDelCliente] porque esa lectura la comparte el
 * detalle de VENTA, que no pinta ficha: meterla ahí sería una consulta de más
 * en cada apertura de una pantalla que no la usa.
 *
 * El criterio es [OrdenDeCobranza.PRIMERO] —el mismo con el que la lista de la
 * Task 17 ordena la ruta del día, así que la fila de arriba aquí es la que ese
 * orden ya considera primera— rematado con `ventaId` ascendente. Ese remate es
 * lo que lo vuelve **total**: dos ventas del mismo cliente empatan de verdad en
 * las dos claves de `PRIMERO` (mismo instante de venta, las dos sin abonos), y
 * `DOCTO_CC_ACR_ID` es `@PrimaryKey` —único y estable entre dispositivos y
 * corridas—, así que no queda empate posible. Es el mismo último eslabón que
 * eligieron `RegistroDeVisitaAdapter` (`minByOrNull { it.DOCTO_CC_ACR_ID }`) y
 * `CarteraEnPantalla` (`.thenBy { it.clienteId }`).
 */
class CargarDetalleCliente @Inject constructor(
    private val reunirCobranzaDelCliente: ReunirCobranzaDelCliente,
    private val fichaPort: FichaDelClientePort
) {

    suspend operator fun invoke(clienteId: Int): DetalleCliente? {
        val cobranza = reunirCobranzaDelCliente(clienteId)
        // ORDEN PRIMERO: todo lo de abajo —el representante del cliente y la
        // lista pintada— sale de esta misma lista ya ordenada.
        val ventas = cobranza.ventas.sortedWith(ORDEN_DE_SUS_VENTAS)
        val primera = ventas.firstOrNull() ?: return null
        val contactos = bitacora(cobranza.visitas, cobranza.pagos)
        return DetalleCliente(
            clienteId = clienteId,
            nombre = primera.clienteNombre,
            telefono = primera.telefono,
            direccion = primera.direccion,
            zona = primera.zona,
            aval = primera.aval,
            telefonoAval = primera.telefonoAval,
            saldoTotal = Money.sum(ventas.map { it.saldo }),
            ventas = ventas.map {
                it.aVentaDelCliente(
                    estado = cobranza.estados[it.ventaId],
                    liquidacion = cobranza.liquidaciones[it.ventaId]
                )
            },
            // El día de la ruta es del DOMICILIO, no de una venta: el cobrador
            // pasa una vez por la puerta. Se toma del representante del cliente,
            // la misma fila de la que ya salen nombre, teléfono, zona y aval.
            diaDeRuta = primera.diaDeRuta,
            frecuencia = primera.frecuencia,
            contactos = contactos.take(CONTACTOS_VISIBLES),
            totalContactos = contactos.size,
            notaDeLaVenta = primera.notas.takeIf { it.isNotBlank() },
            ficha = fichaPort.fichaDe(clienteId),
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
                // El MISMO par (literal, ¿trae día de cita?) que usa el deriver:
                // una cita en la bitácora tiene que verse como cita, no como
                // el "vuelvo" de su literal de cable.
                estado = TipoVisitaCatalogo.estadoDe(
                    visita.tipoVisita,
                    visita.fechaCita != null
                ),
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
        /**
         * El orden de "sus ventas". **Total y único** — ver el KDoc de la clase
         * para por qué vive aquí y no en el DAO ni en el dock.
         */
        val ORDEN_DE_SUS_VENTAS: Comparator<DatosDeVenta> =
            compareBy<DatosDeVenta, RangoDeCobranza>(OrdenDeCobranza.PRIMERO) {
                OrdenDeCobranza.rangoDe(
                    saldo = it.saldo,
                    totalVenta = it.totalVenta,
                    enganche = it.enganche,
                    instanteDeVenta = it.instanteDeVenta
                )
            }.thenBy { it.ventaId }

        /** Cuántos contactos se pintan antes del "ver los N contactos" del mock. */
        const val CONTACTOS_VISIBLES = 3

        /** Etiqueta estática del abono en la bitácora. Minúsculas, sin punto final. */
        const val ETIQUETA_COBRE = "cobré"
    }
}

/**
 * Proyecta una venta a su fila en "sus ventas", con su estado ya derivado.
 *
 * [liquidacion] llega por parámetro y no se lee aquí: quien la tiene es
 * [CobranzaDelCliente], que ya la reunió en la misma pasada. `null` es el caso
 * normal — no toda venta admite liquidación.
 */
internal fun DatosDeVenta.aVentaDelCliente(
    estado: EstadoDelPeriodo?,
    liquidacion: Liquidacion? = null
): VentaDelCliente {
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
        estado = estado ?: EstadoDelPeriodo.sinTocar(parcialidad),
        atrasos = atrasos,
        fechaVenta = fechaVenta,
        frecuencia = frecuencia,
        totalVenta = totalVenta,
        enganche = enganche,
        abonado = abonado,
        liquidacion = liquidacion,
        pagoPromedio = pagoPromedio
    )
}
