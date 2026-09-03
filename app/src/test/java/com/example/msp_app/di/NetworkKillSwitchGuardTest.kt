package com.example.msp_app.di

import javax.inject.Singleton
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Guarda de regresión del kill-switch (money-path) tras cablear `:app` al factory
 * de `:core:network` (T7, Plan 4).
 *
 * La reactividad de la baseURL v1 (override remoto de Firestore en release) vive
 * en `ApiProvider`, que reconstruye su `Retrofit` con la URL vigente. Ese rebuild
 * solo alcanza a un consumidor inyectado si el `@Provides` que entrega el servicio
 * NO está `@Singleton`: con scope, Hilt memoizaría el proxy de la primera
 * `ApiProvider.create(...)` para toda la vida del proceso y el kill-switch dejaría
 * de alcanzar a cualquier inyección tras el primer flip.
 *
 * Este test falla si alguien anota `provideWarehousesApi` (o el patrón se copia a
 * otro `@Provides` de servicio en [NetworkModule]) con `@Singleton`.
 */
class NetworkKillSwitchGuardTest {

    @Test
    fun `provideWarehousesApi NO es Singleton (no congela la baseURL del kill-switch)`() {
        assertNotSingleton("provideWarehousesApi")
    }

    /**
     * Task 10 (plan `pagos-y-visitas`): el reconciliador de visitas es el
     * primer consumidor INYECTADO de `V2VisitsApi`, así que su `@Provides`
     * entra a esta guarda como cualquier otro servicio.
     *
     * Matiz honesto, para que nadie lea de más: la baseURL v2 no está hoy bajo
     * el kill-switch de Firestore (es estática por flavor — ver el KDoc de
     * `V2ApiProvider`), así que congelarla no rompería nada *hoy*. La guarda
     * es sobre la forma: ningún `@Provides` de un servicio salido de un
     * `ApiProvider` lleva scope. Si la v2 gana mañana un override remoto, el
     * `@Singleton` que alguien copió del vecino sería justo el que impide que
     * el flip alcance a los consumidores — y nadie lo notaría hasta la
     * emergencia en la que se usa.
     */
    @Test
    fun `provideV2VisitsApi NO es Singleton (misma forma que el resto de servicios)`() {
        assertNotSingleton("provideV2VisitsApi")
    }

    private fun assertNotSingleton(providesMethodName: String) {
        val method = NetworkModule::class.java.getDeclaredMethod(providesMethodName)

        assertFalse(
            "$providesMethodName NO debe ser @Singleton: Hilt memoizaría el proxy de la " +
                "primera ApiProvider.create(...) para toda la vida del proceso y el " +
                "kill-switch de baseURL dejaría de alcanzar a los consumidores inyectados " +
                "tras el primer flip",
            method.isAnnotationPresent(Singleton::class.java)
        )
    }
}
