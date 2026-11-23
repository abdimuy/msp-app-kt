package com.example.msp_app.core.database.dao.garantiav2

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.garantiav2.GarantiaCatalogoCacheEntity
import com.example.msp_app.core.database.entities.garantiav2.GarantiaComandoPendienteEntity
import com.example.msp_app.core.database.entities.garantiav2.GarantiaFolioSeguidoEntity
import com.example.msp_app.core.database.entities.garantiav2.GarantiaFotoPendienteEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Las consultas de las cuatro tablas de garantías v2 contra el Room real
 * (Robolectric, base en memoria): el SQL, no la intención.
 *
 * ## Qué se prueba acá y por qué no río arriba
 *
 * Estas tablas son la cola que impide que un reintento se convierta en un 409
 * o en una operación perdida. Las consultas que más importan son las que
 * **propagan ids** (`resolverFolio`, `resolverArticulo`, `resolverEvento`):
 * si escribieran en la fila equivocada, el comando saldría con una ruta
 * apuntando a otro folio. Por eso cada una siembra **dos** referencias
 * locales y comprueba que sólo se resuelva la pedida.
 */
class GarantiaV2DaoTest : RobolectricTestBase() {

    private lateinit var database: AppDatabase
    private lateinit var folios: GarantiaFolioDao
    private lateinit var comandos: GarantiaComandoDao
    private lateinit var fotos: GarantiaFotoDao
    private lateinit var catalogos: GarantiaCatalogoDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        folios = database.garantiaFolioDao()
        comandos = database.garantiaComandoDao()
        fotos = database.garantiaFotoDao()
        catalogos = database.garantiaCatalogoDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `folios se listan del mas reciente al mas viejo y se buscan por id local y de servidor`() =
        runTest {
            folios.guardar(folio("folio-viejo", abiertoEn = "2026-11-10T10:00:00Z"))
            folios.guardar(folio("folio-nuevo", abiertoEn = "2026-11-12T10:00:00Z"))
            folios.marcarAbierto("folio-nuevo", "servidor-1", "2026-11-12T10:05:00Z")

            assertEquals(
                listOf("folio-nuevo", "folio-viejo"),
                folios.todos().map { it.ID }
            )
            assertEquals("servidor-1", folios.porId("folio-nuevo")?.FOLIO_ID)
            assertEquals("folio-nuevo", folios.porFolioId("servidor-1")?.ID)
            assertEquals("abierto", folios.porId("folio-nuevo")?.ESTADO_LOCAL)
            assertNull(folios.porId("folio-viejo")?.FOLIO_ID)
            assertEquals("pendiente_envio", folios.porId("folio-viejo")?.ESTADO_LOCAL)
            assertEquals(1, folios.pendientesDeEnvio())

            folios.marcarEstado(
                id = "folio-viejo",
                estado = "rechazado",
                error = "sin permiso",
                ahora = "2026-11-12T10:10:00Z"
            )
            assertEquals("rechazado", folios.porId("folio-viejo")?.ESTADO_LOCAL)
            assertEquals("sin permiso", folios.porId("folio-viejo")?.ULTIMO_ERROR)
            assertEquals(0, folios.pendientesDeEnvio())
        }

    @Test
    fun `los comandos se ordenan por folio y respetan el orden en que se guardaron`() = runTest {
        val primero = comandos.insertar(comando("clave-1", folioRef = "folio-a"))
        val segundo = comandos.insertar(comando("clave-2", folioRef = "folio-a"))
        comandos.insertar(comando("clave-3", folioRef = "folio-b"))
        val tercero = comandos.insertar(comando("clave-4", folioRef = "folio-a"))

        assertEquals(
            listOf("clave-1", "clave-2", "clave-4"),
            comandos.delFolio("folio-a").map { it.CLAVE }
        )
        assertEquals(
            listOf("clave-1", "clave-2", "clave-3", "clave-4"),
            comandos.pendientes().map { it.CLAVE }
        )
        assertTrue(primero < segundo && segundo < tercero)
        assertEquals("clave-2", comandos.porClave("clave-2")?.CLAVE)
        assertEquals(4, comandos.pendientesCount())
    }

    @Test
    fun `resolverFolio propaga el id del servidor solo a los comandos de ese folio local`() =
        runTest {
            comandos.insertar(comando("clave-a1", folioRef = "folio-a"))
            comandos.insertar(comando("clave-a2", folioRef = "folio-a"))
            comandos.insertar(comando("clave-b1", folioRef = "folio-b"))

            val tocados = comandos.resolverFolio(
                folioRef = "folio-a",
                folioId = "servidor-a",
                ahora = "2026-11-12T11:00:00Z"
            )

            assertEquals(2, tocados)
            assertEquals("servidor-a", comandos.porClave("clave-a1")?.FOLIO_ID)
            assertEquals("servidor-a", comandos.porClave("clave-a2")?.FOLIO_ID)
            assertNull(comandos.porClave("clave-b1")?.FOLIO_ID)

            val otraVez = comandos.resolverFolio(
                folioRef = "folio-a",
                folioId = "servidor-distinto",
                ahora = "2026-11-12T11:01:00Z"
            )
            assertEquals("un id ya resuelto no se reescribe", 0, otraVez)
            assertEquals("servidor-a", comandos.porClave("clave-a1")?.FOLIO_ID)
        }

    @Test
    fun `resolverArticulo y resolverEvento propagan por referencia local, no por folio`() =
        runTest {
            comandos.insertar(
                comando("clave-art-1", folioRef = "folio-a", articuloRef = "articulo-local-1")
            )
            comandos.insertar(
                comando("clave-art-2", folioRef = "folio-a", articuloRef = "articulo-local-1")
            )
            comandos.insertar(
                comando("clave-art-3", folioRef = "folio-a", articuloRef = "articulo-local-2")
            )
            comandos.insertar(
                comando("clave-evento-1", folioRef = "folio-a", eventoRef = "evento-local-1")
            )

            assertEquals(
                2,
                comandos.resolverArticulo(
                    articuloRef = "articulo-local-1",
                    articuloId = "articulo-servidor-1",
                    ahora = "2026-11-12T11:05:00Z"
                )
            )
            assertEquals("articulo-servidor-1", comandos.porClave("clave-art-1")?.ARTICULO_ID)
            assertEquals("articulo-servidor-1", comandos.porClave("clave-art-2")?.ARTICULO_ID)
            assertNull(comandos.porClave("clave-art-3")?.ARTICULO_ID)

            assertEquals(
                1,
                comandos.resolverEvento(
                    eventoRef = "evento-local-1",
                    eventoId = "evento-servidor-1",
                    ahora = "2026-11-12T11:06:00Z"
                )
            )
            assertEquals("evento-servidor-1", comandos.porClave("clave-evento-1")?.EVENTO_ID)
        }

    @Test
    fun `marcar enviado, rechazado y anotar intento escriben el estado que corresponde`() =
        runTest {
            val enviado = comandos.insertar(comando("clave-enviado", folioRef = "folio-a"))
            val rechazado = comandos.insertar(comando("clave-rechazado", folioRef = "folio-a"))
            val conIntento = comandos.insertar(comando("clave-intento", folioRef = "folio-a"))

            comandos.marcarEnviado(enviado, "2026-11-12T12:00:00Z")
            comandos.marcarRechazado(rechazado, "huella distinta", "2026-11-12T12:01:00Z")
            comandos.anotarIntento(conIntento, "sin red", "2026-11-12T12:02:00Z")

            assertEquals("enviado", comandos.porClave("clave-enviado")?.ESTADO)
            assertNull(comandos.porClave("clave-enviado")?.ULTIMO_ERROR)
            assertEquals("rechazado", comandos.porClave("clave-rechazado")?.ESTADO)
            assertEquals("huella distinta", comandos.porClave("clave-rechazado")?.ULTIMO_ERROR)
            assertEquals("pendiente", comandos.porClave("clave-intento")?.ESTADO)
            assertEquals(1, comandos.porClave("clave-intento")?.INTENTOS)
            assertEquals("sin red", comandos.porClave("clave-intento")?.ULTIMO_ERROR)
            assertEquals(1, comandos.pendientesCount())
        }

    @Test
    fun `insertarTodos guarda varios comandos de una transaccion`() = runTest {
        val ids = comandos.insertarTodos(
            listOf(
                comando("clave-lote-1", folioRef = "folio-a"),
                comando("clave-lote-2", folioRef = "folio-a")
            )
        )

        assertEquals(2, ids.size)
        assertEquals(
            listOf("clave-lote-1", "clave-lote-2"),
            comandos.delFolio("folio-a").map { it.CLAVE }
        )
    }

    @Test
    fun `las fotos sin evento no estan listas para subir y las demas si`() = runTest {
        fotos.guardarTodas(
            listOf(
                foto("foto-sin-evento", creadaEn = "2026-11-12T10:00:00Z"),
                foto(
                    "foto-con-evento",
                    creadaEn = "2026-11-12T10:01:00Z",
                    eventoId = "evento-servidor-1"
                )
            )
        )

        assertEquals(
            listOf("foto-sin-evento", "foto-con-evento"),
            fotos.pendientes().map { it.CLAVE }
        )
        assertEquals(listOf("foto-con-evento"), fotos.listasParaSubir().map { it.CLAVE })
        assertEquals(2, fotos.pendientesCount())
        assertEquals(
            listOf("/tmp/foto-sin-evento.png", "/tmp/foto-con-evento.png"),
            fotos.rutasVivas()
        )
    }

    @Test
    fun `resolverEvento propaga a las fotos de esa referencia local`() = runTest {
        fotos.guardar(foto("foto-1", eventoRef = "evento-local-1"))
        fotos.guardar(foto("foto-2", eventoRef = "evento-local-1"))
        fotos.guardar(foto("foto-3", eventoRef = "evento-local-2"))

        val tocadas = fotos.resolverEvento(
            eventoRef = "evento-local-1",
            eventoId = "evento-servidor-1",
            folioId = "folio-servidor-1",
            ahora = "2026-11-12T10:05:00Z"
        )

        assertEquals(2, tocadas)
        assertEquals("evento-servidor-1", fotos.porClave("foto-1")?.EVENTO_ID)
        assertEquals("folio-servidor-1", fotos.porClave("foto-1")?.FOLIO_ID)
        assertEquals("evento-servidor-1", fotos.porClave("foto-2")?.EVENTO_ID)
        assertNull(fotos.porClave("foto-3")?.EVENTO_ID)
        assertEquals(listOf("foto-1", "foto-2"), fotos.listasParaSubir().map { it.CLAVE })
    }

    @Test
    fun `marcar subida, rechazo y borrado dejan la foto en el estado correcto`() = runTest {
        fotos.guardar(foto("foto-subida", eventoId = "evento-1"))
        fotos.guardar(foto("foto-rechazada", eventoId = "evento-1"))

        fotos.marcarSubida("foto-subida", "2026-11-12T13:00:00Z")
        fotos.marcarRechazada("foto-rechazada", "tipo no permitido", "2026-11-12T13:01:00Z")
        fotos.anotarIntento("foto-rechazada", "sin red", "2026-11-12T13:02:00Z")

        assertEquals("enviada", fotos.porClave("foto-subida")?.ESTADO)
        assertEquals("2026-11-12T13:00:00Z", fotos.porClave("foto-subida")?.SUBIDA_EN)
        assertEquals("rechazada", fotos.porClave("foto-rechazada")?.ESTADO)
        assertEquals(1, fotos.porClave("foto-rechazada")?.INTENTOS)
        assertEquals(0, fotos.pendientesCount())

        fotos.eliminar("foto-subida")
        assertNull(fotos.porClave("foto-subida"))
        assertEquals(listOf("/tmp/foto-rechazada.png"), fotos.rutasVivas())
    }

    @Test
    fun `el catalogo es una sola fila que se reemplaza al actualizarse`() = runTest {
        assertNull(catalogos.leer())

        catalogos.guardar(
            GarantiaCatalogoCacheEntity(
                CUERPO = """{"acciones":["recolectar"]}""",
                ACTUALIZADO_EN = "2026-11-12T09:00:00Z"
            )
        )
        assertEquals(
            """{"acciones":["recolectar"]}""",
            catalogos.leer()?.CUERPO
        )

        catalogos.guardar(
            GarantiaCatalogoCacheEntity(
                CUERPO = """{"acciones":["entregar"]}""",
                ACTUALIZADO_EN = "2026-11-12T09:30:00Z"
            )
        )
        assertEquals(
            """{"acciones":["entregar"]}""",
            catalogos.leer()?.CUERPO
        )
        assertEquals("2026-11-12T09:30:00Z", catalogos.leer()?.ACTUALIZADO_EN)
    }

    private fun folio(id: String, abiertoEn: String = "2026-11-12T08:00:00Z") =
        GarantiaFolioSeguidoEntity(
            ID = id,
            ORIGEN = "piso",
            ESTADO_LOCAL = "pendiente_envio",
            ABIERTO_EN = abiertoEn,
            ACTUALIZADO_EN = abiertoEn
        )

    private fun comando(
        clave: String,
        folioRef: String,
        articuloRef: String? = null,
        eventoRef: String? = null
    ) = GarantiaComandoPendienteEntity(
        CLAVE = clave,
        OPERACION = "abrir_folio",
        METODO = "POST",
        RUTA = "v2/garantias",
        CUERPO = """{"origen":"piso"}""",
        CONTENT_TYPE = "application/json",
        DEVICE_CREATED_AT = "2026-11-12T08:00:00Z",
        ESTADO = "pendiente",
        FOLIO_REF = folioRef,
        ARTICULO_REF = articuloRef,
        EVENTO_REF = eventoRef,
        CREADO_EN = "2026-11-12T08:00:00Z",
        ACTUALIZADO_EN = "2026-11-12T08:00:00Z"
    )

    private fun foto(
        clave: String,
        creadaEn: String = "2026-11-12T08:00:00Z",
        eventoRef: String? = null,
        eventoId: String? = null
    ) = GarantiaFotoPendienteEntity(
        CLAVE = clave,
        FOLIO_REF = "folio-a",
        EVENTO_REF = eventoRef,
        EVENTO_ID = eventoId,
        RUTA_ARCHIVO = "/tmp/$clave.png",
        MIME = "image/png",
        ESTADO = "pendiente",
        CREADO_EN = creadaEn,
        ACTUALIZADO_EN = creadaEn
    )
}
