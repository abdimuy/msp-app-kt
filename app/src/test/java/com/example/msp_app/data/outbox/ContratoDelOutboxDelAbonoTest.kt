package com.example.msp_app.data.outbox

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PaymentsWorkEnqueuer
import com.example.msp_app.core.database.dao.payment.PaymentImageDao
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.entities.PaymentImageEntity
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.pagos.ComprobantesDeAbonoAdapter
import com.example.msp_app.data.pagos.PREFIJO_COMPROBANTE
import com.example.msp_app.data.pagos.RegistroDeAbonoAdapter
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.domain.model.ComprobanteDelAbono
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.port.AbonoARegistrar
import com.example.msp_app.feature.pagos.domain.port.ResultadoDelAbono
import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.Before

/**
 * El outbox **del dinero**, sometido al contrato compartido.
 *
 * Nada acá es una copia del camino de escritura: es `RegistroDeAbonoAdapter` de
 * producción, sobre el `PaymentsLocalDataSource` de producción, sobre la base
 * Room del contrato. Lo único falso son las hojas —el encolador de WorkManager y
 * el arranque del servicio de ubicación—, y las dos son grabadoras, no stubs
 * mudos: el contrato las mide.
 */
class ContratoDelOutboxDelAbonoTest : ContratoDelOutbox() {

    override val codigoComprobanteNoGuardado: String = PagosTelemetria.CODE_ABONO_SIN_COMPROBANTES
    override val codigoBarridoSinSubir: String = PagosTelemetria.CODE_ABONO_FOTO_BARRIDA_SIN_SUBIR

    @Before
    fun sembrarLaVenta() = runTest {
        db.saleDao().insertAll(listOf(venta()))
    }

    override fun caminoSano(): CaminoDeEscritura = Sujeto()

    override fun caminosConAcompananteRoto(): List<CaminoDeEscritura> = listOf(
        // La foto: escribir la fila de comprobante truena DESPUES del commit.
        Sujeto(imagenes = { DaoDeImagenesQueTruena(db.paymentImageDao()) }),
        // La ubicacion: arrancar `UpdateLocationService` puede lanzar en
        // Android 12+ con la app en segundo plano. Hasta el Arreglo C ese fallo
        // se llevaba puesto el encolado del pago, porque el encolado colgaba de
        // que el servicio arrancara.
        Sujeto(pedirUbicacion = { error("startForegroundService rechazado") })
    )

    override fun caminoConEncoladorQueTruena(): CaminoDeEscritura =
        Sujeto(encolador = EncoladorQueTruena())

    override fun caminoQueSeCancelaAlComitear(job: Job): CaminoDeEscritura =
        // `updateTotal` es la ULTIMA escritura de la transaccion del abono.
        Sujeto(saleDao = { SaleDaoQueCancela(db.saleDao(), job) })

    override suspend fun sembrarComprobanteSinPadre(id: String, subidaEn: String?) {
        val archivo = File(context.filesDir, "$PREFIJO_COMPROBANTE$id.jpg")
        archivo.writeBytes(byteArrayOf(1, 2, 3))
        db.paymentImageDao().insertAll(
            listOf(
                PaymentImageEntity(
                    ID = id,
                    PAGO_ID = "pago-que-el-sync-rellaveo",
                    URI = archivo.absolutePath,
                    MIME = "image/jpeg",
                    ORDEN = 0,
                    CREADA_EN = CREADA_HACE_MUCHO,
                    SUBIDA_EN = subidaEn
                )
            )
        )
    }

    override suspend fun barrerComprobantes() {
        ComprobantesDeAbonoAdapter(
            context = context,
            imagenes = db.paymentImageDao(),
            telemetry = telemetria,
            clock = clock
        ).barrerHuerfanos()
    }

    /** El sujeto: el adaptador de producción con las hojas que el test controla. */
    private inner class Sujeto(
        private val encolador: PaymentsWorkEnqueuer = EncoladorQueGraba(),
        imagenes: () -> PaymentImageDao = { db.paymentImageDao() },
        saleDao: () -> SaleDao = { db.saleDao() },
        pedirUbicacion: (String) -> Unit = {}
    ) : CaminoDeEscritura {

        private val adaptador = RegistroDeAbonoAdapter(
            db = db,
            saleDao = db.saleDao(),
            pagos = PaymentsLocalDataSource(db.paymentDao(), saleDao()),
            imagenes = imagenes(),
            telemetry = telemetria,
            encolador = encolador,
            clock = clock,
            traerUsuario = { COBRADOR },
            pedirUbicacion = pedirUbicacion
        )

        override suspend fun escribir(id: String, comprobantes: List<String>): Boolean =
            adaptador.registrar(
                AbonoARegistrar(
                    abonoId = id,
                    ventaId = VENTA_ID,
                    importe = Money.of(BigDecimal("10")),
                    metodo = MetodoDeCobro.EFECTIVO,
                    comprobantes = comprobantes.map {
                        ComprobanteDelAbono(id = it, archivo = "/files/$it.jpg", mime = "image/jpeg")
                    }
                )
            ) == ResultadoDelAbono.REGISTRADO

        override suspend fun filasDelHecho(id: String): Int =
            if (db.paymentDao().getPaymentById(id) == null) 0 else 1

        override suspend fun filasDeComprobante(id: String): Int =
            db.paymentImageDao().getByPagoId(id).size

        override fun encolados(): List<String> = (encolador as? EncoladorQueGraba)?.encolados
            ?: emptyList()
    }

    /** Fake a mano (sin MockK): lista pública de lo encolado, en orden. */
    private class EncoladorQueGraba : PaymentsWorkEnqueuer {
        val encolados: MutableList<String> = mutableListOf()

        override fun enqueue(paymentId: String) {
            encolados += paymentId
        }
    }

    private class EncoladorQueTruena : PaymentsWorkEnqueuer {
        override fun enqueue(paymentId: String): Unit = error("workmanager no acepto el trabajo")
    }

    /**
     * `PaymentImageDao` real salvo por el insert, que truena — la capa de fotos
     * fallando de la peor forma posible: **después** de que el dinero ya está
     * commiteado. Delega todo lo demás, así que el contrato sigue leyendo la
     * base de verdad.
     */
    private class DaoDeImagenesQueTruena(real: PaymentImageDao) : PaymentImageDao by real {
        override suspend fun insertAll(imagenes: List<PaymentImageEntity>): Unit =
            error("room se cayo al escribir el comprobante")
    }

    /**
     * `SaleDao` real que **cancela [job] justo después** de descontar el saldo,
     * que es la última escritura de la transacción del abono. No lanza: la
     * transacción termina bien y la cancelación queda pedida, que es el instante
     * exacto en que se abre la ventana entre el commit y el encolado.
     */
    private class SaleDaoQueCancela(private val real: SaleDao, private val job: Job) :
        SaleDao by real {
        override suspend fun updateTotal(
            saleId: Int,
            amount: Double,
            estadoCobranza: EstadoCobranza
        ) {
            real.updateTotal(saleId, amount, estadoCobranza)
            job.cancel()
        }
    }

    private fun venta() = SaleEntity(
        DOCTO_CC_ACR_ID = VENTA_ID,
        DOCTO_CC_ID = 91027,
        FOLIO = "V-5188",
        CLIENTE_ID = CLIENTE_ID,
        APLICADO = "S",
        COBRADOR_ID = 77,
        CLIENTE = "Victoria Flores Olmedo",
        ZONA_CLIENTE_ID = 25,
        LIMITE_CREDITO = 0.0,
        NOTAS = "",
        ZONA_NOMBRE = "Centro",
        IMPORTE_PAGO_PROMEDIO = 220.0,
        TOTAL_IMPORTE = 6310.0,
        NUM_IMPORTES = 20,
        FECHA = "2026-04-14T00:00:00Z",
        PARCIALIDAD = 220,
        ENGANCHE = 900.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        VENDEDOR_1 = "",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = 6310.0,
        IMPTE_REST = 1450.0,
        SALDO_REST = 1450.0,
        FECHA_ULT_PAGO = null,
        CALLE = "C. Hidalgo 214",
        CIUDAD = "Tehuacan",
        ESTADO = "Puebla",
        TELEFONO = "2381627597",
        NOMBRE_COBRADOR = "Rosa Elena Martinez Vazquez",
        ESTADO_COBRANZA = "PENDIENTE",
        DIA_COBRANZA = "LUNES",
        DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 5200.0,
        AVAL_O_RESPONSABLE = "",
        FREC_PAGO = "SEMANAL"
    )

    private companion object {
        const val VENTA_ID = 77188
        const val CLIENTE_ID = 5021

        val COBRADOR = User(
            ID = "u-1",
            NOMBRE = "Rosa Elena Martinez Vazquez",
            EMAIL = "rosa@example.com",
            COBRADOR_ID = 77
        )
    }
}
