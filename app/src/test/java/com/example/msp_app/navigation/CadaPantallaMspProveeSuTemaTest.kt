package com.example.msp_app.navigation

import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
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
        // -1 por la raíz de prueba, y -N por los destinos que el grafo registra
        // pero que NO son pantallas Msp — ver [DESTINOS_SIN_TEMA_MSP]. Sin ese
        // segundo descuento, este test y el de arriba se contradicen: aquél
        // exige que toda entrada de PANTALLAS_DE_COBRANZA la vea el escaneo de
        // pantallas con `MspTheme`, y éste exigiría meter ahí una que no lo usa.
        val destinos = nav.graph.count() - 1 - DESTINOS_SIN_TEMA_MSP.size
        assertEquals(
            "destinosDeCobranza registra $destinos destinos con tema y el control positivo " +
                "vigila ${PANTALLAS_DE_COBRANZA.size} pantallas: agregá la pantalla nueva a " +
                "PANTALLAS_DE_COBRANZA, o a DESTINOS_SIN_TEMA_MSP si es una pantalla legada",
            PANTALLAS_DE_COBRANZA.size,
            destinos
        )
    }

    // -----------------------------------------------------------------------

    /**
     * Las pantallas que este gate vigila.
     *
     * ## Por qué el módulo `app` se mira con otra regla
     *
     * Para `:core:*` y `:feature:*` basta con que el **módulo** use `MspTheme`:
     * son módulos Msp enteros, y ahí toda pantalla tiene que proveer su tema.
     *
     * `:app` es legado (Ruling I) y tiene veintiséis pantallas que viven en
     * `MspappTheme` —el Material de siempre, un sistema de composición distinto—
     * y que no leen un solo token Msp. El filtro por módulo las arrastraba a
     * todas en cuanto UNA pantalla Msp aterrizaba en `:app`, y eso pasó:
     * `UbicacionDelClienteScreen` —el mapa grande, que vive ahí porque
     * `play-services-maps` se declara ahí— puso este test en rojo con 26
     * pantallas que no tienen nada que ver.
     *
     * Marcarlas como excepción habría sido una lista de nombres a mano. La regla
     * correcta es más simple y más fina: en `:app`, una pantalla entra al gate
     * **cuando ella misma usa el tema Msp**, leyéndolo o montándolo. Una pantalla
     * legada que no lo toca no es una pantalla Msp, y la número veintisiete
     * tampoco lo será. La que sí lo sea entra sola el día que se escribe.
     */
    private fun pantallasDeModulosMsp(): List<Declaracion> =
        escaner.archivosPorModulo.flatMap { (modulo, archivos) ->
            val moduloUsaElTema = archivos.any { LEE_TEMA.containsMatchIn(codigoDe(it)) }
            if (!moduloUsaElTema) return@flatMap emptyList()
            archivos
                .flatMap { declaracionesDe(codigoDe(it)) }
                .filter { ES_PANTALLA.matches(it.nombre) }
                .filter { modulo != MODULO_LEGADO || usaElTema(it) }
        }

    /** La pantalla misma lee `MspTheme.algo` o monta `MspTheme { }`. */
    private fun usaElTema(pantalla: Declaracion): Boolean =
        LEE_TEMA.containsMatchIn(pantalla.texto) || INVOCA_TEMA.containsMatchIn(pantalla.texto)

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
            .flatMap { declaracionesDe(codigoDe(it)) }
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

        /** El módulo legado (Ruling I), donde el gate se aplica por pantalla. */
        const val MODULO_LEGADO = "app"

        /**
         * Control positivo: las nueve que hoy registra el grafo. No es la lista
         * que gobierna el test —esa sale del escaneo— sino la prueba de que el
         * escaneo ve algo real.
         *
         * La última no es de cobranza: es la descarga opcional del dictado, que
         * `destinosDeDescargas` registra dentro de [destinosDeCobranza] para que
         * las cuatro redes de `:app` la barran. Ver su KDoc.
         *
         * `ForgivenessScreen` —la condonación— está registrada dentro de
         * [destinosDeCobranza] por el mismo motivo que la descarga del dictado,
         * pero **NO va en esta lista**: ésta es el control positivo del escaneo
         * de pantallas que proveen `MspTheme`, y esa pantalla vive en `:app`
         * (módulo legado) y monta `NewForgivenessDialog`
         * con Material3 puro y no lee ni provee `MspTheme`.
         */
        /**
         * Los destinos que [destinosDeCobranza] registra y que **no** son
         * pantallas Msp, así que no entran en [PANTALLAS_DE_COBRANZA].
         *
         * Hoy sólo la **condonación**: vive en `:app` (módulo legado), monta
         * `NewForgivenessDialog` con Material3 puro y no lee ni provee
         * `MspTheme`. Se registra dentro de [destinosDeCobranza] por el mismo
         * motivo que la descarga del dictado —para que las cuatro redes de
         * `:app` la barran—, no porque sea arquitectura nueva.
         *
         * Esta lista es una **excepción declarada**, no un agujero: crece sólo
         * cuando alguien registra a propósito un destino legado en este grafo,
         * y el mensaje del test de arriba dice cuándo toca.
         */
        val DESTINOS_SIN_TEMA_MSP = setOf("forgiveness/{saleId}")

        val PANTALLAS_DE_COBRANZA = setOf(
            "ListaDeClientesScreen",
            "DetalleClienteScreen",
            "BitacoraScreen",
            "DetalleVentaScreen",
            "RegistrarAbonoScreen",
            "TicketDePagoScreen",
            "RegistrarVisitaScreen",
            "TicketDeVisitaScreen",
            "DescargaDelDictadoScreen",
            "UbicacionDelClienteScreen"
        )

        /** `MspTheme.colors` y hermanos: leer el tema, no proveerlo. */
        val LEE_TEMA = Regex("""(?<![A-Za-z0-9_])MspTheme\.""")
    }
}
