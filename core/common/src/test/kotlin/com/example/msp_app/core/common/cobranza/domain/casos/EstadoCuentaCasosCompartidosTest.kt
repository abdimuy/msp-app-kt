package com.example.msp_app.core.common.cobranza.domain.casos

import com.example.msp_app.core.common.cobranza.domain.CoberturaDelPeriodo
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.cobranza.domain.EstadoCuentaDeriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Cada caso del juego compartido con msp-api (`testdata/estado-cuenta/`) corre
 * contra el MISMO deriver que usa la pantalla del cobrador. El servidor corre
 * los mismos archivos contra su port en Go: los dos verdes con el mismo VERSION
 * son la prueba de que teléfono y escritorio derivan igual con las mismas
 * entradas (E-APP-057).
 *
 * `internal` porque expone [CasoDeEstadoCuenta], que es `internal`; en bytecode
 * la clase sigue siendo pública, que es lo que exige el runner de JUnit 4.
 */
@RunWith(Parameterized::class)
internal class EstadoCuentaCasosCompartidosTest(private val caso: CasoDeEstadoCuenta) {

    @Test
    fun `estado y cobertura coinciden con esperado`() {
        val derivacion = EstadoCuentaDeriver.derivar(
            caso.cuentas,
            caso.pagos,
            caso.visitas,
            caso.ventana
        )
        val conAlgo = CoberturaDelPeriodo.ventasConAlgo(
            caso.cuentas,
            caso.pagos,
            caso.visitas,
            caso.ventana
        )
        val obtenido = caso.cuentas.map {
            Esperado(
                it.ventaId,
                derivacion.porVenta.getValue(it.ventaId).estado,
                it.ventaId in conAlgo
            )
        }.sortedBy { it.ventaId }
        assertEquals(caso.toString(), caso.esperado.sortedBy { it.ventaId }, obtenido)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun casos(): List<CasoDeEstadoCuenta> = JuegoDeCasos.archivosDeCaso().map {
            CasoDeEstadoCuenta.desdeJson(it.name, it.readText(Charsets.UTF_8))
        }
    }
}

/** El juego no puede ser verde por vacío o por no ejercitar las ramas (dependencia dura, punto 6). */
class JuegoDeCasosCoberturaTest {

    private val casos = EstadoCuentaCasosCompartidosTest.casos()

    @Test
    fun `los ocho estados aparecen en algun esperado`() {
        val vistos = casos.flatMap { c -> c.esperado.map { it.estado } }.toSet()
        assertEquals(EstadoCuenta.entries.toSet(), vistos)
    }

    @Test
    fun `aparecen ventas con algo y sin nada`() {
        val valores = casos.flatMap { c -> c.esperado.map { it.conAlgo } }.toSet()
        assertEquals(setOf(true, false), valores)
    }

    @Test
    fun `hay al menos un pago rechazado y una condonacion`() {
        val pagos = casos.flatMap { it.pagos }
        assertTrue("falta un caso con rechazado: true", pagos.any { it.rechazado })
        assertTrue(
            "falta un caso con condonación (137026)",
            pagos.any { it.formaCobroId == 137026 }
        )
    }
}
