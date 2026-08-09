package com.example.msp_app.data.api

import com.example.msp_app.core.network.AuthTokenProvider
import com.google.firebase.auth.FirebaseAuth
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Impl Firebase del puerto [AuthTokenProvider] de `:core:network`. Reescritura
 * limpia del `TokenCache` + `FirebaseBearerInterceptor` legacy que vivían en
 * `V2BaseApi` (eliminado en T7): la lógica de red (adjuntar el bearer, reintentar
 * una vez tras 401) se movió a `BearerAuthInterceptor` de `:core:network`; esta
 * clase solo es la **fuente del token** con su caché.
 *
 * Cachea el Firebase ID token ~50 min (los tokens viven 1 h; se renueva antes
 * para evitar carreras de expiración a mitad de vuelo). `BearerAuthInterceptor`
 * fuerza `token(forceRefresh = true)` tras un 401 para renovar y reintentar.
 *
 * Contrato (auditado vs backend Go `internal/auth/infra/authhttp/authn.go`):
 *  - `token(false)`: devuelve el token cacheado si sigue vigente; si no, uno
 *    fresco de Firebase.
 *  - `token(true)`: ignora la caché y fuerza una renovación.
 *  - `null` si no hay usuario autenticado — la request sale sin header
 *    `Authorization` y el backend responde 401 explícito (`missing_authorization`)
 *    en vez de colgarse.
 *
 * [fetch] y [clock] son inyectables **solo para test** (fakes-only, sin MockK);
 * en producción usan Firebase y el reloj del sistema.
 */
class FirebaseAuthTokenProvider(
    private val fetch: suspend (forceRefresh: Boolean) -> String? = ::fetchTokenFromFirebase,
    private val clock: () -> Long = System::currentTimeMillis
) : AuthTokenProvider {

    @Volatile
    private var cached: String? = null

    @Volatile
    private var fetchedAtMillis: Long = 0L

    override suspend fun token(forceRefresh: Boolean): String? =
        if (forceRefresh) refresh() else cachedOrFetch()

    private suspend fun cachedOrFetch(): String? {
        val now = clock()
        val current = cached
        if (current != null && now - fetchedAtMillis < TOKEN_TTL_MILLIS) {
            return current
        }
        return fetch(false)?.also {
            cached = it
            fetchedAtMillis = now
        }
    }

    private suspend fun refresh(): String? {
        val now = clock()
        return fetch(true)?.also {
            cached = it
            fetchedAtMillis = now
        }
    }

    private companion object {
        const val TOKEN_TTL_MILLIS = 50L * 60L * 1_000L
    }
}

/**
 * Obtiene el ID token del usuario Firebase actual. Devuelve `null` si no hay
 * sesión. Fuente por defecto de [FirebaseAuthTokenProvider.fetch].
 */
private suspend fun fetchTokenFromFirebase(forceRefresh: Boolean): String? {
    val user = FirebaseAuth.getInstance().currentUser ?: return null
    return suspendCancellableCoroutine { cont ->
        user.getIdToken(forceRefresh)
            .addOnSuccessListener { result -> cont.resume(result.token) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }
}
