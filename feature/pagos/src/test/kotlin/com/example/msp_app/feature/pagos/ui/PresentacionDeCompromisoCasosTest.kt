package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.CompromisoDeLaVenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDeCompromiso
import com.example.msp_app.feature.pagos.domain.model.TipoDeCompromiso
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El juego de casos de presentación (plan visitas P2, Tarea 20; E-APP-115):
 * `src/test/resources/presentacion-compromisos/`, uno por estado × {con hora,
 * sin hora} (citas) o {con monto, sin monto} (promesas) × {con abonos, sin
 * abonos}, más la venta sin compromiso. Solo Kotlin: la regla de la columna y
 * del estado es del servidor; esto es cómo se rotula.
 *
 * Robolectric solo por `org.json`.
 */
class PresentacionDeCompromisoCasosTest : RobolectricTestBase() {

    private val carpeta = File(
        checkNotNull(javaClass.classLoader?.getResource("presentacion-compromisos")) {
            "falta src/test/resources/presentacion-compromisos"
        }.toURI()
    )

    private fun casos(): List<JSONObject> = carpeta.listFiles { f -> f.name.endsWith(".json") }!!
        .sortedBy { it.name }
        .map { JSONObject(it.readText(Charsets.UTF_8)) }

    @Test
    fun `cada caso contra la presentacion`() {
        val casos = casos()
        assertTrue("sin casos", casos.isNotEmpty())
        for (caso in casos) {
            val nombre = caso.getString("nombre")
            val hoy = LocalDate.parse(caso.getString("hoy"))
            val piezas = piezasDeLaTarjeta(
                conAbonos = caso.getBoolean("con_abonos"),
                compromiso = compromisoDe(caso.optJSONObject("compromiso")),
                hoy = hoy
            )
            val esperado = caso.getJSONObject("esperado")
            val principal = when (esperado.getString("principal")) {
                "estado" -> PiezaPrincipal.ESTADO
                "primer_abono" -> PiezaPrincipal.PRIMER_ABONO
                else -> error("$nombre: principal desconocido")
            }
            assertEquals(nombre, principal, piezas.principal)
            val c = esperado.optJSONObject("compromiso")
            if (c == null) {
                assertEquals(nombre, null, piezas.compromiso)
            } else {
                val p = checkNotNull(piezas.compromiso) { "$nombre: esperaba compromiso" }
                assertEquals(nombre, c.getString("titulo"), p.titulo)
                assertEquals(nombre, c.getString("detalle"), p.detalle)
                assertEquals(nombre, TratoDelEstado.valueOf(c.getString("trato")), p.trato)
            }
        }
    }

    /** Cobertura: el juego no puede perder un estado, un eje de hora/monto o el de abonos. */
    @Test
    fun `cobertura minima`() {
        val vistos = casos().mapNotNull { caso ->
            val c = caso.optJSONObject("compromiso") ?: return@mapNotNull "sin:${caso.getBoolean("con_abonos")}"
            val eje = if (c.getString("tipo") == "cita") {
                c.isNull(
                    "hora"
                )
            } else {
                c.isNull("monto_centavos")
            }
            "${c.getString("tipo")}:${c.getString("estado")}:$eje:${caso.getBoolean("con_abonos")}"
        }.toSet()
        val citas = EstadoDeCompromiso.entries - EstadoDeCompromiso.CUMPLIDA
        val promesas = EstadoDeCompromiso.entries - EstadoDeCompromiso.CITA_CUMPLIDA
        val faltan = buildList {
            for (abonos in listOf(true, false)) {
                add("sin:$abonos")
                for (eje in listOf(true, false)) {
                    citas.forEach { add("cita:${it.codigo}:$eje:$abonos") }
                    promesas.forEach { add("promesa:${it.codigo}:$eje:$abonos") }
                }
            }
        }.filterNot { it in vistos }
        assertEquals(emptyList<String>(), faltan)
    }

    private fun compromisoDe(o: JSONObject?): CompromisoDeLaVenta? {
        if (o == null) return null
        return CompromisoDeLaVenta(
            tipo = checkNotNull(TipoDeCompromiso.deCodigo(o.getString("tipo"))),
            estado = checkNotNull(EstadoDeCompromiso.deCodigo(o.getString("estado"))),
            dia = LocalDate.parse(o.getString("dia")),
            hora = if (o.isNull("hora")) null else LocalTime.parse(o.getString("hora")),
            montoCentavos = if (o.isNull("monto_centavos")) null else o.getLong("monto_centavos")
        )
    }
}
