package com.example.msp_app.data.local.datasource

import android.content.Context
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.ClienteDao
import com.example.msp_app.core.database.entities.ClienteEntity
import javax.inject.Inject

class ClienteDataSource @Inject constructor(
    private val clienteDao: ClienteDao
) {
    /**
     * Puente legacy: `ClienteRepository` sigue construyendo con `context` sin
     * cambios. Delega en la MISMA instancia que `@Inject` recibe vía
     * [com.example.msp_app.core.database.di.DatabaseModule] — ambos resuelven
     * a [AppDatabase.getInstance], una sola conexión a `msp_db`. No abre un
     * builder nuevo.
     */
    constructor(context: Context) : this(AppDatabase.getInstance(context).clienteDao())

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
