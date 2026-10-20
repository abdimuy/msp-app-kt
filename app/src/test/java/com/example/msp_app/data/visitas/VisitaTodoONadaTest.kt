package com.example.msp_app.data.visitas

import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.entities.CapturaEnCursoEntity
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.visitas.application.RegistrarVisita
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import com.example.msp_app.feature.visitas.domain.port.UbicacionDeLaVisita
import com.example.msp_app.feature.visitas.domain.port.UbicacionPort
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * E-APP-087 (decisión 3 del dueño 2026-10-20): una visita sobre varias cuentas
 * es **todo o nada**, en UNA transacción con su marcador. Room real.
 */
class VisitaTodoONadaTest : RoomTestBase() {

    private val clock = FakeClock(Instant.parse("2026-10-20T15:52:00Z"))
    private val hoy = LocalDate.of(2026, 10, 20)
    private val telemetry = RecordingTelemetry(clock)
    private val encoladas = mutableListOf<String>()

    private val dosCuentas = CapturaDeVisita(
        resultado = ResultadoDeVisita.SE_NEGO,
        etiqueta = TipoVisitaCatalogo.NO_VA_A_DAR_PAGO,
        cuentas = setOf(VENTA_A, VENTA_B)
    )

    @Before
    fun sembrar() = runTest {
        db.saleDao().insertAll(listOf(venta(VENTA_A), venta(VENTA_B)))
    }

    private fun casoDeUso(saleDao: SaleDao = db.saleDao()) = RegistrarVisita(
        RegistroDeVisitaAdapter(
            db = db,
            saleDao = db.saleDao(),
            visitas = VisitsLocalDataSource(
                db.visitDao(),
                saleDao,
                object : VisitsWorkEnqueuer {
                    override fun enqueue(visitId: String) {
                        encoladas += visitId
                    }
                },
                clock
            ),
            recomendaciones = db.visitRecommendationDao(),
            imagenes = db.visitImageDao(),
            telemetry = telemetry,
            clock = clock,
            traerUsuario = { User(ID = "u-1", NOMBRE = "Efraín Domínguez", COBRADOR_ID = 7) },
            emailEnSesion = { "efrain@example.com" }
        ),
        object : UbicacionPort {
            override suspend fun ubicacionActual() = UbicacionDeLaVisita(18.4617, -97.3928)
        },
        telemetry
    )

    @Test
    fun `E-APP-087 si la segunda cuenta truena no queda ninguna visita ni estado movido`() =
        runTest {
            val antes = estados()
            var llamadas = 0
            val fragil = object : SaleDao by db.saleDao() {
                override suspend fun updateTotal(
                    saleId: Int,
                    amount: Double,
                    estadoCobranza: EstadoCobranza
                ) {
                    if (++llamadas == 2) error("la segunda cuenta truena")
                    db.saleDao().updateTotal(saleId, amount, estadoCobranza)
                }
            }

            val resultado = casoDeUso(fragil).invoke("visita-1", CLIENTE_ID, null, dosCuentas, hoy)

            assertEquals(ResultadoDelRegistro.FALLO_EL_GUARDADO, resultado)
            assertEquals("todo o nada: ninguna visita", 0, visitas())
            assertEquals("ni un ESTADO_COBRANZA movido", antes, estados())
            assertTrue(encoladas.isEmpty())
        }

    @Test
    fun `E-APP-087 control positivo, sin el fallo las dos cuentas quedan y se encolan`() = runTest {
        val resultado = casoDeUso().invoke("visita-1", CLIENTE_ID, null, dosCuentas, hoy)

        assertEquals(ResultadoDelRegistro.REGISTRADA, resultado)
        assertEquals(2, visitas())
        assertEquals(2, encoladas.size)
        assertEquals(
            CapturaEnCursoEntity.CERRADA,
            db.capturaEnCursoDao().obtener("visita-1")!!.ESTADO
        )
    }

    @Test
    fun `E-APP-087 un reintento tardio sobre una cuenta ya PAGADA la deja PAGADA`() = runTest {
        casoDeUso().invoke("visita-1", CLIENTE_ID, null, dosCuentas, hoy)
        // Después de la visita, un abono deja la cuenta A en PAGADO.
        db.saleDao().updateTotal(VENTA_A, 0.0, EstadoCobranza.PAGADO)

        val resultado = casoDeUso().invoke("visita-1", CLIENTE_ID, null, dosCuentas, hoy)

        assertEquals(ResultadoDelRegistro.REGISTRADA, resultado)
        assertEquals("el reintento no re-fija ESTADO_COBRANZA", "PAGADO", estadoDe(VENTA_A))
        assertEquals("ni duplica visitas", 2, visitas())
    }

    private fun visitas(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM Visit").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun estados(): List<String> = db.openHelper.readableDatabase.query(
        "SELECT ESTADO_COBRANZA FROM sales ORDER BY DOCTO_CC_ACR_ID"
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    private fun estadoDe(venta: Int): String = db.openHelper.readableDatabase.query(
        "SELECT ESTADO_COBRANZA FROM sales WHERE DOCTO_CC_ACR_ID = $venta"
    ).use {
        it.moveToFirst()
        it.getString(0)
    }

    private fun venta(ventaId: Int) = SaleEntity(
        DOCTO_CC_ACR_ID = ventaId,
        DOCTO_CC_ID = ventaId + 1,
        FOLIO = "V-$ventaId",
        CLIENTE_ID = CLIENTE_ID,
        APLICADO = "S",
        COBRADOR_ID = 7,
        CLIENTE = "Victoria Flores Olmedo",
        ZONA_CLIENTE_ID = 21,
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
        VENDEDOR_1 = "J. Carlos Méndez",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = 6310.0,
        IMPTE_REST = 1450.0,
        SALDO_REST = 1450.0,
        FECHA_ULT_PAGO = null,
        CALLE = "C. Hidalgo 214",
        CIUDAD = "Tehuacán",
        ESTADO = "Puebla",
        TELEFONO = "2381627597",
        NOMBRE_COBRADOR = "Efraín Domínguez Reyes",
        ESTADO_COBRANZA = "PENDIENTE",
        DIA_COBRANZA = "MARTES",
        DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 5200.0,
        AVAL_O_RESPONSABLE = "Rosa María Ramírez",
        FREC_PAGO = "SEMANAL"
    )

    private companion object {
        const val CLIENTE_ID = 5021
        const val VENTA_A = 77021
        const val VENTA_B = 77188
    }
}
