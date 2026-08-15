package com.example.msp_app.features.cities

import com.example.msp_app.data.api.services.cities.CitiesApi
import com.example.msp_app.data.api.services.cities.CitiesResponse
import com.example.msp_app.data.models.city.CityDto
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * El catálogo de ciudades es lo único que separa al vendedor de capturar una
 * ciudad que el servidor va a rechazar al aplicar, así que estas pruebas cubren
 * los cuatro desenlaces que la pantalla tiene que distinguir: catálogo cargado,
 * catálogo vacío, red caída y filas rotas.
 *
 * Se prueba contra [MockWebServer] y no contra un `CitiesApi` falso a propósito:
 * el riesgo real de este endpoint no es la lógica del repositorio sino el sobre
 * del JSON. El v2 Go envuelve las listas en `items` y usa `snake_case`, mientras
 * que el v1 Node del que se calcó esta cadena usa `body`. Un fake de la interfaz
 * daría por bueno el mapeo justo donde puede estar mal.
 */
class CitiesRepositoryTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repository(): CitiesRepository {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return CitiesRepository { retrofit.create(CitiesApi::class.java) }
    }

    private fun responderCon(body: String) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
    }

    @Test
    fun `carga el catalogo con cada ciudad pegada a su estado`() = runTest {
        responderCon(
            """
            {"items":[
              {"ciudad_id":338,"ciudad":"TEHUACAN","estado_id":337,"estado":"PUEBLA"},
              {"ciudad_id":11523,"ciudad":"OAXACA","estado_id":11522,"estado":"OAXACA"},
              {"ciudad_id":11751,"ciudad":"ORIZABA","estado_id":11750,"estado":"VERACRUZ"}
            ]}
            """.trimIndent()
        )

        val ciudades = repository().getCities().getOrThrow()

        assertEquals(3, ciudades.size)
        // La fila entera viaja junta: si el estado se pudiera elegir aparte, este
        // catálogo de tres estados produciría clientes cruzados.
        assertEquals("TEHUACAN", ciudades[0].ciudad)
        assertEquals("PUEBLA", ciudades[0].estado)
        assertEquals(338, ciudades[0].ciudadId)
        assertEquals(337, ciudades[0].estadoId)
        assertEquals("VERACRUZ", ciudades[2].estado)
    }

    @Test
    fun `pega al endpoint v2 de ciudades`() = runTest {
        responderCon("""{"items":[]}""")

        repository().getCities()

        assertEquals("/v2/ciudades", server.takeRequest().path)
    }

    @Test
    fun `un catalogo vacio es exito, no error`() = runTest {
        // Un catálogo sin filas NO debe dejar al selector en estado de error: la
        // pantalla tiene que ofrecer el texto libre, no invitar a reintentar.
        responderCon("""{"items":[]}""")

        val resultado = repository().getCities()

        assertTrue(resultado.isSuccess)
        assertEquals(emptyList<Any>(), resultado.getOrThrow())
    }

    @Test
    fun `un sobre sin la clave items no truena`() = runTest {
        // Gson ignora los valores por omisión de Kotlin: sin el tipo nullable de
        // `CitiesResponse.items`, esta respuesta produciría un NPE ofuscado.
        responderCon("{}")

        val resultado = repository().getCities()

        assertTrue(resultado.isSuccess)
        assertEquals(emptyList<Any>(), resultado.getOrThrow())
    }

    @Test
    fun `una fila rota no se lleva a las demas`() = runTest {
        responderCon(
            """
            {"items":[
              {"ciudad_id":338,"ciudad":"TEHUACAN","estado_id":337,"estado":"PUEBLA"},
              {"ciudad_id":999},
              {"ciudad":"SIN ID","estado_id":337,"estado":"PUEBLA"},
              {"ciudad_id":26220,"ciudad":"TLACHICHUCA","estado_id":337,"estado":"PUEBLA"}
            ]}
            """.trimIndent()
        )

        val ciudades = repository().getCities().getOrThrow()

        assertEquals(listOf("TEHUACAN", "TLACHICHUCA"), ciudades.map { it.ciudad })
    }

    @Test
    fun `una ciudad sin estado sigue siendo elegible`() = runTest {
        // El catálogo real trae filas con el estado en blanco. Descartarlas dejaría
        // al vendedor sin ciudades que sí existen; el servidor resuelve el
        // ESTADO_ID desde la misma fila al aplicar.
        responderCon("""{"items":[{"ciudad_id":338,"ciudad":"TEHUACAN"}]}""")

        val ciudades = repository().getCities().getOrThrow()

        assertEquals(1, ciudades.size)
        assertEquals("", ciudades[0].estado)
        assertEquals(0, ciudades[0].estadoId)
    }

    @Test
    fun `recorta los espacios finales del catalogo`() = runTest {
        // `COYOMEAPAN ` y `ESPERANZA ` viven así en producción. Recortar es seguro
        // porque el servidor colapsa espacios antes de comparar.
        responderCon(
            """{"items":[{"ciudad_id":25361,"ciudad":"COYOMEAPAN ","estado_id":337,"estado":"PUEBLA "}]}"""
        )

        val ciudad = repository().getCities().getOrThrow().single()

        assertEquals("COYOMEAPAN", ciudad.ciudad)
        assertEquals("PUEBLA", ciudad.estado)
    }

    @Test
    fun `un error de red devuelve failure y no lanza`() = runTest {
        val rota = CitiesRepository { throw IOException("sin red") }
        // La fábrica es lazy: la excepción tiene que salir por getCities, no por
        // construir el repositorio.
        val resultado = rota.getCities()

        assertTrue(resultado.isFailure)
    }

    @Test
    fun `un 500 devuelve failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val resultado = repository().getCities()

        assertTrue(resultado.isFailure)
    }

    @Test
    fun `un JSON invalido devuelve failure`() = runTest {
        responderCon("no soy json")

        val resultado = repository().getCities()

        assertTrue(resultado.isFailure)
    }

    @Test
    fun `el sobre expone lista vacia cuando items es nulo`() {
        assertEquals(emptyList<CityDto>(), CitiesResponse(items = null).cities())
    }
}
