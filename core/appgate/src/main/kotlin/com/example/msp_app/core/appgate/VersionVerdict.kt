package com.example.msp_app.core.appgate

/**
 * Resultado de la compuerta de versión mínima. Dos valores y nada más: la
 * pantalla de bloqueo no tiene grises ("advertir", "degradar") — o la app
 * corre, o no corre.
 */
enum class VersionVerdict {
    /** La app arranca normal. */
    ALLOWED,

    /** Pantalla de bloqueo, sin salida, hasta que se instale la versión nueva. */
    BLOCKED
}

/**
 * "Sin mínimo configurado". Es el valor que devuelve la configuración remota
 * cuando el campo no existe (o vale `0`), y significa **compuerta apagada** —
 * el mismo interruptor que `MIN_APP_VERSION=""` en el servidor.
 */
const val NO_MINIMUM_VERSION_CODE: Int = 0

/**
 * La decisión, pura: sin Android, sin red, sin reloj.
 *
 * | `minVersionCode` | `exempt` | `installedVersionCode` | Veredicto |
 * |---|---|---|---|
 * | cualquiera | `true` | cualquiera | ALLOWED |
 * | `0` (ausente) | `false` | cualquiera | ALLOWED |
 * | negativo (corrupto) | `false` | cualquiera | ALLOWED |
 * | `N > 0` | `false` | `>= N` | ALLOWED |
 * | `N > 0` | `false` | `< N` | BLOCKED |
 *
 * **Se compara `versionCode` (entero), no `versionName`.** Es el único campo
 * con orden garantizado: como cadenas, `"2.9.5" > "2.10.0"`. `versionName`
 * viaja aparte y existe SOLO para que el usuario lea "tienes 2.15.0 ·
 * necesitas 2.17.0" — nunca decide.
 *
 * Nota de contraste con el servidor: el middleware `MinAppVersion` del API Go
 * compara el **nombre** de versión que llega en `X-App-Version` y responde
 * `409` (reintentable). Son dos mecanismos deliberadamente distintos: aquel es
 * el respaldo que corta el tráfico de una app vieja; éste es el que la app se
 * aplica a sí misma y funciona sin señal.
 *
 * @param installedVersionCode `versionCode` del APK instalado.
 * @param minVersionCode mínimo exigido; `0` o negativo = compuerta apagada.
 * @param exempt el dispositivo/build queda fuera de la compuerta
 *   (ver `isVersionGateExempt`). Gana sobre todo lo demás.
 */
fun decideVersionGate(
    installedVersionCode: Int,
    minVersionCode: Int,
    exempt: Boolean
): VersionVerdict = when {
    exempt -> VersionVerdict.ALLOWED
    // Ausente, `0`, o corrupto en negativo: nunca bloquear por un dato que no
    // se pudo leer. Un fallo de configuración no puede dejar varada a la flota.
    minVersionCode <= NO_MINIMUM_VERSION_CODE -> VersionVerdict.ALLOWED
    installedVersionCode >= minVersionCode -> VersionVerdict.ALLOWED
    else -> VersionVerdict.BLOCKED
}
