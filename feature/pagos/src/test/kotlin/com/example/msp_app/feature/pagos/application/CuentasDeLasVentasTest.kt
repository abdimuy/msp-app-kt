package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.data.fake.FakeProductosPort
import com.example.msp_app.feature.pagos.domain.model.ProductoDeVenta
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El mapa `ventaId → cuenta` que [com.example.msp_app.feature.pagos.domain.BitacoraDelCliente.de]
 * necesita: qué renglón nombra la cuenta, y cuántas veces se pregunta.
 *
 * Usa el caso real de `task-2-brief.md`: dos cuentas del mismo cliente,
 * `Y00001786` (recámara + base de cama) y `Y00002103` (bocina sola).
 */
class CuentasDeLasVentasTest {

    private val productosPort = FakeProductosPort()

    private val ventaRecamara = PagosFixtures.datosDeVenta(
        ventaId = VENTA_RECAMARA,
        folio = "Y00001786",
        descripcion = "Recámara y base",
        cifras = PagosFixtures.Cifras(dinero("8400"), dinero("2100"), dinero("350"), dinero("6300"))
    )

    private val ventaBocina = PagosFixtures.datosDeVenta(
        ventaId = VENTA_BOCINA,
        folio = "Y00002103",
        descripcion = "Bocina",
        cifras = PagosFixtures.Cifras(dinero("1200"), dinero("100"), dinero("100"), dinero("1100"))
    )

    /**
     * **Qué producto nombra una cuenta de varios artículos: el PRIMERO de la
     * lista.** [FakeProductosPort] contesta EXACTAMENTE lo que se le siembra,
     * así que el orden que ve [aCuentas] es el que el fake recibió — en
     * producción, el que [com.example.msp_app.feature.pagos.data.adapter.RoomProductosAdapter]
     * ya promete entregar por `POSICION`. Esta prueba cobra que la selección
     * sea "el primero", no la de mayor precio: la base de cama cuesta más caro
     * aquí adentro y aun así pierde.
     */
    @Test
    fun `una cuenta de varios articulos se nombra con el primero de la lista`() = runTest {
        productosPort.porFolio = mapOf(
            "Y00001786" to listOf(
                ProductoDeVenta("RECAMARA CANTARO KING SIZE CHOCOLATE", dinero("300")),
                // Más cara que la recámara adentro de este fixture, y aun así
                // no debe ganar: el criterio es POSICION, no Money.
                ProductoDeVenta("BASE DE CAMA MATRIMONIAL", dinero("500"))
            )
        )

        val cuentas = cuentasDeLasVentas(listOf(ventaRecamara), productosPort)

        assertEquals(
            "Recamara cantaro king size chocolate",
            cuentas.getValue(VENTA_RECAMARA)
        )
    }

    /** El par que prueba el join, ahora a través del helper completo. */
    @Test
    fun `dos cuentas del mismo cliente con productos distintos dan nombres distintos`() = runTest {
        productosPort.porFolio = mapOf(
            "Y00001786" to listOf(ProductoDeVenta("RECAMARA CANTARO KING SIZE CHOCOLATE", dinero("400"))),
            "Y00002103" to listOf(ProductoDeVenta("BOCINA PROFESIONAL 8'' AUDIOBAHN", dinero("100")))
        )

        val cuentas = cuentasDeLasVentas(listOf(ventaRecamara, ventaBocina), productosPort)

        assertEquals("Recamara cantaro king size chocolate", cuentas.getValue(VENTA_RECAMARA))
        assertEquals("Bocina profesional 8'' audiobahn", cuentas.getValue(VENTA_BOCINA))
    }

    /**
     * **Sin producto, sin cuenta.** Un folio que no sincronizó sus renglones
     * de `products` todavía no entra al mapa — ni con `null` explícito, ni con
     * ninguna llave: es responsabilidad de quien LEE el mapa (`BitacoraDelCliente.de`,
     * vía `Map.get`) tratar la ausencia como `null`.
     */
    @Test
    fun `una venta sin renglones de producto no entra al mapa`() = runTest {
        productosPort.porFolio = mapOf(
            "Y00001786" to listOf(ProductoDeVenta("RECAMARA CANTARO KING SIZE CHOCOLATE", dinero("400")))
            // "Y00002103" no está sembrado: el fake contesta vacío, igual que
            // el adaptador real ante un folio no sincronizado.
        )

        val cuentas = cuentasDeLasVentas(listOf(ventaRecamara, ventaBocina), productosPort)

        assertTrue(cuentas.containsKey(VENTA_RECAMARA))
        assertFalse(
            "una cuenta sin producto no debe tener llave, ni con null explícito",
            cuentas.containsKey(VENTA_BOCINA)
        )
    }

    /**
     * **Una consulta por VENTA, no por pago.** Dos cuentas piden dos folios,
     * sin importar cuántos abonos tenga cada una — la mezcla de contactos
     * pregunta el mapa ya resuelto, nunca el puerto directamente.
     */
    @Test
    fun `pide un folio por venta, ni uno mas ni uno menos`() = runTest {
        cuentasDeLasVentas(listOf(ventaRecamara, ventaBocina), productosPort)

        assertEquals(
            listOf("Y00001786", "Y00002103"),
            productosPort.foliosConsultados
        )
    }

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))

    private companion object {
        const val VENTA_RECAMARA = 12_845_224
        const val VENTA_BOCINA = 14_431_255
    }
}
