package com.example.msp_app.data.local.datasource.guarantee

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.entities.GuaranteeEntity
import com.example.msp_app.core.database.entities.GuaranteeEventEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Task 11 (fechas/AppTime migration) — bug #4: `GuaranteesLocalDataSource
 * .updateGuaranteeStatusAndInsertEvent` used to write `FECHA_EVENTO` via
 * `LocalDateTime.now().format(ISO_LOCAL_DATE_TIME)` — an offset-less string
 * (`"2026-08-08T14:30:00"`), breaking the wire format contract (RFC3339 `Z`-UTC) that
 * `GuaranteesApi.saveGuaranteeEvent` sends to the backend. Fixed to
 * `AppTime.toWireFormat(clock.now())`.
 *
 * Room v27 schema is unchanged (`FECHA_EVENTO` stays TEXT) — only the string content
 * written going forward changes. Pre-existing offset-less rows must remain readable via
 * `AppTime.parseWireFormat`'s legacy branch (interpreted in [com.example.msp_app.core.common.time.BUSINESS_ZONE]).
 */
class GuaranteesLocalDataSourceFechaEventoTest : RoomTestBase() {

    private lateinit var dataSource: GuaranteesLocalDataSource
    private val clock = FakeClock(Instant.parse("2026-08-08T14:30:00Z"))

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dataSource = GuaranteesLocalDataSource(context, clock)
    }

    private suspend fun seedGuarantee(externalId: String = "garantia-001"): Int {
        dataSource.insertGuarantee(
            GuaranteeEntity(
                ID = 0,
                EXTERNAL_ID = externalId,
                DOCTO_CC_ID = null,
                ESTADO = "notificado",
                DESCRIPCION_FALLA = "no enciende",
                OBSERVACIONES = null,
                UPLOADED = 0,
                FECHA_SOLICITUD = "2026-08-08T10:00:00Z"
            )
        )
        return dataSource.getGuaranteeByExternalId(externalId)!!.ID
    }

    // region — 1. New writes: FECHA_EVENTO is Z-UTC wire format, from the injected clock

    @Test
    fun `updateGuaranteeStatusAndInsertEvent writes FECHA_EVENTO as Z-UTC wire format`() = runTest {
        val id = seedGuarantee()

        dataSource.updateGuaranteeStatusAndInsertEvent(
            guaranteeId = id,
            externalId = "garantia-001",
            newEstado = "en_proceso",
            tipoEvento = "CAMBIO_ESTADO"
        )

        val events = dataSource.getEventsByGuaranteeId("garantia-001")
        assertEquals(1, events.size)
        val fechaEvento = events.single().FECHA_EVENTO

        assertEquals(AppTime.toWireFormat(clock.now()), fechaEvento)
        assertEquals("2026-08-08T14:30:00Z", fechaEvento)
        assertTrue("must end with Z (RFC3339 UTC)", fechaEvento.endsWith("Z"))
    }

    @Test
    fun `updateGuaranteeStatusAndInsertEvent is NOT the old offset-less shape`() = runTest {
        val id = seedGuarantee()

        dataSource.updateGuaranteeStatusAndInsertEvent(
            guaranteeId = id,
            externalId = "garantia-001",
            newEstado = "en_proceso",
            tipoEvento = "CAMBIO_ESTADO"
        )

        val fechaEvento = dataSource.getEventsByGuaranteeId("garantia-001").single().FECHA_EVENTO

        // Old bug shape for the SAME instant: "2026-08-08T14:30:00" (no Z). Also confirm
        // AppTime.parseWireFormat's strict Instant.parse branch accepts the new value
        // directly (no legacy fallback needed for freshly-written rows).
        assertTrue(!fechaEvento.contains("+") || fechaEvento.endsWith("Z"))
        assertEquals("2026-08-08T14:30:00Z", fechaEvento)
        assertEquals(Instant.parse(fechaEvento), AppTime.parseWireFormat(fechaEvento))
    }

    @Test
    fun `FECHA_EVENTO reflects clock advancement across two events`() = runTest {
        val id = seedGuarantee()

        dataSource.updateGuaranteeStatusAndInsertEvent(
            guaranteeId = id,
            externalId = "garantia-001",
            newEstado = "en_proceso",
            tipoEvento = "CAMBIO_ESTADO"
        )
        clock.advanceHours(2)
        dataSource.updateGuaranteeStatusAndInsertEvent(
            guaranteeId = id,
            externalId = "garantia-001",
            newEstado = "resuelto",
            tipoEvento = "CAMBIO_ESTADO"
        )

        val events = dataSource.getEventsByGuaranteeId("garantia-001")
        val fechas = events.map { it.FECHA_EVENTO }.sorted()
        assertEquals(listOf("2026-08-08T14:30:00Z", "2026-08-08T16:30:00Z"), fechas)
    }

    // endregion

    // region — 2. Backward-compat: pre-existing offset-less rows stay readable

    @Test
    fun `legacy offset-less FECHA_EVENTO rows remain readable via parseWireFormat`() = runTest {
        // Simulate a row written by the OLD buggy code path directly (bypassing the fixed
        // write path) — this is exactly what already sits in production Room databases.
        val legacyEvent = GuaranteeEventEntity(
            ID = "evt-legacy-1",
            GARANTIA_ID = "garantia-legacy",
            TIPO_EVENTO = "SOLICITUD",
            FECHA_EVENTO = "2026-08-08T14:30:00",
            COMENTARIO = null,
            ENVIADO = 0
        )
        dataSource.insertGuaranteeEvent(legacyEvent)

        val readBack = dataSource.getEventsByGuaranteeId("garantia-legacy").single()
        assertEquals("2026-08-08T14:30:00", readBack.FECHA_EVENTO)

        // Must not throw, and must resolve via the legacy (business-zone) branch.
        val parsed = AppTime.parseWireFormat(readBack.FECHA_EVENTO)
        val expected = readBack.FECHA_EVENTO.let {
            java.time.LocalDateTime.parse(it)
                .atZone(com.example.msp_app.core.common.time.BUSINESS_ZONE)
                .toInstant()
        }
        assertEquals(expected, parsed)
    }

    // endregion
}
