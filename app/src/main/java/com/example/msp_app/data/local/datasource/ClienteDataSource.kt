package com.example.msp_app.data.local.datasource

import android.content.Context
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.ClienteEntity

class ClienteDataSource(context: Context) {
    private val clienteDao = AppDatabase.getInstance(context).clienteDao()

    suspend fun replaceAll(clientes: List<ClienteEntity>) {
        clienteDao.replaceAll(clientes)
    }

    suspend fun searchByNombre(query: String): List<ClienteEntity> {
        return clienteDao.searchByNombre(query)
    }

    suspend fun searchByPrefix(prefix: String): List<ClienteEntity> {
        return clienteDao.searchByPrefix(prefix)
    }

    suspend fun getCount(): Int {
        return clienteDao.getCount()
    }
}
