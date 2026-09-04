package com.example.msp_app.feature.pagos.domain.model

import com.example.msp_app.core.common.money.Money
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Una venta tal como la tiene el teléfono, ya en tipos de dominio — lo que
 * devuelve [com.example.msp_app.feature.pagos.domain.port.VentasPort].
 *
 * Todos los importes son [Money]: el adaptador Room es quien envuelve
 * `SALDO_REST`/`PRECIO_TOTAL`/`ENGANCHE` (`Double`) y `PARCIALIDAD` (`Int`) en
 * el borde. Ningún `Double` de Room llega más adentro que esa línea.
 */
data class DatosDeVenta(
    val ventaId: Int,
    val creditoId: Int,
    val folio: String,
    val clienteId: Int,
    val clienteNombre: String,
    val telefono: String,
    val direccion: String,
    val zona: String,
    val aval: String,
    val notas: String,
    val descripcion: String,
    val fechaVenta: LocalDate?,
    val saldo: Money,
    val parcialidad: Money,
    val frecuencia: String,
    val abonosTotales: Int,
    val totalVenta: Money,
    val precioContado: Money,
    val enganche: Money,
    val vendedor: String
) {
    /** Lo abonado hasta hoy: total financiado menos lo que falta. */
    val abonado: Money get() = totalVenta - saldo
}

/**
 * Una visita del cliente, ya en tipos de dominio — lo que devuelve
 * [com.example.msp_app.feature.pagos.domain.port.VisitasPort].
 *
 * [tipoVisita] viaja **crudo**: la clasificación a estado es de
 * `TipoVisitaCatalogo` (Task 14) y se hace donde se necesita, nunca aquí. Los
 * tres campos de la Task 19 ([fechaPromesa], [montoPrometido], [horaCita])
 * llegan de las columnas que agregó la migración aditiva de la Task 26; hoy
 * vienen en `null` mientras la captura estructurada no exista.
 */
data class VisitaDelCliente(
    val visitaId: String,
    val clienteId: Int,
    val ventaId: Int?,
    val fecha: Instant,
    val tipoVisita: String,
    val nota: String?,
    val fechaPromesa: LocalDate? = null,
    val montoPrometido: Money? = null,
    val horaCita: LocalTime? = null
)
