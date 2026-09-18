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
    /**
     * `IMPORTE_PAGO_PROMEDIO` — lo que este cliente **suele dar** por abono.
     *
     * **Se lee, no se deriva**, por el mismo argumento que [atrasos]: la columna
     * ya viajaba en la proyección de `SaleDao` sin que nadie la pintara, y
     * recalcular el promedio sobre el historial local daría OTRA cifra para el
     * mismo cliente según cuántos pagos alcanzó a sincronizar ese teléfono. Dos
     * cobradores verían dos "suele dar" distintos del mismo cliente.
     *
     * `null` cuando la columna viene vacía: sin dato no se afirma un promedio.
     */
    val pagoPromedio: Money?,
    val frecuencia: String,
    /**
     * `DIA_COBRANZA` — el día de la semana en que a esta venta le toca la ruta,
     * tal como lo manda el servidor. Vacío cuando la fila no lo trae.
     */
    val diaDeCobranza: String,
    /**
     * `DIA_TEMPORAL_COBRANZA` — el día al que se movió **esta vuelta**, cuando
     * alguien lo cambió (`SaleDao.updateTemporaryCollectionDate`).
     *
     * Manda sobre [diaDeCobranza] mientras traiga algo, y solo por esta vuelta.
     * Vacío es el caso normal: nadie lo movió.
     */
    val diaTemporal: String,
    val abonosTotales: Int,
    val totalVenta: Money,
    val precioContado: Money,
    val enganche: Money,
    val vendedor: String,
    /**
     * `NUM_PAGOS_ATRASADOS` — cuántas parcialidades DEBERÍA llevar pagadas a
     * estas alturas menos las que lleva, topado por las que le faltan.
     *
     * **Se lee, no se deriva.** Lo calcula la vista `overdue_payments_view`
     * (`PaymentEntity.OVERDUE_PAYMENTS_VIEW_SQL`, migración 21→22) y es el mismo
     * número que la pantalla vieja lleva años mostrando. Derivarlo aquí daría
     * otra cifra y el cobrador lo leería como un defecto, no como una mejora;
     * si algún día se comprueba que la vista miente, se corrige la vista.
     *
     * `null` en la columna se toma como 0: sin dato no hay atraso que afirmar.
     */
    val atrasos: Int,
    /** `FECHA_ULT_PAGO` — el día del último pago, o `null` si nunca pagó. */
    val fechaUltimoPago: LocalDate?
) {
    /** Lo abonado hasta hoy: total financiado menos lo que falta. */
    val abonado: Money get() = totalVenta - saldo

    /**
     * El día en que hay que pasar: [diaTemporal] si alguien lo movió, y si no
     * [diaDeCobranza].
     *
     * La precedencia vive **aquí y en un solo lugar** a propósito. Es la misma
     * que aplica `SaleDao.updateTemporaryCollectionDate` al escribir, y dejarla
     * a cargo de cada pantalla es cómo se termina con dos sitios diciendo días
     * distintos del mismo cliente.
     */
    val diaDeRuta: String get() = diaTemporal.ifBlank { diaDeCobranza }
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
    val horaCita: LocalTime? = null,
    /**
     * Dónde se paró el cobrador cuando registró esta visita.
     *
     * `VisitEntity.LAT`/`LNG` ya viajaban en la tabla y nadie las mapeaba, igual
     * que pasaba con las del abono. Es lo que deja abrir el mapa en el punto de
     * ESA visita —"aquí toqué y no estaba"— en vez de mandar siempre al punto
     * del último cobro, que es otra puerta y otro día.
     *
     * `null` cuando el teléfono no lo pudo tomar. Ojo: en esa tabla la columna
     * **no admite nulos**, así que "no lo pudo tomar" llega como el par en cero
     * y lo traduce [UbicacionDelCobro.medida] — ver su KDoc.
     */
    val ubicacion: UbicacionDelCobro? = null
)
