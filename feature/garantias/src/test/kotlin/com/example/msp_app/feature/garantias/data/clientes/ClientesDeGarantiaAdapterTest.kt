package com.example.msp_app.feature.garantias.data.clientes

import com.example.msp_app.core.database.entities.ClienteEntity
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.feature.garantias.domain.model.EstadoDeCuenta
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * El padrón de clientes, con lo que un folio de origen cliente necesita.
 *
 * ## Qué protege
 *
 * La venta que se liga al folio es la de `DOCTO_CC_ID` **más alto** —el mismo
 * criterio con el que el sistema legado liga una garantía a su venta— y su
 * calle y ciudad son el único domicilio que el teléfono conoce. El estado de
 * cuenta sale de la suma de `SALDO_REST` de **todas** las ventas, con la
 * tolerancia del medio centavo que evita inventar deuda por ruido binario. Un
 * cliente sin ventas se devuelve igual, marcado como no usable.
 */
class ClientesDeGarantiaAdapterTest : RoomTestBase() {

    private lateinit var clientes: ClientesDeGarantiaAdapter

    @Before
    fun crearAdaptador() {
        clientes = ClientesDeGarantiaAdapter(db.clienteDao(), db.saleDao())
    }

    @Test
    fun `un nombre en blanco no busca nada`() = runTest {
        db.clienteDao().insertAll(listOf(cliente(id = 1, nombre = "JUAN PEREZ")))

        assertTrue(clientes.buscar("   ", 10).isEmpty())
    }

    @Test
    fun `buscar arma el cliente con su venta su saldo y su domicilio`() = runTest {
        db.clienteDao().insertAll(listOf(cliente(id = 30144, nombre = "JUAN PEREZ")))
        db.saleDao().insertAll(
            listOf(
                venta(
                    acrId = 1,
                    doctoCcId = 91027,
                    clienteId = 30144,
                    saldo = 1500.0,
                    calle = "Av. Reforma",
                    ciudad = "CDMX"
                )
            )
        )

        val encontrados = clientes.buscar("juan", 10)

        assertEquals(1, encontrados.size)
        val juan = encontrados.single()
        assertEquals(30144, juan.clienteId)
        assertEquals("JUAN PEREZ", juan.nombre)
        assertEquals(91027, juan.ventaId)
        assertEquals(EstadoDeCuenta.SALDO_PENDIENTE, juan.estadoDeCuenta)
        assertEquals("Av. Reforma", juan.calle)
        assertEquals("CDMX", juan.ciudad)
        assertTrue(juan.sePuedeUsar)
    }

    @Test
    fun `la venta ligada es la del docto cc mas alto`() = runTest {
        db.clienteDao().insertAll(listOf(cliente(id = 7, nombre = "MARIA LOPEZ")))
        db.saleDao().insertAll(
            listOf(
                venta(
                    acrId = 1,
                    doctoCcId = 100,
                    clienteId = 7,
                    saldo = 0.0,
                    calle = "Vieja",
                    ciudad = "Puebla"
                ),
                venta(
                    acrId = 2,
                    doctoCcId = 900,
                    clienteId = 7,
                    saldo = 0.0,
                    calle = "Nueva",
                    ciudad = "Tlaxcala"
                )
            )
        )

        val maria = clientes.buscar("maria", 10).single()

        assertEquals(900, maria.ventaId)
        assertEquals("Nueva", maria.calle)
        assertEquals("Tlaxcala", maria.ciudad)
    }

    @Test
    fun `el saldo de todas las ventas decide el estado de cuenta`() = runTest {
        db.clienteDao().insertAll(
            listOf(
                cliente(id = 1, nombre = "CON DEUDA"),
                cliente(id = 2, nombre = "LIQUIDADO")
            )
        )
        db.saleDao().insertAll(
            listOf(
                venta(acrId = 1, doctoCcId = 10, clienteId = 1, saldo = 100.0),
                venta(acrId = 2, doctoCcId = 11, clienteId = 1, saldo = 0.0),
                venta(acrId = 3, doctoCcId = 20, clienteId = 2, saldo = 0.0),
                venta(acrId = 4, doctoCcId = 21, clienteId = 2, saldo = 0.0)
            )
        )

        val conDeuda = clientes.buscar("deuda", 10).single()
        val liquidado = clientes.buscar("liquidado", 10).single()

        assertEquals(EstadoDeCuenta.SALDO_PENDIENTE, conDeuda.estadoDeCuenta)
        assertEquals(EstadoDeCuenta.LIQUIDADA, liquidado.estadoDeCuenta)
    }

    @Test
    fun `el ruido binario por debajo del medio centavo no inventa deuda`() = runTest {
        db.clienteDao().insertAll(listOf(cliente(id = 1, nombre = "CASI PAGADO")))
        db.saleDao().insertAll(
            listOf(venta(acrId = 1, doctoCcId = 10, clienteId = 1, saldo = 0.004))
        )

        assertEquals(EstadoDeCuenta.LIQUIDADA, clientes.buscar("casi", 10).single().estadoDeCuenta)
    }

    @Test
    fun `un cliente sin ventas se devuelve pero no se puede usar`() = runTest {
        db.clienteDao().insertAll(listOf(cliente(id = 9, nombre = "SIN VENTAS")))

        val sinVentas = clientes.buscar("sin ventas", 10).single()

        assertNull(sinVentas.ventaId)
        assertFalse(sinVentas.sePuedeUsar)
        assertEquals("", sinVentas.calle)
        assertEquals("", sinVentas.ciudad)
    }

    @Test
    fun `buscar respeta el limite`() = runTest {
        db.clienteDao().insertAll(
            (1..5).map { cliente(id = it, nombre = "CLIENTE $it") }
        )

        assertEquals(2, clientes.buscar("cliente", 2).size)
    }

    private fun cliente(id: Int, nombre: String): ClienteEntity = ClienteEntity(
        CLIENTE_ID = id,
        NOMBRE = nombre,
        ESTATUS = "A",
        CAUSA_SUSP = null
    )

    private fun venta(
        acrId: Int,
        doctoCcId: Int,
        clienteId: Int,
        saldo: Double,
        calle: String = "",
        ciudad: String = ""
    ): SaleEntity = SaleEntity(
        DOCTO_CC_ACR_ID = acrId,
        DOCTO_CC_ID = doctoCcId,
        FOLIO = "F-$acrId",
        CLIENTE_ID = clienteId,
        APLICADO = "",
        COBRADOR_ID = 0,
        CLIENTE = "cliente",
        ZONA_CLIENTE_ID = 0,
        LIMITE_CREDITO = 0.0,
        NOTAS = "",
        ZONA_NOMBRE = "",
        IMPORTE_PAGO_PROMEDIO = null,
        TOTAL_IMPORTE = 0.0,
        NUM_IMPORTES = 0,
        FECHA = "2026-01-01",
        PARCIALIDAD = 0,
        ENGANCHE = 0.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        VENDEDOR_1 = "",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = 0.0,
        IMPTE_REST = 0.0,
        SALDO_REST = saldo,
        FECHA_ULT_PAGO = null,
        CALLE = calle,
        CIUDAD = ciudad,
        ESTADO = "",
        TELEFONO = "",
        NOMBRE_COBRADOR = "",
        ESTADO_COBRANZA = "",
        DIA_COBRANZA = "",
        DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 0.0,
        AVAL_O_RESPONSABLE = "",
        FREC_PAGO = null
    )
}
