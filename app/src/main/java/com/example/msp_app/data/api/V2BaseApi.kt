package com.example.msp_app.data.api

import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Base for v2 endpoints — the new Go backend authenticates every request
 * with the user's Firebase ID token as a Bearer header. The token is
 * cached for ~50 minutes (Firebase ID tokens live 1 hour) and refreshed on
 * demand when the cached value expires.
 *
 * v1 endpoints (Node backend) continue to use [BaseApi] without auth.
 */
open class V2BaseApi {

    protected fun createClient(baseUrl: String): Retrofit {
        val client = OkHttpClient.Builder()
            .addInterceptor(FirebaseBearerInterceptor())
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
    }
}

/**
 * Attaches "Authorization: Bearer <Firebase ID token>" to every request. The
 * token is fetched via [FirebaseAuth.currentUser]?.getIdToken; requests issued
 * with no signed-in user pass through without the header so the backend
 * responds 401 explicitly instead of stalling on missing creds.
 *
 * Visibility promoted to `internal` so that [CobranzaSseProvider] can reuse
 * the same interceptor pattern for the SSE OkHttpClient without duplicating
 * the auth logic.
 */
internal class FirebaseBearerInterceptor : Interceptor {

    private val tokenCache = TokenCache()

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenCache.getToken() }
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        val response = chain.proceed(request)
        if (response.code == 401 && token != null) {
            response.close()
            val fresh = runBlocking { tokenCache.refresh() }
            val retried = chain.request().newBuilder()
                .removeHeader("Authorization")
                .apply { if (fresh != null) addHeader("Authorization", "Bearer $fresh") }
                .build()
            return chain.proceed(retried)
        }
        return response
    }
}

/**
 * 50-minute cache for the Firebase ID token. Firebase rotates the token
 * every hour; refreshing slightly early avoids races where the token
 * expires mid-request.
 */
private class TokenCache {

    @Volatile private var cached: String? = null

    @Volatile private var fetchedAtMillis: Long = 0L

    suspend fun getToken(): String? {
        val now = System.currentTimeMillis()
        val current = cached
        if (current != null && now - fetchedAtMillis < TOKEN_TTL_MILLIS) {
            return current
        }
        return fetchToken(forceRefresh = false)?.also {
            cached = it
            fetchedAtMillis = now
        }
    }

    suspend fun refresh(): String? {
        val now = System.currentTimeMillis()
        return fetchToken(forceRefresh = true)?.also {
            cached = it
            fetchedAtMillis = now
        }
    }

    private suspend fun fetchToken(forceRefresh: Boolean): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return suspendCancellableCoroutine { cont ->
            user.getIdToken(forceRefresh)
                .addOnSuccessListener { result -> cont.resume(result.token) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }

    companion object {
        private const val TOKEN_TTL_MILLIS = 50L * 60L * 1_000L
    }
}
