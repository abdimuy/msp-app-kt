package com.example.msp_app.di

import dagger.Provides
import java.lang.reflect.Method
import javax.inject.Singleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Guarda de regresión del kill-switch de baseURL (money-path) sobre los módulos
 * Hilt de red de `:app`.
 *
 * La reactividad de la baseURL v1 (override remoto de Firestore en release) vive
 * en `ApiProvider`, que reconstruye su `Retrofit` con la URL vigente. Ese rebuild
 * solo alcanza a un consumidor inyectado si el `@Provides` que entrega el servicio
 * NO está `@Singleton`: con scope, Hilt memoizaría el proxy de la primera
 * `ApiProvider.create(...)` para toda la vida del proceso y el kill-switch dejaría
 * de alcanzar a cualquier inyección tras el primer flip.
 *
 * ## El default está invertido, y esa es la parte importante
 *
 * Hasta la ronda de arreglos del Task 10 esta guarda era una **lista de opt-in por
 * método**: un `getDeclaredMethod("provideX")` por cada servicio que alguien se
 * acordara de registrar. Su único modo de falla era el olvido — un servicio nuevo
 * que nadie nombrara aquí pasaba en silencio, indefinidamente. Con la Task 11 a
 * punto de agregar disparadores de sync (y con ellos, servicios), eso no era
 * hipotético.
 *
 * Ahora se **barren** todos los `@Provides` de [NetworkModule] y
 * [NetworkConfigModule] y se exige que NINGUNO sea `@Singleton`, salvo los de
 * [SCOPE_PERMITIDO]. O sea: un `@Provides` nuevo está guardado desde que nace, y
 * salir de la guarda exige un acto deliberado **con su razón escrita**. El olvido
 * ya no es una salida.
 *
 * ## Por qué la allowlist es real y no un escape
 *
 * Un barrido a secas sería falso: `@Singleton` SÍ es legítimo para lo que no es un
 * servicio vivo de red. La regla nunca fue "nada con scope"; fue **"nada con scope
 * que sostenga un servicio salido de un `ApiProvider`"**. Config inmutable derivada
 * del `BuildConfig` y la fuente del token bearer no sostienen ningún `Retrofit`, así
 * que su scope es correcto y se queda — nombrado, con su motivo, y verificado por
 * [`la allowlist no acumula entradas muertas`] para que no se pudra en silencio.
 *
 * Alcance: los dos módulos Hilt de `:app` que producen objetos de la capa de red.
 * `:core:printing` y demás módulos con bindings scopeados quedan fuera a propósito
 * — no producen servicios de `ApiProvider`, que es de lo que trata esta guarda.
 */
class NetworkKillSwitchGuardTest {

    /**
     * Los ÚNICOS `@Provides` de red que pueden llevar `@Singleton`, cada uno con la
     * razón por la que el kill-switch no les aplica. Agregar una entrada acá es una
     * decisión, no un trámite: si lo que se exime sostiene un servicio de
     * `ApiProvider.create(...)`, la entrada es un agujero, no una excepción.
     */
    private val scopePermitido: Map<String, String> = mapOf(
        "NetworkConfigModule.provideNetworkConfig" to
            "NetworkConfig es data inmutable derivada del BuildConfig del flavor, no un " +
            "servicio: no sostiene ningún Retrofit y el kill-switch resuelve la URL por request",
        "NetworkConfigModule.provideAuthTokenProvider" to
            "FirebaseAuthTokenProvider es la FUENTE del token bearer, no un servicio de " +
            "ApiProvider: compartir su caché entre consumidores es el punto del scope"
    )

    private val modulosDeRed: List<Class<*>> = listOf(
        NetworkModule::class.java,
        NetworkConfigModule::class.java
    )

    private fun Class<*>.providesMethods(): List<Method> = declaredMethods
        .filterNot { it.isSynthetic }
        .filter { it.isAnnotationPresent(Provides::class.java) }

    private fun nombre(module: Class<*>, method: Method) = "${module.simpleName}.${method.name}"

    @Test
    fun `ningun Provides de red es Singleton, salvo la allowlist documentada`() {
        val infractores = modulosDeRed.flatMap { module ->
            module.providesMethods()
                .filter { it.isAnnotationPresent(Singleton::class.java) }
                .map { nombre(module, it) }
                .filterNot { it in scopePermitido }
        }

        assertEquals(
            "Estos @Provides de red son @Singleton y NO están en la allowlist: $infractores.\n" +
                "Si entregan un servicio de ApiProvider.create(...), Hilt memoizaría el proxy " +
                "para toda la vida del proceso y el kill-switch de baseURL por Firestore " +
                "dejaría de alcanzar a los consumidores inyectados tras el primer flip: " +
                "quita el @Singleton.\n" +
                "Si NO sostienen ningún Retrofit (config inmutable, fuente de token, etc.), " +
                "agrégalos a `scopePermitido` CON la razón escrita.",
            emptyList<String>(),
            infractores
        )
    }

    /**
     * Control positivo de la allowlist: cada entrada tiene que nombrar un método
     * que exista y que de verdad esté `@Singleton` hoy.
     *
     * Sin esto la allowlist se pudre de dos maneras, ambas silenciosas: una entrada
     * cuyo método se renombró deja de eximir a nadie (inofensivo pero engañoso), y
     * una entrada cuyo método ya perdió el `@Singleton` deja escrita una excusa
     * para algo que ya no pasa — que es exactamente como una allowlist termina
     * cubriendo, años después, un caso que nadie revisó.
     */
    @Test
    fun `la allowlist no acumula entradas muertas`() {
        val singletonsReales = modulosDeRed.flatMap { module ->
            module.providesMethods()
                .filter { it.isAnnotationPresent(Singleton::class.java) }
                .map { nombre(module, it) }
        }.toSet()

        val muertas = scopePermitido.keys - singletonsReales

        assertEquals(
            "Estas entradas de `scopePermitido` ya no corresponden a un @Provides " +
                "@Singleton existente: $muertas. Bórralas.",
            emptySet<String>(),
            muertas
        )
    }

    /**
     * Pin explícito del primer servicio que motivó esta guarda (T7, Plan 1). El
     * barrido de arriba ya lo cubre; esto deja el caso nombrado para quien llegue
     * al archivo buscando `WarehousesApi`.
     */
    @Test
    fun `provideWarehousesApi NO es Singleton (no congela la baseURL del kill-switch)`() {
        assertNotSingleton(NetworkModule::class.java, "provideWarehousesApi")
    }

    /**
     * Task 10 (plan `pagos-y-visitas`): el reconciliador de visitas es el primer
     * consumidor INYECTADO de `V2VisitsApi`.
     *
     * Matiz honesto, para que nadie lea de más: la baseURL v2 no está hoy bajo el
     * kill-switch de Firestore (es estática por flavor — ver el KDoc de
     * `V2ApiProvider`), así que congelarla no rompería nada *hoy*. La guarda es
     * sobre la forma: ningún `@Provides` de un servicio salido de un `ApiProvider`
     * lleva scope. Si la v2 gana mañana un override remoto, el `@Singleton` que
     * alguien copió del vecino sería justo el que impide que el flip alcance a los
     * consumidores — y nadie lo notaría hasta la emergencia en la que se usa.
     */
    @Test
    fun `provideV2VisitsApi NO es Singleton (misma forma que el resto de servicios)`() {
        assertNotSingleton(NetworkModule::class.java, "provideV2VisitsApi")
    }

    private fun assertNotSingleton(module: Class<*>, providesMethodName: String) {
        val method = module.getDeclaredMethod(providesMethodName)

        assertFalse(
            "$providesMethodName NO debe ser @Singleton: Hilt memoizaría el proxy de la " +
                "primera ApiProvider.create(...) para toda la vida del proceso y el " +
                "kill-switch de baseURL dejaría de alcanzar a los consumidores inyectados " +
                "tras el primer flip",
            method.isAnnotationPresent(Singleton::class.java)
        )
    }
}
