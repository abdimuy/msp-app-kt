package com.example.msp_app.core.appgate

/**
 * El APK que hay que instalar, tal como lo publica la oficina.
 *
 * [sha256] no es opcional en la práctica: sin él la descarga no se puede dar
 * por buena (ver `ApkDownloader`). Un paquete sin checksum se trata como
 * "todavía no hay APK publicado".
 */
data class UpdatePackage(
    val url: String,
    val sizeBytes: Long,
    val sha256: String
)

/**
 * Configuración remota de la compuerta, tal como vive en Firestore
 * `config/api_settings` (el MISMO documento del kill-switch de `baseURL` que
 * ya escucha `ApiProvider` — un solo lugar que la oficina edita).
 *
 * Todos los campos tienen default "apagado": un documento sin ninguno de
 * ellos produce exactamente el estado previo a esta funcionalidad.
 *
 * [minVersionName] existe **solo para que se lea** ("tienes 2.15.0 · necesitas
 * 2.17.0"); la comparación la hace [minVersionCode]. Ver [decideVersionGate].
 */
data class MinVersionConfig(
    val minVersionCode: Int = NO_MINIMUM_VERSION_CODE,
    val minVersionName: String = "",
    val exemptDeviceIds: Set<String> = emptySet(),
    /**
     * Fecha límite ya formateada para leerse ("vie 22"). Viaja como texto y no
     * como marca de tiempo a propósito: quien la escribe es la misma persona
     * que avisa por WhatsApp, y así lo que se ve en la banda es palabra por
     * palabra lo que ella escribió — sin husos horarios de por medio.
     */
    val deadlineLabel: String = "",
    val updatePackage: UpdatePackage? = null
)

/** Nombres de los campos en `config/api_settings`. Un solo lugar donde viven. */
object MinVersionFields {
    const val MIN_VERSION_CODE = "MIN_VERSION_CODE"
    const val MIN_VERSION_NAME = "MIN_VERSION_NAME"
    const val MIN_VERSION_DEADLINE = "MIN_VERSION_DEADLINE"
    const val MIN_VERSION_EXEMPT_DEVICES = "MIN_VERSION_EXEMPT_DEVICES"
    const val MIN_VERSION_APK_URL = "MIN_VERSION_APK_URL"
    const val MIN_VERSION_APK_SIZE = "MIN_VERSION_APK_SIZE"
    const val MIN_VERSION_APK_SHA256 = "MIN_VERSION_APK_SHA256"
}

/**
 * De qué APK está hablando el APK instalado. Lo provee la raíz de composición
 * (`:app`, desde su `BuildConfig`) porque un módulo de librería no puede leer
 * el `BuildConfig` de la aplicación.
 */
data class AppBuildInfo(
    val versionCode: Int,
    val versionName: String,
    val debugBuild: Boolean
)
