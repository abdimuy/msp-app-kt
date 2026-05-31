package com.example.msp_app.data.api

import retrofit2.Retrofit

/**
 * Builds Retrofit interfaces for the v2 Go backend. Tracks the same base URL
 * as [ApiProvider] (driven by Firestore config) and rebuilds the underlying
 * Retrofit instance when that URL changes.
 *
 * The auth-bearer interceptor lives in [V2BaseApi]; every service created
 * here inherits it for free.
 */
object V2ApiProvider : V2BaseApi() {

    @Volatile private var retrofit: Retrofit? = null

    @Volatile private var cachedUrl: String? = null

    fun <T> create(service: Class<T>): T {
        return retrofitFor(ApiProvider.baseURL.value).create(service)
    }

    private fun retrofitFor(url: String): Retrofit {
        val existing = retrofit
        if (existing != null && cachedUrl == url) {
            return existing
        }
        return synchronized(this) {
            val again = retrofit
            if (again != null && cachedUrl == url) {
                again
            } else {
                val built = createClient(url)
                retrofit = built
                cachedUrl = url
                built
            }
        }
    }
}
