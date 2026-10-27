package com.example.msp_app.core.sync.visitas

import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.visitas.envio.TelemetriaDeVisitas
import com.example.msp_app.data.api.services.visits.ByIdsVisitasZonaResponse
import com.example.msp_app.data.api.services.visits.DigestVisitasZonaResponse
import com.example.msp_app.data.api.services.visits.IdsVisitasZonaResponse
import com.example.msp_app.data.api.services.visits.SyncVisitasZonaResponse
import com.example.msp_app.data.api.services.visits.VisitaZonaDto
import com.example.msp_app.data.api.services.visits.VisitasZonaSyncApi
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val ZONA = 21
private const val X = "2026-10-16T03:00:00.000000Z"

/**
 * E-APP-110 · `visitas_sync.cursor_stalled` (plan visitas P2, Tarea 18;
 * `COBRANZA-SYNC.md` §11): la bajada está atorada cuando varias corridas
 * seguidas CIERRAN en la misma posición mientras siguen aplicando filas.
 *
 * La comparación es cierre contra cierre ENTRE corridas, en memoria del
 * detector, sin leer `visitas_sync_state`. Comparar arranque contra cierre
 * dentro de la corrida da falso negativo con el defecto D1 de pagos (cada
 * corrida arrancaba sin posición y cerraba en el mismo lugar): el control
 * positivo de abajo lo demuestra con la misma secuencia.
 */
class CursorAtoradoDeVisitasTest : RoomTestBase() {

    @After
    fun limpiar() {
        TelemetriaDeVisitas.sinkParaPrueba = null
    }

    /** D1 en visitas: cada corrida arranca sin posición y cierra en `(X, v-9)` con 500 filas. */
    private val corridasD1 = List(4) { PosicionDeCierre(X, "v-9") }

    @Test
    fun cursorStalledComparaCierreContraCierre() {
        val detector = DetectorDeCursorAtorado()

        val alertas = corridasD1.map { detector.registrar(ZONA, it, aplicadas = 500) }

        assertEquals(listOf(null, null, null, 3), alertas)

        // Control positivo: la comparación defectuosa (arranque contra cierre
        // dentro de la corrida) ve la misma secuencia como sana.
        val arranque = PosicionDeCierre(null, null)
        val alertasDelDefecto = corridasD1.map { cierre -> if (arranque == cierre) 1 else null }
        assertEquals(listOf<Int?>(null, null, null, null), alertasDelDefecto)
    }

    @Test
    fun alDiaNoEsAtorado() {
        val detector = DetectorDeCursorAtorado()

        val alertas =
            List(5) { detector.registrar(ZONA, PosicionDeCierre(X, "v-9"), aplicadas = 0) }

        assertEquals(List(5) { null }, alertas)
    }

    @Test
    fun avanzarReiniciaLaRacha() {
        val detector = DetectorDeCursorAtorado()
        val a = PosicionDeCierre(X, "v-1")
        val b = PosicionDeCierre(X, "v-2")

        val alertas = listOf(a, a, a, b, b, b).map { detector.registrar(ZONA, it, aplicadas = 10) }

        assertEquals(listOf(null, null, null, null, null, null), alertas)
        assertEquals(3, detector.registrar(ZONA, b, aplicadas = 10))
    }

    @Test
    fun cadaZonaLlevaSuPropiaMemoria() {
        val detector = DetectorDeCursorAtorado()
        val p = PosicionDeCierre(X, "v-9")

        repeat(3) { detector.registrar(ZONA, p, aplicadas = 1) }

        assertNull(detector.registrar(ZONA + 1, p, aplicadas = 1))
        assertEquals(3, detector.registrar(ZONA, p, aplicadas = 1))
    }

    /** La telemetría lleva solo la racha (un conteo), por la lista blanca de visitas. */
    @Test
    fun laAlertaSaleComoConteoPorLaListaBlanca() {
        val capturado = mutableListOf<Pair<String, Map<String, Any>>>()
        TelemetriaDeVisitas.sinkParaPrueba = { e, m -> capturado += e to m }
        val detector = DetectorDeCursorAtorado()

        repeat(4) {
            reportarBajada(
                detector,
                ZONA,
                ResultadoSync.Ok(1, 500, 0, false, false, cursor = X, afterId = "v-9")
            )
        }

        assertEquals(
            listOf(
                TelemetriaDeVisitas.EVENTO_SYNC to mapOf<String, Any>("visitas_sync.cursor_stalled" to 3)
            ),
            capturado
        )
    }

    /** El gestor reporta la posición en la que CERRÓ la corrida, que es la que guardó. */
    @Test
    fun elResultadoTraeLaPosicionDeCierre() = runTest {
        val api = UnaPagina(
            SyncVisitasZonaResponse(
                items = listOf(dto("v-1"), dto("v-2")),
                cursor = X,
                after_id = "v-2",
                has_more = false
            )
        )
        val mgr = VisitasZonaSyncManager(
            api = api,
            dao = db.visitaZonaDao(),
            zonaActual = { ZONA },
            killSwitch = { true },
            reloj = { Instant.parse("2026-10-20T05:30:00Z") }
        )

        val r = mgr.sincronizar() as ResultadoSync.Ok

        assertEquals(X to "v-2", r.cursor to r.afterId)
        val estado = db.visitaZonaDao().estado(ZONA)!!
        assertEquals(estado.CURSOR to estado.AFTER_ID, r.cursor to r.afterId)
    }

    private fun dto(id: String) = VisitaZonaDto(
        id = id,
        cliente_id = 11486,
        tipo_visita = "PROMESA",
        zona_cliente_id = ZONA,
        fecha = "2026-10-15T18:00:00Z",
        updated_at = X
    )

    private class UnaPagina(private val r: SyncVisitasZonaResponse) : VisitasZonaSyncApi {
        override suspend fun sync(
            zona: Int,
            desde: String,
            cursor: String?,
            afterId: String?,
            limit: Int?
        ): SyncVisitasZonaResponse = r

        override suspend fun digest(zona: Int, desde: String): DigestVisitasZonaResponse =
            error("no se usa")

        override suspend fun ids(
            zona: Int,
            desde: String,
            after: String?,
            limit: Int?
        ): IdsVisitasZonaResponse = error("no se usa")

        override suspend fun byIds(
            zona: Int,
            desde: String,
            ids: String
        ): ByIdsVisitasZonaResponse = error("no se usa")
    }
}
