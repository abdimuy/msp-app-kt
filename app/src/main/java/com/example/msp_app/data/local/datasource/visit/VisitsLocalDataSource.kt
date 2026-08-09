package com.example.msp_app.data.local.datasource.visit

import android.content.Context
import androidx.room.Transaction
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.database.entities.VisitEntity
import javax.inject.Inject

class VisitsLocalDataSource @Inject constructor(
    private val visitDao: VisitDao,
    private val saleDao: SaleDao
) {
    /**
     * Puente legacy: los callers `viewModel()` y los workers aún no-Hilt
     * siguen construyendo con `context` sin cambios. Delega en la MISMA
     * instancia que `@Inject` recibe vía [com.example.msp_app.core.database.di.DatabaseModule]
     * — ambos resuelven a [AppDatabase.getInstance], una sola conexión a
     * `msp_db`. No abre un builder nuevo.
     */
    constructor(context: Context) : this(
        AppDatabase.getInstance(context).visitDao(),
        AppDatabase.getInstance(context).saleDao()
    )

    suspend fun getVisitById(id: String): VisitEntity {
        return visitDao.getVisitById(id)
    }

    suspend fun saveVisit(visit: VisitEntity) {
        visitDao.insertVisit(visit)
    }

    suspend fun getPendingVisits(): List<VisitEntity> {
        return visitDao.getPendingVisits()
    }

    suspend fun getVisitsByDate(start: String, end: String): List<VisitEntity> {
        return visitDao.getVisitsByDate(start, end)
    }

    suspend fun updateVisitState(id: String, newState: Int) {
        visitDao.updateState(id, newState)
    }

    suspend fun updateVisitLocation(id: String, lat: Double, lng: Double) {
        visitDao.updateLocation(id, lat, lng)
    }

    suspend fun changeVisitStatus(id: String, status: Boolean) {
        visitDao.updateState(
            id,
            if (status) 1 else 0
        )
    }

    @Transaction
    suspend fun insertVisitAndUpdateState(
        saleId: Int,
        visit: VisitEntity,
        newState: EstadoCobranza
    ) {
        visitDao.insertVisit(visit)
        saleDao.updateTotal(
            saleId,
            0.0,
            newState
        )
    }

    suspend fun updateTemporaryCollectionDate(saleId: Int, newDate: String) {
        saleDao.updateTemporaryCollectionDate(saleId, newDate)
    }

    suspend fun deleteAllVisits() {
        visitDao.deleteAllVisits()
    }

    /** Prunes only visitas already confirmed by the server; see [VisitDao.deleteUploadedVisits]. */
    suspend fun deleteUploadedVisits() {
        visitDao.deleteUploadedVisits()
    }
}
