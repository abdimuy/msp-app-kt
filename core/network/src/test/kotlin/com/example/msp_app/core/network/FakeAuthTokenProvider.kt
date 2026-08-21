package com.example.msp_app.core.network

/**
 * Fake del puerto [AuthTokenProvider] (política fakes-only: sin MockK). Devuelve
 * [initialToken] para `forceRefresh = false` y [refreshedToken] para
 * `forceRefresh = true`, registrando cuántas veces se pidió cada uno para poder
 * afirmar el contrato exacto (passthrough sin token, refresh-y-reintento).
 *
 * [fallaNormal] y [fallaRefresh] modelan que el proveedor de identidad **no se
 * pueda alcanzar**, que es un caso distinto de "no hay sesión" (`null`). En
 * producción eso es una `FirebaseNetworkException` que `getIdToken` entrega por
 * `addOnFailureListener` cuando el teléfono lleva rato sin señal y venció el
 * caché de 50 minutos del token.
 */
class FakeAuthTokenProvider(
    private val initialToken: String?,
    private val refreshedToken: String? = initialToken,
    private val fallaNormal: Throwable? = null,
    private val fallaRefresh: Throwable? = null
) : AuthTokenProvider {

    var normalCalls: Int = 0
        private set

    var refreshCalls: Int = 0
        private set

    override suspend fun token(forceRefresh: Boolean): String? = if (forceRefresh) {
        refreshCalls++
        fallaRefresh?.let { throw it }
        refreshedToken
    } else {
        normalCalls++
        fallaNormal?.let { throw it }
        initialToken
    }
}

/**
 * Sustituto de `com.google.firebase.FirebaseNetworkException` para las pruebas
 * de `:core:network`, que **no** depende de Firebase.
 *
 * Lo que importa de ella no es el nombre: es que **no es una `IOException`**.
 * Ésa es exactamente la propiedad que mataba el proceso —
 * `Interceptor.intercept` sólo puede lanzar `IOException`, y OkHttp relanza
 * cualquier otro `Throwable` en su hilo de despacho.
 */
class ProveedorDeIdentidadCaido(mensaje: String = "unreachable host") : Exception(mensaje)
