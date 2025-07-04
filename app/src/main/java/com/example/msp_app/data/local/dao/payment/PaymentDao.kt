package com.example.msp_app.data.local.dao.payment;

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.data.local.entities.PaymentEntity
import com.example.msp_app.data.models.payment.PaymentLocation
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Dao
interface PaymentDao {

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
    WHERE ID = :id"""
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
