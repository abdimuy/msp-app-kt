package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.pagos.domain.PlanDeAbonos
import com.example.msp_app.feature.pagos.domain.RielDePagos
import com.example.msp_app.feature.pagos.domain.RitmoDePagos
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.DatosDeVenta
import com.example.msp_app.feature.pagos.domain.model.DetalleCliente
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDeGarantia
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.GarantiaDeLaVenta
import com.example.msp_app.feature.pagos.domain.model.HistorialDePagos
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.ProductoDeVenta
import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente
import com.example.msp_app.feature.pagos.domain.model.VisitaDelCliente
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Los datos del mock `docs/design/mocks/cliente-y-venta.html`, en tipos de
 * dominio: Victoria Flores Olmedo con dos cuentas, una pagada y la otra en
 * promesa. Nombres mexicanos, como pide el repo.
 *
 * Es el mismo cliente para los goldens y para los tests de dominio, así que un
 * cambio de cifras se ve en las dos partes a la vez.
 */
object PagosFixtures {

    /** Martes 1-sep-2026, mediodía CDMX. Todo lo que dependa de "hoy" sale de aquí. */
    val AHORA: Instant = Instant.parse("2026-09-01T18:00:00Z")

    val HOY: LocalDate = AppTime.toBusinessDate(AHORA)

    const val CLIENTE_ID: Int = 5021
    const val VENTA_PAGADA: Int = 77021
    const val VENTA_EN_PROMESA: Int = 77188

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))

    /**
     * Las cifras de una venta, juntas. Viajan como un objeto y no como cuatro
     * parámetros sueltos porque siempre se leen juntas y porque cuatro `Money`
     * en fila son cuatro oportunidades de invertir dos.
     */
    data class Cifras(val total: Money, val restante: Money, val cuota: Money, val cubierto: Money)

    /** El estado de la venta que ya pagó lo de la semana. */
    fun estadoPago(): EstadoDelPeriodo = EstadoDelPeriodo(
        estado = EstadoCuenta.PAGO,
        abonoDelPeriodo = dinero("350"),
        parcialidad = dinero("350")
    )

    /** Promesa CON fecha: el único estado que dice "no vuelvas esta semana". */
    fun estadoPromesaConFecha(): EstadoDelPeriodo = EstadoDelPeriodo(
        estado = EstadoCuenta.PROMETIO_PROXIMA,
        abonoDelPeriodo = Money.ZERO,
        parcialidad = dinero("220"),
        fechaPromesa = LocalDate.of(2026, 9, 15),
        montoPrometido = dinero("220")
    )

    /**
     * Promesa SIN fecha — lo que produce hoy `Pidió reagendar visita`. Debe
     * pintarse como pendiente, nunca como diferido.
     */
    fun estadoPromesaSinFecha(): EstadoDelPeriodo = EstadoDelPeriodo(
        estado = EstadoCuenta.PROMETIO_PROXIMA,
        abonoDelPeriodo = Money.ZERO,
        parcialidad = dinero("220")
    )

    /** Los abonos de la venta en promesa, tal como los pinta el riel del mock. */
    fun pagosDeLaVenta(): List<PagoDelHistorial> = listOf(
        pago(
            "COB-A-10388",
            "2026-08-03T17:10:00Z",
            "350",
            MetodoDeCobro.EFECTIVO,
            "recibo COB-A-10388"
        ),
        pago("COB-A-10201", "2026-07-27T16:40:00Z", "350", MetodoDeCobro.TRANSFERENCIA, null),
        pago(
            "COB-A-10088",
            "2026-07-20T17:05:00Z",
            "700",
            MetodoDeCobro.EFECTIVO,
            "cubrió la semana atrasada"
        ),
        pago("COB-A-09955", "2026-07-13T16:55:00Z", "350", MetodoDeCobro.EFECTIVO, null),
        pago("COB-A-09810", "2026-07-06T17:20:00Z", "350", MetodoDeCobro.EFECTIVO, null),
        pago("COB-A-09677", "2026-06-29T17:00:00Z", "350", MetodoDeCobro.EFECTIVO, null)
    )

    private fun pago(
        id: String,
        fechaIso: String,
        pesos: String,
        metodo: MetodoDeCobro,
        nota: String?
    ) = PagoDelHistorial(
        pagoId = id,
        ventaId = VENTA_EN_PROMESA,
        fecha = Instant.parse(fechaIso),
        importe = dinero(pesos),
        formaCobroId = metodo.formaCobroId,
        metodo = metodo,
        nota = nota
    )

    /** Historial completo (ritmo + riel) armado con el dominio real, no a mano. */
    fun historial(pagos: List<PagoDelHistorial> = pagosDeLaVenta()): HistorialDePagos {
        val semanas = RitmoDePagos.de(pagos = pagos, parcialidad = dinero("220"), hoy = HOY)
        return HistorialDePagos(
            semanas = semanas,
            resumen = RitmoDePagos.resumen(semanas),
            meses = RielDePagos.de(pagos),
            totalPagos = pagos.size
        )
    }

    /** La garantía del mock: refrigerador reportado el 18 de agosto, notificada. */
    fun garantiaDeLaVenta(): GarantiaDeLaVenta = GarantiaDeLaVenta(
        garantiaId = "GAR-2026-0188",
        producto = "Refrigerador Mabe 14'",
        reportadaEl = LocalDate.of(2026, 8, 18),
        falla = "No enfría en el congelador",
        estado = EstadoDeGarantia.NOTIFICADA
    )

    fun liquidacionDeLaVenta(): Liquidacion = Liquidacion(
        monto = dinero("1290"),
        vigenteHasta = LocalDate.of(2026, 9, 6),
        categoria = "precio a 4 meses"
    )

    fun liquidacionDelCliente(): Liquidacion = Liquidacion(
        monto = dinero("3180"),
        vigenteHasta = LocalDate.of(2026, 9, 6),
        categoria = "precio a 4 meses"
    )

    /** El detalle de cliente del mock. [estadoDeLaSegunda] permite variar la promesa. */
    fun detalleCliente(
        estadoDeLaSegunda: EstadoDelPeriodo = estadoPromesaConFecha()
    ): DetalleCliente = DetalleCliente(
        clienteId = CLIENTE_ID,
        nombre = "Victoria Flores Olmedo",
        telefono = "238 162 7597",
        direccion = "C. Hidalgo 214, Centro",
        zona = "ruta 25 · centro",
        aval = "Rosa María Ramírez",
        telefonoAval = "238 118 4402",
        saldoTotal = dinero("3550"),
        ventas = listOf(
            venta(
                ventaId = VENTA_PAGADA,
                folio = "V-5021",
                descripcion = "Sala 3 piezas + base",
                cifras = Cifras(dinero("8400"), dinero("2100"), dinero("350"), dinero("6300")),
                estado = estadoPago()
            ),
            venta(
                ventaId = VENTA_EN_PROMESA,
                folio = "V-5188",
                descripcion = "Refrigerador Mabe 14'",
                cifras = Cifras(dinero("6400"), dinero("1450"), dinero("220"), dinero("4950")),
                estado = estadoDeLaSegunda
            )
        ),
        contactos = listOf(
            ContactoDeCobranza(
                fecha = Instant.parse("2026-08-24T17:00:00Z"),
                etiqueta = "no responde aunque está",
                nota = null,
                estado = EstadoCuenta.VISITE_VUELVO,
                importe = null
            ),
            ContactoDeCobranza(
                fecha = Instant.parse("2026-08-10T17:00:00Z"),
                etiqueta = "pidió reagendar visita",
                nota = "el viernes que cobre mi esposo",
                estado = EstadoCuenta.PROMETIO_PROXIMA,
                importe = null
            ),
            ContactoDeCobranza(
                fecha = Instant.parse("2026-08-03T17:10:00Z"),
                etiqueta = "cobré",
                nota = null,
                estado = EstadoCuenta.PAGO,
                importe = dinero("350")
            )
        ),
        totalContactos = 27,
        ficha = "Trabaja de noche — antes de las 10 am. Atiende la suegra. Casa azul, portón negro.",
        liquidacion = liquidacionDelCliente(),
        ultimaVisita = Instant.parse("2026-08-24T17:00:00Z")
    )

    private fun venta(
        ventaId: Int,
        folio: String,
        descripcion: String,
        cifras: Cifras,
        estado: EstadoDelPeriodo
    ): VentaDelCliente {
        val plan = PlanDeAbonos.de(
            totalVenta = cifras.total,
            abonado = cifras.cubierto,
            parcialidad = cifras.cuota
        )
        return VentaDelCliente(
            ventaId = ventaId,
            folio = folio,
            descripcion = descripcion,
            saldo = cifras.restante,
            parcialidad = cifras.cuota,
            abonosPagados = plan.pagados,
            abonosTotales = plan.totales,
            avance = plan.avance,
            estado = estado
        )
    }

    /** El detalle de venta del mock. */
    fun detalleVenta(estado: EstadoDelPeriodo = estadoPromesaConFecha()): DetalleVenta {
        val total = dinero("6400")
        val abonado = dinero("4950")
        val parcialidad = dinero("220")
        val plan = PlanDeAbonos.de(totalVenta = total, abonado = abonado, parcialidad = parcialidad)
        return DetalleVenta(
            ventaId = VENTA_EN_PROMESA,
            folio = "MIG-V-5188",
            creditoId = 12232,
            clienteId = CLIENTE_ID,
            clienteNombre = "Victoria Flores Olmedo",
            titulo = "Refrigerador Mabe 14'",
            fechaVenta = LocalDate.of(2026, 5, 4),
            saldo = dinero("1450"),
            parcialidad = parcialidad,
            frecuencia = "semanal",
            abonosPagados = plan.pagados,
            abonosTotales = plan.totales,
            avance = plan.avance,
            totalVenta = total,
            precioContado = dinero("5200"),
            enganche = dinero("900"),
            abonado = abonado,
            vendedor = "J. Carlos Méndez",
            estado = estado,
            productos = listOf(ProductoDeVenta("Refrigerador Mabe 14'", total)),
            historial = historial(),
            liquidacion = liquidacionDeLaVenta(),
            garantia = garantiaDeLaVenta()
        )
    }

    /** Las dos ventas del cliente como datos crudos de puerto. */
    fun datosDeVentas(): List<DatosDeVenta> = listOf(
        datosDeVenta(
            ventaId = VENTA_PAGADA,
            folio = "V-5021",
            descripcion = "Sala 3 piezas + base",
            cifras = Cifras(dinero("8400"), dinero("2100"), dinero("350"), dinero("6300"))
        ),
        datosDeVenta(
            ventaId = VENTA_EN_PROMESA,
            folio = "V-5188",
            descripcion = "Refrigerador Mabe 14'",
            cifras = Cifras(dinero("6400"), dinero("1450"), dinero("220"), dinero("4950"))
        )
    )

    fun datosDeVenta(
        ventaId: Int,
        folio: String,
        descripcion: String,
        cifras: Cifras,
        clienteId: Int = CLIENTE_ID
    ): DatosDeVenta = DatosDeVenta(
        ventaId = ventaId,
        creditoId = ventaId + 1,
        folio = folio,
        clienteId = clienteId,
        clienteNombre = "Victoria Flores Olmedo",
        telefono = "238 162 7597",
        direccion = "C. Hidalgo 214, Centro",
        entidad = "Puebla",
        zona = "ruta 25 · centro",
        aval = "Rosa María Ramírez",
        telefonoAval = "238 118 4402",
        notas = "Trabaja de noche — antes de las 10 am",
        descripcion = descripcion,
        fechaVenta = LocalDate.of(2026, 5, 4),
        instanteDeVenta = Instant.parse("2026-05-04T18:00:00Z"),
        saldo = cifras.restante,
        parcialidad = cifras.cuota,
        frecuencia = "semanal",
        abonosTotales = 20,
        totalVenta = cifras.total,
        precioContado = dinero("5200"),
        enganche = dinero("900"),
        vendedor = "J. Carlos Méndez"
    )

    /** Una visita del catálogo, con el literal crudo que le toca. */
    fun visita(
        tipoVisita: String,
        fechaIso: String = "2026-09-01T16:00:00Z",
        ventaId: Int? = VENTA_EN_PROMESA,
        fechaPromesa: LocalDate? = null
    ): VisitaDelCliente = VisitaDelCliente(
        visitaId = "visita-$tipoVisita-$fechaIso",
        clienteId = CLIENTE_ID,
        ventaId = ventaId,
        fecha = Instant.parse(fechaIso),
        tipoVisita = tipoVisita,
        nota = null,
        fechaPromesa = fechaPromesa
    )
}
