package com.example.msp_app.data.outbox

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.dao.visit.VisitImageDao
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.database.entities.VisitImageEntity
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.visitas.ComprobantesDeVisitaAdapter
import com.example.msp_app.data.visitas.PREFIJO_COMPROBANTE_VISITA
import com.example.msp_app.data.visitas.RegistroDeVisitaAdapter
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import com.example.msp_app.feature.visitas.domain.model.ComprobanteDeVisita
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import com.example.msp_app.feature.visitas.domain.port.VisitaARegistrar
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.Before

/**
 * El outbox **de la visita**, sometido al MISMO contrato.
 *
 * Existe para que el contrato no sea una prueba del pago con otro nombre: las
 * propiedades que este plan pagó por aprender de este lado —el `NonCancellable`,
 * el encolado incondicional, el barrido no-mudo— quedan escritas una sola vez y
 * obligan a los dos.
 */
class ContratoDelOutboxDeLaVisitaTest : ContratoDelOutbox() {

    override val codigoComprobanteNoGuardado: String =
        VisitasTelemetria.CODE_VISITA_COMPROBANTES_NO_SE_GUARDARON
    override val codigoBarridoSinSubir: String =
        VisitasTelemetria.CODE_VISITA_FOTO_BARRIDA_SIN_SUBIR

    @Before
    fun sembrarLaVenta() = runTest {
        db.saleDao().insertAll(listOf(venta()))
    }

    override fun caminoSano(): CaminoDeEscritura = Sujeto()

    override fun caminosConAcompananteRoto(): List<CaminoDeEscritura> = listOf(
        // La foto. La visita no pide ubicacion por servicio: la captura inline
        // ocurre ANTES de `registrar` (Task 19), asi que su unico acompanante
        // post-commit es esta.
        Sujeto(imagenes = { DaoDeImagenesQueTruena(db.visitImageDao()) })
    )

    override fun caminoConEncoladorQueTruena(): CaminoDeEscritura =
        Sujeto(encolador = EncoladorQueTruena())

    override fun caminoQueSeCancelaAlComitear(job: Job): CaminoDeEscritura =
        // "No se encontraba" es de alcance CLIENTE, asi que la ultima
        // escritura de la transaccion es `updateEstadoCobranzaActivasByClienteId`
        // — el instante exacto en que se abre la ventana entre el commit y el
        // encolado.
        Sujeto(saleDao = { SaleDaoQueCancela(db.saleDao(), job) })

    override suspend fun sembrarComprobanteSinPadre(id: String, subidaEn: String?) {
        val archivo = File(context.filesDir, "$PREFIJO_COMPROBANTE_VISITA$id.jpg")
        archivo.writeBytes(byteArrayOf(1, 2, 3))
        db.visitImageDao().insertAll(
            listOf(
                VisitImageEntity(
                    ID = id,
                    VISITA_ID = "visita-que-la-poda-se-llevo",
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
        ComprobantesDeVisitaAdapter(
            context = context,
            imagenes = db.visitImageDao(),
            telemetry = telemetria,
            clock = clock
        ).barrerHuerfanos()
    }

    /** El sujeto: el adaptador de producción con las hojas que el test controla. */
    private inner class Sujeto(
        private val encolador: VisitsWorkEnqueuer = EncoladorQueGraba(),
        imagenes: () -> VisitImageDao = { db.visitImageDao() },
        saleDao: () -> SaleDao = { db.saleDao() }
    ) : CaminoDeEscritura {

        private val adaptador = RegistroDeVisitaAdapter(
            db = db,
            saleDao = db.saleDao(),
            visitas = VisitsLocalDataSource(db.visitDao(), saleDao(), encolador, clock),
            recomendaciones = db.visitRecommendationDao(),
            imagenes = imagenes(),
            telemetry = telemetria,
            clock = clock,
            traerUsuario = { COBRADOR }
        )

        override suspend fun escribir(id: String, comprobantes: List<String>): Boolean =
            adaptador.registrar(
                VisitaARegistrar(
                    visitaId = id,
                    clienteId = CLIENTE_ID,
                    ventaId = VENTA_ID,
                    tipoVisita = Constants.NO_SE_ENCONTRABA,
                    nota = null,
                    comprobantes = comprobantes.map {
                        ComprobanteDeVisita(
                            id = it,
                            archivo = "/files/$it.jpg",
                            mime = "image/jpeg"
                        )
                    }
                )
            ) == ResultadoDelRegistro.REGISTRADA

        override suspend fun filasDelHecho(id: String): Int =
            if (runCatching { db.visitDao().getVisitById(id) }.getOrNull() == null) 0 else 1

        override suspend fun filasDeComprobante(id: String): Int =
            db.visitImageDao().getByVisitaId(id).size

        override fun encolados(): List<String> = (encolador as? EncoladorQueGraba)?.encoladas
            ?: emptyList()
    }

    /** Fake a mano (sin MockK): lista pública de lo encolado, en orden. */
    private class EncoladorQueGraba : VisitsWorkEnqueuer {
        val encoladas: MutableList<String> = mutableListOf()

        override fun enqueue(visitId: String) {
            encoladas += visitId
        }
    }

    private class EncoladorQueTruena : VisitsWorkEnqueuer {
        override fun enqueue(visitId: String): Unit = error("workmanager no acepto el trabajo")
    }

    /**
     * `VisitImageDao` real salvo por el insert, que truena. Delega todo lo demás
     * al DAO real —igual que su gemelo de pagos— para que lo que falle sea la
     * escritura de la foto y no "la base entera caída".
     */
    private class DaoDeImagenesQueTruena(real: VisitImageDao) : VisitImageDao by real {
        override suspend fun insertAll(imagenes: List<VisitImageEntity>): Unit =
            error("room se cayo al escribir la foto")
    }

    /**
     * `SaleDao` real que **cancela [job] justo después** de la última escritura
     * de la transacción. No lanza: la transacción termina bien y la cancelación
     * queda pedida, que es el instante en que se abre la ventana entre el commit
     * y el encolado.
     */
    private class SaleDaoQueCancela(private val real: SaleDao, private val job: Job) :
        SaleDao by real {
        override suspend fun updateEstadoCobranzaActivasByClienteId(
            clienteId: Int,
            estadoCobranza: EstadoCobranza
        ): Int {
            val tocadas = real.updateEstadoCobranzaActivasByClienteId(clienteId, estadoCobranza)
            job.cancel()
            return tocadas
        }
    }

    private fun venta() = SaleEntity(
        DOCTO_CC_ACR_ID = VENTA_ID,
        DOCTO_CC_ID = 91027,
        FOLIO = "V-5188",
        CLIENTE_ID = CLIENTE_ID,
        APLICADO = "S",
        COBRADOR_ID = 7,
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
        NOMBRE_COBRADOR = "Efrain Dominguez Reyes",
        ESTADO_COBRANZA = "PENDIENTE",
        DIA_COBRANZA = "MARTES",
        DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 5200.0,
        AVAL_O_RESPONSABLE = "",
        FREC_PAGO = "SEMANAL"
    )

    private companion object {
        const val VENTA_ID = 77188
        const val CLIENTE_ID = 5021

        val COBRADOR = User(ID = "u-1", NOMBRE = "Efrain Dominguez Reyes", COBRADOR_ID = 7)
    }
}
