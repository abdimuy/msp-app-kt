package com.example.msp_app.feature.garantias.data.adapter

import com.example.msp_app.core.database.entities.garantiav2.GarantiaFotoPendienteEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.feature.garantias.domain.model.EstadoDeFoto
import com.example.msp_app.feature.garantias.domain.port.ArchivosDeGarantiaPort
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * El estado de una foto después de intentar subirla.
 *
 * ## Qué protege
 *
 * [RoomEstadoDeLasFotosAdapter.eliminar] es la única escritura que borra el
 * archivo, y lo hace **después** de marcar la subida (D3, mutante A7). Un
 * rechazo o un intento fallido dejan la fila y su archivo intactos: la evidencia
 * sigue visible y reintentable. La resolución del evento sólo alcanza a las
 * fotos que lo esperaban y que todavía no lo tienen.
 */
class RoomEstadoDeLasFotosAdapterTest : RoomTestBase() {

    private val reloj = Instant.parse("2026-11-13T12:00:00Z")

    private lateinit var estado: RoomEstadoDeLasFotosAdapter
    private lateinit var archivos: ArchivosDePrueba

    @Before
    fun crearAdaptador() {
        archivos = ArchivosDePrueba()
        estado = RoomEstadoDeLasFotosAdapter(db.garantiaFotoDao(), archivos)
    }

    @Test
    fun `marcarSubida deja la foto enviada y con su hora`() = runTest {
        insertar(clave = "foto-1", ultimoError = "sin señal")

        estado.marcarSubida("foto-1", reloj)

        val guardada = db.garantiaFotoDao().porClave("foto-1")!!
        assertEquals(EstadoDeFoto.ENVIADA.wireValue, guardada.ESTADO)
        assertEquals("2026-11-13T12:00:00Z", guardada.SUBIDA_EN)
        assertNull(guardada.ULTIMO_ERROR)
        assertTrue(archivos.borrados.isEmpty())
    }

    @Test
    fun `marcarRechazada guarda el estado y el motivo`() = runTest {
        insertar(clave = "foto-1")

        estado.marcarRechazada("foto-1", "el servidor rechazó la imagen", reloj)

        val guardada = db.garantiaFotoDao().porClave("foto-1")!!
        assertEquals(EstadoDeFoto.RECHAZADA.wireValue, guardada.ESTADO)
        assertEquals("el servidor rechazó la imagen", guardada.ULTIMO_ERROR)
        assertEquals("2026-11-13T12:00:00Z", guardada.ACTUALIZADO_EN)
    }

    @Test
    fun `anotarIntento suma uno y guarda el motivo`() = runTest {
        insertar(clave = "foto-1")

        estado.anotarIntento("foto-1", "sin conexión", reloj)

        val guardada = db.garantiaFotoDao().porClave("foto-1")!!
        assertEquals(1, guardada.INTENTOS)
        assertEquals("sin conexión", guardada.ULTIMO_ERROR)
        assertEquals(EstadoDeFoto.PENDIENTE.wireValue, guardada.ESTADO)
    }

    @Test
    fun `resolverEvento propaga el evento y el folio y no repite`() = runTest {
        insertar(clave = "foto-1", eventoRef = "evento-local-1")
        insertar(clave = "otra", eventoRef = "evento-local-2")

        val primera = estado.resolverEvento("evento-local-1", "evento-servidor-1", "folio-1", reloj)
        val segunda = estado.resolverEvento("evento-local-1", "evento-servidor-2", "folio-2", reloj)

        assertEquals(1, primera)
        assertEquals(0, segunda)
        val guardada = db.garantiaFotoDao().porClave("foto-1")!!
        assertEquals("evento-servidor-1", guardada.EVENTO_ID)
        assertEquals("folio-1", guardada.FOLIO_ID)
        assertNull(db.garantiaFotoDao().porClave("otra")!!.EVENTO_ID)
    }

    @Test
    fun `eliminar borra la fila y su archivo`() = runTest {
        insertar(clave = "foto-1")

        estado.eliminar("foto-1")

        assertNull(db.garantiaFotoDao().porClave("foto-1"))
        assertEquals(listOf("fotos/foto-1.jpg"), archivos.borrados)
    }

    @Test
    fun `eliminar una clave ausente no toca ningun archivo`() = runTest {
        estado.eliminar("no-existe")

        assertTrue(archivos.borrados.isEmpty())
    }

    private suspend fun insertar(
        clave: String,
        eventoRef: String? = null,
        ultimoError: String? = null
    ) = db.garantiaFotoDao().guardar(
        GarantiaFotoPendienteEntity(
            CLAVE = clave,
            RUTA_ARCHIVO = "fotos/$clave.jpg",
            MIME = "image/jpeg",
            ESTADO = EstadoDeFoto.PENDIENTE.wireValue,
            EVENTO_REF = eventoRef,
            ULTIMO_ERROR = ultimoError,
            CREADO_EN = "2026-11-13T10:00:00Z",
            ACTUALIZADO_EN = "2026-11-13T10:00:00Z"
        )
    )
}

private class ArchivosDePrueba : ArchivosDeGarantiaPort {

    val borrados = mutableListOf<String>()

    override suspend fun leer(ruta: String): ByteArray? = byteArrayOf(1)

    override suspend fun borrar(ruta: String) {
        borrados += ruta
    }
}
