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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Duplicados a propósito, igual que en [LocalSaleClaimDaoTest] y
// [LocalSaleClaimLifecycleDaoTest]: cada archivo de prueba debe poder leerse
// solo, sin saltar a un tercero.
private const val EDIT_LEASE_MS = 30 * 60 * 1000L
private const val UPLOAD_LEASE_MS = 180 * 1000L
private const val REMOTE_LEASE_MS = 180 * 1000L

private const val SALE_ID = "sale-latido-001"
private const val CLAIM_SUBIDOR = "claim-uuid-subidor"
private const val CLAIM_OTRO = "claim-uuid-otro"

/**
 * `renewClaim` — el LATIDO del subidor (Task 4 del plan "Corregir una venta
 * antes de que suba"). Renueva `CLAIMED_AT` del candado de SUBIDA propio
 * mientras el `POST` sigue en vuelo, para que una subida lenta pero VIVA no
 * deje caducar su propio candado: el cliente HTTP no tiene tope total (ver
 * [LocalSaleClaimLeases.UPLOAD_HEARTBEAT_MS]).
 *
 * **Generalizado en el nivel 2 (Task A1) desde `renewUploadClaim`:** el
 * método viejo fijaba `CLAIM_KIND = 'UPLOAD'` en su `WHERE`; el nuevo sólo
 * exige `CLAIM_ID = :claimId` — un `claimId` es un UUID fresco por cada
 * reclamo, así que coincidir con el de la fila YA prueba que quien llama es
 * el dueño legítimo, sea cual sea el tipo de candado. El nombre de este
 * archivo se conserva (las pruebas de este archivo son las del LATIDO DEL
 * SUBIDOR específicamente) — la cobertura del latido genérico sobre
 * cualquier tipo de candado vive en la prueba
 * `renewClaim renueva cualquier tipo de candado por CLAIM_ID, el tipo no
 * importa` más abajo, que reemplaza a la vieja
 * `no renueva un candado de EDICION, aunque el CLAIM_ID coincida` — esa
 * aserción era válida bajo la semántica VIEJA (filtro por `CLAIM_KIND`) y ya
 * no lo es bajo la nueva.
 *
 * Lo que estas pruebas defienden, y es lo delicado: el latido **renueva**,
 * nunca **reclama**. Sobre un candado que ya no es el suyo (`CLAIM_ID`
 * distinto) no escribe nada — si lo hiciera, le robaría la fila a quien la
 * tiene.
 *
 * El tiempo SIEMPRE viene de [FakeClock] — nunca reloj real.
 */
class LocalSaleUploadHeartbeatDaoTest : RobolectricTestBase() {

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

    private fun sale(claimId: String? = null, claimKind: String? = null, claimedAt: Long? = null) =
        LocalSaleEntity(
            LOCAL_SALE_ID = SALE_ID,
            NOMBRE_CLIENTE = "Leticia Gomez Andrade",
            FECHA_VENTA = "2026-09-19T09:15:00Z",
            LATITUD = 20.676667,
            LONGITUD = -103.347222,
            DIRECCION = "Calle Hidalgo 210",
            PARCIALIDAD = 600.0,
            ENGANCHE = 400.0,
            TELEFONO = "3339876543",
            FREC_PAGO = "SEMANAL",
            AVAL_O_RESPONSABLE = "Ernesto Gomez Andrade",
            NOTA = null,
            DIA_COBRANZA = "JUEVES",
            PRECIO_TOTAL = 5400.0,
            TIEMPO_A_CORTO_PLAZOMESES = 6,
            MONTO_A_CORTO_PLAZO = 5000.0,
            MONTO_DE_CONTADO = 4600.0,
            ENVIADO = false,
            CLAIM_ID = claimId,
            CLAIM_KIND = claimKind,
            CLAIMED_AT = claimedAt
        )

    @Test
    fun `el subidor renueva su propio candado de subida`() = runTest {
        val t0 = clock.now().toEpochMilli()
        database.localSaleDao().insertSale(
            sale(claimId = CLAIM_SUBIDOR, claimKind = "UPLOAD", claimedAt = t0)
        )

        clock.advance(Duration.ofMillis(60_000))
        val t1 = clock.now().toEpochMilli()
        val filas = database.localSaleDao().renewClaim(SALE_ID, CLAIM_SUBIDOR, t1)

        val fila = database.localSaleDao().getSaleById(SALE_ID)!!
        assertEquals(1, filas)
        assertEquals("CLAIMED_AT debe avanzar al instante del latido", t1, fila.CLAIMED_AT)
        assertEquals("el dueño del candado no cambia", CLAIM_SUBIDOR, fila.CLAIM_ID)
        assertEquals("UPLOAD", fila.CLAIM_KIND)
    }

    @Test
    fun `renovar mantiene la venta fuera del barrido pasado el arrendamiento original`() = runTest {
        val t0 = clock.now().toEpochMilli()
        database.localSaleDao().insertSale(
            sale(claimId = CLAIM_SUBIDOR, claimKind = "UPLOAD", claimedAt = t0)
        )

        // Dos latidos a un tercio del arrendamiento, y luego miramos en un
        // instante que ya pasó del arrendamiento contado desde t0.
        clock.advance(Duration.ofMillis(UPLOAD_LEASE_MS / 3))
        database.localSaleDao()
            .renewClaim(SALE_ID, CLAIM_SUBIDOR, clock.now().toEpochMilli())
        clock.advance(Duration.ofMillis(UPLOAD_LEASE_MS / 3))
        database.localSaleDao()
            .renewClaim(SALE_ID, CLAIM_SUBIDOR, clock.now().toEpochMilli())
        clock.advance(Duration.ofMillis(UPLOAD_LEASE_MS / 3 + 1))

        val ahora = clock.now().toEpochMilli()
        assertTrue(
            "sin latido la venta ya estaría libre; con latido el candado sigue vivo",
            ahora - t0 > UPLOAD_LEASE_MS
        )
        val subibles = database.localSaleDao()
            .getUploadableSales(ahora, EDIT_LEASE_MS, UPLOAD_LEASE_MS, REMOTE_LEASE_MS)
        assertTrue(
            "una subida viva no debe soltar su venta al barrido",
            subibles.none { it.LOCAL_SALE_ID == SALE_ID }
        )
        assertEquals(
            "y el editor tampoco puede quitársela",
            0,
            database.localSaleDao()
                .claimForEdit(SALE_ID, CLAIM_OTRO, ahora, UPLOAD_LEASE_MS, REMOTE_LEASE_MS)
        )
    }

    @Test
    fun `no renueva un candado ajeno - no se le roba la fila al editor`() = runTest {
        val t0 = clock.now().toEpochMilli()
        database.localSaleDao().insertSale(
            sale(claimId = CLAIM_OTRO, claimKind = "UPLOAD", claimedAt = t0)
        )

        clock.advance(Duration.ofMillis(60_000))
        val filas = database.localSaleDao()
            .renewClaim(SALE_ID, CLAIM_SUBIDOR, clock.now().toEpochMilli())

        val fila = database.localSaleDao().getSaleById(SALE_ID)!!
        assertEquals("0 filas: el candado ya no es del subidor", 0, filas)
        assertEquals("la fila no se toca", t0, fila.CLAIMED_AT)
        assertEquals(CLAIM_OTRO, fila.CLAIM_ID)
    }

    /**
     * Ronda 2 de revisión, nivel 2 (Task A1): reemplaza a la vieja
     * `no renueva un candado de EDICION, aunque el CLAIM_ID coincida`, que
     * probaba la semántica ANTERIOR (`renewUploadClaim` filtraba por
     * `CLAIM_KIND = 'UPLOAD'`). El método generalizado (`renewClaim`) ya no
     * filtra por tipo — sólo por `CLAIM_ID`, que es un UUID fresco por
     * reclamo y por sí solo ya prueba la propiedad — porque el candado
     * `REMOTE` del nivel 2 necesita el MISMO latido y no hay razón para
     * reimplementarlo. Esta prueba documenta el cambio explícitamente: con
     * el `CLAIM_ID` correcto, `renewClaim` renueva un candado `EDIT` igual
     * que uno `UPLOAD` o uno `REMOTE`.
     */
    @Test
    fun `renewClaim renueva cualquier tipo de candado por CLAIM_ID, el tipo no importa`() =
        runTest {
            listOf("EDIT", "UPLOAD", "REMOTE").forEach { kind ->
                val t0 = clock.now().toEpochMilli()
                database.localSaleDao().insertSale(
                    sale(claimId = CLAIM_SUBIDOR, claimKind = kind, claimedAt = t0)
                )

                clock.advance(Duration.ofMillis(60_000))
                val t1 = clock.now().toEpochMilli()
                val filas = database.localSaleDao().renewClaim(SALE_ID, CLAIM_SUBIDOR, t1)

                val fila = database.localSaleDao().getSaleById(SALE_ID)!!
                assertEquals("renewClaim debe renovar un candado $kind por CLAIM_ID", 1, filas)
                assertEquals(t1, fila.CLAIMED_AT)
                assertEquals(kind, fila.CLAIM_KIND)

                database.localSaleDao().releaseClaim(SALE_ID, CLAIM_SUBIDOR)
            }
        }

    @Test
    fun `no renueva una fila sin candado - una subida terminada no revive nada`() = runTest {
        database.localSaleDao().insertSale(sale())

        clock.advance(Duration.ofMillis(60_000))
        val filas = database.localSaleDao()
            .renewClaim(SALE_ID, CLAIM_SUBIDOR, clock.now().toEpochMilli())

        val fila = database.localSaleDao().getSaleById(SALE_ID)!!
        assertEquals(0, filas)
        assertEquals(null, fila.CLAIMED_AT)
        assertEquals(null, fila.CLAIM_ID)
    }
}
