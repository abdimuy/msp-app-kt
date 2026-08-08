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
 * ViewModels/repositorios/workers. Ese camino es lo que le hereda el rebuild
 * de baseURL por Firestore (kill-switch remoto en release).
 *
 * No hay forma pública de comparar instancias de Retrofit desde la interfaz
 * generada, así que la paridad se prueba por la propiedad que sí es
 * observable sin mocks: tanto `ApiProvider.create(...)` como
 * `NetworkModule.provideWarehousesApi()` deben producir un dynamic proxy JDK
 * (la forma en que Retrofit implementa la interfaz de servicio) — nunca una
 * implementación manual. Si `NetworkModule` alguna vez construyera su propio
 * Retrofit o una fake, este proxy-check dejaría de cumplirse.
 */
class NetworkModuleTest {

    @Test
    fun `provideWarehousesApi devuelve un servicio WarehousesApi utilizable`() {
        val service = NetworkModule.provideWarehousesApi()

        assertTrue(service is WarehousesApi)
    }

    @Test
    fun `provideWarehousesApi delega en el mismo camino dinamico que ApiProvider legacy`() {
        val legacyService = ApiProvider.create(WarehousesApi::class.java)
        val moduleService = NetworkModule.provideWarehousesApi()

        // Retrofit implementa los servicios con un Proxy JDK sobre la interfaz;
        // ambas vías deben producir exactamente ese tipo de objeto.
        assertTrue(
            "ApiProvider.create(...) debería devolver un dynamic proxy",
            Proxy.isProxyClass(legacyService.javaClass)
        )
        assertTrue(
            "NetworkModule.provideWarehousesApi() debería devolver el mismo tipo de " +
                "dynamic proxy que ApiProvider.create(...) — si esto falla, dejó de delegar " +
                "en ApiProvider y probablemente perdió el rebuild de baseURL por Firestore",
            Proxy.isProxyClass(moduleService.javaClass)
        )
        assertTrue(
            moduleService.javaClass == legacyService.javaClass
        )
    }
}
