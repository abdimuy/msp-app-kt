package com.example.msp_app.features.payments.viewmodels

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.utils.ResultState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val CASH_FORMA_COBRO_ID = 157
private const val INICIO_SEMANA = "2026-06-01T00:00:00Z"

/** Tope de emisiones que se aceptan antes de dar por muerta la re-emisión de Room. */
private const val MAX_EMISIONES = 6

/**
 * DEFECTO D6 — el "Porcentaje (Cobro)" se quedaba pegado en **0.00%**.
 *
 * `getAdjustedPaymentPercentage` era una lectura de un solo tiro que `Home` disparaba antes del
 * `await` de `salesState`. Si caía con las tablas todavía vacías, cacheaba `0.0` en el
 * `StateFlow` y ahí se quedaba mientras el resto del tablero se recuperaba por Flow — el mismo
 * patrón que D5: one-shot donde debía observar.
 *
 * Este test ejerce el ViewModel REAL contra un Room en memoria ([RoomTestBase]) y pide
 * exactamente lo que fallaba: leer primero (tablas vacías) y meter los datos DESPUÉS.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdjustedPaymentPercentageReactiveTest : RoomTestBase() {

    private lateinit var viewModel: PaymentsViewModel

    @Before
    fun setUpViewModel() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = PaymentsViewModel(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDownViewModel() {
        Dispatchers.resetMain()
    }

    private fun payment(id: String) = PaymentEntity(
        ID = id,
        COBRADOR = "Rosa Elena Martínez Vázquez",
        DOCTO_CC_ACR_ID = 48213,
        DOCTO_CC_ID = 91027,
        FECHA_HORA_PAGO = "2026-06-08T15:00:00Z",
        GUARDADO_EN_MICROSIP = true,
        IMPORTE = 350.0,
        LAT = null,
        LNG = null,
        CLIENTE_ID = 30144,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = CASH_FORMA_COBRO_ID,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = "Guadalupe Hernández Soto"
    )

    // La consulta une `payment.DOCTO_CC_ACR_ID = sales.DOCTO_CC_ID`: es el `DOCTO_CC_ID` de la
    // venta el que casa con el `DOCTO_CC_ACR_ID` del pago.
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
    fun `sin semana elegida el porcentaje se queda Idle - no calcula sobre una ventana inventada`() =
        runTest {
            assertEquals(ResultState.Idle, viewModel.adjustedPaymentPercentageState.value)
        }

    @Test
    fun `el porcentaje se recalcula cuando los datos llegan DESPUES de la primera lectura`() =
        runTest {
            viewModel.adjustedPaymentPercentageState.test {
                // Estado inicial: nadie ha elegido semana.
                assertEquals(ResultState.Idle, awaitItem())

                // Home elige la semana con las tablas TODAVÍA vacías: ésta es exactamente la
                // lectura prematura que antes cacheaba 0.0 para siempre.
                viewModel.getAdjustedPaymentPercentage(INICIO_SEMANA)
                var actual = awaitItem()
                var intentos = 0
                while (actual !is ResultState.Success && intentos < MAX_EMISIONES) {
                    actual = awaitItem()
                    intentos++
                }
                assertEquals(0.0, (actual as ResultState.Success).data, 0.0)

                // El sync de fondo mete la venta y el pago. NADIE vuelve a llamar al ViewModel.
                db.saleDao().insertAll(listOf(sale()))
                db.paymentDao().saveAll(listOf(payment("pago-1")))

                var recalculado = 0.0
                intentos = 0
                while (recalculado <= 0.0 && intentos < MAX_EMISIONES) {
                    val emision = awaitItem()
                    if (emision is ResultState.Success) recalculado = emision.data
                    intentos++
                }
                assertTrue(
                    "el porcentaje debe dejar de ser 0.00%: $recalculado",
                    recalculado > 0.0
                )

                cancelAndIgnoreRemainingEvents()
            }
        }
}
