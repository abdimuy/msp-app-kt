package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import com.example.msp_app.core.database.dao.visit.VisitImageDao
import com.example.msp_app.core.database.entities.VisitImageEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.data.api.services.visits.VisitaDetalleDTO
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * `marcarSubidas` borra el archivo local SOLO si `marcarSubida` terminó bien
 * (plan visitas P1, Tarea 17; E-APP-076).
 *
 * Mata un vivo de PIT que no era equivalente: quitar el `throwOnFailure` de la
 * reanudación de `imagenes.marcarSubida`. Con un DAO que responde sin
 * suspender, la excepción salía por el camino directo y ninguna prueba veía la
 * diferencia. Aquí el DAO hace `yield()` —como Room, que despacha a su
 * ejecutor— y LUEGO falla: con el mutante el error se traga, el archivo se
 * borra y `SUBIDA_EN` queda nulo, o sea una foto pendiente sin archivo que ya
 * no se puede reenviar.
 */
class PendingVisitsWorkerMarcarSubidasTest : RoomTestBase() {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val ahora: Instant = Instant.parse("2026-10-17T18:00:00Z")

    /** DAO real de fotos cuyo `marcarSubida` suspende de verdad y después falla. */
    private class MarcarSubidaQueFallaTrasSuspender(
        private val real: VisitImageDao
    ) : VisitImageDao by real {
        var llamadas = 0
        override suspend fun marcarSubida(imagenId: String, subidaEn: String) {
            llamadas++
            yield()
            throw IOException("disco lleno")
        }
    }

    @Test
    fun `E-APP-076 si marcarSubida falla tras suspender el archivo se conserva y la foto sigue pendiente`() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            VisitsLocalDataSource(context).saveVisit(pendingVisit())
            val archivo = File(carpeta.root, "foto-1.jpg").apply {
                writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00))
            }
            db.visitImageDao().insertAll(
                listOf(
                    VisitImageEntity(
                        ID = "foto-1",
                        VISITA_ID = "visita-001",
                        URI = archivo.absolutePath,
                        MIME = "image/jpeg",
                        ORDEN = 0,
                        CREADA_EN = "2026-10-17T17:00:00Z"
                    )
                )
            )
            val base = fakeV2Api(multipart = { key, _, _ -> visitaDto(id = key) }) { _, _ ->
                throw AssertionError("con foto viaja como multipart")
            }
            val api = object : V2VisitsApi by base {
                override suspend fun obtenerVisita(id: String): VisitaDetalleDTO =
                    visitaDetalle(id, listOf("foto-1"))
            }
            val imagenes = MarcarSubidaQueFallaTrasSuspender(db.visitImageDao())

            val r = runPendingVisitsWorker(
                context = context,
                imagenes = imagenes,
                clock = FakeClock(ahora),
                modo = ModoEnvioVisitas.V3,
                api = api
            )

            assertEquals(Result.success(), r)
            assertNotNull(
                "la visita sí se confirma: el servidor tiene la foto",
                db.visitDao().findVisitById("visita-001")!!.CONFIRMADA_EN
            )
            assertEquals("se intentó estampar", 1, imagenes.llamadas)
            assertNull(
                "la foto sigue sin SUBIDA_EN",
                db.visitImageDao().getByVisitaId("visita-001").single().SUBIDA_EN
            )
            assertTrue("el archivo NO se borra si no se pudo estampar", archivo.exists())
        }
}
