package com.example.msp_app.data.pagos

import com.example.msp_app.core.database.entities.PlazoDeEnvioEntity
import com.example.msp_app.core.database.entities.UbicacionDeCapturaEntity
import com.example.msp_app.core.sync.pendingwork.data.visits.RoomDineroPendiente
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.pagos.CondonacionFixtures.condonacion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-147 (K7): un pago RETENIDO por su plazo de ubicacion sigue siendo «sin enviar»; no se
 * oculta. Lo cuentan los consumidores de `GUARDADO_EN_MICROSIP = 0` (cambio de semana, aviso «N sin
 * enviar», tarjeta del menu, `DineroPendiente` de la poda de visitas): todos leen
 * `PaymentDao.getPendingPayments` / `observarPendientesDeSubir`, que no conocen el plazo.
 * Control positivo: al soltarse la captura (GUARDADO = 1) el mismo montaje deja de contarla.
 */
class PagoRetenidoCuentaComoPendienteTest : RoomTestBase() {

    private suspend fun guardarRetenido(id: String) {
        db.paymentDao().savePayment(
            condonacion(id, 100.0, formaCobro = CondonacionFixtures.EFECTIVO)
        )
        db.plazoDeEnvioDao().anotar(
            PlazoDeEnvioEntity(
                id,
                UbicacionDeCapturaEntity.TIPO_PAGO,
                1_000L,
                PlazoDeEnvioEntity.PLAZO_MS
            )
        )
    }

    @Test
    fun `E-APP-147 un pago retenido sigue en getPendingPayments - bloquea el cambio de semana`() =
        runTest {
            guardarRetenido("p-1")

            val pendientes = PaymentsLocalDataSource(
                db.paymentDao(),
                db.saleDao()
            ).getPendingPayments()

            assertEquals(listOf("p-1"), pendientes.map { it.ID })
        }

    @Test
    fun `E-APP-147 un pago retenido cuenta en el aviso, la tarjeta y la poda de visitas`() =
        runTest {
            guardarRetenido("p-1")

            assertEquals(1, db.paymentDao().observarPendientesDeSubir().first())
            assertTrue(RoomDineroPendiente(db.paymentDao(), db.localSaleDao()).hayPendientes())
        }

    @Test
    fun `control positivo - soltada la captura deja de contarse`() = runTest {
        guardarRetenido("p-1")
        db.paymentDao().updateEstado("p-1", 1)

        assertEquals(0, db.paymentDao().observarPendientesDeSubir().first())
        assertTrue(
            PaymentsLocalDataSource(db.paymentDao(), db.saleDao()).getPendingPayments().isEmpty()
        )
    }
}
