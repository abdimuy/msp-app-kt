package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.MontosSugeridos
import com.example.msp_app.feature.pagos.domain.PlanDeAbonos
import com.example.msp_app.feature.pagos.domain.SeguridadDelAbono
import com.example.msp_app.feature.pagos.domain.model.DatosDeVenta
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.HistorialDePagos
import com.example.msp_app.feature.pagos.domain.model.MesDePagos
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.ProductoDeVenta
import com.example.msp_app.feature.pagos.domain.model.ResumenDeRitmo
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * La venta del mock `registrar-abono.html`, en tipos de dominio: Victoria
 * Flores Olmedo, refrigerador V-5188, **saldo $1,450**.
 *
 * Las cifras están elegidas para que los TRES sugeridos existan a la vez, como
 * en el mock — y salen de la aritmética real, no de constantes puestas a mano:
 *
 * | dato | valor | de dónde |
 * |---|---|---|
 * | parcialidad | $220 | la cuota semanal |
 * | saldo | $1,450 | lo que falta |
 * | total / enganche | $6,310 / $900 | fijan lo abonado en $4,860 |
 * | fecha de venta | 14-abr-2026 | 140 días antes de "hoy" = **20 semanas** |
 * | liquidación | $1,290 | "hoy liquida con" |
 *
 * De ahí: **esperado hoy $220**, **al corriente** `20 x 220 - (4860 - 900)` =
 * **$440**, **liquidar $1,290**. Los tres exactamente como el mock.
 *
 * "Hoy" es [PagosFixtures.HOY] (1-sep-2026), el mismo de las otras pantallas.
 */
object AbonoFixtures {

    const val VENTA_ID: Int = PagosFixtures.VENTA_EN_PROMESA

    /** El saldo. El techo del bloqueo duro; el borde exacto se prueba contra él. */
    val SALDO: Money = dinero("1450")

    val PARCIALIDAD: Money = dinero("220")

    val TOTAL: Money = dinero("6310")

    val ENGANCHE: Money = dinero("900")

    val LIQUIDACION: Money = dinero("1290")

    /** 140 días antes de [PagosFixtures.HOY]: veinte semanas justas. */
    val FECHA_DE_VENTA: LocalDate = LocalDate.of(2026, 4, 14)

    /** Lo esperado hoy con el periodo sin tocar. */
    val ESPERADO_HOY: Money = PARCIALIDAD

    /** El atraso en dinero: dos cuotas. */
    val AL_CORRIENTE: Money = dinero("440")

    /** El periodo sin tocar: nadie ha cobrado esta semana. */
    fun estadoSinTocar(): EstadoDelPeriodo = EstadoDelPeriodo.sinTocar(PARCIALIDAD)

    /**
     * El periodo con un abono parcial de $100 — lo que enciende la rareza "ya
     * abonó esta semana". Sale del DINERO del periodo, nunca de una visita.
     */
    fun estadoConAbonoParcial(): EstadoDelPeriodo = EstadoDelPeriodo(
        estado = EstadoCuenta.ABONO_PARCIAL,
        abonoDelPeriodo = dinero("100"),
        parcialidad = PARCIALIDAD
    )

    /** La venta del mock. [estado] varía para los estados de la pantalla. */
    fun detalle(estado: EstadoDelPeriodo = estadoSinTocar()): DetalleVenta {
        val abonado = TOTAL - SALDO
        val plan = PlanDeAbonos.de(totalVenta = TOTAL, abonado = abonado, parcialidad = PARCIALIDAD)
        return DetalleVenta(
            ventaId = VENTA_ID,
            folio = "V-5188",
            creditoId = 12232,
            clienteId = PagosFixtures.CLIENTE_ID,
            clienteNombre = "Victoria Flores Olmedo",
            titulo = "Refrigerador Mabe 14'",
            fechaVenta = FECHA_DE_VENTA,
            saldo = SALDO,
            parcialidad = PARCIALIDAD,
            frecuencia = "semanal",
            abonosPagados = plan.pagados,
            abonosTotales = plan.totales,
            avance = plan.avance,
            totalVenta = TOTAL,
            precioContado = dinero("5200"),
            enganche = ENGANCHE,
            abonado = abonado,
            vendedor = "J. Carlos Méndez",
            estado = estado,
            productos = listOf(ProductoDeVenta("Refrigerador Mabe 14'", TOTAL)),
            historial = HistorialDePagos(
                semanas = emptyList(),
                resumen = ResumenDeRitmo(0, 0, Money.ZERO, 0),
                meses = emptyList(),
                totalPagos = 0
            ),
            liquidacion = com.example.msp_app.feature.pagos.domain.model.Liquidacion(
                monto = LIQUIDACION,
                vigenteHasta = LocalDate.of(2026, 9, 6),
                categoria = "precio a 4 meses"
            ),
            garantia = null
        )
    }

    /** La misma venta como dato crudo de puerto, para armar el ViewModel real. */
    fun datosDeVenta(): DatosDeVenta = PagosFixtures.datosDeVenta(
        ventaId = VENTA_ID,
        folio = "V-5188",
        descripcion = "Refrigerador Mabe 14'",
        cifras = PagosFixtures.Cifras(
            total = TOTAL,
            restante = SALDO,
            cuota = PARCIALIDAD,
            cubierto = TOTAL - SALDO
        )
    ).copy(fechaVenta = FECHA_DE_VENTA, enganche = ENGANCHE, frecuencia = "semanal")

    /** Un abono de $100 dentro del periodo abierto: el que enciende el duplicado. */
    fun abonoDeEstaSemana(): PagoDelHistorial = PagoDelHistorial(
        pagoId = "COB-A-010430",
        ventaId = VENTA_ID,
        fecha = Instant.parse("2026-09-01T16:00:00Z"),
        importe = dinero("100"),
        formaCobroId = MetodoDeCobro.EFECTIVO.formaCobroId,
        metodo = MetodoDeCobro.EFECTIVO,
        nota = null
    )

    /** El detalle con un abono ya guardado bajo [pagoId] — el guard resuelto por el hecho. */
    fun detalleConAbono(pagoId: String): DetalleVenta {
        val base = detalle()
        return base.copy(
            historial = base.historial.copy(
                meses = listOf(
                    MesDePagos(
                        mes = java.time.YearMonth.of(2026, 9),
                        nombre = "septiembre",
                        subtotal = ESPERADO_HOY,
                        pagos = listOf(abonoDeEstaSemana().copy(pagoId = pagoId))
                    )
                ),
                totalPagos = 1
            )
        )
    }

    /** El estado de pantalla en captura, con lo esperado hoy ya puesto. */
    fun enCaptura(estado: EstadoDelPeriodo = estadoSinTocar()): RegistrarAbonoUiState =
        conMonto(MontoCapturado.deSugerido(ESPERADO_HOY), estado)

    /** El estado de pantalla con un sobrepago tecleado: el bloqueo duro. */
    fun enBloqueo(): RegistrarAbonoUiState =
        conMonto(MontoCapturado(crudo = "300000"), estadoSinTocar())

    /** El paso dos, con un monto sano. */
    fun enConfirmacion(): RegistrarAbonoUiState = enCaptura().let { base ->
        base.copy(
            confirmacion = ConfirmacionPendiente(
                importe = base.monto.importe,
                metodo = base.metodo,
                veredicto = base.veredicto
            )
        )
    }

    /**
     * El paso dos escalado: $1,200 sobre una venta que ya recibió $100 esta
     * semana y espera $120 — diez veces lo esperado **y** posible duplicado.
     * Sigue por debajo del saldo: un monto que excediera el saldo no llegaría
     * nunca a esta hoja, lo bloquearía la pantalla anterior.
     */
    fun enMontoRaro(): RegistrarAbonoUiState =
        conMonto(MontoCapturado(crudo = "1200"), estadoConAbonoParcial()).let { base ->
            base.copy(
                confirmacion = ConfirmacionPendiente(
                    importe = base.monto.importe,
                    metodo = base.metodo,
                    veredicto = base.veredicto
                )
            )
        }

    /**
     * El final incómodo: la escritura no se pudo comprobar, el guard sigue
     * puesto y el CTA tiene que estar APAGADO, con la banda ofreciendo el
     * reintento real.
     */
    fun enDudaDeVerificacion(): RegistrarAbonoUiState = enCaptura().copy(
        fallo = FalloDelAbono.NO_SE_PUDO_VERIFICAR,
        verificacionPendiente = true
    )

    private fun conMonto(monto: MontoCapturado, estado: EstadoDelPeriodo): RegistrarAbonoUiState {
        val venta = detalle(estado)
        return RegistrarAbonoUiState(
            cargando = false,
            venta = venta,
            monto = monto,
            sugeridos = MontosSugeridos.de(venta, PagosFixtures.HOY),
            veredicto = SeguridadDelAbono.evaluar(
                monto = monto.importe,
                saldo = venta.saldo,
                esperadoHoy = MontosSugeridos.esperadoHoy(venta),
                yaAbonoEstePeriodo = venta.estado.abonoDelPeriodo > Money.ZERO
            )
        )
    }

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))
}
