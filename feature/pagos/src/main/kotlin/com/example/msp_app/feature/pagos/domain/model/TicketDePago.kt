package com.example.msp_app.feature.pagos.domain.model

import com.example.msp_app.core.common.money.Money
import java.time.Instant
import java.time.LocalDate

/**
 * El **contenido** del ticket de pago, ya en tipos de dominio.
 *
 * Deliberadamente separado del layout: este objeto dice QUÉ se imprime y
 * `TicketDePagoFormatter` dice CÓMO se ve en 32 caracteres. Es el mismo reparto
 * que `PaymentReceipt`/`PaymentReceiptFormatter` en `:core:printing`, con una
 * diferencia a propósito: allá los importes ya vienen como cadenas
 * pre-formateadas, aquí viajan en [Money] hasta el formatter.
 *
 * **Por qué [Money] y no cadenas:** la REGLA DE DINERO manda `Money` (escala 2)
 * en todo lo que cruza hacia `application/`/`ui/` de `:feature:pagos`, y este
 * modelo cruza las dos. El redondeo a **peso entero** —"dinero en pesos enteros
 * en todo el stack nuevo", la petición del brief— es una decisión de
 * PRESENTACIÓN y ocurre en un solo lugar: `formatMoneyMxn` dentro del formatter.
 * Así el importe impreso es peso entero sin que el modelo pierda los centavos
 * que el `Double` de `Payment.IMPORTE` sí puede traer. Almacenamiento y
 * aritmética se quedan en escala 2; solo el papel redondea.
 *
 * [cobradoEn] es el instante del abono y **la entrada de la regla del día**: el
 * ticket solo se imprime el día de negocio de esta fecha.
 */
data class TicketDePago(
    val pagoId: String,
    val cobradoEn: Instant,
    val folio: String,
    val cliente: String,
    val domicilio: String,
    val telefono: String,
    val cobrador: String,
    val metodo: MetodoDeCobro,
    val importe: Money,
    /** Saldo antes del abono = saldo actual + importe. Derivado, nunca leído aparte. */
    val saldoAnterior: Money,
    val saldoActual: Money,
    val totalVenta: Money,
    val parcialidad: Money,
    val abonosPagados: Int,
    val abonosTotales: Int,
    /** Hasta cuatro abonos anteriores a este, del más reciente al más viejo. */
    val ultimosPagos: List<PagoImpreso>
)

/** Un renglón del bloque "últimos pagos". */
data class PagoImpreso(
    val fecha: LocalDate,
    val importe: Money,
    val metodo: MetodoDeCobro
)
