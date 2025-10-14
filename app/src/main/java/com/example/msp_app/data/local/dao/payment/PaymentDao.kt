package com.example.msp_app.data.local.dao.payment

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.data.local.entities.OverduePaymentsEntity
import com.example.msp_app.data.local.entities.PaymentEntity
import com.example.msp_app.data.models.payment.PaymentLocation
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    suspend fun getPaymentById(id: String): PaymentEntity

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
                FECHA_HORA_PAGO BETWEEN :start  AND :end
                AND FORMA_COBRO_ID IN 
                (
                    ${Constants.PAGO_EN_EFECTIVO_ID},
                    ${Constants.PAGO_CON_CHEQUE_ID},
                    ${Constants.PAGO_CON_TRANSFERENCIA_ID}
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
                FECHA_HORA_PAGO BETWEEN :start  AND :end
                AND FORMA_COBRO_ID = ${Constants.CONDONACION_ID}
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

    suspend fun getPaymentsGroupedByDaySince(startDate: String): Map<String, List<PaymentEntity>> {
        val endDate = LocalDate
            .now()
            .plusDays(100)
            .format(DateTimeFormatter.ISO_DATE)
        val payments = getPaymentsByDate(startDate, endDate)

        val paymentsByDay = payments.groupBy {
            DateUtils.formatIsoDate(it.FECHA_HORA_PAGO, "yyyy-MM-dd")
        }
        return paymentsByDay.mapValues { (_, paymentList) ->
            paymentList.sortedByDescending { it.FECHA_HORA_PAGO }
        }.toSortedMap(compareByDescending { it })
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
            /* multiplicamos por el factor según frecuencia: */
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
                    COALESCE(MAX(payment.FECHA_HORA_PAGO), DATE('now')) AS FECHA_ULT_PAGO,
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
                        ELSE 0
                      END AS PARCIALIDADES_TRANSCURRIDAS
                FROM sales
                LEFT JOIN payment
                  ON sales.DOCTO_CC_ID = payment.DOCTO_CC_ACR_ID
                  /* Filtramos solo pagos válidos, excluyendo condonaciones */
                  AND payment.FORMA_COBRO_ID IN (
                      ${Constants.PAGO_EN_EFECTIVO_ID},
                      ${Constants.PAGO_CON_CHEQUE_ID},
                      ${Constants.PAGO_CON_TRANSFERENCIA_ID}
                  )
                GROUP BY sales.DOCTO_CC_ID, sales.FREC_PAGO
            ) AS sales
        ) AS sales
          ON payment.DOCTO_CC_ACR_ID = sales.DOCTO_CC_ID
        WHERE payment.FECHA_HORA_PAGO >= :startDate
          AND payment.FORMA_COBRO_ID IN (
              ${Constants.PAGO_EN_EFECTIVO_ID},
              ${Constants.PAGO_CON_CHEQUE_ID},
              ${Constants.PAGO_CON_TRANSFERENCIA_ID}
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

    @Query(
        """
    SELECT
        sales.DOCTO_CC_ID,
        sales.FECHA_ULT_PAGO,
        sales.NUM_IMPORTES,
        sales.PARCIALIDADES_TRANSCURRIDAS,
        CASE 
            WHEN ((sales.PARCIALIDADES_TRANSCURRIDAS * sales.PARCIALIDAD 
                  - (sales.PRECIO_TOTAL - sales.SALDO_REST)) / sales.PARCIALIDAD) 
                > (sales.SALDO_REST / sales.PARCIALIDAD)
            THEN (sales.SALDO_REST / sales.PARCIALIDAD)
            ELSE ((sales.PARCIALIDADES_TRANSCURRIDAS * sales.PARCIALIDAD 
                  - (sales.PRECIO_TOTAL - sales.SALDO_REST - sales.ENGANCHE)) / sales.PARCIALIDAD)
        END AS NUM_PAGOS_ATRASADOS
    FROM (
        SELECT
            s.DOCTO_CC_ID,
            COALESCE(MAX(p.FECHA_HORA_PAGO), DATE('now')) AS FECHA_ULT_PAGO,
            COALESCE(COUNT(p.FECHA_HORA_PAGO), 0) AS NUM_IMPORTES,
            COALESCE(SUM(p.IMPORTE), 0) AS TOTAL_IMPORTE,
            s.FREC_PAGO,
            s.SALDO_REST,
            s.PRECIO_TOTAL,
            s.PARCIALIDAD,
            s.ENGANCHE,
            (
                JULIANDAY(
                    CASE
                        WHEN s.SALDO_REST = 0 THEN MAX(p.FECHA_HORA_PAGO)
                        ELSE DATE('now')
                    END
                ) - JULIANDAY(s.FECHA)
            ) / CASE 
                WHEN s.FREC_PAGO = 'SEMANAL' THEN 7
                WHEN s.FREC_PAGO = 'QUINCENAL' THEN 15
                WHEN s.FREC_PAGO = 'MENSUAL' THEN 30
                ELSE 1
            END AS PARCIALIDADES_TRANSCURRIDAS
        FROM sales AS s
        LEFT JOIN payment AS p ON s.DOCTO_CC_ID = p.DOCTO_CC_ACR_ID
        GROUP BY s.DOCTO_CC_ID, s.FREC_PAGO
    ) AS sales
    """
    )
    suspend fun getOverduePayments(): List<OverduePaymentsEntity>

    @Query(
        """
    SELECT
        sales.DOCTO_CC_ID,
        sales.FECHA_ULT_PAGO,
        sales.NUM_IMPORTES,
        sales.PARCIALIDADES_TRANSCURRIDAS,
        CASE 
            WHEN ((sales.PARCIALIDADES_TRANSCURRIDAS * sales.PARCIALIDAD 
                  - (sales.PRECIO_TOTAL - sales.SALDO_REST)) / sales.PARCIALIDAD) 
                > (sales.SALDO_REST / sales.PARCIALIDAD)
            THEN (sales.SALDO_REST / sales.PARCIALIDAD)
            ELSE ((sales.PARCIALIDADES_TRANSCURRIDAS * sales.PARCIALIDAD 
                  - (sales.PRECIO_TOTAL - sales.SALDO_REST - sales.ENGANCHE)) / sales.PARCIALIDAD)
        END AS NUM_PAGOS_ATRASADOS
    FROM (
        SELECT
            s.DOCTO_CC_ID,
            COALESCE(MAX(p.FECHA_HORA_PAGO), DATE('now')) AS FECHA_ULT_PAGO,
            COALESCE(COUNT(p.FECHA_HORA_PAGO), 0) AS NUM_IMPORTES,
            COALESCE(SUM(p.IMPORTE), 0) AS TOTAL_IMPORTE,
            s.FREC_PAGO,
            s.SALDO_REST,
            s.PRECIO_TOTAL,
            s.PARCIALIDAD,
            s.ENGANCHE,
            (
                JULIANDAY(
                    CASE
                        WHEN s.SALDO_REST = 0 THEN MAX(p.FECHA_HORA_PAGO)
                        ELSE DATE('now')
                    END
                ) - JULIANDAY(s.FECHA)
            ) / CASE 
                WHEN s.FREC_PAGO = 'SEMANAL' THEN 7
                WHEN s.FREC_PAGO = 'QUINCENAL' THEN 15
                WHEN s.FREC_PAGO = 'MENSUAL' THEN 30
                ELSE 1
            END AS PARCIALIDADES_TRANSCURRIDAS
        FROM sales AS s
        LEFT JOIN payment AS p ON s.DOCTO_CC_ID = p.DOCTO_CC_ACR_ID
        WHERE s.DOCTO_CC_ID = :saleId
        GROUP BY s.DOCTO_CC_ID, s.FREC_PAGO
    ) AS sales
    """
    )
    suspend fun getOverduePaymentBySaleId(saleId: Int): OverduePaymentsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePayment(payment: PaymentEntity)

    @Query("UPDATE Payment SET GUARDADO_EN_MICROSIP = :newEstado WHERE id = :id")
    suspend fun updateEstado(id: String, newEstado: Int)

    @Query("UPDATE Payment SET LAT = :lat, LNG = :lng WHERE id = :id")
    suspend fun updateLocation(id: String, lat: Double, lng: Double)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAll(payment: List<PaymentEntity>)

    @Query("DELETE FROM payment")
    suspend fun deleteAll()
}
