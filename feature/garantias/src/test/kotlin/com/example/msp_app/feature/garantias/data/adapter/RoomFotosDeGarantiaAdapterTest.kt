package com.example.msp_app.feature.garantias.data.adapter

import com.example.msp_app.core.database.entities.garantiav2.GarantiaFotoPendienteEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.garantias.domain.model.EstadoDeFoto
import com.example.msp_app.feature.garantias.domain.model.FotoNueva
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Las fotos en espera sobre Room de verdad (en memoria).
 *
 * ## Qué protege
 *
 * La clave es la llave primaria: guardar dos veces la misma foto no duplica la
 * evidencia y un reintento no crea una segunda imagen en el servidor (D3,
 * mutante A1). [RoomFotosDeGarantiaAdapter.listasParaSubir] exige el
 * `EVENTO_ID` porque sin él la ruta del servidor todavía no existe, y un estado
 * que la tabla no conoce no se adivina: es un error.
 */
class RoomFotosDeGarantiaAdapterTest : RoomTestBase() {

    private val reloj = FakeClock(Instant.parse("2026-11-13T10:00:00Z"))

    private lateinit var fotos: RoomFotosDeGarantiaAdapter

    @Before
    fun crearAdaptador() {
        fotos = RoomFotosDeGarantiaAdapter(db.garantiaFotoDao(), reloj)
    }

    @Test
    fun `guardar deja la foto pendiente con su comando y su hora`() = runTest {
        fotos.guardar(foto("foto-1"), comandoId = 7)

        val guardada = fotos.porClave("foto-1")!!
        assertEquals("fotos/foto-1.jpg", guardada.rutaArchivo)
        assertEquals("image/jpeg", guardada.mime)
        assertEquals(EstadoDeFoto.PENDIENTE, guardada.estado)
        assertEquals(0, guardada.intentos)
        assertEquals(7L, guardada.comandoId)
        assertEquals("folio-local-1", guardada.folioRef)
        assertEquals("evento-local-1", guardada.eventoRef)
        assertEquals(reloj.now(), guardada.creadoEn)
        assertNull(guardada.subidaEn)
    }

    @Test
    fun `guardarTodas con una lista vacia no escribe nada`() = runTest {
        fotos.guardarTodas(emptyList(), comandoId = null)

        assertEquals(0, fotos.pendientesCount())
        assertTrue(fotos.pendientes().isEmpty())
    }

    @Test
    fun `guardarTodas guarda todas con la misma hora`() = runTest {
        fotos.guardarTodas(listOf(foto("foto-1"), foto("foto-2")), comandoId = 3)

        assertEquals(2, fotos.pendientesCount())
        assertEquals(reloj.now(), fotos.porClave("foto-2")!!.creadoEn)
    }

    @Test
    fun `guardar dos veces la misma clave no duplica`() = runTest {
        fotos.guardar(foto("foto-1"), comandoId = null)
        fotos.guardar(foto("foto-1").copy(rutaArchivo = "fotos/otra.jpg"), comandoId = null)

        assertEquals(1, fotos.pendientesCount())
        assertEquals("fotos/otra.jpg", fotos.porClave("foto-1")!!.rutaArchivo)
    }

    @Test
    fun `pendientes devuelve solo las pendientes en orden de captura`() = runTest {
        insertar(entidad(clave = "primera", estado = EstadoDeFoto.PENDIENTE, creadoEn = T1))
        insertar(entidad(clave = "segunda", estado = EstadoDeFoto.PENDIENTE, creadoEn = T2))
        insertar(entidad(clave = "enviada", estado = EstadoDeFoto.ENVIADA, creadoEn = T3))

        assertEquals(listOf("primera", "segunda"), fotos.pendientes().map { it.clave })
    }

    @Test
    fun `listasParaSubir exige el id del evento`() = runTest {
        insertar(entidad(clave = "sin-evento", estado = EstadoDeFoto.PENDIENTE, creadoEn = T1))
        insertar(
            entidad(
                clave = "con-evento",
                estado = EstadoDeFoto.PENDIENTE,
                creadoEn = T2,
                eventoId = "evento-servidor-1"
            )
        )
        insertar(
            entidad(
                clave = "ya-enviada",
                estado = EstadoDeFoto.ENVIADA,
                creadoEn = T3,
                eventoId = "evento-servidor-1"
            )
        )

        assertEquals(listOf("con-evento"), fotos.listasParaSubir().map { it.clave })
    }

    @Test
    fun `porClave devuelve null si no existe`() = runTest {
        assertNull(fotos.porClave("no-existe"))
    }

    @Test
    fun `pendientesCount cuenta solo las pendientes`() = runTest {
        insertar(entidad(clave = "p1", estado = EstadoDeFoto.PENDIENTE, creadoEn = T1))
        insertar(entidad(clave = "p2", estado = EstadoDeFoto.PENDIENTE, creadoEn = T2))
        insertar(entidad(clave = "e1", estado = EstadoDeFoto.ENVIADA, creadoEn = T3))

        assertEquals(2, fotos.pendientesCount())
    }

    @Test
    fun `rutasVivas devuelve todas las rutas`() = runTest {
        insertar(entidad(clave = "p1", estado = EstadoDeFoto.PENDIENTE, creadoEn = T1))
        insertar(entidad(clave = "e1", estado = EstadoDeFoto.ENVIADA, creadoEn = T2))

        assertEquals(
            setOf("fotos/p1.jpg", "fotos/e1.jpg"),
            fotos.rutasVivas().toSet()
        )
    }

    @Test
    fun `un estado de foto desconocido es un error`() = runTest {
        db.garantiaFotoDao().guardar(
            GarantiaFotoPendienteEntity(
                CLAVE = "rara",
                RUTA_ARCHIVO = "fotos/rara.jpg",
                MIME = "image/jpeg",
                ESTADO = "en_limbo",
                CREADO_EN = T1,
                ACTUALIZADO_EN = T1
            )
        )

        val fallo = runCatching { fotos.porClave("rara") }.exceptionOrNull()

        assertTrue(fallo is IllegalStateException)
    }

    private suspend fun insertar(entidad: GarantiaFotoPendienteEntity) =
        db.garantiaFotoDao().guardar(entidad)

    private fun foto(clave: String): FotoNueva = FotoNueva(
        clave = clave,
        rutaArchivo = "fotos/$clave.jpg",
        mime = "image/jpeg",
        folioRef = "folio-local-1",
        eventoRef = "evento-local-1"
    )

    private fun entidad(
        clave: String,
        estado: EstadoDeFoto,
        creadoEn: String,
        eventoId: String? = null
    ): GarantiaFotoPendienteEntity = GarantiaFotoPendienteEntity(
        CLAVE = clave,
        RUTA_ARCHIVO = "fotos/$clave.jpg",
        MIME = "image/jpeg",
        ESTADO = estado.wireValue,
        EVENTO_ID = eventoId,
        CREADO_EN = creadoEn,
        ACTUALIZADO_EN = creadoEn
    )

    private companion object {
        const val T1 = "2026-11-13T10:00:00Z"
        const val T2 = "2026-11-13T10:01:00Z"
        const val T3 = "2026-11-13T10:02:00Z"
    }
}
