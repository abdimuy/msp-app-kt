package com.example.msp_app.core.database.dao.payment

import androidx.room.ColumnInfo

/**
 * Una fila de [PaymentDao.getPuntosDeLaRuta]: lo mínimo para saber si un punto
 * es de un solo cliente o de muchos.
 *
 * **No es una entidad y no tiene tabla.** Es la proyección de cuatro columnas
 * que Room arma para esa consulta. Vive aquí, junto al DAO que la produce, y no
 * en `entities/`, porque confundirla con `PaymentEntity` invitaría a agregarle
 * campos "ya que estamos" — y el ahorro de esta consulta es exactamente no
 * traerlos.
 *
 * `LAT`/`LNG` llegan no nulos porque la consulta ya descartó los nulos y el par
 * en cero. Declararlos `Double` y no `Double?` traslada esa garantía al tipo en
 * vez de dejarla como una nota que alguien tiene que recordar leer.
 *
 * **Los nombres van en Kotlin y el mapeo por `@ColumnInfo`**, a diferencia de
 * `PaymentEntity`, que replica las columnas en MAYÚSCULAS. Ésta es una
 * proyección nueva, no una entidad heredada: no hay nada que preservar, y
 * `@ColumnInfo` deja la columna dicha explícitamente **una vez** en vez de
 * escondida en la forma de escribir el identificador.
 */
data class PuntoDeLaRutaRow(
    @ColumnInfo(name = "CLIENTE_ID") val clienteId: Int,
    @ColumnInfo(name = "COBRADOR") val cobrador: String,
    @ColumnInfo(name = "LAT") val lat: Double,
    @ColumnInfo(name = "LNG") val lng: Double
)

/**
 * Una fila de [PaymentDao.getPuntosDelCliente]: un abono medido de este cliente.
 *
 * Misma razón de existir que [PuntoDeLaRutaRow] —una proyección, no una
 * entidad— y misma garantía sobre `LAT`/`LNG`: la consulta ya descartó los
 * nulos y el par en cero, así que aquí son `Double` y no `Double?`.
 *
 * `FORMA_COBRO_ID` viaja **crudo**. Traducirlo a "esto es una transferencia" es
 * una regla de dominio y vive del lado del feature, con el conjunto canónico de
 * ids; meterla en el SQL crearía una segunda definición que puede despegarse de
 * la primera — el mismo argumento que ya sostiene el parámetro de
 * [PaymentDao.getCollectedAmounts].
 */
data class MedicionDelClienteRow(
    @ColumnInfo(name = "ID") val id: String,
    @ColumnInfo(name = "DOCTO_CC_ACR_ID") val ventaId: Int,
    @ColumnInfo(name = "FECHA_HORA_PAGO") val fechaHoraPago: String,
    @ColumnInfo(name = "COBRADOR") val cobrador: String,
    @ColumnInfo(name = "FORMA_COBRO_ID") val formaCobroId: Int,
    @ColumnInfo(name = "LAT") val lat: Double,
    @ColumnInfo(name = "LNG") val lng: Double,
    /** El importe del abono: alimenta el "abono típico" del mapa de lugares. */
    @ColumnInfo(name = "IMPORTE") val importe: Double
)
