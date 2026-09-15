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
 * **La red que llega adonde el `NavHost` no llega.**
 *
 * [CadaDestinoDeCobranzaSeMontaTest] compone de verdad cada destino de
 * [destinosDeCobranza] y es la red fuerte — de grano de destino, sin nada que
 * mantener a mano. Pero solo ve **destinos de ese grafo**. Las otras pantallas
 * Msp del repo (`VersionBlockedScreen` en `:core:appgate`, `ConfiguracionScreen`
 * en `:feature:configuracion`, `CollectionReportScreen` en
 * `:feature:collectionReport`) no se registran ahí, y una pantalla Msp en un
 * módulo que todavía no existe tampoco. Para esas, esto es lo único que hay.
 *
 * Recorre las fuentes de producción de TODOS los módulos del build (vía
 * [EscanerDeFuentes], que los lee de `settings.gradle.kts`), se queda con los
 * módulos cuyo `src/main` lee `MspTheme.` y exige que **toda pantalla ahí provea
 * su propio tema**. Una pantalla nueva entra al escaneo el día que se escribe:
 * no hay lista.
 *
 * ## Cómo se decide "provee", y el verde falso que costó
 *
 * La primera versión aceptaba «llama a un composable declarado en un **archivo**
 * que llama a `MspTheme`». Medido sobre los 749 archivos de producción, eso
 * derivaba **59 proveedores**, y más de 40 eran hojas —`Aviso`, `Cargando`,
 * `ListaVacia`, `CampoDeBusqueda`…— que no proveen nada: entraron al conjunto
 * porque viven en el mismo archivo que una pantalla envuelta. Con eso, una
 * pantalla nueva que reusara un `*Content` público **sin envolver** pasaba en
 * verde. El arreglo de este crash **agrandó** ese agujero, porque metió siete
 * archivos más al conjunto.
 *
 * Ahora se decide por **función**, no por archivo, y un proveedor tiene que ser
 * lo que un proveedor es: un composable que **recibe un `content`** y lo envuelve
 * en `MspTheme`. Sigue siendo derivado —`ThemeRevealRoot` califica sin estar
 * nombrado en ninguna excepción—, pero una hoja sin ranura de contenido ya no
 * puede calificar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class CadaPantallaMspProveeSuTemaTest {

    private val escaner = EscanerDeFuentes()

    @Test
    fun `toda pantalla Msp de produccion provee su propio tema`() {
        val pantallas = pantallasDeModulosMsp()

        // Control positivo: una ausencia no vale hasta probar que el método
        // habría encontrado la cosa. Si el escaneo no ve las ocho pantallas de
        // cobranza —que SÍ existen— tampoco vería la novena.
        assertTrue(
            "el escaneo no encontró ninguna pantalla Msp: no probaría nada",
            pantallas.isNotEmpty()
        )
        assertTrue(
            "el escaneo no vio las pantallas de cobranza (${pantallas.map { it.nombre }}): " +
                "no probaría nada",
            pantallas.map { it.nombre }.containsAll(PANTALLAS_DE_COBRANZA)
        )

        val sinTema = pantallas.filterNot(::proveeTema).map { it.nombre }.toSortedSet()
        assertEquals(
            "estas pantallas leen MspTheme y nadie se lo da — crashean al abrirlas " +
                "desde el NavHost de :app (ver ConfiguracionScreen para el envoltorio)",
            emptySet<String>(),
            sinTema
        )
    }

    /**
     * El control positivo de arriba está escrito a mano, así que tiene que
     * envejecer con el grafo: esto lo ata a [destinosDeCobranza] —la MISMA
     * función que monta la app— para que un destino número ocho ponga rojo este
     * archivo en vez de colarse por debajo de una lista que nadie actualizó.
     */
    @Test
    fun `los destinos de cobranza son los que el control positivo vigila`() {
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph = nav.createGraph(startDestination = RAIZ_DE_PRUEBA) {
            composable(RAIZ_DE_PRUEBA) {}
            destinosDeCobranza(nav)
        }
        val destinos = nav.graph.count() - 1 // -1 por la raíz de prueba
        assertEquals(
            "destinosDeCobranza registra $destinos destinos y el control positivo vigila " +
                "${PANTALLAS_DE_COBRANZA.size} pantallas: agregá la pantalla nueva a " +
                "PANTALLAS_DE_COBRANZA",
            PANTALLAS_DE_COBRANZA.size,
            destinos
        )
    }

    // -----------------------------------------------------------------------

    /** Una función de nivel superior con su texto: la unidad de análisis. */
    private data class Declaracion(val nombre: String, val texto: String)

    private fun pantallasDeModulosMsp(): List<Declaracion> = escaner.raices
        .map { raiz -> raiz.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        .filter { archivos -> archivos.any { LEE_TEMA.containsMatchIn(codigo(it)) } }
        .flatten()
        .flatMap { declaraciones(codigo(it)) }
        .filter { ES_PANTALLA.matches(it.nombre) }

    private fun proveeTema(pantalla: Declaracion): Boolean =
        INVOCA_TEMA.containsMatchIn(pantalla.texto) ||
            (proveedores - pantalla.nombre).any { proveedor ->
                invocacionDe(proveedor).containsMatchIn(pantalla.texto)
            }

    /**
     * Un proveedor de tema es un composable que **recibe un `content`** y lo
     * envuelve —directa o indirectamente— en `MspTheme`. Una hoja sin ranura de
     * contenido no puede serlo, que es exactamente lo que el verde falso de la
     * versión anterior permitía.
     *
     * El cierre es **transitivo** porque el repo ya tiene una cadena de dos
     * saltos: `ThemeRevealRoot` → `ReportMspTheme` → `MspTheme`. Una sola vuelta
     * habría marcado en rojo a `CollectionReportScreen`, que está bien — medido.
     */
    private val proveedores: Set<String> by lazy {
        val candidatos = escaner.archivos
            .flatMap { declaraciones(codigo(it)) }
            .filter { RANURA.containsMatchIn(it.texto) }
        val encontrados = mutableSetOf<String>()
        var creció = true
        while (creció) {
            val nuevos = candidatos.filter { it.nombre !in encontrados }.filter { candidato ->
                INVOCA_TEMA.containsMatchIn(candidato.texto) ||
                    (encontrados - candidato.nombre).any {
                        invocacionDe(it).containsMatchIn(candidato.texto)
                    }
            }
            encontrados += nuevos.map { it.nombre }
            creció = nuevos.isNotEmpty()
        }
        encontrados
    }

    private companion object {

        const val RAIZ_DE_PRUEBA = "raiz_de_prueba"

        /**
         * Control positivo: las ocho que hoy registra el grafo. No es la lista
         * que gobierna el test —esa sale del escaneo— sino la prueba de que el
         * escaneo ve algo real.
         */
        val PANTALLAS_DE_COBRANZA = setOf(
            "ListaDeClientesScreen",
            "DetalleClienteScreen",
            "BitacoraScreen",
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

        /** La ranura de contenido que distingue un envoltorio de una hoja. */
        val RANURA = Regex("""@Composable\s*\(\s*\)\s*->\s*Unit""")

        val ES_PANTALLA = Regex("""[A-Z][A-Za-z0-9]*Screen""")

        /** Arranque de una declaración de nivel superior (columna cero). */
        val DECLARACION = Regex("""^(?:internal |private |public )?fun ([A-Za-z][A-Za-z0-9]*)\(""")

        /** Cualquier otra cosa de nivel superior: cierra la declaración anterior. */
        val OTRO_NIVEL_SUPERIOR = Regex(
            """^(?:@|(?:internal |private |public |abstract |open |data |sealed )*""" +
                """(?:val|var|class|object|interface|enum|typealias|fun)\b)"""
        )

        fun invocacionDe(nombre: String) =
            Regex("(?<![A-Za-z0-9_])" + Regex.escape(nombre) + """\s*[({]""")

        /**
         * Parte [codigo] en declaraciones de nivel superior. Se hace por
         * **función** y no por archivo a propósito: por archivo, una pantalla sin
         * envolver escrita al lado de una envuelta pasaba en verde.
         */
        fun declaraciones(codigo: String): List<Declaracion> {
            val declaraciones = mutableListOf<Declaracion>()
            var nombre: String? = null
            val cuerpo = StringBuilder()
            codigo.lineSequence().forEach { linea ->
                val inicio = DECLARACION.find(linea)
                if (inicio != null || OTRO_NIVEL_SUPERIOR.containsMatchIn(linea)) {
                    nombre?.let { declaraciones += Declaracion(it, cuerpo.toString()) }
                    cuerpo.setLength(0)
                    nombre = inicio?.groupValues?.get(1)
                }
                if (nombre != null) cuerpo.appendLine(linea)
            }
            nombre?.let { declaraciones += Declaracion(it, cuerpo.toString()) }
            return declaraciones
        }

        /**
         * El código sin comentarios ni KDoc. Este plan documenta en el código los
         * defectos que mató —el KDoc de `CollectionReportScreen` NOMBRA a
         * `MspTheme` sin llamarlo—, así que contar comentarios volvería verde a
         * una pantalla por explicar el bug en vez de arreglarlo.
         */
        fun codigo(archivo: File): String = archivo.readLines()
            .filterNot { linea ->
                val limpia = linea.trim()
                limpia.startsWith("//") || limpia.startsWith("*") || limpia.startsWith("/*")
            }
            .joinToString("\n")
    }
}
