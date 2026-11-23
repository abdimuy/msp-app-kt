package com.example.msp_app.feature.garantias.data.catalogo

import com.example.msp_app.feature.garantias.domain.model.CatalogoDeGarantias
import com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mientras `GET /v2/garantias/catalogos` no exista, el catálogo es
 * **explícitamente vacío**. La prueba impide que alguien "resuelva" la pantalla
 * copiando la máquina de estados del servidor al teléfono (D2).
 */
class CatalogoSinServidorTest {

    @Test
    fun `sin endpoint el catalogo esta vacio`() = runTest {
        val catalogo = CatalogoSinServidor().leer()

        assertTrue(catalogo.estaVacio)
        assertEquals(CatalogoDeGarantias.SIN_CATALOGO, catalogo)
    }

    @Test
    fun `sin catalogo no hay acciones para ninguna etapa`() = runTest {
        val catalogo = CatalogoSinServidor().leer()

        assertTrue(
            catalogo.accionesPara(etapa = "recolectado", origen = OrigenDeFolio.CLIENTE).isEmpty()
        )
        assertTrue(
            catalogo.accionesPara(etapa = "registrado", origen = OrigenDeFolio.PISO).isEmpty()
        )
    }
}
