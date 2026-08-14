package com.example.msp_app.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato de [MexicanPhone] frente al VO `NewTelefono` del API Go. Si algo de
 * aquí cambia, el servidor y la app dejan de opinar lo mismo sobre qué teléfono
 * es válido — que es exactamente cómo se produjo el incidente del 2026-08-13.
 */
class MexicanPhoneTest {

    // --- Caso EXACTO del incidente 2026-08-13 ---

    @Test
    fun `incidente 2026-08-13 - 000000 nunca produce +52000000`() {
        // El vendedor tecleó "000000"; la implementación anterior anteponía el
        // prefijo sin contar dígitos y emitía "+52000000", que el API rechaza
        // con `telefono_invalid`. La venta rebotó todo el día en la cola.
        assertNull(MexicanPhone.toE164OrNull("000000"))
        assertFalse(MexicanPhone.isValid("000000"))
    }

    @Test
    fun `incidente 2026-08-13 - el +52000000 ya persistido en Room sale como null`() {
        // Filas viejas que ya viven en Room con el teléfono malo: al reintentarse
        // deben salir SIN teléfono (el API lo acepta ausente) en vez de rebotar
        // para siempre. La versión anterior devolvía la cadena intacta por el "+".
        assertNull(MexicanPhone.toE164OrNull("+52000000"))
    }

    // --- Formatos válidos ---

    @Test
    fun `10 digitos nacionales es valido`() {
        assertTrue(MexicanPhone.isValid("2381202772"))
        assertEquals("+522381202772", MexicanPhone.toE164OrNull("2381202772"))
    }

    @Test
    fun `los dos formatos normalizan al mismo E164`() {
        assertEquals(
            MexicanPhone.toE164OrNull("2381202772"),
            MexicanPhone.toE164OrNull("+522381202772")
        )
        assertEquals("+522381202772", MexicanPhone.toE164OrNull("+522381202772"))
    }

    @Test
    fun `acepta separadores humanos como el VO del servidor`() {
        assertEquals("+522381202772", MexicanPhone.toE164OrNull("238 120 2772"))
        assertEquals("+522381202772", MexicanPhone.toE164OrNull("238-120-2772"))
        assertEquals("+522381202772", MexicanPhone.toE164OrNull("(238) 120.2772"))
        assertEquals("+522381202772", MexicanPhone.toE164OrNull("+52 238 120 2772"))
    }

    @Test
    fun `52 sin mas prefijo tambien se recorta`() {
        // 12 dígitos que empiezan con 52 = código de país + nacional, igual que
        // `NewTelefono` en `telefono_vo.go`.
        assertEquals("+522381202772", MexicanPhone.toE164OrNull("522381202772"))
    }

    @Test
    fun `espacios alrededor no invalidan`() {
        assertEquals("+522381202772", MexicanPhone.toE164OrNull("  2381202772  "))
    }

    // --- Formatos inválidos ---

    @Test
    fun `vacio y blancos son invalidos para el formato`() {
        // "Sin teléfono" es decisión del formulario, no de este validador.
        assertFalse(MexicanPhone.isValid(""))
        assertFalse(MexicanPhone.isValid("   "))
        assertNull(MexicanPhone.toE164OrNull(""))
        assertNull(MexicanPhone.toE164OrNull("   "))
    }

    @Test
    fun `9 digitos es invalido`() {
        assertFalse(MexicanPhone.isValid("238120277"))
        assertNull(MexicanPhone.toE164OrNull("238120277"))
    }

    @Test
    fun `11 digitos es invalido`() {
        assertFalse(MexicanPhone.isValid("23812027722"))
        assertNull(MexicanPhone.toE164OrNull("23812027722"))
    }

    @Test
    fun `13 digitos es invalido aunque empiece con 52`() {
        assertFalse(MexicanPhone.isValid("5223812027722"))
    }

    @Test
    fun `letras solas son invalidas`() {
        assertFalse(MexicanPhone.isValid("no tiene"))
        assertNull(MexicanPhone.toE164OrNull("no tiene"))
    }

    @Test
    fun `nationalDigitsOrNull devuelve siempre los 10 nacionales`() {
        assertEquals("2381202772", MexicanPhone.nationalDigitsOrNull("2381202772"))
        assertEquals("2381202772", MexicanPhone.nationalDigitsOrNull("+522381202772"))
        assertNull(MexicanPhone.nationalDigitsOrNull("000000"))
    }
}
