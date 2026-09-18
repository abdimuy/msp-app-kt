package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.pagos.domain.MontosSugeridosDelCliente
import com.example.msp_app.feature.pagos.domain.PlanDeAbonos
import com.example.msp_app.feature.pagos.domain.RielDePagos
import com.example.msp_app.feature.pagos.domain.RitmoDePagos
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.DatosDeVenta
import com.example.msp_app.feature.pagos.domain.model.DetalleCliente
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDeGarantia
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.FichaDelCliente
import com.example.msp_app.feature.pagos.domain.model.GarantiaDeLaVenta
import com.example.msp_app.feature.pagos.domain.model.HistorialDePagos
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.ProductoDeVenta
import com.example.msp_app.feature.pagos.domain.model.ResumenDelCliente
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import com.example.msp_app.feature.pagos.domain.model.TipoDeContacto
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
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

    /**
     * Quién registró los contactos. Todo contacto que produce el adaptador trae
     * cobrador —sale de la columna `COBRADOR` de la fila, tanto en visitas
     * (`RoomVisitasAdapter`) como en abonos (`RoomPagosAdapter`)—, así que un
     * contacto de fixture con el cobrador vacío es un contacto que la app no
     * puede producir.
     */
    const val COBRADOR: String = "Marisol Vega"

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

    /**
     * **La bitácora del domicilio**, tal como la arma `BitacoraDelCliente.de`.
     *
     * Vive aparte y la comparten el detalle de cliente y el de venta porque en
     * producción es **la misma lista**: las dos pantallas la piden al mismo
     * caso de uso (`CargarDetalleCliente` y `CargarDetalleVenta`, que llaman al
     * mismo mezclador). Dos listas distintas en el fixture dejarían que una
     * pantalla se probara contra hechos que la otra no puede ver.
     *
     * Los tres contactos llevan `tipo` y `cobrador`, y el abono además `metodo`,
     * **porque eso es lo que produce el adaptador** — ver la nota de
     * `telefonoAval`: un fixture que no siembra lo que el adaptador SÍ produce
     * esconde defectos igual de bien que uno que siembra lo imposible. Aquí
     * escondía dos, y los dos salían en los goldens de bitácora: el default de
     * `tipo` es VISITA, así que el abono de $350 no contaba como cobro y el
     * encabezado del mes salía **sin subtotal** con un cobro debajo; y sin
     * `metodo` ni `cobrador` el renglón de meta —justo lo que la fila nueva vino
     * a agregar— no se pintaba en ninguna de las seis fotos. De pilón, la
     * pastilla "Cobros" sobre esta línea daba cero filas.
     */
    fun bitacoraDelDomicilio(): List<ContactoDeCobranza> = listOf(
        ContactoDeCobranza(
            fecha = Instant.parse("2026-08-24T17:00:00Z"),
            etiqueta = "no responde aunque está",
            nota = null,
            estado = EstadoCuenta.VISITE_VUELVO,
            importe = null,
            tipo = TipoDeContacto.VISITA,
            // `metodo` se queda en null en TODA visita, y no por
            // descuido: la columna de cable siempre trae 0 y
            // `MetodoDeCobro.de(0)` cae a EFECTIVO, así que leerla
            // pintaría "efectivo" sobre una puerta donde no se cobró.
            cobrador = COBRADOR
        ),
        ContactoDeCobranza(
            fecha = Instant.parse("2026-08-10T17:00:00Z"),
            etiqueta = "pidió reagendar visita",
            nota = "el viernes que cobre mi esposo",
            estado = EstadoCuenta.PROMETIO_PROXIMA,
            importe = null,
            tipo = TipoDeContacto.VISITA,
            cobrador = COBRADOR
        ),
        // El ÚNICO con punto medido, y es el mismo par que
        // `ultimoCobroAqui`: en la app ese campo sale del abono más
        // reciente que traiga coordenadas (`CargarDetalleCliente.kt`), así
        // que si este fixture pusiera dos pares distintos estaría sembrando
        // un estado que el caso de uso no puede producir. Los otros dos
        // contactos van sin punto a propósito: las dos mitades del
        // afordante —la fila que lleva al mapa y la que no— tienen que
        // verse en el mismo golden.
        //
        // Es el MISMO hecho que el abono `COB-A-10388` de
        // `pagosDeLaVenta()` —misma fecha, mismo importe—, así que viaja
        // con lo que ese abono trae: es un COBRO, fue en efectivo y lo
        // registró un cobrador con nombre.
        ContactoDeCobranza(
            fecha = Instant.parse("2026-08-03T17:10:00Z"),
            etiqueta = "Cobré",
            nota = null,
            estado = EstadoCuenta.PAGO,
            importe = dinero("350"),
            tipo = TipoDeContacto.COBRO,
            metodo = MetodoDeCobro.EFECTIVO,
            cobrador = COBRADOR,
            ventaId = VENTA_EN_PROMESA,
            ubicacion = PUNTO_DEL_ULTIMO_COBRO
        )
    )

    /** El detalle de cliente del mock. [estadoDeLaSegunda] permite variar la promesa. */
    fun detalleCliente(
        estadoDeLaSegunda: EstadoDelPeriodo = estadoPromesaConFecha()
    ): DetalleCliente = detalleCliente(ventasDelDetalle(estadoDeLaSegunda))

    /** Las dos cuentas del mock, para poder derivar el resumen SIN recursión. */
    private fun ventasDelDetalle(estadoDeLaSegunda: EstadoDelPeriodo) = listOf(
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
            estado = estadoDeLaSegunda,
            atrasos = 2
        )
    )

    private fun detalleCliente(ventasDelCliente: List<VentaDelCliente>): DetalleCliente =
        DetalleCliente(
            clienteId = CLIENTE_ID,
            // El "hoy" de la pantalla, FIJO: es lo que hace que la edad de la
            // nota —"hace 3 días"— se pinte igual hoy y dentro de un año, y que
            // los goldens de `pagos_ficha_*` no cambien de texto solos.
            hoy = HOY,
            nombre = "Victoria Flores Olmedo",
            telefono = "238 162 7597",
            direccion = "C. Hidalgo 214, Centro",
            zona = "ruta 25 · centro",
            aval = "Rosa María Ramírez",
            // `null`, y NO un teléfono inventado: el adaptador real no puede
            // traer otra cosa (`RoomVentasAdapter.kt:78`, porque la columna no
            // existe — ver el KDoc de `DetalleCliente.telefonoAval`). Un fixture
            // que siembra lo que el adaptador no puede producir es exactamente
            // lo que escondió el defecto de los atrasos siempre en cero: la
            // pantalla se veía bien en el test y vacía en el teléfono.
            telefonoAval = null,
            saldoTotal = dinero("3550"),
            ventas = ventasDelCliente,
            contactos = bitacoraDelDomicilio(),
            totalContactos = 27,
            diaDeRuta = "jueves",
            frecuencia = "semanal",
            resumen = resumenDelCliente(ventasDelCliente),
            productos = listOf(
                ProductoDeVenta("Sala 3 piezas + base", dinero("6300")),
                ProductoDeVenta("Refrigerador Mabe 14'", dinero("4950"))
            ),
            ultimoCobroAqui = PUNTO_DEL_ULTIMO_COBRO,
            notaDeLaVenta = "entrega en la puerta de atrás",
            ficha = fichaDelCliente(),
            liquidacion = liquidacionDelCliente(),
            ultimaVisita = Instant.parse("2026-08-24T17:00:00Z")
        )

    /**
     * La ficha del mock: dos señales del catálogo cerrado y la nota libre con
     * lo que NO es catálogo — quién atiende por su nombre y las señas de la
     * casa. Es exactamente el reparto que la Task 24 decidió: *"trabaja de
     * noche"* y *"atiende la suegra"* son señales porque la máquina las
     * consulta; *"casa azul, portón negro"* es nota porque solo la lee un
     * humano.
     */
    fun fichaDelCliente(): FichaDelCliente = FichaDelCliente(
        senales = setOf(SenalDeFicha.ESTA_EN_LA_NOCHE, SenalDeFicha.ATIENDE_OTRA_PERSONA),
        nota = "atiende la suegra, doña Remedios.\ncasa azul, portón negro.",
        actualizada = Instant.parse("2026-08-24T17:00:00Z")
    )

    /** La misma ficha, con la advertencia que tiene que verse sin desplazar. */
    fun fichaConAdvertencia(): FichaDelCliente = fichaDelCliente().let {
        it.copy(senales = it.senales + SenalDeFicha.HAY_PERRO)
    }

    private fun venta(
        ventaId: Int,
        folio: String,
        descripcion: String,
        cifras: Cifras,
        estado: EstadoDelPeriodo,
        atrasos: Int = 0
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
            estado = estado,
            atrasos = atrasos,
            fechaVenta = LocalDate.of(2026, 5, 4),
            frecuencia = "semanal",
            totalVenta = cifras.total,
            enganche = dinero("900"),
            abonado = cifras.cubierto,
            liquidacion = liquidacionDeLaVenta(),
            pagoPromedio = dinero("150")
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
            // La MISMA bitácora que el detalle de cliente, y no una lista vacía:
            // `CargarDetalleVenta` llena este campo con `BitacoraDelCliente.de`
            // sobre la cobranza entera del domicilio, así que un `detalleVenta()`
            // sin contactos es un estado que la app no produce — y mientras lo
            // fue, la sección "lo que ha pasado" salía en "Sin movimientos" en
            // todos los goldens y las pastillas de alcance no se fotografiaban
            // con nada debajo.
            contactos = bitacoraDelDomicilio(),
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
        // Ver la nota de `detalleCliente`: el adaptador no puede traerlo.
        telefonoAval = null,
        notas = "Trabaja de noche — antes de las 10 am",
        descripcion = descripcion,
        fechaVenta = LocalDate.of(2026, 5, 4),
        instanteDeVenta = Instant.parse("2026-05-04T18:00:00Z"),
        saldo = cifras.restante,
        parcialidad = cifras.cuota,
        pagoPromedio = dinero("150"),
        frecuencia = "semanal",
        diaDeCobranza = "jueves",
        diaTemporal = "",
        abonosTotales = 20,
        totalVenta = cifras.total,
        precioContado = dinero("5200"),
        enganche = dinero("900"),
        vendedor = "J. Carlos Méndez",
        atrasos = 2,
        fechaUltimoPago = LocalDate.of(2026, 9, 6)
    )

    /**
     * El resumen del bloque de dinero, **derivado con el dominio real** y no
     * escrito a mano.
     *
     * Importa que sea derivado: un resumen inventado dejaría los goldens
     * enseñando cifras que ninguna fórmula produce, y entonces la foto ya no
     * probaría que la pantalla pinta lo que el dominio calcula — que es la mitad
     * de para qué existe el golden.
     */
    fun resumenDelCliente(ventas: List<VentaDelCliente>): ResumenDelCliente {
        val pagos = pagosDeLaVenta()
        val semanas = RitmoDePagos.de(
            pagos = pagos,
            parcialidad = Money.sum(ventas.map { it.parcialidad }),
            hoy = HOY
        )
        return ResumenDelCliente(
            sueleDar = MontosSugeridosDelCliente.sueleDar(ventas),
            pideleHoy = MontosSugeridosDelCliente.pideleHoy(ventas, HOY),
            ultimoPago = pagos.maxByOrNull { it.fecha }
                ?.let { AppTime.toBusinessDate(it.fecha) },
            atrasos = ventas.sumOf { it.atrasos },
            ritmo = semanas,
            semanasCumplidas = RitmoDePagos.resumen(semanas).cumplidas
        )
    }

    /**
     * El punto del abono más reciente con coordenadas de este cliente. Uno solo,
     * compartido por `ultimoCobroAqui` y por el contacto del que sale — ver el
     * comentario en la lista de contactos.
     */
    val PUNTO_DEL_ULTIMO_COBRO: UbicacionDelCobro = UbicacionDelCobro(
        lat = 18.4609,
        lng = -97.3926
    )

    /** Un abono con coordenadas, para el pin del mapa del detalle. */
    fun pagoConUbicacion(lat: Double, lng: Double): PagoDelHistorial = PagoDelHistorial(
        pagoId = "COB-CON-PIN",
        ventaId = VENTA_EN_PROMESA,
        fecha = Instant.parse("2026-09-06T17:10:00Z"),
        importe = dinero("220"),
        formaCobroId = MetodoDeCobro.EFECTIVO.formaCobroId,
        metodo = MetodoDeCobro.EFECTIVO,
        nota = null,
        ubicacion = UbicacionDelCobro(lat, lng)
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
