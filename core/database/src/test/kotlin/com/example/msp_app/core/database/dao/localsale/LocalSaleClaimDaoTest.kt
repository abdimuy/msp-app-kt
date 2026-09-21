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

// 30 min, tal como fija el plan para el candado de EDICIÓN.
private const val EDIT_LEASE_MS = 30 * 60 * 1000L

// Arrendamiento del candado de SUBIDA: medido, no inventado. El cliente v2
// (VentasApi.crearVenta, el POST que sube fotos) usa el perfil de OkHttp de
// `RetrofitClientFactory.V2_TIMEOUT_SECONDS = 60L` (connect Y read, cada uno
// por separado — core/network/.../RetrofitClientFactory.kt:105,122-123,142).
// En el peor caso patológico (conectar lento y luego leer lento) esos dos
// timeouts se SUMAN: 120 s de red antes de que OkHttp tire la toalla. A eso
// se le agrega margen para armar el cuerpo multipart desde disco (fotos) y
// para el hueco entre acuñar el candado y que la llamada de red arranque
// realmente: 60 s más → 180 s. (Nota: no es el "120 s de lectura" que se
// mencionó al pedir esta ronda — ese número es el `ReadTimeout` del
// SERVIDOR Go, `msp-api`, un repo distinto; el timeout real del CLIENTE
// Android, medido aquí, es 60 s por fase.)
private const val UPLOAD_LEASE_MS = 180 * 1000L

private const val SALE_ID = "sale-reclamo-001"
private const val CLAIM_ID_A = "claim-uuid-aaaa"
private const val CLAIM_ID_B = "claim-uuid-bbbb"

/**
 * Cubre el DAO atómico del candado único de la fila (plan "Corregir una venta
 * antes de que suba", "El mecanismo de la carrera" + ronda 2 de revisión: la
 * fila tiene UN candado que puede tomar la edición o la subida, nunca las
 * dos — mutua exclusión por construcción, una sola columna `CLAIM_ID`). Cada
 * método del DAO es un solo `UPDATE`, así que la atomicidad la da SQLite, no
 * un mutex de Kotlin. El tiempo SIEMPRE viene de [FakeClock] — nunca reloj
 * real, para que "el candado venció" sea determinista.
 *
 * También mide la "Duda a resolver" del brief original de Task 1: si Room
 * 2.6.1 devuelve `Int` (filas afectadas) de un `@Query` UPDATE `suspend fun`.
 * Las aserciones de abajo sobre `claimForEdit`/`claimForUpload`/
 * `commitEditGuard`/`releaseClaim` SON esa medición — compilan y pasan sin
 * envolver nada en un plan B de releer la fila, así que la respuesta medida
 * es: sí lo devuelve.
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

    // Fixture de prueba con un parámetro por variante de estado del candado
    // que este archivo ejercita (kind/claimId/claimedAt por separado, más
    // enviado/permanente/revision) — bajarlo de 7 agruparía justo lo que cada
    // test necesita variar independientemente.
    @Suppress("LongParameterList")
    private fun freeSale(
        saleId: String = SALE_ID,
        enviado: Boolean = false,
        lastUploadPermanent: Boolean? = null,
        claimId: String? = null,
        claimKind: String? = null,
        claimedAt: Long? = null,
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
        CLAIM_ID = claimId,
        CLAIM_KIND = claimKind,
        CLAIMED_AT = claimedAt,
        REVISION = revision
    )

    private suspend fun insert(sale: LocalSaleEntity) = database.localSaleDao().insertSale(sale)

    private suspend fun claimForEdit(claimId: String, now: Long = clock.now().toEpochMilli()) =
        database.localSaleDao().claimForEdit(SALE_ID, claimId, now, EDIT_LEASE_MS, UPLOAD_LEASE_MS)

    private suspend fun claimForUpload(claimId: String, now: Long = clock.now().toEpochMilli()) =
        database.localSaleDao().claimForUpload(
            SALE_ID,
            claimId,
            now,
            EDIT_LEASE_MS,
            UPLOAD_LEASE_MS
        )

    // ─── claimForEdit ───────────────────────────────────────────────────

    @Test
    fun `claimForEdit toma el candado sobre una venta libre`() = runTest {
        insert(freeSale())

        val rows = claimForEdit(CLAIM_ID_A)

        assertEquals("una venta libre debe aceptar el candado", 1, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals(CLAIM_ID_A, sale?.CLAIM_ID)
        assertEquals("EDIT", sale?.CLAIM_KIND)
        assertEquals(clock.now().toEpochMilli(), sale?.CLAIMED_AT)
    }

    @Test
    fun `claim rechaza venta ya enviada`() = runTest {
        insert(freeSale(enviado = true))

        val rows = claimForEdit(CLAIM_ID_A)

        assertEquals("una venta ya subida no se puede reclamar para editar", 0, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertNull("el candado no debe haberse escrito", sale?.CLAIM_ID)
    }

    @Test
    fun `claimForEdit rechaza venta con fallo permanente aunque ENVIADO siga en cero`() = runTest {
        insert(freeSale(lastUploadPermanent = true))

        val rows = claimForEdit(CLAIM_ID_A)

        assertEquals(
            "un fallo permanente significa que el servidor ya resguardo el intento",
            0,
            rows
        )
    }

    /**
     * Important #1 de la ronda 2: el caso MÁS COMÚN sin señal. Un fallo
     * transitorio (sin internet, timeout) escribe `LAST_UPLOAD_PERMANENT =
     * false`, no `NULL` (`RoomUploadFailureRepository.updateUploadFailure`).
     * Antes solo se probaba `NULL` y `true`; si alguien reduce el predicado a
     * `LAST_UPLOAD_PERMANENT IS NULL` (perdiendo el `OR ... = 0`), el dueño
     * ya no podría corregir la venta más común que falla — y todo seguía
     * verde sin esta prueba.
     */
    @Test
    fun `claimForEdit acepta con LAST_UPLOAD_PERMANENT en false`() = runTest {
        insert(freeSale(lastUploadPermanent = false))

        val rows = claimForEdit(CLAIM_ID_A)

        assertEquals(
            "un fallo transitorio (LAST_UPLOAD_PERMANENT=false) SI debe poder corregirse",
            1,
            rows
        )
    }

    @Test
    fun `claimForEdit rechaza si ya hay un candado de EDICION vigente`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = now))

        val rows = claimForEdit(CLAIM_ID_B, now)

        assertEquals("un candado de edicion vigente bloquea a un segundo reclamante", 0, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals("el candado original no debe perderse", CLAIM_ID_A, sale?.CLAIM_ID)
    }

    /**
     * Bloque C (carrera nueva encontrada en revisión): mutua exclusión. Si el
     * subidor ya tiene la venta en vuelo (candado UPLOAD vigente), abrir el
     * editor NO puede ganarle la fila — si pudiera, el guardia del guardado
     * vería `ENVIADO=0` y commitearía antes de que vuelva el 2xx, dejando al
     * servidor con el cuerpo viejo y al teléfono creyendo que la corrección
     * se aplicó.
     */
    @Test
    fun `claimForEdit rechaza si hay un candado de SUBIDA vigente`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "UPLOAD", claimedAt = now))

        val rows = claimForEdit(CLAIM_ID_B, now)

        assertEquals(
            "un POST en vuelo debe ganar la fila: el editor no puede abrirse encima",
            0,
            rows
        )
    }

    /**
     * Bloque A #2 de la ronda 2: la frontera del arrendamiento de EDICIÓN
     * fijada en el milisegundo EXACTO, no solo `+1`/`-1`. `<=` en vez de `<`
     * es la diferencia entre "vencido en el instante justo" y "vencido un
     * tick después" — ambas versiones pasaban con las pruebas viejas.
     */
    @Test
    fun `claimForEdit acepta exactamente en el milisegundo en que vence un candado de EDICION`() =
        runTest {
            val claimedAt = clock.now().toEpochMilli()
            insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = claimedAt))

            clock.advance(Duration.ofMillis(EDIT_LEASE_MS))

            val rows = claimForEdit(CLAIM_ID_B)

            assertEquals("exactamente al cumplirse el arrendamiento, ya vencio", 1, rows)
        }

    @Test
    fun `claimForEdit rechaza un milisegundo antes de que venza un candado de EDICION`() = runTest {
        val claimedAt = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = claimedAt))

        clock.advance(Duration.ofMillis(EDIT_LEASE_MS - 1))

        val rows = claimForEdit(CLAIM_ID_B)

        assertEquals("un milisegundo antes de vencer, el candado sigue vigente", 0, rows)
    }

    /**
     * Bloque C: la frontera del arrendamiento de SUBIDA, también en el
     * milisegundo exacto — el subidor puede recuperar una venta cuyo POST
     * anterior nunca volvió (la app murió, el proceso se mató) sin esperar
     * más de lo que su propio arrendamiento promete.
     */
    @Test
    fun `claimForEdit acepta exactamente en el milisegundo en que vence un candado de SUBIDA`() =
        runTest {
            val claimedAt = clock.now().toEpochMilli()
            insert(freeSale(claimId = CLAIM_ID_A, claimKind = "UPLOAD", claimedAt = claimedAt))

            clock.advance(Duration.ofMillis(UPLOAD_LEASE_MS))

            val rows = claimForEdit(CLAIM_ID_B)

            assertEquals("exactamente al cumplirse el arrendamiento de subida, ya vencio", 1, rows)
        }

    @Test
    fun `claimForEdit rechaza un milisegundo antes de que venza un candado de SUBIDA`() = runTest {
        val claimedAt = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "UPLOAD", claimedAt = claimedAt))

        clock.advance(Duration.ofMillis(UPLOAD_LEASE_MS - 1))

        val rows = claimForEdit(CLAIM_ID_B)

        assertEquals("un milisegundo antes de vencer el candado de subida, sigue vigente", 0, rows)
    }

    /**
     * Minor B2 de la ronda 2: "el estado que nunca vence". Un candado con
     * `CLAIM_ID` no nulo pero `CLAIMED_AT` nulo no vencería jamás bajo la
     * comparación `<=` de SQL (`NULL <= x` nunca es verdadero). Ningún camino
     * de hoy produce ese estado, pero "una captura nunca se retiene para
     * siempre" es la regla dura del plan — se trata como vencido por defensa
     * en profundidad.
     */
    @Test
    fun `claimForEdit acepta si el candado vigente tiene CLAIMED_AT nulo`() = runTest {
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = null))

        val rows = claimForEdit(CLAIM_ID_B)

        assertEquals(
            "un candado sin CLAIMED_AT no debe poder retener la venta para siempre",
            1,
            rows
        )
    }

    // ─── claimForUpload ─────────────────────────────────────────────────

    @Test
    fun `claimForUpload toma el candado sobre una venta libre`() = runTest {
        insert(freeSale())

        val rows = claimForUpload(CLAIM_ID_A)

        assertEquals(1, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals(CLAIM_ID_A, sale?.CLAIM_ID)
        assertEquals("UPLOAD", sale?.CLAIM_KIND)
    }

    @Test
    fun `claimForUpload rechaza venta ya enviada`() = runTest {
        insert(freeSale(enviado = true))

        val rows = claimForUpload(CLAIM_ID_A)

        assertEquals(0, rows)
    }

    /**
     * Bloque C, el requisito mínimo simétrico: con un candado de EDICIÓN
     * vigente, el subidor no puede tomar la venta — la corrección gana y el
     * worker debe frenarse con `Result.retry()` en vez de mandar el POST con
     * el cuerpo viejo.
     */
    @Test
    fun `claimForUpload rechaza si hay un candado de EDICION vigente`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = now))

        val rows = claimForUpload(CLAIM_ID_B, now)

        assertEquals(
            "la correccion en curso debe ganar: el subidor no puede tomar la fila",
            0,
            rows
        )
    }

    @Test
    fun `claimForUpload acepta si un candado de EDICION anterior vencio`() = runTest {
        val claimedAt = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = claimedAt))

        clock.advance(Duration.ofMillis(EDIT_LEASE_MS))

        val rows = claimForUpload(CLAIM_ID_B)

        assertEquals(1, rows)
    }

    @Test
    fun `claimForUpload rechaza si ya hay otro candado de SUBIDA vigente`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "UPLOAD", claimedAt = now))

        val rows = claimForUpload(CLAIM_ID_B, now)

        assertEquals(0, rows)
    }

    // ─── releaseClaim ───────────────────────────────────────────────────

    @Test
    fun `releaseClaim suelta un candado de EDICION del dueño`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = now))

        val rows = database.localSaleDao().releaseClaim(SALE_ID, CLAIM_ID_A)

        assertEquals(1, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertNull(sale?.CLAIM_ID)
        assertNull(sale?.CLAIM_KIND)
        assertNull(sale?.CLAIMED_AT)
    }

    @Test
    fun `releaseClaim suelta un candado de SUBIDA del dueño`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "UPLOAD", claimedAt = now))

        val rows = database.localSaleDao().releaseClaim(SALE_ID, CLAIM_ID_A)

        assertEquals(1, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertNull(sale?.CLAIM_ID)
        assertNull(sale?.CLAIM_KIND)
    }

    @Test
    fun `releaseClaim con claimId equivocado no toca la fila`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = now))

        val rows = database.localSaleDao().releaseClaim(SALE_ID, CLAIM_ID_B)

        assertEquals(0, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals("el candado ajeno no se toca", CLAIM_ID_A, sale?.CLAIM_ID)
    }

    // ─── commitEditGuard ────────────────────────────────────────────────

    @Test
    fun `commitEditGuard con otro claimId devuelve 0 y deja la fila intacta`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = now, revision = 3))

        val rows = database.localSaleDao().commitEditGuard(SALE_ID, CLAIM_ID_B)

        assertEquals("un claimId que no coincide no puede comitear", 0, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals("REVISION no debe subir si el guardia rechazo", 3, sale?.REVISION)
        assertEquals("el candado del dueño real no se toca", CLAIM_ID_A, sale?.CLAIM_ID)
    }

    @Test
    fun `commitEditGuard con el claimId correcto cierra el candado y sube REVISION`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = now, revision = 0))

        val rows = database.localSaleDao().commitEditGuard(SALE_ID, CLAIM_ID_A)

        assertEquals(1, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertNull(sale?.CLAIM_ID)
        assertNull(sale?.CLAIM_KIND)
        assertNull(sale?.CLAIMED_AT)
        assertEquals(1, sale?.REVISION)
    }

    @Test
    fun `commitEditGuard rechaza si la venta ya se envio mientras se editaba`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = now, enviado = true))

        val rows = database.localSaleDao().commitEditGuard(SALE_ID, CLAIM_ID_A)

        assertEquals(
            "si el subidor ya marco ENVIADO=1, el guardado del usuario debe perder la carrera",
            0,
            rows
        )
    }

    // ─── markSentAndCloseEdit ───────────────────────────────────────────

    @Test
    fun `marcar enviada cierra el candado de EDICION`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = now))

        database.localSaleDao().markSentAndCloseEdit(SALE_ID)

        // Leidos en la MISMA lectura (una sola fila), no en dos consultas
        // separadas que pudieran ver estados distintos.
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertNotNull(sale)
        assertTrue("ENVIADO debe quedar en 1", sale!!.ENVIADO)
        assertNull("el candado debe quedar cerrado", sale.CLAIM_ID)
        assertNull(sale.CLAIM_KIND)
        assertNull(sale.CLAIMED_AT)
    }

    /**
     * Bloque C, el requisito mínimo explícito: `markSentAndCloseEdit` cierra
     * CUALQUIER candado, no solo el de edición — en este punto el propio
     * subidor es quien tiene el candado de SUBIDA (lo tomó con
     * `claimForUpload` antes del POST) y debe soltarse a sí mismo al marcar
     * la venta enviada.
     */
    @Test
    fun `marcar enviada cierra el candado de SUBIDA del propio subidor`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "UPLOAD", claimedAt = now))

        database.localSaleDao().markSentAndCloseEdit(SALE_ID)

        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertTrue(sale!!.ENVIADO)
        assertNull("el candado de subida tambien debe quedar cerrado", sale.CLAIM_ID)
        assertNull(sale.CLAIM_KIND)
        assertNull(sale.CLAIMED_AT)
    }

    @Test
    fun `marcar enviada sin candado vigente tambien deja ENVIADO en 1`() = runTest {
        insert(freeSale())

        database.localSaleDao().markSentAndCloseEdit(SALE_ID)

        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertTrue(sale!!.ENVIADO)
        assertNull(sale.CLAIM_ID)
    }

    // ─── getUploadableSales ─────────────────────────────────────────────

    private suspend fun getUploadableSales(now: Long = clock.now().toEpochMilli()) =
        database.localSaleDao().getUploadableSales(now, EDIT_LEASE_MS, UPLOAD_LEASE_MS)

    @Test
    fun `getUploadableSales excluye ventas ya enviadas`() = runTest {
        insert(freeSale(saleId = "sale-a", enviado = true))
        insert(freeSale(saleId = "sale-b", enviado = false))

        val uploadable = getUploadableSales()

        assertEquals(setOf("sale-b"), uploadable.map { it.LOCAL_SALE_ID }.toSet())
    }

    @Test
    fun `getUploadableSales excluye una venta con candado de EDICION vigente`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            freeSale(saleId = "sale-a", claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = now)
        )
        insert(freeSale(saleId = "sale-b"))

        val uploadable = getUploadableSales(now)

        assertEquals(
            "la venta con candado vigente no debe entrar al barrido mientras el dueño escribe",
            setOf("sale-b"),
            uploadable.map { it.LOCAL_SALE_ID }.toSet()
        )
    }

    @Test
    fun `getUploadableSales excluye una venta con candado de SUBIDA vigente`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            freeSale(saleId = "sale-a", claimId = CLAIM_ID_A, claimKind = "UPLOAD", claimedAt = now)
        )
        insert(freeSale(saleId = "sale-b"))

        val uploadable = getUploadableSales(now)

        assertEquals(setOf("sale-b"), uploadable.map { it.LOCAL_SALE_ID }.toSet())
    }

    @Test
    fun `getUploadableSales incluye una venta con candado de EDICION vencido`() = runTest {
        val claimedAt = clock.now().toEpochMilli()
        insert(
            freeSale(
                saleId = "sale-a",
                claimId = CLAIM_ID_A,
                claimKind = "EDIT",
                claimedAt = claimedAt
            )
        )

        clock.advance(Duration.ofMillis(EDIT_LEASE_MS))

        val uploadable = getUploadableSales()

        assertEquals(
            "un candado vencido no debe retener la venta para siempre",
            setOf("sale-a"),
            uploadable.map { it.LOCAL_SALE_ID }.toSet()
        )
    }

    @Test
    fun `getUploadableSales incluye una venta con candado de SUBIDA vencido`() = runTest {
        val claimedAt = clock.now().toEpochMilli()
        insert(
            freeSale(
                saleId = "sale-a",
                claimId = CLAIM_ID_A,
                claimKind = "UPLOAD",
                claimedAt = claimedAt
            )
        )

        clock.advance(Duration.ofMillis(UPLOAD_LEASE_MS))

        val uploadable = getUploadableSales()

        assertEquals(setOf("sale-a"), uploadable.map { it.LOCAL_SALE_ID }.toSet())
    }

    // ─── getSaleClaimSnapshot ───────────────────────────────────────────

    @Test
    fun `getSaleClaimSnapshot refleja el estado real de la fila`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            freeSale(
                claimId = CLAIM_ID_A,
                claimKind = "EDIT",
                claimedAt = now,
                revision = 2,
                enviado = false
            )
        )

        val snapshot = database.localSaleDao().getSaleClaimSnapshot(SALE_ID)

        assertNotNull(snapshot)
        assertEquals(CLAIM_ID_A, snapshot!!.CLAIM_ID)
        assertEquals(2, snapshot.REVISION)
        assertFalse(snapshot.ENVIADO)
    }

    @Test
    fun `getSaleClaimSnapshot cambia tras markSentAndCloseEdit, justo lo que revalida el subidor antes del POST`() =
        runTest {
            val now = clock.now().toEpochMilli()
            insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = now))
            val before = database.localSaleDao().getSaleClaimSnapshot(SALE_ID)

            database.localSaleDao().markSentAndCloseEdit(SALE_ID)
            val after = database.localSaleDao().getSaleClaimSnapshot(SALE_ID)

            assertEquals(CLAIM_ID_A, before?.CLAIM_ID)
            assertFalse(before!!.ENVIADO)
            assertNull(
                "tras marcar enviada, el snapshot debe reflejar el candado cerrado",
                after?.CLAIM_ID
            )
            assertTrue(after!!.ENVIADO)
        }

    // ─── Corregir dos veces seguidas (el caso que más preocupa al dueño) ─

    @Test
    fun `corregir dos veces seguidas sube REVISION a 2 sin dejar candado colgado`() = runTest {
        insert(freeSale())
        var now = clock.now().toEpochMilli()

        val firstClaim = claimForEdit(CLAIM_ID_A, now)
        assertEquals(1, firstClaim)
        val firstCommit = database.localSaleDao().commitEditGuard(SALE_ID, CLAIM_ID_A)
        assertEquals(1, firstCommit)

        clock.advance(Duration.ofMinutes(1))
        now = clock.now().toEpochMilli()

        val secondClaim = claimForEdit(CLAIM_ID_B, now)
        assertEquals("la venta debe volver a estar libre tras el primer commit", 1, secondClaim)
        val secondCommit = database.localSaleDao().commitEditGuard(SALE_ID, CLAIM_ID_B)
        assertEquals(1, secondCommit)

        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals("dos correcciones commiteadas", 2, sale?.REVISION)
        assertNull("sin candado huerfano tras la segunda correccion", sale?.CLAIM_ID)
        assertNull(sale?.CLAIMED_AT)
    }
}
