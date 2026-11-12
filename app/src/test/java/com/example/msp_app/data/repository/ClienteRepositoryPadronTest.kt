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

    // --- padrón protegido: lista incompleta (menos del 50 % con más de 1,000 guardados) ---

    private suspend fun sembrar(cuantos: Int) = db.clienteDao().insertAll(
        (1..cuantos).map { ClienteEntity(it, "CLIENTE $it", "A", null, "cliente $it") }
    )

    private fun nueva(cuantos: Int) =
        (1..cuantos).map { ClienteEntity(100_000 + it, "NUEVO $it", "A", null, "nuevo $it") }

    @Test fun `con 1001 guardados una lista de 500 es incompleta y se conserva el padron`() = runTest {
        sembrar(1001)
        assertEquals(
            ResultadoDePadron.INCOMPLETA_IGNORADA,
            repository.aplicarPadronConResultado(nueva(500))
        )
        assertEquals(1001, repository.getCount())
        assertEquals(0L, guardado)
    }

    @Test fun `con 1001 guardados una lista de 501 ya es suficiente y reemplaza`() = runTest {
        sembrar(1001)
        assertEquals(
            ResultadoDePadron.REEMPLAZADO,
            repository.aplicarPadronConResultado(nueva(501))
        )
        assertEquals(501, repository.getCount())
        assertEquals(reloj, guardado)
    }

    @Test fun `con 1000 guardados no hay proteccion por tamano aunque la lista sea de 1`() = runTest {
        sembrar(1000)
        assertEquals(ResultadoDePadron.REEMPLAZADO, repository.aplicarPadronConResultado(nueva(1)))
        assertEquals(1, repository.getCount())
    }

    @Test fun `con 999 guardados no hay proteccion por tamano`() = runTest {
        sembrar(999)
        assertEquals(ResultadoDePadron.REEMPLAZADO, repository.aplicarPadronConResultado(nueva(10)))
        assertEquals(10, repository.getCount())
    }

    @Test fun `con 2000 guardados el 49 por ciento se rechaza y el 50 por ciento reemplaza`() = runTest {
        sembrar(2000)
        assertEquals(
            ResultadoDePadron.INCOMPLETA_IGNORADA,
            repository.aplicarPadronConResultado(nueva(980))
        )
        assertEquals(2000, repository.getCount())
        assertEquals(
            ResultadoDePadron.REEMPLAZADO,
            repository.aplicarPadronConResultado(nueva(1000))
        )
        assertEquals(1000, repository.getCount())
    }

    @Test fun `una lista incompleta se registra solo con conteos y sin datos personales`() = runTest {
        sembrar(2000)
        repository.aplicarPadronConResultado(nueva(10))
        assertEquals(listOf("LISTA_INCOMPLETA_IGNORADA"), avisos.map { it.first })
        assertEquals(
            mapOf<String, Any?>("clientesGuardados" to 2000, "clientesRecibidos" to 10),
            avisos.single().second
        )
    }

    @Test fun `aplicarPadron devuelve false con una lista incompleta`() = runTest {
        sembrar(2000)
        assertFalse(repository.aplicarPadron(nueva(10)))
    }

    @Test fun `la lista vacia sigue protegida y se distingue de la incompleta`() = runTest {
        sembrar(2000)
        assertEquals(
            ResultadoDePadron.VACIA_IGNORADA,
            repository.aplicarPadronConResultado(emptyList())
        )
    }

    // --- salidas del padrón congelado: forzar y 3.ª lista reducida seguida ---

    private suspend fun aplicarVeces(veces: Int, tamano: Int) =
        (1..veces).map { repository.aplicarPadronConResultado(nueva(tamano)) }

    @Test fun `el escenario medido 2000 guardados y el servidor manda 900 se acepta a la 3 vez y aparece el cliente nuevo`() =
        runTest {
            sembrar(2000)
            api.respuesta = (1..900).map {
                cliente(1 + it, "CLIENTE ${1 + it}")
            } + cliente(5000, "NUEVA PERSONA")
            val resultados = (1..3).map { repository.sincronizar() }
            assertEquals(
                listOf(
                    ResultadoDePadron.INCOMPLETA_IGNORADA,
                    ResultadoDePadron.INCOMPLETA_IGNORADA,
                    ResultadoDePadron.REEMPLAZADO
                ),
                resultados
            )
            assertEquals(901, repository.getCount())
            assertEquals(
                listOf(5000),
                repository.searchClientes("nueva persona").map { it.CLIENTE_ID }
            )
            assertEquals(
                listOf(
                    "LISTA_INCOMPLETA_IGNORADA",
                    "LISTA_INCOMPLETA_IGNORADA",
                    "LISTA_INCOMPLETA_ACEPTADA"
                ),
                avisos.map { it.first }
            )
            assertEquals(
                mapOf<String, Any?>("clientesGuardados" to 2000, "clientesRecibidos" to 901),
                avisos.last().second
            )
        }

    @Test fun `con la misma lista reducida solo la 3 vez seguida se acepta`() = runTest {
        sembrar(2000)
        assertEquals(
            listOf(
                ResultadoDePadron.INCOMPLETA_IGNORADA,
                ResultadoDePadron.INCOMPLETA_IGNORADA,
                ResultadoDePadron.REEMPLAZADO
            ),
            aplicarVeces(3, 900)
        )
    }

    @Test fun `un tamano distinto reinicia la cuenta`() = runTest {
        sembrar(2000)
        val r = aplicarVeces(2, 900) + repository.aplicarPadronConResultado(nueva(600)) +
            repository.aplicarPadronConResultado(nueva(600))
        assertEquals(List(4) { ResultadoDePadron.INCOMPLETA_IGNORADA }, r)
        assertEquals(2000, repository.getCount())
        assertEquals(
            ResultadoDePadron.REEMPLAZADO,
            repository.aplicarPadronConResultado(nueva(600))
        )
    }

    @Test fun `la tolerancia es 5 por ciento exacto`() = runTest {
        sembrar(2000)
        // 900 -> 945 es +5.0 %: parecido, cuenta como 2.ª; 945 -> 945 es la 3.ª
        assertEquals(
            listOf(
                ResultadoDePadron.INCOMPLETA_IGNORADA,
                ResultadoDePadron.INCOMPLETA_IGNORADA,
                ResultadoDePadron.REEMPLAZADO
            ),
            listOf(900, 945, 945).map { repository.aplicarPadronConResultado(nueva(it)) }
        )
    }

    @Test fun `mas de 5 por ciento ya no es parecido`() = runTest {
        sembrar(2000)
        // 900 -> 946 es +5.1 %: reinicia; 946 -> 946 solo es la 2.ª
        assertEquals(
            List(3) { ResultadoDePadron.INCOMPLETA_IGNORADA },
            listOf(900, 946, 946).map { repository.aplicarPadronConResultado(nueva(it)) }
        )
    }

    @Test fun `un reemplazo exitoso reinicia la cuenta`() = runTest {
        sembrar(4000)
        aplicarVeces(2, 900)
        assertEquals(
            ResultadoDePadron.REEMPLAZADO,
            repository.aplicarPadronConResultado(nueva(2000))
        )
        // ahora hay 2000 guardados: 900 vuelve a ser incompleta y arranca de 1, no de 3
        assertEquals(
            ResultadoDePadron.INCOMPLETA_IGNORADA,
            repository.aplicarPadronConResultado(nueva(900))
        )
        assertEquals(2000, repository.getCount())
    }

    @Test fun `el boton manual no anota rechazos ni acepta solo una lista recortada`() = runTest {
        sembrar(2000)
        val manuales = (1..3).map {
            repository.aplicarPadronConResultado(nueva(900), automatico = false)
        }
        assertEquals(List(3) { ResultadoDePadron.INCOMPLETA_IGNORADA }, manuales)
        assertEquals(2000, repository.getCount())
        // los toques manuales no suman a la cuenta del worker: a este le falta llegar a su 3.a
        assertEquals(
            ResultadoDePadron.INCOMPLETA_IGNORADA,
            repository.aplicarPadronConResultado(nueva(900))
        )
        assertEquals(2000, repository.getCount())
    }

    @Test fun `el worker sigue aceptando a la 3 vez aunque haya toques manuales en medio`() = runTest {
        sembrar(2000)
        val r = listOf(
            repository.aplicarPadronConResultado(nueva(900)),
            repository.aplicarPadronConResultado(nueva(900), automatico = false),
            repository.aplicarPadronConResultado(nueva(900)),
            repository.aplicarPadronConResultado(nueva(900), automatico = false),
            repository.aplicarPadronConResultado(nueva(900))
        )
        assertEquals(
            listOf(
                ResultadoDePadron.INCOMPLETA_IGNORADA,
                ResultadoDePadron.INCOMPLETA_IGNORADA,
                ResultadoDePadron.INCOMPLETA_IGNORADA,
                ResultadoDePadron.INCOMPLETA_IGNORADA,
                ResultadoDePadron.REEMPLAZADO
            ),
            r
        )
        assertEquals(900, repository.getCount())
    }

    @Test fun `forzar aplica la lista incompleta, la registra con conteos y reinicia la cuenta`() =
        runTest {
            sembrar(2000)
            aplicarVeces(2, 900)
            assertEquals(
                ResultadoDePadron.REEMPLAZADO,
                repository.aplicarPadronConResultado(nueva(10), forzar = true)
            )
            assertEquals(10, repository.getCount())
            assertEquals("LISTA_INCOMPLETA_FORZADA", avisos.last().first)
            assertEquals(
                mapOf<String, Any?>("clientesGuardados" to 2000, "clientesRecibidos" to 10),
                avisos.last().second
            )
            assertEquals(reloj, guardado)
        }

    @Test fun `la lista vacia sigue protegida siempre, tambien forzada y tambien tras rechazos`() =
        runTest {
            sembrar(2000)
            aplicarVeces(2, 900)
            assertEquals(
                ResultadoDePadron.VACIA_IGNORADA,
                repository.aplicarPadronConResultado(emptyList())
            )
            assertEquals(
                ResultadoDePadron.VACIA_IGNORADA,
                repository.aplicarPadronConResultado(emptyList(), forzar = true)
            )
            assertEquals(2000, repository.getCount())
        }

    @Test fun `la cuenta de rechazos sobrevive a un nuevo repositorio con las mismas preferencias`() =
        runTest {
            sembrar(2000)
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            fun conPrefs() = ClienteRepository(
                dataSource = ClienteDataSource(context),
                api = api,
                avisar = { accion, _, datos -> avisos += accion to datos },
                guardarFechaDeSync = { guardado = it },
                leerFechaDeSync = { guardado },
                ahora = { reloj },
                rechazos = RegistroDeRechazos.enPreferencias(context)
            )
            conPrefs().aplicarPadronConResultado(nueva(900))
            conPrefs().aplicarPadronConResultado(nueva(900))
            assertEquals(
                ResultadoDePadron.REEMPLAZADO,
                conPrefs().aplicarPadronConResultado(nueva(900))
            )
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
