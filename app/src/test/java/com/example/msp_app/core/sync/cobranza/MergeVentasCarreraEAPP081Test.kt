package com.example.msp_app.core.sync.cobranza

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.common.cobranza.domain.VisitScope
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PaymentsWorkEnqueuer
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.core.utils.VisitScopeMapper
import com.example.msp_app.core.utils.VisitStatusMapper
import com.example.msp_app.data.api.services.cobranza.DigestResponse
import com.example.msp_app.data.api.services.cobranza.IdsResponse
import com.example.msp_app.data.api.services.cobranza.PagoDto
import com.example.msp_app.data.api.services.cobranza.SyncPagosResponse
import com.example.msp_app.data.api.services.cobranza.SyncVentasResponse
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.VentaDto
import com.example.msp_app.data.api.services.cobranza.toEntity
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.pagos.CondonacionFixtures
import com.example.msp_app.data.pagos.RegistroDeAbonoAdapter
import com.example.msp_app.data.pagos.RegistroDeCondonacion
import com.example.msp_app.data.pagos.ResultadoDeLaCondonacion
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.port.AbonoARegistrar
import com.example.msp_app.feature.pagos.domain.port.ResultadoDelAbono
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * E-APP-081: `CobranzaSyncManager.mergeVentas` aplica cada venta en UNA
 * transacción (lectura de la fila, suma de lo no reconocido, REPLACE y
 * productos), y la reinserción de faltantes de `CobranzaReconciler`
 * re-verifica dentro de su transacción que la venta siga faltando y resta lo
 * que sigue en vuelo.
 *
 * Método:
 *  - Room REAL sobre archivo, con el builder de producción
 *    ([AppDatabase.getInstance] → `buildDatabase`), en Robolectric.
 *  - `mergeVentas` se ejercita por su camino real: [CobranzaSyncManager.applyByIds]
 *    con `SALDOS` (la ruta SSE by-ids), contra un API falso que devuelve el DTO.
 *  - La pausa NO toca producción: el manager recibe un [SaleDao]/[PaymentDao]
 *    envuelto por delegación de Kotlin que, DESPUÉS de que la lectura real
 *    devolvió, se detiene en un [CompletableDeferred]. Con el arreglo esa pausa
 *    cae DENTRO de la transacción.
 *  - El escritor real (abono, visita o condonación, SIN el candado del sync) se
 *    lanza en paralelo mientras el merge está detenido. Se espera [ESPERA_MS] y
 *    se anota si el escritor ya terminó: con la ventana cerrada debe seguir
 *    bloqueado por la transacción. Luego se suelta el merge y se afirma.
 *  - El segundo merge (el "siguiente tick") usa un manager SIN pausa y el mismo
 *    DTO, por la misma ruta.
 *  - Control positivo: la misma secuencia sin pausa (escritor antes o después del
 *    merge completo) deja los mismos valores correctos.
 *
 * Cada caso se repite [REPETICIONES] veces, cada vez con la base recién creada,
 * y se afirma sobre las [REPETICIONES] observaciones (se imprimen todas antes).
 *
 * Medido sin el arreglo (base `37db01f4`): el abono, la condonación y las dos
 * visitas perdían su efecto 20/20; ver el log de la rama.
 */
class MergeVentasCarreraEAPP081Test : RobolectricTestBase() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    @Before
    fun byIdsDisponible() {
        ByIdsChunker.byIdsAvailable.set(true)
    }

    @After
    fun cerrarBase() {
        AppDatabase.clearInstance()
        context.deleteDatabase(NOMBRE_DE_LA_BASE)
    }

    // ─── H1: abono ──────────────────────────────────────────────────────────

    @Test
    fun `EAPP081 H1 abono entre la suma y el REPLACE no se pierde`() {
        val obs = repetir("H1") { carreraDelMerge(pausaEnSuma = Pausa()) { abonar() } }
        afirmar(
            "H1",
            obs,
            Observacion(
                true,
                Fila(SALDO_SERVIDOR - ABONO, PAGADO),
                Fila(SALDO_SERVIDOR - ABONO, PAGADO)
            )
        )
    }

    @Test
    fun `EAPP081 H1b abono entre la lectura de la fila y la suma no se pierde`() {
        val obs = repetir("H1b") { carreraDelMerge(pausaEnLectura = Pausa()) { abonar() } }
        afirmar(
            "H1b",
            obs,
            Observacion(
                true,
                Fila(SALDO_SERVIDOR - ABONO, PAGADO),
                Fila(SALDO_SERVIDOR - ABONO, PAGADO)
            )
        )
    }

    @Test
    fun `EAPP081 H1 control abono antes o despues del merge completo`() {
        val correcto = Fila(SALDO_SERVIDOR - ABONO, PAGADO)
        afirmar(
            "H1-control-antes",
            repetir("H1-control-antes") {
                controlAntes { abonar() }
            },
            Observacion(null, correcto, correcto)
        )
        afirmar(
            "H1-control-despues",
            repetir("H1-control-despues") {
                controlDespues { abonar() }
            },
            Observacion(null, correcto, correcto)
        )
    }

    // ─── H2: visita que cambia ESTADO_COBRANZA ──────────────────────────────

    @Test
    fun `EAPP081 H2 visita de alcance cliente entre la lectura y el REPLACE no se pierde`() {
        assertEquals(VisitScope.CLIENTE, VisitScopeMapper.map(Constants.NO_SE_ENCONTRABA, false))
        val correcto = Fila(SALDO_SERVIDOR, VisitStatusMapper.map(Constants.NO_SE_ENCONTRABA).name)
        assertEquals(EstadoCobranza.VOLVER_VISITAR.name, correcto.estado)
        val obs = repetir("H2-cliente") {
            carreraDelMerge(pausaEnLectura = Pausa()) { visitar(Constants.NO_SE_ENCONTRABA) }
        }
        afirmar("H2-cliente", obs, Observacion(true, correcto, correcto))
    }

    @Test
    fun `EAPP081 H2 visita de alcance venta entre la lectura y el REPLACE no se pierde`() {
        assertEquals(VisitScope.VENTA, VisitScopeMapper.map(Constants.NO_VA_A_DAR_PAGO, false))
        val correcto = Fila(SALDO_SERVIDOR, VisitStatusMapper.map(Constants.NO_VA_A_DAR_PAGO).name)
        assertEquals(EstadoCobranza.NO_PAGADO.name, correcto.estado)
        val obs = repetir("H2-venta") {
            carreraDelMerge(pausaEnLectura = Pausa()) { visitar(Constants.NO_VA_A_DAR_PAGO) }
        }
        afirmar("H2-venta", obs, Observacion(true, correcto, correcto))
    }

    @Test
    fun `EAPP081 H2 control visita antes o despues del merge completo`() {
        for (tipo in listOf(Constants.NO_SE_ENCONTRABA, Constants.NO_VA_A_DAR_PAGO)) {
            val correcto = Fila(SALDO_SERVIDOR, VisitStatusMapper.map(tipo).name)
            afirmar(
                "H2-control-antes-$tipo",
                repetir("H2-control-antes-$tipo") {
                    controlAntes { visitar(tipo) }
                },
                Observacion(null, correcto, correcto)
            )
            afirmar(
                "H2-control-despues-$tipo",
                repetir("H2-control-despues-$tipo") {
                    controlDespues { visitar(tipo) }
                },
                Observacion(null, correcto, correcto)
            )
        }
    }

    // ─── H3: condonación ────────────────────────────────────────────────────

    @Test
    fun `EAPP081 H3 condonacion entre la suma y el REPLACE no se pierde`() {
        val obs = repetir("H3") { carreraDelMerge(pausaEnSuma = Pausa()) { condonar() } }
        afirmar(
            "H3",
            obs,
            Observacion(
                true,
                Fila(SALDO_SERVIDOR - CONDONADO, PAGADO),
                Fila(SALDO_SERVIDOR - CONDONADO, PAGADO)
            )
        )
    }

    @Test
    fun `EAPP081 H3 control condonacion antes o despues del merge completo`() {
        val correcto = Fila(SALDO_SERVIDOR - CONDONADO, PAGADO)
        afirmar(
            "H3-control-antes",
            repetir("H3-control-antes") {
                controlAntes { condonar() }
            },
            Observacion(null, correcto, correcto)
        )
        afirmar(
            "H3-control-despues",
            repetir("H3-control-despues") {
                controlDespues { condonar() }
            },
            Observacion(null, correcto, correcto)
        )
    }

    // ─── R: reinserción de faltantes del reconciliador ──────────────────────

    /**
     * La venta falta cuando el reconciliador lista los ids locales; en la
     * ventana (antes de la red) otro escritor reescribe `sales` con una foto
     * que la trae (`replaceAll`, como "inicio de semana") y el cobrador abona.
     * La reinserción debe ver que ya no falta y no pisar el abono.
     */
    @Test
    fun `EAPP081 R reinsercion de faltantes no pisa la venta que volvio con un abono`() {
        val obs = repetir("R-foto-vieja") {
            db.saleDao().deleteByDoctoCcId(VENTA)
            val pausa = Pausa()
            val tras = carrera(
                proceso = { reconciliar(pausa) },
                pausa = pausa,
                escritor = {
                    db.saleDao().replaceAll(listOf(DTO.toEntity()))
                    abonar()
                }
            )
            Observacion(null, tras, leer())
        }
        val correcto = Fila(SALDO_SERVIDOR - ABONO, PAGADO)
        afirmar("R-foto-vieja", obs, Observacion(null, correcto, correcto))
    }

    /**
     * La venta falta y tiene un abono en vuelo (capturado, sin documento del
     * servidor). La fila que reinserta el reconciliador debe restarlo, con la
     * misma fórmula que `mergeVentas`.
     */
    @Test
    fun `EAPP081 R reinsercion de faltantes resta lo que sigue en vuelo`() {
        val obs = repetir("R-en-vuelo") {
            abonar()
            db.saleDao().deleteByDoctoCcId(VENTA)
            reconciliar(null)
            Observacion(null, leer(), leer())
        }
        val correcto = Fila(SALDO_SERVIDOR - ABONO, PENDIENTE)
        afirmar("R-en-vuelo", obs, Observacion(null, correcto, correcto))
    }

    @Test
    fun `EAPP081 R control reinsercion sin escritor`() {
        val obs = repetir("R-control") {
            db.saleDao().deleteByDoctoCcId(VENTA)
            reconciliar(null)
            Observacion(null, leer(), leer())
        }
        val correcto = Fila(SALDO_SERVIDOR, PENDIENTE)
        afirmar("R-control", obs, Observacion(null, correcto, correcto))
    }

    // ─── S: rama "venta saldada" de mergeVentas ──────────────────────────────

    /**
     * El servidor manda la venta saldada (saldo 0) y el teléfono no tiene pagos
     * en ventana: `mergeVentas` cuenta (`countPagosDesde` = 0) y la borra. El
     * merge se detiene justo después de contar y, en la ventana, el cobrador
     * condona.
     *
     * Lo que la transacción por venta garantiza en esta rama: la cuenta y el
     * borrado ven el mismo estado. Un escritor que relee la venta DENTRO de su
     * transacción (la condonación, `RegistroDeCondonacion.escribir`) queda
     * entero antes o entero después; nunca resulta un pago en ventana sobre una
     * venta que el merge ya decidió borrar.
     *
     * NO lo garantiza para el abono: `RegistroDeAbonoAdapter` valida la venta
     * FUERA de su transacción y, con o sin este arreglo, queda el abono
     * registrado con la venta borrada (compuerta DESPUÉS, 20/20). Ese hueco es
     * E-APP-083 y sigue ABIERTO; esta prueba no lo cubre.
     */
    @Test
    fun `EAPP081 S condonacion tras contar los pagos de una venta saldada no queda sobre la venta borrada`() {
        val obs = repetirSaldada { carreraSaldada() }
        imprimirSaldada("S", obs)
        obs.forEachIndexed { i, o ->
            assertEquals(
                "S repetición ${i + 1}",
                Saldada(
                    ventanaCerrada = true,
                    condonacion = ResultadoDeLaCondonacion.VENTA_NO_ESTA_EN_EL_TELEFONO,
                    ventaPresente = false,
                    pagosEnVentana = 0
                ),
                o
            )
        }
    }

    /**
     * Control positivo de S: la condonación entera antes del merge (cuenta 1,
     * la venta se conserva) o entera después (la venta ya no está, no se
     * escribe nada). Las dos dejan venta y pagos de acuerdo.
     */
    @Test
    fun `EAPP081 S control condonacion antes o despues del merge de la saldada`() {
        val antes = repetirSaldada {
            val r = condonarEnVentana()
            managerSaldada(null).applyByIds(SseKind.SALDOS, listOf(VENTA))
            observarSaldada(null, r)
        }
        imprimirSaldada("S-control-antes", antes)
        val guardada = Saldada(null, ResultadoDeLaCondonacion.GUARDADA, true, 1)
        antes.forEach { assertEquals(guardada, it) }

        val despues = repetirSaldada {
            managerSaldada(null).applyByIds(SseKind.SALDOS, listOf(VENTA))
            observarSaldada(null, condonarEnVentana())
        }
        imprimirSaldada("S-control-despues", despues)
        val rechazada = Saldada(
            ventanaCerrada = null,
            condonacion = ResultadoDeLaCondonacion.VENTA_NO_ESTA_EN_EL_TELEFONO,
            ventaPresente = false,
            pagosEnVentana = 0
        )
        despues.forEach { assertEquals(rechazada, it) }
    }

    // ─── Espera del abono mientras el sync aplica una página de 1000 ventas ──

    /**
     * Medición (n=[REPETICIONES]): cuánto espera un abono real, lanzado mientras
     * `syncNow` aplica una página de 1000 ventas venta por venta en transacción.
     * Imprime p50/p95/máx en microsegundos. Afirma sólo lo que no es tiempo: el
     * abono se registra, el sync termina bien y la venta queda 1230/PAGADO.
     */
    @Test
    fun `EAPP081 espera de un abono durante el merge de una pagina de 1000 ventas`() {
        val esperas = mutableListOf<Long>()
        var solapados = 0
        val finales = mutableListOf<Fila>()
        repeat(REPETICIONES) { i ->
            abrirBaseNueva()
            runBlocking(Dispatchers.IO) {
                withTimeout(TIEMPO_MAXIMO_MS * 3) {
                    val aplicando = CompletableDeferred<Unit>()
                    val m = managerDePagina(SaleDaoQueAvisaAlAplicar(db.saleDao(), aplicando))
                    val sync = async(Dispatchers.IO) { m.syncNow() }
                    aplicando.await()
                    val t0 = System.nanoTime()
                    val r = abonar("abono-eapp081-pagina-$i")
                    esperas += (System.nanoTime() - t0) / NANOS_POR_MICRO
                    if (!sync.isCompleted) solapados++
                    val out = sync.await()
                    assertTrue("sync: $out", out is SyncOutcome.Ok)
                    assertEquals(ResultadoDelAbono.REGISTRADO, r)
                    finales += leer()
                }
            }
        }
        val s = esperas.sorted()
        println("EAPP081 espera_abono_us=$esperas")
        println(
            "EAPP081 espera_abono_us p50=${percentil(s, 50)} p95=${percentil(s, 95)} " +
                "max=${s.last()} n=${s.size} abono_termino_con_el_sync_corriendo=$solapados/$REPETICIONES"
        )
        finales.forEach { assertEquals(Fila(SALDO_SERVIDOR - ABONO, PAGADO), it) }
    }

    // ─── Arnés ──────────────────────────────────────────────────────────────

    private data class Fila(val saldo: Double, val estado: String)

    /**
     * [ventanaCerrada]: el escritor seguía sin terminar [ESPERA_MS] después de
     * lanzarse con el proceso detenido (null = el caso no lo mide).
     * [trasProceso] = justo después del proceso con la ventana; [trasSegundo] =
     * tras el 2o merge.
     */
    private data class Observacion(
        val ventanaCerrada: Boolean?,
        val trasProceso: Fila,
        val trasSegundo: Fila
    )

    /** Corre [cuerpo] [REPETICIONES] veces, cada una con la base recién creada. */
    private fun repetir(caso: String, cuerpo: suspend () -> Observacion): List<Observacion> =
        (1..REPETICIONES).map {
            abrirBaseNueva()
            runBlocking(Dispatchers.IO) { withTimeout(TIEMPO_MAXIMO_MS) { cuerpo() } }
        }

    /** Imprime las [REPETICIONES] observaciones agrupadas y luego afirma cada una. */
    private fun afirmar(caso: String, obs: List<Observacion>, correcto: Observacion) {
        obs.groupingBy { it }.eachCount().forEach { (o, n) ->
            println(
                "EAPP081 $caso $n/${obs.size} ventana_cerrada=${o.ventanaCerrada} " +
                    "tras_proceso=${o.trasProceso.saldo}/${o.trasProceso.estado} " +
                    "tras_2o_merge=${o.trasSegundo.saldo}/${o.trasSegundo.estado}"
            )
        }
        assertEquals(REPETICIONES, obs.size)
        obs.forEachIndexed { i, o -> assertEquals("$caso repetición ${i + 1}", correcto, o) }
    }

    private fun abrirBaseNueva() {
        AppDatabase.clearInstance()
        context.deleteDatabase(NOMBRE_DE_LA_BASE)
        db = AppDatabase.getInstance(context)
        runBlocking { db.saleDao().insertAll(listOf(DTO.toEntity())) }
    }

    /**
     * Arranca [proceso] (que se detiene en [pausa]), lanza [escritor] en
     * paralelo, espera [ESPERA_MS] y anota en [ventana] si el escritor ya
     * terminó; suelta y espera a los dos. Devuelve la fila tras ambos.
     */
    private suspend fun carrera(
        proceso: suspend () -> Unit,
        pausa: Pausa,
        escritor: suspend () -> Unit,
        ventana: (Boolean) -> Unit = {}
    ): Fila = coroutineScope {
        val p = async(Dispatchers.IO) { proceso() }
        pausa.llego.await()
        val w = async(Dispatchers.IO) { escritor() }
        delay(ESPERA_MS)
        ventana(!w.isCompleted)
        assertTrue("el proceso sigue detenido mientras el escritor intenta", !p.isCompleted)
        pausa.suelta.complete(Unit)
        p.await()
        w.await()
        leer()
    }

    /** La carrera contra `mergeVentas`, seguida del 2o merge sin pausa. */
    private suspend fun carreraDelMerge(
        pausaEnLectura: Pausa? = null,
        pausaEnSuma: Pausa? = null,
        escritor: suspend () -> Unit
    ): Observacion {
        val pausa = checkNotNull(pausaEnLectura ?: pausaEnSuma)
        var cerrada = false
        val tras = carrera(
            proceso = {
                manager(
                    pausaEnLectura,
                    pausaEnSuma
                ).applyByIds(SseKind.SALDOS, listOf(VENTA))
            },
            pausa = pausa,
            escritor = escritor,
            ventana = { cerrada = it }
        )
        manager().applyByIds(SseKind.SALDOS, listOf(VENTA))
        return Observacion(cerrada, tras, leer())
    }

    private suspend fun controlAntes(escritor: suspend () -> Unit): Observacion {
        escritor()
        manager().applyByIds(SseKind.SALDOS, listOf(VENTA))
        val uno = leer()
        manager().applyByIds(SseKind.SALDOS, listOf(VENTA))
        return Observacion(null, uno, leer())
    }

    private suspend fun controlDespues(escritor: suspend () -> Unit): Observacion {
        manager().applyByIds(SseKind.SALDOS, listOf(VENTA))
        escritor()
        val uno = leer()
        manager().applyByIds(SseKind.SALDOS, listOf(VENTA))
        return Observacion(null, uno, leer())
    }

    /** [ventanaCerrada]: la condonación seguía sin terminar con el merge detenido. */
    private data class Saldada(
        val ventanaCerrada: Boolean?,
        val condonacion: ResultadoDeLaCondonacion,
        val ventaPresente: Boolean,
        val pagosEnVentana: Int
    )

    private fun repetirSaldada(cuerpo: suspend () -> Saldada): List<Saldada> =
        (1..REPETICIONES).map {
            abrirBaseNueva()
            runBlocking(Dispatchers.IO) { withTimeout(TIEMPO_MAXIMO_MS) { cuerpo() } }
        }

    private fun imprimirSaldada(caso: String, obs: List<Saldada>) {
        assertEquals(REPETICIONES, obs.size)
        obs.groupingBy { it }.eachCount().forEach { (o, n) ->
            println(
                "EAPP081 $caso $n/${obs.size} ventana_cerrada=${o.ventanaCerrada} " +
                    "condonacion=${o.condonacion} venta_presente=${o.ventaPresente} " +
                    "pagos_en_ventana=${o.pagosEnVentana}"
            )
        }
    }

    private suspend fun observarSaldada(cerrada: Boolean?, r: ResultadoDeLaCondonacion) = Saldada(
        cerrada,
        r,
        db.saleDao().findByDoctoCcId(VENTA) != null,
        db.paymentDao().countPagosDesde(VENTA, DESDE_SALDADA)
    )

    /** Merge de la saldada detenido tras `countPagosDesde`; condonación en la ventana. */
    private suspend fun carreraSaldada(): Saldada = coroutineScope {
        val pausa = Pausa()
        val p = async(Dispatchers.IO) {
            managerSaldada(pausa).applyByIds(SseKind.SALDOS, listOf(VENTA))
        }
        pausa.llego.await()
        val w = async(Dispatchers.IO) { condonarEnVentana() }
        delay(ESPERA_MS)
        val cerrada = !w.isCompleted
        assertTrue("el merge sigue detenido mientras la condonación intenta", !p.isCompleted)
        pausa.suelta.complete(Unit)
        p.await()
        observarSaldada(cerrada, w.await())
    }

    private fun managerSaldada(pausa: Pausa?) = CobranzaSyncManager(
        api = ApiSaldada,
        db = db,
        saleDao = db.saleDao(),
        paymentDao = PaymentDaoQuePausaTrasContar(db.paymentDao(), pausa),
        productDao = db.productDao(),
        syncStateDao = db.cobranzaSyncStateDao(),
        connectivity = SiempreEnLinea(context),
        userContextFlow = MutableStateFlow<UserContext?>(
            UserContext(zona = ZONA, fechaCargaInicial = Instant.parse(DESDE_SALDADA))
        ).asStateFlow(),
        cobranzaWriteMutex = CobranzaWriteMutex()
    )

    /** La condonación de producción, con fecha dentro de la ventana del cobrador. */
    private suspend fun condonarEnVentana(): ResultadoDeLaCondonacion =
        RegistroDeCondonacion(db).condonar(
            CondonacionFixtures.condonacion(
                id = "cond-eapp081-saldada",
                importe = CONDONADO,
                venta = VENTA
            ).copy(FECHA_HORA_PAGO = "2026-10-18T15:00:00Z")
        )

    private suspend fun leer(): Fila {
        val v = checkNotNull(db.saleDao().findByDoctoCcId(VENTA)) { "la venta no está" }
        return Fila(v.SALDO_REST, v.ESTADO_COBRANZA)
    }

    private fun contexto() = MutableStateFlow<UserContext?>(
        UserContext(zona = ZONA, fechaCargaInicial = null)
    ).asStateFlow()

    private fun manager(lectura: Pausa? = null, suma: Pausa? = null) = CobranzaSyncManager(
        api = ApiQueDevuelveElDto,
        db = db,
        saleDao = SaleDaoQuePausaTrasLeer(db.saleDao(), lectura),
        paymentDao = PaymentDaoQuePausaTrasSumar(db.paymentDao(), suma),
        productDao = db.productDao(),
        syncStateDao = db.cobranzaSyncStateDao(),
        connectivity = SiempreEnLinea(context),
        userContextFlow = contexto(),
        cobranzaWriteMutex = CobranzaWriteMutex()
    )

    private fun managerDePagina(saleDao: SaleDao) = CobranzaSyncManager(
        api = ApiDePaginas,
        db = db,
        saleDao = saleDao,
        paymentDao = db.paymentDao(),
        productDao = db.productDao(),
        syncStateDao = db.cobranzaSyncStateDao(),
        connectivity = SiempreEnLinea(context),
        userContextFlow = MutableStateFlow<UserContext?>(
            UserContext(zona = ZONA, fechaCargaInicial = Instant.parse("2026-10-11T00:00:00Z"))
        ).asStateFlow(),
        cobranzaWriteMutex = CobranzaWriteMutex()
    )

    private suspend fun reconciliar(pausa: Pausa?) {
        val out = CobranzaReconciler(
            api = ApiQueReconcilia,
            db = db,
            saleDao = SaleDaoQuePausaEnLaSegundaLista(db.saleDao(), pausa),
            paymentDao = db.paymentDao(),
            connectivity = SiempreEnLinea(context),
            userContextFlow = contexto(),
            cobranzaWriteMutex = CobranzaWriteMutex()
        ).reconcileNow()
        assertTrue("reconcile: $out", out is ReconcileOutcome.Ok)
    }

    // ─── Escritores reales (ninguno toma el candado del sync) ───────────────

    /** El abono por el adaptador de producción (`withTransaction` + `updateTotal`). */
    private suspend fun abonar(id: String = "abono-eapp081-0001"): ResultadoDelAbono {
        val adaptador = RegistroDeAbonoAdapter(
            db = db,
            saleDao = db.saleDao(),
            pagos = PaymentsLocalDataSource(db.paymentDao(), db.saleDao()),
            imagenes = db.paymentImageDao(),
            telemetry = RecordingTelemetry(clock),
            encolador = NoEncola,
            clock = clock,
            traerUsuario = { COBRADOR },
            pedirUbicacion = {},
            emailEnSesion = { "rosa@example.com" }
        )
        val r = adaptador.registrar(
            AbonoARegistrar(
                abonoId = id,
                ventaId = VENTA,
                importe = Money.of(BigDecimal("220.00")),
                metodo = MetodoDeCobro.EFECTIVO
            )
        )
        assertEquals(ResultadoDelAbono.REGISTRADO, r)
        return r
    }

    /**
     * La visita por el mismo par que escribe `RegistroDeVisitaAdapter.guardar`:
     * `db.withTransaction { insertVisitAndUpdateState(...) }`.
     */
    private suspend fun visitar(tipo: String) {
        val visitas = VisitsLocalDataSource(db.visitDao(), db.saleDao(), NoEncolaVisitas)
        val visita = VisitEntity(
            ID = "visita-eapp081-0001",
            CLIENTE_ID = CLIENTE,
            COBRADOR = "Cobrador de prueba",
            COBRADOR_ID = 77,
            FECHA = "2026-10-18T15:00:00Z",
            FORMA_COBRO_ID = 0,
            LAT = 0.0,
            LNG = 0.0,
            TIPO_VISITA = tipo,
            ZONA_CLIENTE_ID = ZONA,
            IMPTE_DOCTO_CC_ID = VENTA
        )
        db.withTransaction {
            visitas.insertVisitAndUpdateState(VENTA, visita, VisitStatusMapper.map(tipo))
        }
    }

    /** La condonación por su escritor de producción (`descontarSiAlcanza`). */
    private suspend fun condonar() {
        val r = RegistroDeCondonacion(db).condonar(
            CondonacionFixtures.condonacion(
                id = "cond-eapp081-0001",
                importe = CONDONADO,
                venta = VENTA
            )
        )
        assertEquals(ResultadoDeLaCondonacion.GUARDADA, r)
    }

    private fun percentil(ordenados: List<Long>, p: Int): Long = ordenados[
        kotlin.math.ceil(
            p / 100.0 * (ordenados.size - 1)
        ).toInt().coerceIn(0, ordenados.size - 1)
    ]

    // ─── Dobles (fakes a mano, sin MockK) ───────────────────────────────────

    /** Se detiene UNA vez: avisa en [llego] y espera [suelta]. */
    private class Pausa {
        val llego = CompletableDeferred<Unit>()
        val suelta = CompletableDeferred<Unit>()
        private val armada = AtomicBoolean(true)

        suspend fun aqui() {
            if (armada.getAndSet(false)) {
                llego.complete(Unit)
                suelta.await()
            }
        }
    }

    private class SaleDaoQuePausaTrasLeer(
        private val real: SaleDao,
        private val pausa: Pausa?
    ) : SaleDao by real {
        override suspend fun findByDoctoCcId(doctoCcId: Int): SaleEntity? {
            val leida = real.findByDoctoCcId(doctoCcId)
            pausa?.aqui()
            return leida
        }
    }

    private class PaymentDaoQuePausaTrasSumar(
        private val real: PaymentDao,
        private val pausa: Pausa?
    ) : PaymentDao by real {
        override suspend fun sumImporteNoReconocidoPorElServidor(doctoCcAcrId: Int): Double {
            val suma = real.sumImporteNoReconocidoPorElServidor(doctoCcAcrId)
            pausa?.aqui()
            return suma
        }
    }

    private class PaymentDaoQuePausaTrasContar(
        private val real: PaymentDao,
        private val pausa: Pausa?
    ) : PaymentDao by real {
        override suspend fun countPagosDesde(doctoCcAcrId: Int, fechaIso: String): Int {
            val n = real.countPagosDesde(doctoCcAcrId, fechaIso)
            pausa?.aqui()
            return n
        }
    }

    /**
     * La 1a llamada es el pre-check de digest de `reconcileNow`; la 2a es la
     * lista local de `reconcileSaldosViaIds`, ANTES de ir a la red. Ahí se detiene.
     */
    private class SaleDaoQuePausaEnLaSegundaLista(
        private val real: SaleDao,
        private val pausa: Pausa?
    ) : SaleDao by real {
        private val llamadas = AtomicInteger()
        override suspend fun getActiveIdsByZona(zonaId: Int): List<Int> {
            val ids = real.getActiveIdsByZona(zonaId)
            if (llamadas.incrementAndGet() == 2) pausa?.aqui()
            return ids
        }
    }

    /** Avisa (sin detener) cuando el sync ya va por la venta número [AVISO_EN] de la página. */
    private class SaleDaoQueAvisaAlAplicar(
        private val real: SaleDao,
        private val aplicando: CompletableDeferred<Unit>
    ) : SaleDao by real {
        private val n = AtomicInteger()
        override suspend fun findByDoctoCcId(doctoCcId: Int): SaleEntity? {
            if (n.incrementAndGet() == AVISO_EN) aplicando.complete(Unit)
            return real.findByDoctoCcId(doctoCcId)
        }
    }

    private class SiempreEnLinea(context: Context) : ConnectivityMonitor(context) {
        override fun isNetworkAvailable(): Boolean = true
        override val isConnected: Flow<Boolean> = flowOf(true)
    }

    private object NoEncola : PaymentsWorkEnqueuer {
        override fun enqueue(paymentId: String) = Unit
    }

    private object NoEncolaVisitas : VisitsWorkEnqueuer {
        override fun enqueue(visitId: String) = Unit
    }

    /** El servidor todavía no sabe del abono/condonación: devuelve siempre el mismo DTO. */
    private open class ApiFija : V2CobranzaApi {
        override suspend fun saldosByIds(zonaId: Int, ids: String): List<VentaDto> = listOf(DTO)
        override suspend fun pagosByIds(zonaId: Int, ids: String): List<PagoDto> = emptyList()
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
    }

    private object ApiQueDevuelveElDto : ApiFija()

    /** El servidor ya la da por saldada. */
    private object ApiSaldada : ApiFija() {
        override suspend fun saldosByIds(zonaId: Int, ids: String): List<VentaDto> =
            listOf(venta(VENTA, CLIENTE, "0.00", "Y00013662"))
    }

    /** Digest de pagos igual al local (vacío); el de saldos distinto; `/ids` trae la venta. */
    private object ApiQueReconcilia : ApiFija() {
        override suspend fun pagosDigest(zonaId: Int, desde: String?) =
            DigestResponse(0, "0", "0", null)
        override suspend fun saldosDigest(zonaId: Int, desde: String?) =
            DigestResponse(1, VENTA.toString(), VENTA.toString(), null)
        override suspend fun listSaldoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
            IdsResponse(listOf(VENTA), false)
    }

    /** Una página de 1000 pagos y una de 1000 ventas (la primera es [DTO]). */
    private object ApiDePaginas : ApiFija() {
        override suspend fun syncPagos(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ) = SyncPagosResponse(
            PAGOS_PAGINA,
            "2026-10-18T17:00:00.000000Z",
            "2026-10-18T17:00:01Z",
            false
        )
        override suspend fun syncVentas(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ) = SyncVentasResponse(
            VENTAS_PAGINA,
            "2026-10-18T17:00:00.000000Z",
            "2026-10-18T17:00:01Z",
            false
        )
    }

    private companion object {
        const val NOMBRE_DE_LA_BASE = "msp_db"
        const val REPETICIONES = 20
        const val TIEMPO_MAXIMO_MS = 30_000L
        const val ESPERA_MS = 300L
        const val AVISO_EN = 2
        const val NANOS_POR_MICRO = 1_000L
        const val VENTA = CondonacionFixtures.VENTA
        const val CLIENTE = 4821
        const val ZONA = 21
        const val SALDO_SERVIDOR = 1450.0
        const val ABONO = 220.0
        const val CONDONADO = 300.0
        const val DESDE_SALDADA = "2026-10-11T00:00:00Z"
        val PENDIENTE = EstadoCobranza.PENDIENTE.name
        val PAGADO = EstadoCobranza.PAGADO.name

        val clock = FakeClock(Instant.parse("2026-10-18T18:00:00Z"))

        val COBRADOR = User(
            ID = "u-eapp081",
            NOMBRE = "Cobrador de prueba",
            EMAIL = "cobrador@example.com",
            COBRADOR_ID = 77
        )

        fun venta(id: Int, cliente: Int, saldo: String, folio: String) = VentaDto(
            docto_cc_id = id,
            docto_pv_id = null,
            cliente_id = cliente,
            zona_cliente_id = ZONA,
            folio = folio,
            fecha_cargo = "2026-02-15T00:00:00Z",
            fecha_venta = null,
            precio_total = "3500.00",
            total_importe = "3500.00",
            impte_rest = saldo,
            saldo = saldo,
            num_pagos = 10,
            fecha_ult_pago = null,
            cargo_cancelado = false,
            updated_at = "2026-10-18T17:00:00.000000Z",
            cliente_nombre = "Maria Guadalupe Hernandez Lopez",
            limite_credito = null,
            cliente_notas = "",
            cobrador_id = 77,
            nombre_cobrador = "Cobrador de prueba",
            zona_nombre = "R/21",
            calle = "Av. Reforma 214",
            ciudad = "Tehuacan",
            estado = "Puebla",
            telefono = "",
            parcialidad = 350,
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

        val DTO = venta(VENTA, CLIENTE, "1450.00", "Y00013662")

        val VENTAS_PAGINA: List<VentaDto> = (0 until 1000).map { i ->
            if (i == 0) {
                DTO
            } else {
                venta(
                    9_000_000 + i,
                    50_000 + i / 2,
                    "%d.00".format(500 + (i % 30) * 100),
                    "Y%08d".format(20_000 + i)
                )
            }
        }

        val PAGOS_PAGINA: List<PagoDto> = (0 until 1000).map { i ->
            val v = VENTAS_PAGINA[i]
            PagoDto(
                impte_docto_cc_id = 80_000_000 + i,
                docto_cc_id = 70_100_000 + i,
                docto_cc_acr_id = v.docto_cc_id,
                cliente_id = v.cliente_id,
                zona_cliente_id = ZONA,
                folio = "P%08d".format(i),
                concepto_cc_id = 87327,
                fecha = "2026-10-1${i % 8}T16:00:00Z",
                importe = "150.00",
                impuesto = "0",
                lat = "18.46",
                lon = "-97.39",
                cancelado = false,
                aplicado = true,
                updated_at = "2026-10-18T17:00:00.000000Z",
                cobrador = "Cobrador de prueba",
                cobrador_id = 77,
                nombre_cliente = "Maria Guadalupe Hernandez Lopez",
                forma_cobro_id = 157
            )
        }
    }
}
