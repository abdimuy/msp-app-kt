package com.example.msp_app.core.avisos

import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-124 · `config/visitas_avisos`: ausente o inválido = apagado. */
class KillSwitchAvisosTest : RobolectricTestBase() {

    private fun con(datos: Map<String, Any?>?, error: Throwable? = null) =
        KillSwitchAvisosVigente().also { it.alRecibir(datos, error) }

    @Test
    fun `por omision esta apagado - E-APP-124`() {
        assertFalse(KillSwitchAvisosVigente().alarmas)
        assertFalse(con(null).alarmas)
        assertFalse(con(emptyMap()).alarmas)
    }

    @Test
    fun `solo el booleano true enciende - E-APP-124`() {
        assertTrue(con(mapOf("alarmas" to true)).alarmas)
        assertFalse(con(mapOf("alarmas" to "true")).alarmas)
        assertFalse(con(mapOf("alarmas" to 1)).alarmas)
        assertFalse(con(mapOf("fcm" to true)).alarmas)
        assertTrue(con(mapOf("fcm" to true)).fcm)
    }

    @Test
    fun `un error de lectura conserva el valor vigente - E-APP-124`() {
        val k = con(mapOf("alarmas" to true))
        k.alRecibir(null, IllegalStateException("sin red"))
        assertTrue(k.alarmas)
    }
}
