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

// Mismos valores que [LocalSaleClaimDaoTest] — duplicados a propósito
// (archivos de prueba independientes, sin un módulo de fixtures compartido
// para esto) en vez de compartir una constante `internal`: cada archivo debe
// poder leerse solo. Ver el comentario largo en [LocalSaleClaimDaoTest] para
// el porqué medido de `UPLOAD_LEASE_MS`.
private const val EDIT_LEASE_MS = 30 * 60 * 1000L
private const val UPLOAD_LEASE_MS = 180 * 1000L
private const val REMOTE_LEASE_MS = 180 * 1000L

private const val SALE_ID = "sale-reclamo-001"
private const val CLAIM_ID_A = "claim-uuid-aaaa"
private const val CLAIM_ID_B = "claim-uuid-bbbb"

/**
 * Cubre lo que pasa DESPUÉS de que el candado único de la fila se resuelve
 * (plan "Corregir una venta antes de que suba", "El mecanismo de la
 * carrera"): `markSentAndCloseEdit` (el subidor marca la venta enviada y
 * cierra cualquier candado), `getUploadableSales` (qué ventas ve el barrido)
 * y `getSaleClaimSnapshot` (la proyección barata que el subidor relee antes
 * del POST). El reclamo y la liberación del candado
 * (`claimForEdit`/`claimForUpload`/`releaseClaim`/`commitEditGuard`) viven
 * en [LocalSaleClaimDaoTest] — se separó en la ronda 3 porque un solo
 * archivo con las dos mitades disparaba `detekt.LargeClass`.
 *
 * El tiempo SIEMPRE viene de [FakeClock] — nunca reloj real.
 */
class LocalSaleClaimLifecycleDaoTest : RobolectricTestBase() {

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

    @Suppress("LongParameterList")
    private fun freeSale(
        saleId: String = SALE_ID,
        enviado: Boolean = false,
        lastUploadPermanent: Boolean? = null,
        claimId: String? = null,
        claimKind: String? = null,
        claimedAt: Long? = null,
        revision: Int = 0,
        correccionNoEnviada: Boolean = false
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
        REVISION = revision,
        CORRECCION_NO_ENVIADA = correccionNoEnviada
    )

    private suspend fun insert(sale: LocalSaleEntity) = database.localSaleDao().insertSale(sale)

    private suspend fun getUploadableSales(now: Long = clock.now().toEpochMilli()) =
        database.localSaleDao()
            .getUploadableSales(now, EDIT_LEASE_MS, UPLOAD_LEASE_MS, REMOTE_LEASE_MS)

    // ─── markSentAndCloseEdit ───────────────────────────────────────────
    //
    // Ronda 3: markSentAndCloseEdit recibe la REVISION que el subidor tenia
    // en su snapshot ANTES del POST (revisionAtClaim). Si la REVISION
    // ACTUAL de la fila ya no coincide, una correccion se commiteo MIENTRAS
    // el POST seguia en vuelo (el arrendamiento de subida vencio antes de
    // que volviera el 2xx — ver el comentario de UPLOAD_LEASE_MS en
    // LocalSaleClaimDaoTest.kt) y el 2xx que acaba de volver trae el cuerpo
    // VIEJO. Ese caso se marca con CORRECCION_NO_ENVIADA=1 en la MISMA
    // sentencia, visible en la fila.

    @Test
    fun `marcar enviada cierra el candado de EDICION`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = now))

        database.localSaleDao().markSentAndCloseEdit(SALE_ID, revisionAtClaim = 0)

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

        database.localSaleDao().markSentAndCloseEdit(SALE_ID, revisionAtClaim = 0)

        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertTrue(sale!!.ENVIADO)
        assertNull("el candado de subida tambien debe quedar cerrado", sale.CLAIM_ID)
        assertNull(sale.CLAIM_KIND)
        assertNull(sale.CLAIMED_AT)
    }

    @Test
    fun `marcar enviada sin candado vigente tambien deja ENVIADO en 1`() = runTest {
        insert(freeSale())

        database.localSaleDao().markSentAndCloseEdit(SALE_ID, revisionAtClaim = 0)

        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertTrue(sale!!.ENVIADO)
        assertNull(sale.CLAIM_ID)
    }

    /**
     * Ronda 3, caso 1 del bloque markSent: la REVISION que vuelve con el
     * 2xx (via el snapshot que el subidor tomo antes del POST) coincide con
     * la REVISION actual de la fila — nadie commiteo una correccion
     * mientras el POST estaba en vuelo. Sin marca de divergencia.
     */
    @Test
    fun `marcar enviada con REVISION igual no marca divergencia`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "UPLOAD", claimedAt = now, revision = 5))

        database.localSaleDao().markSentAndCloseEdit(SALE_ID, revisionAtClaim = 5)

        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertTrue(sale!!.ENVIADO)
        assertNull(sale.CLAIM_ID)
        assertFalse(
            "REVISION sin cambios: el 2xx trae el cuerpo correcto, no hay nada que señalar",
            sale.CORRECCION_NO_ENVIADA
        )
    }

    /**
     * Ronda 3, caso 2 del bloque markSent (la carrera nueva que esta ronda
     * cierra): la REVISION actual YA NO coincide con la del snapshot previo
     * al POST — el editor commiteo una correccion mientras la subida seguia
     * en vuelo, y el 2xx que acaba de volver trae el cuerpo VIEJO. Se marca
     * CORRECCION_NO_ENVIADA=1 en la MISMA sentencia que cierra el candado.
     */
    @Test
    fun `marcar enviada con REVISION distinta marca la divergencia`() = runTest {
        val now = clock.now().toEpochMilli()
        // El editor ya tomo el candado y commiteo: REVISION subio a 1 y el
        // candado de edicion (tomado despues de que vencio el de subida) ya
        // se cerro por commitEditGuard. El subidor, que arranco con
        // REVISION=0 en su snapshot, llega tarde con el 2xx.
        insert(freeSale(claimId = null, claimKind = null, claimedAt = null, revision = 1))

        database.localSaleDao().markSentAndCloseEdit(SALE_ID, revisionAtClaim = 0)

        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertTrue(
            "el 2xx prueba que el servidor tiene la venta: ENVIADO=1 de todos modos",
            sale!!.ENVIADO
        )
        assertNull(sale.CLAIM_ID)
        assertTrue(
            "REVISION cambio mientras el POST estaba en vuelo: el 2xx trae el cuerpo viejo",
            sale.CORRECCION_NO_ENVIADA
        )
    }

    /**
     * Ronda 3, caso 3: el candado de subida vencio y el editor lo tomo
     * (CLAIM_KIND paso a EDIT), pero AUN no commitea — REVISION sigue en el
     * mismo valor que el snapshot del subidor. markSentAndCloseEdit no debe
     * marcar divergencia (nada diverge todavia), y el guardado POSTERIOR
     * del editor debe fallar solo por su propio guardia: commitEditGuard ya
     * no encuentra el CLAIM_ID que el snapshot del editor tenia (
     * markSentAndCloseEdit lo cerro), asi que devuelve 0 sin ayuda extra de
     * esta prueba.
     */
    @Test
    fun `candado robado por la edicion sin commit no marca divergencia y el commit posterior falla solo`() =
        runTest {
            val editClaimId = "claim-editor-tardio"
            val now = clock.now().toEpochMilli()
            // El editor tomo el candado de EDICION (el de subida ya habia
            // vencido) pero el usuario aun no le da "guardar".
            insert(
                freeSale(claimId = editClaimId, claimKind = "EDIT", claimedAt = now, revision = 0)
            )

            // El subidor, que traia REVISION=0 en su snapshot previo al POST,
            // llega con el 2xx.
            database.localSaleDao().markSentAndCloseEdit(SALE_ID, revisionAtClaim = 0)

            val sale = database.localSaleDao().getSaleById(SALE_ID)
            assertTrue(sale!!.ENVIADO)
            assertNull("el candado del editor tambien se cierra: sea de quien sea", sale.CLAIM_ID)
            assertFalse(
                "REVISION no cambio (el editor no habia commiteado): sin marca de divergencia",
                sale.CORRECCION_NO_ENVIADA
            )

            // El editor, ajeno a que su candado ya se cerro, intenta guardar.
            val commitRows = database.localSaleDao().commitEditGuard(SALE_ID, editClaimId)
            assertEquals(
                "el guardado del editor debe fallar solo, por su propio guardia — sin ayuda extra",
                0,
                commitRows
            )
        }

    /**
     * Important #1 de la ronda 3 de revisión: la marca se CONSERVA. El
     * `ELSE CORRECCION_NO_ENVIADA` (en vez de `ELSE 0`) existe para este
     * escenario exacto:
     *
     * 1. Sube la subida A (`revisionAtClaim=0`), su arrendamiento vence con
     *    el POST en vuelo.
     * 2. El editor toma el candado y commitea: REVISION pasa a 1.
     * 3. La subida B toma el candado (`claimForUpload`) con la REVISION
     *    nueva (1) y manda un POST con el cuerpo corregido.
     * 4. Vuelve el 2xx de A (tardío): `markSentAndCloseEdit(revisionAtClaim=0)`
     *    — REVISION actual es 1 ≠ 0 → marca `CORRECCION_NO_ENVIADA=1`.
     * 5. Vuelve el 2xx de B: `markSentAndCloseEdit(revisionAtClaim=1)` —
     *    REVISION actual sigue en 1 (nadie commiteó de nuevo) → coincide, el
     *    `CASE` no toca la columna, y el `ELSE CORRECCION_NO_ENVIADA` la dejó
     *    en su valor actual: **sigue en 1**.
     *
     * Si alguien cambiara el `ELSE` a `0`, este segundo `markSent` — que en
     * sí mismo es correcto, su propia REVISION coincide — borraría la
     * evidencia de que la primera subida (A) SÍ llegó con un cuerpo viejo.
     * Antes de esta prueba, ningún test lo hubiera notado.
     */
    @Test
    fun `un markSent posterior con REVISION coincidente no borra una divergencia previa`() =
        runTest {
            val now = clock.now().toEpochMilli()
            // Estado tras el paso 4 de arriba: la subida A ya marcó la
            // divergencia, la subida B tiene el candado con la REVISION nueva.
            insert(
                freeSale(
                    claimId = CLAIM_ID_B,
                    claimKind = "UPLOAD",
                    claimedAt = now,
                    revision = 1,
                    correccionNoEnviada = true
                )
            )

            // Vuelve el 2xx de B: su propia REVISION (1) SÍ coincide.
            database.localSaleDao().markSentAndCloseEdit(SALE_ID, revisionAtClaim = 1)

            val sale = database.localSaleDao().getSaleById(SALE_ID)
            assertTrue(sale!!.ENVIADO)
            assertNull(sale.CLAIM_ID)
            assertTrue(
                "la marca de una divergencia PREVIA no debe borrarse solo porque ESTE markSent coincide",
                sale.CORRECCION_NO_ENVIADA
            )
        }

    // ─── getUploadableSales ─────────────────────────────────────────────

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

    /**
     * Hallazgo 1 de la ronda 3: `getUploadableSales` sólo tenía la frontera
     * EXACTA (arriba); faltaba el "1 ms antes" — el lado que de verdad
     * distingue `<=` de `<` en la dirección de "todavía vigente".
     */
    @Test
    fun `getUploadableSales excluye una venta con candado de EDICION que vence en 1 ms mas`() =
        runTest {
            val claimedAt = clock.now().toEpochMilli()
            insert(
                freeSale(
                    saleId = "sale-a",
                    claimId = CLAIM_ID_A,
                    claimKind = "EDIT",
                    claimedAt = claimedAt
                )
            )

            clock.advance(Duration.ofMillis(EDIT_LEASE_MS - 1))

            val uploadable = getUploadableSales()

            assertTrue(
                "un milisegundo antes de vencer, el candado de edicion sigue vigente: fuera del barrido",
                uploadable.none { it.LOCAL_SALE_ID == "sale-a" }
            )
        }

    @Test
    fun `getUploadableSales excluye una venta con candado de SUBIDA que vence en 1 ms mas`() =
        runTest {
            val claimedAt = clock.now().toEpochMilli()
            insert(
                freeSale(
                    saleId = "sale-a",
                    claimId = CLAIM_ID_A,
                    claimKind = "UPLOAD",
                    claimedAt = claimedAt
                )
            )

            clock.advance(Duration.ofMillis(UPLOAD_LEASE_MS - 1))

            val uploadable = getUploadableSales()

            assertTrue(
                "un milisegundo antes de vencer, el candado de subida sigue vigente: fuera del barrido",
                uploadable.none { it.LOCAL_SALE_ID == "sale-a" }
            )
        }

    @Test
    fun `getUploadableSales incluye una venta con CLAIM_KIND nulo o desconocido`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            freeSale(saleId = "sale-a", claimId = CLAIM_ID_A, claimKind = "BOGUS", claimedAt = now)
        )

        val uploadable = getUploadableSales(now)

        assertEquals(setOf("sale-a"), uploadable.map { it.LOCAL_SALE_ID }.toSet())
    }

    /**
     * El tercer tipo de candado (nivel 2, eje 5): una venta con un candado
     * `REMOTE` vigente tampoco debe entrar al barrido de SUBIDA — en la
     * práctica no debería ocurrir (REMOTE sólo se acuña con ENVIADO=1, y
     * este barrido es de ENVIADO=0), pero el predicado la excluye por la
     * misma defensa en profundidad que ya aplica a EDIT/UPLOAD.
     */
    @Test
    fun `getUploadableSales excluye una venta con candado REMOTE vigente`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            freeSale(saleId = "sale-a", claimId = CLAIM_ID_A, claimKind = "REMOTE", claimedAt = now)
        )
        insert(freeSale(saleId = "sale-b"))

        val uploadable = getUploadableSales(now)

        assertEquals(setOf("sale-b"), uploadable.map { it.LOCAL_SALE_ID }.toSet())
    }

    @Test
    fun `getUploadableSales incluye una venta con candado REMOTE vencido`() = runTest {
        val claimedAt = clock.now().toEpochMilli()
        insert(
            freeSale(
                saleId = "sale-a",
                claimId = CLAIM_ID_A,
                claimKind = "REMOTE",
                claimedAt = claimedAt
            )
        )

        clock.advance(Duration.ofMillis(REMOTE_LEASE_MS))

        val uploadable = getUploadableSales()

        assertEquals(setOf("sale-a"), uploadable.map { it.LOCAL_SALE_ID }.toSet())
    }

    @Test
    fun `getUploadableSales excluye una venta con candado REMOTE que vence en 1 ms mas`() =
        runTest {
            val claimedAt = clock.now().toEpochMilli()
            insert(
                freeSale(
                    saleId = "sale-a",
                    claimId = CLAIM_ID_A,
                    claimKind = "REMOTE",
                    claimedAt = claimedAt
                )
            )

            clock.advance(Duration.ofMillis(REMOTE_LEASE_MS - 1))

            val uploadable = getUploadableSales()

            assertTrue(
                "un milisegundo antes de vencer, el candado remoto sigue vigente: fuera del barrido",
                uploadable.none { it.LOCAL_SALE_ID == "sale-a" }
            )
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

            database.localSaleDao().markSentAndCloseEdit(
                SALE_ID,
                revisionAtClaim = before!!.REVISION
            )
            val after = database.localSaleDao().getSaleClaimSnapshot(SALE_ID)

            assertEquals(CLAIM_ID_A, before?.CLAIM_ID)
            assertFalse(before!!.ENVIADO)
            assertNull(
                "tras marcar enviada, el snapshot debe reflejar el candado cerrado",
                after?.CLAIM_ID
            )
            assertTrue(after!!.ENVIADO)
        }
}
