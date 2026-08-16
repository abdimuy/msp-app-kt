package com.example.msp_app.feature.collectionreport.ui

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.feature.collectionreport.domain.DeltaChip
import com.example.msp_app.feature.collectionreport.domain.DeltaDirection
import com.example.msp_app.feature.collectionreport.domain.Insight
import com.example.msp_app.feature.collectionreport.domain.Timeline
import com.example.msp_app.feature.collectionreport.domain.TimelineBucket
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Datos EXACTOS del mockup (`docs/design/reporte-cobranza-mockup.html`, task-6-brief.md
 * "Datos del mockup para goldens") — compartidos entre el compose-test de comportamiento
 * ([CollectionReportContentTest]) y los goldens Roborazzi
 * (`screenshot/CollectionReportTopSectionScreenshotTest`), para que ambos ejerzan la MISMA
 * fixture visual/numérica en vez de divergir por accidente.
 */
internal object MockupFixtures {

    private const val DIA_START_HOUR = 8
    private val DIA_HOUR_VALUES = listOf(30, 100, 72, 58, 44, 30, 22, 26, 18)
    private const val DIA_HIGHLIGHT_INDEX = 1

    private val SEMANA_DAY_LABELS = listOf("lun", "mar", "mié", "jue", "vie")
    private val SEMANA_DAY_VALUES = listOf(74, 87, 100, 89, 64)

    const val COBRADOR = "Gabriel Roque"

    // Alias cortos de método para que las filas de [dayPaymentsSemana] quepan en una línea
    // (ktlint max-line-length) sin repetir el enum completo por fila.
    private val EFEC = PaymentMethod.EFECTIVO
    private val TRANSF = PaymentMethod.TRANSFERENCIA

    fun heroDia(): HeroUi = HeroUi(
        overline = "Cobrado · vie 7 ago",
        delta = DeltaChip("▲ 12% vs ayer", DeltaDirection.UP),
        monto = money("18300"),
        insight = Insight.Daily(count = 32, progressPct = 0, projection = money("19800")),
        sparkline = Timeline(
            buckets = DIA_HOUR_VALUES.mapIndexed { index, value ->
                val hour = DIA_START_HOUR + index
                TimelineBucket(
                    label = "${hour}h",
                    total = money(value.toString()),
                    count = 0,
                    hour = hour
                )
            },
            highlightIndex = DIA_HIGHLIGHT_INDEX
        )
        // "Meta de la semana" (porcentajeCobro/porcentajeCuentas) solo se calcula en SEMANA
        // (ver KDoc de HeroUi) — Día se queda en los defaults 0f/0.
    )

    // "Meta de la semana" del mockup: cobro (ponderado) 91% supera la meta de 60%; cobertura
    // (cuentas) 78% = 39 de 50 clientes.
    const val PORCENTAJE_COBRO_SEMANA = 91f
    const val PORCENTAJE_CUENTAS_SEMANA = 78f
    const val CLIENTES_PAGARON_SEMANA = 39
    const val CLIENTES_TOTAL_SEMANA = 50

    fun heroSemana(): HeroUi = HeroUi(
        overline = "Cobrado · semana actual",
        delta = DeltaChip("▲ 6% vs semana", DeltaDirection.UP),
        monto = money("118400"),
        insight = Insight.Weekly(
            count = 214,
            progressPct = PORCENTAJE_COBRO_SEMANA.toInt(),
            cycleDay = 5,
            cycleDays = 5
        ),
        sparkline = Timeline(
            buckets = SEMANA_DAY_VALUES.mapIndexed { index, value ->
                TimelineBucket(
                    label = SEMANA_DAY_LABELS[index],
                    total = money(value.toString()),
                    count = 0
                )
            },
            highlightIndex = SEMANA_DAY_VALUES.lastIndex
        ),
        porcentajeCobro = PORCENTAJE_COBRO_SEMANA,
        porcentajeCuentas = PORCENTAJE_CUENTAS_SEMANA,
        clientesPagaron = CLIENTES_PAGARON_SEMANA,
        clientesTotal = CLIENTES_TOTAL_SEMANA
    )

    /**
     * Filas de pago Día (mockup `PAYS`) — nombres/montos/método del mockup, enriquecidas con
     * folio comercial + saldo restante de la venta (fix de dispositivo: la fila mostraba el
     * `DOCTO_CC_ACR_ID` crudo y sin saldo). Folios/saldos representativos (mexicanos, peso
     * entero) para el golden de la fila enriquecida.
     */
    fun paymentsDia(): List<PaymentRowUi> = listOf(
        paymentRow("p-ml", "María López Hernández", "Muebles Bahía", "09:12", "1200", EFEC)
            .copy(folio = "A-10482", saldo = money("5400")),
        paymentRow("p-jp", "Juan Pérez Ramírez", "Recámara Diana", "09:40", "850", TRANSF)
            .copy(folio = "A-10517", saldo = money("3200")),
        paymentRow("p-rm", "Rosa Martínez Cruz", "Sala Toscana", "10:05", "1500", EFEC)
            .copy(folio = "A-10233", saldo = money("8750")),
        paymentRow("p-ps", "Pedro Sánchez Ortiz", "Comedor Roble", "11:20", "2000", EFEC)
            .copy(folio = "A-10604", saldo = money("12500"))
    )

    // Nombres mexicanos para la lista larga (fix de dispositivo: verificar que TODOS los pagos
    // son alcanzables al hacer scroll). Se ciclan por índice; suficiente para el golden.
    private val MANY_CLIENTES = listOf(
        "María López Hernández", "Juan Pérez Ramírez", "Rosa Martínez Cruz",
        "Pedro Sánchez Ortiz", "Lucía Fernández Mora", "Roberto Aguilar Díaz",
        "Verónica Castillo Ramos", "Héctor Domínguez León", "Patricia Núñez Vega",
        "Alejandro Reyes Ortiz", "Gabriela Mendoza Ríos", "Sergio Vargas Peña"
    )

    /**
     * Lista LARGA de pagos Día ([count] filas, 23 por defecto) — golden de scroll (fix de
     * dispositivo): con muchos pagos la lista debe poder recorrerse hasta el último. Datos
     * deterministas (nombre ciclado, folio/hora/monto/saldo por índice), método alternado y
     * una de cada cinco filas sin sincronizar para ejercer el chip "Por subir".
     */
    fun manyPaymentsDia(count: Int = 23): List<PaymentRowUi> = (1..count).map { i ->
        val hora = "%02d:%02d".format(7 + (i % 12), (i * 7) % 60)
        val monto = (400 + (i * 173) % 2200).toString()
        val saldo = (600 + (i * 311) % 9000).toString()
        paymentRow(
            id = "many-$i",
            cliente = MANY_CLIENTES[i % MANY_CLIENTES.size],
            ventaLabel = (70000 + i).toString(),
            hora = hora,
            monto = monto,
            method = if (i % 3 == 0) TRANSF else EFEC
        ).copy(folio = "A-${10000 + i}", saldo = money(saldo), synced = i % 5 != 0)
    }

    /**
     * Condonaciones (mockup `SHEETS.condon` filas) — cliente/monto EXACTOS del mockup, pero
     * `motivo = ""` (fix round 1, Important 2): el mockup muestra motivos de ejemplo
     * ("saldo mínimo · autorizado", etc.) que producción NO puede generar hoy —
     * `RoomPaymentsAdapter.toForgiveness` siempre emite `motivo = ""` (auditado: sin fuente
     * real en v27 ni en el backend Go, ver KDoc de `Forgiveness.motivo`). Esta fixture usa la
     * realidad de producción (vacío), no la muestra bonita del mockup — así el golden del
     * sheet Condonado no miente sobre lo que un cobrador ve de verdad.
     */
    fun condonadoRows(): List<ForgivenessRowUi> = listOf(
        ForgivenessRowUi("Ana Ruiz", "", money("600")),
        ForgivenessRowUi("Luis Gómez", "", money("500")),
        ForgivenessRowUi("María Tovar", "", money("300"))
    )

    /**
     * Visitas (mockup `SHEETS.visitas` filas) — cliente/nota EXACTOS del mockup, `tipo`
     * enriquecido con valores REALES del catálogo de captura (`Constants` en `:app`, ver KDoc
     * de `CollectionVisit.tipo`) — texto libre en español, no un código, elegido acorde a cada
     * nota. `visitedAt` es una hora de tarde representativa (después de la ronda de cobro)
     * para que el prefijo hora/fecha del ticket (Task 2) tenga un dato realista en ambos
     * periodos.
     */
    fun visitRows(): List<VisitRowUi> = listOf(
        VisitRowUi(
            "Carlos Vega",
            "No estaba — dejé recado",
            tipo = "No se encontraba",
            visitedAt = visitedAt("14:15")
        ),
        VisitRowUi(
            "Sofía Luna",
            "Promesa de pago mañana",
            tipo = "Pidió que regrese otro día",
            visitedAt = visitedAt("16:40")
        ),
        VisitRowUi(
            "Diego Mora",
            "Cliente inconforme",
            tipo = "Fue grosero o agresivo",
            visitedAt = visitedAt("17:05")
        )
    )

    /**
     * Pagos INDIVIDUALES por día del ciclo (Semana), alineados 1:1 por índice con [daysSemana]
     * — el sheet `DIA_CICLO` lista los pagos del día tocado (índice 1 = "mar 4 ago", el que usa
     * el golden). Fixture representativa (2-3 pagos por día, no los 39/46/… reales): alcanza
     * para el golden/comportamiento; en producción `ReportAggregator.paymentsByDay` reparte
     * TODOS los pagos del ciclo sin tope. Nombres mexicanos, montos en peso entero.
     */
    fun dayPaymentsSemana(): List<List<PaymentRowUi>> = listOf(
        listOf(
            paymentRow("d0-1", "Lucía Fernández Mora", "Sala Milán", "09:05", "1300", EFEC),
            paymentRow("d0-2", "Roberto Aguilar Díaz", "Recámara Sol", "10:40", "900", TRANSF)
        ),
        listOf(
            paymentRow("d1-1", "Verónica Castillo Ramos", "Comedor Nogal", "08:50", "1500", EFEC),
            paymentRow("d1-2", "Héctor Domínguez León", "Colchón King", "11:15", "1100", EFEC),
            paymentRow("d1-3", "Patricia Núñez Vega", "Sala Verona", "13:30", "950", TRANSF)
        ),
        listOf(
            paymentRow("d2-1", "Alejandro Reyes Ortiz", "Ropero Cedro", "09:20", "1750", EFEC)
        ),
        listOf(
            paymentRow("d3-1", "Gabriela Mendoza Ríos", "Sala Toscana", "10:10", "1200", EFEC),
            paymentRow("d3-2", "Sergio Vargas Peña", "Comedor Roble", "12:00", "2000", TRANSF)
        ),
        listOf(
            paymentRow("d4-1", "María López Hernández", "Muebles Bahía", "09:12", "1200", EFEC),
            paymentRow("d4-2", "Juan Pérez Ramírez", "Recámara Diana", "09:40", "850", TRANSF)
        )
    )

    /**
     * Resumen por día Semana (mockup `DAYS`) — etiquetas/montos/conteos/iniciales EXACTOS.
     * Las fechas son las del propio mockup (lun 3 – vie 7 ago 2026): sirven de LLAVE de la lista
     * perezosa del detalle, no se pintan.
     */
    fun daysSemana(): List<DayRowUi> = listOf(
        dayRow("2026-08-03", "lun 3 ago", "21300", 39, "L3", isToday = false),
        dayRow("2026-08-04", "mar 4 ago", "24800", 46, "M4", isToday = false),
        dayRow("2026-08-05", "mié 5 ago", "28600", 51, "M5", isToday = false),
        dayRow("2026-08-06", "jue 6 ago", "25400", 46, "J6", isToday = false),
        dayRow("2026-08-07", "vie 7 ago (hoy)", "18300", 32, "V7", isToday = true)
    )

    @Suppress("LongParameterList") // 1:1 con los campos de `DayRowUi`; no hay nada que agrupar.
    private fun dayRow(
        date: String,
        label: String,
        amount: String,
        count: Int,
        initials: String,
        isToday: Boolean
    ): DayRowUi = DayRowUi(
        date = LocalDate.parse(date),
        label = label,
        amount = money(amount),
        count = count,
        initials = initials,
        isToday = isToday
    )

    // ─── ciclo REAL de la ruta 34 (medido en producción el 13-ago-2026) ─────────────────
    //
    // El cobrador cargó ruta el jueves 6 de agosto a las 19:33 hora de negocio (CDMX, -06:00) y
    // hoy es jueves 13: 8 días de ciclo, $43,850 en total. Los dos primeros van en CERO — el 6
    // porque cargó de noche y ya no cobró, el 7 porque no cobró — y así deben verse: presentes y
    // atenuados, nunca escondidos (decisión de transparencia del dueño). Es la fixture de
    // referencia de la tira de días: si alguien "arregla" el ciclo escondiendo los ceros o
    // recorriendo el inicio al día siguiente, esta fixture y sus tests truenan a propósito.

    /** `2026-08-07T01:33:00Z` == jueves 6 de agosto, 19:33 en `America/Mexico_City`. */
    val CARGA_RUTA_34: Instant = AppTime.parseWireFormat("2026-08-07T01:33:00Z")

    /** Hoy en el escenario: jueves 13 de agosto de 2026, media mañana. */
    val AHORA_RUTA_34: Instant = AppTime.parseWireFormat("2026-08-13T16:00:00Z")

    val HOY_RUTA_34: LocalDate = LocalDate.of(2026, 8, 13)

    /** jue 6 … jue 13 inclusive — los 8 días elegibles del ciclo. */
    val CICLO_RUTA_34: List<LocalDate> = (6..13).map { LocalDate.of(2026, 8, it) }

    /** Cobrado por día del ciclo. Suma exacta: $43,850. */
    val TOTALES_RUTA_34: Map<LocalDate, Money> = mapOf(
        LocalDate.of(2026, 8, 6) to money("0"),
        LocalDate.of(2026, 8, 7) to money("0"),
        LocalDate.of(2026, 8, 8) to money("5100"),
        LocalDate.of(2026, 8, 9) to money("15350"),
        LocalDate.of(2026, 8, 10) to money("8800"),
        LocalDate.of(2026, 8, 11) to money("5350"),
        LocalDate.of(2026, 8, 12) to money("3450"),
        LocalDate.of(2026, 8, 13) to money("5800")
    )

    /** Chips de la tira para [CICLO_RUTA_34], con [seleccionado] marcado y hoy = [HOY_RUTA_34]. */
    fun cicloRuta34(seleccionado: LocalDate = HOY_RUTA_34): List<DayChipUi> =
        CICLO_RUTA_34.map { day ->
            DayChipUi(
                date = day,
                isToday = day == HOY_RUTA_34,
                isSelected = day == seleccionado,
                hasCollections = (TOTALES_RUTA_34[day] ?: Money.ZERO) > Money.ZERO
            )
        }

    /**
     * Tablero de Día CON tira de días (ruta 34). [seleccionado] manda sobre el rótulo del rango,
     * el monto del hero y la lista de pagos — igual que en producción. El día de la carga
     * (jue 6) sale en cero, con su nota de arranque.
     */
    fun stateDiaConCiclo(
        seleccionado: LocalDate = HOY_RUTA_34,
        masked: Boolean = false
    ): CollectionReportUiState {
        val esDiaDeCarga = seleccionado == CICLO_RUTA_34.first()
        val total = TOTALES_RUTA_34[seleccionado] ?: Money.ZERO
        val pagos = if (total > Money.ZERO) paymentsDia() else emptyList()
        return stateDia(masked = masked).copy(
            rangeLabel = seleccionado.format(
                DateTimeFormatter.ofPattern("EEEE d MMM yyyy", BUSINESS_LOCALE)
            ),
            hero = heroDia().copy(monto = total),
            detail = DetailUi.Payments(pagos),
            cycleDays = cicloRuta34(seleccionado),
            selectedDay = seleccionado,
            selectedDayNote = if (esDiaDeCarga) NOTA_CARGA_RUTA_34 else ""
        )
    }

    /** La misma línea que produce `CollectionReportDayStripBuilder.startNote` para la ruta 34. */
    val NOTA_CARGA_RUTA_34: String = "desde las ${AppTime.formatForDisplay(
        CARGA_RUTA_34,
        "h:mm a"
    )} · inicio de semana"

    fun stateDia(masked: Boolean = false, error: String? = null): CollectionReportUiState =
        CollectionReportUiState(
            period = ReportPeriod.DIA,
            contentPeriod = ReportPeriod.DIA,
            loading = false,
            error = error,
            cobrador = COBRADOR,
            rangeLabel = "viernes 7 ago 2026",
            pendingCount = 3,
            masked = masked,
            hero = heroDia(),
            efectivo = TileUi("Efectivo", money("12100"), 22),
            transferencia = TileUi("Transferencia", money("6200"), 10),
            condonado = ChipUi("Condonado", amount = money("1400")),
            visitas = ChipUi("Visitas", count = 14),
            detail = DetailUi.Payments(paymentsDia()),
            condonadoRows = condonadoRows(),
            visitRows = visitRows()
        )

    fun stateSemana(masked: Boolean = false): CollectionReportUiState = CollectionReportUiState(
        period = ReportPeriod.SEMANA,
        contentPeriod = ReportPeriod.SEMANA,
        loading = false,
        cobrador = COBRADOR,
        rangeLabel = "semana · lun 3 – vie 7 ago · 5 días",
        pendingCount = 3,
        masked = masked,
        hero = heroSemana(),
        efectivo = TileUi("Efectivo", money("79900"), 146),
        transferencia = TileUi("Transferencia", money("38500"), 68),
        condonado = ChipUi("Condonado", amount = money("9200")),
        visitas = ChipUi("Visitas", count = 71),
        detail = DetailUi.Days(daysSemana()),
        dayPayments = dayPaymentsSemana(),
        condonadoRows = condonadoRows(),
        visitRows = visitRows()
    )

    // Siempre `synced = true` — los tests que necesitan una fila "por subir" parten de aquí
    // con `.copy(synced = false)` (ver [paymentsDia] + los tests de `DetailList`), en vez de
    // sumar un séptimo parámetro a este helper (LongParameterList).
    private fun paymentRow(
        id: String,
        cliente: String,
        ventaLabel: String,
        hora: String,
        monto: String,
        method: PaymentMethod
    ): PaymentRowUi = PaymentRowUi(
        id = id,
        cliente = cliente,
        ventaLabel = ventaLabel,
        paidAt = paidAt(hora),
        amount = money(monto),
        method = method,
        synced = true
    )

    private fun paidAt(hora: String): Instant = AppTime.parseWireFormat("2026-08-07T$hora:00")

    private fun visitedAt(hora: String): Instant = AppTime.parseWireFormat("2026-08-07T$hora:00")

    private fun money(value: String) = Money.of(BigDecimal(value))
}
