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
    /**
     * `Sale.ESTADO` — la ENTIDAD federativa, no un estado de cobranza. Se llama
     * así para que nadie la confunda con
     * [com.example.msp_app.core.common.cobranza.domain.EstadoCuenta].
     *
     * Existe porque la búsqueda de la lista (Task 17) concatena los mismos SEIS
     * campos que concatenaba `SalesScreen.kt:68` (retirado en la Task 21)
     * —nombre, folio, calle, ciudad, estado, teléfono— y sin este el buscador
     * nuevo encontraría menos que la pantalla que reemplazó.
     */
    val entidad: String,
    val zona: String,
    val aval: String,
    /** Teléfono del aval. `null` mientras no exista la columna — ver [DetalleCliente.telefonoAval]. */
    val telefonoAval: String?,
    val notas: String,
    val descripcion: String,
    val fechaVenta: LocalDate?,
    /**
     * El instante crudo de `Sale.FECHA`, sin recortar a día.
     *
     * Es la SEGUNDA clave del orden de cobranza y existe para tener paridad
     * exacta con `SalesScreen.kt:202` (retirado en la Task 21), que ordenaba por
     * el texto completo de `FECHA` y por lo tanto separaba dos ventas del mismo
     * día por su hora.
     * Ordenar por [fechaVenta] las empataba: una divergencia que nada forzaba,
     * justo en lo único que había orden de copiar sin cambios.
     *
     * `null` cuando `FECHA` no se pudo leer — ver `OrdenDeCobranza`.
     */
    val instanteDeVenta: Instant?,
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
    /** El DÍA de la cita (`CITA_FECHA`). Sin él, [horaCita] no dice cuándo. */
    val fechaCita: LocalDate? = null,
    val horaCita: LocalTime? = null
)
