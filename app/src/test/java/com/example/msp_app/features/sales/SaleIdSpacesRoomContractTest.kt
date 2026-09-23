package com.example.msp_app.features.sales

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.example.msp_app.core.database.entities.GuaranteeEntity
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.sale.toDomain
import com.example.msp_app.features.sales.viewmodels.SaleDetailsViewModel
import com.example.msp_app.features.sales.viewmodels.SalesViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Arreglo A — la familia del identificador. **Contrato de espacios de id, medido contra Room
 * de verdad**, que es lo que faltaba: los cinco sitios de este arreglo viven en `:app` legado
 * y nadie los atrapó porque no había una sola prueba que ejecutara la consulta.
 *
 * ## Por qué la fixture usa TRES números distintos
 *
 * En producción el único escritor vivo de `sales` (`VentaDto.toEntity`) pone el **mismo**
 * `docto_cc_id` del DTO en `DOCTO_CC_ACR_ID` y en `DOCTO_CC_ID`, así que una fixture realista
 * hace pasar **cualquier** id — con el defecto puesto y sin él. Sería una aserción inerte, y
 * esta migración ya lleva doce. Aquí las tres columnas llevan números **distintos a
 * propósito** para que cada aserción pueda distinguir un espacio del otro, y el test empieza
 * afirmando que son distintos: sin esa precondición, todo lo de abajo pasa por casualidad.
 *
 * La relación que la fixture respeta es la del backend
 * (`internal/cobranza/infra/ventfb/pagos_repo.go`):
 * `JOIN MSP_SALDOS_VENTAS s ON s.DOCTO_CC_ID = p.DOCTO_CC_ACR_ID`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaleIdSpacesRoomContractTest : RoomTestBase() {

    private companion object {
        /** Cota del bucle que espera la emisión resuelta — evita colgar el test si nunca llega. */
        const val MAX_EMISIONES = 10

        /** `sales.DOCTO_CC_ACR_ID` — la PK de Room. Nombre heredado, no un segundo espacio. */
        const val PK_DE_LA_VENTA = 91027

        /** `sales.DOCTO_CC_ID` = `Payment.DOCTO_CC_ACR_ID` — el cargo, según el join del backend. */
        const val CARGO = 48213

        /** `Payment.DOCTO_CC_ID` — el documento del ABONO en Microsip. Nunca identifica una venta. */
        const val DOCUMENTO_DEL_ABONO = 70155

        const val CLIENTE = 30144
    }

    private fun garantia() = GuaranteeEntity(
        EXTERNAL_ID = "gar-48213",
        DOCTO_CC_ID = CARGO,
        ESTADO = "SOLICITADA",
        DESCRIPCION_FALLA = "El refrigerador no enfría",
        OBSERVACIONES = null,
        UPLOADED = 0,
        FECHA_SOLICITUD = "2026-06-10T12:00:00Z",
        NOMBRE_CLIENTE = "Guadalupe Hernández Soto",
        NOMBRE_PRODUCTO = "Refrigerador 11 pies"
    )

    private fun saleEntity() = SaleEntity(
        DOCTO_CC_ACR_ID = PK_DE_LA_VENTA,
        DOCTO_CC_ID = CARGO,
        FOLIO = "CV-48213",
        CLIENTE_ID = 30144,
        APLICADO = "S",
        COBRADOR_ID = 7,
        CLIENTE = "Guadalupe Hernández Soto",
        ZONA_CLIENTE_ID = 21,
        LIMITE_CREDITO = 0.0,
        NOTAS = "",
        ZONA_NOMBRE = "Zona 21",
        IMPORTE_PAGO_PROMEDIO = 350.0,
        TOTAL_IMPORTE = 700.0,
        NUM_IMPORTES = 2,
        FECHA = "2026-06-01T00:00:00Z",
        PARCIALIDAD = 350,
        ENGANCHE = 1000.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        VENDEDOR_1 = "Gabriel Roque Alvarado",
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
        DIA_TEMPORAL_COBRANZA = "LUNES",
        PRECIO_DE_CONTADO = 10000.0,
        AVAL_O_RESPONSABLE = "",
        FREC_PAGO = "SEMANAL"
    )

    private fun paymentEntity(id: String) = PaymentEntity(
        ID = id,
        COBRADOR = "Rosa Elena Martínez Vázquez",
        DOCTO_CC_ACR_ID = CARGO,
        DOCTO_CC_ID = DOCUMENTO_DEL_ABONO,
        FECHA_HORA_PAGO = "2026-06-08T15:00:00Z",
        GUARDADO_EN_MICROSIP = true,
        IMPORTE = 350.0,
        LAT = 19.4326,
        LNG = -99.1332,
        CLIENTE_ID = 30144,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = 157,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = "Guadalupe Hernández Soto"
    )

    private suspend fun seed() {
        db.saleDao().insertAll(listOf(saleEntity()))
        db.paymentDao().saveAll(listOf(paymentEntity("abono-1")))
    }

    private fun sale() = saleEntity().toDomain()

    /**
     * Precondición de todo lo demás: si dos de los tres números coincidieran, las aserciones
     * de abajo no podrían distinguir un espacio de id del otro y pasarían con el bug puesto.
     */
    @Test
    fun `los tres identificadores de la fixture son distintos entre si`() {
        assertNotEquals(PK_DE_LA_VENTA, CARGO)
        assertNotEquals(PK_DE_LA_VENTA, DOCUMENTO_DEL_ABONO)
        assertNotEquals(CARGO, DOCUMENTO_DEL_ABONO)
    }

    /**
     * Sitios **PaymentsHistorySection.kt** (historial de pagos del detalle legado),
     * **NewForgivenessDialog.kt** (relectura tras condonar) y **SaleDetailsScreen.kt**
     * (ruta del mapa de la venta → `SaleMapScreen` → la misma consulta).
     */
    @Test
    fun `los pagos de la venta se piden con el cargo, no con la PK ni con el documento del abono`() =
        runTest {
            seed()
            val dao = db.paymentDao()

            val id = SaleIdSpaces.forSalePayments(sale())

            assertEquals("el selector tiene que entregar el cargo", CARGO, id)
            assertNotEquals("y NO la PK de la fila de sales", PK_DE_LA_VENTA, id)
            assertNotEquals("ni el documento del abono", DOCUMENTO_DEL_ABONO, id)

            assertEquals(
                "con el cargo salen los pagos de la venta",
                1,
                dao.getPaymentsBySaleId(id).size
            )
            assertTrue(
                "con la PK de sales no sale ninguno — es el otro espacio",
                dao.getPaymentsBySaleId(PK_DE_LA_VENTA).isEmpty()
            )
            assertTrue(
                "con el documento del abono tampoco",
                dao.getPaymentsBySaleId(DOCUMENTO_DEL_ABONO).isEmpty()
            )
        }

    /**
     * Sitio **SaleDetailsScreen.kt:81** — el que esta rama torció. Desde la Task 21 el dock
     * entra a `SaleDetails` con el `DOCTO_CC_ACR_ID`, y `overdue_payments_view` filtra
     * `sales.DOCTO_CC_ID`: pasar el argumento de la ruta devuelve `null` y
     * `PaymentProgressCard` pierde los atrasos y la fecha del último pago.
     */
    @Test
    fun `los atrasos de la venta se piden con el cargo, no con el argumento de la ruta`() =
        runTest {
            seed()
            val dao = db.paymentDao()

            val id = SaleIdSpaces.forOverdueView(sale())

            assertEquals(CARGO, id)
            assertNotEquals(
                "el argumento de la ruta (la PK) NO sirve para esta vista",
                PK_DE_LA_VENTA,
                id
            )

            assertNotNull(
                "con el cargo la vista devuelve la fila de la venta",
                dao.getOverduePaymentBySaleId(id)
            )
            assertNull(
                "con la PK de sales la vista no devuelve nada",
                dao.getOverduePaymentBySaleId(PK_DE_LA_VENTA)
            )
        }

    /**
     * Sitio **SaleDetailsScreen.kt:239** — "otras ventas del cliente" navega a
     * `Screen.SaleDetails`, cuyo argumento resuelve `SaleDao.getById`, que filtra la PK.
     *
     * El id sale de la MISMA proyección que pinta esa lista (`SaleDao.getByClientId` →
     * `SaleWithProductsEntity`), que es la única forma en que la pantalla tiene la venta a
     * mano. `SaleIdSpaces` no tiene sobrecarga para `Sale` justamente porque no hay call site
     * que la use.
     */
    @Test
    fun `la fila de la venta se pide con la PK, no con el cargo`() = runTest {
        seed()
        val dao = db.saleDao()

        val fila = dao.getByClientId(CLIENTE).single()
        val id = SaleIdSpaces.forSaleRow(fila.toDomain())

        assertEquals(PK_DE_LA_VENTA, id)
        assertNotEquals("y NO el cargo", CARGO, id)

        assertNotNull("con la PK sale la venta", dao.getById(id))
        assertNull("con el cargo no sale ninguna", dao.getById(CARGO))
    }

    /**
     * **GuaranteeScreen** — la ruta `guarantee/{saleId}` trae el id del CRÉDITO, porque eso es
     * lo que la garantía necesita (`garantias.DOCTO_CC_ID`). La pantalla resolvía la venta con
     * `getById`, que filtra la PK: pedía por la columna que no era.
     *
     * El test afirma las dos mitades a la vez, que es lo que impide el "arreglo" tentador de
     * cambiar el argumento de la ruta: con el id del crédito **la garantía Y la venta** salen
     * las dos; con la PK sale la venta y **se pierde la garantía**, que es la pantalla entera.
     */
    @Test
    fun `la pantalla de garantia resuelve garantia Y venta con el id del credito`() = runTest {
        seed()
        db.guaranteeDao().insertAllGuarantees(listOf(garantia()))

        assertNotNull(
            "con el crédito sale la garantía",
            db.guaranteeDao().getGuaranteeByDoctoCcId(CARGO)
        )
        assertNotNull(
            "y con el crédito también sale la venta — por findByDoctoCcId, no por getById",
            db.saleDao().findByDoctoCcId(CARGO)
        )

        assertNull(
            "cambiar el argumento a la PK rompería la garantía, que es la razón de la pantalla",
            db.guaranteeDao().getGuaranteeByDoctoCcId(PK_DE_LA_VENTA)
        )
        assertNull(
            "y `getById` con el crédito no encuentra la venta: ése era el defecto",
            db.saleDao().getById(CARGO)
        )
    }

    /** El mismo recorrido, por el ViewModel real que usa `GuaranteeScreen`. */
    @Test
    fun `SaleDetailsViewModel carga la venta por credito y no por PK`() = runTest {
        seed()
        val viewModel = SaleDetailsViewModel(ApplicationProvider.getApplicationContext())

        viewModel.saleState.test {
            assertTrue("el estado arranca en Idle", awaitItem() is ResultState.Idle)

            viewModel.loadSaleDetailsByCreditId(CARGO)
            val porCredito = esperarResuelto(this)
            assertTrue("estado por crédito: $porCredito", porCredito is ResultState.Success)
            assertEquals(
                "y es la venta de este crédito",
                PK_DE_LA_VENTA,
                (porCredito as ResultState.Success).data?.DOCTO_CC_ACR_ID
            )

            viewModel.loadSaleDetails(CARGO)
            val porPk = esperarResuelto(this)
            assertTrue(
                "con `getById` el crédito no encuentra nada — el defecto que esto cierra",
                porPk is ResultState.Error
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * El mismo recorrido de `SaleDetailsScreen`, pero por el ViewModel real que la pantalla
     * usa: prueba que el id elegido sobrevive la capa de `PaymentsLocalDataSource` y llega
     * a `PaymentProgressCard` como una fila poblada.
     *
     * Se espera por Turbine y no por `advanceUntilIdle`: los DAO suspend de Room resuelven en
     * su propio executor, así que el tiempo virtual del scheduler puede quedar ocioso con el
     * trabajo todavía corriendo — un `advanceUntilIdle` volvería enseguida y el test afirmaría
     * sobre el `Loading` inicial, que es justo la aserción que no puede fallar.
     */
    @Test
    fun `SalesViewModel entrega los atrasos con el cargo y nada con el argumento de la ruta`() =
        runTest {
            seed()
            val viewModel = SalesViewModel(ApplicationProvider.getApplicationContext())

            viewModel.overduePaymentBySaleState.test {
                assertTrue("el estado arranca en Loading", awaitItem() is ResultState.Loading)

                viewModel.getOverduePaymentBySaleId(SaleIdSpaces.forOverdueView(sale()))
                val conCargo = esperarResuelto(this)
                assertTrue("estado con el cargo: $conCargo", conCargo is ResultState.Success)
                assertNotNull(
                    "con el cargo la tarjeta de progreso recibe su fila",
                    (conCargo as ResultState.Success).data
                )
                assertEquals(
                    "y es la fila de ESTA venta",
                    CARGO,
                    conCargo.data?.DOCTO_CC_ID
                )

                viewModel.getOverduePaymentBySaleId(PK_DE_LA_VENTA)
                val conPk = esperarResuelto(this)
                assertTrue("estado con la PK: $conPk", conPk is ResultState.Success)
                assertNull(
                    "con el argumento de la ruta la tarjeta se queda sin atrasos",
                    (conPk as ResultState.Success).data
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    private suspend fun <T> esperarResuelto(
        turbine: ReceiveTurbine<ResultState<T>>
    ): ResultState<T> {
        var emision = turbine.awaitItem()
        var intentos = 0
        while (emision is ResultState.Loading && intentos < MAX_EMISIONES) {
            emision = turbine.awaitItem()
            intentos++
        }
        return emision
    }
}
