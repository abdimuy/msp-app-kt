package com.example.msp_app.core.database.dao.localsale

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.msp_app.core.database.entities.LocalSaleProductEntity

@Dao
interface LocalSaleProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleProduct(saleProduct: LocalSaleProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSaleProducts(saleProducts: List<LocalSaleProductEntity>)

    @Query(
        "SELECT LOCAL_SALE_ID, ARTICULO_ID, ARTICULO, CANTIDAD, PRECIO_LISTA, PRECIO_CORTO_PLAZO, PRECIO_CONTADO, COMBO_ID, SERVER_UUID FROM local_sale_products WHERE LOCAL_SALE_ID = :saleId"
    )
    suspend fun getProductsForSale(saleId: String): List<LocalSaleProductEntity>

    @Query("DELETE FROM local_sale_products WHERE LOCAL_SALE_ID = :saleId")
    suspend fun deleteProductsForSale(saleId: String)

    @Query(
        "UPDATE local_sale_products SET SERVER_UUID = :serverUuid WHERE LOCAL_SALE_ID = :saleId AND ARTICULO_ID = :articuloId"
    )
    suspend fun updateServerUuid(saleId: String, articuloId: Int, serverUuid: String)

    @Query(
        "DELETE FROM local_sale_products WHERE LOCAL_SALE_ID = :saleId AND ARTICULO_ID IN (:articuloIds)"
    )
    suspend fun deleteProductsByArticuloIds(saleId: String, articuloIds: List<Int>)

    /**
     * Merge que CONSERVA `SERVER_UUID` (plan "Corregir una venta antes de que
     * suba", sección "El merge de líneas"). A diferencia de
     * `deleteProductsForSale` + `insertAllSaleProducts` (la ruta de venta
     * NUEVA, donde nunca hubo un `SERVER_UUID` que perder), esta es la ruta
     * de EDITAR una venta que el subidor ya intentó mandar:
     *
     * - Línea que sobrevive (misma `(LOCAL_SALE_ID, ARTICULO_ID)`): se
     *   actualiza con la cantidad/precios nuevos pero conserva el
     *   `SERVER_UUID` que el subidor ya había acuñado — LA BASE MANDA, no lo
     *   que traiga `products`. El editor no conoce el `SERVER_UUID` (nunca
     *   lo carga), así que un valor entrante para una línea que sobrevive es
     *   basura vieja o `NULL`; confiar en él pierde justo el invariante
     *   central de este merge.
     * - Línea nueva: entra con `SERVER_UUID = NULL`; el subidor se lo acuña
     *   en un intento posterior.
     * - Línea quitada: se borra.
     *
     * `require`: [products] no puede traer `ARTICULO_ID` repetido. Hoy
     * ningún llamador lo hace, pero `insertAllSaleProducts` usa `REPLACE`
     * sobre la PK `(LOCAL_SALE_ID, ARTICULO_ID)` — un duplicado silencioso
     * colapsaría a una sola fila sin ningún aviso.
     *
     * Invariante: corregir una venta no reacuña la identidad de las líneas
     * que no cambiaron. Volver esto a "borrar todo y reinsertar" pierde esa
     * identidad — ver `LineMergeDaoTest`.
     */
    @Transaction
    suspend fun mergeProductsForSale(saleId: String, products: List<LocalSaleProductEntity>) {
        require(products.size == products.distinctBy { it.ARTICULO_ID }.size) {
            "products trae ARTICULO_ID repetido para la venta $saleId"
        }

        val existing = getProductsForSale(saleId)
        val existingUuidByArticulo = existing.associate { it.ARTICULO_ID to it.SERVER_UUID }
        val incomingArticuloIds = products.map { it.ARTICULO_ID }.toSet()
        val removedArticuloIds = existing.map { it.ARTICULO_ID }.filterNot { it in incomingArticuloIds }

        if (removedArticuloIds.isNotEmpty()) {
            deleteProductsByArticuloIds(saleId, removedArticuloIds)
        }

        val merged = products.map { product ->
            val survives = existingUuidByArticulo.containsKey(product.ARTICULO_ID)
            product.copy(
                SERVER_UUID = if (survives) existingUuidByArticulo[product.ARTICULO_ID] else product.SERVER_UUID
            )
        }
        insertAllSaleProducts(merged)
    }
}
