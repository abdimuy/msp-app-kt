package com.example.msp_app.core.network

/**
 * Fake del puerto [AuthTokenProvider] (política fakes-only: sin MockK). Devuelve
 * [initialToken] para `forceRefresh = false` y [refreshedToken] para
 * `forceRefresh = true`, registrando cuántas veces se pidió cada uno para poder
 * afirmar el contrato exacto (passthrough sin token, refresh-y-reintento).
 */
class FakeAuthTokenProvider(
    private val initialToken: String?,
    private val refreshedToken: String? = initialToken
) : AuthTokenProvider {

    var normalCalls: Int = 0
        private set

    var refreshCalls: Int = 0
        private set

    override suspend fun token(forceRefresh: Boolean): String? = if (forceRefresh) {
        refreshCalls++
        refreshedToken
    } else {
        normalCalls++
        initialToken
    }
}
