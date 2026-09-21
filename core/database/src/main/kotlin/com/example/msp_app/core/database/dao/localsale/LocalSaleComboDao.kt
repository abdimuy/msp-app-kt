package com.example.msp_app.core.database.dao.localsale

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.msp_app.core.database.entities.LocalSaleComboEntity

@Dao
interface LocalSaleComboDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCombo(combo: LocalSaleComboEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCombos(combos: List<LocalSaleComboEntity>)

    @Query("SELECT * FROM local_sale_combos WHERE LOCAL_SALE_ID = :saleId")
    suspend fun getCombosForSale(saleId: String): List<LocalSaleComboEntity>

    @Query("DELETE FROM local_sale_combos WHERE LOCAL_SALE_ID = :saleId")
    suspend fun deleteCombosForSale(saleId: String)

    @Query(
        "UPDATE local_sale_combos SET SERVER_UUID = :serverUuid WHERE COMBO_ID = :comboId AND LOCAL_SALE_ID = :saleId"
    )
    suspend fun updateServerUuid(comboId: String, saleId: String, serverUuid: String)

    @Transaction
    suspend fun replaceCombosForSale(saleId: String, combos: List<LocalSaleComboEntity>) {
        deleteCombosForSale(saleId)
        if (combos.isNotEmpty()) {
            insertAllCombos(combos)
        }
    }

    @Query(
        "DELETE FROM local_sale_combos WHERE LOCAL_SALE_ID = :saleId AND COMBO_ID IN (:comboIds)"
    )
    suspend fun deleteCombosByIds(saleId: String, comboIds: List<String>)

    /**
     * Merge que CONSERVA `SERVER_UUID` — mismo criterio que
     * `LocalSaleProductDao.mergeProductsForSale`, aquí con la clave
     * `(COMBO_ID, LOCAL_SALE_ID)`: el combo que sobrevive es el que llega con
     * el MISMO `COMBO_ID` que ya tenía (el llamador lo conserva al editar); un
     * combo genuinamente nuevo trae un `COMBO_ID` fresco. `replaceCombosForSale`
     * (borrar-todo-reinsertar) sigue siendo la ruta correcta para la venta
     * NUEVA, donde no hay `SERVER_UUID` previo que perder.
     */
    @Transaction
    suspend fun mergeCombosForSale(saleId: String, combos: List<LocalSaleComboEntity>) {
        val existing = getCombosForSale(saleId)
        val existingUuidByComboId = existing.associate { it.COMBO_ID to it.SERVER_UUID }
        val incomingComboIds = combos.map { it.COMBO_ID }.toSet()
        val removedComboIds = existing.map { it.COMBO_ID }.filterNot { it in incomingComboIds }

        if (removedComboIds.isNotEmpty()) {
            deleteCombosByIds(saleId, removedComboIds)
        }

        val merged = combos.map { combo ->
            combo.copy(SERVER_UUID = combo.SERVER_UUID ?: existingUuidByComboId[combo.COMBO_ID])
        }
        insertAllCombos(merged)
    }
}
