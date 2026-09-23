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
        correccionNoEnviada: Boolean = false,
        correccionRemotaPendiente: Boolean = false
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
        CORRECCION_NO_ENVIADA = correccionNoEnviada,
        CORRECCION_REMOTA_PENDIENTE = correccionRemotaPendiente
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

    // El reclamo sobre una venta YA ENVIADA, y los dos guardias del commit,
    // viven en `LocalSaleClaimNivel2DaoTest` — se separaron al nacer el nivel
    // 2 por la misma razón por la que en la ronda 3 nació
    // `LocalSaleClaimLifecycleDaoTest`: meterlos aquí dispara
    // `detekt.LargeClass`.

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
        insert(
            freeSale(
                enviado = true,
                correccionRemotaPendiente = true,
                claimId = CLAIM_ID_A,
                claimKind = "EDIT",
                claimedAt = now
            )
        )

        val rows = claimForRemote(CLAIM_ID_B, now)

        assertEquals("una correccion local en curso debe ganar sobre el worker remoto", 0, rows)
    }

    @Test
    fun `claimForRemote rechaza si hay un candado de SUBIDA vigente`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            freeSale(
                enviado = true,
                correccionRemotaPendiente = true,
                claimId = CLAIM_ID_A,
                claimKind = "UPLOAD",
                claimedAt = now
            )
        )

        val rows = claimForRemote(CLAIM_ID_B, now)

        assertEquals(0, rows)
    }

    @Test
    fun `claimForRemote NO es reentrante - rechaza otro candado REMOTE vigente`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            freeSale(
                enviado = true,
                correccionRemotaPendiente = true,
                claimId = CLAIM_ID_A,
                claimKind = "REMOTE",
                claimedAt = now
            )
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
                    correccionRemotaPendiente = true,
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
                    correccionRemotaPendiente = true,
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
                    correccionRemotaPendiente = true,
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
                correccionRemotaPendiente = true,
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
        insert(
            freeSale(
                enviado = true,
                correccionRemotaPendiente = true,
                claimId = CLAIM_ID_A,
                claimKind = "BOGUS",
                claimedAt = now
            )
        )

        val rows = claimForRemote(CLAIM_ID_B, now)

        assertEquals(
            "un CLAIM_KIND que no es EDIT/UPLOAD/REMOTE no debe retener la venta para siempre",
            1,
            rows
        )
    }
}
