package com.example.msp_app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
     * Candidatos para el buscador de la Nueva Venta: `word` debe llegar YA normalizado
     * (`normalizeForSearch` — minúsculas, sin acentos, un solo espacio entre palabras),
     * comparado contra `NOMBRE_NORMALIZADO`, la misma normalización aplicada al insertar
     * (`ClienteRepository.syncFromServer`). `ClienteRepository.searchClientes` la llama
     * UNA vez con la palabra más larga de la consulta (la más selectiva) y luego filtra
     * en memoria que las demás palabras también aparezcan, en cualquier orden — así
     * "guadalupe maria" encuentra "MARIA GUADALUPE RIVERA".
     *
     * `LIMIT 300`: el padrón mide ~43,700 clientes activos (medido en la base de
     * referencia); sin tope, una palabra corta y común devolvería miles de filas por
     * tecla. El límite puede dejar fuera un match legítimo si la palabra ancla es muy
     * genérica — mismo riesgo, mismo orden de magnitud, que tenía el `LIMIT 200`
     * original.
     */
    @Query("SELECT * FROM cliente WHERE NOMBRE_NORMALIZADO LIKE '%' || :word || '%' LIMIT 300")
    suspend fun searchByNormalizedWord(word: String): List<ClienteEntity>

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
