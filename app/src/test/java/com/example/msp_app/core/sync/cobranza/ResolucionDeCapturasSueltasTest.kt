package com.example.msp_app.core.sync.cobranza

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.database.entities.DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.api.services.cobranza.DigestResponse
import com.example.msp_app.data.api.services.cobranza.IdsResponse
import com.example.msp_app.data.api.services.cobranza.PagoDto
import com.example.msp_app.data.api.services.cobranza.SyncPagosResponse
import com.example.msp_app.data.api.services.cobranza.SyncVentasResponse
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.VentaDto
import com.example.msp_app.data.api.services.payment.PagoRecibidoDTO
import com.example.msp_app.data.api.services.payment.V2PaymentsApi
import com.example.msp_app.data.pagos.CondonacionFixtures
import com.example.msp_app.workers.ResolucionDeCapturasSueltasWorker
import java.io.IOException
import kotlin.math.max
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * **E-APP-048: el saldo del teléfono quedaba un abono (o varios) por debajo del
 * de Microsip.**
 *
 * Caso de campo (Q00002413, 29-sep-2026): el ticket dijo saldo anterior $3,800 y
 * actual $3,550; Microsip, $3,800 tras el abono. La causa, medida en el Room del
 * teléfono y en producción el 2026-09-30: una captura del 11-ago (`cfd7bc17…`,
 * $250) que el API Node aplicó (documento 15866668) sin dejar
 * `IMPTE_DOCTO_CC_ID`, así que el sync nunca la nombra; quedó `GUARDADO = 1`,
 * `DOCTO_CC_ID = 0` y desde 2.18.0 `sumImporteNoReconocidoPorElServidor` la resta
 * del saldo del servidor otra vez. En la flota: 2,206 capturas así, 1,725 ventas
 * por debajo, $359,860.
 *
 * Lo que se prueba: que la app le pregunte al servidor por cada captura suelta y
 * sólo actúe con DOS pruebas (documento del `GET` + gemelo del sync con ese
 * documento, cargo e importe), y que el saldo quede en el del servidor en la
 * misma transacción.
 */
class ResolucionDeCapturasSueltasTest : RoomTestBase() {

    private val respuestas = mutableMapOf<String, () -> PagoRecibidoDTO>()
    private val consultados = mutableListOf<String>()
    private val saldosDelServidor = mutableMapOf<Int, Double>()
    private var sinRedEnByIds = false

    private val pagosApi = object : V2PaymentsApi {
        override suspend fun crearPago(
            idempotencyKey: String,
            datos: RequestBody,
            imagenes: List<MultipartBody.Part>
        ): PagoRecibidoDTO = error("la resolución no manda pagos")

        override suspend fun obtenerPago(id: String): PagoRecibidoDTO {
            consultados += id
            return checkNotNull(respuestas[id]) { "sin respuesta para $id" }()
        }
    }

    private val cobranzaApi = object : V2CobranzaApi {
        override suspend fun saldosByIds(zonaId: Int, ids: String): List<VentaDto> {
            if (sinRedEnByIds) throw IOException("sin señal")
            return ids.split(",").mapNotNull { id ->
                saldosDelServidor[id.toInt()]?.let { ventaDelServidor(id.toInt(), it) }
            }
        }
        override suspend fun syncVentas(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncVentasResponse = error("no se usa")
        override suspend fun syncPagos(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ): SyncPagosResponse = error("no se usa")
        override suspend fun pagosDigest(zonaId: Int, desde: String?): DigestResponse =
            error("no se usa")
        override suspend fun saldosDigest(zonaId: Int, desde: String?): DigestResponse =
            error("no se usa")
        override suspend fun listPagoIds(
            zonaId: Int,
            after: Int,
            limit: Int,
            desde: String?
        ): IdsResponse = error("no se usa")
        override suspend fun listSaldoIds(
            zonaId: Int,
            after: Int,
            limit: Int,
            desde: String?
        ): IdsResponse = error("no se usa")
        override suspend fun pagosByIds(zonaId: Int, ids: String): List<PagoDto> =
            error("no se usa")
    }

    private fun resolucion() = ResolucionDeCapturasSueltas(
        pagosApi = pagosApi,
        refresco = RefrescoDelSaldoDeLaVenta(
            api = cobranzaApi,
            db = db,
            cobranzaWriteMutex = CobranzaWriteMutex()
        ),
        db = db
    )

    // --- El caso de campo, con los números medidos ------------------------------

    /**
     * Q00002413 tal como estaba en el Room del cobrador el 2026-09-30 14:14 UTC
     * (R1/R2): base del servidor 3,800, `SALDO_REST` 3,550, la captura del 11-ago
     * suelta junto a su gemelo numérico, y los abonos modernos ya nombrados.
     */
    @Test
    fun `E-APP-048 caso Q00002413 - la captura del 11-ago se reconoce y el saldo vuelve a 3800`() {
        sembrar(venta(CARGO_CINDY, precio = 12800.0, total = 8500.0, rest = 500.0, saldo = 3550.0))
        guardar(captura(CFD7, CARGO_CINDY, 250.0, fecha = "2026-08-11T22:18:31.721Z"))
        guardar(numerico("15866669", CARGO_CINDY, 250.0, documento = 15_866_668))
        guardar(numerico("16124186", CARGO_CINDY, 250.0, 16_124_185, nombra = DEL_22))
        guardar(numerico("16158448", CARGO_CINDY, 250.0, 16_158_447, nombra = DEL_29))
        // Lo que contesta producción para una captura de la era Node (medido en
        // dev el 2026-10-01): documento, y cargo/importe en cero.
        respuestas[CFD7] = { PagoRecibidoDTO(id = CFD7, docto_cc_id = 15_866_668) }
        saldosDelServidor[CARGO_CINDY] = 3800.0

        val resumen = runBlocking { resolucion().resolver() }

        assertTrue(resumen.completa)
        assertEquals(1, resumen.reconocidas)
        assertEquals("el saldo es el de Microsip", 3800.0, saldo(CARGO_CINDY), 1e-9)
        assertNull("la copia local colapsa con su gemelo", pago(CFD7))
        assertEquals(15_866_668, pago("15866669")!!.DOCTO_CC_ID)
        assertEquals(
            "quedan el gemelo y los dos abonos modernos, intactos",
            listOf("15866669", "16124186", "16158448"),
            pagosDe(CARGO_CINDY).map { it.ID }.sorted()
        )
    }

    /**
     * La venta con fósil cuyo saldo TODAVÍA estaba bien (158 en la flota): no la
     * había alcanzado un `VentaDto`. Se reconoce antes de que la alcance.
     */
    @Test
    fun `E-APP-048 una venta con fosil latente queda en el saldo del servidor`() {
        sembrar(venta(CARGO_CINDY, precio = 12800.0, total = 8500.0, rest = 500.0, saldo = 3800.0))
        guardar(captura(CFD7, CARGO_CINDY, 250.0))
        guardar(numerico("15866669", CARGO_CINDY, 250.0, documento = 15_866_668))
        respuestas[CFD7] = { PagoRecibidoDTO(id = CFD7, docto_cc_id = 15_866_668) }
        saldosDelServidor[CARGO_CINDY] = 3800.0

        runBlocking { resolucion().resolver() }

        assertEquals(3800.0, saldo(CARGO_CINDY), 1e-9)
        assertNull(pago(CFD7))
    }

    // --- Lo que NO basta para reconocer -------------------------------------------

    /**
     * El `GET` contesta 200 con documento aunque el documento ya no exista en
     * `DOCTOS_CC` (9 capturas en la flota, medido 2026-10-01). Sin el gemelo del
     * sync no hay prueba de que se aplicó: no se toca, sigue restando.
     */
    @Test
    fun `documento sin gemelo en el telefono no se toca`() {
        sembrar(venta(CARGO_A, saldo = 900.0, base = 1000.0))
        guardar(captura(X1, CARGO_A, 100.0))
        respuestas[X1] = { PagoRecibidoDTO(id = X1, docto_cc_id = 15_864_786) }
        saldosDelServidor[CARGO_A] = 1000.0

        val resumen = runBlocking { resolucion().resolver() }

        assertEquals(0, documento(X1))
        assertEquals("sin segunda prueba el saldo no sube", 900.0, saldo(CARGO_A), 1e-9)
        assertEquals(1, resumen.sinEvidencia)
        assertTrue("no es transitorio: no pide reintento", resumen.completa)
    }

    @Test
    fun `un gemelo con otro importe no basta`() {
        sembrar(venta(CARGO_A, saldo = 800.0, base = 1000.0))
        guardar(captura(X1, CARGO_A, 200.0))
        guardar(numerico("900001", CARGO_A, 100.0, documento = DOC_X1))
        respuestas[X1] = { PagoRecibidoDTO(id = X1, docto_cc_id = DOC_X1) }
        saldosDelServidor[CARGO_A] = 1000.0

        runBlocking { resolucion().resolver() }

        assertEquals(0, documento(X1))
        assertEquals(800.0, saldo(CARGO_A), 1e-9)
    }

    @Test
    fun `un gemelo en otro cargo no basta`() {
        sembrar(venta(CARGO_A, saldo = 800.0, base = 1000.0))
        sembrar(venta(CARGO_B, saldo = 500.0, base = 500.0))
        guardar(captura(X1, CARGO_A, 200.0))
        guardar(numerico("900001", CARGO_B, 200.0, documento = DOC_X1))
        respuestas[X1] = { PagoRecibidoDTO(id = X1, docto_cc_id = DOC_X1) }
        saldosDelServidor[CARGO_A] = 1000.0

        runBlocking { resolucion().resolver() }

        assertEquals(0, documento(X1))
        assertEquals(800.0, saldo(CARGO_A), 1e-9)
    }

    /** 200 sin documento: el servidor la tiene pendiente (las 2 condonaciones P). */
    @Test
    fun `una captura pendiente en el servidor no se toca`() {
        sembrar(venta(CARGO_A, saldo = 600.0, base = 1000.0))
        guardar(captura(X1, CARGO_A, 400.0, forma = CondonacionFixtures.CONDONACION))
        respuestas[X1] = { PagoRecibidoDTO(id = X1, sincronizacion = "pendiente") }
        saldosDelServidor[CARGO_A] = 1000.0

        runBlocking { resolucion().resolver() }

        assertEquals(0, documento(X1))
        assertEquals(600.0, saldo(CARGO_A), 1e-9)
    }

    // --- El servidor no la tiene ----------------------------------------------------

    /**
     * 404 del propio API con `pago_no_encontrado`: el servidor no tiene la
     * captura (7 en la flota; 5 son 422 custodiados que oficina capturó a mano).
     * Decisión del dueño del 2026-09-29: deja de restarse, el saldo es el del
     * servidor, y la fila se queda marcada.
     */
    @Test
    fun `E-APP-048 una captura que el servidor no tiene se marca no aplicada`() {
        sembrar(venta(CARGO_A, saldo = 650.0, base = 1000.0))
        guardar(captura(X1, CARGO_A, 350.0))
        respuestas[X1] = { throw noExiste() }
        saldosDelServidor[CARGO_A] = 1000.0

        val resumen = runBlocking { resolucion().resolver() }

        assertEquals(DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR, documento(X1))
        assertEquals(1000.0, saldo(CARGO_A), 1e-9)
        assertEquals(1, resumen.noAplicadas)
    }

    /**
     * Un 404 en `problem+json` que NO dice `pago_no_encontrado` no prueba que la
     * captura no exista (la compuerta previa pidió exigir el código).
     */
    @Test
    fun `un 404 del API sin pago_no_encontrado no marca`() {
        sembrar(venta(CARGO_A, saldo = 650.0, base = 1000.0))
        guardar(captura(X1, CARGO_A, 350.0))
        respuestas[X1] = { throw httpError(404, cuerpo = """{"status":404}""") }
        saldosDelServidor[CARGO_A] = 1000.0

        runBlocking { resolucion().resolver() }

        assertEquals(0, documento(X1))
        assertEquals(650.0, saldo(CARGO_A), 1e-9)
    }

    @Test
    fun `un 404 que no es del API reintenta y no marca`() {
        sembrar(venta(CARGO_A, saldo = 650.0, base = 1000.0))
        guardar(captura(X1, CARGO_A, 350.0))
        respuestas[X1] = { throw httpError(404, contentType = "text/html", cuerpo = "<h1>404</h1>") }

        val resumen = runBlocking { resolucion().resolver() }

        assertFalse(resumen.completa)
        assertEquals(0, documento(X1))
    }

    // --- Sin red y atomicidad --------------------------------------------------------

    @Test
    fun `sin red al preguntar no toca nada y pide reintento`() {
        sembrar(venta(CARGO_CINDY, precio = 12800.0, total = 8500.0, rest = 500.0, saldo = 3550.0))
        guardar(captura(CFD7, CARGO_CINDY, 250.0))
        guardar(numerico("15866669", CARGO_CINDY, 250.0, documento = 15_866_668))
        respuestas[CFD7] = { throw IOException("sin señal") }

        val resumen = runBlocking { resolucion().resolver() }

        assertFalse(resumen.completa)
        assertEquals(0, documento(CFD7))
        assertEquals(3550.0, saldo(CARGO_CINDY), 1e-9)
    }

    /**
     * El documento se anota en la MISMA transacción que fija el saldo: si no se
     * pudo leer el saldo del servidor, no se anota nada (si se anotara, la venta
     * se quedaría abajo y la captura ya no volvería a aparecer como suelta).
     */
    @Test
    fun `sin red al leer el saldo no anota nada y pide reintento`() {
        sembrar(venta(CARGO_CINDY, precio = 12800.0, total = 8500.0, rest = 500.0, saldo = 3550.0))
        guardar(captura(CFD7, CARGO_CINDY, 250.0))
        guardar(numerico("15866669", CARGO_CINDY, 250.0, documento = 15_866_668))
        respuestas[CFD7] = { PagoRecibidoDTO(id = CFD7, docto_cc_id = 15_866_668) }
        sinRedEnByIds = true

        val resumen = runBlocking { resolucion().resolver() }

        assertFalse(resumen.completa)
        assertEquals("nada a medias", 0, documento(CFD7))
        assertEquals(3550.0, saldo(CARGO_CINDY), 1e-9)
    }

    /**
     * La gemela de la de arriba para la rama 404 (compuerta DESPUÉS del
     * 2026-10-02, M1): la marca -1 va en la MISMA transacción que fija el saldo.
     * Si se marcara antes y fallara la lectura del saldo, la captura dejaría de
     * ser suelta —nadie la volvería a intentar— con la venta todavía abajo.
     */
    @Test
    fun `sin red al leer el saldo no marca la no aplicada y pide reintento`() {
        sembrar(venta(CARGO_A, saldo = 650.0, base = 1000.0))
        guardar(captura(X1, CARGO_A, 350.0))
        respuestas[X1] = { throw noExiste() }
        sinRedEnByIds = true

        val resumen = runBlocking { resolucion().resolver() }

        assertFalse(resumen.completa)
        assertEquals("nada a medias", 0, documento(X1))
        assertEquals(650.0, saldo(CARGO_A), 1e-9)
    }

    /**
     * El servidor no trae la venta (liquidada u otra zona): una ausencia no es un
     * saldo. Se reconoce la captura —tiene sus dos pruebas— y el saldo no se toca.
     */
    @Test
    fun `si el servidor no trae la venta se reconoce la captura y el saldo no se toca`() {
        sembrar(venta(CARGO_A, saldo = 0.0, base = 200.0))
        guardar(captura(X1, CARGO_A, 200.0))
        guardar(numerico("900001", CARGO_A, 200.0, documento = DOC_X1))
        respuestas[X1] = { PagoRecibidoDTO(id = X1, docto_cc_id = DOC_X1) }

        runBlocking { resolucion().resolver() }

        assertNull(pago(X1))
        assertEquals(0.0, saldo(CARGO_A), 1e-9)
    }

    // --- Lo que sigue en vuelo de verdad ---------------------------------------------

    /** Un abono de hoy sin subir (`GUARDADO = 0`) sigue restando: está en vuelo. */
    @Test
    fun `un abono de verdad en vuelo se sigue restando`() {
        sembrar(venta(CARGO_CINDY, precio = 12800.0, total = 8500.0, rest = 500.0, saldo = 3300.0))
        guardar(captura(CFD7, CARGO_CINDY, 250.0))
        guardar(numerico("15866669", CARGO_CINDY, 250.0, documento = 15_866_668))
        guardar(captura(HOY, CARGO_CINDY, 250.0, guardado = false))
        respuestas[CFD7] = { PagoRecibidoDTO(id = CFD7, docto_cc_id = 15_866_668) }
        saldosDelServidor[CARGO_CINDY] = 3800.0

        runBlocking { resolucion().resolver() }

        assertEquals(3550.0, saldo(CARGO_CINDY), 1e-9)
        assertFalse("el pendiente no se consulta", HOY in consultados)
    }

    @Test
    fun `una captura nombrada por el servidor no se consulta`() {
        sembrar(venta(CARGO_A, saldo = 800.0, base = 800.0))
        guardar(captura(X1, CARGO_A, 200.0))
        guardar(numerico("900001", CARGO_A, 200.0, documento = DOC_X1, nombra = X1))

        runBlocking { resolucion().resolver() }

        assertTrue(consultados.isEmpty())
    }

    @Test
    fun `cualquier forma de cobro se resuelve, no solo condonaciones`() {
        sembrar(venta(CARGO_A, saldo = 600.0, base = 1000.0))
        guardar(captura(X1, CARGO_A, 200.0, forma = CondonacionFixtures.EFECTIVO))
        guardar(captura(X2, CARGO_A, 200.0, forma = TRANSFERENCIA))
        guardar(numerico("900001", CARGO_A, 200.0, documento = DOC_X1))
        guardar(numerico("900002", CARGO_A, 200.0, documento = DOC_X2))
        respuestas[X1] = { PagoRecibidoDTO(id = X1, docto_cc_id = DOC_X1) }
        respuestas[X2] = { PagoRecibidoDTO(id = X2, docto_cc_id = DOC_X2) }
        saldosDelServidor[CARGO_A] = 1000.0

        runBlocking { resolucion().resolver() }

        assertEquals(1000.0, saldo(CARGO_A), 1e-9)
        assertNull(pago(X1))
        assertNull(pago(X2))
    }

    @Test
    fun `es idempotente - la segunda corrida no consulta nada`() {
        sembrar(venta(CARGO_A, saldo = 650.0, base = 1000.0))
        guardar(captura(X1, CARGO_A, 350.0))
        respuestas[X1] = { throw noExiste() }
        saldosDelServidor[CARGO_A] = 1000.0
        runBlocking { resolucion().resolver() }
        consultados.clear()

        val segunda = runBlocking { resolucion().resolver() }

        assertTrue(consultados.isEmpty())
        assertEquals(0, segunda.revisadas)
        assertEquals(1000.0, saldo(CARGO_A), 1e-9)
    }

    // --- Propiedad ------------------------------------------------------------------

    /**
     * 300 escenarios con semilla fija (base 20260929). Cada venta lleva una
     * mezcla de capturas: aplicadas con gemelo, aplicadas sin gemelo, que el
     * servidor no tiene, pendientes en el servidor y abonos sin subir; su saldo
     * empieza como lo dejó 2.18+ (todo restado) o todavía sin restar.
     *
     * Invariantes tras resolver:
     * - INV-1 nunca por encima del servidor;
     * - INV-2 en una venta que la resolución tocó, exactamente
     *   `max(0, servidor - lo que sigue sin prueba)`, la misma fórmula del sync
     *   (no inventa una segunda definición); en una que no tocó, el mismo saldo
     *   que tenía;
     * - INV-4 ninguna captura sin gemelo se borra.
     *
     * No hay un "nunca más bajo que antes": si el servidor recibió un abono nuevo
     * mientras hay uno local sin subir, el saldo correcto SÍ está debajo del que
     * había, y forzar sólo-hacia-arriba lo dejaría por encima del real (se probó
     * y se descartó el 2026-10-01).
     */
    @Test
    fun `E-APP-048 propiedad - 300 escenarios cumplen los invariantes del saldo`() {
        repeat(ESCENARIOS) { n -> escenario(Random(SEMILLA + n), n) }
    }

    private fun escenario(r: Random, n: Int) {
        runBlocking { db.clearAllTables() }
        respuestas.clear()
        consultados.clear()
        saldosDelServidor.clear()
        data class Esperado(
            val cargo: Int,
            val base: Double,
            val antes: Double,
            val queda: Double,
            val tocada: Boolean
        )
        val esperados = mutableListOf<Esperado>()
        val sinGemelo = mutableListOf<String>()
        var doc = 20_000_000
        repeat(1 + r.nextInt(4)) { v ->
            val cargo = 30_000_000 + n * 10 + v
            val base = 50.0 * r.nextInt(0, 200)
            var restado = 0.0
            var queda = 0.0
            var tocada = false
            repeat(r.nextInt(0, 5)) { k ->
                val id = "e%07d-0000-4000-8000-%012d".format(n, v * 10 + k)
                val importe = 50.0 * r.nextInt(1, 10)
                when (r.nextInt(5)) {
                    0 -> { // aplicada, con gemelo: deja de restar
                        doc += 2
                        guardar(captura(id, cargo, importe))
                        guardar(numerico("${doc + 1}", cargo, importe, documento = doc))
                        val d = doc
                        respuestas[id] = { PagoRecibidoDTO(id = id, docto_cc_id = d) }
                        restado += importe
                        tocada = true
                    }
                    1 -> { // documento sin gemelo: sigue restando
                        doc += 2
                        guardar(captura(id, cargo, importe))
                        val d = doc
                        respuestas[id] = { PagoRecibidoDTO(id = id, docto_cc_id = d) }
                        sinGemelo += id
                        restado += importe
                        queda += importe
                    }
                    2 -> { // el servidor no la tiene: deja de restar
                        guardar(captura(id, cargo, importe))
                        respuestas[id] = { throw noExiste() }
                        restado += importe
                        tocada = true
                    }
                    3 -> { // pendiente en el servidor: sigue restando
                        guardar(captura(id, cargo, importe))
                        respuestas[id] = { PagoRecibidoDTO(id = id, sincronizacion = "pendiente") }
                        sinGemelo += id
                        restado += importe
                        queda += importe
                    }
                    else -> { // abono de hoy sin subir: en vuelo
                        guardar(captura(id, cargo, importe, guardado = false))
                        restado += importe
                        queda += importe
                    }
                }
            }
            val latente = r.nextInt(4) == 0
            val antes = if (latente) base else max(0.0, base - restado)
            sembrar(venta(cargo, saldo = antes, base = base))
            saldosDelServidor[cargo] = base
            esperados += Esperado(cargo, base, antes, queda, tocada)
        }

        val resumen = runBlocking { resolucion().resolver() }

        assertTrue("escenario $n: completa", resumen.completa)
        for (e in esperados) {
            val s = saldo(e.cargo)
            assertTrue("escenario $n INV-1 venta ${e.cargo}: $s > ${e.base}", s <= e.base + 1e-9)
            val esperado = if (e.tocada) max(0.0, e.base - e.queda) else e.antes
            assertEquals("escenario $n INV-2 venta ${e.cargo}", esperado, s, 1e-9)
        }
        for (id in sinGemelo) assertTrue("escenario $n INV-4 $id", pago(id) != null)
    }

    // --- El trabajo ---------------------------------------------------------------------

    @Test
    fun `el trabajo termina y reporta lo que resolvio`() {
        sembrar(venta(CARGO_A, saldo = 650.0, base = 1000.0))
        guardar(captura(X1, CARGO_A, 350.0))
        respuestas[X1] = { throw noExiste() }
        saldosDelServidor[CARGO_A] = 1000.0
        val reportes = mutableListOf<ResumenDeLaResolucion>()

        assertEquals(ListenableWorker.Result.success(), correrElTrabajo(reportes = reportes))

        assertEquals(1, reportes.single().noAplicadas)
        assertEquals(listOf("$X1|$CARGO_A|350.0"), reportes.single().detalleNoAplicadas)
    }

    @Test
    fun `sin capturas sueltas el trabajo no consulta ni reporta`() {
        val reportes = mutableListOf<ResumenDeLaResolucion>()

        assertEquals(ListenableWorker.Result.success(), correrElTrabajo(reportes = reportes))

        assertTrue(consultados.isEmpty())
        assertTrue(reportes.isEmpty())
    }

    @Test
    fun `sin red el trabajo reintenta, y en el tope termina sin tocar nada`() {
        sembrar(venta(CARGO_CINDY, precio = 12800.0, total = 8500.0, rest = 500.0, saldo = 3550.0))
        guardar(captura(CFD7, CARGO_CINDY, 250.0))
        respuestas[CFD7] = { throw IOException("sin señal") }

        assertEquals(ListenableWorker.Result.retry(), correrElTrabajo())
        assertEquals(
            ListenableWorker.Result.success(),
            correrElTrabajo(intento = ResolucionDeCapturasSueltasWorker.MAX_INTENTOS)
        )

        assertEquals(0, documento(CFD7))
        assertEquals(3550.0, saldo(CARGO_CINDY), 1e-9)
    }

    @Test
    fun `una telemetria que falla no tumba el trabajo`() {
        sembrar(venta(CARGO_A, saldo = 650.0, base = 1000.0))
        guardar(captura(X1, CARGO_A, 350.0))
        respuestas[X1] = { throw noExiste() }
        saldosDelServidor[CARGO_A] = 1000.0

        val resultado = correrElTrabajo(telemetria = { error("Firestore caído") })

        assertEquals(ListenableWorker.Result.success(), resultado)
        assertEquals(DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR, documento(X1))
    }

    private fun correrElTrabajo(
        intento: Int = 0,
        reportes: MutableList<ResumenDeLaResolucion> = mutableListOf(),
        telemetria: (ResumenDeLaResolucion) -> Unit = { reportes += it }
    ): ListenableWorker.Result {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<ResolucionDeCapturasSueltasWorker>(context)
            .setRunAttemptCount(intento)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = ResolucionDeCapturasSueltasWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    resolucion = resolucion(),
                    telemetria = telemetria
                )
            })
            .build()
        return runBlocking { (worker as ResolucionDeCapturasSueltasWorker).doWork() }
    }

    // --- Ayudas ------------------------------------------------------------------------

    private fun venta(
        cargo: Int,
        saldo: Double,
        base: Double = saldo,
        precio: Double = base + 1000.0,
        total: Double = 1000.0,
        rest: Double = precio - total - base
    ): SaleEntity = CondonacionFixtures.venta(saldo = saldo, id = cargo).copy(
        // `sales.FOLIO` es único: con el mismo folio la segunda venta pisaría a la primera.
        FOLIO = "Q$cargo",
        PRECIO_TOTAL = precio,
        TOTAL_IMPORTE = total,
        IMPTE_REST = rest,
        SALDO_REST = saldo,
        CLIENTE = "Rocio Aguilar Medina"
    )

    private fun captura(
        id: String,
        cargo: Int,
        importe: Double,
        forma: Int = CondonacionFixtures.EFECTIVO,
        guardado: Boolean = true,
        fecha: String = "2026-08-11T22:18:31.721Z"
    ): PaymentEntity = CondonacionFixtures.condonacion(
        id = id,
        importe = importe,
        venta = cargo,
        formaCobro = forma,
        guardado = guardado
    ).copy(FECHA_HORA_PAGO = fecha, NOMBRE_CLIENTE = "Rocio Aguilar Medina")

    private fun numerico(
        id: String,
        cargo: Int,
        importe: Double,
        documento: Int,
        nombra: String? = null
    ): PaymentEntity = captura(id, cargo, importe).copy(
        DOCTO_CC_ID = documento,
        PAGO_RECIBIDO_ID = nombra,
        FECHA_HORA_PAGO = "2026-08-11T22:18:31Z"
    )

    private fun sembrar(venta: SaleEntity) = runBlocking { db.saleDao().insertAll(listOf(venta)) }

    private fun guardar(pago: PaymentEntity) = runBlocking { db.paymentDao().savePayment(pago) }

    private fun pago(id: String): PaymentEntity? = runBlocking {
        db.paymentDao().getPaymentById(
            id
        )
    }

    private fun pagosDe(cargo: Int): List<PaymentEntity> =
        runBlocking { db.paymentDao().getPaymentsBySaleId(cargo) }

    private fun documento(id: String): Int = pago(id)!!.DOCTO_CC_ID

    private fun saldo(cargo: Int): Double = runBlocking { CondonacionFixtures.saldoDe(db, cargo) }

    /** El 404 del propio API, con la forma medida en dev el 2026-10-01. */
    private fun noExiste() = httpError(
        404,
        cuerpo = """{"title":"Not Found","status":404,"detail":"no se encontró el pago",""" +
            """"errors":[{"message":"code=pago_no_encontrado"}]}"""
    )

    private fun httpError(
        code: Int,
        contentType: String = "application/problem+json",
        cuerpo: String
    ): HttpException {
        val raw = okhttp3.Response.Builder()
            .code(code)
            .message("test")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .header("Content-Type", contentType)
            .build()
        return HttpException(
            Response.error<PagoRecibidoDTO>(
                cuerpo.toResponseBody(contentType.toMediaTypeOrNull()),
                raw
            )
        )
    }

    private fun ventaDelServidor(cargo: Int, saldo: Double) = VentaDto(
        docto_cc_id = cargo,
        docto_pv_id = null,
        cliente_id = 4821,
        zona_cliente_id = 21,
        folio = "Q00002413",
        fecha_cargo = "2025-09-26T00:00:00Z",
        fecha_venta = null,
        precio_total = "%.2f".format(saldo + 1000.0),
        total_importe = "1000.00",
        impte_rest = "0.00",
        saldo = "%.2f".format(saldo),
        num_pagos = 10,
        fecha_ult_pago = null,
        cargo_cancelado = false,
        updated_at = "2026-09-30T08:36:18.000000Z",
        cliente_nombre = "Rocio Aguilar Medina",
        limite_credito = null,
        cliente_notas = "",
        cobrador_id = null,
        nombre_cobrador = "Rosa Elena Martinez Vazquez",
        zona_nombre = "Centro",
        calle = "Av. Reforma 100",
        ciudad = "Tehuacan",
        estado = "Puebla",
        telefono = "",
        parcialidad = 250,
        enganche = "500.00",
        tiempo_corto_plazo_meses = null,
        monto_corto_plazo = null,
        precio_de_contado = null,
        aval_o_responsable = "",
        vendedor_1 = "",
        vendedor_2 = "",
        vendedor_3 = "",
        frec_pago = "SEMANAL"
    )

    private companion object {
        const val CARGO_CINDY = 14_186_779
        const val CARGO_A = 13_982_082
        const val CARGO_B = 15_416_710
        const val CFD7 = "cfd7bc17-fb8f-45e4-82f4-4d8d59d7d375"
        const val DEL_22 = "03e224f3-8ece-4819-bc10-6c4bbf39d07c"
        const val DEL_29 = "14325b85-a337-4788-befa-5832a3990fca"
        const val HOY = "7a1e0c55-2b6d-4c3e-9f10-aa0b1c2d3e4f"
        const val X1 = "88983d2f-8ce4-478a-8d03-ae1187433eea"
        const val X2 = "df4c4329-6f9a-48ef-abd5-8c8713e6d049"
        const val DOC_X1 = 15_864_000
        const val DOC_X2 = 15_864_100
        const val TRANSFERENCIA = 52_569
        const val ESCENARIOS = 300
        const val SEMILLA = 20_260_929
    }
}
