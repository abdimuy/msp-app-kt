package com.example.msp_app.feature.garantias.domain.port

import com.example.msp_app.feature.garantias.domain.model.ClienteDeGarantia

/**
 * El padrón de clientes que el teléfono ya sincronizó.
 *
 * Es un puerto y no una consulta directa a `ClienteDao` porque `application`
 * no conoce Room y porque la búsqueda combina dos tablas (cliente y venta) para
 * decidir el estado de cuenta. El adaptador vive en `data/clientes`.
 */
interface ClientesDeGarantiaPort {

    /**
     * Los clientes cuyo nombre contiene [nombre], como máximo [limite].
     *
     * Query en blanco devuelve lista vacía: no se ofrece el padrón entero.
     */
    suspend fun buscar(nombre: String, limite: Int = LIMITE_POR_DEFECTO): List<ClienteDeGarantia>

    companion object {
        /** Suficiente para elegir entre homónimos sin llenar la pantalla. */
        const val LIMITE_POR_DEFECTO: Int = 20
    }
}
