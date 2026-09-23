package com.example.msp_app.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import java.io.File
import java.lang.reflect.Method
import java.util.jar.JarFile
import javax.inject.Inject
import javax.inject.Scope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `include(":core:common")` de `settings.gradle.kts`. */
private val INCLUDE = Regex("""include\("(:[^"]+)"\)""")

/** `@Module` como anotación real, no la palabra suelta dentro de un KDoc. */
private val ANOTACION_MODULE = Regex("""(?m)^\s*@Module\b""")

/**
 * Declaraciones de tipo de un archivo. Se toman todas y se cruzan por nombre
 * simple: un archivo con `@Module` puede declarar además tipos auxiliares, y un
 * nombre de más solo puede hacer el cruce MÁS estricto, nunca más laxo.
 */
private val DECLARACION =
    Regex(
        """(?m)^\s*(?:internal\s+|private\s+|public\s+|abstract\s+)*(?:object|class|interface)\s+(\w+)"""
    )

/** Sube desde el directorio de trabajo hasta el que tiene `settings.gradle.kts`. */
private fun raizDelRepo(): File {
    var actual: File? = File(".").absoluteFile
    while (actual != null) {
        if (File(actual, "settings.gradle.kts").isFile) return actual
        actual = actual.parentFile
    }
    error("no se encontró settings.gradle.kts subiendo desde ${File(".").absolutePath}")
}

/**
 * Guarda de regresión del kill-switch de baseURL (money-path).
 *
 * La reactividad de la baseURL v1 (override remoto de Firestore en release) vive
 * en `ApiProvider`, que reconstruye su `Retrofit` con la URL vigente. Ese rebuild
 * solo alcanza a un consumidor si nada **con scope** sostiene el proxy: con
 * `@Singleton`, Hilt memoiza el objeto de la primera `ApiProvider.create(...)`
 * para toda la vida del proceso y el flip deja de alcanzar a las inyecciones.
 *
 * ## Arreglo B — el default se invirtió dos veces, y la segunda es esta
 *
 * Antes del Task 10 la guarda era una lista de métodos escritos a mano; su único
 * modo de falla era el olvido. El Task 10 la volvió un barrido… **de dos clases
 * escritas a mano** (`NetworkModule`, `NetworkConfigModule`). O sea: el olvido
 * salió por la puerta de los métodos y volvió a entrar por la de los módulos.
 * Los `@Module` que la rama `feat/pagos-y-visitas` agregó —tres de ellos en ESTE
 * MISMO paquete— nunca fueron mirados, `@Binds` era invisible entero, y un
 * `@Singleton` a nivel de clase tampoco se veía.
 *
 * Ahora **nada se escribe a mano**:
 *
 *  - **Los portadores de bindings se descubren** recorriendo el classpath de
 *    prueba (que es el grafo de dependencias de Gradle) y quedándose con lo que
 *    lleva `@dagger.Module` **o declara un `@Provides`/`@Binds`**. Lo segundo no
 *    es redundante: los `@Provides` de un `companion object` compilan a una
 *    clase `X$Companion` que **no** lleva `@Module`, y filtrar solo por la
 *    anotación los dejaba fuera — lo descubrió el control positivo transitivo,
 *    no el diseño. Un módulo nuevo entra el día que compila.
 *  - **Qué es "un servicio de red" también se descubre**: es toda interfaz con al
 *    menos un método anotado con `retrofit2.http.*`. Una API nueva entra sola,
 *    sin que nadie la registre.
 *  - **`@Binds` cuenta**, mirando el tipo de su parámetro (la implementación) y
 *    no solo el de retorno.
 *  - **El scope se descubre** por meta-anotación `@javax.inject.Scope`, así que
 *    `@ActivityRetainedScoped` y compañía cuentan igual que `@Singleton`.
 *  - **La tenencia es transitiva**: una clase scopeada que inyecta una clase que
 *    inyecta una API congela el proxy exactamente igual.
 *
 * Salir de la guarda cuesta escribir una entrada en [scopePermitido] **con su
 * razón**. El olvido ya no es una salida.
 *
 * ## Por qué la allowlist es real y no un escape
 *
 * `@Singleton` SÍ es legítimo para lo que no sostiene un servicio de red — la
 * regla nunca fue "nada con scope". Por eso la guarda solo mira lo que
 * (transitivamente) sostiene un proxy de Retrofit, y por eso
 * [`la allowlist no acumula entradas muertas`] exige que cada entrada siga
 * correspondiendo a algo que hoy existe y hoy está scopeado.
 */
class NetworkKillSwitchGuardTest {

    /**
     * Los ÚNICOS bindings scopeados que pueden sostener (directa o
     * transitivamente) un servicio de Retrofit, cada uno con la razón por la que
     * el kill-switch no les aplica. Agregar una entrada acá es una decisión, no
     * un trámite: si lo que se exime sostiene un servicio de
     * `ApiProvider.create(...)`, la entrada es un agujero, no una excepción.
     *
     * **Vacía hoy**, y esa es la medición que importa: al pasar de 2 módulos a
     * todos los del build no apareció un solo binding que necesitara la excusa.
     */
    private val scopePermitido: Map<String, String> = emptyMap()

    // ── Los dos hallazgos ────────────────────────────────────────────────────

    @Test
    fun `ningun binding de Hilt scopeado sostiene un servicio de red`() {
        val grafo = Grafo.descubrir()

        val infractores = grafo.modulos
            .flatMap { module -> module.bindings().map { module to it } }
            .filter { (_, metodo) -> metodo.scopes().isNotEmpty() }
            .filter { (_, metodo) ->
                metodo.tiposSostenidos().any {
                    grafo.sostieneServicioDeRed(
                        it
                    )
                }
            }
            .map { (module, metodo) -> "${module.nombreLegible()}.${metodo.name}" }
            .filterNot { it in scopePermitido }
            .sorted()

        assertEquals(
            "Estos bindings de Hilt tienen scope Y entregan (directa o transitivamente) un " +
                "servicio de Retrofit: $infractores.\n" +
                "Hilt memoizaría el proxy de la primera ApiProvider.create(...) para toda la " +
                "vida del proceso y el kill-switch de baseURL por Firestore dejaría de alcanzar " +
                "a los consumidores inyectados tras el primer flip: quita el scope.\n" +
                "Si de verdad no sostiene ningún Retrofit, agrégalo a `scopePermitido` CON la " +
                "razón escrita.",
            emptyList<String>(),
            infractores
        )
    }

    /**
     * El medio agujero que un barrido por `@Provides` no ve: una clase
     * `@Singleton` **a nivel de clase** que inyecta una API. Hilt la memoiza
     * igual, y ningún `@Provides` la nombra.
     *
     * Es el caso concreto que la revisión final señaló: una palabra encima de
     * `V2VisitCustodyRegistry` congelaba el proxy v2 y nada se ponía rojo.
     */
    @Test
    fun `ninguna clase inyectable scopeada sostiene un servicio de red`() {
        val grafo = Grafo.descubrir()

        val infractores = grafo.inyectables
            .filter { it.scopes().isNotEmpty() }
            .filter { grafo.sostieneServicioDeRed(it) }
            .map { it.simpleName }
            .filterNot { it in scopePermitido }
            .sorted()

        assertEquals(
            "Estas clases con `@Inject constructor` llevan scope Y sostienen (directa o " +
                "transitivamente) un servicio de Retrofit: $infractores.\n" +
                "Es el mismo congelamiento del kill-switch que el caso de `@Provides`, solo que " +
                "escrito encima de la clase: quita el scope, o justifícalo en `scopePermitido`.",
            emptyList<String>(),
            infractores
        )
    }

    // ── Controles positivos del propio barrido ───────────────────────────────

    /**
     * **Control positivo del descubrimiento, y es la parte que esta rama pagó
     * caro.**
     *
     * Los dos tests de arriba afirman una ausencia ("ningún infractor"). Una
     * ausencia no vale nada hasta probar que el método habría encontrado la
     * cosa: con cero módulos o cero servicios de red descubiertos saldrían
     * verdes sin medir nada — que es exactamente el defecto que el Arreglo B
     * vino a cerrar.
     *
     * Y cruza el classpath contra el ÁRBOL DE FUENTES: cada archivo con
     * `@Module` bajo el `src/main` de cualquier módulo de `settings.gradle.kts`
     * tiene que aparecer en el barrido. Si un módulo nuevo no llega al classpath
     * de prueba de `:app`, la guarda no lo ve — y eso tiene que ser ruidoso.
     */
    @Test
    fun `el descubrimiento ve todos los modulos Hilt del arbol de fuentes`() {
        val grafo = Grafo.descubrir()

        assertTrue(
            "el barrido no encontró NINGÚN servicio de Retrofit — sin eso los dos tests de " +
                "arriba salen verdes midiendo nada",
            grafo.serviciosDeRed.isNotEmpty()
        )
        assertTrue(
            "el barrido no encontró NINGÚN @Module de Hilt — no midió nada",
            grafo.modulos.isNotEmpty()
        )

        val enFuentes = modulosEnElArbolDeFuentes()
        assertTrue(
            "no se leyó el árbol de fuentes: cero archivos con @Module bajo los src/main de " +
                "settings.gradle.kts, así que el cruce de abajo no probaría nada",
            enFuentes.isNotEmpty()
        )

        val descubiertos = grafo.modulos.map { it.simpleName }.toSet()
        val invisibles = (enFuentes - descubiertos).sorted()

        assertEquals(
            "Estos @Module existen en el árbol de fuentes pero NO llegaron al classpath de " +
                "prueba de :app, así que esta guarda no los mira: $invisibles.\n" +
                "Un módulo que la guarda no ve es un módulo sin guarda: agregá la dependencia de " +
                "Gradle que falta, o el barrido vuelve a ser una lista escrita a mano con otro " +
                "nombre.",
            emptyList<String>(),
            invisibles
        )
    }

    @Test
    fun `la allowlist no acumula entradas muertas`() {
        val grafo = Grafo.descubrir()

        val bindingsScopeados = grafo.modulos.flatMap { module ->
            module.bindings()
                .filter { it.scopes().isNotEmpty() }
                .map { "${module.nombreLegible()}.${it.name}" }
        }
        val clasesScopeadas = grafo.inyectables
            .filter { it.scopes().isNotEmpty() }
            .map { it.simpleName }

        val muertas = (scopePermitido.keys - (bindingsScopeados + clasesScopeadas).toSet()).sorted()

        assertEquals(
            "Estas entradas de `scopePermitido` ya no corresponden a un binding ni a una clase " +
                "con scope que exista hoy: $muertas. Bórralas — una excusa escrita para algo que " +
                "ya no pasa es como una allowlist termina cubriendo, años después, un caso que " +
                "nadie revisó.",
            emptyList<String>(),
            muertas
        )
    }

    // ── Mecánica ─────────────────────────────────────────────────────────────

    /**
     * `VisitsReconcileModule$Companion` se llama `Companion` a secas por
     * `simpleName`, que en un mensaje de error no dice nada. Se antepone la
     * clase que lo contiene.
     */
    private fun Class<*>.nombreLegible(): String =
        if (simpleName == "Companion" && enclosingClass != null) {
            "${enclosingClass.simpleName}.Companion"
        } else {
            simpleName
        }

    private fun Class<*>.bindings(): List<Method> = declaredMethods
        .filterNot { it.isSynthetic }
        .filter {
            it.isAnnotationPresent(Provides::class.java) || it.isAnnotationPresent(Binds::class.java)
        }

    /**
     * Los tipos que la instancia scopeada acaba sosteniendo.
     *
     * Un `@Binds` liga la IMPLEMENTACIÓN (su parámetro) al puerto (su retorno);
     * lo que Hilt memoiza es la implementación, así que es su tipo el que hay
     * que mirar — mirar solo el retorno es justo lo que dejaba `@Binds`
     * invisible.
     *
     * Un `@Provides` sostiene tanto lo que devuelve como **lo que recibe**: el
     * cuerpo del método es opaco a la reflexión, pero cada parámetro es algo que
     * el objeto construido puede guardar. `@Singleton` sobre un `@Provides` que
     * recibe un puerto de red es el mismo congelamiento aunque el tipo de
     * retorno no diga "Api" por ningún lado.
     */
    private fun Method.tiposSostenidos(): List<Class<*>> =
        if (isAnnotationPresent(Binds::class.java) && parameterTypes.size == 1) {
            listOf(parameterTypes[0])
        } else {
            listOf(returnType) + parameterTypes
        }

    private fun Method.scopes(): List<String> = scopesDe(
        annotations.map { it.annotationClass.java }
    )

    private fun Class<*>.scopes(): List<String> = scopesDe(
        annotations.map { it.annotationClass.java }
    )

    /**
     * El scope se detecta por **meta-anotación**: en Dagger/Hilt un scope es
     * cualquier anotación anotada con `@javax.inject.Scope`. Buscar el literal
     * `@Singleton` sería otra lista escrita a mano y dejaría fuera
     * `@ActivityRetainedScoped`, `@ViewModelScoped` y cualquier scope propio.
     */
    private fun scopesDe(anotaciones: List<Class<*>>): List<String> =
        anotaciones.filter { it.isAnnotationPresent(Scope::class.java) }.map { it.simpleName }

    /**
     * Los nombres simples de toda clase con `@Module` bajo el `src/main` de
     * cualquier módulo declarado en `settings.gradle.kts`. La lista de módulos
     * sale del archivo que la define, no de una lista acá.
     */
    private fun modulosEnElArbolDeFuentes(): Set<String> {
        val raiz = raizDelRepo()
        val settings = File(raiz, "settings.gradle.kts").readText()
        val rutas = INCLUDE.findAll(
            settings
        ).map { it.groupValues[1].trim(':').replace(':', '/') }.toList()
        check(rutas.isNotEmpty()) { "no se leyó ningún include(...) de settings.gradle.kts" }

        return rutas.flatMap { ruta ->
            val srcMain = File(raiz, "$ruta/src/main")
            if (!srcMain.isDirectory) {
                emptyList()
            } else {
                srcMain.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .map { it.readText() }
                    .filter { ANOTACION_MODULE.containsMatchIn(it) }
                    .flatMap { texto -> DECLARACION.findAll(texto).map { it.groupValues[1] } }
                    .toList()
            }
        }.toSet()
    }

    /**
     * El grafo descubierto: los `@Module`, los servicios de Retrofit y las
     * clases con `@Inject constructor`, todo leído del classpath de prueba.
     */
    private class Grafo(
        val modulos: List<Class<*>>,
        val serviciosDeRed: Set<Class<*>>,
        val inyectables: List<Class<*>>
    ) {

        /**
         * Puerto → implementaciones, leído de los `@Binds` descubiertos. Sin
         * esto la cadena se corta en cada interfaz: un `@Provides` que recibe
         * `VisitCustodyRegistry` (el puerto) no llegaría nunca a
         * `V2VisitCustodyRegistry` (que sí sostiene la API).
         */
        private val implementacionesDe: Map<Class<*>, List<Class<*>>> = modulos
            .flatMap { it.declaredMethods.asIterable() }
            .filterNot { it.isSynthetic }
            .filter { it.isAnnotationPresent(Binds::class.java) && it.parameterTypes.size == 1 }
            .groupBy({ it.returnType }, { it.parameterTypes[0] })

        /**
         * ¿[tipo] sostiene un servicio de Retrofit, directa o transitivamente?
         *
         * Transitivo porque el congelamiento lo es: un `@Singleton` sobre un
         * registro que inyecta un repositorio que inyecta la API memoiza el
         * proxy igual que si lo inyectara él mismo.
         */
        fun sostieneServicioDeRed(tipo: Class<*>): Boolean = sostiene(tipo, HashSet())

        private fun sostiene(tipo: Class<*>, visitados: MutableSet<Class<*>>): Boolean {
            if (!visitados.add(tipo)) return false
            if (tipo in serviciosDeRed) return true
            if (!tipo.name.startsWith(PAQUETE)) return false
            if (implementacionesDe[tipo].orEmpty().any { sostiene(it, visitados) }) return true
            val constructor = tipo.declaredConstructors
                .firstOrNull { it.isAnnotationPresent(Inject::class.java) }
                ?: return false
            return constructor.parameterTypes.any { sostiene(it, visitados) }
        }

        companion object {
            const val PAQUETE = "com.example.msp_app."

            private const val DESC_MODULE = "Ldagger/Module;"
            private const val DESC_PROVIDES = "Ldagger/Provides;"
            private const val DESC_BINDS = "Ldagger/Binds;"
            private const val DESC_RETROFIT = "Lretrofit2/http/"
            private const val DESC_INJECT = "Ljavax/inject/Inject;"

            fun descubrir(): Grafo {
                val modulos = mutableListOf<Class<*>>()
                val servicios = mutableSetOf<Class<*>>()
                val inyectables = mutableListOf<Class<*>>()

                recorrerClasspath { nombre, bytes ->
                    val texto = String(bytes, Charsets.ISO_8859_1)
                    // `@Module` NO alcanza como filtro: los `@Provides` de un
                    // `companion object` compilan a una clase `X$Companion` que
                    // NO lleva `@Module` (lo verifiqué con `javap` sobre
                    // `VisitsReconcileModule$Companion`). Buscar directamente
                    // los bindings es lo que cierra ese medio agujero — y es,
                    // otra vez, descubrir en vez de listar.
                    val portaBindings = texto.contains(DESC_MODULE) ||
                        texto.contains(DESC_PROVIDES) ||
                        texto.contains(DESC_BINDS)
                    val esServicio = texto.contains(DESC_RETROFIT)
                    val esInyectable = texto.contains(DESC_INJECT)
                    if (portaBindings || esServicio || esInyectable) {
                        val clazz = cargar(nombre)
                        if (clazz != null) {
                            if (portaBindings && esPortadorDeBindings(clazz)) {
                                modulos += clazz
                            }
                            if (esServicio && clazz.isInterface && tieneMetodoRetrofit(clazz)) {
                                servicios += clazz
                            }
                            if (esInyectable && tieneConstructorInyectado(clazz)) {
                                inyectables += clazz
                            }
                        }
                    }
                }

                return Grafo(
                    modulos.distinct().sortedBy { it.name },
                    servicios,
                    inyectables.distinct().sortedBy { it.name }
                )
            }

            /**
             * Portador de bindings = lleva `@Module`, **o** declara al menos un
             * `@Provides`/`@Binds`. Lo segundo es lo que atrapa a los
             * `companion object` de un `@Module`, que Dagger acepta y el
             * bytecode no marca.
             */
            private fun esPortadorDeBindings(clazz: Class<*>): Boolean = runCatching {
                clazz.isAnnotationPresent(Module::class.java) ||
                    clazz.declaredMethods.any {
                        it.isAnnotationPresent(Provides::class.java) ||
                            it.isAnnotationPresent(Binds::class.java)
                    }
            }.getOrDefault(false)

            private fun tieneMetodoRetrofit(clazz: Class<*>): Boolean = runCatching {
                clazz.declaredMethods.any { metodo ->
                    metodo.annotations.any {
                        it.annotationClass.java.name.startsWith("retrofit2.http.")
                    }
                }
            }.getOrDefault(false)

            private fun tieneConstructorInyectado(clazz: Class<*>): Boolean = runCatching {
                clazz.declaredConstructors.any { it.isAnnotationPresent(Inject::class.java) }
            }.getOrDefault(false)

            /**
             * `Class.forName` con `initialize = false`: la guarda solo lee
             * anotaciones y firmas, y correr los inicializadores estáticos de
             * medio `:app` bajo un test JVM plano reventaría por Android.
             *
             * Lo que no carga se ignora **acá**, y el cruce contra el árbol de
             * fuentes de
             * [`el descubrimiento ve todos los modulos Hilt del arbol de fuentes`]
             * es lo que impide que ese silencio se vuelva punto ciego: si lo que
             * no cargó era un `@Module`, ese test se pone rojo con su nombre.
             */
            private fun cargar(nombre: String): Class<*>? = runCatching {
                Class.forName(nombre, false, Grafo::class.java.classLoader)
            }.getOrNull()

            private fun recorrerClasspath(visitar: (String, ByteArray) -> Unit) {
                val raiz = raizDelRepo().absolutePath
                System.getProperty("java.class.path").orEmpty()
                    .split(File.pathSeparator)
                    .map { File(it) }
                    // Solo lo que produce el propio repo: android.jar, la stdlib y
                    // las ~300 dependencias de Maven no traen módulos nuestros, y
                    // recorrerlas multiplicaría el costo sin cambiar el resultado.
                    .filter { it.absolutePath.startsWith(raiz) && it.exists() }
                    .forEach { entrada ->
                        when {
                            entrada.isDirectory -> recorrerDirectorio(entrada, visitar)
                            entrada.extension == "jar" -> recorrerJar(entrada, visitar)
                        }
                    }
            }

            private fun recorrerDirectorio(raiz: File, visitar: (String, ByteArray) -> Unit) {
                raiz.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { archivo ->
                        val nombre = archivo.relativeTo(raiz).invariantSeparatorsPath
                            .removeSuffix(".class")
                            .replace('/', '.')
                        if (nombre.startsWith(PAQUETE)) visitar(nombre, archivo.readBytes())
                    }
            }

            private fun recorrerJar(archivo: File, visitar: (String, ByteArray) -> Unit) {
                runCatching {
                    JarFile(archivo).use { jar ->
                        jar.entries().asSequence()
                            .filter { it.name.endsWith(".class") }
                            .filter { it.name.replace('/', '.').startsWith(PAQUETE) }
                            .toList()
                            .forEach { entry ->
                                val nombre = entry.name.removeSuffix(".class").replace('/', '.')
                                visitar(nombre, jar.getInputStream(entry).readBytes())
                            }
                    }
                }
            }
        }
    }
}
