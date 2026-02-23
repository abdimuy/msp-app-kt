package com.example.msp_app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.msp_app.data.local.entities.ClienteEntity

@Dao
interface ClienteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(clientes: List<ClienteEntity>)

    @Query("DELETE FROM cliente")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(clientes: List<ClienteEntity>) {
        deleteAll()
        insertAll(clientes)
    }

    @Query("SELECT * FROM cliente WHERE NOMBRE LIKE '%' || :query || '%' LIMIT 200")
    suspend fun searchByNombre(query: String): List<ClienteEntity>

    @Query("SELECT * FROM cliente WHERE NOMBRE LIKE :prefix || '%' LIMIT 200")
    suspend fun searchByPrefix(prefix: String): List<ClienteEntity>

    @Query("SELECT COUNT(*) FROM cliente")
    suspend fun getCount(): Int
}
