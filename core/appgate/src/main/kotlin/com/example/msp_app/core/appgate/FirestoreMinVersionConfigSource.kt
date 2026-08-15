package com.example.msp_app.core.appgate

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged

private const val TAG = "MinVersionConfig"

/** Colección/documento: el MISMO que ya escucha `ApiProvider` para `baseURL`. */
private const val COLLECTION_CONFIG = "config"
private const val DOCUMENT_API_SETTINGS = "api_settings"

/**
 * [MinVersionConfigSource] contra Firestore `config/api_settings`.
 *
 * `firestore` llega como lambda y no como instancia: `FirebaseFirestore.getInstance()`
 * exige que Firebase esté inicializado, y este objeto se construye en el grafo
 * de Hilt (que se arma en el arranque, antes de que nadie observe nada).
 * Evaluarla perezosamente deja el grafo construible en cualquier contexto
 * —incluidos los tests— y falla, si acaso, cuando alguien de verdad observa.
 *
 * Un error del listener **no** se propaga: se registra y el `Flow` sigue vivo
 * esperando la siguiente emisión. Propagarlo cancelaría la observación y
 * dejaría la compuerta congelada en la última lectura; degradar a la caché es
 * el comportamiento correcto y es lo que hace [VersionGateCache].
 */
class FirestoreMinVersionConfigSource(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) : MinVersionConfigSource {

    override fun observe(): Flow<MinVersionConfig> = callbackFlow {
        val registration = firestore()
            .collection(COLLECTION_CONFIG)
            .document(DOCUMENT_API_SETTINGS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "no se pudo leer la versión mínima remota", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(snapshot.toMinVersionConfig())
                }
            }

        awaitClose { registration.remove() }
    }
        .distinctUntilChanged()
        // Red de seguridad del ARRANQUE del flow, distinta del error de
        // listener de arriba: si `FirebaseFirestore.getInstance()` lanza
        // (Firebase sin inicializar, `google-services` ausente en un flavor),
        // la excepción escaparía hasta el `viewModelScope` y tiraría la app.
        // La compuerta no puede ser la que tumbe la app que protege: se
        // registra y se sigue con lo que haya en caché.
        .catch { error -> Log.w(TAG, "no se pudo abrir la escucha de versión mínima", error) }
}

/**
 * Lectura defensiva: cada campo ausente o de tipo inesperado cae a su default
 * "apagado" en vez de lanzar. Un documento a medio editar por la oficina no
 * puede tumbar la app ni, peor, bloquearla por accidente.
 */
internal fun DocumentSnapshot.toMinVersionConfig(): MinVersionConfig = MinVersionConfig(
    minVersionCode = readLong(MinVersionFields.MIN_VERSION_CODE)?.toInt()
        ?: NO_MINIMUM_VERSION_CODE,
    minVersionName = readString(MinVersionFields.MIN_VERSION_NAME).orEmpty(),
    exemptDeviceIds = readStringList(MinVersionFields.MIN_VERSION_EXEMPT_DEVICES),
    deadlineLabel = readString(MinVersionFields.MIN_VERSION_DEADLINE).orEmpty(),
    updatePackage = readUpdatePackage()
)

private fun DocumentSnapshot.readUpdatePackage(): UpdatePackage? {
    val url = readString(MinVersionFields.MIN_VERSION_APK_URL)
    val sha256 = readString(MinVersionFields.MIN_VERSION_APK_SHA256)
    // Sin checksum no hay forma de dar la descarga por buena, y sin URL no hay
    // qué descargar: en cualquiera de los dos casos es "todavía no hay APK".
    if (url.isNullOrBlank() || sha256.isNullOrBlank()) return null
    return UpdatePackage(
        url = url,
        sizeBytes = readLong(MinVersionFields.MIN_VERSION_APK_SIZE) ?: 0L,
        sha256 = sha256
    )
}

private fun DocumentSnapshot.readLong(field: String): Long? = get(field) as? Long

private fun DocumentSnapshot.readString(field: String): String? = get(field) as? String

private fun DocumentSnapshot.readStringList(field: String): Set<String> = (get(field) as? List<*>)
    ?.filterIsInstance<String>()
    ?.filter { it.isNotBlank() }
    ?.toSet()
    .orEmpty()
