package com.example.msp_app.data.local.dao.visit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.data.local.entities.VisitEntity

@Dao
interface VisitDao {
    @Query(
        """
        SELECT 
            ID,
            CLIENTE_ID,
            COBRADOR,
            COBRADOR_ID,
            FECHA,
            FORMA_COBRO_ID,
            LAT,
            LNG,
            NOTA,
            TIPO_VISITA,
            ZONA_CLIENTE_ID,
            IMPTE_DOCTO_CC_ID,
            GUARDADO_EN_MICROSIP
        FROM Visit
        WHERE ID = :id
        """
    )
    suspend fun getVisitById(id: String): VisitEntity

    @Insert(
        onConflict = OnConflictStrategy.REPLACE
    )
    suspend fun insertVisit(visit: VisitEntity)

    @Query(
        """
        SELECT 
            ID,
            CLIENTE_ID,
            COBRADOR,
            COBRADOR_ID,
            FECHA,
            FORMA_COBRO_ID,
            LAT,
            LNG,
            NOTA,
            TIPO_VISITA,
            ZONA_CLIENTE_ID,
            IMPTE_DOCTO_CC_ID,
            GUARDADO_EN_MICROSIP
        FROM Visit
        WHERE GUARDADO_EN_MICROSIP = 0
        """
    )
    suspend fun getPendingVisits(): List<VisitEntity>

    @Query("UPDATE Visit SET GUARDADO_EN_MICROSIP = :newState WHERE id = :id")
    suspend fun updateState(id: String, newState: Int)

    @Query("UPDATE Visit SET LAT = :lat, LNG = :lng WHERE id = :id")
    suspend fun updateLocation(id: String, lat: Double, lng: Double)

    @Query("DELETE FROM Visit")
    suspend fun deleteAllVisits()
}