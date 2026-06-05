package com.example.msp_app.data.api.services.usuarios

import retrofit2.http.Body
import retrofit2.http.POST

data class EnsureVendedoresRequest(
    val emails: List<String>
)

data class EnsureVendedoresResponseItem(
    val email: String,
    val usuario_id: String
)

data class EnsureVendedoresResponse(
    val vendedores: List<EnsureVendedoresResponseItem>
)

/**
 * Retrofit interface for the v2 Go backend's usuarios endpoints. Today only
 * the lazy upsert endpoint is exposed here; other usuarios reads stay on the
 * v1 Node backend.
 *
 * El endpoint resuelve email → usuario_id para los vendedores asignados por
 * camioneta en Firestore. Es llamado por el cliente justo antes del POST de
 * venta para construir el array `vendedores[].usuario_id`. Vendedores que
 * existen sólo en Firestore (no han creado cuenta Firebase) se crean
 * lazily en MSP_USUARIOS con ESTATUS = 'VENDEDOR_ONLY'.
 *
 * Idempotente por construcción — no requiere Idempotency-Key.
 */
interface UsuariosApi {
    @POST("v2/usuarios/ensure-vendedores-by-email")
    suspend fun ensureVendedoresByEmail(
        @Body request: EnsureVendedoresRequest
    ): EnsureVendedoresResponse
}
