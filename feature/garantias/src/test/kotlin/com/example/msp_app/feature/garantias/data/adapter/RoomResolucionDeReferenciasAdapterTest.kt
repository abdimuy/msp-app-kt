package com.example.msp_app.feature.garantias.data.adapter

import com.example.msp_app.core.database.entities.garantiav2.GarantiaComandoPendienteEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.feature.garantias.domain.model.EstadoDeComando
import com.example.msp_app.feature.garantias.domain.model.OperacionDeGarantia
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * La propagación de los ids que devuelve el servidor a la cola.
 *
 * ## Qué protege
 *
 * La resolución escribe **sólo** `FOLIO_ID`, `ARTICULO_ID` y `EVENTO_ID`, y sólo
 * donde todavía están en `NULL`: un reintento no puede cambiar un id ya resuelto
 * (D4). La prueba verifica que la clave, el cuerpo y la hora del dispositivo
 * sigan intactos después de resolver, porque un `UPDATE` que los tocara haría
 * que el servidor viera otro contenido para la misma clave (D3).
 */
class RoomResolucionDeReferenciasAdapterTest : RoomTestBase() {

    private val reloj = Instant.parse("2026-11-13T12:00:00Z")

    private lateinit var resolucion: RoomResolucionDeReferenciasAdapter

    @Before
    fun crearAdaptador() {
        resolucion = RoomResolucionDeReferenciasAdapter(db.garantiaComandoDao())
    }

    @Test
    fun `resolverFolio propaga el id y no toca clave cuerpo ni hora`() = runTest {
        db.garantiaComandoDao().insertar(comando(clave = "clave-1", folioRef = "folio-local-1"))

        val filas = resolucion.resolverFolio("folio-local-1", "folio-servidor-1", reloj)

        assertEquals(1, filas)
        val guardado = db.garantiaComandoDao().porClave("clave-1")!!
        assertEquals("folio-servidor-1", guardado.FOLIO_ID)
        assertEquals("clave-1", guardado.CLAVE)
        assertEquals("""{"origen":"piso"}""", guardado.CUERPO)
        assertEquals("2026-11-13T09:59:00Z", guardado.DEVICE_CREATED_AT)
        assertEquals("2026-11-13T12:00:00Z", guardado.ACTUALIZADO_EN)
    }

    @Test
    fun `resolverFolio no cambia un id ya resuelto`() = runTest {
        db.garantiaComandoDao().insertar(
            comando(clave = "clave-1", folioRef = "folio-local-1", folioId = "folio-servidor-1")
        )

        val filas = resolucion.resolverFolio("folio-local-1", "folio-servidor-2", reloj)

        assertEquals(0, filas)
        assertEquals("folio-servidor-1", db.garantiaComandoDao().porClave("clave-1")!!.FOLIO_ID)
    }

    @Test
    fun `resolverFolio alcanza a todos los comandos de ese folio`() = runTest {
        db.garantiaComandoDao().insertar(comando(clave = "clave-1", folioRef = "folio-local-1"))
        db.garantiaComandoDao().insertar(comando(clave = "clave-2", folioRef = "folio-local-1"))
        db.garantiaComandoDao().insertar(comando(clave = "otro", folioRef = "folio-local-2"))

        val filas = resolucion.resolverFolio("folio-local-1", "folio-servidor-1", reloj)

        assertEquals(2, filas)
        assertEquals("folio-servidor-1", db.garantiaComandoDao().porClave("clave-1")!!.FOLIO_ID)
        assertEquals("folio-servidor-1", db.garantiaComandoDao().porClave("clave-2")!!.FOLIO_ID)
        assertNull(db.garantiaComandoDao().porClave("otro")!!.FOLIO_ID)
    }

    @Test
    fun `resolverArticulo propaga el id y no repite`() = runTest {
        db.garantiaComandoDao().insertar(
            comando(clave = "clave-1", articuloRef = "articulo-local-1")
        )

        val primera = resolucion.resolverArticulo("articulo-local-1", "articulo-servidor-1", reloj)
        val segunda = resolucion.resolverArticulo("articulo-local-1", "articulo-servidor-2", reloj)

        assertEquals(1, primera)
        assertEquals(0, segunda)
        assertEquals(
            "articulo-servidor-1",
            db.garantiaComandoDao().porClave("clave-1")!!.ARTICULO_ID
        )
    }

    @Test
    fun `resolverEvento propaga el id y no repite`() = runTest {
        db.garantiaComandoDao().insertar(comando(clave = "clave-1", eventoRef = "evento-local-1"))

        val primera = resolucion.resolverEvento("evento-local-1", "evento-servidor-1", reloj)
        val segunda = resolucion.resolverEvento("evento-local-1", "evento-servidor-2", reloj)

        assertEquals(1, primera)
        assertEquals(0, segunda)
        assertEquals("evento-servidor-1", db.garantiaComandoDao().porClave("clave-1")!!.EVENTO_ID)
    }

    private fun comando(
        clave: String,
        folioRef: String? = null,
        folioId: String? = null,
        articuloRef: String? = null,
        eventoRef: String? = null
    ): GarantiaComandoPendienteEntity = GarantiaComandoPendienteEntity(
        CLAVE = clave,
        OPERACION = OperacionDeGarantia.ABRIR_FOLIO.wireValue,
        METODO = "POST",
        RUTA = "v2/garantias",
        CUERPO = """{"origen":"piso"}""",
        CONTENT_TYPE = "application/json",
        DEVICE_CREATED_AT = "2026-11-13T09:59:00Z",
        ESTADO = EstadoDeComando.PENDIENTE.wireValue,
        FOLIO_REF = folioRef,
        FOLIO_ID = folioId,
        ARTICULO_REF = articuloRef,
        EVENTO_REF = eventoRef,
        CREADO_EN = "2026-11-13T09:59:00Z",
        ACTUALIZADO_EN = "2026-11-13T09:59:00Z"
    )
}
