package com.example.msp_app.features.sales.precios

import com.example.msp_app.core.common.ventas.precios.ReglaOrdenDePrecios
import com.example.msp_app.core.common.ventas.precios.VeredictoOrdenDePrecios
import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel
import com.example.msp_app.`test-fixtures`.TestDataFactory
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-061 (contado sólo contado) y E-APP-062 (validación previa). */
class LineasDeVentaTest : RobolectricTestBase() {

    private val catalogo = "Precio de lista:1600.0, Precio 4 Meses:1200.0, Precio 1 Meses:1000.0"
    private val producto = TestDataFactory.createProductInventory(
        id = 1,
        name = "Base King",
        prices = catalogo
    )

    private fun combo(lista: Double, corto: Double, contado: Double) = LocalSaleComboEntity(
        COMBO_ID = "c1",
        LOCAL_SALE_ID = "v1",
        NOMBRE_COMBO = "Recámara",
        PRECIO_LISTA = lista,
        PRECIO_CORTO_PLAZO = corto,
        PRECIO_CONTADO = contado
    )

    private fun vm(tipo: String) = SaleProductsViewModel().apply {
        setTipoVenta(tipo)
        addProductToSale(producto, 1)
    }

    private fun triple(e: com.example.msp_app.core.database.entities.LocalSaleProductEntity) =
        Triple(e.PRECIO_CONTADO, e.PRECIO_CORTO_PLAZO, e.PRECIO_LISTA)

    @Test
    fun `E-APP-061 en CONTADO el producto se envia solo con contado sin editar precios`() {
        val v = vm("CONTADO")
        val lineas = LineasDeVenta.productos("v1", v.saleItems, "CONTADO")
        assertEquals(listOf(Triple(1000.0, 0.0, 0.0)), lineas.map(::triple))
    }

    @Test
    fun `E-APP-061 en CREDITO el producto se envia con los tres precios del catalogo`() {
        val lineas = LineasDeVenta.productos("v1", vm("CREDITO").saleItems, "CREDITO")
        assertEquals(listOf(Triple(1000.0, 1200.0, 1600.0)), lineas.map(::triple))
    }

    @Test
    fun `E-APP-061 credito a contado y de regreso a credito restaura los precios del catalogo`() {
        val v = vm("CREDITO")
        v.setTipoVenta("CONTADO")
        assertEquals(
            listOf(Triple(1000.0, 0.0, 0.0)),
            LineasDeVenta.productos("v1", v.saleItems, v.tipoVenta).map(::triple)
        )
        assertEquals(0.0, v.getTotalPrecioListaWithCombos(), 0.0)
        assertEquals(0.0, v.getTotalMontoCortoPlazoWithCombos(), 0.0)
        v.setTipoVenta("CREDITO")
        assertEquals(
            listOf(Triple(1000.0, 1200.0, 1600.0)),
            LineasDeVenta.productos("v1", v.saleItems, v.tipoVenta).map(::triple)
        )
        assertEquals(1600.0, v.getTotalPrecioListaWithCombos(), 0.0)
        assertEquals(1200.0, v.getTotalMontoCortoPlazoWithCombos(), 0.0)
    }

    @Test
    fun `E-APP-061 en CONTADO el combo creado en credito tambien sale solo con contado`() {
        val c = LineasDeVenta.combos(listOf(combo(7000.0, 6000.0, 5000.0)), "CONTADO").single()
        assertEquals(
            Triple(5000.0, 0.0, 0.0),
            Triple(c.PRECIO_CONTADO, c.PRECIO_CORTO_PLAZO, c.PRECIO_LISTA)
        )
        val k = LineasDeVenta.combos(listOf(combo(7000.0, 6000.0, 5000.0)), "CREDITO").single()
        assertEquals(
            Triple(5000.0, 6000.0, 7000.0),
            Triple(k.PRECIO_CONTADO, k.PRECIO_CORTO_PLAZO, k.PRECIO_LISTA)
        )
    }

    @Test
    fun `E-APP-061 E-APP-062 caso medido - contado combo 0-0-5000 mas producto ya no arma totales imposibles`() {
        val combos = LineasDeVenta.combos(listOf(combo(0.0, 0.0, 5000.0)), "CONTADO")
        val productos = LineasDeVenta.productos("v1", vm("CONTADO").saleItems, "CONTADO")
        // Lo que sale en el cuerpo ya no tiene orden que romper: el servidor no distingue
        // contado y antes esto era `monto_tier_order_invalid` (caso 016 del juego compartido).
        assertEquals(
            VeredictoOrdenDePrecios.Valido,
            LineasDeVenta.validar("CONTADO", productos, combos)
        )
        // Y la regla de orden sola (la del servidor, que no distingue contado) tampoco la rechaza.
        assertEquals(
            VeredictoOrdenDePrecios.Valido,
            ReglaOrdenDePrecios.validar(
                LineasDeVenta.lineas(productos, combos)
            )
        )
    }

    @Test
    fun `E-APP-062 en CREDITO el combo con anual capturado y totales imposibles se detiene`() {
        // Combo 5000/0/6000 (pasa E-APP-067 y su línea), totales 6000/1200/7600: imposibles.
        val combos = LineasDeVenta.combos(listOf(combo(6000.0, 0.0, 5000.0)), "CREDITO")
        val productos = LineasDeVenta.productos("v1", vm("CREDITO").saleItems, "CREDITO")
        val v = LineasDeVenta.validar("CREDITO", productos, combos)
        assertEquals(
            ReglaOrdenDePrecios.CODIGO_TOTALES,
            (v as VeredictoOrdenDePrecios.Invalido).codigo
        )
        assertTrue(
            LineasDeVenta.mensajeParaVendedor(
                v
            ).startsWith("Los totales de la venta son imposibles")
        )
    }

    @Test
    fun `E-APP-062 los productos dentro de un combo no suman a los totales como en el servidor`() {
        val v = SaleProductsViewModel().apply { addProductToSaleWithCombo(producto, 1, "c1") }
        val productos = LineasDeVenta.productos("v1", v.saleItems, "CREDITO")
        // Sumando el hijo: contado 1500 > corto 1200. Sin sumarlo: sólo el combo, 500/0/2000.
        assertEquals(
            VeredictoOrdenDePrecios.Valido,
            LineasDeVenta.validar("CREDITO", productos, listOf(combo(2000.0, 0.0, 500.0)))
        )
    }

    @Test
    fun `E-APP-067 en CREDITO un combo sin anual se detiene nombrando el combo`() {
        val productos = LineasDeVenta.productos("v1", vm("CREDITO").saleItems, "CREDITO")
        val v = LineasDeVenta.validar("CREDITO", productos, listOf(combo(0.0, 0.0, 5000.0)))
        v as VeredictoOrdenDePrecios.Invalido
        assertEquals("precio_anual_credito_requerido", v.codigo)
        assertTrue(LineasDeVenta.mensajeParaVendedor(v).contains("Recámara"))
    }

    @Test
    fun `E-APP-067 en CREDITO un producto suelto sin anual se detiene, dentro de combo no`() {
        val sinLista = TestDataFactory.createProductInventory(
            id = 9,
            name = "Buró",
            prices = "Precio 1 Meses:500.0"
        )
        val suelto = SaleProductsViewModel().apply { addProductToSale(sinLista, 1) }
        val v = LineasDeVenta.validar(
            "CREDITO",
            LineasDeVenta.productos("v1", suelto.saleItems, "CREDITO"),
            emptyList()
        )
        assertEquals(
            "precio_anual_credito_requerido",
            (v as VeredictoOrdenDePrecios.Invalido).codigo
        )

        val enCombo = SaleProductsViewModel().apply { addProductToSaleWithCombo(sinLista, 1, "c1") }
        assertEquals(
            VeredictoOrdenDePrecios.Valido,
            LineasDeVenta.validar(
                "CREDITO",
                LineasDeVenta.productos("v1", enCombo.saleItems, "CREDITO"),
                listOf(combo(1600.0, 1200.0, 1000.0))
            )
        )
        // En CONTADO no aplica.
        assertEquals(
            VeredictoOrdenDePrecios.Valido,
            LineasDeVenta.validar(
                "CONTADO",
                LineasDeVenta.productos("v1", suelto.saleItems, "CONTADO"),
                emptyList()
            )
        )
    }

    @Test
    fun `E-APP-068 en CONTADO un producto suelto o combo sin contado se detiene`() {
        val sinContado = TestDataFactory.createProductInventory(
            id = 8,
            name = "Cómoda",
            prices = "Precio de lista:1600.0"
        )
        val v = SaleProductsViewModel().apply {
            setTipoVenta("CONTADO")
            addProductToSale(sinContado, 1)
        }
        val veredicto = LineasDeVenta.validar(
            "CONTADO",
            LineasDeVenta.productos("v1", v.saleItems, "CONTADO"),
            emptyList()
        )
        veredicto as VeredictoOrdenDePrecios.Invalido
        assertEquals("precio_contado_requerido", veredicto.codigo)
        assertTrue(LineasDeVenta.mensajeParaVendedor(veredicto).contains("Cómoda"))
        // En crédito esa misma línea (sin contado, con anual) sí pasa.
        assertEquals(
            VeredictoOrdenDePrecios.Valido,
            LineasDeVenta.validar(
                "CREDITO",
                LineasDeVenta.productos("v1", v.saleItems, "CREDITO"),
                emptyList()
            )
        )
    }

    @Test
    fun `E-APP-068 caso 047 - con producto y combo malos se nombra el producto como el servidor`() {
        val suelto = TestDataFactory.createProductInventory(
            id = 8,
            name = "Cómoda",
            prices = "Precio de lista:1600.0"
        )
        val v = SaleProductsViewModel().apply {
            setTipoVenta("CONTADO")
            addProductToSale(suelto, 1)
        }
        val veredicto = LineasDeVenta.validar(
            "CONTADO",
            LineasDeVenta.productos("v1", v.saleItems, "CONTADO"),
            listOf(combo(3000.0, 2500.0, 0.0))
        ) as VeredictoOrdenDePrecios.Invalido
        assertEquals("precio_contado_requerido", veredicto.codigo)
        assertEquals("Cómoda", veredicto.linea?.nombre)
    }

    @Test
    fun `E-APP-067 aviso al pasar a credito`() {
        assertEquals(null, LineasDeVenta.avisoAlPasarACredito(emptyList(), emptyList()))
        assertEquals(
            "Precios del catálogo restaurados: Base King\nCaptura el precio anual de: Recámara",
            LineasDeVenta.avisoAlPasarACredito(listOf("Base King"), listOf("Recámara"))
        )
    }

    @Test
    fun `E-APP-062 linea desordenada nombra el articulo en el mensaje`() {
        val v = SaleProductsViewModel().apply { addProductToSale(producto, 1) }
        v.updateProductPrices(producto, 1600.0, 900.0, 1000.0)
        val productos = LineasDeVenta.productos("v1", v.saleItems, "CREDITO")
        val veredicto = LineasDeVenta.validar("CREDITO", productos, emptyList())
        veredicto as VeredictoOrdenDePrecios.Invalido
        assertEquals(ReglaOrdenDePrecios.CODIGO_LINEA, veredicto.codigo)
        assertEquals(
            "Los precios capturados de la línea deben ir de menor a mayor: contado, corto plazo y anual\n" +
                "Base King: contado 1000.00, corto plazo 900.00, anual 1600.00",
            LineasDeVenta.mensajeParaVendedor(veredicto)
        )
    }

    @Test
    fun `E-APP-061 propiedad - en CONTADO nunca sale corto ni anual y nunca se bloquea`() {
        repeat(SEMILLAS) { semilla ->
            val rnd = Random(semilla)
            val v = SaleProductsViewModel().apply { setTipoVenta("CONTADO") }
            repeat(rnd.nextInt(1, 5)) { i ->
                val p = TestDataFactory.createProductInventory(
                    id = i + 1,
                    prices = "Precio de lista:${rnd.nextInt(0, 9000)}.0, " +
                        "Precio 4 Meses:${rnd.nextInt(0, 9000)}.0, Precio 1 Meses:${rnd.nextInt(1, 9000)}.0"
                )
                v.addProductToSale(p, rnd.nextInt(1, 4))
            }
            val combos = LineasDeVenta.combos(
                listOf(
                    combo(
                        rnd.nextInt(0, 9000).toDouble(),
                        rnd.nextInt(0, 9000).toDouble(),
                        rnd.nextInt(1, 9000).toDouble()
                    )
                ),
                "CONTADO"
            )
            val productos = LineasDeVenta.productos("v1", v.saleItems, "CONTADO")
            assertTrue(productos.all { it.PRECIO_LISTA == 0.0 && it.PRECIO_CORTO_PLAZO == 0.0 })
            assertTrue(combos.all { it.PRECIO_LISTA == 0.0 && it.PRECIO_CORTO_PLAZO == 0.0 })
            assertEquals(
                "semilla $semilla",
                VeredictoOrdenDePrecios.Valido,
                LineasDeVenta.validar("CONTADO", productos, combos)
            )
        }
    }

    private companion object {
        const val SEMILLAS = 300
    }
}
