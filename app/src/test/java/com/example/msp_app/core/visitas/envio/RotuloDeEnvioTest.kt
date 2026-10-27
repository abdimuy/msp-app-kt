package com.example.msp_app.core.visitas.envio

import com.example.msp_app.core.database.entities.VisitEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** Plan visitas P1, Tarea 14; spec §4.4: la resguardada no se rotula "enviada". */
class RotuloDeEnvioTest {

    private fun visita(guardado: Int, confirmada: String? = null, resguardada: String? = null) =
        VisitEntity(
            ID = "6f1c2a3b-0000-4000-8000-00000000000a",
            CLIENTE_ID = 30144,
            COBRADOR = "Domínguez Reyes, Efraín",
            COBRADOR_ID = 7,
            FECHA = "2026-10-16T10:00:00Z",
            FORMA_COBRO_ID = 157,
            LAT = 19.043415,
            LNG = -98.198234,
            TIPO_VISITA = "SIN_PAGO",
            ZONA_CLIENTE_ID = 21,
            GUARDADO_EN_MICROSIP = guardado,
            CONFIRMADA_EN = confirmada,
            RESGUARDADA_EN = resguardada
        )

    @Test
    fun `confirmada es enviada`() {
        assertEquals("enviada", rotuloDeEnvio(visita(1, confirmada = "2026-10-16T11:00:00Z")))
    }

    @Test
    fun `resguardada es en revision de oficina`() {
        assertEquals(
            "en revisión de oficina",
            rotuloDeEnvio(visita(1, resguardada = "2026-10-16T11:00:00Z"))
        )
    }

    @Test
    fun `sin soltar es pendiente de envio`() {
        assertEquals("pendiente de envío", rotuloDeEnvio(visita(0)))
    }

    @Test
    fun `legada soltada sin marcas es enviada sin confirmar`() {
        assertEquals("enviada (sin confirmar)", rotuloDeEnvio(visita(1)))
    }
}
