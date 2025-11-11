package com.example.msp_app.features.transfers.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.msp_app.features.transfers.data.local.entities.PendingTransferEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for pending transfers (offline queue)
 */
@Dao
interface PendingTransfersDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingTransfer(transfer: PendingTransferEntity): Long

    @Update
    suspend fun updatePendingTransfer(transfer: PendingTransferEntity)

    @Query("SELECT * FROM pending_transfers ORDER BY created_at ASC")
    suspend fun getAllPendingTransfers(): List<PendingTransferEntity>

    @Query("SELECT * FROM pending_transfers ORDER BY created_at ASC")
    fun getAllPendingTransfersFlow(): Flow<List<PendingTransferEntity>>

    @Query("SELECT * FROM pending_transfers WHERE id = :id")
    suspend fun getPendingTransfer(id: Long): PendingTransferEntity?

    @Query("DELETE FROM pending_transfers WHERE id = :id")
    suspend fun deletePendingTransfer(id: Long)

    @Query("DELETE FROM pending_transfers")
    suspend fun deleteAllPendingTransfers()

    @Query("SELECT COUNT(*) FROM pending_transfers")
    suspend fun getPendingTransfersCount(): Int

    @Query("SELECT COUNT(*) FROM pending_transfers")
    fun getPendingTransfersCountFlow(): Flow<Int>

    @Query("UPDATE pending_transfers SET retry_count = retry_count + 1, last_error = :error, updated_at = :timestamp WHERE id = :id")
    suspend fun incrementRetryCount(id: Long, error: String, timestamp: Long = System.currentTimeMillis())
}
