package com.example.msp_app.data.repository

import android.content.Context
import com.example.msp_app.core.database.entities.ClienteEntity
import com.example.msp_app.core.utils.normalizeForSearch
import com.example.msp_app.core.utils.searchSimilarItems
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.clientes.ClientesApi
import com.example.msp_app.data.local.datasource.ClienteDataSource

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
                CAUSA_SUSP = cliente.CAUSA_SUSP,
                NOMBRE_NORMALIZADO = normalizeForSearch(cliente.NOMBRE)
            )
        }
        dataSource.replaceAll(entities)
    }

    /**
     * Tolerante a acentos, mayúsculas, espacios de más y orden de las palabras —
     * el padrón viene de Microsip, donde "JOSE" y "JOSÉ" conviven, y el cobrador teclea
     * rápido sin fijarse en el espacio extra o en qué nombre puso primero.
     *
     * ## Por qué se ancla en la palabra más larga y no en toda la consulta
     *
     * `NOMBRE_NORMALIZADO LIKE '%<consulta completa>%'` (lo que hacía la versión
     * anterior) exige que las palabras aparezcan EN ESE ORDEN y sin nada entre ellas —
     * así "guadalupe maria" nunca encuentra "MARIA GUADALUPE RIVERA". En vez de eso: se
     * trae del padrón (~43,700 clientes activos medidos en la base de referencia, ver
     * REPORTE-BUSCADOR.md) solo lo que contiene la palabra MÁS LARGA de la consulta —
     * la más selectiva, la que menos falsos candidatos trae — y luego se exige en
     * memoria que las demás palabras también estén, sin importar en qué orden.
     */
    suspend fun searchClientes(query: String): List<ClienteEntity> {
        val normalizedQuery = normalizeForSearch(query)
        if (normalizedQuery.isEmpty()) return emptyList()

        val words = normalizedQuery.split(" ").filter { it.isNotEmpty() }
        val anchorWord = words.maxByOrNull { it.length } ?: return emptyList()

        val candidates = dataSource.searchByNormalizedWord(anchorWord)
        val matching = candidates.filter { cliente ->
            words.all { word -> cliente.NOMBRE_NORMALIZADO.contains(word) }
        }

        // El filtrado de arriba ya garantiza que cada resultado contiene TODAS las
        // palabras tecleadas; threshold = 0 porque aquí solo se ordena por relevancia,
        // no se vuelve a descartar nada. Con el umbral por defecto, un nombre de tres o
        // más palabras reordenado podía anotar por debajo del corte y desaparecer de
        // los resultados pese a ser un match correcto.
        return searchSimilarItems(
            query = query,
            items = matching,
            threshold = 0,
            selectText = { it.NOMBRE }
        ).take(20)
    }

    suspend fun getCount(): Int {
        return dataSource.getCount()
    }
}
