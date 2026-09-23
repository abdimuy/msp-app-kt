package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * `msp.compuertas` — publica la marca [CompuertaDelRepo] y la red que la vigila.
 *
 * Se aplica **a la raíz**, y con eso alcanza: el `plugins {}` de la raíz mete el
 * jar de `build-logic` en el classpath del script raíz y, por herencia de scope,
 * en el de todos los scripts de módulo. O sea que cualquier `build.gradle.kts`
 * de este build puede registrar una compuerta marcada sin aplicar nada extra, y
 * los convention plugins —que ya viven en este jar— también.
 *
 * Su único comportamiento es registrar [NOMBRE_DE_LA_RED], que es la respuesta a
 * la pregunta *"¿y si alguien se olvida de marcarla?"*.
 *
 * ## Lo que esta red NO atrapa — límite conocido, declarado (ronda 5)
 *
 * Mira **sólo** tareas cuyo grupo sea `verification`. Así que una compuerta que
 * se registre **sin la marca y sin grupo** —`tasks.register("checkAlgo") {
 * doLast { … } }`— es invisible para esta red **y** para el descubrimiento: no
 * entra al gate, sale verde y nadie avisa. Es un escape real; queda escrito acá
 * en vez de que lo encuentre alguien dentro de seis meses.
 *
 * Por qué el filtro sigue siendo ése: de las 5.566 tareas de este build,
 * **4.220 no declaran grupo** (271 de ellas con tipo público `DefaultTask`).
 * Mirarlas todas exigiría una allowlist enorme de andamiaje de AGP/KGP, y una
 * red con cientos de excepciones escritas es ruido, no red. El grupo es la señal
 * más barata que separa "esto pretende ser una verificación" del andamiaje.
 *
 * Lo que **no** se puede decir de esta red —y la ronda 4 lo dijo— es que su
 * error sólo pueda ser "de más, nunca de menos". Puede ser de menos, y el
 * párrafo de arriba describe exactamente cómo.
 */
class CompuertasConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val raiz = target.rootProject
        target.tasks.register<CompuertaDelRepoTask>(NOMBRE_DE_LA_RED) {
            description = "Falla si una tarea con forma de compuerta escrita a mano (tipo " +
                "público DefaultTask, grupo verification) no lleva la marca CompuertaDelRepo."

            doLast {
                // Una tarea cuyo TIPO PÚBLICO es exactamente `DefaultTask` no
                // puede llevar la marca —`CompuertaDelRepo` es un tipo distinto—,
                // así que toda candidata es, por construcción, una compuerta sin
                // marcar… o una tarea de plugin con la misma forma. Distinguirlas
                // es lo único que hace falta decidir, y para eso está
                // [NO_SON_COMPUERTAS].
                val candidatas = mutableListOf<Pair<String, String>>()
                val nombresRegistrados = mutableSetOf<String>()
                val noRealizables = mutableListOf<String>()

                raiz.allprojects.forEach { proyecto ->
                    proyecto.tasks.collectionSchema.elements.forEach { nombresRegistrados += it.name }
                    // `collectionSchema` da nombre + tipo público de cada tarea
                    // registrada **sin realizarla**. Se filtra primero por tipo
                    // —que es lo barato— y sólo se realizan las poquísimas cuyo
                    // tipo es exactamente `DefaultTask`, que es la forma que
                    // tiene una compuerta escrita a mano en un script.
                    proyecto.tasks.collectionSchema.elements
                        .filter { it.publicType.concreteClass == DefaultTask::class.java }
                        .forEach { elemento ->
                            val grupo = try {
                                proyecto.tasks.named(elemento.name).get().group
                            } catch (error: Exception) {
                                noRealizables += "${proyecto.path}:${elemento.name} → " +
                                    "${error::class.simpleName}: ${error.message}"
                                null
                            }
                            if (grupo == GRUPO_DE_VERIFICACION) {
                                candidatas += proyecto.path to elemento.name
                            }
                        }
                }

                // Una tarea que no se puede realizar es una que no se pudo
                // clasificar. Tragarse ese error dejaría un punto ciego del
                // tamaño exacto del defecto que esta red persigue.
                if (noRealizables.isNotEmpty()) {
                    throw GradleException(
                        "$NOMBRE_DE_LA_RED: no pude realizar estas tareas, así que no sé si " +
                            "son compuertas sin marca: ${noRealizables.sorted()}."
                    )
                }

                val sinMarca = candidatas
                    .filterNot { (_, nombre) -> nombre in NO_SON_COMPUERTAS }
                    .map { (ruta, nombre) -> if (ruta == ":") ":$nombre" else "$ruta:$nombre" }
                    .sorted()
                if (sinMarca.isNotEmpty()) {
                    throw GradleException(
                        "$NOMBRE_DE_LA_RED: estas tareas están en el grupo " +
                            "`$GRUPO_DE_VERIFICACION` y tienen la forma de una compuerta escrita " +
                            "a mano, pero NO llevan la marca `CompuertaDelRepo`, así que " +
                            "`prePushCheck` no las corre: $sinMarca.\n" +
                            "Si es una compuerta de este repo, registrala como " +
                            "`tasks.register<CompuertaDelRepoTask>(…)` (o hacé que su tipo " +
                            "implemente `CompuertaDelRepo`). Si es de un plugin, escribila en " +
                            "`NO_SON_COMPUERTAS` CON su razón."
                    )
                }

                // Control positivo de esta misma red: un "no encontré nada malo"
                // que no se distingue de "no miré nada" es un verde vacío, que
                // es el defecto que este arreglo entero persigue. `check` existe
                // en los 17 proyectos con script, así que un barrido sano SIEMPRE
                // trae candidatas; cero significa que el recorrido de proyectos o
                // el filtro por tipo se rompió.
                if (candidatas.isEmpty()) {
                    throw GradleException(
                        "$NOMBRE_DE_LA_RED: el barrido no encontró NINGUNA tarea con forma de " +
                            "compuerta escrita a mano en ${raiz.allprojects.size} proyectos. " +
                            "Eso no es que esté todo bien: es que no miró."
                    )
                }

                // Control de entrada muerta. Se mide contra los nombres
                // REGISTRADOS (el esquema completo del build), no contra las
                // candidatas de hoy: el grupo de una tarea de plugin depende de
                // cuánto se haya configurado en esta corrida —`ktlintCheck`, por
                // ejemplo, sólo declara su grupo cuando el gate ya realizó sus
                // tareas por source set—, y una exención no puede morir por eso.
                val exencionesMuertas = NO_SON_COMPUERTAS.keys
                    .filterNot { it in nombresRegistrados }
                    .sorted()
                if (exencionesMuertas.isNotEmpty()) {
                    throw GradleException(
                        "$NOMBRE_DE_LA_RED: estas entradas de `NO_SON_COMPUERTAS` no describen " +
                            "ninguna tarea registrada en este build: $exencionesMuertas.\nEl " +
                            "plugin que las registraba ya no está, así que sobran: borralas. Una " +
                            "excusa escrita para algo que no pasa es ruido que tapa a la próxima."
                    )
                }

                logger.lifecycle(
                    "$NOMBRE_DE_LA_RED: ${raiz.allprojects.size} proyectos, " +
                        "${candidatas.size} tareas con forma de compuerta escrita a mano en " +
                        "`$GRUPO_DE_VERIFICACION`, todas marcadas o exentas con razón."
                )
            }
        }
    }

    private companion object {
        const val NOMBRE_DE_LA_RED = "verificarMarcaDeCompuertas"
        const val GRUPO_DE_VERIFICACION = "verification"

        /**
         * Tareas que tienen la forma de una compuerta escrita a mano —tipo
         * público `DefaultTask` y grupo `verification`— pero **no** son
         * compuertas de este repo, cada una con su razón.
         *
         * Es una allowlist, no una lista de alcance: no decide qué se verifica
         * (eso lo decide la marca), sólo silencia el ruido de la red. Y tiene
         * control de entrada muerta: una exención que ya no describe nada hace
         * fallar la tarea.
         */
        val NO_SON_COMPUERTAS: Map<String, String> = mapOf(
            "check" to
                "tarea de ciclo de vida del plugin `base` de Gradle: no verifica nada por sí " +
                "misma, agrega. El gate no la usa porque arrastraría lint y pruebas de " +
                "dispositivo al pre-push.",
            "detektMain" to
                "la registra el plugin de detekt por source set. El gate ya corre su agregado " +
                "`detekt` por familia de tareas.",
            "detektTest" to
                "la registra el plugin de detekt por source set. El gate ya corre su agregado " +
                "`detekt` por familia de tareas.",
            "detektBaselineMain" to
                "la registra el plugin de detekt: GENERA un baseline de supresión, no verifica. " +
                "Correrla en pre-push escribiría el archivo que ablanda a detekt.",
            "detektBaselineTest" to
                "la registra el plugin de detekt: GENERA un baseline de supresión, no verifica. " +
                "Correrla en pre-push escribiría el archivo que ablanda a detekt.",
            "ktlintCheck" to
                "la registra el plugin de ktlint como agregado de sus tareas por source set: no " +
                "es de este repo. El gate ya la corre en los 17 proyectos por familia de tareas, " +
                "y está en la base congelada. (Sólo declara su grupo cuando esas tareas por " +
                "source set ya fueron realizadas, así que aparece como candidata dentro del gate " +
                "y no en una corrida suelta.)",
            "prePushCheck" to
                "es la compuerta agregada: marcarla la haría depender de sí misma. Su alcance lo " +
                "vigila la base congelada `gradle/prepush-baseline.txt`."
        )
    }
}
