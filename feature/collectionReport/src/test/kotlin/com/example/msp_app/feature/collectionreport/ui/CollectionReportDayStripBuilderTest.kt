package com.example.msp_app.feature.collectionreport.ui

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.collectionreport.domain.RangeCalculator
import com.example.msp_app.feature.collectionreport.domain.model.CollectionPayment
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lógica pura de la tira de días del ciclo ([CollectionReportDayStripBuilder]): qué días entran,
 * cuál queda seleccionado cuando el ciclo cambia, y la nota de arranque del día de la carga.
 *
 * Escenario base: el de producción de la ruta 34 ([MockupFixtures.CARGA_RUTA_34]) — carga el
 * jueves 6 de agosto de 2026 a las 19:33 CDMX, hoy jueves 13, ocho días de ciclo.
 */
class CollectionReportDayStripBuilderTest {

    private val clock = FakeClock(MockupFixtures.AHORA_RUTA_34)
    private val hoy = MockupFixtures.HOY_RUTA_34
    private val diaDeCarga = MockupFixtures.CICLO_RUTA_34.first()

    private fun cycleDays(carga: Instant? = MockupFixtures.CARGA_RUTA_34): List<LocalDate> =
        RangeCalculator.cycleDays(clock, carga)

    // ─── la tira lista EXACTAMENTE los días del ciclo ───────────────────────────────────

    @Test
    fun `la tira lista los ocho dias del ciclo, ni uno antes de la carga ni uno despues de hoy`() {
        val chips = CollectionReportDayStripBuilder.chips(
            cycleDays = cycleDays(),
            selectedDay = hoy,
            today = hoy,
            dayGroups = emptyMap()
        )

        assertEquals(MockupFixtures.CICLO_RUTA_34, chips.map { it.date })
        assertEquals(diaDeCarga, chips.first().date)
        assertEquals(hoy, chips.last().date)
        // El día ANTERIOR a la carga pertenece a un ciclo cerrado: no existe en la tira.
        assertFalse(chips.any { it.date == diaDeCarga.minusDays(1) })
        // Mañana tampoco: el ciclo cierra en hoy.
        assertFalse(chips.any { it.date.isAfter(hoy) })
    }

    @Test
    fun `sin fecha de carga no hay tira - un solo dia no es algo que elegir`() {
        val chips = CollectionReportDayStripBuilder.chips(
            cycleDays = cycleDays(carga = null),
            selectedDay = hoy,
            today = hoy,
            dayGroups = emptyMap()
        )

        assertTrue(chips.isEmpty())
    }

    @Test
    fun `un ciclo de un solo dia (carga hoy mismo) tampoco pinta tira`() {
        val chips = CollectionReportDayStripBuilder.chips(
            cycleDays = cycleDays(carga = AppTime.startOfDay(hoy)),
            selectedDay = hoy,
            today = hoy,
            dayGroups = emptyMap()
        )

        assertTrue(chips.isEmpty())
    }

    // ─── día en cero: presente y marcado, NUNCA ausente ─────────────────────────────────

    @Test
    fun `los dias sin cobros siguen en la tira, marcados como sin cobros`() {
        val chips = CollectionReportDayStripBuilder.chips(
            cycleDays = cycleDays(),
            selectedDay = hoy,
            today = hoy,
            dayGroups = dayGroupsRuta34()
        )

        // Los 8 días siguen ahí: los dos primeros del ciclo cerraron en cero (cargó de noche el
        // jueves y no cobró el viernes) y aun así se listan.
        assertEquals(MockupFixtures.CICLO_RUTA_34.size, chips.size)
        assertFalse(chips.first { it.date == diaDeCarga }.hasCollections)
        assertFalse(chips.first { it.date == LocalDate.of(2026, 8, 7) }.hasCollections)
        assertTrue(chips.first { it.date == LocalDate.of(2026, 8, 8) }.hasCollections)
        assertTrue(chips.last().hasCollections)
    }

    @Test
    fun `hoy y seleccionado son estados distintos y pueden no coincidir`() {
        val miercoles = LocalDate.of(2026, 8, 12)

        val chips = CollectionReportDayStripBuilder.chips(
            cycleDays = cycleDays(),
            selectedDay = miercoles,
            today = hoy,
            dayGroups = dayGroupsRuta34()
        )

        val chipMiercoles = chips.first { it.date == miercoles }
        val chipHoy = chips.first { it.date == hoy }
        assertTrue(chipMiercoles.isSelected)
        assertFalse(chipMiercoles.isToday)
        assertTrue(chipHoy.isToday)
        assertFalse(chipHoy.isSelected)
    }

    // ─── resolución del día seleccionado (incl. cambio de ciclo) ────────────────────────

    @Test
    fun `sin peticion se muestra hoy`() {
        assertEquals(hoy, CollectionReportDayStripBuilder.resolveSelectedDay(cycleDays(), null))
    }

    @Test
    fun `una peticion dentro del ciclo se respeta`() {
        val domingo = LocalDate.of(2026, 8, 9)

        assertEquals(
            domingo,
            CollectionReportDayStripBuilder.resolveSelectedDay(cycleDays(), domingo)
        )
    }

    @Test
    fun `una peticion anterior a la carga vuelve a hoy`() {
        val ayerDelCicloPasado = diaDeCarga.minusDays(3)

        assertEquals(
            hoy,
            CollectionReportDayStripBuilder.resolveSelectedDay(cycleDays(), ayerDelCicloPasado)
        )
    }

    /**
     * El caso del CAMBIO DE CICLO: el cobrador vuelve a cargar ruta el lunes 10, así que la tira
     * pasa a ser lun 10 … jue 13. El domingo 9 que tenía elegido ya no existe en ella y la
     * selección debe volver a hoy — no quedarse apuntando a un día fantasma (que daría un rango
     * vacío permanente, leído como "no cobré nada").
     */
    @Test
    fun `al cambiar de ciclo la seleccion vuelve a hoy en vez de quedar en un dia fantasma`() {
        val domingo = LocalDate.of(2026, 8, 9)
        val cicloViejo = cycleDays()
        assertTrue(domingo in cicloViejo)

        val cargaNueva = AppTime.parseWireFormat("2026-08-10T15:00:00Z") // lunes 10, 09:00 CDMX
        val cicloNuevo = cycleDays(carga = cargaNueva)

        assertFalse(domingo in cicloNuevo)
        assertEquals(
            hoy,
            CollectionReportDayStripBuilder.resolveSelectedDay(cicloNuevo, domingo)
        )
    }

    @Test
    fun `un ciclo vacio (carga en el futuro) no selecciona nada`() {
        val cargaFutura = AppTime.parseWireFormat("2026-09-01T15:00:00Z")

        assertEquals(
            null,
            CollectionReportDayStripBuilder.resolveSelectedDay(cycleDays(carga = cargaFutura), hoy)
        )
    }

    // ─── nota del día de la carga ───────────────────────────────────────────────────────

    @Test
    fun `el dia de la carga lleva la hora de arranque y el motivo`() {
        val nota = CollectionReportDayStripBuilder.startNote(
            diaDeCarga,
            MockupFixtures.CARGA_RUTA_34
        )

        // La hora se afirma por sus dígitos: el sufijo AM/PM lo formatea el CLDR de la JVM
        // ("p.m." / "p. m." según versión) y no es lo que este test cuida.
        assertTrue("nota sin la hora de carga: '$nota'", nota.contains("7:33"))
        assertTrue("nota sin el motivo: '$nota'", nota.contains("inicio de semana"))
    }

    @Test
    fun `los demas dias del ciclo no llevan nota`() {
        MockupFixtures.CICLO_RUTA_34.drop(1).forEach { day ->
            assertEquals(
                "el día $day no debería llevar nota de arranque",
                "",
                CollectionReportDayStripBuilder.startNote(day, MockupFixtures.CARGA_RUTA_34)
            )
        }
    }

    @Test
    fun `sin fecha de carga no hay nota que mostrar`() {
        assertEquals("", CollectionReportDayStripBuilder.startNote(hoy, null))
        assertEquals(
            "",
            CollectionReportDayStripBuilder.startNote(null, MockupFixtures.CARGA_RUTA_34)
        )
    }

    // ─── el rango del día respeta la selección Y el recorte del ciclo ───────────────────

    @Test
    fun `el rango del dia de la carga arranca a la hora de la carga, no a medianoche`() {
        val range = CollectionReportStateBuilder.resolveRange(
            period = ReportPeriod.DIA,
            clock = clock,
            fechaCargaInicial = MockupFixtures.CARGA_RUTA_34,
            selectedDay = diaDeCarga
        )

        assertEquals(AppTime.toWireFormat(MockupFixtures.CARGA_RUTA_34), range.startIso)
        assertEquals(
            AppTime.toWireFormat(AppTime.startOfNextDay(diaDeCarga)),
            range.endExclusiveIso
        )
    }

    @Test
    fun `el rango de un dia intermedio es el dia natural completo`() {
        val domingo = LocalDate.of(2026, 8, 9)

        val range = CollectionReportStateBuilder.resolveRange(
            period = ReportPeriod.DIA,
            clock = clock,
            fechaCargaInicial = MockupFixtures.CARGA_RUTA_34,
            selectedDay = domingo
        )

        assertEquals(AppTime.toWireFormat(AppTime.startOfDay(domingo)), range.startIso)
        assertEquals(AppTime.toWireFormat(AppTime.startOfNextDay(domingo)), range.endExclusiveIso)
    }

    // ─── helpers ────────────────────────────────────────────────────────────────────────

    /** `yyyy-MM-dd -> pagos` con la forma que devuelve `paymentsGroupedByDaySince`. */
    private fun dayGroupsRuta34(): Map<String, List<CollectionPayment>> = MockupFixtures
        .TOTALES_RUTA_34
        .filterValues { it > Money.ZERO }
        .entries
        .associate { (day, total) ->
            AppTime.toWireDate(day) to listOf(
                CollectionPayment(
                    id = "p-$day",
                    cliente = "Rosa Martínez Cruz",
                    ventaLabel = "70001",
                    amount = total,
                    method = PaymentMethod.EFECTIVO,
                    paidAt = AppTime.startOfDay(day).plusSeconds(SECONDS_MIDMORNING),
                    synced = true
                )
            )
        }

    private companion object {
        /** ~10:00 de la mañana desde la medianoche de negocio. */
        const val SECONDS_MIDMORNING = 36_000L
    }
}
