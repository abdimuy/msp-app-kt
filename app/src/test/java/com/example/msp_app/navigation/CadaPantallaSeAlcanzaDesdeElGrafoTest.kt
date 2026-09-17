package com.example.msp_app.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Ninguna pantalla construida y muerta.**
 *
 * ## El defecto que esta red existe para no repetir
 *
 * `DescargaDelDictadoScreen` (`:core:speech`) se escribió entera —con su
 * dominio, su puerto, su adaptador, sus pruebas y sus goldens— y **no quedó
 * registrada en el grafo de navegación**. O sea: el cobrador no tenía forma de
 * llegar a bajar el modelo de voz. Una función completa, probada y sin puerta.
 * `DescargaDelMapaScreen` de `:core:mapas` era la gemela, y se fue con su
 * módulo: sin renderizador dentro de la app no hay qué bajar.
 *
 * Nada en la compuerta lo veía, y no por descuido: cada red existente mira el
 * grafo **desde adentro**. [CadaDestinoDeCobranzaSeMontaTest] compone lo que
 * [destinosDeCobranza] registre; [ReglaDelOrigenTest] compara lo registrado
 * contra lo que algo alcanza; [CadaPantallaMspProveeSuTemaTest] exige que toda
 * pantalla provea su tema. Las tres son verdaderas sobre una pantalla que nadie
 * registró: sencillamente no hablan de ella. Faltaba la pregunta al revés —
 * **¿toda pantalla que existe tiene puerta?**— y es la que se hace acá.
 *
 * ## Cómo se decide "se alcanza"
 *
 * No es una lista de dos nombres: una pantalla futura la dispara igual, el día
 * que se escribe, sin que nadie agregue nada.
 *
 * 1. Se leen las fuentes de producción de **todos** los módulos del build (vía
 *    [EscanerDeFuentes], que los saca de `settings.gradle.kts`).
 * 2. Se arma el grafo de archivos: un archivo **alcanza** a otro si su código
 *    nombra un símbolo de nivel superior que el otro declara.
 * 3. Se cierra transitivamente **desde `:app`**, que es donde vive el `NavHost`
 *    y por lo tanto la raíz de todo lo que el usuario puede llegar a ver.
 * 4. Una pantalla está viva si **algún archivo alcanzable, que no sea el suyo**,
 *    nombra su función. Excluir el archivo propio es la mitad que importa: sin
 *    eso, una pantalla muerta escrita al lado de una viva pasaría en verde
 *    —exactamente el verde falso que [CadaPantallaMspProveeSuTemaTest] ya pagó
 *    una vez por decidir por archivo en vez de por función.
 *
 * El recorrido es **por archivo** y el veredicto **por función**: la propagación
 * gruesa sólo puede hacer el conjunto alcanzable más grande, y aun así el
 * veredicto exige que alguien nombre la pantalla por su nombre.
 *
 * ## Lo que esta red NO cubre, y quién lo cubre
 *
 * Contesta "¿alguien la nombra?", no "¿está registrada en el `NavHost`?". Una
 * función de registro **escrita y nunca llamada** seguiría nombrando la pantalla
 * y esto quedaría verde. Esa mitad la cobran, sobre el grafo REAL y no sobre las
 * fuentes, [ReglaDelOrigenTest] —que compara los destinos registrados contra el
 * conjunto exacto que algo alcanza— y [CadaPantallaMspProveeSuTemaTest], que
 * cuenta los destinos de [destinosDeCobranza]. Las dos se ponen rojas si alguien
 * borra la llamada. Está escrito acá para que nadie lea este archivo creyendo
 * que cubre lo que no cubre.
 *
 * ## Por qué `:app` queda fuera del veredicto
 *
 * `:app` es legado (Ruling I de `global-constraints.md`) y hoy tiene **dos**
 * pantallas muertas de antes de este plan. No se arreglan de paso —sería
 * alcance ajeno— y se usan para algo mejor: son el control positivo de
 * [`el mismo método encuentra pantallas muertas en el app legado`], la prueba
 * de que este detector sabe contestar que NO.
 */
class CadaPantallaSeAlcanzaDesdeElGrafoTest {

    private val escaner = EscanerDeFuentes()

    /** El código de cada archivo de producción, sin comentarios ni KDoc. */
    private val codigo: Map<File, String> by lazy {
        escaner.archivosPorModulo.values.flatten().associateWith(::codigoDe)
    }

    /** Nombre de nivel superior → los archivos que lo declaran. */
    private val declarantes: Map<String, Set<File>> by lazy {
        val mapa = mutableMapOf<String, MutableSet<File>>()
        codigo.forEach { (archivo, texto) ->
            texto.lineSequence().forEach { linea ->
                DECLARACIONES.firstNotNullOfOrNull { it.find(linea)?.groupValues?.get(1) }
                    ?.let { mapa.getOrPut(it) { mutableSetOf() } += archivo }
            }
        }
        mapa
    }

    /**
     * Los símbolos declarados que cada archivo nombra. Se tokeniza una sola vez
     * y se cruza con los declarados: buscar 2280 nombres con 2280 expresiones
     * regulares sobre 815 archivos es el mismo resultado y dos órdenes de
     * magnitud más lento.
     */
    private val nombrados: Map<File, Set<String>> by lazy {
        codigo.mapValues { (_, texto) ->
            IDENTIFICADOR.findAll(texto).map { it.value }.filterTo(mutableSetOf()) {
                it in declarantes
            }
        }
    }

    /** Pantalla (`fun …Screen(` de nivel superior) → el archivo que la declara. */
    private val pantallas: Map<String, File> by lazy {
        buildMap {
            codigo.forEach { (archivo, texto) ->
                texto.lineSequence().forEach { linea ->
                    PANTALLA.find(linea)?.groupValues?.get(1)?.let { put(it, archivo) }
                }
            }
        }
    }

    // -----------------------------------------------------------------------

    @Test
    fun `toda pantalla de core y feature se alcanza desde el grafo de app`() {
        // Control positivo: si el escaneo no viera las pantallas que SÍ están
        // montadas, tampoco vería la que no, y el `assertEquals` de abajo
        // pasaría por conjunto vacío sin haber mirado nada.
        assertTrue(
            "el escaneo no encontró ninguna pantalla: no probaría nada",
            pantallas.isNotEmpty()
        )
        assertTrue(
            "el escaneo no vio las pantallas que hoy SÍ están montadas " +
                "(${pantallas.keys.sorted()}): no probaría nada",
            pantallas.keys.containsAll(PANTALLAS_MONTADAS)
        )

        val muertas = pantallasMuertas(nuevas = true)
        assertEquals(
            "estas pantallas existen, compilan y no están registradas en ningún destino: " +
                "el cobrador no puede llegar a ellas. Registralas en el grafo (ver " +
                "`destinosDeDescargas` en DestinosDeCobranzaGraph.kt) o borralas",
            emptyList<String>(),
            muertas
        )
    }

    /**
     * **El control positivo del detector, con sujeto real.**
     *
     * Un detector que contestara "todo bien" siempre haría pasar el test de
     * arriba para siempre. Éste exige que el MISMO cálculo, corrido sobre el
     * `:app` legado, siga encontrando las pantallas muertas que ahí hay: si
     * dejara de encontrarlas, o el legado se limpió —y entonces hay que buscarle
     * otro sujeto a este control— o el método dejó de mirar.
     */
    @Test
    fun `el mismo metodo encuentra pantallas muertas en el app legado`() {
        val muertasDelLegado = pantallasMuertas(nuevas = false)
        assertTrue(
            "el mismo método no encontró NINGUNA pantalla inalcanzable en el :app legado. " +
                "Medidas el 2026-09-16: LoadingScreen y SaleHomeScreen. Si de verdad ya no " +
                "queda ninguna, este control positivo perdió su sujeto: hay que darle otro, " +
                "porque sin él el verde del test de arriba podría ser un método que no mira",
            muertasDelLegado.isNotEmpty()
        )
    }

    // -----------------------------------------------------------------------

    /**
     * Las pantallas sin puerta. [nuevas] `true` mira `:core:*`/`:feature:*` —lo
     * que la regla gobierna—; `false` mira el `:app` legado, que es el sujeto
     * del control positivo.
     */
    private fun pantallasMuertas(nuevas: Boolean): List<String> {
        val alcanzables = cierreDesdeApp()
        return pantallas
            .filter { (_, archivo) -> esDeArquitecturaNueva(archivo) == nuevas }
            .filterNot { (nombre, archivo) ->
                alcanzables.any { otro -> otro != archivo && nombre in nombrados.getValue(otro) }
            }
            .map { (nombre, archivo) -> "$nombre (${archivo.path})" }
            .sorted()
    }

    /**
     * El cierre transitivo de "quién nombra a quién" **desde `:app`**, que es
     * donde vive el `NavHost` y por lo tanto la raíz de lo que el cobrador puede
     * llegar a ver.
     */
    private fun cierreDesdeApp(): Set<File> {
        val semilla = escaner.archivosPorModulo.getValue("app").toSet()
        check(semilla.isNotEmpty()) { "no se leyó un solo archivo de :app: no probaría nada" }
        val alcanzados = semilla.toMutableSet()
        var frontera = semilla
        while (frontera.isNotEmpty()) {
            val siguiente = frontera
                .flatMap { archivo -> nombrados.getValue(archivo) }
                .flatMap { nombre -> declarantes.getValue(nombre) }
                .filterNot { it in alcanzados }
                .toSet()
            alcanzados += siguiente
            frontera = siguiente
        }
        return alcanzados
    }

    private fun esDeArquitecturaNueva(archivo: File): Boolean =
        MODULOS_NUEVOS.any { archivo.path.contains("/$it/") }

    private companion object {

        /**
         * Las pantallas que HOY están montadas. No es la lista que gobierna el
         * test —ésa sale del escaneo— sino la prueba de que el escaneo ve algo
         * real, y de que lo ve en los tres sitios donde hay pantallas: el grafo
         * de cobranza, los `:feature:*` con destino propio y los `:core:*` que
         * `:app` compone a mano.
         */
        val PANTALLAS_MONTADAS = setOf(
            "ListaDeClientesScreen",
            "DetalleClienteScreen",
            "BitacoraScreen",
            "DetalleVentaScreen",
            "RegistrarAbonoScreen",
            "TicketDePagoScreen",
            "RegistrarVisitaScreen",
            "TicketDeVisitaScreen",
            "DescargaDelDictadoScreen",
            "ConfiguracionScreen",
            "CollectionReportScreen",
            "VersionBlockedScreen"
        )

        /** Dónde manda la regla: la arquitectura nueva. `:app` es legado. */
        val MODULOS_NUEVOS = listOf("core", "feature")

        /** `fun NombreScreen(` de nivel superior, con o sin modificadores. */
        val PANTALLA = Regex("""^(?:[a-z]+ )*fun\s+([A-Z][A-Za-z0-9]*Screen)\s*\(""")

        /**
         * Las tres formas de declarar algo de nivel superior que otro archivo
         * puede nombrar. La de `fun` acepta receptor de extensión
         * (`fun NavGraphBuilder.destinoDeBitacora(`), que es justamente como se
         * registran los destinos de este repo: sin eso, el recorrido se cortaba
         * en `:app` y TODA pantalla de `:feature:*` salía muerta.
         */
        val DECLARACIONES = listOf(
            Regex(
                """^(?:[a-z]+ )*fun\s+(?:<[^>]*>\s+)?""" +
                    """(?:[A-Za-z_][A-Za-z0-9_.<>,?\s]*?\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\("""
            ),
            Regex(
                """^(?:[a-z]+ )*(?:class|object|interface|enum class|annotation class|""" +
                    """typealias)\s+([A-Za-z_][A-Za-z0-9_]*)"""
            ),
            Regex("""^(?:[a-z]+ )*(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)""")
        )

        val IDENTIFICADOR = Regex("""[A-Za-z_][A-Za-z0-9_]*""")

        /**
         * El código sin comentarios ni KDoc, por la misma razón que en
         * [EscanerDeFuentes]: este plan documenta en el código los defectos que
         * mató, y un KDoc que NOMBRA una pantalla no es una puerta a ella.
         */
        fun codigoDe(archivo: File): String = archivo.readLines()
            .filterNot { linea ->
                val limpia = linea.trim()
                limpia.startsWith("//") || limpia.startsWith("*") || limpia.startsWith("/*")
            }
            .joinToString("\n")
    }
}
