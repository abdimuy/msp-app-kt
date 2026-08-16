package com.example.msp_app.core.database.dao.payment

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val CASH_FORMA_COBRO_ID = 157

/** Tope de emisiones que se aceptan antes de dar por muerta la re-emisión de Room. */
private const val MAX_EMISIONES = 5

/**
 * DEFECTO D6 — el "Porcentaje (Cobro)" del tablero se quedaba pegado en **0.00%**.
 *
 * `PaymentsViewModel.getAdjustedPaymentPercentage` era una lectura de un solo tiro disparada
 * desde `Home` antes de que las tablas estuvieran pobladas: si caía en ese hueco, la consulta
 * devolvía `NULL` (traducido a `0.0`), se cacheaba, y ahí se quedaba mientras todo lo demás se
 * recuperaba por Flow.
 *
 * Esta suite prueba la palanca del arreglo contra el Room REAL (Robolectric, DB en memoria):
 * [PaymentDao.observeAdjustedPaymentPercentage] re-emite cuando entran los datos, así que un
 * primer `0.0` deja de ser definitivo.
 */
class PaymentAdjustedPercentageFlowTest : RobolectricTestBase() {

    private lateinit var database: AppDatabase

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

    private fun payment(id: String, importe: Double, fecha: String) = PaymentEntity(
        ID = id,
        COBRADOR = "Rosa Elena Martínez Vázquez",
        DOCTO_CC_ACR_ID = 48213,
        DOCTO_CC_ID = 91027,
        FECHA_HORA_PAGO = fecha,
        GUARDADO_EN_MICROSIP = true,
        IMPORTE = importe,
        LAT = null,
        LNG = null,
        CLIENTE_ID = 30144,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = CASH_FORMA_COBRO_ID,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = "Guadalupe Hernández Soto"
    )

    // OJO con las llaves: la consulta une `payment.DOCTO_CC_ACR_ID = sales.DOCTO_CC_ID`, así que
    // el `DOCTO_CC_ID` de la venta es el que tiene que coincidir con el `DOCTO_CC_ACR_ID` del
    // pago (no al revés). Con las llaves cruzadas el INNER JOIN no casa y la consulta devuelve
    // NULL aunque haya datos — el mismo cero de siempre, por otra causa.
    private fun sale() = SaleEntity(
        DOCTO_CC_ACR_ID = 91027,
        DOCTO_CC_ID = 48213,
        FOLIO = "A-48213",
        CLIENTE_ID = 30144,
        APLICADO = "S",
        COBRADOR_ID = 7,
        CLIENTE = "Guadalupe Hernández Soto",
        ZONA_CLIENTE_ID = 21,
        LIMITE_CREDITO = 0.0,
        NOTAS = "",
        ZONA_NOMBRE = "Zona 21",
        IMPORTE_PAGO_PROMEDIO = 350.0,
        TOTAL_IMPORTE = 0.0,
        NUM_IMPORTES = 0,
        FECHA = "2026-06-01",
        PARCIALIDAD = 350,
        ENGANCHE = 1000.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        VENDEDOR_1 = "Gabriel Roque",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = 12000.0,
        IMPTE_REST = 8000.0,
        SALDO_REST = 8000.0,
        FECHA_ULT_PAGO = null,
        CALLE = "Av. Juárez 120",
        CIUDAD = "Puebla",
        ESTADO = "Puebla",
        TELEFONO = "2221234567",
        NOMBRE_COBRADOR = "Rosa Elena Martínez Vázquez",
        ESTADO_COBRANZA = "PENDIENTE",
        DIA_COBRANZA = "LUNES",
        DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 10000.0,
        FREC_PAGO = "SEMANAL",
        AVAL_O_RESPONSABLE = ""
    )

    @Test
    fun `con las tablas vacias la consulta devuelve NULL - el cero que se cacheaba`() = runTest {
        // Ésta es la lectura que el one-shot hacía demasiado pronto: sin filas, SUM() es NULL y
        // el ViewModel la guardaba como 0.0 para siempre.
        assertNull(database.paymentDao().getAdjustedPaymentPercentage("2026-06-01T00:00:00Z"))
    }

    @Test
    fun `el Flow re-emite cuando los datos entran DESPUES de la primera lectura`() = runTest {
        val dao = database.paymentDao()

        dao.observeAdjustedPaymentPercentage("2026-06-01T00:00:00Z").test {
            // Primera emisión: tablas vacías -> null (el 0.00% del campo).
            assertNull(awaitItem())

            // Llegan la venta y el pago (lo que en producción hace el sync en segundo plano).
            // Room invalida por TABLA, así que puede emitir una vez tras cada inserción; la
            // primera (sólo la venta, sin pagos) sigue siendo NULL. Lo que se afirma es que la
            // secuencia LLEGA a un valor real sin que nadie vuelva a pedir nada — ésa es la
            // reparación que el one-shot no podía dar.
            database.saleDao().insertAll(listOf(sale()))
            dao.saveAll(listOf(payment("pago-1", 350.0, "2026-06-08T15:00:00Z")))

            var recalculado = awaitItem()
            var intentos = 0
            while (recalculado == null && intentos < MAX_EMISIONES) {
                recalculado = awaitItem()
                intentos++
            }
            assertNotNull("el Flow nunca llegó a un valor real", recalculado)
            assertTrue("debe dejar de ser cero: $recalculado", requireNotNull(recalculado) > 0.0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `el Flow coincide con la version one-shot una vez que hay datos`() = runTest {
        val dao = database.paymentDao()
        database.saleDao().insertAll(listOf(sale()))
        dao.saveAll(listOf(payment("pago-1", 350.0, "2026-06-08T15:00:00Z")))

        val oneShot = dao.getAdjustedPaymentPercentage("2026-06-01T00:00:00Z")
        // Con datos reales la comparación no puede ser `null == null` (que pasaría en vacío).
        assertNotNull("el fixture debe producir un porcentaje real", oneShot)

        dao.observeAdjustedPaymentPercentage("2026-06-01T00:00:00Z").test {
            assertEquals(oneShot, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
