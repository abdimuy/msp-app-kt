package com.example.msp_app.core.appgate

import android.content.Context
import android.provider.Settings

/**
 * Identificador estable del teléfono, para la lista de exentos.
 *
 * Puerto (no una clase Android) para que la decisión de exención se pueda
 * probar sin `Settings.Secure` de por medio.
 */
fun interface DeviceIdProvider {
    /** `null`/vacío cuando el sistema no lo entrega — nunca lanza. */
    fun deviceId(): String?
}

/**
 * `Settings.Secure.ANDROID_ID` — **exactamente** el mismo valor que ya calcula
 * `DeviceProtectionManager.deviceId` en `:app`, para que la lista de exentos
 * de la compuerta de versión se pueda copiar de la de dispositivos
 * autorizados sin traducir nada.
 */
class AndroidDeviceIdProvider(private val context: Context) : DeviceIdProvider {
    override fun deviceId(): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
}

/**
 * Quién queda fuera de la compuerta.
 *
 * Dos capas, ambas acá:
 * - **Build**: un `BuildConfig.DEBUG` nunca se bloquea. Quien compila la app
 *   no puede quedar atrapado por la misma compuerta que está tocando.
 * - **Dispositivo**: lista de `deviceId` exentos que viaja en la
 *   configuración remota, para desatorar un teléfono concreto sin bajarle el
 *   mínimo a toda la flota.
 *
 * **Tercera capa, sin código — es operativa.** `MIN_VERSION_CODE` se escribe
 * ÚNICAMENTE en el Firestore de producción (`msp-db-1c2ce`). Los flavors
 * `devlocal`/`devserver` apuntan a `msp-dev-96ff5`, donde ese campo no existe:
 * la configuración remota entrega `0` y la compuerta queda apagada por
 * [decideVersionGate]. No hay nada que verificar en tiempo de compilación —
 * si alguien escribiera el campo en el proyecto de desarrollo, la compuerta
 * se encendería ahí también, y sería el comportamiento correcto.
 *
 * @param debugBuild valor de `BuildConfig.DEBUG` del APK instalado.
 * @param deviceId identificador del teléfono; `null`/vacío nunca hace match
 *   (un id ilegible no puede convertirse en una exención accidental).
 * @param exemptDeviceIds lista de exentos de la configuración remota.
 */
fun isVersionGateExempt(
    debugBuild: Boolean,
    deviceId: String?,
    exemptDeviceIds: Set<String>
): Boolean = when {
    debugBuild -> true
    deviceId.isNullOrBlank() -> false
    else -> deviceId in exemptDeviceIds
}
