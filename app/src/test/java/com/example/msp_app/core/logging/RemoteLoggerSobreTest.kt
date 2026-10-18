package com.example.msp_app.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Lista blanca del sobre de [RemoteLogger] (revisión de la Tarea 14, decisión
 * del coordinador 2026-10-17): sale el uid como seudónimo, el correo y la
 * versión; `deviceInfo` ya no. Ver el KDoc de [sobreDeIdentidad].
 */
class RemoteLoggerSobreTest {

    /**
     * Las claves de identidad, una por línea. Quitar el correo cuando se decida
     * (P3, con un tablero de adopción por uid) es borrar su línea aquí y en
     * [sobreDeIdentidad].
     */
    private val sobre = setOf(
        "userId",
        "userEmail",
        "appVersion"
    )

    private val datos = mapOf<String, Any?>("visitas.pendientes" to 1, "nulo" to null)

    @Test
    fun `H14 el sobre de un evento lleva exactamente estas claves`() {
        val e = entradaDeEvento(
            "ts",
            "VISITAS_ENVIO",
            RemoteLogger.EventType.SYSTEM_EVENT,
            "visitas_estado",
            "uid-123",
            "cobrador@ejemplo.mx",
            datos
        )
        assertEquals(
            setOf("id", "timestamp", "module", "eventType", "eventName", "data") + sobre,
            e.keys
        )
        assertFalse("deviceInfo" in e.keys)
        assertEquals(mapOf("visitas.pendientes" to 1), e["data"])
    }

    @Test
    fun `H14 el sobre de un log lleva exactamente estas claves`() {
        val e = entradaDeLog(
            "ts",
            "2026-10-17 09:00:00",
            "COBRANZA",
            "COLLAPSE_TWIN",
            RemoteLogger.LogLevel.INFO,
            "m",
            "uid-123",
            "cobrador@ejemplo.mx",
            datos
        )
        assertEquals(
            setOf("id", "timestamp", "timestampString", "module", "action", "level", "message", "data") + sobre,
            e.keys
        )
    }

    @Test
    fun `H14 sin sesion el sobre dice anonymous`() {
        assertEquals("anonymous", sobreDeIdentidad(null, null)["userId"])
    }
}
