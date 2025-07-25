package com.example.msp_app.data.local.datasource.guarantee

import android.content.Context
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.GuaranteeEntity
import com.example.msp_app.data.local.entities.GuaranteeEventEntity
import com.example.msp_app.data.local.entities.GuaranteeImageEntity

class GuaranteesLocalDataSource(private val context: Context) {
    private val guaranteesDao = AppDatabase.getInstance(context).guaranteeDao()

    suspend fun getGuaranteeById(id: Int): GuaranteeEntity? {
        return guaranteesDao.getGuaranteesById(id)
    }

    suspend fun getAllGuarantees(): List<GuaranteeEntity> {
        return guaranteesDao.getAllGuarantees()
    }

    suspend fun insertGuarantee(guarantee: GuaranteeEntity) {
        guaranteesDao.insertGuarantees(guarantee)
    }

    suspend fun updateUploadedStatus(id: Int, uploaded: Int) {
        guaranteesDao.updateUploadedStatus(id, uploaded)
    }

    suspend fun insertGuaranteeImage(image: GuaranteeImageEntity) {
        guaranteesDao.insertGuaranteesImagen(image)
    }

    suspend fun getImagesByGuaranteeId(guaranteeId: Int): List<GuaranteeImageEntity> {
        return guaranteesDao.getImagenesByGuaranteesId(guaranteeId)
    }

    suspend fun insertGuaranteeEvent(event: GuaranteeEventEntity) {
        guaranteesDao.insertEvento(event)
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
}