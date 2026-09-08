package com.example.msp_app.workmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La política de encolado del camino del dinero: **`KEEP`, siempre**.
 *
 * ## Por qué este archivo existe
 *
 * La Task 6 borró el parámetro `replace` de la cadena y clavó
 * `ExistingWorkPolicy.KEEP` dentro de las cinco funciones de trabajo pendiente.
 * El arreglo fue correcto y **se llevó puesta la aserción**: los cinco tests que
 * afirmaban `KEEP` lo hacían sobre el booleano capturado, y ese booleano dejó de
 * existir. Sus nombres siguieron prometiendo lo que sus cuerpos ya no
 * comprobaban, y desde entonces **ningún test en ningún módulo afirmaba la
 * política real** donde ahora vive. El único control que quedó era de
 * compilación (`No parameter with name 'replace'`), que protege contra
 * reintroducir el parámetro y **no** contra escribir `REPLACE` a mano — el
 * cambio de una palabra, en el archivo donde vive la política de todo el cobro.
 *
 * ## Dos mitades, y la segunda es la que no envejece
 *
 * 1. [`la politica de encolado es KEEP en cada trabajo unico del camino del dinero`]
 *    lo afirma **por comportamiento**: encola dos veces bajo el mismo nombre
 *    único y exige que sobreviva el PRIMER trabajo. Con `REPLACE`, el primero se
 *    cancela y aparece otro id. No mira el código, mira lo que WorkManager hace.
 *
 * 2. [`ningun encolado unico del arbol usa una politica distinta de KEEP`] barre
 *    **todos los módulos del build** buscando los sitios de encolado, en vez de
 *    conocer una lista de funciones. Un encolado nuevo —en un módulo que todavía
 *    no existe— entra a la regla sin que nadie lo registre, y salir cuesta
 *    escribirlo en [POLITICAS_PERMITIDAS] con su razón.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class WorkEnqueuePolicyGuardTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setTaskExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    // ── Mitad 1: la política, por comportamiento ─────────────────────────────

    /**
     * Ninguna de estas llamadas abre la compuerta de red (`setAllConstraintsMet`
     * no se llama), así que el trabajo se queda `ENQUEUED` y la segunda llamada
     * se topa con uno vivo — que es la única situación en la que `KEEP` y
     * `REPLACE` se distinguen.
     */
    @Test
    fun `la politica de encolado es KEEP en cada trabajo unico del camino del dinero`() {
        val encolados = listOf(
            "sync_pending_payments_ABONO-1" to { enqueuePendingPaymentsWorker(context, "ABONO-1") },
            "sync_pending_visit_VISITA-1" to { enqueuePendingVisitsWorker(context, "VISITA-1") },
            "sync_pending_guarantee_GAR-1" to { enqueuePendingGuaranteesWorker(context, "GAR-1") },
            "sync_pending_guarantee_events" to { enqueuePendingGuaranteeEventsWorker(context) },
            "sync_pending_local_sale_VENTA-1" to {
                enqueuePendingLocalSalesWorker(context, "VENTA-1", "rosa.jimenez@example.com")
            },
            COBRANZA_RECONCILE_NOW_WORK to { enqueueCobranzaReconcileNowWorker(context) },
            VISITS_RECONCILE_NOW_WORK to { enqueueVisitsReconcileNowWorker(context) },
            COBRANZA_RECONCILE_PERIODIC_WORK to { enqueueCobranzaReconcilePeriodicWorker(context) },
            VISITS_RECONCILE_PERIODIC_WORK to { enqueueVisitsReconcilePeriodicWorker(context) },
            "sync_clientes" to { enqueueClienteSyncWorker(context) }
        )

        // Control positivo del propio recorrido: si la lista quedara vacía este
        // test saldría verde sin encolar nada. Diez es el número de funciones de
        // encolado único que `WorkManagerUtils` expone hoy; el barrido de la
        // mitad 2 es lo que impide que una nueva se quede sin cubrir.
        assertEquals("no se ejercitó ningún encolado", 10, encolados.size)

        encolados.forEach { (nombreUnico, encolar) ->
            encolar()
            val primero = infoViva(nombreUnico)
            assertTrue(
                "$nombreUnico no quedó encolado en la primera llamada, así que la segunda no " +
                    "distingue KEEP de REPLACE y este caso no probaría nada",
                primero != null
            )

            encolar()
            val despues = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(nombreUnico)
                .get()
                .filterNot { it.state == WorkInfo.State.CANCELLED }

            assertEquals(
                "$nombreUnico: tras encolar dos veces quedó más de un trabajo vivo. Con KEEP el " +
                    "segundo encolado se descarta.",
                1,
                despues.size
            )
            assertEquals(
                "$nombreUnico: el trabajo vivo cambió de id, o sea que el segundo encolado " +
                    "CANCELÓ al primero. Eso es ExistingWorkPolicy.REPLACE, y en el camino del " +
                    "dinero significa matar una subida en vuelo y arriesgar un envío duplicado.",
                primero!!.id,
                despues.single().id
            )
        }
    }

    private fun infoViva(nombreUnico: String): WorkInfo? = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(nombreUnico)
        .get()
        .firstOrNull { it.state != WorkInfo.State.CANCELLED }

    // ── Mitad 2: el barrido, por descubrimiento ──────────────────────────────

    /**
     * Barre el `src/main` de **todos** los módulos de `settings.gradle.kts` y
     * exige que ningún archivo que encola trabajo único mencione una política
     * distinta de `KEEP`, salvo las entradas escritas en [POLITICAS_PERMITIDAS]
     * con su razón.
     *
     * Es un barrido de texto, de la misma familia que `checkNoLegacyDateApi` y
     * `SaleIdSpacesCallSitesTest`: la política es un argumento de enum en un
     * `.kt`, y no hay forma de leerla por reflexión desde la firma. Lo que sí se
     * gana sobre una lista escrita a mano es que **el conjunto de archivos se
     * descubre**.
     */
    @Test
    fun `ningun encolado unico del arbol usa una politica distinta de KEEP`() {
        val encoladores = archivosQueEncolanTrabajoUnico()

        assertTrue(
            "el barrido no encontró NINGÚN archivo que encole trabajo único — sin eso este " +
                "test sale verde sin medir nada",
            encoladores.isNotEmpty()
        )
        assertTrue(
            "el barrido no vio WorkManagerUtils.kt, que es donde vive la política del camino " +
                "del dinero: el descubrimiento está roto",
            encoladores.keys.any { it.endsWith("workmanager/WorkManagerUtils.kt") }
        )

        val vistas = Exenciones()
        val violaciones = encoladores.flatMap { (ruta, texto) ->
            texto.lineSequence().mapIndexedNotNull { indice, linea ->
                val codigo = linea.trim()
                if (codigo.startsWith("*") || codigo.startsWith("//")) return@mapIndexedNotNull null
                val politica = POLITICAS_PROHIBIDAS.firstOrNull { codigo.contains(it) }
                    ?: return@mapIndexedNotNull null
                if (vistas.exento(ruta, codigo)) return@mapIndexedNotNull null
                "$ruta:${indice + 1}: `$politica`"
            }
        }.sorted()

        assertEquals(
            "Estos encoladores usan una política distinta de KEEP: $violaciones.\n" +
                "`REPLACE` cancela lo que ya esté corriendo bajo el mismo nombre único y no " +
                "rescata nada que KEEP no recupere solo: cuando el trabajo previo llega a un " +
                "estado terminal, KEEP encola igual. En el camino del dinero su único efecto " +
                "real es matar una subida en vuelo.\n" +
                "Si el caso es legítimo, agregá la línea exacta a `POLITICAS_PERMITIDAS` CON su " +
                "razón escrita.",
            emptyList<String>(),
            violaciones
        )
    }

    /**
     * El medio agujero que el barrido de constantes no ve: un **booleano** que
     * elige la política.
     *
     * `OfflineSyncManager.enqueue(..., replaceExisting: Boolean)` resuelve
     * `REPLACE` o `KEEP` según ese parámetro. La constante prohibida vive en el
     * encolador —y ahí está allowlistada con su razón—, pero **la decisión se
     * toma en el call site**, en un archivo que ni siquiera menciona
     * `enqueueUniqueWork`. O sea que un `replaceExisting = true` nuevo, en
     * cualquier módulo, entraba sin que nada chillara. Lo señaló la revisión de
     * la ronda 1, y tenía razón: sin esto, la razón escrita en la allowlist
     * ("su único llamador que lo pone en true es este") era una afirmación que
     * la compuerta no hacía cumplir.
     *
     * Los nombres de los selectores **se descubren**: se busca, dentro de los
     * archivos que encolan trabajo único, el identificador del `if` que elige
     * una política prohibida. No hay lista de nombres escrita a mano.
     */
    @Test
    fun `ningun call site pide una politica distinta de KEEP por un booleano`() {
        val selectores = selectoresDePolitica(archivosQueEncolanTrabajoUnico())

        assertTrue(
            "no se descubrió ningún selector booleano de política. Si el encolador dejó de " +
                "elegir la política con un booleano, borrá este test; si sigue haciéndolo, el " +
                "descubrimiento está roto y el verde de abajo no vale.",
            selectores.isNotEmpty()
        )

        val vistas = Exenciones()
        val violaciones = fuentesDeProduccion { true }.flatMap { (ruta, texto) ->
            texto.lines().mapIndexedNotNull { indice, linea ->
                val codigo = linea.trim()
                if (codigo.startsWith("*") || codigo.startsWith("//")) return@mapIndexedNotNull null
                val selector = selectores.firstOrNull {
                    Regex("""\b$it\s*=\s*true\b""").containsMatchIn(codigo)
                } ?: return@mapIndexedNotNull null
                if (vistas.exento(ruta, codigo)) return@mapIndexedNotNull null
                "$ruta:${indice + 1}: `$selector = true`"
            }
        }.sorted()

        assertEquals(
            "Estos call sites piden una política distinta de KEEP por booleano: $violaciones.\n" +
                "Es el mismo REPLACE del barrido de constantes, tomado un archivo más arriba. " +
                "Si el caso es legítimo, agregá la línea exacta a `POLITICAS_PERMITIDAS` CON su " +
                "razón escrita.",
            emptyList<String>(),
            violaciones
        )
    }

    /**
     * Cuenta las exenciones consumidas, para que una allowlist **por línea** no
     * se convierta en una allowlist por archivo.
     *
     * `POLITICAS_PERMITIDAS` no dice sólo QUÉ línea se exime: dice **cuántas
     * veces**. Sin el conteo, una segunda línea con el texto idéntico en el
     * mismo archivo quedaba exenta gratis — lo comprobé sembrando un segundo
     * `replaceExisting = true` en `LocalSaleSyncExtensions` y viendo la
     * compuerta verde. Con el conteo, la exención cubre la ocurrencia declarada
     * y ni una más.
     */
    private class Exenciones {
        private val consumidas = mutableMapOf<Pair<String, String>, Int>()

        fun exento(ruta: String, codigo: String): Boolean {
            val permitidas = POLITICAS_PERMITIDAS[ruta]?.get(codigo) ?: return false
            val clave = ruta to codigo
            val usadas = consumidas.getOrDefault(clave, 0)
            if (usadas >= permitidas) return false
            consumidas[clave] = usadas + 1
            return true
        }
    }

    /**
     * Los identificadores booleanos que, dentro de un archivo que encola trabajo
     * único, deciden una política prohibida. Se toma el `if (<ident>)` que abre
     * como mucho tres líneas antes de la constante.
     */
    private fun selectoresDePolitica(encoladores: Map<String, String>): Set<String> {
        val encontrados = mutableSetOf<String>()
        encoladores.values.forEach { texto ->
            val lineas = texto.lines()
            lineas.forEachIndexed { indice, linea ->
                val codigo = linea.trim()
                if (codigo.startsWith("*") || codigo.startsWith("//")) return@forEachIndexed
                if (POLITICAS_PROHIBIDAS.none { codigo.contains(it) }) return@forEachIndexed
                for (atras in indice downTo maxOf(0, indice - 3)) {
                    val condicion = CONDICION_IF.find(lineas[atras]) ?: continue
                    encontrados += condicion.groupValues[1]
                    break
                }
            }
        }
        return encontrados
    }

    /**
     * Todo archivo de producción, en cualquier módulo del build, que cumpla
     * [filtro]. El conjunto de módulos sale de `settings.gradle.kts` — el
     * archivo que los define — y no de una lista acá.
     */
    private fun fuentesDeProduccion(filtro: (String) -> Boolean): Map<String, String> {
        val raiz = raizDelRepoDeWorkManager()
        val rutas = INCLUDE_MODULO.findAll(File(raiz, "settings.gradle.kts").readText())
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
                    .filter { (_, texto) -> filtro(texto) }
                    .toList()
            }
        }.toMap()
    }

    private fun archivosQueEncolanTrabajoUnico(): Map<String, String> =
        fuentesDeProduccion { ENCOLA_UNICO.containsMatchIn(it) }

    private companion object {
        val INCLUDE_MODULO = Regex("""include\("(:[^"]+)"\)""")
        val ENCOLA_UNICO = Regex("""enqueueUnique(Periodic)?Work\(""")

        /** `if (replaceExisting) {` — el identificador que decide la política. */
        val CONDICION_IF = Regex("""\bif\s*\(\s*(\w+)\s*\)""")

        /**
         * Toda política que NO conserva el trabajo vivo. `UPDATE` entra porque
         * también pisa lo que ya está encolado (aunque sin cancelarlo), y
         * `APPEND*` porque encadena en vez de descartar.
         */
        val POLITICAS_PROHIBIDAS = listOf(
            "ExistingWorkPolicy.REPLACE",
            "ExistingWorkPolicy.APPEND",
            "ExistingPeriodicWorkPolicy.REPLACE",
            "ExistingPeriodicWorkPolicy.UPDATE"
        )

        /**
         * Las ÚNICAS líneas que pueden encolar con una política distinta de
         * `KEEP`, con la razón por la que el argumento del camino del dinero no
         * les aplica. Se permiten por **línea exacta**, no por archivo: una
         * segunda política distinta en el mismo archivo sigue fallando.
         */
        val POLITICAS_PERMITIDAS: Map<String, Map<String, Int>> = mapOf(
            // `:core:appgate` descarga el APK de actualización. Acá REPLACE es
            // la decisión correcta y está documentada en el propio scheduler: un
            // toque explícito del usuario debe pisar la descarga automática, y
            // si el paquete pendiente es OTRA versión, seguir con el viejo sería
            // dejar a la flota bajando un APK que ya no sirve. No hay dinero en
            // vuelo: lo que se cancela es una descarga reintentable.
            "core/appgate/src/main/kotlin/com/example/msp_app/core/appgate/download/UpdateDownloadScheduler.kt" to
                mapOf(
                    "enqueue(update, automatic = false, policy = ExistingWorkPolicy.REPLACE)" to 1,
                    "ExistingWorkPolicy.REPLACE" to 1
                ),
            // LEGACY `:app`, y es DEUDA REAL, no una excepción cómoda. El
            // `OfflineSyncManager` es el encolador anterior a la política del
            // plan y elige `REPLACE` o `KEEP` con su parámetro `replaceExisting`.
            //
            // La razón que estaba escrita acá era FALSA y la revisión de la ronda
            // 1 la encontró: decía que el único llamador con `true` era el re-sync
            // de una venta local, y había un segundo — `enqueueBatch`, que lo
            // clavaba en `true` para todo un lote. Estaba muerto (cero llamadores)
            // así que se borró, y la afirmación pasó a ser cierta. Y que siga
            // siéndolo ya no depende de que alguien relea este comentario: lo hace
            // cumplir `ningun call site pide una politica distinta de KEEP por un
            // booleano`.
            "app/src/main/java/com/example/msp_app/core/sync/OfflineSyncManager.kt" to mapOf(
                "ExistingWorkPolicy.REPLACE" to 1
            ),
            // El ÚNICO llamador vivo que pide REPLACE: re-sincroniza una venta
            // local ya encolada. Cambiarlo es un cambio de comportamiento en el
            // camino del dinero de ventas, fuera del alcance del Arreglo B —
            // anotado en vez de invisible.
            "app/src/main/java/com/example/msp_app/features/sales/sync/LocalSaleSyncExtensions.kt" to
                mapOf("replaceExisting = true" to 1)
        )

        fun raizDelRepoDeWorkManager(): File {
            var actual: File? = File(".").absoluteFile
            while (actual != null) {
                if (File(actual, "settings.gradle.kts").isFile) return actual
                actual = actual.parentFile
            }
            error("no se encontró settings.gradle.kts subiendo desde ${File(".").absolutePath}")
        }
    }
}
