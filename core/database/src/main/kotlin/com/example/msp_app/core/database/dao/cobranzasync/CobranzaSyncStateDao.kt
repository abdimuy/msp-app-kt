package com.example.msp_app.core.database.dao.cobranzasync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.core.database.entities.CobranzaSyncStateEntity

@Dao
interface CobranzaSyncStateDao {

    @Query("SELECT * FROM cobranza_sync_state WHERE RESOURCE = :resource")
    suspend fun get(resource: String): CobranzaSyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: CobranzaSyncStateEntity)

    @Query(
        """
        UPDATE cobranza_sync_state
        SET LAST_ERROR = :error,
            LAST_SYNCED_AT = :at
        WHERE RESOURCE = :resource
        """
    )
    suspend fun recordError(resource: String, error: String, at: String)

    @Query("DELETE FROM cobranza_sync_state WHERE RESOURCE = :resource")
    suspend fun clear(resource: String)
}
