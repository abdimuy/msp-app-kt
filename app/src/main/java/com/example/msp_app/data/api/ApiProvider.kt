package com.example.msp_app.data.api

import com.example.msp_app.BuildConfig
import com.example.msp_app.core.utils.Constants.COLLECTION_CONFIG
import com.example.msp_app.core.utils.Constants.DOCUMENT_API_SETTINGS
import com.example.msp_app.core.utils.Constants.FIELD_BASE_URL
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit

/**
 * Proveedor v1 (Node legacy). El cliente Retrofit lo construye ahora el
 * [com.example.msp_app.core.network.RetrofitClientFactory] de `:core:network`
 * (T7) en vez del `BaseApi.createClient` eliminado — SIN bearer y con el mismo
 * timeout de 300 s del legacy (sync masivos lentos).
 *
 * **Kill-switch (se PRESERVA):** este `object` mantiene su `_baseURL` StateFlow,
 * su listener de Firestore y su `retrofitInstance` mutable. En release, un cambio
 * en `api_settings.baseURL` reconstruye `retrofitInstance` pasándole al factory la
 * URL vigente — el factory nunca congela la baseURL. En debug gana la URL del
 * flavor (el `if (!BuildConfig.DEBUG)` se conserva).
 */
object ApiProvider {

    // Base legacy por flavor (devlocal: local; devserver: apidb; prod: msp2025).
    // En release, Firestore puede sobreescribirla en runtime vía
    // `api_settings.baseURL` (kill-switch remoto).
    private val DEFAULT_BASE_URL = BuildConfig.LEGACY_BASE_URL
    private val _baseURL = MutableStateFlow(DEFAULT_BASE_URL)
    val baseURL: StateFlow<String> = _baseURL
    private var retrofitInstance: Retrofit? = null
    private var firestoreListener: ListenerRegistration? = null

    init {
        // The Firestore-driven base-URL override is a remote kill-switch for the
        // DEPLOYED (release) apps. In debug builds the static flavor URL wins
        // (devlocal → local backend, devserver → apidb) so dev work is not
        // silently repointed by a Firestore doc.
        if (!BuildConfig.DEBUG) {
            firestoreListener = FirebaseFirestore.getInstance()
                .collection(COLLECTION_CONFIG)
                .document(DOCUMENT_API_SETTINGS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        println("Error al escuchar base URL: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val newUrl = snapshot.getString(FIELD_BASE_URL) ?: DEFAULT_BASE_URL
                        if (newUrl.isNotEmpty() && newUrl != _baseURL.value) {
                            _baseURL.value = newUrl
                            CoroutineScope(Dispatchers.IO).launch {
                                retrofitInstance = buildClient(newUrl)
                            }
                        }
                    }
                }
        }
    }

    /**
     * Construye el cliente v1 para [url] vía el factory de `:core:network`.
     * `auth = false` (el v1 Node no usa bearer) y `timeoutSeconds = 300` para
     * conservar exactamente el perfil del `BaseApi` legacy (sin regresión en los
     * sync masivos lentos). La [url] SIEMPRE es la baseURL vigente del
     * kill-switch — nunca `NetworkConfig.legacyBaseUrl` directo — para que el
     * override de Firestore alcance a la request.
     */
    private fun buildClient(url: String): Retrofit = appRetrofitClientFactory.create(
        baseUrl = url,
        auth = false,
        timeoutSeconds = LEGACY_TIMEOUT_SECONDS
    )

    private fun getRetrofit(): Retrofit {
        return retrofitInstance ?: synchronized(this) {
            retrofitInstance ?: buildClient(_baseURL.value).also { retrofitInstance = it }
        }
    }

    fun <T> create(service: Class<T>): T {
        return getRetrofit().create(service)
    }

    /** Perfil de timeout del v1 Node legacy (sync masivos): 300 s. */
    private const val LEGACY_TIMEOUT_SECONDS = 300L
}
