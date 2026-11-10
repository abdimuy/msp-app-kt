package com.example.msp_app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.msp_app.core.database.entities.ClienteBusquedaRow
import com.example.msp_app.core.database.entities.ClienteEntity
import com.example.msp_app.core.database.entities.ClienteRefRow

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

    /**
     * Candidatos del buscador (E-APP-157). La consulta la arma [ConsultasDeBusqueda] con el
     * texto del usuario SOLO como argumentos ligados (`LIKE ? ESCAPE '\\'`), nunca
     * concatenado, y SIN `LIMIT`: el `LIMIT 300` anterior cortaba antes de filtrar todas las
     * palabras y dejaba fuera al cliente en ~30 % de los nombres completos exactos.
     * Devuelve solo id + nombre normalizado (ligero); las entidades de los 20 mostrados se
     * traen con [getByIds].
     */
    @RawQuery
    suspend fun filasDeBusqueda(consulta: SupportSQLiteQuery): List<ClienteBusquedaRow>

    /** Entidades por `CLIENTE_ID`; el llamador trocea (máx. 20 en el buscador). */
    @Query("SELECT * FROM cliente WHERE CLIENTE_ID IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<ClienteEntity>

    @Query("SELECT COUNT(*) FROM cliente")
    suspend fun getCount(): Int

    /**
     * Referencia ligera (solo `NOMBRE`) de un conjunto de clientes por su `CLIENTE_ID` — batch
     * de un solo query, mismo criterio anti-N+1 que `SaleDao.getSaleRefsByAcrIds` (evita un
     * `getById` por fila cuando se enriquece una lista, p. ej. las visitas del reporte de
     * cobranza en `:feature:collectionReport`).
     */
    @Query("SELECT CLIENTE_ID, NOMBRE FROM cliente WHERE CLIENTE_ID IN (:ids)")
    suspend fun getNombresByIds(ids: List<Int>): List<ClienteRefRow>
}
