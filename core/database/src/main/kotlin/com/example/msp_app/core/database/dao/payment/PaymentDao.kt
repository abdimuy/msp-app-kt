package com.example.msp_app.core.database.dao.payment

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.core.common.time.AppTime
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
 * byte-identical internal copy of the legacy `DateUtils.formatIsoDate`, see
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
            NOMBRE_CLIENTE
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
        NOMBRE_CLIENTE
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
    suspend fun getPaymentsByDate(start: String, end: String): List<PaymentEntity>

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

    @Query("UPDATE Payment SET LAT = :lat, LNG = :lng WHERE id = :id")
    suspend fun updateLocation(id: String, lat: Double, lng: Double)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(payment: List<PaymentEntity>)

    @Query("DELETE FROM Payment WHERE DOCTO_CC_ACR_ID = :doctoCcAcrId")
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
     * IDs de filas UUID (captura local) cuyo gemelo numérico ya está local
     * (existe una fila con PAGO_RECIBIDO_ID = ese UUID). Colapsables: la
     * numérica es la canónica. Solo UUID ya subidas (GUARDADO_EN_MICROSIP=1);
     * nunca una captura pendiente. Red de seguridad idempotente para el caso
     * que mergePagos no atrapó (carrera pull-vs-markDone / histórico).
     */
    @Query(
        "SELECT ID FROM Payment WHERE GUARDADO_EN_MICROSIP = 1 " +
            "AND ID IN (SELECT PAGO_RECIBIDO_ID FROM Payment WHERE PAGO_RECIBIDO_ID IS NOT NULL)"
    )
    suspend fun findCollapsibleUuidTwins(): List<String>

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
     * Filtra [ids] a solo aquellos que existen localmente Y ya están
     * confirmados por el servidor (`GUARDADO_EN_MICROSIP = 1`). Usado por
     * el merge de pagos para colapsar el gemelo local UUID de un pago
     * cuando llega su versión numérica con `pago_recibido_id`: nunca borra
     * un pago pendiente de subir (`GUARDADO_EN_MICROSIP = 0`), aunque por
     * error llegara referenciado — un pendiente jamás pudo haber llegado
     * al servidor, así que esto es una red de seguridad defensiva.
     */
    @Query("SELECT ID FROM Payment WHERE ID IN (:ids) AND GUARDADO_EN_MICROSIP = 1")
    suspend fun filterUploadedIDs(ids: List<String>): List<String>

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
