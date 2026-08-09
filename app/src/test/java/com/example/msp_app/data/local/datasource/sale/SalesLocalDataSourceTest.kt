package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite exhaustiva de [SalesLocalDataSource] construido por el **constructor
 * de DAOs inyectados** (la forma Hilt) con [SaleDao] de la DB in-memory de
 * [RoomTestBase]. Prueba además que la forma inyectada es EQUIVALENTE al
 * puente `context` que usan `PaymentsViewModel`/`SalesViewModel`/
 * `SaleDetailsViewModel`/`AuthViewModel` (ambos resuelven a la misma DB via
 * [com.example.msp_app.core.database.AppDatabase.getInstance]).
 */
class SalesLocalDataSourceTest : RoomTestBase() {

    private lateinit var store: SalesLocalDataSource

    @Before
    fun setUpStore() {
        store = SalesLocalDataSource(db.saleDao())
    }

    // ─── fixtures ────────────────────────────────────────────────────────────

    private fun sale(
        saleId: Int,
        clienteId: Int = 4821,
        saldoRest: Double = 1000.0,
        estadoCobranza: String = "PENDIENTE"
    ) = SaleEntity(
        DOCTO_CC_ACR_ID = saleId,
        DOCTO_CC_ID = saleId + 1,
        FOLIO = "A-$saleId",
        CLIENTE_ID = clienteId,
        APLICADO = "S",
        COBRADOR_ID = 7,
        CLIENTE = "Guadalupe Hernandez Soto",
        ZONA_CLIENTE_ID = 21,
        LIMITE_CREDITO = 0.0,
        NOTAS = "",
        ZONA_NOMBRE = "Centro",
        IMPORTE_PAGO_PROMEDIO = 350.0,
        TOTAL_IMPORTE = 3500.0,
        NUM_IMPORTES = 10,
        FECHA = "2026-01-01T00:00:00Z",
        PARCIALIDAD = 350,
        ENGANCHE = 500.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        VENDEDOR_1 = "",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = 3500.0,
        IMPTE_REST = saldoRest,
        SALDO_REST = saldoRest,
        FECHA_ULT_PAGO = null,
        CALLE = "Av. Reforma 100",
        CIUDAD = "Tehuacan",
        ESTADO = "Puebla",
        TELEFONO = "2381234567",
        NOMBRE_COBRADOR = "Rosa Elena Martinez Vazquez",
        ESTADO_COBRANZA = estadoCobranza,
        DIA_COBRANZA = "LUNES",
        DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 3000.0,
        AVAL_O_RESPONSABLE = "",
        FREC_PAGO = "SEMANAL"
    )

    // ─── getById / saveAll round-trip ─────────────────────────────────────────

    @Test
    fun getById_nullWhenAbsent() = runTest {
        assertNull(store.getById(9999))
    }

    @Test
    fun saveAll_roundTripsAndIsQueryableById() = runTest {
        store.saveAll(listOf(sale(saleId = 5000), sale(saleId = 6000)))

        val got = store.getById(5000)!!
        assertEquals("A-5000", got.FOLIO)
        assertEquals(1000.0, got.SALDO_REST, 1e-9)
    }

    @Test
    fun saveAll_replacesPreviousContents() = runTest {
        store.saveAll(listOf(sale(saleId = 5000)))
        assertEquals(1000.0, store.getById(5000)!!.SALDO_REST, 1e-9)

        // saveAll hace un refresh completo (DELETE + INSERT): la venta 5000
        // desaparece si el segundo lote ya no la trae.
        store.saveAll(listOf(sale(saleId = 6000)))

        assertNull("5000 ya no viene en el refresh: debe desaparecer", store.getById(5000))
        assertEquals(1000.0, store.getById(6000)!!.SALDO_REST, 1e-9)
    }

    // ─── getByClientId ─────────────────────────────────────────────────────────

    @Test
    fun getByClientId_filtersByCliente() = runTest {
        store.saveAll(
            listOf(
                sale(saleId = 5000, clienteId = 4821),
                sale(saleId = 5100, clienteId = 4821),
                sale(saleId = 6000, clienteId = 9999)
            )
        )

        val result = store.getByClientId(4821)

        assertEquals(
            listOf(5000, 5100).sorted(),
            result.map { it.DOCTO_CC_ACR_ID }.sorted()
        )
    }

    @Test
    fun getByClientId_emptyWhenNoMatch() = runTest {
        store.saveAll(listOf(sale(saleId = 5000, clienteId = 4821)))

        assertTrue(store.getByClientId(1).isEmpty())
    }

    // ─── getAll / observeAll ─────────────────────────────────────────────────

    @Test
    fun getAll_returnsSavedSalesWithNullProductosWhenNoProductRows() = runTest {
        store.saveAll(listOf(sale(saleId = 5000)))

        val all = store.getAll()

        assertEquals(1, all.size)
        assertEquals(5000, all.first().DOCTO_CC_ACR_ID)
        assertNull(
            "sin filas en products, el LEFT JOIN produce PRODUCTOS = null",
            all.first().PRODUCTOS
        )
    }

    @Test
    fun observeAll_emitsOnEverySaveAll() = runTest {
        store.observeAll().test {
            assertTrue("estado inicial vacio", awaitItem().isEmpty())

            store.saveAll(listOf(sale(saleId = 5000)))
            assertEquals(listOf(5000), awaitItem().map { it.DOCTO_CC_ACR_ID })

            store.saveAll(listOf(sale(saleId = 6000)))
            assertEquals(listOf(6000), awaitItem().map { it.DOCTO_CC_ACR_ID })

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── equivalencia inyectado ⇔ puente context ──────────────────────────────

    @Test
    fun injectedFormEquivalentToContextForm() = runTest {
        store.saveAll(listOf(sale(saleId = 5000), sale(saleId = 6000, clienteId = 9999)))

        // Tipo explicito: los dos constructores de un arg (DAO vs Context)
        // hacen ambigua la inferencia de `getApplicationContext<T>()`.
        val contextForm = SalesLocalDataSource(ApplicationProvider.getApplicationContext<Context>())

        assertEquals(
            "ambos constructores resuelven a la misma DB: mismo getById",
            store.getById(5000),
            contextForm.getById(5000)
        )
        assertEquals(
            store.getByClientId(9999).map { it.DOCTO_CC_ACR_ID },
            contextForm.getByClientId(9999).map { it.DOCTO_CC_ACR_ID }
        )
    }
}
