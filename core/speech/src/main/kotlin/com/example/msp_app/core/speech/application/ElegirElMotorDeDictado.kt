package com.example.msp_app.core.speech.application

import com.example.msp_app.core.speech.domain.MotorDeDictado

/**
 * **Qué motor corre, decidido en un solo lugar y sin Android adentro.**
 *
 * ## La app vive en DOS MODOS a la vez, y eso es una regla, no un accidente
 *
 * - **Sin modelo** → el reconocedor que ya trae el teléfono.
 * - **Con modelo** → whisper.
 *
 * Los dos modos coexisten en la misma flota, y los dos tienen que dictar. Por
 * eso esta función es **total**: para cualquier combinación de las tres
 * entradas devuelve un motor o `null`, y `null` significa "se escribe a mano",
 * nunca "se rompe".
 *
 * ## El orden de las preguntas es el contrato
 *
 * whisper gana **solo si las dos mitades están**: la librería nativa cargada Y
 * el modelo verificado en disco. Que falte cualquiera de las dos degrada a
 * Android — no lanza, no avisa, no deja al cobrador sin dictar. Es la condición
 * innegociable del JNI: *un teléfono sin el modelo tiene que dictar igual*.
 *
 * Y Android solo entra si el reconocedor en-dispositivo existe, que es cosa de
 * la versión del sistema (`createOnDeviceSpeechRecognizer`, API 33+). Un
 * teléfono viejo sin el modelo bajado cae en `null`, que es la verdad.
 *
 * Pura y en `application/` a propósito: es **la** decisión del módulo, y acá se
 * prueba sin un `.so`, sin un micrófono y sin un teléfono.
 */
object ElegirElMotorDeDictado {

    /**
     * @param libreriaNativaCargada ¿`System.loadLibrary` de whisper funcionó?
     * @param modeloListo ¿el `.bin` está completo y verificado en disco?
     * @param reconocedorDeAndroidDisponible ¿el sistema ofrece el reconocedor
     *   en-dispositivo? (Android 13+, y el fabricante no lo quitó)
     * @return el motor a usar, o `null` si no hay ninguno.
     */
    operator fun invoke(
        libreriaNativaCargada: Boolean,
        modeloListo: Boolean,
        reconocedorDeAndroidDisponible: Boolean
    ): MotorDeDictado? = when {
        libreriaNativaCargada && modeloListo -> MotorDeDictado.WHISPER
        reconocedorDeAndroidDisponible -> MotorDeDictado.ANDROID
        else -> null
    }
}
