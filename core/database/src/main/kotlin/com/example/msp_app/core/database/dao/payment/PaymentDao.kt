package com.example.msp_app.core.database.dao.payment

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.entities.DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR
import com.example.msp_app.core.database.entities.FORMA_COBRO_CONDONACION
import com.example.msp_app.core.database.entities.OverduePaymentsEntity
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.PaymentLocation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * IDs de FORMA_COBRO_ID/CONCEPTO_CC_ID inlineados como literales en las
 * queries de abajo (157=efectivo, 158=cheque, 52569=transferencia,
 * 137026=condonacion). Copiados de `com.example.msp_app.core.utils.
 * Constants` (que se queda en `:app`, no es alcanzable desde
 * `:core:database`). Si esos IDs cambian en Microsip, actualizar ambos
 * lados.
 */

/**
 * Ventana hacia adelante usada como límite superior "abierto" al agrupar
 * pagos por día desde [PaymentDao.getPaymentsGroupedByDaySince] /
 * [PaymentDao.observePaymentsGroupedByDaySince]: los pagos no se registran
 * con fecha futura dentro de este horizonte, así que basta con `hoy + 100
 * días` como cota superior en vez de una fecha real de "hoy" que se
 * recalcularía en cada request.
 */
private const val PAYMENT_LOOKAHEAD_WINDOW_DAYS = 100L

/**
 * Day-grouping key for [PaymentDao.getPaymentsGroupedByDaySince] /
 * [PaymentDao.observePaymentsGroupedByDaySince]: the calendar date (business
 * zone, `America/Mexico_City`, via [AppTime.toBusinessDate]) that
 * [fechaHoraPago] falls on, formatted `yyyy-MM-dd`.
 *
 * **Money-path fix:** this used to be computed in the device's timezone (a
 * byte-identical internal copy of the legacy date util's `formatIsoDate`, see
 * `date-lib-audit.md` bug #1/#7). A cobrador with the phone set to another
 * zone (travel, roaming, misconfiguration) could see a payment near
 * midnight grouped under the wrong day. Grouping is now anchored to the
 * business zone regardless of device settings — see
 * `PaymentDayGroupingTest` for the characterization of the old vs. new
 * result on the same instant.
 *
 * Preserves the legacy fallback contract: on unparsable input, the raw
 * string is returned instead of throwing, so a single corrupted
 * `FECHA_HORA_PAGO` row degrades to its own ungrouped bucket instead of
 * crashing the whole query.
 */
private fun dayKeyOf(fechaHoraPago: String): String = AppTime.parseWireFormatOrNull(fechaHoraPago)
    ?.let { AppTime.toBusinessDate(it).toString() }
    ?: fechaHoraPago

/**
 * ## Por qué TRES de estas proyecciones cargan `PAGO_RECIBIDO_ID` y las demás no
 *
 * Las lecturas que alimentan el HISTORIAL de abonos —[PaymentDao.getPaymentById],
 * [PaymentDao.getPaymentsBySaleId] y [PaymentDao.getPaymentsByDate]— la traen
 * porque el guard anti-duplicado de la pantalla de abono se resuelve preguntando
 * *"¿está mi abono en el historial?"*, y `Payment.ID` **no sobrevive a la
 * sincronización**: `CobranzaSyncManager.mergePagos` borra la fila del UUID y
 * reinserta la canónica bajo la llave numérica de Microsip, conservando el UUID
 * en esta columna. Sin ella en la proyección, la columna llega `null` por el
 * default de la data class —Room no se queja— y el guard se suelta después de
 * cada merge.
 *
 * Las demás proyecciones NO la cargan a propósito: no la leen, y una lista de
 * columnas dice qué usa cada consulta.
 */
@Dao
interface PaymentDao {

    @Query(
        """
        SELECT 
            ID,
            COBRADOR,
            DOCTO_CC_ACR_ID,
            DOCTO_CC_ID,
            FECHA_HORA_PAGO,
            GUARDADO_EN_MICROSIP,
            IMPORTE,
            LAT,
            LNG,
            CLIENTE_ID,
            COBRADOR_ID,
            FORMA_COBRO_ID,
            ZONA_CLIENTE_ID,
            NOMBRE_CLIENTE,
            PAGO_RECIBIDO_ID
        FROM Payment
        WHERE ID = :id
        """
    )
    suspend fun getPaymentById(id: String): PaymentEntity?

    @Query(
        """SELECT 
        ID,
        COBRADOR,
        DOCTO_CC_ACR_ID,
        DOCTO_CC_ID,
        FECHA_HORA_PAGO,
        GUARDADO_EN_MICROSIP,
        IMPORTE,
        LAT,
        LNG,
        CLIENTE_ID,
        COBRADOR_ID,
        FORMA_COBRO_ID,
        ZONA_CLIENTE_ID,
        NOMBRE_CLIENTE,
        PAGO_RECIBIDO_ID
    FROM Payment
    WHERE DOCTO_CC_ACR_ID = :saleId"""
    )
    suspend fun getPaymentsBySaleId(saleId: Int): List<PaymentEntity>

    @Query(
        """SELECT 
                ID,
                COBRADOR,
                DOCTO_CC_ACR_ID,
                DOCTO_CC_ID,
                FECHA_HORA_PAGO,
                GUARDADO_EN_MICROSIP,
                IMPORTE,
                LAT,
                LNG,
                CLIENTE_ID,
                COBRADOR_ID,
                FORMA_COBRO_ID,
                ZONA_CLIENTE_ID,
                NOMBRE_CLIENTE,
                PAGO_RECIBIDO_ID
            FROM Payment
            WHERE
                FECHA_HORA_PAGO >= :start AND FECHA_HORA_PAGO < :end
                AND FORMA_COBRO_ID IN
                (
                    157,
                    158,
                    52569
                )
            ORDER BY FECHA_HORA_PAGO DESC
        """
    )
    suspend fun getPaymentsByDate(start: String, end: String): List<PaymentEntity>

    /**
     * **Sólo los importes** de los abonos de las formas de cobro que le pasen,
     * sin fecha y sin venta.
     *
     * Es la muestra de la que sale la línea base de la ruta (el percentil de lo
     * que esta ruta paga de verdad). Son miles de filas y de cada una lo único
     * que se mira es el peso, así que devolver `PaymentEntity` completa sería
     * pagar catorce columnas por una.
     *
     * Las formas de cobro entran **como parámetro** y no escritas en el SQL a
     * propósito: el conjunto canónico es `VentanaCobro.FORMAS_COBRO_COBRANZA` y
     * duplicarlo aquí crearía una segunda definición de "qué cuenta como
     * cobranza" que puede despegarse de la primera sin que nada avise.
     */
    @Query(
        """
            SELECT IMPORTE
            FROM Payment
            WHERE FORMA_COBRO_ID IN (:formasDeCobro) AND IMPORTE > 0
        """
    )
    suspend fun getCollectedAmounts(formasDeCobro: Set<Int>): List<Double>

    /**
     * **Todos los puntos medidos de la ruta, de todos los clientes.**
     *
     * Es la consulta que alimenta la detección de **puntos compartidos** de
     * `:core:geo`: un punto sólo se puede saber compartido **mirando a los demás
     * clientes**, así que a diferencia de casi todo lo demás en este DAO, aquí
     * NO hay filtro por cliente ni por venta. Ése es justamente el punto.
     *
     * Existe porque el 2026-09-24 se midió que hay lugares donde cobran a
     * decenas de clientes distintos —la tienda, con 381 clientes de 30
     * cobradores; el punto fijo de un cobrador, con 48 clientes y 86 % de
     * transferencias— y **sin esta lectura el 6.4 % de los clientes tendría su
     * "puerta" señalando un lugar donde no vive**.
     *
     * ## Cuatro columnas y no la entidad entera
     *
     * De cada fila sólo se miran cliente, cobrador y coordenada. Son decenas de
     * miles de filas en un teléfono cargado; devolver `PaymentEntity` sería
     * pagar catorce columnas por cuatro. Mismo criterio que [getCollectedAmounts].
     *
     * ## El filtro de `LAT`/`LNG`
     *
     * `IS NOT NULL` descarta lo que nunca se midió, y el `NOT (LAT = 0 AND
     * LNG = 0)` descarta el **centinela de "sin señal"**: el par `(0, 0)` es un
     * punto de verdad —está en el Golfo de Guinea— y contarlo como un lugar
     * contagiaría de "compartido" a todos los clientes que alguna vez cobraron
     * sin GPS. Es el mismo criterio que `UbicacionDelCobro.medida` aplica del
     * lado del dominio.
     *
     * **No lleva filtro por forma de cobro, a propósito.** Una transferencia
     * también ocupa lugar en el mapa y también aporta a saber que un punto es
     * compartido. Lo que no hace es votar por la puerta, y esa regla vive en
     * `LugaresDelCliente`, no aquí.
     */
    @Query(
        """
            SELECT CLIENTE_ID, COBRADOR, LAT, LNG
            FROM Payment
            WHERE LAT IS NOT NULL AND LNG IS NOT NULL
              AND NOT (LAT = 0.0 AND LNG = 0.0)
        """
    )
    suspend fun getPuntosDeLaRuta(): List<PuntoDeLaRutaRow>

    /**
     * **Los abonos medidos de un cliente**, con lo justo para agrupar, filtrar y
     * decidir cuál lugar es la puerta.
     *
     * Va **por `CLIENTE_ID` y no por venta**, a diferencia de
     * [getPaymentsBySaleId]: la pantalla de ubicación se abre desde el detalle
     * de cliente y lo que enseña son los lugares del cliente, no los de una
     * cuenta. Medido el 2026-09-24: de los clientes con dos o más ventas con
     * GPS, el **65 % las tiene a menos de 40 m entre sí** — la misma puerta.
     * Partir por venta sería partir un lugar en dos por un accidente de
     * contabilidad.
     *
     * `DOCTO_CC_ACR_ID` viaja igual, porque el **filtro por venta** de la
     * pantalla lo necesita; lo que no hace es decidir el agrupamiento.
     *
     * Mismo filtro de coordenada que [getPuntosDeLaRuta]: fuera los nulos y
     * fuera el par `(0, 0)`, que es el centinela de "sin señal" y no un lugar.
     * Y **sin filtro por forma de cobro**, por lo mismo: una transferencia se
     * dibuja; que no vote por la puerta lo decide `LugaresDelCliente`.
     */
    @Query(
        """
            SELECT ID, DOCTO_CC_ACR_ID, FECHA_HORA_PAGO, COBRADOR, FORMA_COBRO_ID, LAT, LNG, IMPORTE
            FROM Payment
            WHERE CLIENTE_ID = :clienteId
              AND LAT IS NOT NULL AND LNG IS NOT NULL
              AND NOT (LAT = 0.0 AND LNG = 0.0)
            ORDER BY FECHA_HORA_PAGO DESC
        """
    )
    suspend fun getPuntosDelCliente(clienteId: Int): List<MedicionDelClienteRow>

    /**
     * El cliente dueño de una venta.
     *
     * Existe porque el detalle de venta conoce el `DOCTO_CC_ACR_ID` y **no** el
     * `CLIENTE_ID`, y el mapa de ubicación enseña los lugares del **cliente**:
     * medido el 2026-09-24, de los clientes con dos o más ventas con GPS el
     * 65 % las tiene a menos de 40 m entre sí — o sea la misma puerta. Abrir el
     * mapa acotado a una venta partiría un lugar en dos por un accidente de
     * contabilidad.
     *
     * `LIMIT 1` sin `ORDER BY` a propósito: **todas las filas de una venta
     * tienen el mismo `CLIENTE_ID`**, así que cualquiera sirve y ordenar sería
     * pagar por una garantía que la columna ya da. Devuelve `null` cuando la
     * venta no tiene ningún abono en el teléfono, y entonces no hay nada que
     * mapear.
     */
    @Query("SELECT CLIENTE_ID FROM Payment WHERE DOCTO_CC_ACR_ID = :ventaId LIMIT 1")
    suspend fun getClienteDeVenta(ventaId: Int): Int?

    @Query(
        """SELECT
                ID,
                COBRADOR,
                DOCTO_CC_ACR_ID,
                DOCTO_CC_ID,
                FECHA_HORA_PAGO,
                GUARDADO_EN_MICROSIP,
                IMPORTE,
                LAT,
                LNG,
                CLIENTE_ID,
                COBRADOR_ID,
                FORMA_COBRO_ID,
                ZONA_CLIENTE_ID,
                NOMBRE_CLIENTE
            FROM Payment
            WHERE
                FECHA_HORA_PAGO >= :start AND FECHA_HORA_PAGO < :end
                AND FORMA_COBRO_ID = 137026
            ORDER BY FECHA_HORA_PAGO DESC
        """
    )
    suspend fun getForgivenessByDate(start: String, end: String): List<PaymentEntity>

    @Query(
        """SELECT 
                ID,
                COBRADOR,
                DOCTO_CC_ACR_ID,
                DOCTO_CC_ID,
                FECHA_HORA_PAGO,
                GUARDADO_EN_MICROSIP,
                IMPORTE,
                LAT,
                LNG,
                CLIENTE_ID,
                COBRADOR_ID,
                FORMA_COBRO_ID,
                ZONA_CLIENTE_ID,
                NOMBRE_CLIENTE
            FROM Payment
            WHERE 
                GUARDADO_EN_MICROSIP = 0
            ORDER BY FECHA_HORA_PAGO ASC"""
    )
    suspend fun getPendingPayments(): List<PaymentEntity>

    @Query(
        """
        SELECT
            ID, COBRADOR, DOCTO_CC_ACR_ID, DOCTO_CC_ID, FECHA_HORA_PAGO,
            GUARDADO_EN_MICROSIP, IMPORTE, LAT, LNG, CLIENTE_ID,
            COBRADOR_ID, FORMA_COBRO_ID, ZONA_CLIENTE_ID, NOMBRE_CLIENTE
        FROM Payment
        ORDER BY FECHA_HORA_PAGO DESC
        """
    )
    suspend fun getAllPayments(): List<PaymentEntity>

    suspend fun getPaymentsGroupedByDaySince(startDate: String): Map<String, List<PaymentEntity>> {
        val endDate = LocalDate
            .now()
            .plusDays(PAYMENT_LOOKAHEAD_WINDOW_DAYS)
            .format(DateTimeFormatter.ISO_DATE)
        val payments = getPaymentsByDate(startDate, endDate)

        val paymentsByDay = payments.groupBy { dayKeyOf(it.FECHA_HORA_PAGO) }
        return paymentsByDay.mapValues { (_, paymentList) ->
            paymentList.sortedByDescending { it.FECHA_HORA_PAGO }
        }.toSortedMap(compareByDescending { it })
    }

    /**
     * Reactive variant of [getPaymentsByDate]. Room re-emits the full list
     * every time a row in `Payment` is inserted/updated/deleted within the
     * date+forma_cobro filter, so any subscriber stays in sync with persisted
     * state without manual re-query calls.
     */
    @Query(
        """SELECT
                ID,
                COBRADOR,
                DOCTO_CC_ACR_ID,
                DOCTO_CC_ID,
                FECHA_HORA_PAGO,
                GUARDADO_EN_MICROSIP,
                IMPORTE,
                LAT,
                LNG,
                CLIENTE_ID,
                COBRADOR_ID,
                FORMA_COBRO_ID,
                ZONA_CLIENTE_ID,
                NOMBRE_CLIENTE
            FROM Payment
            WHERE
                FECHA_HORA_PAGO >= :start AND FECHA_HORA_PAGO < :end
                AND FORMA_COBRO_ID IN
                (
                    157,
                    158,
                    52569
                )
            ORDER BY FECHA_HORA_PAGO DESC
        """
    )
    fun observePaymentsByDate(start: String, end: String): Flow<List<PaymentEntity>>

    /**
     * Reactive sibling of [getPaymentsGroupedByDaySince]: returns a [Flow]
     * that emits the same day-grouped map whenever the underlying `Payment`
     * table changes. The end date is fixed at subscription (now + 100 days),
     * which matches the one-shot semantics — payments cannot be future-dated
     * meaningfully within that horizon.
     *
     * The grouping/sort is performed downstream from Room's emission and
     * does not block Room's own thread.
     */
    fun observePaymentsGroupedByDaySince(
        startDate: String
    ): Flow<Map<String, List<PaymentEntity>>> {
        val endDate = LocalDate
            .now()
            .plusDays(PAYMENT_LOOKAHEAD_WINDOW_DAYS)
            .format(DateTimeFormatter.ISO_DATE)
        return observePaymentsByDate(startDate, endDate).map { payments ->
            payments
                .groupBy { dayKeyOf(it.FECHA_HORA_PAGO) }
                .mapValues { (_, list) -> list.sortedByDescending { it.FECHA_HORA_PAGO } }
                .toSortedMap(compareByDescending { it })
        }
    }

    @Query(
        """
        SELECT 
            LAT,
            LNG,
            DOCTO_CC_ACR_ID
        FROM
            Payment
        WHERE
            LAT IS NOT NULL
            AND LNG IS NOT NULL
            AND LAT != 0
            AND LNG != 0
        """
    )
    suspend fun getAllLocations(): List<PaymentLocation>

    @Query(
        """
        SELECT
            SUM(PORCENTAJE) AS TOTAL_PORCENTAJE
        FROM (
            SELECT
                sales.DOCTO_CC_ID,
                /* calculamos el porcentaje base: */
                CASE
                  WHEN SUM(payment.IMPORTE) / sales.PARCIALIDAD >= 1
                  THEN (
                    CASE
                      WHEN sales.NUM_PAGOS_ATRASADOS >= SUM(payment.IMPORTE) / sales.PARCIALIDAD
                      THEN SUM(payment.IMPORTE) / sales.PARCIALIDAD
                      ELSE 1
                    END
                  )
                  ELSE SUM(payment.IMPORTE) / sales.PARCIALIDAD
                END
                /* y ahora lo multiplicamos por el factor según frecuencia: */
                * CASE sales.FREC_PAGO
                    WHEN 'SEMANAL'   THEN 1
                    WHEN 'QUINCENAL' THEN 2
                    WHEN 'MENSUAL'   THEN 4
                    ELSE 1
                  END
                AS PORCENTAJE
            FROM payment
            INNER JOIN (
                SELECT
                    sales.DOCTO_CC_ID,
                    sales.CLIENTE,
                    sales.FECHA_ULT_PAGO,
                    sales.NUM_IMPORTES,
                    sales.TOTAL_IMPORTE,
                    sales.FREC_PAGO,
                    sales.PARCIALIDADES_TRANSCURRIDAS,
                    CASE
                      WHEN ( (sales.PARCIALIDADES_TRANSCURRIDAS * sales.PARCIALIDAD
                              - (sales.PRECIO_TOTAL - sales.SALDO_REST)) / sales.PARCIALIDAD )
                           > (sales.SALDO_REST / sales.PARCIALIDAD)
                      THEN (sales.SALDO_REST / sales.PARCIALIDAD)
                      ELSE ( (sales.PARCIALIDADES_TRANSCURRIDAS * sales.PARCIALIDAD
                              - (sales.PRECIO_TOTAL - sales.SALDO_REST - sales.ENGANCHE)) / sales.PARCIALIDAD )
                    END AS NUM_PAGOS_ATRASADOS,
                    sales.PARCIALIDAD
                FROM (
                    SELECT
                        sales.DOCTO_CC_ID,
                        sales.CLIENTE,
                        sales.FECHA,
                        COALESCE(MAX(payment.FECHA_HORA_PAGO), sales.FECHA) AS FECHA_ULT_PAGO,
                        COALESCE(COUNT(payment.FECHA_HORA_PAGO), 0) AS NUM_IMPORTES,
                        COALESCE(SUM(payment.IMPORTE), 0) AS TOTAL_IMPORTE,
                        sales.FREC_PAGO,
                        sales.SALDO_REST,
                        sales.PRECIO_TOTAL,
                        sales.ENGANCHE,
                        sales.PARCIALIDAD,
                        ( JULIANDAY(
                              CASE
                                WHEN sales.SALDO_REST = 0
                                THEN MAX(payment.FECHA_HORA_PAGO)
                                ELSE DATE('now')
                              END
                          )
                          - JULIANDAY(sales.FECHA) )
                        / CASE
                            WHEN sales.FREC_PAGO = 'SEMANAL'   THEN 7
                            WHEN sales.FREC_PAGO = 'QUINCENAL' THEN 15
                            WHEN sales.FREC_PAGO = 'MENSUAL'   THEN 30
                            ELSE 1
                          END AS PARCIALIDADES_TRANSCURRIDAS
                    FROM sales
                    LEFT JOIN payment
                      ON sales.DOCTO_CC_ID = payment.DOCTO_CC_ACR_ID
                      AND payment.FORMA_COBRO_ID IN (
                        157,
                        158,
                        52569
                      )
                    GROUP BY sales.DOCTO_CC_ID, sales.FREC_PAGO
                ) AS sales
            ) AS sales
              ON payment.DOCTO_CC_ACR_ID = sales.DOCTO_CC_ID
            WHERE payment.FECHA_HORA_PAGO >= :startDate
              AND payment.FORMA_COBRO_ID IN (
                157,
                158,
                52569
              )
            GROUP BY payment.DOCTO_CC_ACR_ID
        ) t;
    """
    )
    suspend fun getAdjustedPaymentPercentage(startDate: String): Double?

    /**
     * Variante REACTIVA de [getAdjustedPaymentPercentage] (misma consulta, palabra por palabra;
     * mismo criterio de duplicación que [observePaymentsByDate] frente a [getPaymentsByDate] —
     * Room exige el SQL literal en la anotación).
     *
     * Existe por el defecto D6: el "Porcentaje (Cobro)" del inicio se leía UNA sola vez y se
     * quedaba pegado en 0.00% si esa lectura caía antes de que los datos estuvieran en Room.
     * Con este [Flow], cualquier INSERT/UPDATE en `Payment` o `sales` re-dispara la consulta y
     * el porcentaje se recalcula solo — sin que el cobrador tenga que salir y volver a entrar.
     */
    @Query(
        """
        SELECT
            SUM(PORCENTAJE) AS TOTAL_PORCENTAJE
        FROM (
            SELECT
                sales.DOCTO_CC_ID,
                /* calculamos el porcentaje base: */
                CASE
                  WHEN SUM(payment.IMPORTE) / sales.PARCIALIDAD >= 1
                  THEN (
                    CASE
                      WHEN sales.NUM_PAGOS_ATRASADOS >= SUM(payment.IMPORTE) / sales.PARCIALIDAD
                      THEN SUM(payment.IMPORTE) / sales.PARCIALIDAD
                      ELSE 1
                    END
                  )
                  ELSE SUM(payment.IMPORTE) / sales.PARCIALIDAD
                END
                /* y ahora lo multiplicamos por el factor según frecuencia: */
                * CASE sales.FREC_PAGO
                    WHEN 'SEMANAL'   THEN 1
                    WHEN 'QUINCENAL' THEN 2
                    WHEN 'MENSUAL'   THEN 4
                    ELSE 1
                  END
                AS PORCENTAJE
            FROM payment
            INNER JOIN (
                SELECT
                    sales.DOCTO_CC_ID,
                    sales.CLIENTE,
                    sales.FECHA_ULT_PAGO,
                    sales.NUM_IMPORTES,
                    sales.TOTAL_IMPORTE,
                    sales.FREC_PAGO,
                    sales.PARCIALIDADES_TRANSCURRIDAS,
                    CASE
                      WHEN ( (sales.PARCIALIDADES_TRANSCURRIDAS * sales.PARCIALIDAD
                              - (sales.PRECIO_TOTAL - sales.SALDO_REST)) / sales.PARCIALIDAD )
                           > (sales.SALDO_REST / sales.PARCIALIDAD)
                      THEN (sales.SALDO_REST / sales.PARCIALIDAD)
                      ELSE ( (sales.PARCIALIDADES_TRANSCURRIDAS * sales.PARCIALIDAD
                              - (sales.PRECIO_TOTAL - sales.SALDO_REST - sales.ENGANCHE)) / sales.PARCIALIDAD )
                    END AS NUM_PAGOS_ATRASADOS,
                    sales.PARCIALIDAD
                FROM (
                    SELECT
                        sales.DOCTO_CC_ID,
                        sales.CLIENTE,
                        sales.FECHA,
                        COALESCE(MAX(payment.FECHA_HORA_PAGO), sales.FECHA) AS FECHA_ULT_PAGO,
                        COALESCE(COUNT(payment.FECHA_HORA_PAGO), 0) AS NUM_IMPORTES,
                        COALESCE(SUM(payment.IMPORTE), 0) AS TOTAL_IMPORTE,
                        sales.FREC_PAGO,
                        sales.SALDO_REST,
                        sales.PRECIO_TOTAL,
                        sales.ENGANCHE,
                        sales.PARCIALIDAD,
                        ( JULIANDAY(
                              CASE
                                WHEN sales.SALDO_REST = 0
                                THEN MAX(payment.FECHA_HORA_PAGO)
                                ELSE DATE('now')
                              END
                          )
                          - JULIANDAY(sales.FECHA) )
                        / CASE
                            WHEN sales.FREC_PAGO = 'SEMANAL'   THEN 7
                            WHEN sales.FREC_PAGO = 'QUINCENAL' THEN 15
                            WHEN sales.FREC_PAGO = 'MENSUAL'   THEN 30
                            ELSE 1
                          END AS PARCIALIDADES_TRANSCURRIDAS
                    FROM sales
                    LEFT JOIN payment
                      ON sales.DOCTO_CC_ID = payment.DOCTO_CC_ACR_ID
                      AND payment.FORMA_COBRO_ID IN (
                        157,
                        158,
                        52569
                      )
                    GROUP BY sales.DOCTO_CC_ID, sales.FREC_PAGO
                ) AS sales
            ) AS sales
              ON payment.DOCTO_CC_ACR_ID = sales.DOCTO_CC_ID
            WHERE payment.FECHA_HORA_PAGO >= :startDate
              AND payment.FORMA_COBRO_ID IN (
                157,
                158,
                52569
              )
            GROUP BY payment.DOCTO_CC_ACR_ID
        ) t;
    """
    )
    fun observeAdjustedPaymentPercentage(startDate: String): Flow<Double?>

    @Query(
        """
        SELECT 
            DISTINCT CAST(IMPORTE AS INTEGER)
        FROM Payment
        WHERE DOCTO_CC_ACR_ID = :saleId
        ORDER BY IMPORTE DESC
        """
    )
    suspend fun getSuggestedAmountsBySaleId(saleId: Int): List<Int>

    @Query("SELECT * FROM overdue_payments_view")
    suspend fun getOverduePayments(): List<OverduePaymentsEntity>

    @Query("SELECT * FROM overdue_payments_view WHERE DOCTO_CC_ID = :saleId")
    suspend fun getOverduePaymentBySaleId(saleId: Int): OverduePaymentsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePayment(payment: PaymentEntity)

    @Query("UPDATE Payment SET GUARDADO_EN_MICROSIP = :newEstado WHERE id = :id")
    suspend fun updateEstado(id: String, newEstado: Int)

    /**
     * Guarda el DOCTO_CC_ID que Microsip asignó al aplicar el pago. Sin él la
     * app no puede casar el pago local con el que baja del servidor y el mismo
     * pago aparece dos veces en los totales.
     */
    @Query("UPDATE Payment SET DOCTO_CC_ID = :doctoCcId WHERE id = :id")
    suspend fun updateDoctoCcId(id: String, doctoCcId: Int)

    @Query("UPDATE Payment SET LAT = :lat, LNG = :lng WHERE id = :id")
    suspend fun updateLocation(id: String, lat: Double, lng: Double)

    /**
     * Marca una condonación PENDIENTE como rechazada por el servidor: la suelta
     * (`GUARDADO_EN_MICROSIP = 1`) y le pone
     * [DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR]. Devuelve cuántas filas tocó.
     *
     * Los cuatro cerrojos del `WHERE` la vuelven una transición de una sola vez:
     * sólo una condonación (`FORMA_COBRO_ID`), sin documento (`= 0`) y todavía
     * pendiente (`= 0`). Una segunda corrida del worker sobre la misma fila toca
     * cero filas: [soltarCondonacionRechazada] la marca una sola vez.
     */
    @Query(
        """
        UPDATE Payment
        SET GUARDADO_EN_MICROSIP = 1, DOCTO_CC_ID = $DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR
        WHERE ID = :id
          AND FORMA_COBRO_ID = $FORMA_COBRO_CONDONACION
          AND DOCTO_CC_ID = 0
          AND GUARDADO_EN_MICROSIP = 0
        """
    )
    suspend fun marcarCondonacionRechazada(id: String): Int

    /**
     * **Condonaciones "fantasma"**: soltadas (`GUARDADO_EN_MICROSIP = 1`) sin
     * documento (`DOCTO_CC_ID = 0`) — la firma de las que el servidor rechazó
     * antes de que existiera [DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR]
     * (E-APP-029/E-APP-032), indistinguibles desde el teléfono de una aplicada
     * cuya respuesta se perdió. Las lee la reparación única
     * (`ReparacionDeCondonaciones`), que le pregunta al servidor por cada una.
     *
     * Sólo capturas del teléfono (`ID LIKE '%-%'`) que ninguna fila del
     * servidor nombra por `PAGO_RECIBIDO_ID` (ésas las colapsa el sync).
     */
    @Query(
        """
        SELECT
            ID, COBRADOR, DOCTO_CC_ACR_ID, DOCTO_CC_ID, FECHA_HORA_PAGO,
            GUARDADO_EN_MICROSIP, IMPORTE, LAT, LNG, CLIENTE_ID, COBRADOR_ID,
            FORMA_COBRO_ID, ZONA_CLIENTE_ID, NOMBRE_CLIENTE, PAGO_RECIBIDO_ID
        FROM Payment
        WHERE FORMA_COBRO_ID = $FORMA_COBRO_CONDONACION
          AND GUARDADO_EN_MICROSIP = 1
          AND DOCTO_CC_ID = 0
          AND ID LIKE '%-%'
          AND ID NOT IN (
              SELECT PAGO_RECIBIDO_ID FROM Payment
              WHERE PAGO_RECIBIDO_ID IS NOT NULL AND PAGO_RECIBIDO_ID <> ID
          )
        """
    )
    suspend fun condonacionesFantasma(): List<PaymentEntity>

    /**
     * Marca una condonación fantasma como rechazada por el servidor. Una sola vez:
     * sólo si sigue soltada y sin documento. No toca el saldo (eso lo hace el
     * refresco de su venta, contra el servidor).
     */
    @Query(
        """
        UPDATE Payment
        SET DOCTO_CC_ID = $DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR
        WHERE ID = :id
          AND FORMA_COBRO_ID = $FORMA_COBRO_CONDONACION
          AND DOCTO_CC_ID = 0
          AND GUARDADO_EN_MICROSIP = 1
        """
    )
    suspend fun marcarCondonacionFantasma(id: String): Int

    /**
     * Fija el `SALDO_REST` de la venta del [cargo] (`sales.DOCTO_CC_ID`, que casa
     * con `Payment.DOCTO_CC_ACR_ID`, ver `SaleIdSpaces`) a un valor ABSOLUTO. Lo
     * usa sólo `RefrescoDelSaldoDeLaVenta`, bajo el mutex de escritura del sync.
     */
    @Query("UPDATE sales SET SALDO_REST = :saldo WHERE DOCTO_CC_ID = :cargo")
    suspend fun fijarSaldoDeLaVenta(cargo: Int, saldo: Double): Int

    /**
     * **Suelta una condonación que el servidor rechazó y la marca como tal.**
     * NO toca el saldo.
     *
     * El servidor contestó `422 pago_saldo_insuficiente` con `X-Intent-Captured`:
     * no la aplicó y la resguardó para la oficina (E-APP-029).
     * [marcarCondonacionRechazada] hace que la fila deje de leerse "no
     * reconocida" ([sumImporteNoReconocidoPorElServidor] pide `DOCTO_CC_ID = 0`,
     * su "límite conocido 1") y pase a decir "rechazada". **No se borra**: nunca
     * se borra una captura que el servidor no nombró.
     *
     * ## Por qué el saldo NO se toca aquí
     *
     * Sumar el importe de vuelta deja el saldo por encima del real (medido: 1000
     * contra 500), porque un 422 por saldo ocurre justo cuando el teléfono tenía
     * un saldo viejo. El único valor correcto es el del servidor, y leerlo es
     * trabajo aparte: `RefrescoDelSaldoDeLaVenta`, bajo el mutex del sync, que el
     * worker encola después de esta marca. Si no hay señal, el saldo se queda
     * donde lo dejó la captura —nunca arriba de lo que había— hasta que el
     * refresco corra.
     *
     * Si la marca no toca nada —no es condonación, ya tenía documento o ya
     * estaba soltada— queda el comportamiento de siempre: sólo se marca enviada.
     *
     * @return `true` si esta llamada la marcó como rechazada.
     */
    @Transaction
    suspend fun soltarCondonacionRechazada(id: String): Boolean {
        val marcada = marcarCondonacionRechazada(id) == 1
        if (!marcada) updateEstado(id, 1)
        return marcada
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(payment: List<PaymentEntity>)

    /**
     * Borra el cache de pagos de un cargo, preservando SIEMPRE las capturas
     * que el cobrador todavía no logró subir (`GUARDADO_EN_MICROSIP = 0`).
     *
     * La llama el merge del sync cuando el backend avisa que el cargo fue
     * cancelado en Microsip. La cancelación es definitiva para lo que ya está
     * confirmado, pero NO autoriza a tirar trabajo del cobrador: si capturó un
     * pago en la calle sobre un cargo que en oficina cancelaron ese mismo día,
     * borrarlo aquí lo desaparecería sin dejar rastro — nadie podría siquiera
     * saber que existió. Conservado, el worker lo intentará subir y el
     * servidor lo rechazará contra un cargo cancelado, quedando registrado en
     * la captura de intentos fallidos para resolverse desde el escritorio.
     * Perder dinero en silencio nunca es una opción; fallar ruidosamente sí.
     */
    @Query("DELETE FROM Payment WHERE DOCTO_CC_ACR_ID = :doctoCcAcrId AND GUARDADO_EN_MICROSIP = 1")
    suspend fun deleteByDoctoCcAcrId(doctoCcAcrId: Int)

    /**
     * Tombstone-aware single-row delete. Used by the cobranza sync when the
     * backend reports a pago as `cancelado=true`: the row in `MSP_PAGOS_VENTAS`
     * is kept server-side with `IMPORTE=0` to make the cancellation visible to
     * the incremental cursor, and the client deletes it locally so the
     * cobrador never sees a phantom $0 pago. Idempotent: if the row is not
     * present (e.g. the tombstone arrived for a pago that was never seen
     * locally), this is a no-op DELETE with zero rows affected. Mirrors the
     * cargo-side [deleteByDoctoCcAcrId] but scoped to a single
     * IMPTE_DOCTO_CC_ID (the PK of `Payment`).
     */
    @Query("DELETE FROM Payment WHERE ID = :id")
    suspend fun deleteByID(id: String)

    /**
     * Bulk variant of [deleteByID]. Used by the digest-driven reconcile when
     * a set of orphaned IDs (present locally but absent on the server) must
     * be evicted in one round-trip. Empty list short-circuits to no-op.
     */
    @Query("DELETE FROM Payment WHERE ID IN (:ids)")
    suspend fun deleteByIDs(ids: List<String>)

    /**
     * IDs de filas UUID (captura local) cuyo gemelo numérico ya está local:
     * existe OTRA fila con `PAGO_RECIBIDO_ID` = ese UUID. Colapsables — la
     * numérica es la canónica. Red de seguridad idempotente para el caso que
     * `mergePagos` no atrapó (carrera pull-vs-markDone / histórico).
     *
     * **El criterio es la evidencia del servidor, no la bandera local.** Esta
     * consulta exigía además `GUARDADO_EN_MICROSIP = 1` sobre la fila UUID,
     * con el argumento de que "un pendiente jamás pudo haber llegado al
     * servidor". El argumento está invertido: `PAGO_RECIBIDO_ID` sólo lo
     * escribe `PagoDto.toEntity()` con lo que trae el canal de sync, así que
     * una fila que referencia el UUID **prueba** que el servidor recibió esa
     * captura y le asignó su id numérico en Microsip. La bandera dice lo que
     * este teléfono alcanzó a anotar; el `PAGO_RECIBIDO_ID` dice lo que el
     * servidor efectivamente hizo. Y el aviso del servidor sale dentro de la
     * misma transacción que escribe el pago, así que puede ganarle al
     * `markDone` del worker; peor aún, si la respuesta HTTP nunca llega
     * (timeout) la bandera se queda en 0 para siempre y el duplicado se
     * vuelve permanente en la pantalla del cobrador.
     *
     * Lo que sigue protegiendo a una captura que NUNCA llegó al servidor es
     * justamente esa referencia: el UUID lo genera el teléfono, así que el
     * servidor no puede nombrar uno que no recibió. Un pendiente sin subir no
     * está referenciado por ninguna fila y nunca entra a este resultado.
     *
     * `PAGO_RECIBIDO_ID <> ID` descarta la auto-referencia: sin ese cerrojo
     * una fila que se apuntara a sí misma se borraría siendo la única copia.
     */
    @Query(
        "SELECT ID FROM Payment WHERE ID IN (" +
            "SELECT PAGO_RECIBIDO_ID FROM Payment " +
            "WHERE PAGO_RECIBIDO_ID IS NOT NULL AND PAGO_RECIBIDO_ID <> ID)"
    )
    suspend fun findCollapsibleUuidTwins(): List<String>

    /**
     * Borra las filas que dejó el sync legacy (Node) para los documentos de
     * pago [doctoCcIds] que el canal v2 acaba de entregar.
     *
     * Los dos canales guardan el mismo pago con llaves distintas:
     *
     *   - legacy: `MSP_PAGOS_RECIBIDOS.ID` (UUID de la captura) cuando el
     *     pago se capturó desde la app, o `"<DOCTO_CC_ID>-<IMPTE_DOCTO_CC_ID>"`
     *     cuando se capturó en oficina — ver el `COALESCE` de
     *     `getAllVentasByZona` en el API Node.
     *   - v2 (Go): `IMPTE_DOCTO_CC_ID` a secas, siempre numérico puro.
     *
     * Como `Payment.ID` es la PK, sin este borrado el mismo pago queda dos
     * veces en Room y todos los totales del cobrador salen al doble.
     *
     * El match es por `DOCTO_CC_ID` (el documento de pago en Microsip), que
     * es exacto: identifica el mismo abono en ambos canales sin depender de
     * `pago_recibido_id` — que es NULL para todo el histórico anterior al
     * cutover, porque el Node nunca escribió
     * `MSP_PAGOS_RECIBIDOS.IMPTE_DOCTO_CC_ID`.
     *
     * Tres cerrojos hacen segura la operación:
     *  - `GUARDADO_EN_MICROSIP = 1`: una captura pendiente de subir jamás se
     *    toca (además de que su `DOCTO_CC_ID` es 0 hasta que se aplica).
     *  - `ID LIKE '%-%'`: solo formatos legacy (UUID o compuesto). La fila
     *    canónica del canal v2 es numérica pura y nunca se borra a sí misma.
     *  - `DOCTO_CC_ID > 0`: el 0 es el centinela de "aún sin documento".
     */
    @Query(
        """
        DELETE FROM Payment
        WHERE GUARDADO_EN_MICROSIP = 1
          AND ID LIKE '%-%'
          AND DOCTO_CC_ID > 0
          AND DOCTO_CC_ID IN (:doctoCcIds)
        """
    )
    suspend fun deleteLegacyTwinsByDoctoCcIds(doctoCcIds: List<Int>): Int

    /**
     * Variante global de [deleteLegacyTwinsByDoctoCcIds] para el histórico
     * que ya está en Room y que el sync incremental nunca volverá a mandar
     * (su `UPDATED_AT` no cambió, o cayó fuera de `FECHA_CARGA_INICIAL`).
     *
     * Borra la fila legacy **solo si su gemelo numérico ya existe en local**:
     * la subconsulta es la que evita perder los pagos viejos que el canal v2
     * no alcanza a reponer — sin ella, los totales históricos del cobrador se
     * desplomarían en vez de duplicarse. Los mismos tres cerrojos de la
     * variante por lote aplican aquí.
     */
    @Query(
        """
        DELETE FROM Payment
        WHERE GUARDADO_EN_MICROSIP = 1
          AND ID LIKE '%-%'
          AND DOCTO_CC_ID > 0
          AND DOCTO_CC_ID IN (
              SELECT DOCTO_CC_ID FROM Payment WHERE ID NOT LIKE '%-%'
          )
        """
    )
    suspend fun deleteLegacyTwins(): Int

    /**
     * Returns the full set of locally-cached pago IDs for the given zone.
     * Used by CobranzaReconciler to compute the local digest fingerprint
     * and to enumerate orphans (phantoms). Excludes nothing — there is no
     * client-side tombstone flag; the row either exists or it doesn't.
     *
     * The server /digest and /ids filters are now aligned with /sync
     * (CANCELADO='N' + CONCEPTO_CC_ID IN (87327,27969) + SALDO > 0).
     * In steady state `extras` should be 0; non-zero extras across many
     * runs indicate a server-side filter regression.
     */
    @Query(
        "SELECT ID FROM Payment WHERE ZONA_CLIENTE_ID = :zonaId ORDER BY CAST(ID AS INTEGER) ASC"
    )
    suspend fun getActiveIDsByZona(zonaId: Int): List<String>

    /**
     * Filtra [ids] a sólo aquellos que existen localmente. Lo usa el merge de
     * pagos para colapsar el gemelo local UUID cuando llega la versión
     * numérica del pago: [ids] son los `pago_recibido_id` que trae la página
     * del servidor, y esta consulta dice cuáles de esos UUID siguen ocupando
     * una fila propia en Room.
     *
     * **Ya no exige `GUARDADO_EN_MICROSIP = 1`** — ver el razonamiento
     * completo en [findCollapsibleUuidTwins]. En corto: que el servidor
     * devuelva `pago_recibido_id = <uuid>` es evidencia más fuerte que la
     * bandera local, porque prueba que el pago ya está en Microsip con su id
     * asignado, mientras que la bandera sólo dice si este teléfono alcanzó a
     * anotarlo. Exigirla dejaba sin colapsar exactamente la carrera que
     * produce el duplicado en campo.
     *
     * La protección contra borrar una captura que nunca subió no vive aquí
     * sino en el origen de [ids]: son UUID que **el servidor nombró**, y el
     * servidor no puede nombrar un UUID generado en el teléfono que nunca
     * recibió. El llamador además descarta las auto-referencias.
     */
    @Query("SELECT ID FROM Payment WHERE ID IN (:ids)")
    suspend fun filterExistingIDs(ids: List<String>): List<String>

    /**
     * Cuenta los pagos del cargo cuyo `FECHA_HORA_PAGO` cae dentro de la
     * ventana del cobrador (>= `fechaIso`). Lo usa el merge del sync para
     * decidir si una venta saldada que llega debe conservarse — basta con
     * que tenga un pago en ventana para mantenerla visible.
     */
    @Query(
        """
        SELECT COUNT(*)
        FROM Payment
        WHERE DOCTO_CC_ACR_ID = :doctoCcAcrId
          AND FECHA_HORA_PAGO >= :fechaIso
        """
    )
    suspend fun countPagosDesde(doctoCcAcrId: Int, fechaIso: String): Int

    /**
     * Suma de los pagos de un cargo que **el servidor todavía no ha
     * reconocido** — el descuento que la fila del servidor aún no trae.
     *
     * ## Por qué existe
     *
     * Al cobrar, `insertPaymentAndUpdateSale` descuenta el `SALDO_REST` en la
     * misma transacción del insert. El sync reescribe esa fila cada 30 s con
     * el DTO del servidor, que todavía no sabe del pago, y el saldo salta
     * hacia atrás delante del cobrador.
     *
     * ## Por qué NO se arregla preservando el valor local
     *
     * Porque `SALDO_REST` **lo posee el servidor**, a diferencia de
     * `ESTADO_COBRANZA` y `DIA_TEMPORAL_COBRANZA`, que son locales y por eso
     * sí se preservan tal cual. Conservar el número local a ciegas es decirle
     * al teléfono que ignore al servidor: si mientras el pago está en vuelo la
     * oficina cancela la venta, otro cobrador aplica un pago o entra una
     * condonación, el teléfono **no se entera nunca**.
     *
     * La forma correcta es que el servidor siga mandando y sólo se le reste lo
     * que aún no ha visto:
     *
     * ```
     * SALDO_REST = saldo del servidor − suma de esta consulta
     * ```
     *
     * Cada tick parte del valor del servidor, así que cualquier cambio de allá
     * entra siempre, y lo local es sólo un delta por los pagos en vuelo.
     *
     * ## Qué cuenta como "no reconocido"
     *
     * El criterio NO es `GUARDADO_EN_MICROSIP`. Esa bandera es local y el
     * documento del sync (§6) la descarta expresamente como evidencia: el
     * worker puede no alcanzar a marcarla, y un pago RECHAZADO también la deja
     * en 1. La evidencia fuerte es la del servidor.
     *
     * **El criterio es `DOCTO_CC_ID = 0`: Microsip no le ha asignado
     * documento, así que el saldo del servidor no puede incluirlo.** Es
     * evidencia POSITIVA de no-inclusión, y viene del servidor: el `0` es el
     * centinela con el que nace la captura (`PaymentFactory`), y el único que
     * lo cambia es `PendingPaymentsWorker.persistDoctoCcId` con el
     * `docto_cc_id` que devolvió la respuesta de `POST /pagos`. Esa respuesta
     * sale después de que Microsip aplicó el abono, y el recálculo de
     * `MSP_SALDOS_VENTAS` va en la MISMA transacción que el abono (trigger
     * `MSP_PAGOS_IMPORTES_AIUD` → `MSP_RECOMPUTE_PAGO`). Por eso:
     *
     * > si el teléfono tiene `DOCTO_CC_ID > 0`, el saldo que publica el
     * > servidor **ya trae ese pago descontado**.
     *
     * ## Por qué NO basta "ninguna fila numérica la nombra"
     *
     * Ese era el criterio anterior y es AUSENCIA de evidencia, no evidencia.
     * La fila numérica viaja por `/sync/pagos` y el saldo por `/sync/ventas`,
     * con cursores independientes; `syncNow` consulta pagos ANTES que ventas,
     * así que la foto de pagos es siempre la más vieja de las dos. Todo pago
     * aplicado en esa ventana llega con su saldo ya descontado y sin su fila
     * numérica: el saldo se descuenta dos veces. Medido en producción
     * (crédito 15689642, 2026-09-23): el saldo se publicó 3 ms antes que el
     * pago y un abono de $100 bajó el saldo $200.
     *
     * Peor todavía, ese descuento se fosilizaba. Cuando el colapso del gemelo
     * por fin borraba la fila UUID, nadie recalculaba el saldo, y
     * `SALDO_REST` sólo se reescribe cuando llega otro `VentaDto` — que para
     * ese cargo ya había pasado.
     *
     * Con `DOCTO_CC_ID` el descuento deja de depender del OTRO canal: cambia
     * cuando cambia la fila del pago, que es local. Y las dos transiciones se
     * compensan exactamente — en el instante en que el servidor aplica el
     * abono, el saldo que publica baja en `IMPORTE` y esta suma deja de
     * incluirlo por el mismo `IMPORTE` —, así que el número mostrado es
     * continuo y el orden de llegada deja de importar.
     *
     * ## Los otros dos cerrojos, que se conservan
     *
     * `ID LIKE '%-%'` y "nadie la nombra por `PAGO_RECIBIDO_ID`" siguen
     * exigiéndose. Son redundantes en el caso sano —una fila del servidor
     * siempre trae `DOCTO_CC_ID > 0`— y esa redundancia es el punto: cada uno
     * por su lado basta para NO restar. `PAGO_RECIBIDO_ID <> ID` descarta la
     * auto-referencia, igual que en [pagoRecibidoIdsReclamados].
     *
     * ## Límites conocidos
     *
     * 1. Un pago que Microsip RECHAZÓ se queda como fila local con
     *    `DOCTO_CC_ID = 0` para siempre, así que se resta para siempre y el
     *    saldo mostrado queda por debajo del real. **Este arreglo NO lo
     *    cierra**; se cierra cuando la fila local pueda decir "rechazado".
     * 2. Un pago que el servidor SÍ aplicó pero cuya respuesta nunca llegó al
     *    teléfono queda con `DOCTO_CC_ID = 0` y se sigue restando de más
     *    —igual que antes de este cambio, sin regresión— hasta que el colapso
     *    del gemelo borra la fila. La firma existe en la flota:
     *    `GUARDADO_EN_MICROSIP = 1` con `DOCTO_CC_ID = 0` (incidente del
     *    2026-08-31), y `persistDoctoCcId` es best-effort a propósito.
     *
     * ## Por qué es por cargo y no por lote
     *
     * Se consulta una vez por venta, dentro del bucle de `mergeVentas`, en vez
     * de resolver la página entera con un `IN (...)`. Eso evita el tope de 999
     * parámetros de SQLite en Android ≤ 11 (§7.4 del documento del sync), que
     * ninguna prueba puede detectar: Robolectric usa el SQLite de escritorio,
     * con tope alto.
     */
    @Query(
        """
        SELECT COALESCE(SUM(IMPORTE), 0)
        FROM Payment
        WHERE DOCTO_CC_ACR_ID = :doctoCcAcrId
          AND DOCTO_CC_ID = 0
          AND ID LIKE '%-%'
          AND ID NOT IN (
              SELECT PAGO_RECIBIDO_ID FROM Payment
              WHERE PAGO_RECIBIDO_ID IS NOT NULL AND PAGO_RECIBIDO_ID <> ID
          )
        """
    )
    suspend fun sumImporteNoReconocidoPorElServidor(doctoCcAcrId: Int): Double

    @Query("DELETE FROM payment")
    suspend fun deleteAll()

    /**
     * Borra solo los pagos ya confirmados por el servidor
     * (`GUARDADO_EN_MICROSIP = 1`), preservando los pendientes de subir
     * (`= 0`). Se usa en la limpieza por cambio de zona/cobrador: el cache
     * descargado de la zona anterior se descarta, pero el trabajo sin
     * sincronizar del cobrador NUNCA se pierde — se sube después con su
     * propia atribución (COBRADOR_ID/zona horneados en la fila).
     */
    @Query("DELETE FROM payment WHERE GUARDADO_EN_MICROSIP = 1")
    suspend fun deleteUploaded()
}
