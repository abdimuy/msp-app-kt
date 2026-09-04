package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.OrdenDeCobranza
import com.example.msp_app.feature.pagos.domain.PlanDeAbonos
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.domain.model.DatosDeVenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente
import com.example.msp_app.feature.pagos.domain.model.VentaEnLista
import java.math.BigDecimal
import java.time.LocalDate

/**
 * La ruta de un cobrador, en tipos de dominio: tres puertas, una de ellas con
 * dos cuentas en situaciones distintas — el caso exacto que la lista por venta
 * partía en dos personas.
 *
 * Nombres mexicanos, como pide el repo. Las cifras son las del mock de cliente
 * (`PagosFixtures`) para que las dos pantallas cuenten la misma historia.
 */
object ListaFixtures {

    /** Martes 1-sep-2026 en zona de negocio — el mismo "hoy" de `PagosFixtures`. */
    val HOY: LocalDate = LocalDate.of(2026, 9, 1)

    const val VICTORIA: Int = 5021
    const val RICARDO: Int = 6102
    const val GUADALUPE: Int = 7310

    fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))

    fun estado(
        estado: EstadoCuenta,
        parcialidad: Money = dinero("350"),
        abonoDelPeriodo: Money = Money.ZERO,
        fechaPromesa: LocalDate? = null
    ): EstadoDelPeriodo = EstadoDelPeriodo(
        estado = estado,
        abonoDelPeriodo = abonoDelPeriodo,
        parcialidad = parcialidad,
        fechaPromesa = fechaPromesa
    )

    /**
     * Una venta ya proyectada, con su rango calculado por el dominio de verdad
     * —no a mano— para que el orden que prueban los tests sea el que corre.
     */
    @Suppress("LongParameterList") // constructor de fixture: 1:1 con las columnas de la venta
    fun venta(
        ventaId: Int,
        folio: String,
        descripcion: String,
        saldo: Money,
        totalVenta: Money,
        enganche: Money,
        fechaVenta: LocalDate?,
        estado: EstadoDelPeriodo
    ): VentaEnLista {
        val parcialidad = estado.parcialidad
        val plan = PlanDeAbonos.de(
            totalVenta = totalVenta,
            abonado = totalVenta - saldo,
            parcialidad = parcialidad
        )
        return VentaEnLista(
            venta = VentaDelCliente(
                ventaId = ventaId,
                folio = folio,
                descripcion = descripcion,
                saldo = saldo,
                parcialidad = parcialidad,
                abonosPagados = plan.pagados,
                abonosTotales = plan.totales,
                avance = plan.avance,
                estado = estado
            ),
            rango = OrdenDeCobranza.rangoDe(
                saldo = saldo,
                totalVenta = totalVenta,
                enganche = enganche,
                fechaVenta = fechaVenta
            )
        )
    }

    fun cliente(
        clienteId: Int,
        nombre: String,
        ventas: List<VentaEnLista>,
        telefono: String = "238 162 7597",
        direccion: String = "C. Hidalgo 214, Centro",
        zona: String = "ruta 25 · centro"
    ): ClienteEnLista = ClienteEnLista(
        clienteId = clienteId,
        nombre = nombre,
        telefono = telefono,
        direccion = direccion,
        zona = zona,
        saldoTotal = Money.sum(ventas.map { it.venta.saldo }),
        ventas = ventas,
        textoBuscable = com.example.msp_app.feature.pagos.domain.BusquedaDeClientes.textoBuscable(
            listOf(nombre, direccion, telefono) + ventas.map { it.venta.folio }
        )
    )

    /**
     * Victoria tiene DOS cuentas: una cobrada esta semana y otra que nadie ha
     * tocado y que no ha recibido un solo peso. Es la puerta que decide el
     * desempate por cliente.
     */
    fun victoria(): ClienteEnLista = cliente(
        clienteId = VICTORIA,
        nombre = "Victoria Flores Olmedo",
        ventas = listOf(
            venta(
                ventaId = 77021,
                folio = "V-5021",
                descripcion = "Sala 3 piezas + base",
                saldo = dinero("2100"),
                totalVenta = dinero("8400"),
                enganche = dinero("900"),
                fechaVenta = LocalDate.of(2026, 5, 4),
                estado = estado(EstadoCuenta.PAGO, abonoDelPeriodo = dinero("350"))
            ),
            venta(
                ventaId = 77188,
                folio = "V-5188",
                descripcion = "Refrigerador Mabe 14'",
                saldo = dinero("5500"),
                totalVenta = dinero("6400"),
                enganche = dinero("900"),
                fechaVenta = LocalDate.of(2026, 8, 10),
                estado = estado(EstadoCuenta.SIN_TOCAR, parcialidad = dinero("220"))
            )
        )
    )

    /** Ricardo: una sola cuenta, vieja, sin un peso abonado. Encabeza la lista. */
    fun ricardo(): ClienteEnLista = cliente(
        clienteId = RICARDO,
        nombre = "Ricardo Zepeda Nava",
        telefono = "238 104 2210",
        direccion = "Av. Juárez 87, San Miguel",
        ventas = listOf(
            venta(
                ventaId = 78400,
                folio = "V-6400",
                descripcion = "Estufa Acros 6 quemadores",
                saldo = dinero("4300"),
                totalVenta = dinero("5200"),
                enganche = dinero("900"),
                fechaVenta = LocalDate.of(2026, 3, 12),
                estado = estado(EstadoCuenta.SIN_TOCAR)
            )
        )
    )

    /** Guadalupe: ya abonó algo y se negó a dar más. Cuenta escalada. */
    fun guadalupe(): ClienteEnLista = cliente(
        clienteId = GUADALUPE,
        nombre = "Guadalupe Arellano Sosa",
        telefono = "238 155 0904",
        direccion = "Priv. Morelos 3, Tepeaca",
        ventas = listOf(
            venta(
                ventaId = 79115,
                folio = "V-7115",
                descripcion = "Lavadora Whirlpool 19kg",
                saldo = dinero("1800"),
                totalVenta = dinero("7300"),
                enganche = dinero("800"),
                fechaVenta = LocalDate.of(2026, 6, 21),
                estado = estado(EstadoCuenta.SE_NEGO)
            )
        )
    )

    /** La ruta completa, en el orden en que la fuente la devuelve. */
    fun ruta(): List<ClienteEnLista> = listOf(victoria(), ricardo(), guadalupe())

    /** Las ventas crudas de la ruta, para los tests que entran por los puertos. */
    fun datosDeLaRuta(): List<DatosDeVenta> = listOf(
        datos(
            VICTORIA,
            "Victoria Flores Olmedo",
            77021,
            "V-5021",
            "2100",
            "8400",
            "900",
            "2026-05-04"
        ),
        datos(
            VICTORIA,
            "Victoria Flores Olmedo",
            77188,
            "V-5188",
            "5500",
            "6400",
            "900",
            "2026-08-10"
        ),
        datos(RICARDO, "Ricardo Zepeda Nava", 78400, "V-6400", "4300", "5200", "900", "2026-03-12"),
        datos(
            GUADALUPE,
            "Guadalupe Arellano Sosa",
            79115,
            "V-7115",
            "1800",
            "7300",
            "800",
            "2026-06-21"
        )
    )

    @Suppress("LongParameterList") // es un constructor de fixture: 1:1 con las columnas de la venta
    fun datos(
        clienteId: Int,
        nombre: String,
        ventaId: Int,
        folio: String,
        saldo: String,
        total: String,
        enganche: String,
        fecha: String?
    ): DatosDeVenta = DatosDeVenta(
        ventaId = ventaId,
        creditoId = ventaId + 1,
        folio = folio,
        clienteId = clienteId,
        clienteNombre = nombre,
        telefono = "238 162 7597",
        direccion = "C. Hidalgo 214, Centro",
        zona = "ruta 25 · centro",
        aval = "Rosa María Ramírez",
        telefonoAval = null,
        notas = "",
        descripcion = "Refrigerador Mabe 14'",
        fechaVenta = fecha?.let(LocalDate::parse),
        saldo = dinero(saldo),
        parcialidad = dinero("350"),
        frecuencia = "semanal",
        abonosTotales = 20,
        totalVenta = dinero(total),
        precioContado = dinero("5200"),
        enganche = dinero(enganche),
        vendedor = "J. Carlos Méndez"
    )
}
