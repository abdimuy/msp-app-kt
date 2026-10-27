package com.example.msp_app.core.common.visitas.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Deriva [caso] y lo compara con su esperado; null si coinciden, o el porqué. */
internal fun diferenciaContraEsperado(
    caso: CasoDeCompromiso,
    visitas: List<VisitaCompromiso> = caso.visitas
): String? {
    val obtenido = derivarCompromisos(caso.cuentas, visitas, caso.pagos, caso.hoy, caso.zona)
    val esperado = caso.esperado.mapValues { it.value.vigente }
    if (obtenido != esperado) return "$caso\n  esperado=$esperado\n  obtenido=$obtenido"
    val columnas = obtenido.mapValues { columnaSugeridaDe(it.value) }
    val columnasEsperadas = caso.esperado.mapValues { it.value.columna }
    if (columnas != columnasEsperadas) return "$caso\n  columnas esperadas=$columnasEsperadas\n  obtenidas=$columnas"
    return null
}

/**
 * Deriva el estado de cada tarea de [caso] y lo compara con su esperado (plan
 * P3 Tarea 11, E-APP-122); null si coinciden, o el porqué. `derivarEstadoTarea`
 * recibe el historial completo del caso, como la función de Go.
 */
internal fun diferenciaDeTareas(
    caso: CasoDeCompromiso,
    visitas: List<VisitaCompromiso> = caso.visitas,
    pagos: List<PagoCompromiso> = caso.pagos
): String? {
    for (t in caso.tareas) {
        val obtenido = derivarEstadoTarea(t.tarea, visitas, pagos, t.cuenta, caso.hoy, caso.zona)
        if (obtenido != t.esperado) {
            return "$caso\n  tarea ${t.tarea.id}\n  esperado=${t.esperado}\n  obtenido=$obtenido"
        }
    }
    return null
}

/**
 * Cada caso del juego compartido con msp-api (`testdata/compromisos/`) contra
 * [derivarCompromisos]. msp-api corre los mismos bytes contra
 * `DerivarCompromisos`: los dos verdes con el mismo VERSION son la amarra I5
 * (E-APP-104). Incluye 021 (empate en el mismo segundo, E-APP-105) y 022 (23:30
 * local, E-APP-106).
 */
@RunWith(Parameterized::class)
internal class CompromisosCasosCompartidosTest(private val caso: CasoDeCompromiso) {

    @Test
    fun `cada caso contra la derivacion`() {
        assertEquals(null, diferenciaContraEsperado(caso))
    }

    /** El orden de entrada no importa: fija el desempate por id y no por posición. */
    @Test
    fun `invertir las visitas no cambia el resultado`() {
        assertEquals(null, diferenciaContraEsperado(caso, caso.visitas.reversed()))
    }

    /** Casos 057–076 (tareas, E-APP-122): el estado derivado contra el que fija Go. */
    @Test
    fun `cada tarea del caso contra la derivacion de tareas`() {
        assertEquals(null, diferenciaDeTareas(caso))
    }

    /** El orden de visitas y pagos no importa: fija el empate de instante por id. */
    @Test
    fun `invertir visitas y pagos no cambia el estado de las tareas`() {
        assertEquals(null, diferenciaDeTareas(caso, caso.visitas.reversed(), caso.pagos.reversed()))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun casos(): List<CasoDeCompromiso> = JuegoDeCompromisos.casos()
    }
}

/** El juego no puede ser verde por vacío o por no ejercitar las ramas. */
class CompromisosCoberturaTest {

    private val casos = JuegoDeCompromisos.casos()

    @Test
    fun `cobertura minima`() {
        val esperados = casos.flatMap { it.esperado.values }
        assertEquals(EstadoCompromiso.entries.toSet(), esperados.map { it.vigente.estado }.toSet())
        assertEquals(ColumnaSugerida.entries.toSet(), esperados.map { it.columna }.toSet())
        assertEquals(TipoCompromiso.entries.toSet(), esperados.map { it.vigente.tipo }.toSet())
        assertTrue(
            "al menos un caso sin entrada (reemplazada)",
            casos.any { it.esperado.isEmpty() }
        )
        assertTrue(
            "al menos una captura local pendiente",
            casos.any { c -> c.localPendiente.any { it } }
        )
        assertTrue("al menos un pago rechazado", casos.any { c -> c.pagos.any { it.rechazado } })
        assertTrue(
            "al menos un pago de oficina sin hora",
            casos.any { c ->
                c.pagos.any {
                    it.instante == null
                }
            }
        )
    }

    /** Los casos con tareas no pueden ser verdes por vacío (E-APP-122). */
    @Test
    fun `cobertura de tareas`() {
        val tareas = casos.flatMap { it.tareas }
        assertTrue("al menos 20 tareas", tareas.size >= 20)
        assertEquals(EstadoTarea.entries.toSet(), tareas.map { it.esperado.estado }.toSet())
        val atenciones = tareas.mapNotNull { it.esperado.atendida }
        assertEquals(TipoAtencion.entries.toSet(), atenciones.map { it.tipo }.toSet())
        assertTrue("una atendida por pago sin hora", atenciones.any { it.instante == null })
        assertTrue(
            "una cancelada que además tenía atención",
            casos.any { c ->
                c.tareas.any { it.esperado.estado == EstadoTarea.CANCELADA } && c.pagos.isNotEmpty()
            }
        )
    }

    @Test
    fun `los casos del Review Focus estan`() {
        val nombres = casos.map { it.archivo }
        assertTrue(nombres.contains("021-empate-mismo-segundo.json"))
        assertTrue(nombres.contains("022-visita-2330-local.json"))
    }
}

/**
 * Paridad con los 1000 historiales que genera msp-api con rapid
 * (`TestCompromisos_RapidGeneraCasosParaKotlin`, con COMPROMISOS_GENERADOS=1),
 * en `$MSP_API_DIR/testdata/compromisos-generados/`. Sin MSP_API_DIR se salta
 * (no es parte del juego versionado). Toda discrepancia se congela como caso
 * nuevo en testdata/compromisos/ (E-APP-104).
 */
class CompromisosGeneradosTest {

    @Test
    fun generadosCoincidenConGo() {
        val raiz = System.getenv("MSP_API_DIR")
        assumeTrue("MSP_API_DIR no está definido", !raiz.isNullOrBlank())
        val dir = File(raiz, "testdata/compromisos-generados")
        assertTrue(
            "no existe $dir; corre COMPROMISOS_GENERADOS=1 go test ./internal/visitas/domain/",
            dir.isDirectory
        )
        val casos = JuegoDeCompromisos.casos(dir)
        assertTrue(
            "se esperaban al menos $MINIMO_GENERADOS, hay ${casos.size}",
            casos.size >= MINIMO_GENERADOS
        )
        val diferencias = casos.mapNotNull { diferenciaContraEsperado(it) }
        assertEquals(
            "discrepancias Go-Kotlin:\n" + diferencias.joinToString("\n"),
            0,
            diferencias.size
        )
        println("generadosCoincidenConGo: ${casos.size} historiales, 0 discrepancias")
    }

    private companion object {
        const val MINIMO_GENERADOS = 1000
    }
}
