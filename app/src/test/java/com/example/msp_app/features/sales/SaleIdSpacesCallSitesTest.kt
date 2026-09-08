package com.example.msp_app.features.sales

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Arreglo A — control de reversión **por sitio**.
 *
 * [SaleIdSpacesRoomContractTest] prueba que cada selector entrega el id que su consulta
 * filtra; eso se pone rojo si alguien cambia un selector. Falta la otra mitad: que los cinco
 * **call sites** sigan pasando por el selector. Ninguno de los tres archivos es montable en
 * un test —`SaleDetailsScreen` arrastra el drawer, el mapa y dos ViewModels de Android;
 * `NewForgivenessDialog` arranca un `ForegroundService`—, así que la lectura del propio
 * fuente es el control de reversión más fuerte disponible **sin rediseñar las pantallas
 * legadas**, que este arreglo tiene prohibido. Revertir cualquiera de las cinco líneas a
 * `sale.DOCTO_CC_*` pone este test rojo, y nombra cuál.
 *
 * Es la misma familia de guardarraíl que `checkNoLegacyDateApi` en `prePushCheck`.
 *
 * **Control positivo:** cada aserción se apoya en [leer], que falla si el archivo no existe o
 * viene vacío. Sin eso, un `contains` sobre un string vacío reportaría "no está" por la razón
 * equivocada — una ausencia que no prueba nada.
 */
class SaleIdSpacesCallSitesTest {

    private fun leer(rutaRelativa: String): String {
        val candidatos = listOf(
            File(rutaRelativa),
            File("app/$rutaRelativa"),
            File("../app/$rutaRelativa")
        )
        val archivo = candidatos.firstOrNull { it.isFile }
        assertTrue(
            "no se encontró $rutaRelativa desde ${File(".").absolutePath} — " +
                "el test no puede afirmar nada sobre un archivo que no leyó",
            archivo != null
        )
        val texto = archivo!!.readText()
        assertTrue("$rutaRelativa vino vacío", texto.length > 100)
        return texto
    }

    private fun exigir(fuente: String, sitio: String, fragmento: String) {
        assertTrue(
            "$sitio dejó de pasar por SaleIdSpaces: no se encontró `$fragmento`. " +
                "Ese es el arreglo A; revertirlo devuelve el id del otro espacio a la consulta.",
            fuente.contains(fragmento)
        )
    }

    private val historial =
        "src/main/java/com/example/msp_app/features/sales/components/" +
            "paymentshistorysection/PaymentsHistorySection.kt"
    private val condonacion =
        "src/main/java/com/example/msp_app/features/forgiveness/components/" +
            "NewForgivenessDialog.kt"
    private val detalle =
        "src/main/java/com/example/msp_app/features/sales/screens/SaleDetailsScreen.kt"
    private val garantia =
        "src/main/java/com/example/msp_app/features/guarantees/screens/GuaranteesScreen.kt"

    @Test
    fun `el historial de pagos del detalle legado pide el id por SaleIdSpaces`() {
        val fuente = leer(historial)
        exigir(
            fuente,
            "PaymentsHistorySection (historial de pagos)",
            "getGroupedPaymentsBySaleId(saleId = SaleIdSpaces.forSalePayments(sale))"
        )
        assertTrue(
            "quedó una lectura cruda de sale.DOCTO_CC_ID en el historial",
            !fuente.contains("getGroupedPaymentsBySaleId(saleId = sale.DOCTO_CC_ID)")
        )
    }

    @Test
    fun `la relectura tras condonar pide el id por SaleIdSpaces`() {
        val fuente = leer(condonacion)
        exigir(
            fuente,
            "NewForgivenessDialog (relectura tras condonar)",
            "getGroupedPaymentsBySaleId(SaleIdSpaces.forSalePayments(sale))"
        )
        assertTrue(
            "quedó una lectura cruda de sale.DOCTO_CC_ID en la condonación",
            !fuente.contains("getGroupedPaymentsBySaleId(sale.DOCTO_CC_ID)")
        )
    }

    @Test
    fun `los atrasos del detalle salen de la venta cargada y no del argumento de la ruta`() {
        val fuente = leer(detalle)
        exigir(
            fuente,
            "SaleDetailsScreen:81 (PaymentProgressCard)",
            "getOverduePaymentBySaleId(SaleIdSpaces.forOverdueView(saleSuccess.data))"
        )
        assertTrue(
            "volvió a pasarse el argumento de la ruta a la vista de atrasos",
            !fuente.contains("getOverduePaymentBySaleId(saleId)")
        )
    }

    @Test
    fun `el mapa de la venta pide el id de los pagos por SaleIdSpaces`() {
        val fuente = leer(detalle)
        exigir(
            fuente,
            "SaleDetailsScreen:165 (mapa de la venta)",
            "Screen.SaleMap.createRoute(saleId = SaleIdSpaces.forSalePayments(sale))"
        )
        assertTrue(
            "quedó una lectura cruda de sale.DOCTO_CC_ID en la ruta del mapa",
            !fuente.contains("Screen.SaleMap.createRoute(saleId = sale.DOCTO_CC_ID)")
        )
    }

    @Test
    fun `otras ventas del cliente navega con la PK que la ruta resuelve`() {
        val fuente = leer(detalle)
        exigir(
            fuente,
            "SaleDetailsScreen:239 (otras ventas del cliente)",
            "saleId = SaleIdSpaces.forSaleRow(saleItem)"
        )
        assertTrue(
            "volvió a navegarse con saleItem.DOCTO_CC_ID, que no es lo que getById filtra",
            !fuente.contains("saleId = saleItem.DOCTO_CC_ID")
        )
    }

    @Test
    fun `la pantalla de garantia resuelve la venta por el credito`() {
        val fuente = leer(garantia)
        exigir(
            fuente,
            "GuaranteesScreen:108 (venta de la garantía)",
            "saleViewModel.loadSaleDetailsByCreditId(saleId)"
        )
        assertTrue(
            "volvió a resolverse con getById, que filtra la PK y no el crédito",
            !fuente.contains("saleViewModel.loadSaleDetails(saleId)")
        )
    }
}
