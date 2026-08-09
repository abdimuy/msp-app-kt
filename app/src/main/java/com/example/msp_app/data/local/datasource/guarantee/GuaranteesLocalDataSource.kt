package com.example.msp_app.data.local.datasource.guarantee

import android.content.Context
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.guarantee.GuaranteeDao
import com.example.msp_app.core.database.entities.GuaranteeEntity
import com.example.msp_app.core.database.entities.GuaranteeEventEntity
import com.example.msp_app.core.database.entities.GuaranteeImageEntity
import java.util.UUID
import javax.inject.Inject

class GuaranteesLocalDataSource @Inject constructor(
    private val guaranteesDao: GuaranteeDao,
    private val clock: AppClock = AppClock.System
) {
    /**
     * Puente legacy: `PendingWorkSyncFactory`, `PendingGuaranteeEventsWorker`,
     * `PendingGuaranteesWorker` (workers aún no `@HiltWorker`) y los
     * ViewModels de garantías/ventas no-Hilt siguen construyendo con
     * `context` (y opcionalmente `clock`) sin cambios. Delega en la MISMA
     * instancia que `@Inject` recibe vía
     * [com.example.msp_app.core.database.di.DatabaseModule] — ambos resuelven
     * a [AppDatabase.getInstance], una sola conexión a `msp_db`. No abre un
     * builder nuevo. Preserva el `AppClock` inyectado desde Task 11
     * (`FECHA_EVENTO` en wire format RFC3339 UTC).
     */
    constructor(context: Context, clock: AppClock = AppClock.System) : this(
        AppDatabase.getInstance(context).guaranteeDao(),
        clock
    )

    suspend fun getGuaranteeById(id: Int): GuaranteeEntity? {
        return guaranteesDao.getGuaranteesById(id)
    }

    suspend fun getAllGuarantees(): List<GuaranteeEntity> {
        return guaranteesDao.getAllGuarantees()
    }

    suspend fun getStandaloneGuarantees(): List<GuaranteeEntity> {
        return guaranteesDao.getStandaloneGuarantees()
    }

    suspend fun saveAllGurantees(guarantees: List<GuaranteeEntity>) {
        guaranteesDao.deleteAllGuarantees()
        val guaranteesAsUploaded = guarantees.map { it.copy(UPLOADED = 1) }
        guaranteesDao.insertAllGuarantees(guaranteesAsUploaded)
    }

    suspend fun insertGuarantee(guarantee: GuaranteeEntity) {
        guaranteesDao.insertGuarantees(guarantee)
    }

    suspend fun updateUploadedStatus(id: Int, uploaded: Int) {
        guaranteesDao.updateUploadedStatus(id, uploaded)
    }

    suspend fun getGuaranteeByDoctoCcId(doctoCcId: Int): GuaranteeEntity? {
        return guaranteesDao.getGuaranteeByDoctoCcId(doctoCcId)
    }

    suspend fun insertGuaranteeImage(image: List<GuaranteeImageEntity>) {
        guaranteesDao.insertGuaranteesImagen(image)
    }

    suspend fun getImagesByGuaranteeId(guaranteeId: Int): List<GuaranteeImageEntity> {
        return guaranteesDao.getImagenesByGuaranteesId(guaranteeId)
    }

    suspend fun getImagesByExternalId(externalId: String): List<GuaranteeImageEntity> {
        return guaranteesDao.getImagesByExternalId(externalId)
    }

    suspend fun insertGuaranteeEvent(event: GuaranteeEventEntity) {
        guaranteesDao.insertEvento(event)
    }

    suspend fun saveAllGuaranteeEvents(events: List<GuaranteeEventEntity>) {
        guaranteesDao.deleteAllGuaranteesEvents()
        val eventsAsSent = events.map { it.copy(ENVIADO = 1) }
        guaranteesDao.insertAllEvents(eventsAsSent)
        guaranteesDao.getAllEventos()
    }

    suspend fun getAllGuaranteeEvents(): List<GuaranteeEventEntity> {
        return guaranteesDao.getAllEventos()
    }

    suspend fun getEventsByGuaranteeId(guaranteeId: String): List<GuaranteeEventEntity> {
        return guaranteesDao.getEventosByGuaranteesId(guaranteeId)
    }

    suspend fun updateEventSentStatus(id: String, sent: Int) {
        guaranteesDao.updateEventoEnviado(id, sent)
    }

    suspend fun deleteAllGuaranteesData() {
        guaranteesDao.deleteAllGuaranteesImages()
        guaranteesDao.deleteAllGuaranteesEvents()
        guaranteesDao.deleteAllGuarantees()
    }

    suspend fun updateGuaranteeStatusAndInsertEvent(
        guaranteeId: Int,
        externalId: String,
        newEstado: String,
        tipoEvento: String,
        comentario: String? = null
    ) {
        guaranteesDao.updateGuaranteeEstado(guaranteeId, newEstado)

        val newEvent = GuaranteeEventEntity(
            ID = UUID.randomUUID().toString(),
            GARANTIA_ID = externalId,
            TIPO_EVENTO = tipoEvento,
            FECHA_EVENTO = AppTime.toWireFormat(clock.now()),
            COMENTARIO = comentario,
            ENVIADO = 0
        )
        guaranteesDao.insertEvento(newEvent)
    }

    suspend fun getGuaranteeByExternalId(externalId: String): GuaranteeEntity? {
        return guaranteesDao.getGuaranteeByExternalId(externalId)
    }

    suspend fun markGuaranteeAsUploaded(externalId: String) {
        guaranteesDao.markGuaranteeAsUploaded(externalId)
    }

    suspend fun getPendingGuarantees(): List<GuaranteeEntity> {
        return guaranteesDao.getPendingGuarantees()
    }

    suspend fun getPendingGuaranteeEvents(): List<GuaranteeEventEntity> {
        return guaranteesDao.getPendingGuaranteeEvents()
    }
}
