package com.example.msp_app.core.database.dao.localsale

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.time.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val SALE_ID = "sale-correccion-remota-001"
private const val CLAIM_ID_A = "claim-uuid-remoto-aaaa"

// 15 minutos, el umbral del barrido de estado del servidor (eje 1: "En el
// barrido de sesión"). Duplicado a propósito, mismo criterio que el resto de
// los archivos de prueba de este paquete: cada archivo se lee solo.
private const val MAX_AGE_MS = 15 * 60 * 1000L

/**
 * Cubre lo que el plan "Corregir una venta DESPUÉS de que subió, mientras
 * siga en borrador" (nivel 2, Task A1) agrega para el estado del servidor y
 * la cola de correcciones remotas: `marcarEstadoServidor`,
 * `marcarCorreccionRemotaPendiente`, `cerrarCorreccionRemota`,
 * `marcarCorreccionRemotaTerminal`, `getVentasParaRefrescarEstado`,
 * `getVentasConCorreccionRemotaPendiente` y `getRemoteCorrectionSnapshot`. El
 * candado `REMOTE` en sí (mutua exclusión con `EDIT`/`UPLOAD`) vive en
 * [LocalSaleClaimDaoTest] y [LocalSaleClaimLifecycleDaoTest] — este archivo
 * cubre lo que pasa una vez que la fila ya tiene el candado, o no lo
 * necesita en absoluto (como `marcarEstadoServidor`, que corre fuera de
 * cualquier candado: es sólo la caché de lectura del `GET`).
 *
 * El tiempo SIEMPRE viene de [FakeClock] — nunca reloj real.
 */
class LocalSaleRemoteCorrectionDaoTest : RobolectricTestBase() {

    private lateinit var database: AppDatabase
    private val clock = FakeClock.at("2026-09-21T12:00:00Z")

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
    private fun sale(
        saleId: String = SALE_ID,
        enviado: Boolean = true,
        claimId: String? = null,
        claimKind: String? = null,
        claimedAt: Long? = null,
        revision: Int = 0,
        correccionRemotaPendiente: Boolean = false,
        correccionRemotaEstado: String? = null,
        revisionRemotaEnviada: Int? = null,
        serverSituacion: String? = null,
        serverSincronizacion: String? = null,
        serverStateAt: Long? = null
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
        CLAIM_ID = claimId,
        CLAIM_KIND = claimKind,
        CLAIMED_AT = claimedAt,
        REVISION = revision,
        CORRECCION_REMOTA_PENDIENTE = correccionRemotaPendiente,
        CORRECCION_REMOTA_ESTADO = correccionRemotaEstado,
        REVISION_REMOTA_ENVIADA = revisionRemotaEnviada,
        SERVER_SITUACION = serverSituacion,
        SERVER_SINCRONIZACION = serverSincronizacion,
        SERVER_STATE_AT = serverStateAt
    )

    private suspend fun insert(entity: LocalSaleEntity) = database.localSaleDao().insertSale(entity)

    // ─── marcarEstadoServidor ───────────────────────────────────────────

    @Test
    fun `marcarEstadoServidor persiste lo que el GET reporto`() = runTest {
        insert(sale())
        val now = clock.now().toEpochMilli()

        val rows = database.localSaleDao()
            .marcarEstadoServidor(SALE_ID, "borrador", "pendiente", 7, now)

        assertEquals(1, rows)
        val fila = database.localSaleDao().getSaleById(SALE_ID)!!
        assertEquals("borrador", fila.SERVER_SITUACION)
        assertEquals("pendiente", fila.SERVER_SINCRONIZACION)
        assertEquals(7, fila.SERVER_VERSION)
        assertEquals(now, fila.SERVER_STATE_AT)
    }

    @Test
    fun `marcarEstadoServidor no toca el candado ni la bandera de correccion pendiente`() =
        runTest {
            val now = clock.now().toEpochMilli()
            insert(
                sale(
                    claimId = CLAIM_ID_A,
                    claimKind = "REMOTE",
                    claimedAt = now,
                    correccionRemotaPendiente = true
                )
            )

            database.localSaleDao().marcarEstadoServidor(SALE_ID, "borrador", "pendiente", 3, now)

            val fila = database.localSaleDao().getSaleById(SALE_ID)!!
            assertEquals("es solo la cache de lectura del GET", CLAIM_ID_A, fila.CLAIM_ID)
            assertTrue(fila.CORRECCION_REMOTA_PENDIENTE)
        }

    // ─── marcarCorreccionRemotaPendiente ────────────────────────────────

    @Test
    fun `marcarCorreccionRemotaPendiente levanta la bandera sobre una venta enviada`() = runTest {
        insert(sale(enviado = true))

        val rows = database.localSaleDao().marcarCorreccionRemotaPendiente(SALE_ID)

        assertEquals(1, rows)
        val fila = database.localSaleDao().getSaleById(SALE_ID)!!
        assertTrue(fila.CORRECCION_REMOTA_PENDIENTE)
    }

    @Test
    fun `marcarCorreccionRemotaPendiente no hace nada sobre una venta sin enviar`() = runTest {
        insert(sale(enviado = false))

        val rows = database.localSaleDao().marcarCorreccionRemotaPendiente(SALE_ID)

        assertEquals(
            "una venta sin enviar es del nivel 1: su camino sigue siendo el POST de creación",
            0,
            rows
        )
        val fila = database.localSaleDao().getSaleById(SALE_ID)!!
        assertFalse(fila.CORRECCION_REMOTA_PENDIENTE)
    }

    // ─── cerrarCorreccionRemota ──────────────────────────────────────────

    @Test
    fun `cerrarCorreccionRemota con REVISION igual limpia la bandera y cierra el candado`() =
        runTest {
            val now = clock.now().toEpochMilli()
            insert(
                sale(
                    claimId = CLAIM_ID_A,
                    claimKind = "REMOTE",
                    claimedAt = now,
                    revision = 3,
                    correccionRemotaPendiente = true
                )
            )

            database.localSaleDao().cerrarCorreccionRemota(SALE_ID, revisionEnviada = 3)

            val fila = database.localSaleDao().getSaleById(SALE_ID)!!
            assertFalse(
                "la REVISION enviada coincide con la vigente: nada quedó sin mandar",
                fila.CORRECCION_REMOTA_PENDIENTE
            )
            assertEquals(3, fila.REVISION_REMOTA_ENVIADA)
            assertNull("el candado debe quedar cerrado", fila.CLAIM_ID)
            assertNull(fila.CLAIM_KIND)
            assertNull(fila.CLAIMED_AT)
        }

    /**
     * El caso que el dueño pidió explícitamente proteger: si corrigió otra
     * vez MIENTRAS la corrida de tres peticiones seguía en vuelo, la bandera
     * debe QUEDARSE puesta — la corrección nueva no se declara enviada
     * porque no lo fue.
     */
    @Test
    fun `cerrarCorreccionRemota con REVISION distinta deja la bandera puesta`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            sale(
                claimId = CLAIM_ID_A,
                claimKind = "REMOTE",
                claimedAt = now,
                revision = 4,
                correccionRemotaPendiente = true
            )
        )

        // La corrida arrancó con REVISION=3 en su snapshot; el dueño corrigió
        // otra vez mientras corría (REVISION subió a 4).
        database.localSaleDao().cerrarCorreccionRemota(SALE_ID, revisionEnviada = 3)

        val fila = database.localSaleDao().getSaleById(SALE_ID)!!
        assertTrue(
            "REVISION actual (4) != la enviada (3): la corrección nueva no viajó todavía",
            fila.CORRECCION_REMOTA_PENDIENTE
        )
        assertEquals(
            "REVISION_REMOTA_ENVIADA se escribe siempre, sea cual sea la comparación",
            3,
            fila.REVISION_REMOTA_ENVIADA
        )
        assertNull(
            "el candado se cierra de todos modos: los tres pasos SÍ dieron 2xx",
            fila.CLAIM_ID
        )
    }

    @Test
    fun `cerrarCorreccionRemota cierra el candado sea cual sea su dueño`() = runTest {
        val now = clock.now().toEpochMilli()
        // Nadie tiene candado vigente en este momento (el ejemplo simple:
        // la propia corrida ya lo soltó en la lectura, o nunca lo tomó
        // porque el caller la invoca tras confirmar los tres 2xx).
        insert(sale(claimId = null, claimKind = null, claimedAt = null, revision = 1))

        val rows = database.localSaleDao().cerrarCorreccionRemota(SALE_ID, revisionEnviada = 1)

        assertEquals(1, rows)
    }

    // ─── marcarCorreccionRemotaTerminal ─────────────────────────────────

    @Test
    fun `marcarCorreccionRemotaTerminal RECHAZADA_ESTADO escribe la marca, el estado del servidor y limpia el candado`() =
        runTest {
            val now = clock.now().toEpochMilli()
            insert(
                sale(
                    claimId = CLAIM_ID_A,
                    claimKind = "REMOTE",
                    claimedAt = now,
                    correccionRemotaPendiente = true
                )
            )

            database.localSaleDao().marcarCorreccionRemotaTerminal(
                SALE_ID,
                estado = "RECHAZADA_ESTADO",
                situacion = "revisada",
                sincronizacion = "pendiente",
                version = 9,
                at = now
            )

            val fila = database.localSaleDao().getSaleById(SALE_ID)!!
            assertEquals("RECHAZADA_ESTADO", fila.CORRECCION_REMOTA_ESTADO)
            assertEquals("revisada", fila.SERVER_SITUACION)
            assertEquals("pendiente", fila.SERVER_SINCRONIZACION)
            assertEquals(9, fila.SERVER_VERSION)
            assertEquals(now, fila.SERVER_STATE_AT)
            assertNull("el candado se suelta", fila.CLAIM_ID)
            assertFalse(
                "la bandera se limpia porque la marca terminal la SUSTITUYE, no porque se resolvió",
                fila.CORRECCION_REMOTA_PENDIENTE
            )
        }

    @Test
    fun `marcarCorreccionRemotaTerminal CONFLICTO deja la venta lista para LaCambioLaOficina`() =
        runTest {
            val now = clock.now().toEpochMilli()
            insert(sale(claimId = CLAIM_ID_A, claimKind = "REMOTE", claimedAt = now))

            database.localSaleDao().marcarCorreccionRemotaTerminal(
                SALE_ID,
                estado = "CONFLICTO",
                situacion = "borrador",
                sincronizacion = "pendiente",
                version = 5,
                at = now
            )

            val fila = database.localSaleDao().getSaleById(SALE_ID)!!
            assertEquals("CONFLICTO", fila.CORRECCION_REMOTA_ESTADO)
        }

    /**
     * La aserción con nombre propio del plan: "marcarCorreccionRemotaTerminal
     * deja la marca y un segundo cerrarCorreccionRemota no la borra" —
     * `cerrarCorreccionRemota` nunca escribe `CORRECCION_REMOTA_ESTADO`, así
     * que la marca terminal sobrevive a cualquier cierre posterior por
     * construcción, no por una condición que alguien tenga que acordarse de
     * mantener.
     */
    @Test
    fun `un cerrarCorreccionRemota posterior no borra una marca terminal ya escrita`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(sale(claimId = CLAIM_ID_A, claimKind = "REMOTE", claimedAt = now, revision = 2))

        database.localSaleDao().marcarCorreccionRemotaTerminal(
            SALE_ID,
            estado = "CONFLICTO",
            situacion = "borrador",
            sincronizacion = "pendiente",
            version = 5,
            at = now
        )

        // Una segunda llamada (p.ej. una corrida vieja que todavía no se
        // enteró del terminal) no debe borrar la marca.
        database.localSaleDao().cerrarCorreccionRemota(SALE_ID, revisionEnviada = 2)

        val fila = database.localSaleDao().getSaleById(SALE_ID)!!
        assertEquals(
            "la marca terminal sobrevive: cerrarCorreccionRemota nunca toca esa columna",
            "CONFLICTO",
            fila.CORRECCION_REMOTA_ESTADO
        )
    }

    // ─── getVentasParaRefrescarEstado ───────────────────────────────────

    @Test
    fun `getVentasParaRefrescarEstado excluye las ya aplicadas`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            sale(
                saleId = "sale-aplicada",
                serverSincronizacion = "aplicada",
                serverStateAt = null
            )
        )
        insert(sale(saleId = "sale-pendiente", serverSincronizacion = "pendiente"))

        val resultado = database.localSaleDao().getVentasParaRefrescarEstado(now, MAX_AGE_MS, 50)

        assertEquals(
            "aplicada es TERMINAL: nunca vuelve a cambiar y no se vuelve a pedir",
            setOf("sale-pendiente"),
            resultado.map { it.LOCAL_SALE_ID }.toSet()
        )
    }

    @Test
    fun `getVentasParaRefrescarEstado excluye las frescas`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            sale(
                saleId = "sale-fresca",
                serverSincronizacion = "pendiente",
                serverStateAt = now - (MAX_AGE_MS - 1)
            )
        )

        val resultado = database.localSaleDao().getVentasParaRefrescarEstado(now, MAX_AGE_MS, 50)

        assertTrue(
            "una lectura de hace menos de 15 minutos sigue siendo una base honesta",
            resultado.none { it.LOCAL_SALE_ID == "sale-fresca" }
        )
    }

    @Test
    fun `getVentasParaRefrescarEstado incluye exactamente en el milisegundo en que deja de ser fresca`() =
        runTest {
            val now = clock.now().toEpochMilli()
            insert(
                sale(
                    saleId = "sale-al-limite",
                    serverSincronizacion = "pendiente",
                    serverStateAt = now - MAX_AGE_MS
                )
            )

            val resultado =
                database.localSaleDao().getVentasParaRefrescarEstado(now, MAX_AGE_MS, 50)

            assertEquals(setOf("sale-al-limite"), resultado.map { it.LOCAL_SALE_ID }.toSet())
        }

    @Test
    fun `getVentasParaRefrescarEstado incluye las que nunca se leyeron`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(sale(saleId = "sale-nunca-leida", serverSincronizacion = null, serverStateAt = null))

        val resultado = database.localSaleDao().getVentasParaRefrescarEstado(now, MAX_AGE_MS, 50)

        assertEquals(
            "NULL = nunca se leyó: es justo el caso que hay que refrescar",
            setOf("sale-nunca-leida"),
            resultado.map { it.LOCAL_SALE_ID }.toSet()
        )
    }

    @Test
    fun `getVentasParaRefrescarEstado excluye una venta sin enviar`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(sale(saleId = "sale-sin-enviar", enviado = false, serverStateAt = null))

        val resultado = database.localSaleDao().getVentasParaRefrescarEstado(now, MAX_AGE_MS, 50)

        assertTrue(resultado.none { it.LOCAL_SALE_ID == "sale-sin-enviar" })
    }

    @Test
    fun `getVentasParaRefrescarEstado respeta el tope`() = runTest {
        val now = clock.now().toEpochMilli()
        (1..5).forEach { i ->
            insert(sale(saleId = "sale-$i", serverSincronizacion = null, serverStateAt = null))
        }

        val resultado = database.localSaleDao().getVentasParaRefrescarEstado(now, MAX_AGE_MS, 3)

        assertEquals(3, resultado.size)
    }

    // ─── getVentasConCorreccionRemotaPendiente ──────────────────────────

    @Test
    fun `getVentasConCorreccionRemotaPendiente solo devuelve las que tienen la bandera puesta`() =
        runTest {
            insert(sale(saleId = "sale-pendiente", correccionRemotaPendiente = true))
            insert(sale(saleId = "sale-sin-pendiente", correccionRemotaPendiente = false))

            val resultado = database.localSaleDao().getVentasConCorreccionRemotaPendiente()

            assertEquals(
                setOf("sale-pendiente"),
                resultado.map { it.LOCAL_SALE_ID }.toSet()
            )
        }

    // ─── getRemoteCorrectionSnapshot ─────────────────────────────────────

    @Test
    fun `getRemoteCorrectionSnapshot refleja el estado real de la fila`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            sale(
                claimId = CLAIM_ID_A,
                claimKind = "REMOTE",
                claimedAt = now,
                revision = 5,
                correccionRemotaPendiente = true
            )
        )

        val snapshot = database.localSaleDao().getRemoteCorrectionSnapshot(SALE_ID)

        assertNotNull(snapshot)
        assertEquals(CLAIM_ID_A, snapshot!!.CLAIM_ID)
        assertEquals(5, snapshot.REVISION)
        assertTrue(snapshot.CORRECCION_REMOTA_PENDIENTE)
    }

    @Test
    fun `getRemoteCorrectionSnapshot cambia tras cerrarCorreccionRemota`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            sale(
                claimId = CLAIM_ID_A,
                claimKind = "REMOTE",
                claimedAt = now,
                revision = 2,
                correccionRemotaPendiente = true
            )
        )
        val before = database.localSaleDao().getRemoteCorrectionSnapshot(SALE_ID)

        database.localSaleDao().cerrarCorreccionRemota(SALE_ID, revisionEnviada = before!!.REVISION)
        val after = database.localSaleDao().getRemoteCorrectionSnapshot(SALE_ID)

        assertTrue(before.CORRECCION_REMOTA_PENDIENTE)
        assertNull("tras cerrar, el snapshot refleja el candado cerrado", after?.CLAIM_ID)
        assertFalse(after!!.CORRECCION_REMOTA_PENDIENTE)
    }
}
