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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MEDICIÓN de E-APP-081 (no es un arreglo): `CobranzaSyncManager.mergeVentas`
 * no va en transacción. Lee la venta (`findByDoctoCcId`), suma lo no reconocido
 * (`sumImporteNoReconocidoPorElServidor`) y hace REPLACE (`insertAll`) copiando
 * `ESTADO_COBRANZA` de la fila que leyó.
 *
 * Método:
 *  - Room REAL sobre archivo, con el builder de producción
 *    ([AppDatabase.getInstance] → `buildDatabase`), en Robolectric.
 *  - `mergeVentas` se ejercita por su camino real: [CobranzaSyncManager.applyByIds]
 *    con `SALDOS` (la ruta SSE by-ids), contra un API falso que devuelve el DTO.
 *  - La pausa NO toca producción: el manager recibe un [SaleDao]/[PaymentDao]
 *    envuelto por delegación de Kotlin que, DESPUÉS de que la lectura real
 *    devolvió, se detiene en un [CompletableDeferred]. En esa ventana el escritor
 *    real (abono, visita o condonación) confirma, y luego se suelta el merge.
 *  - El segundo merge (el "siguiente tick") usa un manager SIN pausa y el mismo
 *    DTO, por la misma ruta.
 *  - Control positivo: la misma secuencia sin pausa (escritor antes o después del
 *    merge completo) deja los valores correctos. Cada caso se repite
 *    [REPETICIONES] veces, cada vez con la base recién creada.
 *
 * Las afirmaciones de los casos con pausa fijan lo que se MIDIÓ, no lo deseable:
 * cuando se arregle E-APP-081 deben ponerse rojas y cambiarse por los valores del
 * control.
 */
class MergeVentasCarreraEAPP081Test : RobolectricTestBase() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    @After
    fun cerrarBase() {
        AppDatabase.clearInstance()
        context.deleteDatabase(NOMBRE_DE_LA_BASE)
    }

    // ─── H1: abono que confirma entre la suma y el REPLACE ──────────────────

    @Test
    fun `EAPP081 H1 abono entre la suma y el REPLACE`() {
        val obs = repetir("H1") {
            val tras = carrera(pausaEnSuma = Pausa()) { abonar() }
            Observacion(tras.first, tras.second)
        }
        imprimir("H1", obs)
        // Correcto (control): 1230.0 / PAGADO tras ambos merges.
        val o = obs.first()
        obs.forEach { assertEquals(o, it) }
        assertEquals(Fila(SALDO_SERVIDOR, PENDIENTE), o.first)
        assertEquals(Fila(SALDO_SERVIDOR - ABONO, PENDIENTE), o.second)
    }

    /** H1, segunda ventana: el abono confirma entre la lectura de la fila y la suma. */
    @Test
    fun `EAPP081 H1b abono entre la lectura de la fila y la suma`() {
        val obs = repetir("H1b") {
            val tras = carrera(pausaEnLectura = Pausa()) { abonar() }
            Observacion(tras.first, tras.second)
        }
        imprimir("H1b", obs)
        val o = obs.first()
        obs.forEach { assertEquals(o, it) }
        assertEquals(Fila(SALDO_SERVIDOR - ABONO, PENDIENTE), o.first)
        assertEquals(Fila(SALDO_SERVIDOR - ABONO, PENDIENTE), o.second)
    }

    @Test
    fun `EAPP081 H1 control abono antes o despues del merge completo`() {
        val antes = repetir("H1-control-antes") { controlAntes { abonar() } }
        val despues = repetir("H1-control-despues") { controlDespues { abonar() } }
        imprimir("H1-control-antes", antes)
        imprimir("H1-control-despues", despues)
        val correcto = Fila(SALDO_SERVIDOR - ABONO, PAGADO)
        (antes + despues).forEach { assertEquals(Observacion(correcto, correcto), it) }
    }

    // ─── H2: visita que cambia ESTADO_COBRANZA entre la lectura y el REPLACE ─

    @Test
    fun `EAPP081 H2 visita de alcance cliente entre la lectura y el REPLACE`() {
        assertEquals(VisitScope.CLIENTE, VisitScopeMapper.map(Constants.NO_SE_ENCONTRABA, false))
        val obs = repetir("H2-cliente") {
            val tras = carrera(pausaEnLectura = Pausa()) { visitar(Constants.NO_SE_ENCONTRABA) }
            Observacion(tras.first, tras.second)
        }
        imprimir("H2-cliente", obs)
        // Correcto (control): VOLVER_VISITAR tras ambos merges.
        val o = obs.first()
        obs.forEach { assertEquals(o, it) }
        assertEquals(Fila(SALDO_SERVIDOR, PENDIENTE), o.first)
        assertEquals(Fila(SALDO_SERVIDOR, PENDIENTE), o.second)
    }

    @Test
    fun `EAPP081 H2 visita de alcance venta entre la lectura y el REPLACE`() {
        assertEquals(VisitScope.VENTA, VisitScopeMapper.map(Constants.NO_VA_A_DAR_PAGO, false))
        val obs = repetir("H2-venta") {
            val tras = carrera(pausaEnLectura = Pausa()) { visitar(Constants.NO_VA_A_DAR_PAGO) }
            Observacion(tras.first, tras.second)
        }
        imprimir("H2-venta", obs)
        val o = obs.first()
        obs.forEach { assertEquals(o, it) }
        assertEquals(Fila(SALDO_SERVIDOR, PENDIENTE), o.first)
        assertEquals(Fila(SALDO_SERVIDOR, PENDIENTE), o.second)
    }

    @Test
    fun `EAPP081 H2 control visita antes o despues del merge completo`() {
        for (tipo in listOf(Constants.NO_SE_ENCONTRABA, Constants.NO_VA_A_DAR_PAGO)) {
            val esperado = VisitStatusMapper.map(tipo).name
            val antes = repetir("H2-control-antes-$tipo") { controlAntes { visitar(tipo) } }
            val despues = repetir("H2-control-despues-$tipo") { controlDespues { visitar(tipo) } }
            imprimir("H2-control-antes-$tipo", antes)
            imprimir("H2-control-despues-$tipo", despues)
            val correcto = Fila(SALDO_SERVIDOR, esperado)
            (antes + despues).forEach { assertEquals(Observacion(correcto, correcto), it) }
        }
    }

    // ─── H3: condonación entre la suma y el REPLACE ─────────────────────────

    @Test
    fun `EAPP081 H3 condonacion entre la suma y el REPLACE`() {
        val obs = repetir("H3") {
            val tras = carrera(pausaEnSuma = Pausa()) { condonar() }
            Observacion(tras.first, tras.second)
        }
        imprimir("H3", obs)
        // Correcto (control): 1150.0 / PAGADO tras ambos merges.
        val o = obs.first()
        obs.forEach { assertEquals(o, it) }
        assertEquals(Fila(SALDO_SERVIDOR, PENDIENTE), o.first)
        assertEquals(Fila(SALDO_SERVIDOR - CONDONADO, PENDIENTE), o.second)
    }

    @Test
    fun `EAPP081 H3 control condonacion antes o despues del merge completo`() {
        val antes = repetir("H3-control-antes") { controlAntes { condonar() } }
        val despues = repetir("H3-control-despues") { controlDespues { condonar() } }
        imprimir("H3-control-antes", antes)
        imprimir("H3-control-despues", despues)
        val correcto = Fila(SALDO_SERVIDOR - CONDONADO, PAGADO)
        (antes + despues).forEach { assertEquals(Observacion(correcto, correcto), it) }
    }

    // ─── Arnés ──────────────────────────────────────────────────────────────

    private data class Fila(val saldo: Double, val estado: String)

    /** `first` = justo después del merge con la ventana; `second` = tras el 2o merge. */
    private data class Observacion(val first: Fila, val second: Fila)

    /** Corre [cuerpo] [REPETICIONES] veces, cada una con la base recién creada. */
    private fun repetir(caso: String, cuerpo: suspend () -> Observacion): List<Observacion> =
        (1..REPETICIONES).map {
            abrirBaseNueva()
            runBlocking(Dispatchers.IO) { withTimeout(TIEMPO_MAXIMO_MS) { cuerpo() } }
        }.also { println("EAPP081 $caso corridas=${it.size}/$REPETICIONES") }

    private fun imprimir(caso: String, obs: List<Observacion>) {
        obs.forEachIndexed { i, o ->
            println(
                "EAPP081 $caso #${i + 1} tras_replace=${o.first.saldo}/${o.first.estado} " +
                    "tras_2o_merge=${o.second.saldo}/${o.second.estado}"
            )
        }
    }

    private fun abrirBaseNueva() {
        AppDatabase.clearInstance()
        context.deleteDatabase(NOMBRE_DE_LA_BASE)
        db = AppDatabase.getInstance(context)
        runBlocking { db.saleDao().insertAll(listOf(DTO.toEntity())) }
    }

    /**
     * Arranca el merge con la pausa puesta, espera a que llegue a ella, confirma
     * [escritor] por su camino real, suelta, y lee. Luego un segundo merge sin
     * pausa con el mismo DTO, y vuelve a leer.
     */
    private suspend fun carrera(
        pausaEnLectura: Pausa? = null,
        pausaEnSuma: Pausa? = null,
        escritor: suspend () -> Unit
    ): Pair<Fila, Fila> = kotlinx.coroutines.coroutineScope {
        val pausa = checkNotNull(pausaEnLectura ?: pausaEnSuma)
        val conPausa = manager(pausaEnLectura, pausaEnSuma)
        val merge = async(Dispatchers.IO) { conPausa.applyByIds(SseKind.SALDOS, listOf(VENTA)) }
        pausa.llego.await()
        escritor()
        assertTrue("el merge sigue detenido mientras escribe el otro", !merge.isCompleted)
        pausa.suelta.complete(Unit)
        merge.await()
        val trasReplace = leer()
        manager().applyByIds(SseKind.SALDOS, listOf(VENTA))
        trasReplace to leer()
    }

    private suspend fun controlAntes(escritor: suspend () -> Unit): Observacion {
        escritor()
        manager().applyByIds(SseKind.SALDOS, listOf(VENTA))
        val uno = leer()
        manager().applyByIds(SseKind.SALDOS, listOf(VENTA))
        return Observacion(uno, leer())
    }

    private suspend fun controlDespues(escritor: suspend () -> Unit): Observacion {
        manager().applyByIds(SseKind.SALDOS, listOf(VENTA))
        escritor()
        val uno = leer()
        manager().applyByIds(SseKind.SALDOS, listOf(VENTA))
        return Observacion(uno, leer())
    }

    private suspend fun leer(): Fila {
        val v = checkNotNull(db.saleDao().findByDoctoCcId(VENTA))
        return Fila(v.SALDO_REST, v.ESTADO_COBRANZA)
    }

    private fun manager(lectura: Pausa? = null, suma: Pausa? = null) = CobranzaSyncManager(
        api = ApiQueDevuelveElDto,
        db = db,
        saleDao = SaleDaoQuePausaTrasLeer(db.saleDao(), lectura),
        paymentDao = PaymentDaoQuePausaTrasSumar(db.paymentDao(), suma),
        productDao = db.productDao(),
        syncStateDao = db.cobranzaSyncStateDao(),
        connectivity = SiempreEnLinea(context),
        userContextFlow = MutableStateFlow<UserContext?>(
            UserContext(zona = ZONA, fechaCargaInicial = null)
        ).asStateFlow(),
        cobranzaWriteMutex = CobranzaWriteMutex()
    )

    // ─── Escritores reales ──────────────────────────────────────────────────

    /** El abono por el adaptador de producción (`withTransaction` + `updateTotal`). */
    private suspend fun abonar() {
        val adaptador = RegistroDeAbonoAdapter(
            db = db,
            saleDao = db.saleDao(),
            pagos = PaymentsLocalDataSource(db.paymentDao(), db.saleDao()),
            imagenes = db.paymentImageDao(),
            telemetry = RecordingTelemetry(clock),
            encolador = NoEncola,
            clock = clock,
            traerUsuario = { COBRADOR },
            pedirUbicacion = {}
        )
        val r = adaptador.registrar(
            AbonoARegistrar(
                abonoId = "abono-eapp081-0001",
                ventaId = VENTA,
                importe = Money.of(BigDecimal("220.00")),
                metodo = MetodoDeCobro.EFECTIVO
            )
        )
        assertEquals(ResultadoDelAbono.REGISTRADO, r)
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
    private object ApiQueDevuelveElDto : V2CobranzaApi {
        override suspend fun saldosByIds(zonaId: Int, ids: String): List<VentaDto> = listOf(DTO)
        override suspend fun pagosByIds(zonaId: Int, ids: String) = error("no se usa")
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
        override suspend fun listPagoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
            error("no se usa")
        override suspend fun listSaldoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
            error("no se usa")
    }

    private companion object {
        const val NOMBRE_DE_LA_BASE = "msp_db"
        const val REPETICIONES = 10
        const val TIEMPO_MAXIMO_MS = 20_000L
        const val VENTA = CondonacionFixtures.VENTA
        const val CLIENTE = 4821
        const val ZONA = 21
        const val SALDO_SERVIDOR = 1450.0
        const val ABONO = 220.0
        const val CONDONADO = 300.0
        val PENDIENTE = EstadoCobranza.PENDIENTE.name
        val PAGADO = EstadoCobranza.PAGADO.name

        val clock = FakeClock(Instant.parse("2026-10-18T18:00:00Z"))

        val COBRADOR = User(
            ID = "u-eapp081",
            NOMBRE = "Cobrador de prueba",
            EMAIL = "cobrador@example.com",
            COBRADOR_ID = 77
        )

        val DTO = VentaDto(
            docto_cc_id = VENTA,
            docto_pv_id = null,
            cliente_id = CLIENTE,
            zona_cliente_id = ZONA,
            folio = "Y00013662",
            fecha_cargo = "2026-02-15T00:00:00Z",
            fecha_venta = null,
            precio_total = "3500.00",
            total_importe = "3500.00",
            impte_rest = "1450.00",
            saldo = "1450.00",
            num_pagos = 10,
            fecha_ult_pago = null,
            cargo_cancelado = false,
            updated_at = "2026-10-18T17:00:00.000000Z",
            cliente_nombre = "Cliente de prueba",
            limite_credito = null,
            cliente_notas = "",
            cobrador_id = 77,
            nombre_cobrador = "Cobrador de prueba",
            zona_nombre = "R/21",
            calle = "Calle de prueba 1",
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
    }
}
