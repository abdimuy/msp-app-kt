package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PaymentsWorkEnqueuer
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.entities.SaleWithProductsEntity
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.core.sync.cobranza.CobranzaSyncManager
import com.example.msp_app.core.sync.cobranza.CobranzaWriteMutex
import com.example.msp_app.core.sync.cobranza.SseKind
import com.example.msp_app.core.sync.cobranza.UserContext
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.data.api.services.cobranza.DigestResponse
import com.example.msp_app.data.api.services.cobranza.IdsResponse
import com.example.msp_app.data.api.services.cobranza.PagoDto
import com.example.msp_app.data.api.services.cobranza.SyncPagosResponse
import com.example.msp_app.data.api.services.cobranza.SyncVentasResponse
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.VentaDto
import com.example.msp_app.data.api.services.cobranza.toEntity
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.pagos.CondonacionFixtures
import com.example.msp_app.data.pagos.RegistroDeAbonoAdapter
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.port.AbonoARegistrar
import com.example.msp_app.feature.pagos.domain.port.ResultadoDelAbono
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
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
import org.junit.Test

/**
 * E-APP-082: "inicio de semana" ([InicioDeSemanaDeVentas], llamado por
 * `AuthViewModel.updateStartOfWeekDate`) lee toda `sales` y la reescribe
 * completa. La lectura, el mapeo y el `replaceAll` van en UNA transacción: un
 * abono o un saldo nuevo del servidor que confirmen mientras tanto esperan a que
 * termine y se aplican DESPUÉS, en vez de quedar pisados por la foto vieja.
 *
 * Room REAL sobre archivo con el builder de producción. La pausa va en un
 * [SaleDao] envuelto por delegación, justo después de `getAll`. El escritor real
 * se lanza en paralelo; tras [ESPERA_MS] se anota si ya terminó (con la ventana
 * cerrada sigue bloqueado), se suelta y se afirma. [REPETICIONES] por caso.
 *
 * Medido sin el arreglo (compuerta ANTES, 2026-10-19): abono → 1450/PENDIENTE y
 * saldo del servidor → 1450, 10/10.
 */
class InicioDeSemanaCarreraEAPP082Test : RobolectricTestBase() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    @After
    fun cerrarBase() {
        AppDatabase.clearInstance()
        context.deleteDatabase(NOMBRE_DE_LA_BASE)
    }

    @Test
    fun `EAPP082 abono que confirma mientras el inicio de semana lee no se pierde`() {
        val obs = repetir { carrera { abonar() } }
        afirmar("abono", obs, Observacion(true, Fila(SALDO_SERVIDOR - ABONO, PAGADO)))
    }

    @Test
    fun `EAPP082 saldo del servidor que entra mientras el inicio de semana lee no se pierde`() {
        val obs = repetir { carrera { mergeDelServidor() } }
        afirmar("merge-servidor", obs, Observacion(true, Fila(SALDO_NUEVO, PENDIENTE)))
    }

    @Test
    fun `EAPP082 control escritor antes o despues del inicio de semana`() {
        afirmar(
            "control-abono-despues",
            repetir {
                iniciarSemana()
                abonar()
                Observacion(null, leer())
            },
            Observacion(null, Fila(SALDO_SERVIDOR - ABONO, PAGADO))
        )
        afirmar(
            "control-merge-despues",
            repetir {
                iniciarSemana()
                mergeDelServidor()
                Observacion(null, leer())
            },
            Observacion(null, Fila(SALDO_NUEVO, PENDIENTE))
        )
        // Antes: el abono queda, y el inicio de semana sólo regresa el estado.
        afirmar(
            "control-abono-antes",
            repetir {
                abonar()
                iniciarSemana()
                Observacion(null, leer())
            },
            Observacion(null, Fila(SALDO_SERVIDOR - ABONO, PENDIENTE))
        )
    }

    // ─── Arnés ──────────────────────────────────────────────────────────────

    private data class Fila(val saldo: Double, val estado: String)
    private data class Observacion(val ventanaCerrada: Boolean?, val fila: Fila)

    private fun repetir(cuerpo: suspend () -> Observacion): List<Observacion> =
        (1..REPETICIONES).map {
            abrirBaseNueva()
            runBlocking(Dispatchers.IO) { withTimeout(TIEMPO_MAXIMO_MS) { cuerpo() } }
        }

    private fun afirmar(caso: String, obs: List<Observacion>, correcto: Observacion) {
        obs.groupingBy { it }.eachCount().forEach { (o, n) ->
            println(
                "EAPP082 $caso $n/${obs.size} ventana_cerrada=${o.ventanaCerrada} final=${o.fila.saldo}/${o.fila.estado}"
            )
        }
        assertEquals(REPETICIONES, obs.size)
        obs.forEachIndexed { i, o -> assertEquals("$caso repetición ${i + 1}", correcto, o) }
    }

    private fun abrirBaseNueva() {
        AppDatabase.clearInstance()
        context.deleteDatabase(NOMBRE_DE_LA_BASE)
        db = AppDatabase.getInstance(context)
        runBlocking {
            db.saleDao().insertAll(
                listOf(DTO.toEntity().copy(ESTADO_COBRANZA = EstadoCobranza.VOLVER_VISITAR.name))
            )
        }
    }

    private suspend fun iniciarSemana(pausa: Pausa? = null) {
        InicioDeSemanaDeVentas(
            db,
            SaleDaoQuePausaTrasGetAll(db.saleDao(), pausa)
        ).reiniciarEstadosDeCobranza()
    }

    private suspend fun carrera(escritor: suspend () -> Unit): Observacion = coroutineScope {
        val pausa = Pausa()
        val p = async(Dispatchers.IO) { iniciarSemana(pausa) }
        pausa.llego.await()
        val w = async(Dispatchers.IO) { escritor() }
        delay(ESPERA_MS)
        val cerrada = !w.isCompleted
        assertTrue("el inicio de semana sigue detenido", !p.isCompleted)
        pausa.suelta.complete(Unit)
        p.await()
        w.await()
        Observacion(cerrada, leer())
    }

    private suspend fun leer(): Fila {
        val v = checkNotNull(db.saleDao().findByDoctoCcId(VENTA)) { "la venta no está" }
        return Fila(v.SALDO_REST, v.ESTADO_COBRANZA)
    }

    private suspend fun abonar() {
        val r = RegistroDeAbonoAdapter(
            db = db,
            saleDao = db.saleDao(),
            pagos = PaymentsLocalDataSource(db.paymentDao(), db.saleDao()),
            imagenes = db.paymentImageDao(),
            telemetry = RecordingTelemetry(clock),
            encolador = NoEncola,
            clock = clock,
            traerUsuario = { COBRADOR },
            emailEnSesion = { "rosa@example.com" }
        ).registrar(
            AbonoARegistrar(
                abonoId = "abono-eapp082-0001",
                ventaId = VENTA,
                importe = Money.of(BigDecimal("220.00")),
                metodo = MetodoDeCobro.EFECTIVO
            )
        )
        assertEquals(ResultadoDelAbono.REGISTRADO, r)
    }

    /** El sync real por la ruta by-ids de SALDOS, con el saldo nuevo del servidor. */
    private suspend fun mergeDelServidor() {
        CobranzaSyncManager(
            api = ApiConSaldoNuevo,
            db = db,
            saleDao = db.saleDao(),
            paymentDao = db.paymentDao(),
            productDao = db.productDao(),
            syncStateDao = db.cobranzaSyncStateDao(),
            connectivity = SiempreEnLinea(context),
            userContextFlow = MutableStateFlow<UserContext?>(
                UserContext(zona = ZONA, fechaCargaInicial = null)
            ).asStateFlow(),
            cobranzaWriteMutex = CobranzaWriteMutex()
        ).applyByIds(SseKind.SALDOS, listOf(VENTA))
    }

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

    private class SaleDaoQuePausaTrasGetAll(
        private val real: SaleDao,
        private val pausa: Pausa?
    ) : SaleDao by real {
        override suspend fun getAll(): List<SaleWithProductsEntity> {
            val todas = real.getAll()
            pausa?.aqui()
            return todas
        }
    }

    private class SiempreEnLinea(context: Context) : ConnectivityMonitor(context) {
        override fun isNetworkAvailable(): Boolean = true
        override val isConnected: Flow<Boolean> = flowOf(true)
    }

    private object NoEncola : PaymentsWorkEnqueuer {
        override fun enqueue(paymentId: String) = Unit
    }

    private object ApiConSaldoNuevo : V2CobranzaApi {
        override suspend fun saldosByIds(zonaId: Int, ids: String): List<VentaDto> =
            listOf(DTO_NUEVO)
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

    private companion object {
        const val NOMBRE_DE_LA_BASE = "msp_db"
        const val REPETICIONES = 20
        const val TIEMPO_MAXIMO_MS = 30_000L
        const val ESPERA_MS = 300L
        const val VENTA = CondonacionFixtures.VENTA
        const val ZONA = 21
        const val SALDO_SERVIDOR = 1450.0
        const val SALDO_NUEVO = 1000.0
        const val ABONO = 220.0
        val PENDIENTE = EstadoCobranza.PENDIENTE.name
        val PAGADO = EstadoCobranza.PAGADO.name

        val clock = FakeClock(Instant.parse("2026-10-18T18:00:00Z"))

        val COBRADOR = User(
            ID = "u-eapp082",
            NOMBRE = "Cobrador de prueba",
            EMAIL = "cobrador@example.com",
            COBRADOR_ID = 77
        )

        val DTO = VentaDto(
            docto_cc_id = VENTA,
            docto_pv_id = null,
            cliente_id = 4821,
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

        val DTO_NUEVO = DTO.copy(
            saldo = "1000.00",
            impte_rest = "1000.00",
            updated_at = "2026-10-18T17:30:00.000000Z"
        )
    }
}
