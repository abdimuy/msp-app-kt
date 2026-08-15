package com.example.msp_app.core.updates

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.example.msp_app.core.utils.Constants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class UpdateInfo(
    val latestVersion: String,
    val apkUrl: String
)

object UpdateChecker {

    /** Componentes numéricos comparados de una versión (mayor.menor.parche). */
    private const val VERSION_PARTS = 3

    private const val FIELD_LATEST_VERSION = "LATEST_VERSION"
    private const val FIELD_APK_URL = "APK_URL"

    private val _updateAvailable = MutableStateFlow<UpdateInfo?>(null)
    val updateAvailable: StateFlow<UpdateInfo?> = _updateAvailable

    init {
        FirebaseFirestore.getInstance()
            .collection(Constants.COLLECTION_CONFIG)
            .document(Constants.DOCUMENT_API_SETTINGS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("UpdateChecker", "Error checking for updates: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val latestVersion = snapshot.getString(FIELD_LATEST_VERSION)
                    val apkUrl = snapshot.getString(FIELD_APK_URL)

                    // `!=` ofrecía "actualizar" a una versión ANTERIOR: tras
                    // una reversión en Firestore, un teléfono ya al día veía la
                    // versión vieja como novedad. Sólo es actualización si es
                    // estrictamente mayor.
                    if (latestVersion != null &&
                        apkUrl != null &&
                        isNewerVersion(latestVersion, Constants.APP_VERSION)
                    ) {
                        _updateAvailable.value = UpdateInfo(latestVersion, apkUrl)
                    } else {
                        _updateAvailable.value = null
                    }
                }
            }
    }

    /**
     * ¿[candidate] es estrictamente posterior a [installed]?
     *
     * Compara componente por componente como NÚMEROS, así que 2.9.0 queda por
     * debajo de 2.10.0 (una comparación de cadenas diría lo contrario). Los
     * sufijos de sabor/compilación ("2.16.0-dev") se ignoran, y los componentes
     * faltantes valen cero. Si alguna de las dos no se puede leer, devuelve
     * false: ante la duda, no ofrecer nada.
     */
    @VisibleForTesting
    internal fun isNewerVersion(candidate: String, installed: String): Boolean {
        val c = parseVersion(candidate) ?: return false
        val i = parseVersion(installed) ?: return false
        for (k in 0 until VERSION_PARTS) {
            if (c[k] != i[k]) return c[k] > i[k]
        }
        return false
    }

    private fun parseVersion(v: String): IntArray? {
        val base = v.trim().substringBefore('-').substringBefore('+')
        if (base.isEmpty()) return null
        val out = IntArray(VERSION_PARTS)
        base.split('.').take(VERSION_PARTS).forEachIndexed { idx, seg ->
            val n = seg.toIntOrNull() ?: return null
            if (n < 0) return null
            out[idx] = n
        }
        return out
    }
}
