package com.example.msp_app.data.repository

import android.content.Context
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.clientes.ClientesApi
import com.example.msp_app.data.local.datasource.ClienteDataSource
import com.example.msp_app.data.local.entities.ClienteEntity
import com.example.msp_app.core.utils.searchSimilarItems

class ClienteRepository(context: Context) {
    private val dataSource = ClienteDataSource(context)
    private val api = ApiProvider.create(ClientesApi::class.java)

    suspend fun syncFromServer() {
        val response = api.getClientes()
        val entities = response.body.map { cliente ->
            ClienteEntity(
                CLIENTE_ID = cliente.CLIENTE_ID,
                NOMBRE = cliente.NOMBRE,
                ESTATUS = cliente.ESTATUS,
                CAUSA_SUSP = cliente.CAUSA_SUSP
            )
        }
        dataSource.replaceAll(entities)
    }

    suspend fun searchClientes(query: String): List<ClienteEntity> {
        if (query.isBlank()) return emptyList()

        // 1. Exact LIKE match (contains the full query)
        val containsResults = dataSource.searchByNombre(query)

        // 2. Prefix match using first 3+ chars to broaden candidates
        //    e.g. "garza" -> prefix "gar" finds "garcia", "garza", "garibay", etc.
        val prefixLength = minOf(query.length, 3)
        val prefix = query.take(prefixLength)
        val prefixResults = if (prefix.length >= 2) {
            dataSource.searchByPrefix(prefix)
        } else {
            emptyList()
        }

        // 3. Combine and deduplicate
        val combined = (containsResults + prefixResults)
            .distinctBy { it.CLIENTE_ID }

        // 4. Fuzzy rank the broader candidate set
        return searchSimilarItems(
            query = query,
            items = combined,
            threshold = 50,
            selectText = { it.NOMBRE }
        ).take(20)
    }

    suspend fun getCount(): Int {
        return dataSource.getCount()
    }
}
