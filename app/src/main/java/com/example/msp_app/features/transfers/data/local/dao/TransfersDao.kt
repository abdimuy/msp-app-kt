package com.example.msp_app.features.transfers.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.msp_app.features.transfers.data.local.entities.TransferDetailEntity
import com.example.msp_app.features.transfers.data.local.entities.TransferEntity
import com.example.msp_app.features.transfers.data.local.entities.TransferWithDetails
import kotlinx.coroutines.flow.Flow

/**
 * DAO for transfers database operations
 */
@Dao
interface TransfersDao {

    // ===== Transfer CRUD Operations =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: TransferEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfers(transfers: List<TransferEntity>)

    @Update
    suspend fun updateTransfer(transfer: TransferEntity)

    @Query("DELETE FROM transfers WHERE docto_in_id = :doctoInId")
    suspend fun deleteTransfer(doctoInId: Int)

    @Query("DELETE FROM transfers")
    suspend fun deleteAllTransfers()

    // ===== Transfer Detail Operations =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransferDetails(details: List<TransferDetailEntity>)

    @Query("DELETE FROM transfer_details WHERE docto_in_id = :doctoInId")
    suspend fun deleteTransferDetails(doctoInId: Int)

    // ===== Query Operations =====

    @Transaction
    @Query("SELECT * FROM transfers ORDER BY fecha DESC, docto_in_id DESC")
    fun getAllTransfersWithDetails(): Flow<List<TransferWithDetails>>

    @Transaction
    @Query("""
        SELECT * FROM transfers
        WHERE (:almacenOrigenId IS NULL OR almacen_origen_id = :almacenOrigenId)
        AND (:almacenDestinoId IS NULL OR almacen_destino_id = :almacenDestinoId)
        AND (:fechaInicio IS NULL OR fecha >= :fechaInicio)
        AND (:fechaFin IS NULL OR fecha <= :fechaFin)
        ORDER BY fecha DESC, docto_in_id DESC
    """)
    fun getFilteredTransfersWithDetails(
        almacenOrigenId: Int? = null,
        almacenDestinoId: Int? = null,
        fechaInicio: String? = null,
        fechaFin: String? = null
    ): Flow<List<TransferWithDetails>>

    @Transaction
    @Query("SELECT * FROM transfers WHERE docto_in_id = :doctoInId")
    suspend fun getTransferWithDetails(doctoInId: Int): TransferWithDetails?

    @Transaction
    @Query("SELECT * FROM transfers WHERE docto_in_id = :doctoInId")
    fun getTransferWithDetailsFlow(doctoInId: Int): Flow<TransferWithDetails?>

    @Query("SELECT * FROM transfers WHERE docto_in_id = :doctoInId")
    suspend fun getTransfer(doctoInId: Int): TransferEntity?

    @Query("SELECT * FROM transfers WHERE sincronizado = 0")
    suspend fun getUnsyncedTransfers(): List<TransferEntity>

    @Query("SELECT COUNT(*) FROM transfers WHERE sincronizado = 0")
    fun getUnsyncedTransfersCount(): Flow<Int>

    // ===== Composite Operations =====

    @Transaction
    suspend fun insertTransferWithDetails(
        transfer: TransferEntity,
        details: List<TransferDetailEntity>
    ) {
        insertTransfer(transfer)
        insertTransferDetails(details)
    }

    @Transaction
    suspend fun deleteTransferComplete(doctoInId: Int) {
        deleteTransferDetails(doctoInId)
        deleteTransfer(doctoInId)
    }

    // ===== Statistics =====

    @Query("SELECT COUNT(*) FROM transfers")
    suspend fun getTransfersCount(): Int

    @Query("SELECT COUNT(*) FROM transfers WHERE fecha = :fecha")
    suspend fun getTransfersCountByDate(fecha: String): Int

    @Query("""
        SELECT COALESCE(SUM(costo_total), 0.0)
        FROM transfers
        WHERE fecha >= :fechaInicio AND fecha <= :fechaFin
    """)
    suspend fun getTotalCostByDateRange(fechaInicio: String, fechaFin: String): Double

    @Query("""
        SELECT COALESCE(SUM(total_productos), 0)
        FROM transfers
        WHERE almacen_origen_id = :almacenId OR almacen_destino_id = :almacenId
    """)
    suspend fun getTotalProductsByWarehouse(almacenId: Int): Int
}
