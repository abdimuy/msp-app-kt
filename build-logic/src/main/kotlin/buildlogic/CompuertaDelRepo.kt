package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.Task

/**
 * La marca que dice **"esta tarea es una compuerta de este repo"**.
 *
 * ## Por qué existe (Arreglo B, ronda 4)
 *
 * `prePushCheck` tiene que saber cuáles de las ~350 tareas de un módulo son
 * compuertas nuestras y cuáles vienen de AGP, Kover, detekt o ktlint. Las tres
 * primeras rondas contestaron esa pregunta **leyendo el código fuente**: un
 * regex sobre los `tasks.register("…")` de tres rutas de archivo. Eso midió
 * *cómo está escrito el registro*, no *qué es la tarea*, y falló en las tres
 * direcciones posibles:
 *
 * - una compuerta con el nombre **en una constante** no entraba al gate, en
 *   verde y sin aviso;
 * - una compuerta registrada **desde un convention plugin con el nombre por
 *   variante** (`"check$sufijo"`) tampoco;
 * - y una línea de **comentario** que mencionara `tasks.register("lintDebug")`
 *   metía Android Lint en el pre-push de los 14 módulos Android.
 *
 * El diagnóstico del revisor: *mientras "¿es nuestra esta compuerta?" se
 * conteste leyendo el fuente en vez de mirando el objeto Gradle, va a haber
 * otra lista*. Esta interfaz es esa respuesta: la identidad de una compuerta es
 * **su tipo**, que es un hecho del modelo de objetos y no del texto.
 *
 * ## Por qué una marca de TIPO y no una propiedad
 *
 * Porque el tipo es lo **único** que Gradle sabe contestar **sin realizar la
 * tarea**: `tasks.withType(CompuertaDelRepo::class)` filtra por el tipo con el
 * que se registró, mirando el `TaskProvider` pendiente. Una marca guardada en
 * `extra`/`extensions` obligaría a realizar las ~5.000 tareas del build entero
 * para leerla — caro, y encima capaz de romperse con tareas que fallan al
 * realizarse. Es la misma razón por la que el descubrimiento de este repo
 * prefiere `tasks.names` a `tasks.forEach`.
 *
 * ## Por qué una INTERFAZ y no sólo una clase base
 *
 * Kotlin no tiene herencia múltiple de clases. Si la marca fuera únicamente
 * `CompuertaDelRepoTask`, una compuerta que necesite ser un `Exec`, un
 * `JavaExec` o un `SourceTask` **no podría llevarla**, y quien la escriba
 * volvería a inventar un mecanismo aparte — el borde del que salieron los tres
 * huecos anteriores. Con la marca como interfaz, cualquier tipo de tarea puede
 * declararla: `class MiCompuerta : Exec(), CompuertaDelRepo`.
 *
 * Para el caso común —una compuerta que sólo tiene un `doLast`— está
 * [CompuertaDelRepoTask], que además fija el grupo.
 *
 * ## Qué pasa si alguien se olvida de la marca
 *
 * No pasa desapercibido: `msp.compuertas` registra `verificarMarcaDeCompuertas`,
 * que corre dentro del propio `prePushCheck` y **falla** ante cualquier tarea
 * del grupo `verification` con la forma de una compuerta escrita a mano (tipo
 * público `DefaultTask`) que no lleve esta marca ni esté declarada, con su
 * razón, como tarea de plugin. Ver `CompuertasConventionPlugin`.
 */
interface CompuertaDelRepo : Task

/**
 * El caso común de [CompuertaDelRepo]: una compuerta propia sin tipo especial.
 *
 * Fija `group = "verification"` porque toda compuerta de este repo lo está —
 * pero ojo: **el grupo ya no es el criterio**. El criterio es la marca. El grupo
 * queda para que `./gradlew tasks` las siga listando donde corresponde, y para
 * que la red de `verificarMarcaDeCompuertas` (que busca justamente tareas de
 * `verification` sin marca) mire el mismo vecindario.
 */
abstract class CompuertaDelRepoTask : DefaultTask(), CompuertaDelRepo {
    init {
        group = "verification"
    }
}
