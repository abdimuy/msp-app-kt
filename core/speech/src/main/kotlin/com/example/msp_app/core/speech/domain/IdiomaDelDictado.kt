package com.example.msp_app.core.speech.domain

/**
 * Qué variante de español pedirle al reconocedor **de este teléfono**.
 *
 * ## El defecto que esto cierra, medido en vidrio
 *
 * El adaptador pedía `es-MX` fijo. En el SM-A256E del dueño —Android 15, API 35—
 * eso contesta:
 *
 * ```
 * es-MX  →  NO arranca — error 12 (ERROR_LANGUAGE_NOT_SUPPORTED)
 * es-US  →  SÍ arranca (escuchando)
 * ```
 *
 * O sea: **el dictado no habría funcionado ni una vez en el teléfono del dueño.**
 * El motor trae `[es-US]` instalado y `es-MX` no está ni en los 30 idiomas que
 * ese aparato dice soportar.
 *
 * **Por qué no lo atrapó nada antes:** `checkRecognitionSupport` contesta **lo
 * mismo** para `es-MX` que para `es-US` —también medido—, así que preguntarle no
 * distingue. Lo único que distingue es abrir la sesión. Ningún test de JVM podía
 * verlo, y `androidTest` no lo corre ninguna compuerta.
 *
 * ## La regla, y por qué NO es "usa el idioma del sistema"
 *
 * El KDoc del adaptador ya tenía razón en esto y se conserva: un teléfono
 * configurado en inglés no cambia el idioma en que la gente habla en la puerta.
 * Lo que estaba mal no era preferir español, era **asumir cuál español**.
 *
 * El orden de preferencia:
 *
 * 1. **`es-MX` si está instalado.** Es el español de la calle donde se cobra.
 * 2. **`es-US` si está.** Es español latinoamericano; para un cobrador de Tehuacán
 *    está mucho más cerca que el peninsular.
 * 3. **Cualquier otro `es-*` instalado**, en orden alfabético para que la elección
 *    sea **determinista**: el mismo teléfono tiene que elegir siempre lo mismo, o
 *    la calidad del dictado cambiaría entre dos arranques sin que nadie tocara nada.
 * 4. **Si no hay ningún español instalado**, se pide [PREFERIDO] igual. No es
 *    optimismo: es que sin español instalado **no hay dictado que salvar**, y pedir
 *    el idioma correcto deja el error del motor diciendo la verdad
 *    (`ERROR_LANGUAGE_NOT_SUPPORTED`) en vez de inventar un idioma ajeno que
 *    fallaría igual y confundiría el diagnóstico.
 *
 * Dominio PURO: sin Android, sin reloj, sin puertos.
 */
object IdiomaDelDictado {

    /** El español que se prefiere cuando el teléfono lo tiene. */
    const val PREFERIDO: String = "es-MX"

    /** El español latinoamericano que traen muchos aparatos en vez de `es-MX`. */
    const val LATINOAMERICANO: String = "es-US"

    /**
     * El idioma a pedir, dados los que el motor tiene **instalados en el
     * dispositivo** (no los que soporta: soportado es "se podría bajar", y eso no
     * dicta nada parado en una puerta sin señal).
     */
    fun de(instalados: List<String>): String {
        val espanoles = instalados.filter { esEspanol(it) }
        // Se devuelve el elemento ENCONTRADO, no la constante con la que se
        // comparó: si el aparato reporta `es_US`, hay que pedirle `es_US`. Volver
        // a pedirle una forma "canónica" que él no usa sería repetir el defecto
        // de `es-MX` con otro disfraz.
        return espanoles.firstOrNull { lo(it) == lo(PREFERIDO) }
            ?: espanoles.firstOrNull { lo(it) == lo(LATINOAMERICANO) }
            // Orden alfabético: determinista a propósito. Ver el KDoc.
            ?: espanoles.minByOrNull { lo(it) }
            ?: PREFERIDO
    }

    /**
     * `true` si la etiqueta es alguna variante de español.
     *
     * Compara solo la **subetiqueta de idioma** —lo que va antes del primer `-` o
     * `_`— porque las etiquetas llegan del motor con formas distintas (`es-US`,
     * `es_US`, `spa`) y un `startsWith("es")` crudo diría que sí a `est` (estonio).
     */
    fun esEspanol(etiqueta: String): Boolean = lo(etiqueta).substringBefore('-') in IDIOMAS_ESPANOL

    /**
     * Normaliza SOLO para comparar. Lo que se devuelve es siempre la etiqueta tal
     * como la reportó el motor: si el aparato dice `es_US`, se le pide `es_US`, no
     * una versión "bonita" que podría no reconocer.
     *
     * El guion bajo y el guion son el mismo separador para esto: los motores usan
     * los dos y `es_US` tiene que empatar con [LATINOAMERICANO].
     */
    private fun lo(etiqueta: String) = etiqueta.trim().lowercase().replace('_', '-')

    /** ISO 639-1 y 639-2 del español. `spa` aparece en algunos motores. */
    private val IDIOMAS_ESPANOL = setOf("es", "spa")
}
