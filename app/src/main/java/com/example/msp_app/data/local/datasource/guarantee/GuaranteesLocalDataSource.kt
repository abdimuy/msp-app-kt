package com.example.msp_app.data.local.datasource.guarantee

import android.content.Context
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.GuaranteeEntity
import com.example.msp_app.data.local.entities.GuaranteeEventEntity
import com.example.msp_app.data.local.entities.GuaranteeImageEntity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class GuaranteesLocalDataSource(private val context: Context) {
    private val guaranteesDao = AppDatabase.getInstance(context).guaranteeDao()

    suspend fun getGuaranteeById(id: Int): GuaranteeEntity? {
        return guaranteesDao.getGuaranteesById(id)
    }

    suspend fun getAllGuarantees(): List<GuaranteeEntity> {
        return guaranteesDao.getAllGuarantees()
    }

    suspend fun saveAllGurantees(guarantees: List<GuaranteeEntity>) {
        guaranteesDao.deleteAllGuarantees()
        guaranteesDao.insertAllGuarantees(guarantees)
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

    suspend fun insertGuaranteeEvent(event: GuaranteeEventEntity) {
        guaranteesDao.insertEvento(event)
    }

    suspend fun saveAllGuaranteeEvents(events: List<GuaranteeEventEntity>) {
        guaranteesDao.deleteAllGuaranteesEvents()
        guaranteesDao.insertAllEvents(events)
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
            FECHA_EVENTO = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            COMENTARIO = comentario,
            ENVIADO = 0
        )
        guaranteesDao.insertEvento(newEvent)
    }

}