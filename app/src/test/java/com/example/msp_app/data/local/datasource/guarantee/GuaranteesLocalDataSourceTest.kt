package com.example.msp_app.data.local.datasource.guarantee

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.entities.GuaranteeEntity
import com.example.msp_app.core.database.entities.GuaranteeEventEntity
import com.example.msp_app.core.database.entities.GuaranteeImageEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite exhaustiva de [GuaranteesLocalDataSource] construido por el
 * **constructor de DAO inyectado** (la forma Hilt), cubriendo garantias,
 * imagenes y eventos. Complementa
 * [GuaranteesLocalDataSourceFechaEventoTest] (que ya cubre a fondo el
 * contrato de fecha de `FECHA_EVENTO`) con el resto del ciclo de vida y con
 * la equivalencia inyectado <-> puente `context`, verificando en particular
 * que el puente PRESERVA el `AppClock` inyectado desde Task 11 (no lo
 * reemplaza por `AppClock.System`).
 */
class GuaranteesLocalDataSourceTest : RoomTestBase() {

    private lateinit var store: GuaranteesLocalDataSource

    @Before
    fun setUpStore() {
        store = GuaranteesLocalDataSource(db.guaranteeDao())
    }

    private fun guarantee(
        externalId: String = "garantia-001",
        estado: String = "notificado",
        doctoCcId: Int? = null,
        uploaded: Int = 0,
        fechaSolicitud: String = "2026-08-08T10:00:00Z"
    ) = GuaranteeEntity(
        ID = 0,
        EXTERNAL_ID = externalId,
        DOCTO_CC_ID = doctoCcId,
        ESTADO = estado,
        DESCRIPCION_FALLA = "no enciende",
        OBSERVACIONES = null,
        UPLOADED = uploaded,
        FECHA_SOLICITUD = fechaSolicitud
    )

    // ─── garantias ─────────────────────────────────────────────────────────

    @Test
    fun insertGuarantee_and_getGuaranteeByExternalId_roundTrips() = runTest {
        store.insertGuarantee(guarantee(externalId = "g-1"))

        val got = store.getGuaranteeByExternalId("g-1")!!

        assertEquals("notificado", got.ESTADO)
    }

    @Test
    fun getGuaranteeByExternalId_nullWhenAbsent() = runTest {
        assertNull(store.getGuaranteeByExternalId("no-existe"))
    }

    @Test
    fun getStandaloneGuarantees_excludesLinkedToSale() = runTest {
        store.insertGuarantee(guarantee(externalId = "standalone", doctoCcId = null))
        store.insertGuarantee(guarantee(externalId = "linked", doctoCcId = 5000))

        val result = store.getStandaloneGuarantees()

        assertEquals(listOf("standalone"), result.map { it.EXTERNAL_ID })
    }

    @Test
    fun getGuaranteeByDoctoCcId_findsLinkedGuarantee() = runTest {
        store.insertGuarantee(guarantee(externalId = "linked", doctoCcId = 5000))

        assertEquals("linked", store.getGuaranteeByDoctoCcId(5000)!!.EXTERNAL_ID)
    }

    @Test
    fun saveAllGurantees_replacesContentsAndForcesUploaded() = runTest {
        store.insertGuarantee(guarantee(externalId = "old"))

        store.saveAllGurantees(listOf(guarantee(externalId = "new", uploaded = 0)))

        val all = store.getAllGuarantees()
        assertEquals(listOf("new"), all.map { it.EXTERNAL_ID })
        assertEquals(
            "saveAllGurantees fuerza UPLOADED=1 en todo lo que sincroniza, sin importar el valor de entrada",
            1,
            all.single().UPLOADED
        )
    }

    @Test
    fun updateUploadedStatus_and_markGuaranteeAsUploaded() = runTest {
        store.insertGuarantee(guarantee(externalId = "g-1", uploaded = 0))
        val id = store.getGuaranteeByExternalId("g-1")!!.ID

        store.updateUploadedStatus(id, 1)
        assertEquals(1, store.getGuaranteeByExternalId("g-1")!!.UPLOADED)

        store.updateUploadedStatus(id, 0)
        store.markGuaranteeAsUploaded("g-1")
        assertEquals(1, store.getGuaranteeByExternalId("g-1")!!.UPLOADED)
    }

    @Test
    fun getPendingGuarantees_onlyUnuploaded() = runTest {
        store.insertGuarantee(guarantee(externalId = "pend", uploaded = 0))
        store.insertGuarantee(guarantee(externalId = "done", uploaded = 1))

        assertEquals(listOf("pend"), store.getPendingGuarantees().map { it.EXTERNAL_ID })
    }

    // ─── imagenes ──────────────────────────────────────────────────────────

    @Test
    fun insertGuaranteeImage_and_getImagesByExternalId_roundTrips() = runTest {
        store.insertGuarantee(guarantee(externalId = "g-1"))

        store.insertGuaranteeImage(
            listOf(
                GuaranteeImageEntity(
                    ID = "img-1",
                    GARANTIA_ID = "g-1",
                    IMG_PATH = "/x/y.jpg",
                    IMG_MIME = "image/jpeg",
                    IMG_DESC = null,
                    FECHA_SUBIDA = "2026-08-08T10:00:00Z"
                )
            )
        )

        val images = store.getImagesByExternalId("g-1")

        assertEquals(1, images.size)
        assertEquals("/x/y.jpg", images.first().IMG_PATH)
    }

    // ─── eventos ───────────────────────────────────────────────────────────

    @Test
    fun insertGuaranteeEvent_and_getEventsByGuaranteeId_roundTrips() = runTest {
        store.insertGuaranteeEvent(
            GuaranteeEventEntity(
                ID = "evt-1",
                GARANTIA_ID = "g-1",
                TIPO_EVENTO = "SOLICITUD",
                FECHA_EVENTO = "2026-08-08T10:00:00Z",
                COMENTARIO = null,
                ENVIADO = 0
            )
        )

        val events = store.getEventsByGuaranteeId("g-1")

        assertEquals(1, events.size)
        assertEquals("SOLICITUD", events.first().TIPO_EVENTO)
    }

    @Test
    fun saveAllGuaranteeEvents_replacesContentsAndForcesEnviado() = runTest {
        store.insertGuaranteeEvent(
            GuaranteeEventEntity("old", "g-1", "SOLICITUD", "2026-08-08T10:00:00Z", null, 0)
        )

        store.saveAllGuaranteeEvents(
            listOf(GuaranteeEventEntity("new", "g-1", "SOLICITUD", "2026-08-08T10:00:00Z", null, 0))
        )

        val all = store.getAllGuaranteeEvents()
        assertEquals(listOf("new"), all.map { it.ID })
        assertEquals(1, all.single().ENVIADO)
    }

    @Test
    fun updateEventSentStatus_and_getPendingGuaranteeEvents() = runTest {
        store.insertGuaranteeEvent(
            GuaranteeEventEntity("evt-1", "g-1", "SOLICITUD", "2026-08-08T10:00:00Z", null, 0)
        )
        store.insertGuaranteeEvent(
            GuaranteeEventEntity("evt-2", "g-1", "SOLICITUD", "2026-08-08T11:00:00Z", null, 0)
        )

        store.updateEventSentStatus("evt-1", 1)

        assertEquals(listOf("evt-2"), store.getPendingGuaranteeEvents().map { it.ID })
    }

    // ─── deleteAllGuaranteesData ───────────────────────────────────────────

    @Test
    fun deleteAllGuaranteesData_clearsGuaranteesImagesAndEvents() = runTest {
        store.insertGuarantee(guarantee(externalId = "g-1"))
        store.insertGuaranteeImage(
            listOf(
                GuaranteeImageEntity(
                    "img-1",
                    "g-1",
                    "/x.jpg",
                    "image/jpeg",
                    null,
                    "2026-08-08T10:00:00Z"
                )
            )
        )
        store.insertGuaranteeEvent(
            GuaranteeEventEntity("evt-1", "g-1", "SOLICITUD", "2026-08-08T10:00:00Z", null, 0)
        )

        store.deleteAllGuaranteesData()

        assertTrue(store.getAllGuarantees().isEmpty())
        assertTrue(store.getImagesByExternalId("g-1").isEmpty())
        assertTrue(store.getAllGuaranteeEvents().isEmpty())
    }

    // ─── updateGuaranteeStatusAndInsertEvent: usa el AppClock inyectado ───────

    @Test
    fun updateGuaranteeStatusAndInsertEvent_usesInjectedClockAndUpdatesEstado() = runTest {
        val clock = FakeClock(Instant.parse("2026-08-08T14:30:00Z"))
        val storeWithClock = GuaranteesLocalDataSource(db.guaranteeDao(), clock)
        storeWithClock.insertGuarantee(guarantee(externalId = "g-1", estado = "notificado"))
        val id = storeWithClock.getGuaranteeByExternalId("g-1")!!.ID

        storeWithClock.updateGuaranteeStatusAndInsertEvent(
            guaranteeId = id,
            externalId = "g-1",
            newEstado = "en_proceso",
            tipoEvento = "CAMBIO_ESTADO",
            comentario = "revisado"
        )

        assertEquals("en_proceso", storeWithClock.getGuaranteeByExternalId("g-1")!!.ESTADO)
        val event = storeWithClock.getEventsByGuaranteeId("g-1").single()
        assertEquals("2026-08-08T14:30:00Z", event.FECHA_EVENTO)
        assertEquals("revisado", event.COMENTARIO)
    }

    // ─── equivalencia inyectado ⇔ puente context (preserva AppClock) ──────────

    @Test
    fun injectedFormEquivalentToContextForm_defaultClock() = runTest {
        store.insertGuarantee(guarantee(externalId = "eq-1"))

        val contextForm =
            GuaranteesLocalDataSource(ApplicationProvider.getApplicationContext<Context>())

        assertEquals(
            "ambos constructores resuelven a la misma DB",
            store.getGuaranteeByExternalId("eq-1")!!.ESTADO,
            contextForm.getGuaranteeByExternalId("eq-1")!!.ESTADO
        )
    }

    @Test
    fun contextBridgePreservesInjectedClock() = runTest {
        val clock = FakeClock(Instant.parse("2026-01-01T00:00:00Z"))
        val bridged = GuaranteesLocalDataSource(
            ApplicationProvider.getApplicationContext<Context>(),
            clock
        )
        bridged.insertGuarantee(guarantee(externalId = "clk-1"))
        val id = bridged.getGuaranteeByExternalId("clk-1")!!.ID

        bridged.updateGuaranteeStatusAndInsertEvent(
            guaranteeId = id,
            externalId = "clk-1",
            newEstado = "resuelto",
            tipoEvento = "CAMBIO_ESTADO"
        )

        assertEquals(
            "el puente context(clock) sigue usando el AppClock inyectado, no AppClock.System",
            "2026-01-01T00:00:00Z",
            bridged.getEventsByGuaranteeId("clk-1").single().FECHA_EVENTO
        )
    }
}
