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
        val method = NetworkModule::class.java.getDeclaredMethod("provideWarehousesApi")

        assertFalse(
            "provideWarehousesApi NO debe ser @Singleton: congelaría la WarehousesApi " +
                "y el kill-switch de baseURL por Firestore dejaría de alcanzar a los " +
                "consumidores inyectados tras el primer flip",
            method.isAnnotationPresent(Singleton::class.java)
        )
    }
}
