package com.example.msp_app.feature.garantias.data.adapter

import com.example.msp_app.core.database.entities.garantiav2.GarantiaFolioSeguidoEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.feature.garantias.domain.model.EstadoLocalDeFolio
import com.example.msp_app.feature.garantias.domain.model.FolioSeguido
import com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * "Mis folios" sobre Room de verdad (en memoria).
 *
 * ## Qué protege
 *
 * [FolioSeguido.id] es local y nunca viaja como `id` del folio;
 * [RoomFoliosSeguidosAdapter.marcarAbierto] es la única escritura que guarda el
 * `id` del servidor, y sólo cuando `abrir` ya respondió (D4). Un origen o un
 * estado local que la tabla no conoce no se adivina: es un error, porque una
 * fila así sólo puede venir de una migración a mano.
 */
class RoomFoliosSeguidosAdapterTest : RoomTestBase() {

    private val reloj = Instant.parse("2026-11-13T12:00:00Z")

    private lateinit var folios: RoomFoliosSeguidosAdapter

    @Before
    fun crearAdaptador() {
        folios = RoomFoliosSeguidosAdapter(db.garantiaFolioDao())
    }

    @Test
    fun `guardar y leer un folio conserva todos los campos`() = runTest {
        val folio = folio(id = "local-1", estadoLocal = EstadoLocalDeFolio.PENDIENTE_ENVIO)

        folios.guardar(folio)

        assertEquals(folio, folios.porId("local-1"))
    }

    @Test
    fun `la bandeja va del mas reciente al mas viejo`() = runTest {
        folios.guardar(folio(id = "viejo", abiertoEn = Instant.parse("2026-11-01T10:00:00Z")))
        folios.guardar(folio(id = "nuevo", abiertoEn = Instant.parse("2026-11-10T10:00:00Z")))

        assertEquals(listOf("nuevo", "viejo"), folios.todos().map { it.id })
    }

    @Test
    fun `porFolioId encuentra el folio por su id del servidor`() = runTest {
        folios.guardar(folio(id = "local-1", folioId = "folio-servidor-1"))

        assertEquals("local-1", folios.porFolioId("folio-servidor-1")?.id)
        assertNull(folios.porFolioId("folio-servidor-2"))
    }

    @Test
    fun `marcarAbierto guarda el id del servidor y limpia el error`() = runTest {
        folios.guardar(folio(id = "local-1", ultimoError = "sin señal"))

        folios.marcarAbierto("local-1", "folio-servidor-1", reloj)

        val leido = folios.porId("local-1")!!
        assertEquals("folio-servidor-1", leido.folioId)
        assertEquals(EstadoLocalDeFolio.ABIERTO, leido.estadoLocal)
        assertNull(leido.ultimoError)
        assertEquals(reloj, leido.actualizadoEn)
    }

    @Test
    fun `marcarEstado guarda el estado local y su error`() = runTest {
        folios.guardar(folio(id = "local-1"))

        folios.marcarEstado("local-1", EstadoLocalDeFolio.RECHAZADO, "el servidor rechazó", reloj)

        val leido = folios.porId("local-1")!!
        assertEquals(EstadoLocalDeFolio.RECHAZADO, leido.estadoLocal)
        assertEquals("el servidor rechazó", leido.ultimoError)
    }

    @Test
    fun `pendientesDeEnvio cuenta solo los que esperan al servidor`() = runTest {
        folios.guardar(folio(id = "p1"))
        folios.guardar(folio(id = "p2"))
        folios.guardar(folio(id = "abierto", estadoLocal = EstadoLocalDeFolio.ABIERTO))

        assertEquals(2, folios.pendientesDeEnvio())
    }

    @Test
    fun `un origen desconocido en la tabla es un error`() = runTest {
        db.garantiaFolioDao().guardar(
            GarantiaFolioSeguidoEntity(
                ID = "local-x",
                ORIGEN = "bodega",
                ESTADO_LOCAL = "abierto",
                ABIERTO_EN = "2026-11-13T10:00:00Z",
                ACTUALIZADO_EN = "2026-11-13T10:00:00Z"
            )
        )

        val fallo = runCatching { folios.porId("local-x") }.exceptionOrNull()

        assertTrue(fallo is IllegalStateException)
    }

    @Test
    fun `un estado local desconocido en la tabla es un error`() = runTest {
        db.garantiaFolioDao().guardar(
            GarantiaFolioSeguidoEntity(
                ID = "local-y",
                ORIGEN = "piso",
                ESTADO_LOCAL = "en_limbo",
                ABIERTO_EN = "2026-11-13T10:00:00Z",
                ACTUALIZADO_EN = "2026-11-13T10:00:00Z"
            )
        )

        val fallo = runCatching { folios.porId("local-y") }.exceptionOrNull()

        assertTrue(fallo is IllegalStateException)
    }

    private fun folio(
        id: String,
        folioId: String? = null,
        estadoLocal: EstadoLocalDeFolio = EstadoLocalDeFolio.PENDIENTE_ENVIO,
        abiertoEn: Instant = Instant.parse("2026-11-13T10:00:00Z"),
        ultimoError: String? = null
    ): FolioSeguido = FolioSeguido(
        id = id,
        folioId = folioId,
        origen = OrigenDeFolio.PISO,
        clienteId = null,
        ventaId = null,
        estadoLocal = estadoLocal,
        abiertoEn = abiertoEn,
        actualizadoEn = abiertoEn,
        ultimoError = ultimoError
    )
}
