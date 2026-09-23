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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

// Los mismos arrendamientos que usa `LocalSaleClaimDaoTest`, duplicados a
// propósito por la misma razón que ahí: que cada archivo de prueba se pueda
// leer solo, sin saltar a un tercero. La fuente única de PRODUCCIÓN es
// `LocalSaleClaimLeases`.
private const val EDIT_LEASE_MS = 30 * 60 * 1000L
private const val UPLOAD_LEASE_MS = 180 * 1000L
private const val REMOTE_LEASE_MS = 180 * 1000L

private const val SALE_ID = "sale-nivel2-001"
private const val CLAIM_ID_A = "claim-uuid-aaaa"
private const val CLAIM_ID_B = "claim-uuid-bbbb"

/**
 * Lo que el nivel 2 ("Corregir una venta DESPUÉS de que subió, mientras siga
 * en borrador") le cambió al candado: `claimForEdit` sobre una venta con
 * `ENVIADO = 1`, y el guardia nuevo [LocalSaleDao.commitEditGuardEnviada].
 *
 * Vive aparte de [LocalSaleClaimDaoTest] por la misma razón por la que en la
 * ronda 3 nació [LocalSaleClaimLifecycleDaoTest]: un solo archivo con todo
 * dispara `detekt.LargeClass`.
 *
 * Las dos reglas que este archivo blinda, y por qué duelen si se pierden:
 *
 * 1. **`claimForEdit` es el espejo SQL de `evaluarCorregibilidad`** (el
 *    dominio, en `:feature:ventaCorreccion`): deja pasar exactamente los dos
 *    estados corregibles, `Corregible` y `CorregibleEnviada`, y ninguno más.
 *    Si los dos se despegan, el síntoma es cruel y silencioso: la UI pinta el
 *    botón "Corregir venta", el usuario lo toca, y el candado falla sin una
 *    razón que mostrar.
 * 2. **`commitEditGuardEnviada` NO toca `ENVIADO`.** Bajarlo devolvería la
 *    venta a la cola de ALTA con su `Idempotency-Key` original: el servidor
 *    contestaría con la respuesta que ya tenía guardada, no actualizaría
 *    nada, y el teléfono se quedaría creyendo que reenvió. La corrección se
 *    perdería en silencio.
 *
 * El tiempo SIEMPRE viene de [FakeClock] — nunca reloj real.
 */
class LocalSaleClaimNivel2DaoTest : RobolectricTestBase() {

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

    // Fixture propio (duplicado a propósito — convención del repo), con las
    // dos columnas del nivel 2 que `LocalSaleClaimDaoTest` no necesita
    // sembrar.
    @Suppress("LongParameterList")
    private fun freeSale(
        enviado: Boolean = false,
        lastUploadPermanent: Boolean? = null,
        claimId: String? = null,
        claimKind: String? = null,
        claimedAt: Long? = null,
        revision: Int = 0,
        correccionNoEnviada: Boolean = false,
        correccionRemotaPendiente: Boolean = false,
        correccionRemotaEstado: String? = null
    ) = LocalSaleEntity(
        LOCAL_SALE_ID = SALE_ID,
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
        CORRECCION_REMOTA_PENDIENTE = correccionRemotaPendiente,
        CORRECCION_REMOTA_ESTADO = correccionRemotaEstado
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

    private suspend fun claimForRemote(claimId: String, now: Long = clock.now().toEpochMilli()) =
        database.localSaleDao().claimForRemote(
            SALE_ID,
            claimId,
            now,
            EDIT_LEASE_MS,
            UPLOAD_LEASE_MS,
            REMOTE_LEASE_MS
        )

    // ─── claimForEdit sobre una venta YA ENVIADA ────────────────────────

    /**
     * **El caso que abrió el nivel 2**, y el que más cambió: hasta el nivel 1 este `WHERE`
     * exigía `ENVIADO = 0` a secas y una venta ya subida no se podía reclamar NUNCA. Ahora una
     * venta enviada y limpia SÍ acepta el candado — el servidor la tiene en `borrador` y lo que
     * se corrija viaja por la cola de correcciones remotas.
     *
     * Este predicado es el espejo SQL de `evaluarCorregibilidad`: aquí acepta exactamente los
     * dos estados corregibles (`Corregible` y `CorregibleEnviada`). Si vuelve a exigir
     * `ENVIADO = 0`, la UI sigue pintando el botón "Corregir venta" (el dominio sí dice
     * `CorregibleEnviada`) y el candado lo rechaza sin una razón que mostrar — el síntoma cruel
     * y silencioso que el KDoc del DAO nombra.
     */
    @Test
    fun `claimForEdit ACEPTA una venta ya enviada y limpia`() = runTest {
        insert(freeSale(enviado = true))

        val rows = claimForEdit(CLAIM_ID_A)

        assertEquals("una venta enviada en borrador SI se puede corregir", 1, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals(CLAIM_ID_A, sale?.CLAIM_ID)
        assertEquals("EDIT", sale?.CLAIM_KIND)
        assertEquals("reclamar para editar NUNCA toca ENVIADO", true, sale?.ENVIADO)
    }

    /**
     * Enviada PERO con la divergencia ya marcada: el servidor tiene la venta y no su última
     * corrección (`LaRevisaLaOficina`). Encimar otra corrección encima de una que nadie
     * concilió es justo lo que no se puede.
     */
    @Test
    fun `claimForEdit rechaza una venta enviada con CORRECCION_NO_ENVIADA en 1`() = runTest {
        insert(freeSale(enviado = true, correccionNoEnviada = true))

        val rows = claimForEdit(CLAIM_ID_A)

        assertEquals("el servidor tiene la venta pero no su ultima correccion", 0, rows)
        assertNull(
            "el candado no debe haberse escrito",
            database.localSaleDao().getSaleById(SALE_ID)?.CLAIM_ID
        )
    }

    /**
     * Ya hay una corrección commiteada esperando a que la cola la entregue: encimarle otra
     * dejaría al trabajador entregando un cuerpo que nadie revisó, y el usuario sólo vería
     * confirmada la última.
     */
    @Test
    fun `claimForEdit rechaza si CORRECCION_REMOTA_PENDIENTE es 1`() = runTest {
        insert(freeSale(enviado = true, correccionRemotaPendiente = true))

        val rows = claimForEdit(CLAIM_ID_A)

        assertEquals("una correccion en cola bloquea al editor", 0, rows)
        assertNull(
            "el candado no debe haberse escrito",
            database.localSaleDao().getSaleById(SALE_ID)?.CLAIM_ID
        )
    }

    /**
     * Marca terminal: el servidor cerró la puerta para siempre. El `WHERE` dice
     * `CORRECCION_REMOTA_ESTADO IS NULL`, no "no está en la lista de valores conocidos" — igual
     * que `esCorreccionRemotaTerminal`, falla CERRADO ante un valor que todavía no existe.
     */
    @Test
    fun `claimForEdit rechaza si CORRECCION_REMOTA_ESTADO no es nulo`() = runTest {
        insert(freeSale(enviado = true, correccionRemotaEstado = "RECHAZADA_ESTADO"))

        val rows = claimForEdit(CLAIM_ID_A)

        assertEquals("el servidor ya cerro la puerta para siempre", 0, rows)
        assertNull(
            "el candado no debe haberse escrito",
            database.localSaleDao().getSaleById(SALE_ID)?.CLAIM_ID
        )
    }

    @Test
    fun `claimForEdit rechaza una marca terminal que todavia no existe en el vocabulario`() =
        runTest {
            insert(freeSale(enviado = true, correccionRemotaEstado = "UN_TERMINAL_NUEVO"))

            val rows = claimForEdit(CLAIM_ID_A)

            assertEquals("el predicado falla CERRADO ante un valor desconocido", 0, rows)
        }

    /**
     * Las dos columnas del nivel 2 bloquean ANTES que cualquier otra cosa, así que también
     * bloquean sobre una venta SIN enviar — donde en producción nunca se escriben (el
     * `AND ENVIADO = 1` de `marcarCorreccionRemotaPendiente`). Es defensa en profundidad y ESTA
     * prueba es la que verifica que el `AND` vive fuera del `OR` de `ENVIADO`, no dentro de una
     * sola de sus ramas.
     */
    @Test
    fun `claimForEdit rechaza la cola y la marca terminal incluso sin enviar`() = runTest {
        insert(freeSale(correccionRemotaPendiente = true))
        assertEquals("la cola bloquea aunque ENVIADO sea 0", 0, claimForEdit(CLAIM_ID_A))

        database.localSaleDao()
            .insertSale(freeSale(correccionRemotaEstado = "CONFLICTO"))
        assertEquals("la marca bloquea aunque ENVIADO sea 0", 0, claimForEdit(CLAIM_ID_B))
    }

    /**
     * Control positivo del `OR` completo: una venta enviada NO mira `LAST_UPLOAD_PERMANENT`. Ese
     * chequeo vive sólo en la rama `ENVIADO = 0` del predicado — un fallo permanente es del
     * intento de ALTA, y si la venta ya subió ese intento terminó. Si alguien saca el
     * `LAST_UPLOAD_PERMANENT` fuera del paréntesis, este caso se pone rojo.
     */
    @Test
    fun `claimForEdit acepta una venta enviada aunque LAST_UPLOAD_PERMANENT sea true`() = runTest {
        insert(freeSale(enviado = true, lastUploadPermanent = true))

        val rows = claimForEdit(CLAIM_ID_A)

        assertEquals(
            "un fallo permanente del ALTA no dice nada de una venta que ya subio",
            1,
            rows
        )
    }

    @Test
    fun `claimForEdit rechaza una venta enviada con un candado de SUBIDA vigente`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            freeSale(
                enviado = true,
                claimId = CLAIM_ID_A,
                claimKind = "UPLOAD",
                claimedAt = now
            )
        )

        val rows = claimForEdit(CLAIM_ID_B, now)

        assertEquals("el candado sigue mandando sobre una venta enviada", 0, rows)
        assertEquals(CLAIM_ID_A, database.localSaleDao().getSaleById(SALE_ID)?.CLAIM_ID)
    }

    // ─── commitEditGuardEnviada (nivel 2) ───────────────────────────────
    //
    // El gemelo de `commitEditGuard` para la venta que YA subió. Los dos son
    // EXCLUYENTES por `ENVIADO` (`= 0` uno, `= 1` el otro): elegir mal el
    // guardia no abre un agujero, abre un 0 filas y la transacción entera se
    // revierte. Lo que este guardia NO hace, y es lo más importante de todo
    // este trabajo, es tocar `ENVIADO`.

    /**
     * **El invariante caro, medido en el `UPDATE` mismo.** El guardia de la venta enviada sube
     * `REVISION`, cierra el candado y deja `ENVIADO` EXACTAMENTE donde estaba. Si lo bajara a 0
     * —que es lo que hace el guardia del nivel 1— la venta volvería a la cola de ALTA con su
     * `Idempotency-Key` original: el servidor contestaría con la respuesta que ya tenía
     * guardada, no actualizaría nada, y el teléfono se quedaría creyendo que reenvió.
     */
    @Test
    fun `commitEditGuardEnviada cierra el candado, sube REVISION y NO baja ENVIADO`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            freeSale(
                enviado = true,
                claimId = CLAIM_ID_A,
                claimKind = "EDIT",
                claimedAt = now,
                revision = 2
            )
        )

        val rows = database.localSaleDao().commitEditGuardEnviada(SALE_ID, CLAIM_ID_A)

        assertEquals(1, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals(
            "bajar ENVIADO devolveria la venta a la cola de ALTA con su Idempotency-Key",
            true,
            sale?.ENVIADO
        )
        assertNull(sale?.CLAIM_ID)
        assertNull(sale?.CLAIM_KIND)
        assertNull(sale?.CLAIMED_AT)
        assertEquals(3, sale?.REVISION)
    }

    @Test
    fun `commitEditGuardEnviada rechaza una venta que todavia NO se envio`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            freeSale(
                enviado = false,
                claimId = CLAIM_ID_A,
                claimKind = "EDIT",
                claimedAt = now,
                revision = 2
            )
        )

        val rows = database.localSaleDao().commitEditGuardEnviada(SALE_ID, CLAIM_ID_A)

        assertEquals(
            "los dos guardias son excluyentes por ENVIADO: esta venta es del otro camino",
            0,
            rows
        )
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals("REVISION no debe subir si el guardia rechazo", 2, sale?.REVISION)
        assertEquals("el candado no se toca", CLAIM_ID_A, sale?.CLAIM_ID)
    }

    @Test
    fun `commitEditGuardEnviada con otro claimId devuelve 0 y deja la fila intacta`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            freeSale(
                enviado = true,
                claimId = CLAIM_ID_A,
                claimKind = "EDIT",
                claimedAt = now,
                revision = 3
            )
        )

        val rows = database.localSaleDao().commitEditGuardEnviada(SALE_ID, CLAIM_ID_B)

        assertEquals("un claimId que no coincide no puede comitear", 0, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals(3, sale?.REVISION)
        assertEquals(CLAIM_ID_A, sale?.CLAIM_ID)
        assertEquals(true, sale?.ENVIADO)
    }

    /**
     * Otra sesión alcanzó a commitear su corrección entre el guardia y este commit: encimarle
     * otra significa que el trabajador acaba entregando un cuerpo que nadie revisó, y el usuario
     * sólo vería confirmada la última.
     */
    @Test
    fun `commitEditGuardEnviada rechaza si CORRECCION_REMOTA_PENDIENTE es 1`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            freeSale(
                enviado = true,
                claimId = CLAIM_ID_A,
                claimKind = "EDIT",
                claimedAt = now,
                revision = 1,
                correccionRemotaPendiente = true
            )
        )

        val rows = database.localSaleDao().commitEditGuardEnviada(SALE_ID, CLAIM_ID_A)

        assertEquals("ya hay una correccion esperando turno", 0, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals(1, sale?.REVISION)
        assertEquals(CLAIM_ID_A, sale?.CLAIM_ID)
        assertEquals("y ENVIADO tampoco se toca cuando el guardia rechaza", true, sale?.ENVIADO)
    }

    @Test
    fun `commitEditGuardEnviada rechaza si CORRECCION_REMOTA_ESTADO no es nulo`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            freeSale(
                enviado = true,
                claimId = CLAIM_ID_A,
                claimKind = "EDIT",
                claimedAt = now,
                revision = 1,
                correccionRemotaEstado = "CONFLICTO"
            )
        )

        val rows = database.localSaleDao().commitEditGuardEnviada(SALE_ID, CLAIM_ID_A)

        assertEquals("el servidor ya cerro la puerta: no hay nada que reintentar", 0, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertEquals(1, sale?.REVISION)
        assertEquals(CLAIM_ID_A, sale?.CLAIM_ID)
        assertEquals(true, sale?.ENVIADO)
    }

    /**
     * La contracara: `commitEditGuard` (el del nivel 1) NO mira ninguna de las dos columnas del
     * nivel 2 — su `WHERE` es `ENVIADO = 0 AND CLAIM_ID = :claimId` y nada más. Sobre una venta
     * sin enviar esas columnas no se escriben nunca en producción, así que agregárselas sería
     * una condición que nunca cambia nada. Esta prueba fija ese límite: si alguien "empareja"
     * los dos guardias copiándole los `AND` al de abajo, se pone roja y obliga a explicar por
     * qué.
     */
    @Test
    fun `commitEditGuard no mira las columnas del nivel 2`() = runTest {
        val now = clock.now().toEpochMilli()
        insert(
            freeSale(
                enviado = false,
                claimId = CLAIM_ID_A,
                claimKind = "EDIT",
                claimedAt = now,
                correccionRemotaPendiente = true,
                correccionRemotaEstado = "CONFLICTO"
            )
        )

        val rows = database.localSaleDao().commitEditGuard(SALE_ID, CLAIM_ID_A)

        assertEquals("el guardia del nivel 1 sigue siendo ENVIADO = 0 AND CLAIM_ID", 1, rows)
    }

    // ─── claimForRemote: sólo se reclama lo que hay que entregar ────────

    /**
     * El worker remoto sólo debe reclamar lo que de verdad hay que entregar.
     * `reencolar` lo encola en CADA guardado y el barrido de sesión lo vuelve a encolar, así
     * que un encolado de más sobre una venta cuya corrección ya viajó es cosa de todos los
     * días. Sin `CORRECCION_REMOTA_PENDIENTE = 1` en su `WHERE`, ese encolado tomaba el candado
     * y dejaba la fila con un `REMOTE` vivo y la bandera en 0 — el único hueco por el que
     * `evaluarCorregibilidad` y [LocalSaleDao.claimForEdit] podían discrepar, y por el que el
     * usuario veía un botón "Corregir venta" que no hacía nada ni explicaba por qué.
     */
    @Test
    fun `claimForRemote rechaza una venta enviada que no tiene correccion en cola`() = runTest {
        insert(freeSale(enviado = true, correccionRemotaPendiente = false))

        val rows = claimForRemote(CLAIM_ID_A)

        assertEquals(0, rows)
        val sale = database.localSaleDao().getSaleById(SALE_ID)
        assertNull(sale?.CLAIM_ID)
        assertNull(sale?.CLAIM_KIND)
    }
}
