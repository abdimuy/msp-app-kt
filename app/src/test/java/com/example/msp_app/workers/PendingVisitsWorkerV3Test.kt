package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import com.example.msp_app.core.database.dao.visit.VisitImageDao
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.database.entities.VisitImageEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.upload.HEADER_INTENT_CAPTURED
import com.example.msp_app.core.upload.UploadDecision
import com.example.msp_app.core.upload.classifyUpload
import com.example.msp_app.core.visitas.envio.FirestoreModoEnvioVisitas
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas.V2_ACTUAL
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas.V3
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.data.api.services.visits.VisitaDetalleDTO
import com.example.msp_app.data.api.services.visits.VisitsApi
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.visit.Visit
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * El envío v3 de visitas (plan visitas P1, Tarea 11; cierra E-VIS-001 y H1b).
 *
 * Invariante (spec §2.2-1): la visita se suelta SOLO con prueba del servidor.
 *  - «la apliqué»: 2xx con el eco del mismo id y, si llevaba fotos, un
 *    `GET /v2/visitas/{id}` con existencia COMPLETA (desviación opción A del
 *    dueño, 2026-10-17; E-VIS-022); o, tras un error, existencia COMPLETA.
 *    → `CONFIRMADA_EN`.
 *  - «la tengo guardada»: `X-Intent-Captured` en un 4xx/5xx que la tabla
 *    compartida `classifyUpload` suelta (E-VIS-020, E-VIS-024). →
 *    `RESGUARDADA_EN`.
 *  - Todo lo demás reintenta, sin tope (E-APP-071).
 *
 * Las fotos locales se estampan y se borran SOLO tras CONFIRMADA.
 */
class PendingVisitsWorkerV3Test : RoomTestBase() {

    private val ahora: Instant = Instant.parse("2026-10-17T18:00:00Z")
    private val captura = "c0ffee00-0000-4000-8000-000000000000"

    private lateinit var context: Context

    @get:Rule
    val carpeta: TemporaryFolder = TemporaryFolder()

    @Before
    fun setUpContext() {
        context = ApplicationProvider.getApplicationContext()
    }

    // ─── fixtures ──────────────────────────────────────────────────────────────

    private suspend fun seed(visit: VisitEntity) = VisitsLocalDataSource(context).saveVisit(visit)

    private suspend fun fila(id: String = "visita-001"): VisitEntity = db.visitDao().findVisitById(
        id
    )!!

    private suspend fun guardadoFlag(id: String): Int = fila(id).GUARDADO_EN_MICROSIP

    /** Comprobante con su archivo de verdad: el armador del multipart exige `File.exists()`. */
    private suspend fun sembrarImagen(
        visitaId: String = "visita-001",
        imagenId: String,
        orden: Int = 0
    ): File {
        val archivo = File(carpeta.root, "$imagenId.jpg")
        archivo.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00))
        db.visitImageDao().insertAll(listOf(imagen(visitaId, imagenId, archivo, orden)))
        return archivo
    }

    /** Comprobante cuyo archivo NO existe: el armador lo omite (no viaja). */
    private suspend fun sembrarImagenSinArchivo(
        visitaId: String = "visita-001",
        imagenId: String,
        orden: Int
    ) {
        val archivo = File(carpeta.root, "$imagenId-borrado.jpg")
        db.visitImageDao().insertAll(listOf(imagen(visitaId, imagenId, archivo, orden)))
    }

    private fun imagen(visitaId: String, imagenId: String, archivo: File, orden: Int) =
        VisitImageEntity(
            ID = imagenId,
            VISITA_ID = visitaId,
            URI = archivo.absolutePath,
            MIME = "image/jpeg",
            ORDEN = orden,
            CREADA_EN = "2026-10-17T17:00:00Z"
        )

    private suspend fun subidaEn(imagenId: String, visitaId: String = "visita-001"): String? =
        db.visitImageDao().getByVisitaId(visitaId).single { it.ID == imagenId }.SUBIDA_EN

    private fun conCabecera(code: Int, body: String = "{}") =
        httpErrorConCabecera(code, HEADER_INTENT_CAPTURED, captura, body)

    private fun conGet(
        api: V2VisitsApi,
        getVisita: (suspend (String) -> VisitaDetalleDTO)?
    ): V2VisitsApi = if (getVisita == null) {
        api
    } else {
        object : V2VisitsApi by api {
            override suspend fun obtenerVisita(id: String): VisitaDetalleDTO = getVisita(id)
        }
    }

    @Suppress("LongParameterList") // seams de test
    private fun buildAndRunWorker(
        api: V2VisitsApi,
        modo: ModoEnvioVisitas,
        getVisita: (suspend (String) -> VisitaDetalleDTO)? = null,
        runAttemptCount: Int = 0,
        imagenes: VisitImageDao = db.visitImageDao(),
        visitId: String = "visita-001"
    ): Result = runPendingVisitsWorker(
        context = context,
        imagenes = imagenes,
        clock = FakeClock(ahora),
        modo = modo,
        visitId = visitId,
        api = conGet(api, getVisita),
        maxAttempts = 10,
        runAttemptCount = runAttemptCount
    )

    /** Multipart que contesta con el eco del id que viene en `datos`. */
    private fun multipartConEco(contador: IntArray = IntArray(1)): V2VisitsApi = fakeV2Api(
        multipart = { key, _, _ ->
            contador[0]++
            visitaDto(id = key)
        }
    ) { _, _ -> throw AssertionError("una visita con fotos viaja como multipart") }

    // ─── 2xx ──────────────────────────────────────────────────────────────────

    @Test
    fun `2xx con el mismo id confirma dentro de NonCancellable`() = runTest {
        seed(pendingVisit())
        val r = buildAndRunWorker(api = fakeV2Api { _, b -> visitaDto(id = b.id) }, modo = V3)
        assertEquals(Result.success(), r)
        val v = fila()
        assertEquals(1, v.GUARDADO_EN_MICROSIP)
        assertEquals("2026-10-17T18:00:00Z", v.CONFIRMADA_EN)
        assertNull(v.RESGUARDADA_EN)
    }

    @Test
    fun v3_200_con_id_distinto_no_suelta() = runTest {
        seed(pendingVisit())
        var gets = 0
        assertEquals(
            Result.retry(),
            buildAndRunWorker(
                api = fakeV2Api { _, _ -> visitaDto(id = "otra") },
                modo = V3,
                getVisita = {
                    gets++
                    throw httpError(404)
                }
            )
        )
        assertEquals(0, guardadoFlag("visita-001"))
        assertNull(fila().CONFIRMADA_EN)
        assertEquals("un eco ajeno se verifica por GET", 1, gets)
    }

    @Test
    fun v3_200_sin_cuerpo_no_suelta() = runTest {
        seed(pendingVisit())
        assertEquals(
            Result.retry(),
            buildAndRunWorker(
                api = fakeV2Api { _, _ -> visitaDto(id = null) },
                modo = V3,
                getVisita = { throw httpError(404) }
            )
        )
        assertEquals(0, guardadoFlag("visita-001"))
        assertNull(fila().CONFIRMADA_EN)
    }

    @Test
    fun `un eco ajeno con GET completo se confirma por existencia`() = runTest {
        seed(pendingVisit())
        assertEquals(
            Result.success(),
            buildAndRunWorker(
                api = fakeV2Api { _, _ -> visitaDto(id = "otra") },
                modo = V3,
                getVisita = { visitaDetalle(id = it, imagenIds = emptyList()) }
            )
        )
        assertNotNull(fila().CONFIRMADA_EN)
    }

    // ─── 2xx con fotos (opción A del dueño, E-VIS-022) ────────────────────────

    @Test
    fun `E-VIS-022 2xx con eco y fotos exige GET completo y solo entonces estampa y borra`() =
        runTest {
            seed(pendingVisit())
            val f1 = sembrarImagen(imagenId = "f1")
            val pedidos = mutableListOf<String>()
            val r = buildAndRunWorker(
                api = multipartConEco(),
                modo = V3,
                getVisita = {
                    pedidos += it
                    visitaDetalle(id = it, imagenIds = listOf("f1"))
                }
            )
            assertEquals(Result.success(), r)
            assertEquals(listOf("visita-001"), pedidos)
            assertNotNull(fila().CONFIRMADA_EN)
            assertEquals("2026-10-17T18:00:00Z", subidaEn("f1"))
            assertFalse("el archivo se borra tras CONFIRMADA", f1.exists())
        }

    /**
     * Detecta que el worker pase un conjunto VACÍO (o equivocado) de fotos
     * pendientes a `salidaTras2xx`: con `emptySet` bastaría el eco y la visita
     * se confirmaría con la foto ausente del servidor.
     */
    @Test
    fun `E-VIS-022 2xx con eco y GET sin la foto no suelta ni borra la foto`() = runTest {
        seed(pendingVisit())
        val f1 = sembrarImagen(imagenId = "f1")
        val r = buildAndRunWorker(
            api = multipartConEco(),
            modo = V3,
            getVisita = { visitaDetalle(id = it, imagenIds = emptyList()) }
        )
        assertEquals(Result.retry(), r)
        val v = fila()
        assertEquals(0, v.GUARDADO_EN_MICROSIP)
        assertNull(v.CONFIRMADA_EN)
        assertNull(v.RESGUARDADA_EN)
        assertNull(subidaEn("f1"))
        assertTrue("la foto local se conserva", f1.exists())
    }

    @Test
    fun `E-VIS-022 2xx con eco y dos fotos con GET que lista solo una no suelta`() = runTest {
        seed(pendingVisit())
        val f1 = sembrarImagen(imagenId = "f1", orden = 0)
        val f2 = sembrarImagen(imagenId = "f2", orden = 1)
        assertEquals(
            Result.retry(),
            buildAndRunWorker(
                api = multipartConEco(),
                modo = V3,
                getVisita = { visitaDetalle(id = it, imagenIds = listOf("f1")) }
            )
        )
        assertNull(fila().CONFIRMADA_EN)
        assertTrue(f1.exists() && f2.exists())
        assertNull(subidaEn("f1"))
    }

    @Test
    fun `E-VIS-022 2xx con eco y fotos con servidor viejo sin imagen_ids no suelta`() = runTest {
        seed(pendingVisit())
        val f1 = sembrarImagen(imagenId = "f1")
        assertEquals(
            Result.retry(),
            buildAndRunWorker(
                api = multipartConEco(),
                modo = V3,
                getVisita = { visitaDetalle(id = it, imagenIds = null) }
            )
        )
        assertNull(fila().CONFIRMADA_EN)
        assertTrue(f1.exists())
    }

    @Test
    fun `E-VIS-022 2xx con eco y fotos con GET que falla no suelta`() = runTest {
        val fallas: List<suspend (String) -> VisitaDetalleDTO> = listOf(
            { throw httpError(503) },
            { throw httpError(404) },
            { throw IOException("red") },
            { throw IllegalStateException("json roto") },
            { visitaDetalle(id = "otra", imagenIds = listOf("f1")) },
            { visitaDetalle(id = null, imagenIds = listOf("f1")) }
        )
        fallas.forEachIndexed { i, falla ->
            db.clearAllTables()
            seed(pendingVisit())
            val f1 = sembrarImagen(imagenId = "f1")
            assertEquals(
                "falla $i",
                Result.retry(),
                buildAndRunWorker(api = multipartConEco(), modo = V3, getVisita = falla)
            )
            assertNull("falla $i", fila().CONFIRMADA_EN)
            assertTrue("falla $i", f1.exists())
            assertNull("falla $i", subidaEn("f1"))
        }
    }

    /**
     * Las fotos que se exigen en `imagen_ids` son las que VIAJARON. Una foto
     * omitida (sin archivo local) no puede llegar nunca al servidor: exigirla
     * dejaría la visita reintentando para siempre. Se queda pendiente, como en
     * el envío de hoy, y no bloquea la visita.
     */
    @Test
    fun `una foto omitida no bloquea la confirmacion y se queda pendiente`() = runTest {
        seed(pendingVisit())
        val f1 = sembrarImagen(imagenId = "f1", orden = 0)
        sembrarImagenSinArchivo(imagenId = "f2", orden = 1)
        val r = buildAndRunWorker(
            api = multipartConEco(),
            modo = V3,
            getVisita = { visitaDetalle(id = it, imagenIds = listOf("f1")) }
        )
        assertEquals(Result.success(), r)
        assertNotNull(fila().CONFIRMADA_EN)
        assertNotNull(subidaEn("f1"))
        assertFalse(f1.exists())
        assertNull("la omitida sigue pendiente", subidaEn("f2"))
    }

    // ─── errores HTTP ─────────────────────────────────────────────────────────

    @Test
    fun `5xx sostenido 12 h no suelta`() = runTest {
        seed(pendingVisit())
        // 12 h con el backoff por omisión ≈ intento 12 (E-APP-071); se prueba hasta el 30 sin tope.
        (0..30).forEach { intento ->
            val r = buildAndRunWorker(
                api = fakeV2Api { _, _ -> throw httpError(503) },
                modo = V3,
                runAttemptCount = intento,
                getVisita = { throw httpError(503) }
            )
            assertEquals("intento $intento", Result.retry(), r)
        }
        assertEquals(0, guardadoFlag("visita-001"))
    }

    @Test
    fun `401 sostenido no termina en failure sin tope`() = runTest {
        seed(pendingVisit())
        (0..30).forEach { intento ->
            val r = buildAndRunWorker(
                api = fakeV2Api { _, _ -> throw httpError(401) },
                modo = V3,
                runAttemptCount = intento,
                getVisita = { throw httpError(401) }
            )
            assertEquals("intento $intento", Result.retry(), r)
        }
        assertEquals(0, guardadoFlag("visita-001"))
    }

    @Test
    fun `500 seguido de GET 200 completo suelta una vez con RECONCILED_VIA_GET`() = runTest {
        seed(pendingVisit())
        var gets = 0
        val r = buildAndRunWorker(
            api = fakeV2Api { _, _ -> throw httpError(500) },
            modo = V3,
            getVisita = {
                gets++
                visitaDetalle(id = it, imagenIds = emptyList())
            }
        )
        assertEquals(Result.success(), r)
        assertEquals(1, gets)
        assertNotNull(fila().CONFIRMADA_EN)
        assertNull(fila().RESGUARDADA_EN)
    }

    @Test
    fun `409 nunca suelta sin existencia completa`() = runTest {
        seed(pendingVisit())
        listOf(conCabecera(409), httpError(409)).forEach { error ->
            assertEquals(
                Result.retry(),
                buildAndRunWorker(api = fakeV2Api { _, _ -> throw error }, modo = V3, getVisita = {
                    throw httpError(404)
                })
            )
        }
        assertEquals(0, guardadoFlag("visita-001"))
    }

    @Test
    fun `422 con X-Intent-Captured se resguarda y no se confirma`() = runTest {
        seed(pendingVisit())
        assertEquals(
            Result.success(),
            buildAndRunWorker(
                api = fakeV2Api { _, _ ->
                    throw conCabecera(
                        422
                    )
                },
                modo = V3,
                getVisita = {
                    throw httpError(404)
                }
            )
        )
        val v = fila()
        assertEquals(1, v.GUARDADO_EN_MICROSIP)
        assertNull(v.CONFIRMADA_EN)
        assertEquals("2026-10-17T18:00:00Z", v.RESGUARDADA_EN)
    }

    @Test
    fun `422 sin cabecera reintenta (el pool trabado pierde la captura)`() = runTest {
        seed(pendingVisit())
        assertEquals(
            Result.retry(),
            buildAndRunWorker(
                api = fakeV2Api { _, _ ->
                    throw httpError(
                        422
                    )
                },
                modo = V3,
                getVisita = {
                    throw httpError(404)
                }
            )
        )
        assertEquals(0, guardadoFlag("visita-001"))
    }

    @Test
    fun `E-VIS-020 413 visita_body_too_large con cabecera se resguarda y sin cabecera reintenta`() =
        runTest {
            val cuerpo = """{"status":413,"code":"visita_body_too_large"}"""
            seed(pendingVisit())
            assertEquals(
                Result.retry(),
                buildAndRunWorker(
                    api = fakeV2Api { _, _ ->
                        throw httpError(
                            413,
                            """{"status":413,"code":"visita_body_too_large_to_capture"}"""
                        )
                    },
                    modo = V3,
                    getVisita = { throw httpError(404) }
                )
            )
            assertEquals(0, guardadoFlag("visita-001"))
            assertEquals(
                Result.success(),
                buildAndRunWorker(
                    api = fakeV2Api { _, _ ->
                        throw conCabecera(
                            413,
                            cuerpo
                        )
                    },
                    modo = V3,
                    getVisita = {
                        throw httpError(404)
                    }
                )
            )
            assertNotNull(fila().RESGUARDADA_EN)
            assertNull(fila().CONFIRMADA_EN)
        }

    @Test
    fun `existencia sin la foto pendiente no suelta`() = runTest {
        seed(pendingVisit())
        val f1 = sembrarImagen(visitaId = "visita-001", imagenId = "f1")
        assertEquals(
            Result.retry(),
            buildAndRunWorker(
                api = fakeV2Api(
                    multipart = { _, _, _ -> throw httpError(500) }
                ) { _, _ -> throw AssertionError("multipart") },
                modo = V3,
                getVisita = { visitaDetalle(id = it, imagenIds = emptyList()) }
            )
        )
        assertEquals(0, guardadoFlag("visita-001"))
        assertTrue(f1.exists())
        assertNull(subidaEn("f1"))
    }

    /** Revisor de la Tarea 9: con existencia INCOMPLETA decide `classifyUpload`, y las fotos no se tocan. */
    @Test
    fun `5xx con cabecera y existencia incompleta se resguarda sin borrar fotos`() = runTest {
        seed(pendingVisit())
        val f1 = sembrarImagen(imagenId = "f1")
        assertEquals(UploadDecision.RELEASE, classifyUpload(503, captureConfirmed = true))
        assertEquals(
            Result.success(),
            buildAndRunWorker(
                api = fakeV2Api(
                    multipart = { _, _, _ -> throw conCabecera(503) }
                ) { _, _ -> throw AssertionError("multipart") },
                modo = V3,
                getVisita = { visitaDetalle(id = it, imagenIds = emptyList()) }
            )
        )
        val v = fila()
        assertNotNull(v.RESGUARDADA_EN)
        assertNull(v.CONFIRMADA_EN)
        assertTrue("resguardada no borra la foto local", f1.exists())
        assertNull(subidaEn("f1"))
    }

    @Test
    fun `5xx sin cabecera y existencia incompleta reintenta sin borrar fotos`() = runTest {
        seed(pendingVisit())
        val f1 = sembrarImagen(imagenId = "f1")
        assertEquals(
            Result.retry(),
            buildAndRunWorker(
                api = fakeV2Api(
                    multipart = { _, _, _ -> throw httpError(503) }
                ) { _, _ -> throw AssertionError("multipart") },
                modo = V3,
                getVisita = { visitaDetalle(id = it, imagenIds = emptyList()) }
            )
        )
        assertEquals(0, guardadoFlag("visita-001"))
        assertTrue(f1.exists())
        assertNull(subidaEn("f1"))
    }

    /**
     * Existencia COMPLETA tras un error, con fotos: el servidor lista cada
     * foto pendiente con fila Y archivo (E-VIS-022), así que se estampan, se
     * borran y la visita se confirma con `confirmarTrasSubida` en la misma
     * salida. Con `VisitDao.confirmar` la retención por foto actualizaría 0
     * filas y la visita se atascaría hasta el reencolado (hallazgo de la
     * simulación Go, Tarea 16) aunque el POST siga fallando.
     */
    @Test
    fun `existencia completa tras error con fotos estampa borra y confirma`() = runTest {
        seed(pendingVisit())
        val f1 = sembrarImagen(imagenId = "f1", orden = 0)
        val f2 = sembrarImagen(imagenId = "f2", orden = 1)
        assertEquals(
            Result.success(),
            buildAndRunWorker(
                api = fakeV2Api(
                    multipart = { _, _, _ -> throw httpError(500) }
                ) { _, _ -> throw AssertionError("multipart") },
                modo = V3,
                getVisita = { visitaDetalle(id = it, imagenIds = listOf("f1", "f2")) }
            )
        )
        assertEquals("2026-10-17T18:00:00Z", fila().CONFIRMADA_EN)
        assertEquals("2026-10-17T18:00:00Z", subidaEn("f1"))
        assertEquals("2026-10-17T18:00:00Z", subidaEn("f2"))
        assertFalse(f1.exists() || f2.exists())
    }

    /**
     * Carrera con `RegistroDeVisitaAdapter.guardar`, que confirma la Visit en
     * su transacción y DESPUÉS inserta las fotos: un worker que corra en ese
     * hueco lee 0 fotos, manda JSON y recibe 2xx con eco. Antes de confirmar
     * se vuelve a leer: una foto pendiente que no estaba en la primera lectura
     * impide confirmar (no viajó y nadie la reenviaría).
     */
    @Test
    fun `una foto que aparece entre el envio y la marca impide confirmar`() = runTest {
        val getCompleto: suspend (
            String
        ) -> VisitaDetalleDTO = { visitaDetalle(id = it, imagenIds = emptyList()) }
        val casos: List<Pair<V2VisitsApi, (suspend (String) -> VisitaDetalleDTO)?>> = listOf(
            Pair(fakeV2Api { _, b -> visitaDto(id = b.id) }, null),
            Pair(fakeV2Api { _, _ -> throw httpError(500) }, getCompleto)
        )
        casos.forEachIndexed { i, (api, get) ->
            db.clearAllTables()
            seed(pendingVisit())
            var lecturas = 0
            var tardia: File? = null
            val conCarrera = object : VisitImageDao by db.visitImageDao() {
                override suspend fun getPendientesDe(visitaId: String): List<VisitImageEntity> {
                    lecturas++
                    if (lecturas == 2) tardia = sembrarImagen(imagenId = "tardia")
                    return db.visitImageDao().getPendientesDe(visitaId)
                }
            }
            val r = buildAndRunWorker(api = api, modo = V3, getVisita = get, imagenes = conCarrera)
            assertEquals("caso $i", Result.retry(), r)
            assertNull("caso $i", fila().CONFIRMADA_EN)
            assertEquals("caso $i", 0, fila().GUARDADO_EN_MICROSIP)
            assertNull("caso $i", subidaEn("tardia"))
            assertTrue("caso $i", tardia!!.exists())
        }
    }

    /** Si la relectura previa a confirmar falla, no se confirma: se reintenta. */
    @Test
    fun `una relectura de fotos que falla antes de confirmar reintenta`() = runTest {
        seed(pendingVisit())
        var lecturas = 0
        val rota = object : VisitImageDao by db.visitImageDao() {
            override suspend fun getPendientesDe(visitaId: String): List<VisitImageEntity> {
                lecturas++
                if (lecturas >= 2) throw IllegalStateException("no se pudo leer visita_imagenes")
                return db.visitImageDao().getPendientesDe(visitaId)
            }
        }
        assertEquals(
            Result.retry(),
            buildAndRunWorker(
                api = fakeV2Api { _, b -> visitaDto(id = b.id) },
                modo = V3,
                imagenes = rota
            )
        )
        assertEquals(2, lecturas)
        assertNull(fila().CONFIRMADA_EN)

        // Mismo hueco en el camino de existencia COMPLETA tras un error.
        lecturas = 0
        assertEquals(
            Result.retry(),
            buildAndRunWorker(
                api = fakeV2Api { _, _ -> throw httpError(500) },
                modo = V3,
                getVisita = { visitaDetalle(id = it, imagenIds = emptyList()) },
                imagenes = rota
            )
        )
        assertEquals(2, lecturas)
        assertNull(fila().CONFIRMADA_EN)
    }

    /**
     * El hueco que la relectura no cierra: con WAL la relectura no espera el
     * candado de escritura y un `insertAll` encolado entra entre ella y el
     * UPDATE. La condición va en el UPDATE mismo
     * (`confirmarTrasSubidaSiNoHayFotosNuevas`, SQL real de Room): aquí la
     * foto se inserta justo antes de ejecutarlo, en los dos caminos.
     */
    @Test
    fun `una foto que entra entre la relectura y el UPDATE impide confirmar`() = runTest {
        val getCompleto: suspend (
            String
        ) -> VisitaDetalleDTO = { visitaDetalle(id = it, imagenIds = emptyList()) }
        val casos: List<Pair<V2VisitsApi, (suspend (String) -> VisitaDetalleDTO)?>> = listOf(
            Pair(fakeV2Api { _, b -> visitaDto(id = b.id) }, null),
            Pair(fakeV2Api { _, _ -> throw httpError(500) }, getCompleto)
        )
        casos.forEachIndexed { i, (api, get) ->
            db.clearAllTables()
            seed(pendingVisit())
            var tardia: File? = null
            val conCarrera = object : com.example.msp_app.core.database.dao.visit.VisitDao by db.visitDao() {
                override suspend fun confirmarTrasSubidaSiNoHayFotosNuevas(
                    id: String,
                    ahora: String,
                    conocidas: List<String>
                ): Int {
                    tardia = sembrarImagen(imagenId = "tardia")
                    return db.visitDao().confirmarTrasSubidaSiNoHayFotosNuevas(id, ahora, conocidas)
                }
            }
            val r = runPendingVisitsWorker(
                context = context,
                imagenes = db.visitImageDao(),
                clock = FakeClock(ahora),
                modo = V3,
                api = conGet(api, get),
                visitDao = conCarrera
            )
            assertEquals("caso $i", Result.retry(), r)
            assertNull("caso $i", fila().CONFIRMADA_EN)
            assertEquals("caso $i", 0, fila().GUARDADO_EN_MICROSIP)
            assertNull("caso $i", subidaEn("tardia"))
            assertTrue("caso $i", tardia!!.exists())
        }
    }

    /**
     * Orden (simulación Go T16b, semilla
     * `foto-tardia-tras-relectura-servidor-pierde-archivo`): si la marca
     * condicional da 0 filas, la foto que SÍ viajó no se estampa ni se borra.
     * Si se borrara antes y el servidor perdiera ese archivo, el teléfono ya
     * no podría reenviarla. Se estampa y se borra solo después de la marca.
     */
    @Test
    fun `una marca que da 0 filas no estampa ni borra la foto que viajo`() = runTest {
        val casos: List<Pair<V2VisitsApi, String>> = listOf(
            Pair(multipartConEco(), "2xx"),
            Pair(
                fakeV2Api(
                    multipart = { _, _, _ -> throw httpError(500) }
                ) { _, _ -> throw AssertionError("multipart") },
                "GET COMPLETO tras 500"
            )
        )
        casos.forEach { (api, nombre) ->
            db.clearAllTables()
            seed(pendingVisit())
            val f1 = sembrarImagen(imagenId = "f1")
            val conCarrera = object : com.example.msp_app.core.database.dao.visit.VisitDao by db.visitDao() {
                override suspend fun confirmarTrasSubidaSiNoHayFotosNuevas(
                    id: String,
                    ahora: String,
                    conocidas: List<String>
                ): Int {
                    sembrarImagen(imagenId = "tardia", orden = 1)
                    return db.visitDao().confirmarTrasSubidaSiNoHayFotosNuevas(id, ahora, conocidas)
                }
            }
            val r = runPendingVisitsWorker(
                context = context,
                imagenes = db.visitImageDao(),
                clock = FakeClock(ahora),
                modo = V3,
                api = conGet(api) { visitaDetalle(id = it, imagenIds = listOf("f1")) },
                visitDao = conCarrera
            )
            assertEquals(nombre, Result.retry(), r)
            assertNull(nombre, fila().CONFIRMADA_EN)
            assertNull("$nombre: la foto que viajo sigue pendiente", subidaEn("f1"))
            assertTrue("$nombre: su archivo local se conserva", f1.exists())
        }
    }

    /**
     * La visita la confirmó OTRO camino (p. ej. la reconciliación) entre la
     * lectura inicial y la marca, y además entró una foto nueva: la marca
     * condicional da 0 filas. La visita ya está confirmada, así que es éxito,
     * pero NO se estampa ni se borra nada (decisión estricta, aceptada por el
     * revisor) y se anota `visita_confirmada_por_otro_camino_con_foto_nueva`
     * para que la foto atascada se vea antes del barrido.
     */
    @Test
    fun `confirmada por otro camino con foto nueva es exito sin estampar y se anota`() = runTest {
        seed(pendingVisit())
        val f1 = sembrarImagen(imagenId = "f1")
        var tardia: File? = null
        val otroCamino = object : com.example.msp_app.core.database.dao.visit.VisitDao by db.visitDao() {
            override suspend fun confirmarTrasSubidaSiNoHayFotosNuevas(
                id: String,
                ahora: String,
                conocidas: List<String>
            ): Int {
                db.visitDao().confirmarTrasSubida(id, "2026-10-17T17:59:00Z") // el otro camino
                tardia = sembrarImagen(imagenId = "tardia", orden = 1)
                return db.visitDao().confirmarTrasSubidaSiNoHayFotosNuevas(id, ahora, conocidas)
            }
        }
        val anotados = mutableListOf<com.example.msp_app.core.network.diagnostico.EnvioObservado>()
        val r = runPendingVisitsWorker(
            context = context,
            imagenes = db.visitImageDao(),
            clock = FakeClock(ahora),
            modo = V3,
            api = conGet(multipartConEco()) { visitaDetalle(id = it, imagenIds = listOf("f1")) },
            visitDao = otroCamino,
            diagnostico = { anotados += it }
        )
        assertEquals(Result.success(), r)
        assertEquals("la marca es la del otro camino", "2026-10-17T17:59:00Z", fila().CONFIRMADA_EN)
        assertNull("no se estampa la foto que viajo", subidaEn("f1"))
        assertTrue("ni se borra su archivo", f1.exists())
        assertNull(subidaEn("tardia"))
        assertTrue(tardia!!.exists())
        assertEquals(
            listOf("visita_confirmada_por_otro_camino_con_foto_nueva"),
            anotados.map { it.fallo?.codigo }
        )
    }

    /** Si la marca no queda escrita (0 filas) no hay éxito: se reintenta. */
    @Test
    fun `una confirmacion que no marca ninguna fila nunca es exito`() = runTest {
        seed(pendingVisit())
        val sinMarca = object : com.example.msp_app.core.database.dao.visit.VisitDao by db.visitDao() {
            override suspend fun confirmarTrasSubida(id: String, ahora: String): Int = 0
            override suspend fun confirmarTrasSubidaSiNoHayFotosNuevas(
                id: String,
                ahora: String,
                conocidas: List<String>
            ): Int = 0
            override suspend fun confirmar(ids: List<String>, ahora: String): Int = 0
            override suspend fun resguardar(id: String, ahora: String): Int = 0
        }
        val getCompleto: suspend (
            String
        ) -> VisitaDetalleDTO = { visitaDetalle(id = it, imagenIds = emptyList()) }
        val get404: suspend (String) -> VisitaDetalleDTO = { throw httpError(404) }
        val casos: List<Pair<V2VisitsApi, (suspend (String) -> VisitaDetalleDTO)?>> = listOf(
            Pair(fakeV2Api { _, b -> visitaDto(id = b.id) }, null),
            Pair(fakeV2Api { _, _ -> throw httpError(500) }, getCompleto),
            Pair(fakeV2Api { _, _ -> throw conCabecera(422) }, get404)
        )
        casos.forEachIndexed { i, (api, get) ->
            val r = runPendingVisitsWorker(
                context = context,
                imagenes = db.visitImageDao(),
                clock = FakeClock(ahora),
                modo = V3,
                api = conGet(api, get),
                visitDao = sinMarca
            )
            assertEquals("caso $i", Result.retry(), r)
        }
        assertEquals(0, guardadoFlag("visita-001"))
    }

    /**
     * Un solo lugar decide (spec §2.2-2): con la existencia en NO_EXISTE, la
     * salida del worker coincide con `classifyUpload` en cada código y con y
     * sin cabecera. RELEASE = resguardada; RETRY = reintenta.
     */
    @Test
    fun `tras un error sin existencia la salida es la de classifyUpload`() = runTest {
        val codigos =
            listOf(400, 401, 403, 404, 408, 409, 410, 413, 422, 425, 429, 500, 502, 503, 504, 599)
        codigos.forEach { code ->
            listOf(true, false).forEach { cabecera ->
                db.clearAllTables()
                seed(pendingVisit())
                val error = if (cabecera) conCabecera(code) else httpError(code)
                val r =
                    buildAndRunWorker(api = fakeV2Api { _, _ -> throw error }, modo = V3, getVisita = {
                        throw httpError(404)
                    })
                val v = fila()
                when (classifyUpload(code, captureConfirmed = cabecera)) {
                    UploadDecision.RELEASE -> {
                        assertEquals("$code/$cabecera", Result.success(), r)
                        assertNotNull("$code/$cabecera", v.RESGUARDADA_EN)
                    }
                    UploadDecision.RETRY -> {
                        assertEquals("$code/$cabecera", Result.retry(), r)
                        assertEquals("$code/$cabecera", 0, v.GUARDADO_EN_MICROSIP)
                    }
                }
                assertNull("$code/$cabecera nunca confirma sin existencia", v.CONFIRMADA_EN)
            }
        }
    }

    // ─── sin respuesta ────────────────────────────────────────────────────────

    @Test
    fun `IOException nunca suelta`() = runTest {
        seed(pendingVisit())
        assertEquals(
            Result.retry(),
            buildAndRunWorker(api = fakeV2Api { _, _ -> throw IOException("x") }, modo = V3)
        )
        assertEquals(0, guardadoFlag("visita-001"))
    }

    @Test
    fun `GET de existencia que lanza cualquier cosa es indeterminado`() = runTest {
        seed(pendingVisit())
        assertEquals(
            Result.retry(),
            buildAndRunWorker(
                api = fakeV2Api { _, _ -> throw httpError(500) },
                modo = V3,
                getVisita = { throw IllegalStateException("json roto") }
            )
        )
        assertEquals(0, guardadoFlag("visita-001"))
    }

    @Test
    fun `comprobantes ilegibles no mandan nada y reintentan`() = runTest {
        seed(pendingVisit())
        val rotas = object : VisitImageDao by db.visitImageDao() {
            override suspend fun getPendientesDe(visitaId: String): List<VisitImageEntity> =
                throw IllegalStateException("no se pudo leer visita_imagenes")
        }
        assertEquals(
            Result.retry(),
            buildAndRunWorker(
                api = fakeV2Api { _, _ -> throw AssertionError("no debe mandar") },
                modo = V3,
                imagenes = rotas
            )
        )
        assertEquals(0, guardadoFlag("visita-001"))
    }

    // ─── ya entregada ─────────────────────────────────────────────────────────

    @Test
    fun `ya confirmada no se vuelve a mandar`() = runTest {
        seed(pendingVisit())
        db.visitDao().confirmar(listOf("visita-001"), "t")
        assertEquals(
            Result.success(),
            buildAndRunWorker(
                api = fakeV2Api { _, _ -> throw AssertionError("no debe mandar") },
                modo = V3
            )
        )
        assertEquals("t", fila().CONFIRMADA_EN)
    }

    @Test
    fun `ya resguardada no se vuelve a mandar`() = runTest {
        seed(pendingVisit())
        db.visitDao().resguardar("visita-001", "t")
        assertEquals(
            Result.success(),
            buildAndRunWorker(
                api = fakeV2Api { _, _ -> throw AssertionError("no debe mandar") },
                modo = V3
            )
        )
        assertEquals("t", fila().RESGUARDADA_EN)
    }

    /** Control: una legada (`GUARDADO = 1` sin marca, Room v35) SÍ se vuelve a probar. */
    @Test
    fun `una legada marcada sin confirmar se vuelve a mandar y se confirma`() = runTest {
        seed(pendingVisit(guardado = 1))
        var posts = 0
        assertEquals(
            Result.success(),
            buildAndRunWorker(
                api = fakeV2Api { _, b ->
                    posts++
                    visitaDto(id = b.id)
                },
                modo = V3
            )
        )
        assertEquals(1, posts)
        assertNotNull(fila().CONFIRMADA_EN)
    }

    // ─── H1b: la marca no se corta a la mitad ────────────────────────────────

    private class Trabajo {
        lateinit var diferido: Deferred<Result>
    }

    private fun correrCancelable(
        api: (Trabajo) -> V2VisitsApi,
        imagenes: (Trabajo) -> VisitImageDao = { db.visitImageDao() }
    ): Trabajo {
        val trabajo = Trabajo()
        val worker = construirPendingVisitsWorker(
            context = context,
            imagenes = imagenes(trabajo),
            clock = FakeClock(ahora),
            modo = V3,
            api = api(trabajo)
        )
        runBlocking(Dispatchers.Default) {
            trabajo.diferido = async(start = CoroutineStart.LAZY) { worker.doWork() }
            try {
                trabajo.diferido.await()
            } catch (_: CancellationException) {
                // Esperado: el trabajo se canceló a media marca.
            }
        }
        return trabajo
    }

    @Test
    fun `H1b una cancelacion a media marca no deja fotos estampadas con la visita sin confirmar`() =
        runTest {
            seed(pendingVisit())
            val f1 = sembrarImagen(imagenId = "f1", orden = 0)
            val f2 = sembrarImagen(imagenId = "f2", orden = 1)
            val trabajo = correrCancelable(
                api = {
                    conGet(
                        multipartConEco()
                    ) { visitaDetalle(id = it, imagenIds = listOf("f1", "f2")) }
                },
                imagenes = { t ->
                    object : VisitImageDao by db.visitImageDao() {
                        override suspend fun marcarSubida(imagenId: String, subidaEn: String) {
                            db.visitImageDao().marcarSubida(imagenId, subidaEn)
                            t.diferido.cancel() // WorkManager detiene el trabajo justo aquí
                        }
                    }
                }
            )
            assertTrue(trabajo.diferido.isCancelled)
            assertNotNull(
                "la visita queda confirmada aunque cancelen a media marca",
                fila().CONFIRMADA_EN
            )
            assertNotNull(subidaEn("f1"))
            assertNotNull(subidaEn("f2"))
            assertFalse(f1.exists() || f2.exists())
        }

    @Test
    fun `H1b una cancelacion tras el GET no impide resguardar`() = runTest {
        seed(pendingVisit())
        val t = correrCancelable(
            api = { t ->
                conGet(fakeV2Api { _, _ -> throw conCabecera(422) }) {
                    t.diferido.cancel()
                    throw httpError(404)
                }
            }
        )
        assertTrue(t.diferido.isCancelled)
        assertNotNull(fila().RESGUARDADA_EN)
    }

    @Test
    fun `H1b una cancelacion tras el GET no impide confirmar por existencia`() = runTest {
        seed(pendingVisit())
        val t = correrCancelable(
            api = { t ->
                conGet(fakeV2Api { _, _ -> throw httpError(500) }) {
                    t.diferido.cancel()
                    visitaDetalle(id = it, imagenIds = emptyList())
                }
            }
        )
        assertTrue(t.diferido.isCancelled)
        assertNotNull(fila().CONFIRMADA_EN)
    }

    // ─── kill-switch ──────────────────────────────────────────────────────────

    @Test
    fun `modo V2_ACTUAL conserva el envio de hoy y V3 no suelta el mismo 500`() = runTest {
        seed(pendingVisit())
        val api500 = fakeV2Api { _, _ -> throw httpError(500) }
        assertEquals(
            Result.retry(),
            buildAndRunWorker(api = api500, modo = V3, runAttemptCount = 9, getVisita = {
                throw httpError(404)
            })
        )
        assertEquals(0, guardadoFlag("visita-001"))
        // Control positivo: el modo de reversa suelta en el intento 10 (E-VIS-001).
        assertEquals(
            Result.success(),
            buildAndRunWorker(api = api500, modo = V2_ACTUAL, runAttemptCount = 9)
        )
        assertEquals(1, guardadoFlag("visita-001"))
        assertNull(fila().CONFIRMADA_EN)
    }

    @Test
    fun `sin useV2 sigue el camino legacy en cualquier modo`() = runTest {
        var legacy = 0
        val legacyApi = object : VisitsApi {
            override suspend fun saveVisit(visit: Visit) {
                legacy++
            }
        }
        ModoEnvioVisitas.entries.forEachIndexed { i, modo ->
            // Una visita por modo: el legado ya no reenvia una entregada (E-APP-154).
            val id = "visita-00$i"
            seed(pendingVisit(id))
            runPendingVisitsWorker(
                context = context,
                imagenes = db.visitImageDao(),
                clock = FakeClock(ahora),
                modo = modo,
                visitId = id,
                api = fakeV2Api { _, _ -> throw AssertionError("v2 no") },
                legacyApi = legacyApi,
                useV2 = false
            )
        }
        assertEquals(2, legacy)
    }

    /** El constructor por omisión lee `FirestoreModoEnvioVisitas.actual` en cada corrida. */
    @Test
    fun `por omision el worker sigue al kill-switch`() = runTest {
        seed(pendingVisit())
        fun correrPorOmision(): Result {
            val worker = androidx.work.testing.TestListenableWorkerBuilder<PendingVisitsWorker>(
                context,
                androidx.work.Data.Builder().putString("visit_id", "visita-001").build()
            ).setRunAttemptCount(9).setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters
                ) = PendingVisitsWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    visitsStore = VisitsLocalDataSource(appContext),
                    v2Api = conGet(
                        fakeV2Api { _, _ -> throw httpError(500) }
                    ) { throw httpError(404) },
                    legacyApi = throwingLegacyApi(),
                    useV2 = true,
                    imagenes = db.visitImageDao(),
                    clock = FakeClock(ahora)
                )
            }).build() as PendingVisitsWorker
            return runBlocking { worker.doWork() }
        }
        try {
            FirestoreModoEnvioVisitas.aplicarParaPrueba(null)
            assertEquals(Result.retry(), correrPorOmision())
            assertEquals(0, guardadoFlag("visita-001"))
            FirestoreModoEnvioVisitas.aplicarParaPrueba(mapOf("modo" to "v2_actual"))
            assertEquals(Result.success(), correrPorOmision())
            assertEquals(1, guardadoFlag("visita-001"))
        } finally {
            FirestoreModoEnvioVisitas.aplicarParaPrueba(null)
        }
    }
}
