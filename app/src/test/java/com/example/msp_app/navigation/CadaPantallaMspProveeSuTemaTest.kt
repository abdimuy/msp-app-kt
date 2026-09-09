package com.example.msp_app.navigation

import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **La compuerta contra la pantalla número ocho, a escala del repo.**
 *
 * El contrato del design system —quien monte una pantalla Msp tiene que
 * envolverla en `MspTheme`— no está en ninguna firma: `:app` monta `MspappTheme`
 * (Material legado) y su `NavHost` no provee `MspTheme` a nadie, así que la
 * primera lectura de `MspTheme.colors` dentro de un destino revienta con
 * `IllegalStateException("MspTheme ausente")`.
 *
 * **Ese contrato ya se rompió dos veces.** La primera fue el piloto
 * `:feature:collectionReport`; el arreglo dejó una nota en el KDoc de
 * `CollectionReportScreen` que dice, palabra por palabra, «`:app` NUNCA provee
 * `MspTheme`». La nota estaba en `main` cuando nacieron las siete pantallas de
 * cobranza, y **no impidió nada**. Una nota no es una red.
 *
 * Esta sí lo es, y **no lista las pantallas a mano**: sale de las fuentes de
 * producción de TODOS los módulos del build (vía [EscanerDeFuentes], que los lee
 * de `settings.gradle.kts`). Una pantalla nueva —en un módulo existente o en uno
 * que todavía no existe— entra al escaneo el día que se escribe.
 *
 * ## Qué mide y qué NO mide
 *
 * Esto es un escaneo de fuentes: prueba que el envoltorio **está escrito**. Lo
 * que prueba que el envoltorio **funciona** son los tests que componen el punto
 * de entrada real sin aportar tema
 * (`feature.pagos.ui.ElTemaLoPoneLaPantallaTest` y su gemelo de visitas). Los
 * dos hacen falta: aquel mide, éste descubre.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class CadaPantallaMspProveeSuTemaTest {

    private val escaner = EscanerDeFuentes()

    /**
     * Toda pantalla de producción que viva en un módulo del design system Msp
     * tiene que proveerse el tema: o llamando a `MspTheme` directo, o a un
     * envoltorio que lo haga (`ThemeRevealRoot`, en el reporte de cobranza).
     */
    @Test
    fun `toda pantalla Msp de produccion provee su propio tema`() {
        val pantallas = pantallasDeModulosMsp()

        // Control positivo: una ausencia no vale hasta probar que el método
        // habría encontrado la cosa. Si el escaneo no ve las siete pantallas de
        // cobranza —que SÍ existen— tampoco vería la octava, y el verde de abajo
        // no significaría nada.
        assertTrue(
            "el escaneo no encontró ninguna pantalla Msp: no probaría nada",
            pantallas.isNotEmpty()
        )
        assertTrue(
            "el escaneo no vio las pantallas de cobranza ($pantallas): no probaría nada",
            pantallas.keys.containsAll(PANTALLAS_DE_COBRANZA)
        )

        val sinTema = pantallas.filterValues { !proveeTema(it) }.keys.toSortedSet()
        assertEquals(
            "estas pantallas leen MspTheme y nadie se lo da — crashean al abrirlas " +
                "desde el NavHost de :app (ver ConfiguracionScreen para el envoltorio)",
            emptySet<String>(),
            sinTema
        )
    }

    /**
     * El control positivo de arriba está escrito a mano, así que tiene que
     * envejecer con el grafo: esta prueba lo ata a
     * [destinosDeCobranza] —la MISMA función que monta la app— para que un
     * destino número ocho ponga rojo este archivo en vez de colarse por debajo
     * de una lista que nadie actualizó.
     */
    @Test
    fun `los destinos de cobranza son los que el control positivo vigila`() {
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph = nav.createGraph(startDestination = RAIZ_DE_PRUEBA) {
            composable(RAIZ_DE_PRUEBA) {}
            destinosDeCobranza(nav)
        }
        // -1 por la raíz de prueba, que no es un destino de cobranza.
        val destinos = nav.graph.count() - 1
        assertEquals(
            "destinosDeCobranza registra $destinos destinos y el control positivo " +
                "vigila ${PANTALLAS_DE_COBRANZA.size} pantallas: agrega la pantalla nueva " +
                "a PANTALLAS_DE_COBRANZA y su prueba de composición sin tema",
            PANTALLAS_DE_COBRANZA.size,
            destinos
        )
    }

    // -----------------------------------------------------------------------

    /** Nombre del composable → archivo que lo declara, para módulos que usan Msp. */
    private fun pantallasDeModulosMsp(): Map<String, File> {
        val archivosMsp = escaner.raices
            .map { raiz -> raiz.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .filter { archivos -> archivos.any { LEE_TEMA.containsMatchIn(codigo(it)) } }
            .flatten()
        return buildMap {
            archivosMsp.forEach { archivo ->
                PANTALLA.findAll(codigo(archivo)).forEach { put(it.groupValues[1], archivo) }
            }
        }
    }

    /**
     * ¿[archivo] provee el tema a su propio subárbol? O llama a `MspTheme`, o
     * llama a un composable declarado en un archivo que sí lo llama — un nivel
     * de indirección, **derivado**, no una lista de excepciones a mano: así
     * `ThemeRevealRoot` vale sin nombrarlo.
     */
    private fun proveeTema(archivo: File): Boolean {
        val codigo = codigo(archivo)
        if (INVOCA_TEMA.containsMatchIn(codigo)) return true
        val propias = DECLARACION.findAll(codigo).map { it.groupValues[1] }.toSet()
        return (proveedores - propias).any { proveedor ->
            Regex("(?<![A-Za-z0-9_])" + Regex.escape(proveedor) + "\\s*[({]")
                .containsMatchIn(codigo)
        }
    }

    /** Composables declarados en archivos que llaman a `MspTheme`. */
    private val proveedores: Set<String> by lazy {
        escaner.archivos
            .filter { INVOCA_TEMA.containsMatchIn(codigo(it)) }
            .flatMap { DECLARACION.findAll(codigo(it)).map { m -> m.groupValues[1] }.toList() }
            .toSet()
    }

    private companion object {

        const val RAIZ_DE_PRUEBA = "raiz_de_prueba"

        /**
         * Control positivo: las siete que este arreglo envolvió. No es la lista
         * que gobierna el test —esa sale del escaneo— sino la prueba de que el
         * escaneo ve algo real.
         */
        val PANTALLAS_DE_COBRANZA = setOf(
            "ListaDeClientesScreen",
            "DetalleClienteScreen",
            "DetalleVentaScreen",
            "RegistrarAbonoScreen",
            "TicketDePagoScreen",
            "RegistrarVisitaScreen",
            "TicketDeVisitaScreen"
        )

        /** `MspTheme(...)` o `MspTheme { ... }` — las DOS formas de llamada. */
        val INVOCA_TEMA = Regex("""(?<![.A-Za-z0-9_])MspTheme\s*[({]""")

        /** `MspTheme.colors` y hermanos: leer el tema, no proveerlo. */
        val LEE_TEMA = Regex("""(?<![A-Za-z0-9_])MspTheme\.""")

        /** `fun XxxScreen(` de nivel superior, que es la forma que el grafo monta. */
        val PANTALLA = Regex("""(?m)^(?:internal )?fun ([A-Z][A-Za-z0-9]*Screen)\(""")

        /** Cualquier composable de nivel superior, para derivar los proveedores. */
        val DECLARACION = Regex("""(?m)^(?:internal |private )?fun ([A-Z][A-Za-z0-9]*)\(""")

        /**
         * El código sin comentarios ni KDoc. Este plan documenta en el código los
         * defectos que mató —el KDoc de `CollectionReportScreen` NOMBRA a
         * `MspTheme` sin llamarlo—, así que contar comentarios volvería verde a
         * una pantalla por explicar el bug en vez de arreglarlo.
         */
        fun codigo(archivo: File): String = archivo.readLines()
            .map { it.trim() }
            .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }
            .joinToString("\n")
    }
}
