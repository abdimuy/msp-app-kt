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

    /**
     * La raíz del repo. El directorio de trabajo de un test de Gradle es el del
     * módulo (`<repo>/app`), y el `?:` cubre correr la clase desde la raíz.
     */
    private val raiz: File =
        if (File("src/main/java").isDirectory) File("..") else File(".")

    /**
     * Las fuentes de producción: `:app` en `java/`, y cada `:feature:*` /
     * `:core:*` en `kotlin/`. Se listan por glob y no a mano para que un módulo
     * nuevo entre solo — una lista escrita a mano envejece hasta volver a dejar
     * un hueco, que es el defecto que esta clase vino a cerrar.
     */
    val raices: List<File> = buildList {
        add(File(raiz, "app/src/main/java"))
        listOf("feature", "core").forEach { grupo ->
            File(raiz, grupo).listFiles()?.sortedBy { it.name }?.forEach { modulo ->
                add(File(modulo, "src/main/kotlin"))
            }
        }
    }.filter { it.isDirectory }

    val archivos: List<File> by lazy {
        raices.flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" } }
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
