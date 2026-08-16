package com.example.msp_app.feature.collectionreport.domain

import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Robustez SUPREMA de los rangos half-open en zona negocio: fin EXCLUSIVO,
 * ciclo del cobrador que abre en el INSTANTE de la carga (no a medianoche),
 * recorte por día, rangos vacíos bien formados, fallback null, cruce de
 * medianoche/mes/año y una transición DST histórica de México (2021). JVM puro
 * con [FakeClock] — determinista, sin zona del dispositivo.
 *
 * El bloque "incidente ruta 34" reproduce el error medido contra producción el
 * 13-ago-2026 (18 pagos por $3,150 contados dos veces, $48,200 reportados
 * contra $43,850 reales). Si esos tests se ponen en rojo, el doble conteo
 * volvió.
 */
class RangeCalculatorTest {

    // 2026-08-07 12:00 en America/Mexico_City (offset -06:00, sin DST desde 2022).
    private val noonAug7Cdmx = FakeClock(Instant.parse("2026-08-07T18:00:00Z"))

    private fun contains(range: DateRange, iso: String): Boolean {
        val t = Instant.parse(iso)
        val start = Instant.parse(range.startIso)
        val end = Instant.parse(range.endExclusiveIso)
        return !t.isBefore(start) && t.isBefore(end)
    }

    /**
     * [RangeCalculator.cycleRange] donde SÍ debe existir semana. Falla el test —con mensaje— si
     * devuelve `null`, en vez de dejar que un `!!` reviente con un NPE sin contexto.
     */
    private fun cycle(clock: FakeClock, carga: Instant?): DateRange = requireNotNull(
        RangeCalculator.cycleRange(clock, carga)
    ) { "se esperaba una semana utilizable para carga=$carga" }

    /** [RangeCalculator.cycleInfo] donde SÍ debe existir semana (ver [cycle]). */
    private fun info(clock: FakeClock, carga: Instant?): CycleInfo = requireNotNull(
        RangeCalculator.cycleInfo(clock, carga)
    ) { "se esperaban etiquetas de semana para carga=$carga" }

    // region — dayRange

    @Test
    fun `dayRange serializa medianoche negocio a wire UTC`() {
        val range = RangeCalculator.dayRange(noonAug7Cdmx, null)
        assertEquals("2026-08-07T06:00:00Z", range.startIso)
        assertEquals("2026-08-08T06:00:00Z", range.endExclusiveIso)
    }

    @Test
    fun `dayRange char-test un pago a las 23-59-59 SI cae dentro de hoy`() {
        val range = RangeCalculator.dayRange(noonAug7Cdmx, null)
        // 23:59:59 del 7-ago en CDMX == 05:59:59Z del 8-ago.
        assertTrue(contains(range, "2026-08-08T05:59:59Z"))
    }

    @Test
    fun `dayRange el fin es EXCLUSIVO - 00-00-00 del dia siguiente NO cae dentro`() {
        val range = RangeCalculator.dayRange(noonAug7Cdmx, null)
        // 00:00:00 del 8-ago en CDMX == 06:00:00Z, igual al borde exclusivo.
        assertFalse(contains(range, "2026-08-08T06:00:00Z"))
    }

    @Test
    fun `dayRange incluye el inicio 00-00-00`() {
        val range = RangeCalculator.dayRange(noonAug7Cdmx, null)
        assertTrue(contains(range, "2026-08-07T06:00:00Z"))
    }

    @Test
    fun `dayRange de hoy con carga de un dia previo NO se recorta`() {
        // La carga (3-ago 10:00) es anterior al día de hoy: el recorte no aplica
        // y el día de hoy sigue siendo el día natural completo.
        val range = RangeCalculator.dayRange(noonAug7Cdmx, Instant.parse("2026-08-03T16:00:00Z"))
        assertEquals("2026-08-07T06:00:00Z", range.startIso)
        assertEquals("2026-08-08T06:00:00Z", range.endExclusiveIso)
    }

    @Test
    fun `dayRange de hoy con carga de hoy arranca a la hora de la carga`() {
        // Carga hoy a las 09:00 CDMX: lo cobrado antes de esa hora es del ciclo
        // anterior, así que el día de HOY también abre a las 09:00.
        val range = RangeCalculator.dayRange(noonAug7Cdmx, Instant.parse("2026-08-07T15:00:00Z"))
        assertEquals("2026-08-07T15:00:00Z", range.startIso)
        assertEquals("2026-08-08T06:00:00Z", range.endExclusiveIso)
        assertFalse(contains(range, "2026-08-07T14:59:59Z"))
        assertTrue(contains(range, "2026-08-07T15:00:00Z"))
    }

    @Test
    fun `dayRange con carga null sigue siendo el dia de hoy completo`() {
        // Sin fecha de carga no hay contra qué recortar, pero "hoy" sigue siendo un día natural
        // bien definido: Día NO se degrada por no saber dónde abre la semana (lo que sí se
        // degrada es la SEMANA, ver la región cycleRange).
        val range = RangeCalculator.dayRange(noonAug7Cdmx, null)
        assertEquals("2026-08-07T06:00:00Z", range.startIso)
        assertEquals("2026-08-08T06:00:00Z", range.endExclusiveIso)
        assertEquals(1, range.days)
    }

    // region — dayRange(day) fuera del ciclo: vacío bien formado, jamás invertido

    @Test
    fun `dayRange de un dia ANTERIOR al ciclo devuelve un rango vacio, no invertido`() {
        // Carga 6-ago 19:33 CDMX; se pregunta por el 5-ago, que ya no pertenece
        // al ciclo. Sin la guarda saldría [6-ago 19:33, 6-ago 00:00) — invertido.
        val range = RangeCalculator.dayRange(
            FakeClock(Instant.parse("2026-08-13T18:00:00Z")),
            LocalDate.of(2026, 8, 5),
            Instant.parse("2026-08-07T01:33:00Z")
        )
        assertEquals(range.startIso, range.endExclusiveIso)
        assertEquals("2026-08-05T06:00:00Z", range.startIso)
        assertEquals(0, range.days)
        assertFalse(contains(range, "2026-08-05T06:00:00Z"))
        assertFalse(contains(range, "2026-08-05T18:00:00Z"))
        assertFalse(contains(range, "2026-08-06T05:59:59Z"))
    }

    @Test
    fun `dayRange de un dia POSTERIOR a hoy devuelve un rango vacio, no invertido`() {
        val range = RangeCalculator.dayRange(
            noonAug7Cdmx,
            LocalDate.of(2026, 8, 20),
            Instant.parse("2026-08-03T16:00:00Z")
        )
        assertEquals(range.startIso, range.endExclusiveIso)
        assertEquals("2026-08-20T06:00:00Z", range.startIso)
        assertEquals(0, range.days)
        assertFalse(contains(range, "2026-08-20T12:00:00Z"))
    }

    @Test
    fun `dayRange de MANANA tampoco se cuela por el borde exclusivo del ciclo`() {
        // Caso frontera: el 8-ago arranca exactamente en el fin exclusivo del
        // ciclo (06:00Z). start == end, no un rango de ancho cero "válido".
        val range = RangeCalculator.dayRange(
            noonAug7Cdmx,
            LocalDate.of(2026, 8, 8),
            Instant.parse("2026-08-03T16:00:00Z")
        )
        assertEquals(range.startIso, range.endExclusiveIso)
        assertFalse(contains(range, "2026-08-08T06:00:00Z"))
    }

    // region — cycleRange

    @Test
    fun `cycleRange abarca desde el INSTANTE de la carga hasta el fin exclusivo de hoy`() {
        // Carga: 3-ago 10:00 CDMX. El inicio conserva la hora; el conteo de días
        // sigue siendo por calendario (3-ago … 7-ago = 5).
        val range = cycle(noonAug7Cdmx, Instant.parse("2026-08-03T16:00:00Z"))
        assertEquals("2026-08-03T16:00:00Z", range.startIso)
        assertEquals("2026-08-08T06:00:00Z", range.endExclusiveIso)
        assertEquals(5, range.days)
    }

    @Test
    fun `cycleRange fin EXCLUSIVO - pago de hoy a las 23-59-59 SI cuenta`() {
        val range = cycle(noonAug7Cdmx, Instant.parse("2026-08-03T16:00:00Z"))
        assertTrue(contains(range, "2026-08-08T05:59:59Z"))
        assertFalse(contains(range, "2026-08-08T06:00:00Z"))
    }

    /**
     * TEST INVERTIDO A PROPÓSITO — antes: "cycleRange con carga null cae al día de hoy completo".
     *
     * Ese fallback ES el defecto D5 que se vio en campo: sin `FECHA_CARGA_INICIAL` (Firestore
     * ausente o caído) la ventana de la SEMANA se encogía a un día y el tablero mostraba $0.00
     * cobrado con la tabla de pagos llena. Que la causa fuera el rango y no una resincronización
     * quedó probado en el mismo tablero: el contador de VENTAS (103, sin filtro de fecha)
     * sobrevivía intacto mientras los pagos daban 0 — sólo un rango malo produce `0/103`.
     *
     * La regla nueva: sin fecha de carga NO hay semana, y eso se dice (null), no se disfraza.
     */
    @Test
    fun `cycleRange con carga null es null - NO cae al dia de hoy`() {
        assertNull(RangeCalculator.cycleRange(noonAug7Cdmx, null))
    }

    @Test
    fun `cycleRange con carga null no empieza ni hoy ni ahora`() {
        // Criterio de aceptación 1, dicho como propiedad y no como igualdad puntual: sea cual
        // sea el resultado, NO puede ser una ventana que arranque en el día de hoy ni en `now()`.
        val range = RangeCalculator.cycleRange(noonAug7Cdmx, null)
        val hoy = RangeCalculator.dayRange(noonAug7Cdmx, null)
        assertNull(range)
        assertNotEquals(hoy.startIso, range?.startIso)
        assertNotEquals("2026-08-07T18:00:00Z", range?.startIso)
    }

    @Test
    fun `cycleRange con carga hoy es un ciclo de un dia que abre a la hora de la carga`() {
        val range = cycle(noonAug7Cdmx, Instant.parse("2026-08-07T15:00:00Z"))
        assertEquals("2026-08-07T15:00:00Z", range.startIso)
        assertEquals("2026-08-08T06:00:00Z", range.endExclusiveIso)
        assertEquals(1, range.days)
    }

    @Test
    fun `cycleRange abre en el INSTANTE de la carga, no a medianoche de ese dia`() {
        // TEST INVERTIDO A PROPÓSITO (antes: "cycleRange usa la fecha de negocio
        // de la carga, no la del sistema", que fijaba el truncado a medianoche).
        // El truncado era el bug: abría el ciclo a las 00:00 del día de la carga
        // y volvía a contar lo cobrado esa mañana, que era del ciclo anterior.
        // Se invierte porque es un criterio de NEGOCIO revertido por el dueño del
        // producto, no un test roto por un refactor.
        //
        // Carga a las 23:30 CDMX del 2-ago (== 05:30Z del 3-ago). Lo que se sigue
        // conservando de aquel test: la fecha de NEGOCIO del inicio es el 2-ago
        // (no el 3, que es la fecha del instante en UTC), y por eso el conteo de
        // días no se mueve. Lo que cambia: el inicio ya no se trunca a las 00:00.
        val range = cycle(noonAug7Cdmx, Instant.parse("2026-08-03T05:30:00Z"))
        assertEquals("2026-08-03T05:30:00Z", range.startIso)
        assertEquals(LocalDate.of(2026, 8, 2), range.startDate)
        assertEquals(6, range.days)
        assertFalse(contains(range, "2026-08-03T05:29:59Z"))
    }

    /**
     * La guarda `minOf(carga, cycleEnd)` NO se tocó: sigue impidiendo un rango invertido, y este
     * test lo prueba donde vive — en `dayRange`/`cycleDays`, que son los que la consumen. Lo
     * único que cambia es aguas arriba: [RangeCalculator.cycleRange] ya no SIRVE ese vacío
     * permanente como si fuera una semana real, porque "$0 cobrado" y "tu fecha de semana no
     * sirve" no son la misma respuesta (ver el test de abajo).
     */
    @Test
    fun `carga en el FUTURO - la guarda sigue dando vacio bien formado, jamas invertido`() {
        // Reloj corrido o dato sucio en Firestore: la carga cae después del fin del ciclo.
        val cargaFutura = Instant.parse("2026-09-01T16:00:00Z")
        val dia = RangeCalculator.dayRange(noonAug7Cdmx, LocalDate.of(2026, 8, 7), cargaFutura)
        assertEquals(dia.startIso, dia.endExclusiveIso)
        assertEquals(0, dia.days)
        assertFalse(contains(dia, "2026-08-07T18:00:00Z"))
        assertTrue(RangeCalculator.cycleDays(noonAug7Cdmx, cargaFutura).isEmpty())
    }

    @Test
    fun `cycleRange con carga en el FUTURO es null, no un vacio que parezca semana`() {
        assertNull(RangeCalculator.cycleRange(noonAug7Cdmx, Instant.parse("2026-09-01T16:00:00Z")))
        assertNull(RangeCalculator.cycleInfo(noonAug7Cdmx, Instant.parse("2026-09-01T16:00:00Z")))
    }

    // region — cycleDays

    @Test
    fun `cycleDays lista de la carga a hoy inclusive, en orden ascendente`() {
        val days = RangeCalculator.cycleDays(noonAug7Cdmx, Instant.parse("2026-08-03T16:00:00Z"))
        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 4),
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 6),
                LocalDate.of(2026, 8, 7)
            ),
            days
        )
    }

    @Test
    fun `cycleDays con carga null devuelve solo hoy`() {
        assertEquals(
            listOf(LocalDate.of(2026, 8, 7)),
            RangeCalculator.cycleDays(noonAug7Cdmx, null)
        )
    }

    @Test
    fun `cycleDays con carga hoy devuelve solo hoy`() {
        assertEquals(
            listOf(LocalDate.of(2026, 8, 7)),
            RangeCalculator.cycleDays(noonAug7Cdmx, Instant.parse("2026-08-07T15:00:00Z"))
        )
    }

    @Test
    fun `cycleDays cruza el cambio de mes y de anio sin saltarse dias`() {
        // Carga 30-dic-2026 10:00 CDMX, reloj 2-ene-2027 12:00 CDMX.
        val clock = FakeClock(Instant.parse("2027-01-02T18:00:00Z"))
        val carga = Instant.parse("2026-12-30T16:00:00Z")
        val days = RangeCalculator.cycleDays(clock, carga)
        assertEquals(
            listOf(
                LocalDate.of(2026, 12, 30),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 2)
            ),
            days
        )
        assertEquals(days.size, cycle(clock, carga).days)
    }

    // region — incidente ruta 34 (13-ago-2026): el doble conteo de $3,150

    /** Carga real de la ruta 34: jueves 6-ago-2026 19:33 CDMX. */
    private val cargaRuta34 = Instant.parse("2026-08-07T01:33:00Z")

    /** Reloj del día en que se detectó: jueves 13-ago-2026 12:00 CDMX. */
    private val jueves13Cdmx = FakeClock(Instant.parse("2026-08-13T18:00:00Z"))

    @Test
    fun `ruta 34 - los pagos previos a la carga del mismo dia quedan FUERA del ciclo`() {
        val range = cycle(jueves13Cdmx, cargaRuta34)
        assertEquals("2026-08-07T01:33:00Z", range.startIso)
        assertEquals("2026-08-14T06:00:00Z", range.endExclusiveIso)
        // Pago de las 17:05 del 6-ago (== 23:05Z): del ciclo ANTERIOR, fuera.
        assertFalse(contains(range, "2026-08-06T23:05:00Z"))
        // Pago de las 08:27 del 6-ago (== 14:27Z): igual, fuera.
        assertFalse(contains(range, "2026-08-06T14:27:00Z"))
        // Pago de las 20:00 del 6-ago (== 02:00Z del 7): ya con la ruta cargada, dentro.
        assertTrue(contains(range, "2026-08-07T02:00:00Z"))
        // La carga misma es el primer instante incluido (borde inferior INCLUSIVO).
        assertTrue(contains(range, "2026-08-07T01:33:00Z"))
        assertFalse(contains(range, "2026-08-07T01:32:59Z"))
    }

    @Test
    fun `ruta 34 - el ciclo sigue midiendo 8 dias y la etiqueta no cambia de forma`() {
        // Decisión EXPLÍCITA del dueño del producto: el ciclo toca ocho días
        // naturales (jue 6 … jue 13) aunque el primero sea parcial. Que el día de
        // la carga pueda verse en $0 es transparencia buscada, no un bug — no
        // "arreglar" esto recorriendo el inicio al día siguiente.
        val info = info(jueves13Cdmx, cargaRuta34)
        assertEquals(8, info.days)
        assertEquals("semana · jue 6 – jue 13 ago · 8 días", info.cycleLabel)
        assertEquals("jueves 13 ago 2026", info.dayLabel)
        assertEquals(8, cycle(jueves13Cdmx, cargaRuta34).days)
    }

    @Test
    fun `ruta 34 - el dia de la carga arranca a las 19-33, los demas a medianoche`() {
        val diaCarga = RangeCalculator.dayRange(jueves13Cdmx, LocalDate.of(2026, 8, 6), cargaRuta34)
        assertEquals("2026-08-07T01:33:00Z", diaCarga.startIso)
        assertEquals("2026-08-07T06:00:00Z", diaCarga.endExclusiveIso)
        assertFalse(contains(diaCarga, "2026-08-06T23:05:00Z"))
        assertTrue(contains(diaCarga, "2026-08-07T02:00:00Z"))

        val otroDia = RangeCalculator.dayRange(jueves13Cdmx, LocalDate.of(2026, 8, 9), cargaRuta34)
        assertEquals("2026-08-09T06:00:00Z", otroDia.startIso)
        assertEquals("2026-08-10T06:00:00Z", otroDia.endExclusiveIso)

        val hoy = RangeCalculator.dayRange(jueves13Cdmx, cargaRuta34)
        assertEquals("2026-08-13T06:00:00Z", hoy.startIso)
        assertEquals("2026-08-14T06:00:00Z", hoy.endExclusiveIso)
    }

    @Test
    fun `ruta 34 - cycleDays lista jue 6 a jue 13 sin dias previos`() {
        val days = RangeCalculator.cycleDays(jueves13Cdmx, cargaRuta34)
        assertEquals(8, days.size)
        assertEquals(LocalDate.of(2026, 8, 6), days.first())
        assertEquals(LocalDate.of(2026, 8, 13), days.last())
        assertEquals(days.sorted(), days)
        assertEquals(days.distinct(), days)
        assertFalse(days.contains(LocalDate.of(2026, 8, 5)))
    }

    // region — propiedad: la suma de los días cuadra con la semana

    /**
     * Invariante que amarra el fix: para todo día `d` de `cycleDays`, `dayRange(d)`
     * está CONTENIDO en `cycleRange`, los días embonan uno tras otro sin huecos ni
     * traslapes, y la unión cubre el ciclo completo. Si esto se cumple, la suma de
     * los totales por día es exactamente el total del ciclo — que es justo lo que
     * NO cuadraba con el inicio truncado a medianoche.
     */
    private fun assertDiasCubrenElCicloSinHuecos(clock: FakeClock, carga: Instant?) {
        val cycle = cycle(clock, carga)
        val days = RangeCalculator.cycleDays(clock, carga)
        assertTrue("cycleDays no debe venir vacío para un ciclo no vacío", days.isNotEmpty())
        assertEquals("cycleDays debe tener un día por día del ciclo", cycle.days, days.size)

        val cycleStart = Instant.parse(cycle.startIso)
        val cycleEnd = Instant.parse(cycle.endExclusiveIso)
        var cursor = cycle.startIso
        days.forEach { day ->
            val range = RangeCalculator.dayRange(clock, day, carga)
            assertEquals("hueco o traslape antes de $day", cursor, range.startIso)
            assertTrue(
                "$day arranca antes del ciclo",
                !Instant.parse(range.startIso).isBefore(cycleStart)
            )
            assertTrue(
                "$day termina después del ciclo",
                !Instant.parse(range.endExclusiveIso).isAfter(cycleEnd)
            )
            assertTrue("$day no puede ser un rango vacío", range.startIso < range.endExclusiveIso)
            cursor = range.endExclusiveIso
        }
        assertEquals("la unión no llega al fin del ciclo", cycle.endExclusiveIso, cursor)
    }

    @Test
    fun `los dayRange del ciclo cubren el cycleRange sin huecos ni traslapes - ruta 34`() {
        assertDiasCubrenElCicloSinHuecos(jueves13Cdmx, cargaRuta34)
    }

    // El caso "carga null" YA NO aplica a esta invariante: sin fecha de carga no hay ciclo que
    // cubrir (`cycleRange` devuelve null). Lo que sustituye a aquel test es la pareja
    // `cycleRange con carga null es null` / `cycleDays con carga null devuelve solo hoy`, que
    // fija ambas mitades del contrato nuevo.

    @Test
    fun `los dayRange del ciclo cubren el cycleRange sin huecos ni traslapes - carga hoy`() {
        assertDiasCubrenElCicloSinHuecos(noonAug7Cdmx, Instant.parse("2026-08-07T15:00:00Z"))
    }

    @Test
    fun `los dayRange del ciclo cubren el cycleRange sin huecos ni traslapes - cruce de anio`() {
        assertDiasCubrenElCicloSinHuecos(
            FakeClock(Instant.parse("2027-01-02T18:00:00Z")),
            Instant.parse("2026-12-30T16:00:00Z")
        )
    }

    @Test
    fun `los dayRange del ciclo cubren el cycleRange sin huecos ni traslapes - transicion DST 2021`() {
        // El día de la transición dura 25 horas: si el recorte por día usara un
        // offset fijo en vez del vigente, aquí aparecería un hueco de una hora.
        assertDiasCubrenElCicloSinHuecos(
            FakeClock(Instant.parse("2021-11-01T18:00:00Z")),
            Instant.parse("2021-10-30T15:00:00Z")
        )
    }

    // region — cycleInfo (etiquetas)

    @Test
    fun `cycleInfo produce dias y etiquetas es-MX`() {
        val info = info(noonAug7Cdmx, Instant.parse("2026-08-03T16:00:00Z"))
        assertEquals(5, info.days)
        assertEquals("semana · lun 3 – vie 7 ago · 5 días", info.cycleLabel)
        assertEquals("viernes 7 ago 2026", info.dayLabel)
    }

    @Test
    fun `cycleInfo ciclo de un dia usa singular dia`() {
        val info = info(noonAug7Cdmx, Instant.parse("2026-08-07T15:00:00Z"))
        assertEquals(1, info.days)
        assertEquals("semana · vie 7 ago · 1 día", info.cycleLabel)
        assertEquals("viernes 7 ago 2026", info.dayLabel)
    }

    @Test
    fun `cycleInfo con carga null es null - no se inventan etiquetas de semana`() {
        assertNull(RangeCalculator.cycleInfo(noonAug7Cdmx, null))
    }

    // region — offset UTC histórico a través de una transición DST (2021)

    @Test
    fun `startOfDay respeta el offset DST vigente a cada lado de la transicion 2021`() {
        // México observó DST NACIONAL hasta oct-2022; desde entonces no hay
        // transición que ejercer para ciclos actuales. Este test usa fechas de
        // 2021 (cuando SÍ había DST) para verificar lo único que un cambio de
        // offset podría romper: que los bordes se serialicen con el offset UTC
        // vigente ESE día, no uno fijo. El 30-oct-2021 aún estaba en DST
        // (-05:00 → 05:00Z); tras el cambio del 31-oct, el 2-nov ya estaba en
        // horario estándar (-06:00 → 06:00Z). El conteo de días (calendario) se
        // mantiene en 3 pese al offset distinto en cada extremo.
        //
        // El inicio del ciclo ya NO se trunca a medianoche (es el instante de la
        // carga), así que el offset se ejerce ahora en el fin exclusivo y en los
        // `dayRange` intermedios — el 31-oct es el día de la transición y dura 25
        // horas: de 05:00Z (todavía DST) a 06:00Z del 1-nov (ya estándar).
        val clock = FakeClock(Instant.parse("2021-11-01T18:00:00Z")) // 1-nov 12:00 CDMX
        val carga = Instant.parse("2021-10-30T15:00:00Z") // 30-oct 10:00 CDMX (DST)
        val range = cycle(clock, carga)
        assertEquals("2021-10-30T15:00:00Z", range.startIso) // instante de la carga, en DST
        assertEquals("2021-11-02T06:00:00Z", range.endExclusiveIso) // borde en estándar (-06:00)
        assertEquals(3, range.days)

        val diaDeLaTransicion = RangeCalculator.dayRange(clock, LocalDate.of(2021, 10, 31), carga)
        assertEquals("2021-10-31T05:00:00Z", diaDeLaTransicion.startIso) // medianoche en DST
        assertEquals("2021-11-01T06:00:00Z", diaDeLaTransicion.endExclusiveIso) // 25 horas después
        assertEquals(1, diaDeLaTransicion.days)
    }
}
