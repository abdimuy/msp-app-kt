package com.example.msp_app.core.speech.application

import com.example.msp_app.core.speech.domain.MotorDeDictado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **La tabla completa de la elección de motor.** Ocho filas, que son todas las
 * combinaciones de las tres entradas: una función total se prueba entera o no
 * se prueba.
 *
 * Es el control de reversión de la decisión central del módulo. Si alguien
 * invierte el orden de las dos primeras ramas, o deja que whisper gane sin el
 * modelo, estas filas se ponen rojas.
 */
class ElegirElMotorDeDictadoTest {

    @Test
    fun `con libreria y modelo gana whisper`() {
        assertEquals(
            MotorDeDictado.WHISPER,
            ElegirElMotorDeDictado(
                libreriaNativaCargada = true,
                modeloListo = true,
                reconocedorDeAndroidDisponible = true
            )
        )
    }

    @Test
    fun `whisper gana aunque el reconocedor de android no exista`() {
        assertEquals(
            MotorDeDictado.WHISPER,
            ElegirElMotorDeDictado(
                libreriaNativaCargada = true,
                modeloListo = true,
                reconocedorDeAndroidDisponible = false
            )
        )
    }

    /**
     * **La degradación, fila por fila.** Falta media condición de whisper y el
     * cobrador dicta igual: eso es lo innegociable del JNI escrito como test.
     */
    @Test
    fun `sin modelo degrada al reconocedor de android`() {
        assertEquals(
            MotorDeDictado.ANDROID,
            ElegirElMotorDeDictado(
                libreriaNativaCargada = true,
                modeloListo = false,
                reconocedorDeAndroidDisponible = true
            )
        )
    }

    @Test
    fun `sin libreria nativa degrada al reconocedor de android`() {
        assertEquals(
            MotorDeDictado.ANDROID,
            ElegirElMotorDeDictado(
                libreriaNativaCargada = false,
                modeloListo = true,
                reconocedorDeAndroidDisponible = true
            )
        )
    }

    @Test
    fun `sin libreria ni modelo degrada al reconocedor de android`() {
        assertEquals(
            MotorDeDictado.ANDROID,
            ElegirElMotorDeDictado(
                libreriaNativaCargada = false,
                modeloListo = false,
                reconocedorDeAndroidDisponible = true
            )
        )
    }

    /**
     * Las tres filas sin ningún motor. `null` significa "se escribe a mano", y
     * es un desenlace legítimo — no un error que haya que reportar.
     */
    @Test
    fun `sin nada no hay motor y eso no es un error`() {
        assertNull(
            ElegirElMotorDeDictado(
                libreriaNativaCargada = false,
                modeloListo = false,
                reconocedorDeAndroidDisponible = false
            )
        )
        assertNull(
            ElegirElMotorDeDictado(
                libreriaNativaCargada = true,
                modeloListo = false,
                reconocedorDeAndroidDisponible = false
            )
        )
        assertNull(
            ElegirElMotorDeDictado(
                libreriaNativaCargada = false,
                modeloListo = true,
                reconocedorDeAndroidDisponible = false
            )
        )
    }
}
