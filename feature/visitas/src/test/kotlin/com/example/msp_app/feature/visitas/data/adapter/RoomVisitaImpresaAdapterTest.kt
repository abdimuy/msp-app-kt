package com.example.msp_app.feature.visitas.data.adapter

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CLIENTE = 5021
private const val VENTA = 77188
private const val EFECTIVO = 157

/**
 * La lectura de vuelta de una visita, sobre Room/SQLite real.
 *
 * Los tres hechos que esta clase fija:
 * 1. **Un id que no existe devuelve `null`, no una excepción.** `getVisitById`
 *    está declarada no-nula y revienta con `NullPointerException` cuando no
 *    encuentra la fila —los tests del repo ya la envuelven en `runCatching`—;
 *    por eso esta tarea agregó `findVisitById`, y aquí se comprueba que el
 *    adaptador usa esa y no la otra.
 * 2. **El monto prometido cruza en centavos enteros**, con
 *    `BigDecimal.valueOf(centavos, 2)` y jamás por `Double`.
 * 3. **Nada se traga** (NORMA DE ERRORES): una `FECHA` ilegible y una
 *    `CITA_HORA` mal formada emiten su propio código grepeable.
 */
class RoomVisitaImpresaAdapterTest : RoomTestBase() {

    private val telemetria = RecordingTelemetry()
    private val adapter by lazy { RoomVisitaImpresaAdapter(db.visitDao(), telemetria) }

    @Suppress(
        "LongParameterList"
    ) // constructor de fixture de una entidad ancha; nombrarlos es el punto.
    private fun visita(
        id: String = "vis-1",
        fecha: String = "2026-09-01T18:00:00Z",
        ventaLigada: Int = 0,
        promesaFecha: String? = null,
        centavos: Long? = null,
        citaFecha: String? = null,
        horaCita: String? = null
    ) = VisitEntity(
        ID = id,
        CLIENTE_ID = CLIENTE,
        COBRADOR = "Martín Salgado",
        COBRADOR_ID = 12,
        FECHA = fecha,
        FORMA_COBRO_ID = EFECTIVO,
        LAT = 18.46,
        LNG = -97.39,
        NOTA = "preguntar por la mañana",
        TIPO_VISITA = "No se encontraba",
        ZONA_CLIENTE_ID = 25,
        IMPTE_DOCTO_CC_ID = ventaLigada,
        GUARDADO_EN_MICROSIP = 0,
        PROMESA_FECHA = promesaFecha,
        PROMESA_MONTO_CENTAVOS = centavos,
        CITA_FECHA = citaFecha,
        CITA_HORA = horaCita
    )

    private suspend fun sembrar(fila: VisitEntity) = db.visitDao().insertVisit(fila)

    @Test
    fun `una visita guardada se lee de vuelta entera`() = runTest {
        sembrar(visita(ventaLigada = VENTA))

        val leida = adapter.visita("vis-1")

        assertEquals("vis-1", leida?.visitaId)
        assertEquals(CLIENTE, leida?.clienteId)
        assertEquals(VENTA, leida?.ventaId)
        assertEquals(Instant.parse("2026-09-01T18:00:00Z"), leida?.registradaEn)
        assertEquals("Martín Salgado", leida?.cobrador)
        assertEquals("No se encontraba", leida?.tipoVisita)
    }

    @Test
    fun `un id que no existe devuelve null y NO revienta`() = runTest {
        sembrar(visita())

        assertNull(adapter.visita("no-existe"))
        // Control positivo: el MISMO camino con el id bueno sí encuentra la fila.
        assertEquals("vis-1", adapter.visita("vis-1")?.visitaId)
    }

    @Test
    fun `IMPTE_DOCTO_CC_ID en cero significa sin venta ligada`() = runTest {
        sembrar(visita(ventaLigada = 0))

        assertNull(adapter.visita("vis-1")?.ventaId)
    }

    @Test
    fun `el monto prometido cruza en centavos exactos`() = runTest {
        // 1,287.35 pesos = 128 735 centavos. Ni un centavo fantasma.
        sembrar(visita(promesaFecha = "2026-09-04", centavos = 128_735L))

        val leida = adapter.visita("vis-1")

        assertEquals(Money.of(BigDecimal("1287.35")), leida?.montoPrometido)
        assertEquals(LocalDate.of(2026, 9, 4), leida?.fechaPromesa)
    }

    @Test
    fun `una promesa sin monto llega como null, nunca como cero`() = runTest {
        sembrar(visita(promesaFecha = "2026-09-04", centavos = null))

        assertNull(adapter.visita("vis-1")?.montoPrometido)
    }

    @Test
    fun `la cita cruza su dia y su hora`() = runTest {
        sembrar(visita(citaFecha = "2026-09-03", horaCita = "16:00"))

        val leida = adapter.visita("vis-1")

        assertEquals(LocalDate.of(2026, 9, 3), leida?.fechaCita)
        assertEquals(LocalTime.of(16, 0), leida?.horaCita)
    }

    @Test
    fun `una FECHA ilegible NO arma ticket y se reporta con su codigo`() = runTest {
        sembrar(visita(fecha = "el martes pasado"))

        // Sin fecha la regla del día no puede decidir: mejor sin ticket que con
        // una fecha inventada que abra o cierre la impresión por accidente.
        assertNull(adapter.visita("vis-1"))
        assertEquals(
            listOf(VisitasTelemetria.CODE_TICKET_VISITA_SIN_FECHA),
            telemetria.recorded.filter { it.type == TelemetryEventType.ERROR }.map { it.name }
        )
    }

    @Test
    fun `una CITA_HORA mal formada conserva la visita, sin hora, y se reporta`() = runTest {
        sembrar(visita(citaFecha = "2026-09-03", horaCita = "a las cuatro"))

        val leida = adapter.visita("vis-1")

        assertEquals(LocalDate.of(2026, 9, 3), leida?.fechaCita)
        assertNull(leida?.horaCita)
        val error = telemetria.recorded.single { it.type == TelemetryEventType.ERROR }
        assertEquals(VisitasTelemetria.CODE_TICKET_VISITA_HORA_INVALIDA, error.name)
        // Anti-PII: la clase de la excepción, nunca el texto que tecleó el cobrador.
        assertEquals(
            "DateTimeParseException",
            error.props[VisitasTelemetria.PROP_EXCEPCION]
        )
        assertTrue(error.props.values.none { it.contains("a las cuatro") })
    }

    @Test
    fun `el camino feliz no emite ningun error`() = runTest {
        // Control positivo de las dos ausencias de arriba: el grabador SÍ ve
        // errores cuando los hay, así que esta ausencia significa algo.
        sembrar(visita(citaFecha = "2026-09-03", horaCita = "16:00"))

        adapter.visita("vis-1")

        assertTrue(telemetria.recorded.none { it.type == TelemetryEventType.ERROR })
    }
}
