package com.example.msp_app.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Barrido paramétrico por longitud sobre [MexicanPhone]: de 0 a 15 dígitos,
 * **solo** los de 10 pueden pasar.
 *
 * La versión anterior del normalizador jamás contaba dígitos: cualquier cadena
 * con al menos uno salía como `+52<lo que fuera>`. Un test puntual por caso
 * ("000000") arregla el síntoma; este barrido cierra la clase entera de defecto,
 * para que un cambio futuro no reabra el hueco por otro largo.
 *
 * Los casos usan `1` repetido a propósito: al no empezar con `52` aíslan la
 * regla de longitud del recorte de código de país (probado aparte en
 * `MexicanPhoneTest`).
 */
@RunWith(Parameterized::class)
class MexicanPhoneLengthTest(
    private val raw: String,
    private val esperadoValido: Boolean
) {

    @Test
    fun `solo diez digitos nacionales son validos`() {
        assertEquals(esperadoValido, MexicanPhone.isValid(raw))
    }

    @Test
    fun `todo lo invalido normaliza a null, nunca a una cadena a medias`() {
        if (!esperadoValido) {
            assertNull(MexicanPhone.toE164OrNull(raw))
        } else {
            assertEquals("+52$raw", MexicanPhone.toE164OrNull(raw))
        }
    }

    companion object {
        private const val NATIONAL_LENGTH = 10

        @JvmStatic
        @Parameterized.Parameters(name = "{0} digitos -> valido={1}")
        fun casos(): List<Array<Any>> = (0..15).map { len ->
            arrayOf("1".repeat(len), len == NATIONAL_LENGTH)
        }
    }
}
