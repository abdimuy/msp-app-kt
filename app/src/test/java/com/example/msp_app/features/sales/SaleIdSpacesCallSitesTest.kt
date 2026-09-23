package com.example.msp_app.features.sales

import java.io.File
import org.junit.Assert.assertEquals
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
 *
 * ## Arreglo B — los pines se quedan, el CONJUNTO se descubre (I3)
 *
 * Hasta acá este archivo era **solo** los pines de abajo: cinco (luego seis) substrings escritos
 * a mano. Documentaba el contrato y no lo impedía — un séptimo call site entraba sin ruido, y de
 * hecho **ya habían entrado dos**: `GuaranteeSection` construía la ruta de garantía con
 * `sale.DOCTO_CC_ID` crudo, en dos sitios, y nadie los miraba.
 *
 * Ahora hay dos mitades y cada una tapa lo que la otra no puede:
 *
 *  - [`ningun call site pasa un id de venta crudo`] **descubre** los sitios: barre el `src/main`
 *    de todos los módulos de `settings.gradle.kts` y falla ante cualquier lectura cruda de
 *    `DOCTO_CC_ID`/`DOCTO_CC_ACR_ID` pasada como argumento. Un call site nuevo entra a la regla
 *    sin que nadie lo registre.
 *  - Los pines por sitio **siguen**, porque hay un caso que ninguna regla sobre lecturas de campo
 *    puede ver: `SaleDetailsScreen:81` pasaba **el argumento de la ruta** (`saleId`), no un campo.
 *    Revertirlo no reintroduce ninguna lectura cruda, así que solo el pin lo atrapa.
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

    // ── La mitad que descubre ────────────────────────────────────────────────

    /**
     * Barre **todos** los módulos del build y falla ante cualquier lectura cruda de
     * `DOCTO_CC_ID`/`DOCTO_CC_ACR_ID` pasada como argumento a una llamada.
     *
     * Comparaciones y chequeos de nulo quedan fuera a propósito: `if (a.DOCTO_CC_ID == b...)` no
     * entrega un id a nadie, y meterlo solo agregaría ruido sin cubrir un defecto de esta familia.
     * Las construcciones por argumento nombrado (`DOCTO_CC_ACR_ID = sale.DOCTO_CC_ACR_ID,`) son
     * la capa de escritura, no la de consumo, y tampoco entran: allí el campo destino nombra la
     * columna, que es justo lo que este contrato pide.
     */
    @Test
    fun `ningun call site pasa un id de venta crudo`() {
        val archivos = fuentesDeProduccion()

        // Control positivo del barrido: sin archivos, la ausencia de violaciones no prueba nada.
        assertTrue(
            "el barrido no leyó ningún .kt de producción — su verde no vale",
            archivos.size > 100
        )
        assertTrue(
            "el barrido no vio SaleIdSpaces.kt: el descubrimiento está roto",
            archivos.keys.any { it.endsWith("features/sales/SaleIdSpaces.kt") }
        )

        val vistas = Exenciones()
        val violaciones = archivos.flatMap { (ruta, texto) ->
            texto.lines().mapIndexedNotNull { indice, linea ->
                val codigo = linea.trim()
                if (codigo.startsWith("*") || codigo.startsWith("//")) return@mapIndexedNotNull null
                if (!LECTURA_CRUDA.containsMatchIn(codigo)) return@mapIndexedNotNull null
                if (COMPARACION.containsMatchIn(codigo)) return@mapIndexedNotNull null
                if (vistas.exento(ruta, codigo)) return@mapIndexedNotNull null
                "$ruta:${indice + 1}: $codigo"
            }
        }.sorted()

        assertEquals(
            "Estos call sites entregan un id de venta crudo en vez de pedirlo por SaleIdSpaces: " +
                "$violaciones.\n" +
                "`CLIENTE_ID`, `DOCTO_CC_ID` y `DOCTO_CC_ACR_ID` son tres `Int` pelados y el " +
                "compilador nunca avisa cuando uno se pasa donde iba otro — de ahí salió toda la " +
                "familia de defectos de esta rama. Pedí el id por el selector que nombra al " +
                "consumidor, o agregá la línea exacta a `LINEAS_EXENTAS` CON su razón escrita.",
            emptyList<String>(),
            violaciones
        )
    }

    /**
     * Cuenta las exenciones consumidas: [LINEAS_EXENTAS] no dice sólo QUÉ línea se exime,
     * dice **cuántas veces**. Sin el conteo, una segunda línea de texto idéntico en el mismo
     * archivo quedaba exenta gratis, y una exención por línea se volvía una por archivo — que
     * es exactamente la regla ablandada que la ronda 1 de revisión señaló.
     */
    private class Exenciones {
        private val consumidas = mutableMapOf<Pair<String, String>, Int>()

        fun exento(ruta: String, codigo: String): Boolean {
            val permitidas = LINEAS_EXENTAS[ruta]?.get(codigo) ?: return false
            val clave = ruta to codigo
            val usadas = consumidas.getOrDefault(clave, 0)
            if (usadas >= permitidas) return false
            consumidas[clave] = usadas + 1
            return true
        }
    }

    /** Todo `.kt` de producción de todos los módulos declarados en `settings.gradle.kts`. */
    private fun fuentesDeProduccion(): Map<String, String> {
        val raiz = raizDelRepo()
        val rutas = INCLUDE.findAll(File(raiz, "settings.gradle.kts").readText())
            .map { it.groupValues[1].trim(':').replace(':', '/') }
            .toList()
        check(rutas.isNotEmpty()) { "no se leyó ningún include(...) de settings.gradle.kts" }

        return rutas.flatMap { modulo ->
            val srcMain = File(raiz, "$modulo/src/main")
            if (!srcMain.isDirectory) {
                emptyList()
            } else {
                srcMain.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .map { it.relativeTo(raiz).invariantSeparatorsPath to it.readText() }
                    .toList()
            }
        }.toMap()
    }

    private fun raizDelRepo(): File {
        var actual: File? = File(".").absoluteFile
        while (actual != null) {
            if (File(actual, "settings.gradle.kts").isFile) return actual
            actual = actual.parentFile
        }
        error("no se encontró settings.gradle.kts subiendo desde ${File(".").absolutePath}")
    }

    private companion object {
        val INCLUDE = Regex("""include\("(:[^"]+)"\)""")

        /** Una lectura de campo entregada como argumento: `algo(… x.DOCTO_CC_ID …)`. */
        val LECTURA_CRUDA = Regex("""\(\s*[^()]*?\b\w+\.DOCTO_CC_(?:ACR_)?ID\b""")

        /** Comparaciones y chequeos de nulo: no entregan el id a ningún consumidor. */
        val COMPARACION =
            Regex("""\.DOCTO_CC_(?:ACR_)?ID\s*(==|!=|\?:)|(==|!=)\s*\w+\.DOCTO_CC_(?:ACR_)?ID""")

        /**
         * Líneas exentas, por contenido exacto y con su razón. Por contenido y no por archivo a
         * propósito: una SEGUNDA lectura cruda en el mismo archivo sigue fallando.
         */
        val LINEAS_EXENTAS: Map<String, Map<String, Int>> = mapOf(
            // ── LA MISMA DEUDA, TRES VECES ────────────────────────────────────────────
            // Las tres líneas de abajo son **un solo defecto** repetido: entregan
            // `Payment.DOCTO_CC_ACR_ID` —que es el **cargo**, = `sales.DOCTO_CC_ID`— a un
            // consumidor que resuelve la venta por su **PK** (`SaleDao.getById`, detrás de
            // `loadSaleDetails` y de la ruta `pagos/venta/{ventaId}`). Hoy no rompen nada
            // porque las dos columnas de `sales` llevan siempre el mismo número, probado por
            // construcción en el KDoc de `SaleIdSpaces`. El arreglo honesto es resolver por
            // crédito, como hizo el Arreglo A con la garantía, y eso cambia la consulta de
            // pantallas del camino del dinero: merece su tarea.
            //
            // Van por LÍNEA y no por archivo. La ronda 1 de revisión encontró que
            // `DestinosDeCobranza` estaba exento ENTERO —seis líneas tapadas de un saque,
            // de las cuales solo estas dos eran deuda— y que eso ablandaba la regla que este
            // arreglo defiende. Las otras cuatro pasaron a `SaleIdSpaces.forSaleRow`, que es
            // lo que había que hacer con ellas.
            "app/src/main/java/com/example/msp_app/navigation/DestinosDeCobranza.kt" to mapOf(
                "fun ventaDeUnPago(payment: Payment): String = " +
                    "PagosRutas.detalleVenta(payment.DOCTO_CC_ACR_ID)" to 1,
                "fun ventaDeUnRecibo(payment: Payment): String = " +
                    "PagosRutas.detalleVenta(payment.DOCTO_CC_ACR_ID)" to 1
            ),
            // DEUDA REAL, no una excepción cómoda — y va al reporte del Arreglo B.
            // `loadSaleDetails` resuelve con `SaleDao.getById`, que filtra la **PK**
            // (`sales.DOCTO_CC_ACR_ID`); lo que el recibo tiene en la mano es
            // `Payment.DOCTO_CC_ACR_ID`, que es el **cargo** (= `sales.DOCTO_CC_ID`). Hoy no
            // rompe nada porque las dos columnas de `sales` llevan siempre el mismo número, y
            // eso está probado por construcción en el KDoc de `SaleIdSpaces`. El arreglo honesto
            // es `loadSaleDetailsByCreditId`, igual que hizo el Arreglo A con la garantía — pero
            // eso cambia la consulta de una pantalla del camino del dinero y merece su tarea, no
            // un renglón de esta. NO se inventa un selector para taparlo: nombrar "PK de la
            // venta" a un cargo sería escribir en `SaleIdSpaces` la confusión que existe para
            // impedir.
            "app/src/main/java/com/example/msp_app/features/payments/screens/PaymentTicketScreen.kt" to
                mapOf("saleViewModel.loadSaleDetails(payment.DOCTO_CC_ACR_ID)" to 1)
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
