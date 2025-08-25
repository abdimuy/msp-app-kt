package com.example.msp_app.data.api

import com.example.msp_app.core.utils.Constants.COLLECTION_CONFIG
import com.example.msp_app.core.utils.Constants.DOCUMENT_API_SETTINGS
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit

object ApiProvider : BaseApi() {

    private const val DEFAULT_BASE_URL = "https://prueba2025.loclx.io/"
    private val _baseURL = MutableStateFlow(DEFAULT_BASE_URL)
    val baseURL: StateFlow<String> = _baseURL
    private var retrofitInstance: Retrofit? = null
    private var firestoreListener: ListenerRegistration? = null

    init {
        firestoreListener = FirebaseFirestore.getInstance()
            .collection(COLLECTION_CONFIG)
            .document(DOCUMENT_API_SETTINGS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("Error al escuchar base URL: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val newUrl = DEFAULT_BASE_URL
                    if (newUrl.isNotEmpty() && newUrl != _baseURL.value) {
                        _baseURL.value = newUrl
                        CoroutineScope(Dispatchers.IO).launch {
                            retrofitInstance = createClient(newUrl)
                        }
                    }
                }
            }
    }

    private fun getRetrofit(): Retrofit {
        return retrofitInstance ?: synchronized(this) {
            retrofitInstance ?: createClient(_baseURL.value).also { retrofitInstance = it }
        }
    }

    fun <T> create(service: Class<T>): T {
        return getRetrofit().create(service)
    }
}
