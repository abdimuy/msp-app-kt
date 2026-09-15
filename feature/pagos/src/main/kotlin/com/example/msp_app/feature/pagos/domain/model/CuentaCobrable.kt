package com.example.msp_app.feature.pagos.domain.model

import com.example.msp_app.core.common.money.Money
import java.time.LocalDate

/**
 * Lo mínimo que hay que saber de una cuenta para sugerir cuánto pedirle.
 *
 * ## Por qué existe
 *
 * [com.example.msp_app.feature.pagos.domain.MontosSugeridos] nació atado a
 * [DetalleVenta], que es el modelo de la pantalla de UNA venta. El detalle del
 * **cliente** necesita las mismas tres cifras y no tiene ese modelo: tiene
 * [VentaDelCliente], que es más chico a propósito (no carga historial, ni
 * productos, ni garantía).
 *
 * Las salidas eran dos: duplicar las fórmulas, o fabricar un [DetalleVenta] de
 * mentiras para poder llamarlas. **Las dos son inaceptables en el camino del
 * dinero** — la primera deja dos fórmulas que se pueden despegar, y la segunda
 * mete un modelo inventado en el cálculo de lo que se le va a cobrar a alguien.
 *
 * Este contrato es la tercera: los dos modelos YA tenían estos ocho datos, así
 * que se nombra lo que comparten y las fórmulas quedan en un solo lugar,
 * llamadas desde las dos pantallas con el mismo código.
 *
 * Todo lo de aquí es dominio puro: sin Android, sin Room, sin reloj. El "hoy"
 * entra por parámetro en quien lo necesita.
 */
interface CuentaCobrable {

    /** Lo que falta por pagar. Tope duro de TODO sugerido. */
    val saldo: Money

    /** La cuota del periodo. */
    val parcialidad: Money

    /** El estado ya derivado del catálogo de ocho — de él sale `abonoDelPeriodo`. */
    val estado: EstadoDelPeriodo

    /** Lo abonado hasta hoy, enganche incluido. */
    val abonado: Money

    /** El enganche, que **no** es una cuota y por eso se descuenta del atraso. */
    val enganche: Money

    /** El día de la venta. `null` cuando `FECHA` no se pudo leer. */
    val fechaVenta: LocalDate?

    /** "semanal" / "quincenal" / "mensual"; cualquier otra cosa cuenta por día. */
    val frecuencia: String

    /** "Hoy liquida con" de esta cuenta, o `null` si no admite liquidación. */
    val liquidacion: Liquidacion?
}
