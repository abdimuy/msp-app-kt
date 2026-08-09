package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.database.entities.SaleWithProductsEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class SalesLocalDataSource @Inject constructor(
    private val saleDao: SaleDao
) {
    /**
     * Puente legacy: los callers `viewModel()` siguen construyendo con
     * `context` sin cambios. Delega en la MISMA instancia que `@Inject`
     * recibe vía [com.example.msp_app.core.database.di.DatabaseModule] —
     * ambos resuelven a [AppDatabase.getInstance], una sola conexión a
     * `msp_db`. No abre un builder nuevo: eso duplicaría la ruta de
     * escritura al dinero.
     */
    constructor(context: Context) : this(AppDatabase.getInstance(context).saleDao())

    suspend fun getAll(): List<SaleWithProductsEntity> = saleDao.getAll()

    fun observeAll(): Flow<List<SaleWithProductsEntity>> = saleDao.observeAll()

    suspend fun getByClientId(clientId: Int): List<SaleWithProductsEntity> {
        return saleDao.getByClientId(clientId)
    }

    suspend fun saveAll(sales: List<SaleEntity>) {
        saleDao.deleteAll()
        saleDao.insertAll(sales)
    }

    suspend fun getById(id: Int): SaleEntity? {
        return saleDao.getById(id)
    }
}
