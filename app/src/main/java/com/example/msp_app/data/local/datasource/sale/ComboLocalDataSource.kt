package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.localsale.LocalSaleComboDao
import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import javax.inject.Inject

class ComboLocalDataSource @Inject constructor(
    private val comboDao: LocalSaleComboDao
) {
    /**
     * Puente legacy: ViewModels no-Hilt y `PendingLocalSalesWorker` (V1, aún
     * no `@HiltWorker`) siguen construyendo con `context` sin cambios.
     * Delega en la MISMA instancia que `@Inject` recibe vía
     * [com.example.msp_app.core.database.di.DatabaseModule] — ambos
     * resuelven a [AppDatabase.getInstance], una sola conexión a `msp_db`.
     * No abre un builder nuevo: eso duplicaría la ruta de escritura al
     * dinero.
     */
    constructor(context: Context) : this(AppDatabase.getInstance(context).localSaleComboDao())

    suspend fun insertCombo(combo: LocalSaleComboEntity) {
        comboDao.insertCombo(combo)
    }

    suspend fun insertCombos(combos: List<LocalSaleComboEntity>) {
        comboDao.insertAllCombos(combos)
    }

    /**
     * Lee los combos de una venta. Un resultado vacío significa que la venta
     * NO tiene combos (dato real y legítimo: una venta puede no llevar
     * combos). Un fallo del DAO se PROPAGA — jamás se colapsa a lista vacía.
     *
     * Money-path: tragar la excepción aquí falseaba el conjunto de renglones
     * que se sube a Microsip. El guard downstream (sync handler y worker)
     * decide qué es un vacío válido y qué es un error propagado.
     */
    suspend fun getCombosForSale(saleId: String): List<LocalSaleComboEntity> =
        comboDao.getCombosForSale(saleId)

    suspend fun deleteCombosForSale(saleId: String) {
        comboDao.deleteCombosForSale(saleId)
    }

    suspend fun replaceCombosForSale(saleId: String, combos: List<LocalSaleComboEntity>) {
        comboDao.replaceCombosForSale(saleId, combos)
    }

    suspend fun updateServerUuid(comboId: String, saleId: String, serverUuid: String) {
        comboDao.updateServerUuid(comboId, saleId, serverUuid)
    }
}
