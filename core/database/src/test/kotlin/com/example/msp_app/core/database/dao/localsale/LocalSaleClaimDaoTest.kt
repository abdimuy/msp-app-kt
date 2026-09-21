package com.example.msp_app.core.database.dao.localsale

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val LEASE_MS = 30 * 60 * 1000L // 30 min, mismo arrendamiento que el plan fija
private const val SALE_ID = "sale-reclamo-001"
private const val CLAIM_ID_A = "claim-uuid-aaaa"
private const val CLAIM_ID_B = "claim-uuid-bbbb"

/**
 * Cubre el DAO atómico del reclamo (plan "Corregir una venta antes de que
 * suba", "El mecanismo de la carrera"): cada método de [LocalSaleDao] que
 * toca `EDIT_CLAIM_ID`/`EDIT_CLAIMED_AT`/`REVISION` es un solo `UPDATE`, así
 * que la atomicidad la da SQLite, no un mutex de Kotlin. El tiempo SIEMPRE
 * viene de [FakeClock] — nunca reloj real, para que "el reclamo venció" sea
 * determinista.
 *
 * También mide la "Duda a resolver" del brief de Task 1: si Room 2.6.1
 * devuelve `Int` (filas afectadas) de un `@Query` UPDATE `suspend fun`. Las
 * aserciones de abajo sobre `claimForEdit`/`commitEditGuard`/`releaseClaim`
 * SON esa medición — compilan y pasan sin envolver nada en un plan B de
 * releer la fila, así que la respuesta medida es: sí lo devuelve.
 */
class LocalSaleClaimDaoTest : RobolectricTestBase() {

    private lateinit var database: AppDatabase
    private val clock = FakeClock.at("2026-09-20T12:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun freeSale(
        saleId: String = SALE_ID,
        enviado: Boolean = false,
        lastUploadPermanent: Boolean? = null,
        editClaimId: String? = null,
        editClaimedAt: Long? = null,
        revision: Int = 0
    ) = LocalSaleEntity(
        LOCAL_SALE_ID = saleId,
        NOMBRE_CLIENTE = "Rosa Elena Martinez Vazquez",
        FECHA_VENTA = "2026-09-18T15:30:00Z",
        LATITUD = 19.043415,
        LONGITUD = -98.198234,
        DIRECCION = "Privada de las Rosas 45",
        PARCIALIDAD = 850.0,
        ENGANCHE = 500.0,
        TELEFONO = "2221234567",
        FREC_PAGO = "SEMANAL",
        AVAL_O_RESPONSABLE = "Juan Martinez Vazquez",
        NOTA = null,
        DIA_COBRANZA = "MARTES",
        PRECIO_TOTAL = 6800.0,
        TIEMPO_A_CORTO_PLAZOMESES = 8,
        MONTO_A_CORTO_PLAZO = 6300.0,
        MONTO_DE_CONTADO = 5800.0,
        ENVIADO = enviado,
        LAST_UPLOAD_PERMANENT = lastUploadPermanent,
        EDIT_CLAIM_ID = editClaimId,
        EDIT_CLAIMED_AT = editClaimedAt,
        REVISION = revision
    )

    private suspend fun insert(sale: LocalSaleEntity) = database.localSaleDao().insertSale(sale)

    // ─── claimForEdit ───────────────────────────────────────────────────

    @Test
    fun `claimForEdit toma el reclamo sobre una venta libre`() = runTest {
        insert(freeSale())

        val rows = database.localSaleDao().claimForEdit(
            SALE_ID,
            CLAIM_ID_A,
            clock.now().toEpochMilli(),
            LEASE_MS
        )

        assertEquals("una venta libre debe aceptar el reclamo", 1, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals(CLAIM_ID_A, sale?.EDIT_CLAIM_ID)
        assertEquals(clock.now().toEpochMilli(), sale?.EDIT_CLAIMED_AT)
    }

    @Test
    fun `claim rechaza venta ya enviada`() = runTest {
        insert(freeSale(enviado = true))

        val rows = database.localSaleDao().claimForEdit(
            SALE_ID,
            CLAIM_ID_A,
            clock.now().toEpochMilli(),
            LEASE_MS
        )

        assertEquals("una venta ya subida no se puede reclamar para editar", 0, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertNull("el reclamo no debe haberse escrito", sale?.EDIT_CLAIM_ID)
    }

    @Test
    fun `claimForEdit rechaza venta con fallo permanente aunque ENVIADO siga en cero`() = runTest {
        insert(freeSale(lastUploadPermanent = true))

        val rows = database.localSaleDao().claimForEdit(
            SALE_ID,
            CLAIM_ID_A,
            clock.now().toEpochMilli(),
            LEASE_MS
        )

        assertEquals(
            "un fallo permanente significa que el servidor ya resguardo el intento",
            0,
            rows
        )
    }

    @Test
    fun `claimForEdit rechaza si ya hay un reclamo vigente`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(editClaimId = CLAIM_ID_A, editClaimedAt = now))

        val rows = database.localSaleDao().claimForEdit(SALE_ID, CLAIM_ID_B, now, LEASE_MS)

        assertEquals("un reclamo vigente bloquea a un segundo reclamante", 0, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals("el reclamo original no debe perderse", CLAIM_ID_A, sale?.EDIT_CLAIM_ID)
    }

    @Test
    fun `claimForEdit acepta si el reclamo anterior vencio`() = runTest {
        val claimedAt = clock.now().toEpochMilli()
        insert(freeSale(editClaimId = CLAIM_ID_A, editClaimedAt = claimedAt))

        clock.advance(Duration.ofMillis(LEASE_MS + 1))

        val rows = database.localSaleDao().claimForEdit(
            SALE_ID,
            CLAIM_ID_B,
            clock.now().toEpochMilli(),
            LEASE_MS
        )

        assertEquals("un reclamo vencido se recupera solo", 1, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals(CLAIM_ID_B, sale?.EDIT_CLAIM_ID)
    }

    @Test
    fun `claimForEdit rechaza justo antes de que el reclamo venza`() = runTest {
        val claimedAt = clock.now().toEpochMilli()
        insert(freeSale(editClaimId = CLAIM_ID_A, editClaimedAt = claimedAt))

        clock.advance(Duration.ofMillis(LEASE_MS - 1))

        val rows = database.localSaleDao().claimForEdit(
            SALE_ID,
            CLAIM_ID_B,
            clock.now().toEpochMilli(),
            LEASE_MS
        )

        assertEquals("un milisegundo antes de vencer, el reclamo sigue vigente", 0, rows)
    }

    // ─── releaseClaim ───────────────────────────────────────────────────

    @Test
    fun `releaseClaim suelta el reclamo del dueño`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(editClaimId = CLAIM_ID_A, editClaimedAt = now))

        val rows = database.localSaleDao().releaseClaim(SALE_ID, CLAIM_ID_A)

        assertEquals(1, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertNull(sale?.EDIT_CLAIM_ID)
        assertNull(sale?.EDIT_CLAIMED_AT)
    }

    @Test
    fun `releaseClaim con claimId equivocado no toca la fila`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(editClaimId = CLAIM_ID_A, editClaimedAt = now))

        val rows = database.localSaleDao().releaseClaim(SALE_ID, CLAIM_ID_B)

        assertEquals(0, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals("el reclamo ajeno no se toca", CLAIM_ID_A, sale?.EDIT_CLAIM_ID)
    }

    // ─── commitEditGuard ────────────────────────────────────────────────

    @Test
    fun `commitEditGuard con otro claimId devuelve 0 y deja la fila intacta`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(editClaimId = CLAIM_ID_A, editClaimedAt = now, revision = 3))

        val rows = database.localSaleDao().commitEditGuard(SALE_ID, CLAIM_ID_B)

        assertEquals("un claimId que no coincide no puede comitear", 0, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals("REVISION no debe subir si el guardia rechazo", 3, sale?.REVISION)
        assertEquals("el reclamo del dueño real no se toca", CLAIM_ID_A, sale?.EDIT_CLAIM_ID)
    }

    @Test
    fun `commitEditGuard con el claimId correcto cierra el reclamo y sube REVISION`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(editClaimId = CLAIM_ID_A, editClaimedAt = now, revision = 0))

        val rows = database.localSaleDao().commitEditGuard(SALE_ID, CLAIM_ID_A)

        assertEquals(1, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertNull(sale?.EDIT_CLAIM_ID)
        assertNull(sale?.EDIT_CLAIMED_AT)
        assertEquals(1, sale?.REVISION)
    }

    @Test
    fun `commitEditGuard rechaza si la venta ya se envio mientras se editaba`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(editClaimId = CLAIM_ID_A, editClaimedAt = now, enviado = true))

        val rows = database.localSaleDao().commitEditGuard(SALE_ID, CLAIM_ID_A)

        assertEquals(
            "si el subidor ya marco ENVIADO=1, el guardado del usuario debe perder la carrera",
            0,
            rows
        )
    }

    // ─── markSentAndCloseEdit ───────────────────────────────────────────

    @Test
    fun `marcar enviada cierra el reclamo`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(editClaimId = CLAIM_ID_A, editClaimedAt = now))

        database.localSaleDao().markSentAndCloseEdit(SALE_ID)

        // Leidos en la MISMA lectura (una sola fila), no en dos consultas
        // separadas que pudieran ver estados distintos.
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertNotNull(sale)
        assertTrue("ENVIADO debe quedar en 1", sale!!.ENVIADO)
        assertNull("el reclamo debe quedar cerrado", sale.EDIT_CLAIM_ID)
        assertNull(sale.EDIT_CLAIMED_AT)
    }

    @Test
    fun `marcar enviada sin reclamo vigente tambien deja ENVIADO en 1`() = runTest {
        insert(freeSale())

        database.localSaleDao().markSentAndCloseEdit(SALE_ID)

        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertTrue(sale!!.ENVIADO)
        assertNull(sale.EDIT_CLAIM_ID)
    }

    // ─── getUploadableSales ─────────────────────────────────────────────

    @Test
    fun `getUploadableSales excluye ventas ya enviadas`() = runTest {
        insert(freeSale(saleId = "sale-a", enviado = true))
        insert(freeSale(saleId = "sale-b", enviado = false))

        val uploadable = database.localSaleDao().getUploadableSales(
            clock.now().toEpochMilli(),
            LEASE_MS
        )

        assertEquals(setOf("sale-b"), uploadable.map { it.LOCAL_SALE_ID }.toSet())
    }

    @Test
    fun `getUploadableSales excluye una venta con reclamo vigente`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(saleId = "sale-a", editClaimId = CLAIM_ID_A, editClaimedAt = now))
        insert(freeSale(saleId = "sale-b"))

        val uploadable = database.localSaleDao().getUploadableSales(now, LEASE_MS)

        assertEquals(
            "la venta con reclamo vigente no debe entrar al barrido mientras el dueño escribe",
            setOf("sale-b"),
            uploadable.map { it.LOCAL_SALE_ID }.toSet()
        )
    }

    @Test
    fun `getUploadableSales incluye una venta con reclamo vencido`() = runTest {
        val claimedAt = clock.now().toEpochMilli()
        insert(freeSale(saleId = "sale-a", editClaimId = CLAIM_ID_A, editClaimedAt = claimedAt))

        clock.advance(Duration.ofMillis(LEASE_MS + 1))

        val uploadable = database.localSaleDao().getUploadableSales(
            clock.now().toEpochMilli(),
            LEASE_MS
        )

        assertEquals(
            "un reclamo vencido no debe retener la venta para siempre",
            setOf("sale-a"),
            uploadable.map { it.LOCAL_SALE_ID }.toSet()
        )
    }

    // ─── getSaleClaimSnapshot ───────────────────────────────────────────

    @Test
    fun `getSaleClaimSnapshot refleja el estado real de la fila`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            freeSale(editClaimId = CLAIM_ID_A, editClaimedAt = now, revision = 2, enviado = false)
        )

        val snapshot = database.localSaleDao().getSaleClaimSnapshot(SALE_ID)

        assertNotNull(snapshot)
        assertEquals(CLAIM_ID_A, snapshot!!.EDIT_CLAIM_ID)
        assertEquals(2, snapshot.REVISION)
        assertFalse(snapshot.ENVIADO)
    }

    @Test
    fun `getSaleClaimSnapshot cambia tras markSentAndCloseEdit, justo lo que revalida el subidor antes del POST`() =
        runTest {
            val now = clock.now().toEpochMilli()
            insert(freeSale(editClaimId = CLAIM_ID_A, editClaimedAt = now))
            val before = database.localSaleDao().getSaleClaimSnapshot(SALE_ID)

            database.localSaleDao().markSentAndCloseEdit(SALE_ID)
            val after = database.localSaleDao().getSaleClaimSnapshot(SALE_ID)

            assertEquals(CLAIM_ID_A, before?.EDIT_CLAIM_ID)
            assertFalse(before!!.ENVIADO)
            assertNull(
                "tras marcar enviada, el snapshot debe reflejar el reclamo cerrado",
                after?.EDIT_CLAIM_ID
            )
            assertTrue(after!!.ENVIADO)
        }

    // ─── Corregir dos veces seguidas (el caso que más preocupa al dueño) ─

    @Test
    fun `corregir dos veces seguidas sube REVISION a 2 sin dejar reclamo colgado`() = runTest {
        insert(freeSale())
        var now = clock.now().toEpochMilli()

        val firstClaim = database.localSaleDao().claimForEdit(SALE_ID, CLAIM_ID_A, now, LEASE_MS)
        assertEquals(1, firstClaim)
        val firstCommit = database.localSaleDao().commitEditGuard(SALE_ID, CLAIM_ID_A)
        assertEquals(1, firstCommit)

        clock.advance(Duration.ofMinutes(1))
        now = clock.now().toEpochMilli()

        val secondClaim = database.localSaleDao().claimForEdit(SALE_ID, CLAIM_ID_B, now, LEASE_MS)
        assertEquals("la venta debe volver a estar libre tras el primer commit", 1, secondClaim)
        val secondCommit = database.localSaleDao().commitEditGuard(SALE_ID, CLAIM_ID_B)
        assertEquals(1, secondCommit)

        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals("dos correcciones commiteadas", 2, sale?.REVISION)
        assertNull("sin reclamo huerfano tras la segunda correccion", sale?.EDIT_CLAIM_ID)
        assertNull(sale?.EDIT_CLAIMED_AT)
    }
}
