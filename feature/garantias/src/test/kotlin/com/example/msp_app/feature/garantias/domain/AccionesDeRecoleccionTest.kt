package com.example.msp_app.feature.garantias.domain

import com.example.msp_app.feature.garantias.domain.model.AccionDeGarantia
import com.example.msp_app.feature.garantias.domain.model.CatalogoDeGarantias
import com.example.msp_app.feature.garantias.domain.model.EtapaYOrigen
import com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **El respaldo de la recolección respeta el origen y la etapa** (hallazgo del
 * punta a punta: el servidor rechazó `recolectado` desde `registrado`).
 *
 * Sin el origen en la llave, un folio de piso recibiría la acción de cliente
 * —`recolectado`, que el servidor nunca admite para piso— y un folio de cliente
 * se saltaría `pendiente_recoleccion`. Estas pruebas fijan las únicas tres
 * aristas que el tronco admite
 * (`internal/garantias/domain/transiciones.go`, `SiguientesPorAvanzar`).
 */
class AccionesDeRecoleccionTest {

    @Test
    fun `un folio de piso en registrado pasa a revision`() {
        assertEquals(
            listOf(AccionDeGarantia(etapaDestino = "en_revision", etiqueta = "Pasar a revisión")),
            acciones("registrado", OrigenDeFolio.PISO)
        )
    }

    @Test
    fun `un folio de cliente en registrado queda pendiente de recoleccion`() {
        assertEquals(
            listOf(
                AccionDeGarantia(
                    etapaDestino = "pendiente_recoleccion",
                    etiqueta = "Programar recolección"
                )
            ),
            acciones("registrado", OrigenDeFolio.CLIENTE)
        )
    }

    @Test
    fun `un folio de cliente pendiente de recoleccion se marca recolectado`() {
        assertEquals(
            listOf(
                AccionDeGarantia(etapaDestino = "recolectado", etiqueta = "Marcar recolectado")
            ),
            acciones("pendiente_recoleccion", OrigenDeFolio.CLIENTE)
        )
    }

    @Test
    fun `el respaldo nunca ofrece recolectado desde registrado`() {
        val etapasDePiso = acciones("registrado", OrigenDeFolio.PISO).map { it.etapaDestino }
        val etapasDeCliente = acciones("registrado", OrigenDeFolio.CLIENTE).map { it.etapaDestino }

        assertFalse(etapasDePiso.contains("recolectado"))
        assertFalse(etapasDeCliente.contains("recolectado"))
    }

    @Test
    fun `un folio de piso nunca camina el tronco de recoleccion`() {
        assertTrue(acciones("pendiente_recoleccion", OrigenDeFolio.PISO).isEmpty())
        assertTrue(acciones("recolectado", OrigenDeFolio.PISO).isEmpty())
    }

    @Test
    fun `una etapa recolectada no ofrece salida`() {
        assertTrue(acciones("recolectado", OrigenDeFolio.CLIENTE).isEmpty())
    }

    @Test
    fun `con catalogo se usa el catalogo y no el respaldo`() {
        val catalogo = CatalogoDeGarantias(
            porEtapaYOrigen = mapOf(
                EtapaYOrigen("registrado", OrigenDeFolio.PISO) to listOf(
                    AccionDeGarantia(etapaDestino = "enviar_proveedor", etiqueta = "Enviar a proveedor")
                )
            )
        )

        assertEquals(
            listOf(
                AccionDeGarantia(etapaDestino = "enviar_proveedor", etiqueta = "Enviar a proveedor")
            ),
            AccionesDeRecoleccion.acciones(catalogo, "registrado", OrigenDeFolio.PISO)
        )
    }

    private fun acciones(etapa: String, origen: OrigenDeFolio): List<AccionDeGarantia> =
        AccionesDeRecoleccion.acciones(CatalogoDeGarantias.SIN_CATALOGO, etapa, origen)
}
