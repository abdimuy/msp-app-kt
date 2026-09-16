package com.example.msp_app.navigation

import java.io.File

/**
 * **Cómo se prueba que algo fue retirado.**
 *
 * Una ausencia no es un hallazgo hasta probar que la búsqueda habría encontrado
 * la cosa (regla de control positivo, `global-constraints.md`). Este escáner es
 * la mitad que busca; la otra mitad —el control positivo— la pone cada test,
 * buscando con el MISMO método un símbolo hermano que sí existe.
 *
 * ## Por qué recorre todos los módulos y no solo `:app`
 *
 * La primera versión de este escáner (Task 21, retiro de `NewVisitDialog`) miraba
 * únicamente `:app/src/main/java`. La revisión marcó el hueco: **un sobreviviente
 * en `:feature:*` o `:core:*` no lo habría atrapado**, y el test habría dado
 * verde sobre un retiro incompleto — o sea, exactamente la mentira que el control
 * positivo existe para impedir. Ahora recorre los tres conjuntos de fuentes de
 * producción del repo, así que el cero que reporta es un cero del repo entero.
 *
 * `src/test` NO se recorre a propósito: un test que nombre lo retirado es o este
 * archivo o un test que también hay que borrar, y compilar ya lo detecta.
 */
internal class EscanerDeFuentes {

    private companion object {
        /** `include(":core:common")` de `settings.gradle.kts`. */
        val INCLUDE = Regex("""include\("(:[^"]+)"\)""")
    }

    /**
     * La raíz del repo. El directorio de trabajo de un test de Gradle es el del
     * módulo (`<repo>/app`), y el `?:` cubre correr la clase desde la raíz.
     */
    private val raiz: File =
        if (File("src/main/java").isDirectory) File("..") else File(".")

    /**
     * Las fuentes de producción de **todos** los módulos del build.
     *
     * La versión anterior recorría los grupos `feature` y `core` por glob, y eso
     * era mejor que una lista de módulos pero seguía siendo una lista: la de los
     * **grupos**. Dejaba fuera `build-tools/detekt-rules` —que también tiene
     * código de producción, las reglas de detekt— y habría dejado fuera
     * cualquier grupo nuevo. El Arreglo B lo encontró contando listas escritas a
     * mano: era la décima.
     *
     * Ahora los módulos salen de `settings.gradle.kts`, el archivo que los
     * define, y de cada uno se toman las dos convenciones de `src/main` que el
     * repo usa (`java/` en `:app`, `kotlin/` en el resto). Un módulo nuevo —o un
     * grupo nuevo— entra el día que entra al build.
     */
    private val raicesPorModulo: List<Pair<String, File>> = buildList {
        val settings = File(raiz, "settings.gradle.kts").readText()
        val modulos = INCLUDE.findAll(settings)
            .map { it.groupValues[1].trim(':').replace(':', '/') }
            .toList()
        check(modulos.isNotEmpty()) {
            "no se leyó ningún include(...) de settings.gradle.kts: el escáner no miraría nada"
        }
        modulos.sorted().forEach { modulo ->
            add(modulo to File(raiz, "$modulo/src/main/java"))
            add(modulo to File(raiz, "$modulo/src/main/kotlin"))
        }
    }.filter { (_, dir) -> dir.isDirectory }

    val raices: List<File> = raicesPorModulo.map { (_, dir) -> dir }

    val archivos: List<File> by lazy {
        raices.flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" } }
    }

    /**
     * Los archivos de producción **agrupados por el módulo que los contiene**
     * (`app`, `core/speech`, `feature/pagos`…), con el nombre tal como lo
     * escribe `settings.gradle.kts` pero con `/` en vez de `:`.
     *
     * Lo necesita [CadaPantallaSeAlcanzaDesdeElGrafoTest], que arranca su
     * recorrido en `:app` —la raíz del grafo de navegación— y exige el
     * resultado sólo sobre `:core:*`/`:feature:*`, porque `:app` es legado
     * (Ruling I) y tiene pantallas muertas de antes.
     */
    val archivosPorModulo: Map<String, List<File>> by lazy {
        raicesPorModulo
            .groupBy({ (modulo, _) -> modulo }) { (_, dir) ->
                dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            }
            .mapValues { (_, listas) -> listas.flatten() }
    }

    /**
     * Los archivos cuyo **código** menciona [simbolo]. Las líneas de comentario
     * y de KDoc se descartan: este plan documenta en el código los defectos que
     * mató, y un KDoc que explica por qué un diálogo se fue no es una llamada a
     * ese diálogo. Contarlo como referencia haría imposible probar la ausencia
     * sin borrar la explicación, que es justo lo contrario de lo que se quiere.
     *
     * La coincidencia es de **palabra completa**: buscar `SalesScreen` a secas
     * encontraría `UnifiedSalesScreen`, que es otra pantalla y que el brief
     * ordena no tocar — el test habría fallado por la razón equivocada, o peor,
     * habría pasado por ella.
     */
    fun archivosQueMencionan(simbolo: String): List<String> {
        val patron = Regex("(?<![A-Za-z0-9_])" + Regex.escape(simbolo) + "(?![A-Za-z0-9_])")
        return archivos.filter { archivo ->
            archivo.readText().lineSequence().any { linea ->
                val limpia = linea.trim()
                val esComentario = limpia.startsWith("//") ||
                    limpia.startsWith("*") ||
                    limpia.startsWith("/*")
                !esComentario && patron.containsMatchIn(limpia)
            }
        }.map { it.path }
    }

    /** ¿Existe el archivo [rutaRelativa], contada desde la raíz del repo? */
    fun existe(rutaRelativa: String): Boolean = File(raiz, rutaRelativa).exists()
}
