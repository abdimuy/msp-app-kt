package com.example.msp_app.core.visitas.agenda

import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-103: la bajada de la agenda está apagada salvo `bajada: true`. */
class KillSwitchAgendaTest : RobolectricTestBase() {

    @Test
    fun ausenteOInvalidoEsApagado_EAPP103() {
        assertFalse(KillSwitchAgendaVigente.bajadaDesde(null))
        assertFalse(KillSwitchAgendaVigente.bajadaDesde(emptyMap()))
        assertFalse(KillSwitchAgendaVigente.bajadaDesde(mapOf("bajada" to null)))
        assertFalse(KillSwitchAgendaVigente.bajadaDesde(mapOf("bajada" to "true")))
        assertFalse(KillSwitchAgendaVigente.bajadaDesde(mapOf("bajada" to 1L)))
        assertFalse(KillSwitchAgendaVigente.bajadaDesde(mapOf("bajada" to false)))
        assertFalse(KillSwitchAgendaVigente.bajadaDesde(mapOf("Bajada" to true)))
        assertTrue(KillSwitchAgendaVigente.bajadaDesde(mapOf("bajada" to true)))
    }

    @Test
    fun arrancaApagadoYUnErrorConservaElVigente_EAPP103() {
        val ks = KillSwitchAgendaVigente(firestore = { error("sin firestore en la prueba") })
        assertFalse("apagado antes de leer nada", ks.bajada)

        ks.alRecibir(mapOf("bajada" to true), null)
        assertTrue(ks.bajada)

        ks.alRecibir(null, RuntimeException("sin red"))
        assertTrue("un error de Firestore no cambia el valor vigente", ks.bajada)

        ks.alRecibir(null, null)
        assertFalse("documento borrado = apagado", ks.bajada)
    }
}
