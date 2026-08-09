package com.example.msp_app.core.network

/**
 * Puerto que abastece el token bearer para las requests autenticadas del
 * backend v2 (Go). Justificación hexagonal (política del proyecto: puerto solo
 * si cruza módulo o habilita fakes): cruza el límite de módulo — la única
 * implementación real ([`FirebaseAuthTokenProvider`], con el caché de ~50 min
 * del ID token) vive en `:app` (T7), de modo que `:core:network` NO depende de
 * Firebase y se prueba con un fake.
 *
 * Semántica del contrato (auditada contra el backend Go
 * `internal/auth/infra/authhttp/authn.go`):
 * - `token(forceRefresh = false)` devuelve el token cacheado si sigue vigente.
 * - `token(forceRefresh = true)` fuerza una renovación (usado por
 *   [BearerAuthInterceptor] tras un 401).
 * - Devuelve `null` cuando no hay usuario autenticado; en ese caso la request
 *   sale sin header `Authorization` y el backend responde 401 explícito
 *   (`missing_authorization`) en vez de colgarse.
 */
interface AuthTokenProvider {

    /**
     * @param forceRefresh si `true`, ignora cualquier caché y obtiene un token
     *   fresco del proveedor de identidad.
     * @return el ID token vigente, o `null` si no hay sesión.
     */
    suspend fun token(forceRefresh: Boolean = false): String?
}
