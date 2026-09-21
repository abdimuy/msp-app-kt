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

// Arrendamiento del candado de SUBIDA. CORREGIDO en la ronda 3 de revisión:
// 180 s NO es un tope real de cuánto puede tardar la subida — la cuenta
// "60+60=120s" de la ronda 2 estaba incompleta, y aquí queda la cifra
// correcta con lo que SÍ acota y lo que NO.
//
// Medido en `RetrofitClientFactory.kt:121-123,142`
// (`core/network/.../RetrofitClientFactory.kt`): el perfil v2 (el que usa
// `VentasApi.crearVenta`, el POST que sube fotos) sólo fija
// `connectTimeout(60s)` y `readTimeout(60s)`. NO hay `writeTimeout`
// (queda el default de OkHttp: 10 s) ni `callTimeout` (default: 0, SIN
// límite) — ninguno de los dos aparece en todo el repo. Y en OkHttp esos
// timeouts miden INACTIVIDAD entre bytes, no un total acumulado: mientras
// el envío del cuerpo multipart siga avanciendo, aunque sea lento (una foto
// de ~3.4 MB a 10 KB/s tarda ~340 s), NINGÚN timeout salta. Es decir: el
// envío del cuerpo de la venta NO tiene un tope real hoy, y el "120 s" de
// la ronda 2 nunca lo incluyó — sólo cubría conectar + leer la respuesta,
// no mandar el cuerpo.
//
// Consecuencia que esto deja abierta, y que `markSentAndCloseEdit` existe
// para cerrar sin perder el caso en silencio (ver `LocalSaleDao.kt`): el
// arrendamiento de subida puede vencer con el POST TODAVÍA en vuelo. El
// editor toma entonces el candado y commitea, y LUEGO vuelve el 2xx con el
// cuerpo VIEJO. `markSentAndCloseEdit` lo detecta comparando `REVISION`
// contra el snapshot que el subidor tenía antes del POST, y marca
// `CORRECCION_NO_ENVIADA` en la fila en vez de pisar la corrección callada.
//
// Los 180 s de abajo NO son, entonces, un tope verdadero — no existe uno en
// el cliente hoy. Es el valor que usan las pruebas de este archivo, elegido
// como "tiempo razonable para una subida que SÍ progresa"; decidir el valor
// de producción, y sobre todo RENOVAR el arrendamiento mientras el POST
// sigue en vuelo (para que una subida lenta pero viva no dispare esta
// carrera en la práctica), es de la Task 4. Decisión del orquestador: NO
// agregar `callTimeout` — un tope total haría fallar PARA SIEMPRE una
// subida lenta que sí avanza, cambiando una carrera por una venta que nunca
// llega.
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
    // enviado/permanente/revision/divergencia) — bajarlo de 8 agruparía
    // justo lo que cada test necesita variar independientemente.
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

    /**
     * Hallazgo 1 de la ronda 3 (a medias en la ronda 2): `claimForUpload` NO
     * tenía NINGUNA prueba de frontera. Frontera exacta del arrendamiento de
     * EDICIÓN, vista desde `claimForUpload` (simétrico a lo que ya cubre
     * `claimForEdit` desde su propio lado).
     */
    @Test
    fun `claimForUpload acepta exactamente en el milisegundo en que vence un candado de EDICION`() =
        runTest {
            val claimedAt = clock.now().toEpochMilli()
            insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = claimedAt))

            clock.advance(Duration.ofMillis(EDIT_LEASE_MS))

            val rows = claimForUpload(CLAIM_ID_B)

            assertEquals("exactamente al cumplirse el arrendamiento, ya vencio", 1, rows)
        }

    @Test
    fun `claimForUpload rechaza un milisegundo antes de que venza un candado de EDICION`() =
        runTest {
            val claimedAt = clock.now().toEpochMilli()
            insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = claimedAt))

            clock.advance(Duration.ofMillis(EDIT_LEASE_MS - 1))

            val rows = claimForUpload(CLAIM_ID_B)

            assertEquals("un milisegundo antes de vencer, el candado sigue vigente", 0, rows)
        }

    /**
     * Hallazgo 1: "un reclamo de subida vencido que vuelve a reclamar otra
     * subida" — el caso explícito que se pidió cubrir. La app murió a media
     * subida (o el proceso se mató); un `claimForUpload` posterior (otro
     * intento del mismo worker, o un reintento tras un crash) debe poder
     * recuperar la venta en su propio arrendamiento, en el milisegundo
     * exacto.
     */
    @Test
    fun `claimForUpload acepta exactamente en el milisegundo en que vence un candado de SUBIDA anterior`() =
        runTest {
            val claimedAt = clock.now().toEpochMilli()
            insert(freeSale(claimId = CLAIM_ID_A, claimKind = "UPLOAD", claimedAt = claimedAt))

            clock.advance(Duration.ofMillis(UPLOAD_LEASE_MS))

            val rows = claimForUpload(CLAIM_ID_B)

            assertEquals("exactamente al cumplirse el arrendamiento de subida, ya vencio", 1, rows)
        }

    @Test
    fun `claimForUpload rechaza un milisegundo antes de que venza un candado de SUBIDA anterior`() =
        runTest {
            val claimedAt = clock.now().toEpochMilli()
            insert(freeSale(claimId = CLAIM_ID_A, claimKind = "UPLOAD", claimedAt = claimedAt))

            clock.advance(Duration.ofMillis(UPLOAD_LEASE_MS - 1))

            val rows = claimForUpload(CLAIM_ID_B)

            assertEquals(
                "un milisegundo antes de vencer, el candado de subida sigue vigente",
                0,
                rows
            )
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
    //
    // Ronda 3: markSentAndCloseEdit recibe la REVISION que el subidor tenia
    // en su snapshot ANTES del POST (revisionAtClaim). Si la REVISION
    // ACTUAL de la fila ya no coincide, una correccion se commiteo MIENTRAS
    // el POST seguia en vuelo (el arrendamiento de subida vencio antes de
    // que volviera el 2xx — ver el comentario de UPLOAD_LEASE_MS arriba) y
    // el 2xx que acaba de volver trae el cuerpo VIEJO. Ese caso se marca con
    // CORRECCION_NO_ENVIADA=1 en la MISMA sentencia, visible en la fila.

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

    // ─── CLAIM_KIND nulo o desconocido (hermano del hallazgo 6, ronda 3) ─
    //
    // Con CLAIM_ID puesto, CLAIMED_AT puesto y CLAIM_KIND nulo (o un valor
    // que no es 'EDIT' ni 'UPLOAD'), ninguna de las dos ramas del CASE se
    // cumplía y la venta quedaba retenida para siempre — el mismo argumento
    // de "una captura nunca se retiene para siempre" que ya cubre
    // CLAIMED_AT nulo, aplicado a CLAIM_KIND.

    @Test
    fun `claimForEdit acepta si CLAIM_KIND es nulo`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = null, claimedAt = now))

        val rows = claimForEdit(CLAIM_ID_B, now)

        assertEquals(
            "CLAIM_ID puesto con CLAIM_KIND nulo no debe retener la venta para siempre",
            1,
            rows
        )
    }

    @Test
    fun `claimForEdit acepta si CLAIM_KIND trae un valor desconocido`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "BOGUS", claimedAt = now))

        val rows = claimForEdit(CLAIM_ID_B, now)

        assertEquals(
            "un CLAIM_KIND que no es EDIT ni UPLOAD tampoco debe retener la venta para siempre",
            1,
            rows
        )
    }

    @Test
    fun `claimForUpload acepta si CLAIM_KIND es nulo o desconocido`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = null, claimedAt = now))

        val rows = claimForUpload(CLAIM_ID_B, now)

        assertEquals(1, rows)
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
