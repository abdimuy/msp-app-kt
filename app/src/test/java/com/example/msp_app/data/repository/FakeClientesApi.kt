package com.example.msp_app.data.repository

import com.example.msp_app.data.api.services.clientes.Cliente
import com.example.msp_app.data.api.services.clientes.ClienteResponse
import com.example.msp_app.data.api.services.clientes.ClientesApi
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

/** Servidor falso de clientes: tarda [duracionMs] (virtual con `runTest`), anota llamadas y cancelaciones. */
class FakeClientesApi(
    var duracionMs: Long = 0,
    var respuesta: List<Cliente> = emptyList(),
    var falla: Throwable? = null
) : ClientesApi {
    var llamadas = 0
    var cancelaciones = 0
    var terminadas = 0

    override suspend fun getClientes(): ClienteResponse {
        llamadas++
        try {
            delay(duracionMs)
        } catch (e: CancellationException) {
            cancelaciones++
            throw e
        }
        falla?.let { throw it }
        terminadas++
        return ClienteResponse(respuesta)
    }

    companion object {
        fun cliente(id: Int, nombre: String, estatus: String = "A") =
            Cliente(id, nombre, estatus, null)
    }
}
