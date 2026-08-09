package com.example.msp_app.core.network

/**
 * Configuración de red **inmutable e inyectada**. Contiene lo que hoy vive en el
 * `BuildConfig` de `:app` (`LEGACY_BASE_URL`, la base v2 del Go, `IMAGES_BASE_URL`
 * y la versión de la app) — valores que un módulo *library* como `:core:network`
 * NO puede leer, porque `BuildConfig` es del módulo de aplicación.
 *
 * Por eso se **inyecta**: `:app` la provee en T7 leyendo su propio `BuildConfig`
 * (y, en release, el override remoto de Firestore para [v2BaseUrl]/[legacyBaseUrl]).
 * Esta clase es un contenedor de datos puro — sin Firebase, sin `BuildConfig`,
 * sin estado mutable — para que el módulo quede *vendor-free* y testeable con un
 * valor literal en los tests.
 *
 * **Kill-switch:** esta clase es inmutable a propósito. La reactividad del
 * kill-switch de baseURL NO vive aquí, sino en cómo se *provee*: el consumidor
 * inyecta `Provider<NetworkConfig>` (ver [RetrofitClientFactory]) y resuelve el
 * valor vigente por llamada, de modo que un cambio de baseURL alcance a la
 * siguiente request en vez de quedar congelado en un `@Singleton`.
 *
 * @property legacyBaseUrl base del backend v1 (Node), sin autenticación bearer.
 * @property v2BaseUrl base del backend v2 (Go), autenticado con bearer por request.
 * @property imagesBaseUrl base del backend de imágenes.
 * @property appVersion versión de la app (p. ej. `BuildConfig.VERSION_NAME`),
 *   enviada como header `X-App-Version` de forma *best-effort* (ver
 *   [AppVersionInterceptor]).
 */
data class NetworkConfig(
    val legacyBaseUrl: String,
    val v2BaseUrl: String,
    val imagesBaseUrl: String,
    val appVersion: String
)
