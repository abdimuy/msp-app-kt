package com.example.msp_app.di

import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.warehouses.WarehousesApi
import java.lang.reflect.Proxy
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 7 (Plan 1 — cimiento): [NetworkModule] no debe reimplementar el
 * Retrofit de [ApiProvider] — solo delegar en `ApiProvider.create(...)`, el
 * mismo camino que usan las ~27 llamadas legacy repartidas en
 * ViewModels/repositorios/workers.
 *
 * IMPORTANTE (qué SÍ y qué NO prueba `Proxy.isProxyClass`): un JDK dynamic
 * proxy se cachea por (classloader, lista de interfaces) — NO por instancia
 * de `InvocationHandler` ni por la baseURL del `Retrofit` que hay detrás. Que
 * `moduleService.javaClass == legacyService.javaClass` sea `true` prueba
 * únicamente que ambos caminos siguen produciendo un proxy Retrofit sobre la
 * MISMA interfaz `WarehousesApi` — es decir, que `NetworkModule` sigue
 * delegando en Retrofit (vía `ApiProvider`) y no construyó a mano una
 * implementación manual/fake de la interfaz. Esta prueba NO puede detectar
 * (ni pretende detectar) si el `Retrofit` subyacente apunta a una baseURL
 * distinta o desactualizada — no existe una API pública en `Retrofit`/en la
 * interfaz generada para comparar esa baseURL desde aquí. La verificación de
 * paridad de baseURL en sí queda para el test de grafo Hilt
 * ([NetworkAndConnectivityHiltGraphTest]) y, en última instancia, para una
 * validación en dispositivo/emulador con el kill-switch real de Firestore.
 */
class NetworkModuleTest {

    @Test
    fun `provideWarehousesApi devuelve un servicio WarehousesApi utilizable`() {
        val service = NetworkModule.provideWarehousesApi()

        assertTrue(service is WarehousesApi)
    }

    @Test
    fun `provideWarehousesApi sigue delegando en un proxy Retrofit sobre WarehousesApi, igual que ApiProvider legacy`() {
        val legacyService = ApiProvider.create(WarehousesApi::class.java)
        val moduleService = NetworkModule.provideWarehousesApi()

        // Retrofit implementa los servicios con un Proxy JDK sobre la interfaz;
        // ambas vías deben producir ese tipo de objeto para la misma interfaz.
        // (Ver KDoc de la clase: esto NO compara baseURL, solo que se siga
        // usando Retrofit/ApiProvider en vez de una implementación manual.)
        assertTrue(
            "ApiProvider.create(...) debería devolver un dynamic proxy",
            Proxy.isProxyClass(legacyService.javaClass)
        )
        assertTrue(
            "NetworkModule.provideWarehousesApi() debería devolver un dynamic proxy Retrofit " +
                "sobre WarehousesApi — si esto falla, dejó de delegar en ApiProvider.create(...) " +
                "(p.ej. construyó su propio Retrofit o una fake), lo cual arriesgaría perder el " +
                "rebuild de baseURL por Firestore",
            Proxy.isProxyClass(moduleService.javaClass)
        )
        assertTrue(
            "ambos proxies deberían implementar exactamente la misma interfaz WarehousesApi",
            moduleService.javaClass == legacyService.javaClass
        )
    }
}
