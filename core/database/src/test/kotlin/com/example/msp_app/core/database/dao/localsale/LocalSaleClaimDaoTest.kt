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
import org.junit.Assert.assertNull
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
// el envío del cuerpo multipart siga avanzando, aunque sea lento (una foto
// de ~3.4 MB a 10 KB/s tarda ~340 s), NINGÚN timeout salta. Es decir: el
// envío del cuerpo de la venta NO tiene un tope real hoy, y el "120 s" de
// la ronda 2 nunca lo incluyó — sólo cubría conectar + leer la respuesta,
// no mandar el cuerpo.
//
// Consecuencia que esto deja abierta, y que `markSentAndCloseEdit` existe
// para cerrar sin perder el caso en silencio (ver `LocalSaleDao.kt` y
// `LocalSaleClaimLifecycleDaoTest.kt`): el arrendamiento de subida puede
// vencer con el POST TODAVÍA en vuelo. El editor toma entonces el candado y
// commitea, y LUEGO vuelve el 2xx con el cuerpo VIEJO. `markSentAndCloseEdit`
// lo detecta comparando `REVISION` contra el snapshot que el subidor tenía
// antes del POST, y marca `CORRECCION_NO_ENVIADA` en la fila en vez de pisar
// la corrección callada.
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

// Arrendamiento del candado de CORRECCIÓN REMOTA (nivel 2, eje 5): mismo
// valor y mismo argumento que UPLOAD_LEASE_MS — tres peticiones sobre una
// red mala tardan más que una sola, pero no hay razón para que tarden MÁS
// que subir un cuerpo con fotos.
private const val REMOTE_LEASE_MS = 180 * 1000L

private const val SALE_ID = "sale-reclamo-001"
private const val CLAIM_ID_A = "claim-uuid-aaaa"
private const val CLAIM_ID_B = "claim-uuid-bbbb"

/**
 * Cubre el reclamo/liberación del candado único de la fila (plan "Corregir
 * una venta antes de que suba", "El mecanismo de la carrera" + ronda 2 de
 * revisión: la fila tiene UN candado que puede tomar la edición o la
 * subida, nunca las dos — mutua exclusión por construcción, una sola
 * columna `CLAIM_ID`): `claimForEdit`, `claimForUpload`, `releaseClaim`,
 * `commitEditGuard`. Lo que pasa DESPUÉS de que el candado se resuelve
 * (`markSentAndCloseEdit`, `getUploadableSales`, `getSaleClaimSnapshot`)
 * vive en [LocalSaleClaimLifecycleDaoTest] — se separó en la ronda 3 porque
 * un solo archivo con las dos mitades disparaba `detekt.LargeClass`.
 *
 * Cada método del DAO es un solo `UPDATE`, así que la atomicidad la da
 * SQLite, no un mutex de Kotlin. El tiempo SIEMPRE viene de [FakeClock] —
 * nunca reloj real, para que "el candado venció" sea determinista.
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
        database.localSaleDao().claimForEdit(
            SALE_ID,
            claimId,
            now,
            UPLOAD_LEASE_MS,
            REMOTE_LEASE_MS
        )

    private suspend fun claimForUpload(claimId: String, now: Long = clock.now().toEpochMilli()) =
        database.localSaleDao().claimForUpload(
            SALE_ID,
            claimId,
            now,
            EDIT_LEASE_MS,
            UPLOAD_LEASE_MS,
            REMOTE_LEASE_MS
        )

    private suspend fun claimForRemote(claimId: String, now: Long = clock.now().toEpochMilli()) =
        database.localSaleDao().claimForRemote(
            SALE_ID,
            claimId,
            now,
            EDIT_LEASE_MS,
            UPLOAD_LEASE_MS,
            REMOTE_LEASE_MS
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

    /**
     * Task 3, decisión del orquestador: `claimForEdit` es REENTRANTE para un
     * candado `EDIT` vivo — lo toma de nuevo con un `claimId` FRESCO en vez
     * de bloquear. En el alcance de este plan (un teléfono, una venta que
     * nunca salió) un `EDIT` vivo sólo puede ser una sesión anterior del
     * editor en el MISMO teléfono: si la app murió con el editor abierto, el
     * dueño no debe quedar 30 min sin poder corregir su propia venta.
     * Consecuencia (cubierta en
     * `claimForEdit reentrante invalida el claimId de la sesion anterior, su commitEditGuard no escribe nada`
     * más abajo): la sesión vieja pierde su candado.
     */
    @Test
    fun `claimForEdit toma de nuevo un candado de EDICION vigente, con un claimId fresco`() =
        runTest {
            val now = clock.now().toEpochMilli()
            insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = now))

            val rows = claimForEdit(CLAIM_ID_B, now)

            assertEquals("un candado EDIT vivo es reentrante, no bloquea", 1, rows)
            val sale = database.localSaleDao().getSaleById(SALE_ID)
            assertEquals("el candado ahora es el de la sesion nueva", CLAIM_ID_B, sale?.CLAIM_ID)
            assertEquals("EDIT", sale?.CLAIM_KIND)
            assertEquals(now, sale?.CLAIMED_AT)
        }

    /**
     * La consecuencia exacta que pide el orquestador: la sesión vieja no se
     * entera de que perdió la fila hasta que intenta commitear — y en ese
     * momento su guardia (`commitEditGuard`) falla solo, sin escribir nada,
     * porque `CLAIM_ID` ya no es el suyo.
     */
    @Test
    fun `claimForEdit reentrante invalida el claimId de la sesion anterior, su commitEditGuard no escribe nada`() =
        runTest {
            val now = clock.now().toEpochMilli()
            insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = now))

            // La app "muere" y se reabre: reclama de nuevo, con un claimId distinto.
            val rows = claimForEdit(CLAIM_ID_B, now)
            assertEquals(1, rows)

            // La sesión VIEJA (CLAIM_ID_A), que nunca se enteró de la muerte,
            // intenta commitear con su claimId original.
            val guardRows = database.localSaleDao().commitEditGuard(SALE_ID, CLAIM_ID_A)

            assertEquals(
                "el claimId de la sesion vieja ya no es el vigente: 0 filas, nada se escribe",
                0,
                guardRows
            )
            val sale = database.localSaleDao().getSaleById(SALE_ID)
            assertEquals(
                "el candado de la sesion NUEVA sigue intacto, la vieja no lo tocó",
                CLAIM_ID_B,
                sale?.CLAIM_ID
            )
            assertEquals(0, sale?.REVISION)
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
     * Task 3: `claimForEdit` ya NO tiene frontera de vencimiento para un
     * candado `EDIT` — es reentrante sin condición. Estas dos pruebas
     * confirman que el paso del tiempo es IRRELEVANTE para este método
     * (antes de Task 3 sí importaba; ver `claimForUpload` más abajo para el
     * mismo candado EDIT SÍ importándole la frontera a QUIEN llama desde el
     * otro lado).
     */
    @Test
    fun `claimForEdit acepta un candado de EDICION mucho antes de que venza (reentrante)`() =
        runTest {
            val claimedAt = clock.now().toEpochMilli()
            insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = claimedAt))

            clock.advance(Duration.ofMillis(EDIT_LEASE_MS))

            val rows = claimForEdit(CLAIM_ID_B)

            assertEquals(1, rows)
        }

    @Test
    fun `claimForEdit acepta un candado de EDICION recien tomado (reentrante, sin esperar nada)`() =
        runTest {
            val claimedAt = clock.now().toEpochMilli()
            insert(freeSale(claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = claimedAt))

            clock.advance(Duration.ofMillis(EDIT_LEASE_MS - 1))

            val rows = claimForEdit(CLAIM_ID_B)

            assertEquals(
                "un candado EDIT vivo nunca bloquea, sin importar cuanto le falte",
                1,
                rows
            )
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

    // ─── CLAIM_KIND nulo/vacío/desconocido en el reclamo (hermano del
    // hallazgo 6, ronda 3) ─────────────────────────────────────────────
    //
    // Con CLAIM_ID puesto, CLAIMED_AT puesto y CLAIM_KIND nulo, vacío, o un
    // valor que no es 'EDIT' ni 'UPLOAD', ninguna de las dos ramas del CASE
    // se cumplía y la venta quedaba retenida para siempre — el mismo
    // argumento de "una captura nunca se retiene para siempre" que ya cubre
    // CLAIMED_AT nulo, aplicado a CLAIM_KIND. (El caso de `getUploadableSales`
    // vive en [LocalSaleClaimLifecycleDaoTest].)

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

    /**
     * Minor de la ronda 3: cadena VACÍA, no sólo `NULL` y un valor no vacío
     * ("BOGUS"). Distingue de `NULL` porque `COALESCE(CLAIM_KIND, '')`
     * convierte un `CLAIM_KIND` nulo en `''` — sin esta prueba, un
     * `COALESCE(CLAIM_KIND, 'EDIT')` (que también "arregla" el caso nulo,
     * pero mal, tratando el nulo como si fuera edición) hubiera pasado las
     * otras dos pruebas de fallback sin que ninguna lo notara.
     */
    @Test
    fun `claimForEdit acepta si CLAIM_KIND es cadena vacia`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "", claimedAt = now))

        val rows = claimForEdit(CLAIM_ID_B, now)

        assertEquals(
            "CLAIM_KIND en cadena vacia tampoco debe retener la venta para siempre",
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

    // ─── El tercer tipo de candado: REMOTE (plan nivel 2, eje 5) ─────────
    //
    // Las nueve combinaciones {libre, EDIT vivo, EDIT vencido, UPLOAD vivo,
    // UPLOAD vencido, REMOTE vivo, REMOTE vencido, CLAIM_KIND nulo,
    // CLAIM_KIND desconocido} × {claimForEdit, claimForUpload, claimForRemote}
    // ya cubiertas para EDIT/UPLOAD arriba (libre, EDIT vivo/vencido, UPLOAD
    // vivo/vencido, CLAIM_KIND nulo/desconocido). Este bloque cubre lo que
    // falta: REMOTE vivo/vencido contra los tres métodos, y claimForRemote
    // contra las nueve combinaciones.

    /**
     * `enviado = false` (el default de `freeSale`), no `true`: `claimForEdit`
     * sólo puede aceptar filas con `ENVIADO = 0`, así que probar el rechazo
     * por `REMOTE` sobre `ENVIADO = 1` habría rechazado por la razón
     * EQUIVOCADA — la venta ya enviada, no el candado — y hubiera pasado
     * igual con la rama `REMOTE` del predicado borrada. Un `REMOTE` sobre
     * `ENVIADO = 0` no ocurre en producción (REMOTE sólo se acuña sobre
     * `ENVIADO = 1`), pero el predicado lo reconoce por defensa en
     * profundidad y ESTA prueba es la que verifica esa rama en concreto.
     */
    @Test
    fun `claimForEdit rechaza si hay un candado REMOTE vigente`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "REMOTE", claimedAt = now))

        val rows = claimForEdit(CLAIM_ID_B, now)

        assertEquals(
            "una correccion remota en curso debe ganar: el editor no puede abrirse encima",
            0,
            rows
        )
    }

    @Test
    fun `claimForEdit acepta exactamente en el milisegundo en que vence un candado REMOTE`() =
        runTest {
            val claimedAt = clock.now().toEpochMilli()
            insert(
                freeSale(
                    claimId = CLAIM_ID_A,
                    claimKind = "REMOTE",
                    claimedAt = claimedAt
                )
            )

            clock.advance(Duration.ofMillis(REMOTE_LEASE_MS))

            val rows = claimForEdit(CLAIM_ID_B)

            assertEquals(
                "exactamente al cumplirse el arrendamiento remoto, ya vencio",
                1,
                rows
            )
        }

    @Test
    fun `claimForEdit rechaza un milisegundo antes de que venza un candado REMOTE`() = runTest {
        val claimedAt = clock.now().toEpochMilli()
        insert(
            freeSale(claimId = CLAIM_ID_A, claimKind = "REMOTE", claimedAt = claimedAt)
        )

        clock.advance(Duration.ofMillis(REMOTE_LEASE_MS - 1))

        val rows = claimForEdit(CLAIM_ID_B)

        assertEquals("un milisegundo antes de vencer, el candado remoto sigue vigente", 0, rows)
    }

    @Test
    fun `claimForUpload rechaza si hay un candado REMOTE vigente`() = runTest {
        val now = clock.now().toEpochMilli()
        // ENVIADO=0: sólo así claimForUpload puede aceptar la fila en
        // primer lugar. Un candado REMOTE sobre ENVIADO=0 no ocurre en
        // producción (REMOTE sólo se acuña con ENVIADO=1), pero el
        // predicado lo reconoce por defensa en profundidad.
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "REMOTE", claimedAt = now))

        val rows = claimForUpload(CLAIM_ID_B, now)

        assertEquals(0, rows)
    }

    @Test
    fun `claimForUpload acepta si un candado REMOTE anterior vencio`() = runTest {
        val claimedAt = clock.now().toEpochMilli()
        insert(freeSale(claimId = CLAIM_ID_A, claimKind = "REMOTE", claimedAt = claimedAt))

        clock.advance(Duration.ofMillis(REMOTE_LEASE_MS))

        val rows = claimForUpload(CLAIM_ID_B)

        assertEquals(1, rows)
    }

    // ─── claimForRemote ─────────────────────────────────────────────────

    @Test
    fun `claimForRemote toma el candado sobre una venta ya enviada y libre`() = runTest {
        insert(freeSale(enviado = true))

        val rows = claimForRemote(CLAIM_ID_A)

        assertEquals(1, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals(CLAIM_ID_A, sale?.CLAIM_ID)
        assertEquals("REMOTE", sale?.CLAIM_KIND)
        assertEquals(clock.now().toEpochMilli(), sale?.CLAIMED_AT)
    }

    @Test
    fun `claimForRemote rechaza una venta que todavia no se envio`() = runTest {
        insert(freeSale(enviado = false))

        val rows = claimForRemote(CLAIM_ID_A)

        assertEquals(
            "una venta sin enviar no tiene correccion remota que correr: su camino sigue siendo el POST",
            0,
            rows
        )
    }

    @Test
    fun `claimForRemote rechaza si hay un candado de EDICION vigente`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(enviado = true, claimId = CLAIM_ID_A, claimKind = "EDIT", claimedAt = now))

        val rows = claimForRemote(CLAIM_ID_B, now)

        assertEquals("una correccion local en curso debe ganar sobre el worker remoto", 0, rows)
    }

    @Test
    fun `claimForRemote rechaza si hay un candado de SUBIDA vigente`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            freeSale(enviado = true, claimId = CLAIM_ID_A, claimKind = "UPLOAD", claimedAt = now)
        )

        val rows = claimForRemote(CLAIM_ID_B, now)

        assertEquals(0, rows)
    }

    @Test
    fun `claimForRemote NO es reentrante - rechaza otro candado REMOTE vigente`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            freeSale(enviado = true, claimId = CLAIM_ID_A, claimKind = "REMOTE", claimedAt = now)
        )

        val rows = claimForRemote(CLAIM_ID_B, now)

        assertEquals(
            "a diferencia de EDIT, REMOTE no es reentrante: dos corridas no deben pisarse",
            0,
            rows
        )
    }

    @Test
    fun `claimForRemote acepta exactamente en el milisegundo en que vence un candado EDIT`() =
        runTest {
            val claimedAt = clock.now().toEpochMilli()
            insert(
                freeSale(
                    enviado = true,
                    claimId = CLAIM_ID_A,
                    claimKind = "EDIT",
                    claimedAt = claimedAt
                )
            )

            clock.advance(Duration.ofMillis(EDIT_LEASE_MS))

            val rows = claimForRemote(CLAIM_ID_B)

            assertEquals(1, rows)
        }

    @Test
    fun `claimForRemote acepta exactamente en el milisegundo en que vence un candado UPLOAD`() =
        runTest {
            val claimedAt = clock.now().toEpochMilli()
            insert(
                freeSale(
                    enviado = true,
                    claimId = CLAIM_ID_A,
                    claimKind = "UPLOAD",
                    claimedAt = claimedAt
                )
            )

            clock.advance(Duration.ofMillis(UPLOAD_LEASE_MS))

            val rows = claimForRemote(CLAIM_ID_B)

            assertEquals(1, rows)
        }

    @Test
    fun `claimForRemote acepta exactamente en el milisegundo en que vence otro candado REMOTE`() =
        runTest {
            val claimedAt = clock.now().toEpochMilli()
            insert(
                freeSale(
                    enviado = true,
                    claimId = CLAIM_ID_A,
                    claimKind = "REMOTE",
                    claimedAt = claimedAt
                )
            )

            clock.advance(Duration.ofMillis(REMOTE_LEASE_MS))

            val rows = claimForRemote(CLAIM_ID_B)

            assertEquals("exactamente al cumplirse el arrendamiento remoto, ya vencio", 1, rows)
        }

    @Test
    fun `claimForRemote rechaza un milisegundo antes de que venza otro candado REMOTE`() = runTest {
        val claimedAt = clock.now().toEpochMilli()
        insert(
            freeSale(
                enviado = true,
                claimId = CLAIM_ID_A,
                claimKind = "REMOTE",
                claimedAt = claimedAt
            )
        )

        clock.advance(Duration.ofMillis(REMOTE_LEASE_MS - 1))

        val rows = claimForRemote(CLAIM_ID_B)

        assertEquals("un milisegundo antes de vencer, el candado remoto sigue vigente", 0, rows)
    }

    @Test
    fun `claimForRemote acepta si CLAIM_KIND es nulo o desconocido`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(freeSale(enviado = true, claimId = CLAIM_ID_A, claimKind = "BOGUS", claimedAt = now))

        val rows = claimForRemote(CLAIM_ID_B, now)

        assertEquals(
            "un CLAIM_KIND que no es EDIT/UPLOAD/REMOTE no debe retener la venta para siempre",
            1,
            rows
        )
    }
}
