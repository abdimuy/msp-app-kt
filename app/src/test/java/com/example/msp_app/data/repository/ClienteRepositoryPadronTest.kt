package com.example.msp_app.data.repository

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.common.busqueda.NormalizadorDeBusqueda
import com.example.msp_app.core.common.busqueda.TiempoAgotadoException
import com.example.msp_app.core.common.busqueda.TopeDeDescarga
import com.example.msp_app.core.database.entities.ClienteEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.local.datasource.ClienteDataSource
import com.example.msp_app.data.repository.FakeClientesApi.Companion.cliente
import java.io.IOException
import java.text.Normalizer
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** E-APP-159 y E-APP-158: descarga, reemplazo atómico, padrón protegido, fecha de actualización y tope del worker. */
class ClienteRepositoryPadronTest : RoomTestBase() {

    private val avisos = mutableListOf<Pair<String, Map<String, Any?>>>()
    private var guardado = 0L
    private var reloj = 1_700_000_000_000L
    private val api = FakeClientesApi()

    private fun repo() = ClienteRepository(
        dataSource = ClienteDataSource(
            ApplicationProvider.getApplicationContext<android.content.Context>()
        ),
        api = api,
        avisar = { accion, _, datos -> avisos += accion to datos },
        guardarFechaDeSync = { guardado = it },
        leerFechaDeSync = { guardado },
        ahora = { reloj }
    )

    private lateinit var repository: ClienteRepository

    @Before fun armar() {
        repository = repo()
    }

    private suspend fun sembrarViejo() = db.clienteDao().insertAll(
        listOf(
            ClienteEntity(1, "ANA RUIZ", "A", null, "ana ruiz"),
            ClienteEntity(2, "BETO SOTO", "A", null, "beto soto")
        )
    )

    // --- padrón protegido ---

    @Test fun `una lista vacia del servidor no reemplaza el padron`() = runTest {
        sembrarViejo()
        api.respuesta = emptyList()
        assertFalse(repository.syncFromServer())
        assertEquals(2, repository.getCount())
        assertEquals(listOf("ANA RUIZ"), repository.searchClientes("ana ruiz").map { it.NOMBRE })
    }

    @Test fun `una lista vacia se registra con el logger y no actualiza la fecha`() = runTest {
        sembrarViejo()
        api.respuesta = emptyList()
        repository.syncFromServer()
        assertEquals(listOf("LISTA_VACIA_IGNORADA"), avisos.map { it.first })
        assertEquals(2, avisos.single().second["clientesGuardados"])
        assertEquals(0L, guardado)
        assertNull(repository.ultimaActualizacionMs())
    }

    @Test fun `el cuerpo vacio del servidor tampoco borra el padron`() = runTest {
        sembrarViejo()
        assertFalse(repository.aplicarPadron(emptyList()))
        assertEquals(2, repository.getCount())
    }

    @Test fun `con padron local vacio una lista vacia tampoco hace nada ni truena`() = runTest {
        api.respuesta = emptyList()
        assertFalse(repository.syncFromServer())
        assertEquals(0, repository.getCount())
    }

    @Test fun `una lista con datos reemplaza todo el padron`() = runTest {
        sembrarViejo()
        api.respuesta = listOf(cliente(10, "CARLA DIAZ"), cliente(11, "DANIEL PAZ"), cliente(12, "ELENA ROJAS"))
        assertTrue(repository.syncFromServer())
        assertEquals(3, repository.getCount())
        assertEquals(emptyList<String>(), repository.searchClientes("ana ruiz").map { it.NOMBRE })
        assertEquals(listOf("CARLA DIAZ"), repository.searchClientes("carla").map { it.NOMBRE })
    }

    @Test fun `una lista con datos guarda la fecha de actualizacion`() = runTest {
        api.respuesta = listOf(cliente(10, "CARLA DIAZ"))
        repository.syncFromServer()
        assertEquals(reloj, guardado)
        assertEquals(reloj, repository.ultimaActualizacionMs())
    }

    @Test fun `un solo cliente del servidor es suficiente para reemplazar`() = runTest {
        sembrarViejo()
        api.respuesta = listOf(cliente(10, "CARLA DIAZ"))
        assertTrue(repository.syncFromServer())
        assertEquals(1, repository.getCount())
    }

    @Test fun `nunca se actualizo la fecha es nula`() {
        assertNull(repository.ultimaActualizacionMs())
    }

    // --- normalización al guardar ---

    @Test fun `el padron se guarda con la normalizacion nueva`() = runTest {
        api.respuesta = listOf(cliente(1, "J. GUADALUPE O'CONNOR-PEÑA"))
        repository.syncFromServer()
        val guardadoEn = db.clienteDao().getByIds(listOf(1)).single()
        assertEquals("j guadalupe o connor pena", guardadoEn.NOMBRE_NORMALIZADO)
        assertEquals("J. GUADALUPE O'CONNOR-PEÑA", guardadoEn.NOMBRE)
    }

    @Test fun `lo guardado y la consulta pasan por la misma normalizacion`() = runTest {
        val nombre = "  MARÍA   DE LOS ÁNGELES  NÚÑEZ. "
        api.respuesta = listOf(cliente(1, nombre))
        repository.syncFromServer()
        val guardadoEn = db.clienteDao().getByIds(listOf(1)).single().NOMBRE_NORMALIZADO
        assertEquals(NormalizadorDeBusqueda.normalizar(nombre), guardadoEn)
        assertEquals(listOf(1), repository.searchClientes(nombre).map { it.CLIENTE_ID })
    }

    // --- padrón guardado con la normalización VIEJA ---

    private fun vieja(texto: String) = Normalizer.normalize(
        texto.trim().lowercase(),
        Normalizer.Form.NFD
    )
        .replace("\\p{M}".toRegex(), "").replace("\\s+".toRegex(), " ").trim()

    @Test fun `un padron guardado con la normalizacion vieja se encuentra con la logica nueva`() =
        runTest {
            val nombres =
                listOf(
                    "J. GUADALUPE RAMOS",
                    "PÉREZ-GARCÍA JOSEFINA",
                    "O'CONNOR RUIZ",
                    "MARIA  DE   LA CRUZ"
                )
            db.clienteDao().insertAll(
                nombres.mapIndexed { i, n -> ClienteEntity(i + 1, n, "A", null, vieja(n)) }
            )
            assertEquals(listOf(1), repository.searchClientes("j guadalupe").map { it.CLIENTE_ID })
            assertEquals(listOf(2), repository.searchClientes("perez garcia").map { it.CLIENTE_ID })
            assertEquals(listOf(3), repository.searchClientes("o connor").map { it.CLIENTE_ID })
            assertEquals(
                listOf(4),
                repository.searchClientes("maria de la cruz").map { it.CLIENTE_ID }
            )
        }

    @Test fun `tras el siguiente sync el padron queda con la normalizacion nueva y sigue encontrandose`() =
        runTest {
            db.clienteDao().insertAll(
                listOf(
                    ClienteEntity(1, "J. GUADALUPE RAMOS", "A", null, vieja("J. GUADALUPE RAMOS"))
                )
            )
            api.respuesta = listOf(cliente(1, "J. GUADALUPE RAMOS"))
            repository.syncFromServer()
            assertEquals(
                "j guadalupe ramos",
                db.clienteDao().getByIds(listOf(1)).single().NOMBRE_NORMALIZADO
            )
            assertEquals(listOf(1), repository.searchClientes("j guadalupe").map { it.CLIENTE_ID })
        }

    // --- tope total con reloj virtual ---

    @Test fun `el worker vence exactamente en su tope de segundo plano`() = runTest {
        sembrarViejo()
        api.duracionMs = Long.MAX_VALUE / 4
        val trabajo =
            async { runCatching { repository.syncFromServer(TopeDeDescarga.SEGUNDO_PLANO_MS) } }
        runCurrent()
        advanceTimeBy(TopeDeDescarga.SEGUNDO_PLANO_MS - 1)
        runCurrent()
        assertTrue("a un ms del tope sigue descargando", trabajo.isActive)
        advanceTimeBy(1)
        runCurrent()
        val error = trabajo.await().exceptionOrNull()
        assertTrue(error is TiempoAgotadoException)
        assertEquals(TopeDeDescarga.SEGUNDO_PLANO_MS, currentTime)
    }

    @Test fun `al vencer el worker cancela la peticion y deja el padron como estaba`() = runTest {
        sembrarViejo()
        api.duracionMs = Long.MAX_VALUE / 4
        val error = runCatching { repository.syncFromServer(1_000) }.exceptionOrNull()
        assertTrue(error is TiempoAgotadoException)
        assertEquals(1, api.cancelaciones)
        assertEquals(0, api.terminadas)
        assertEquals(2, repository.getCount())
        assertEquals(0L, guardado)
    }

    @Test fun `el tope de segundo plano es el del worker por omision`() = runTest {
        api.duracionMs = TopeDeDescarga.SEGUNDO_PLANO_MS - 1
        api.respuesta = listOf(cliente(1, "ANA RUIZ"))
        assertTrue(repository.syncFromServer())
    }

    @Test fun `un error de red del servidor se propaga al worker para que reintente`() = runTest {
        sembrarViejo()
        api.falla = IOException("sin red")
        val error = runCatching { repository.syncFromServer() }.exceptionOrNull()
        assertTrue(error is IOException)
        assertEquals(2, repository.getCount())
    }

    @Test fun `un parecido por error de dedo sale por el repositorio`() = runTest {
        db.clienteDao().insertAll(
            listOf(ClienteEntity(1, "GUADALUPE HERNANDEZ", "A", null, "guadalupe hernandez"))
        )
        val r = repository.buscar("guadalupe hernandes")
        assertTrue(r.exactos.isEmpty())
        assertEquals(listOf(1), r.parecidos.map { it.CLIENTE_ID })
    }
}
