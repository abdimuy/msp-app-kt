package com.example.msp_app.core.sync.visitas

import com.example.msp_app.core.database.dao.visit.IdYUpdatedAt
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.database.entities.VisitaZonaEntity
import com.example.msp_app.core.database.entities.VisitasSyncStateEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.api.services.visits.ByIdsVisitasZonaResponse
import com.example.msp_app.data.api.services.visits.DigestVisitasZonaResponse
import com.example.msp_app.data.api.services.visits.IdsVisitasZonaResponse
import com.example.msp_app.data.api.services.visits.SyncVisitasZonaResponse
import com.example.msp_app.data.api.services.visits.VisitaZonaDto
import com.example.msp_app.data.api.services.visits.VisitasZonaSyncApi
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ZONA = 21
private const val DESDE = "2026-07-19T06:00:00Z"
private const val U1 = "2026-10-16T18:00:00.123456Z"
private const val U2 = "2026-10-16T19:00:00Z"
private const val ID_1 = "6f1c2a3b-0000-4000-8000-000000000001"
private const val ID_2 = "6f1c2a3b-0000-4000-8000-000000000002"
private const val ID_3 = "6f1c2a3b-0000-4000-8000-000000000003"
private const val ID_4 = "6f1c2a3b-0000-4000-8000-000000000004"

/**
 * E-APP-109 · Inventario de la agenda de la zona (plan visitas P2, Tarea 18;
 * spec §5.4.4-4 y §4.6): el teléfono compara su `visita_zona` con el digest
 * del servidor y, si difiere, rescata lo que le falta y borra lo que el
 * servidor ya no entrega. Las reglas que se cobran aquí:
 *
 * - digest igual = no se pide nada más;
 * - lo rescatado entra por `by-ids` sin mover la posición del sync;
 * - lo que se borra es SOLO de `visita_zona` (I8: `Visit` nunca);
 * - una consulta fallida no decide nada: no borra ni da por igual.
 */
class VisitasZonaReconcilerTest : RoomTestBase() {

    private val dao get() = db.visitaZonaDao()
    private val visitDao get() = db.visitDao()

    private fun reconciler(api: VisitasZonaSyncApi, limiteIds: Int = 5000) =
        VisitasZonaReconciler(api = api, dao = dao, limiteIds = limiteIds)

    /**
     * El hash del teléfono es el de `HashInventario` del servidor
     * (`internal/visitas/app/sync.go`): SHA-256 hex de `"<id>|<updated_at>"`
     * ordenadas por id en bytes y unidas con `\n`. El valor esperado lo da el
     * propio Go (`TestHashInventario_VectorDelTelefono`, msp-api) y coincide
     * con `shasum -a 256` de las mismas dos líneas.
     */
    @Test
    fun `E-APP-109 el hash local es el del servidor`() {
        assertEquals(
            "70e84806bbb23f9a1f2be8ae4da76dd1dffd658f90bb90aae551bf8b9dce7af4",
            VisitasZonaReconciler.hashInventario(
                // Desordenadas a propósito: el hash ordena.
                listOf(IdYUpdatedAt(ID_2, U2), IdYUpdatedAt(ID_1, U1))
            )
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            VisitasZonaReconciler.hashInventario(emptyList())
        )
    }

    @Test
    fun digestIgualNoPideIds() = runTest {
        sembrar(fila(ID_1, U1), fila(ID_2, U2))
        val api = FakeApi(servidor = listOf(dto(ID_1, U1), dto(ID_2, U2)))

        val r = reconciler(api).reconciliar(ZONA, DESDE)

        assertEquals(ResultadoInventario.Igual(revisadas = 2), r)
        assertEquals(listOf("digest"), api.llamadas)
    }

    @Test
    fun digestDistintoRescataFaltantes() = runTest {
        // Posición real del sync: el rescate no puede moverla ni borrarla.
        dao.aplicarPagina(ZONA, listOf(fila(ID_1, U1)), U1, ID_1)
        dao.guardarEpoch(ZONA, 3)
        val estadoAntes = dao.estado(ZONA)
        assertEquals(VisitasSyncStateEntity(ZONA, U1, ID_1, 3), estadoAntes)
        val api = FakeApi(servidor = listOf(dto(ID_1, U1), dto(ID_2, U2), dto(ID_3, U2)))

        val r = reconciler(api).reconciliar(ZONA, DESDE)

        assertEquals(
            ResultadoInventario.Reconciliado(revisadas = 1, rescatadas = 2, borradas = 0),
            r
        )
        assertEquals(listOf(ID_1, ID_2, ID_3), dao.deZona(ZONA).map { it.ID })
        assertEquals(listOf(listOf(ID_2, ID_3)), api.pedidosByIds)
        assertEquals("el rescate no mueve la posición del sync", estadoAntes, dao.estado(ZONA))
        assertEquals(
            "después del rescate el inventario local es el del servidor",
            api.hashServidor(),
            VisitasZonaReconciler.hashInventario(dao.idsYUpdatedAt(ZONA, DESDE))
        )
    }

    @Test
    fun rescateEnTandasDeCien() = runTest {
        val servidor = (1..250).map { dto("6f1c2a3b-0000-4000-8000-%012d".format(it), U1) }
        val api = FakeApi(servidor = servidor)

        reconciler(api).reconciliar(ZONA, DESDE)

        assertEquals(listOf(100, 100, 50), api.pedidosByIds.map { it.size })
        assertEquals(250, dao.deZona(ZONA).size)
    }

    @Test
    fun idsPaginadosHastaElFinal() = runTest {
        val api = FakeApi(servidor = listOf(dto(ID_1, U1), dto(ID_2, U1), dto(ID_3, U1)))

        reconciler(api, limiteIds = 2).reconciliar(ZONA, DESDE)

        assertEquals(listOf(null, ID_2), api.afterDeIds)
        assertEquals(listOf(ID_1, ID_2, ID_3), dao.deZona(ZONA).map { it.ID })
    }

    /** I8: lo que el servidor ya no entrega sale de `visita_zona` y `Visit` queda igual. */
    @Test
    fun idQueElServidorYaNoListaSeBorraSoloDeVisitaZona() = runTest {
        sembrar(fila(ID_1, U1), fila(ID_4, U1))
        val pendiente = visitaPendiente(ID_4)
        visitDao.insertVisit(pendiente)
        val api = FakeApi(servidor = listOf(dto(ID_1, U1)))

        val r = reconciler(api).reconciliar(ZONA, DESDE)

        assertEquals(
            ResultadoInventario.Reconciliado(revisadas = 2, rescatadas = 0, borradas = 1),
            r
        )
        assertEquals(listOf(ID_1), dao.deZona(ZONA).map { it.ID })
        assertEquals("Visit no se toca", pendiente, visitDao.findVisitById(ID_4))
    }

    @Test
    fun loAnteriorALaVentanaSePodaSinPreguntar() = runTest {
        sembrar(fila(ID_1, U1, fecha = "2026-07-19T05:59:59Z"), fila(ID_2, U1))
        val api = FakeApi(servidor = listOf(dto(ID_2, U1)))

        val r = reconciler(api).reconciliar(ZONA, DESDE)

        assertEquals(ResultadoInventario.Igual(revisadas = 1), r)
        assertEquals(listOf(ID_2), dao.deZona(ZONA).map { it.ID })
    }

    @Test
    fun consultaFallidaNoBorraNada() = runTest {
        for (falla in listOf("digest", "ids", "byIds")) {
            dao.borrar(dao.deZona(ZONA).map { it.ID })
            sembrar(fila(ID_1, U1), fila(ID_4, U1))
            val api = FakeApi(servidor = listOf(dto(ID_1, U1), dto(ID_2, U1)), falla = falla)

            val r = reconciler(api).reconciliar(ZONA, DESDE)

            assertTrue("$falla: $r", r is ResultadoInventario.Fallido)
            assertTrue("$falla: control, sí se consultó", falla in api.llamadas)
            assertTrue(
                "$falla: nada se borra con una consulta fallida",
                dao.deZona(ZONA).map { it.ID }.containsAll(listOf(ID_1, ID_4))
            )
        }
    }

    @Test
    fun respuestaSinHashNoDecideNada() = runTest {
        sembrar(fila(ID_4, U1))
        val api = FakeApi(servidor = emptyList(), digestSinHash = true)

        val r = reconciler(api).reconciliar(ZONA, DESDE)

        assertTrue(r is ResultadoInventario.Fallido)
        assertEquals(listOf("digest"), api.llamadas)
        assertEquals(listOf(ID_4), dao.deZona(ZONA).map { it.ID })
    }

    /** Un servidor que dice `has_more` sin dar ids no puede dejar al teléfono en un bucle. */
    @Test
    fun idsConHasMoreSinIdsNoEntraEnBucle() = runTest {
        sembrar(fila(ID_4, U1))
        val api = FakeApi(servidor = listOf(dto(ID_1, U1)), idsVaciosConHasMore = true)

        val r = reconciler(api).reconciliar(ZONA, DESDE)

        assertTrue(r is ResultadoInventario.Fallido)
        assertEquals(listOf(ID_4), dao.deZona(ZONA).map { it.ID })
    }

    @Test
    fun todosLosCanalesRecibenElMismoDesde() = runTest {
        sembrar(fila(ID_4, U1))
        val api = FakeApi(servidor = listOf(dto(ID_1, U1)))

        reconciler(api).reconciliar(ZONA, DESDE)

        assertEquals(setOf(DESDE), api.desdes.toSet())
        assertEquals(listOf("digest", "ids", "byIds"), api.llamadas)
    }

    // ─── apoyo ──────────────────────────────────────────────────────────────

    private suspend fun sembrar(vararg filas: VisitaZonaEntity) {
        dao.rescatar(filas.toList())
    }

    private fun fila(id: String, updatedAt: String, fecha: String = "2026-10-15T18:00:00Z") =
        VisitaZonaEntity(
            ID = id,
            CLIENTE_ID = 11486,
            COBRADOR_ID = 2991009,
            COBRADOR = "Mendoza Torres, Ana",
            FECHA = fecha,
            TIPO_VISITA = "PROMESA",
            ZONA_CLIENTE_ID = ZONA,
            IMPTE_DOCTO_CC_ID = 512345,
            NOTA = null,
            PROMESA_VENTA_ID = 512345,
            PROMESA_FECHA = "2026-10-20",
            PROMESA_MONTO_CENTAVOS = 20000L,
            CITA_FECHA = null,
            CITA_HORA = null,
            UPDATED_AT = updatedAt
        )

    private fun dto(id: String, updatedAt: String) = VisitaZonaDto(
        id = id,
        cliente_id = 11486,
        cobrador_id = 2991009,
        cobrador = "Mendoza Torres, Ana",
        tipo_visita = "PROMESA",
        zona_cliente_id = ZONA,
        impte_docto_cc_id = 512345,
        fecha = "2026-10-15T18:00:00Z",
        promesa_venta_id = 512345,
        promesa_fecha = "2026-10-20",
        promesa_monto_centavos = 20000L,
        updated_at = updatedAt
    )

    private fun visitaPendiente(id: String) = VisitEntity(
        ID = id,
        CLIENTE_ID = 11486,
        COBRADOR = "Mendoza Torres, Ana",
        COBRADOR_ID = 2991009,
        FECHA = "2026-10-15T18:00:00Z",
        FORMA_COBRO_ID = 157,
        LAT = 19.4326077,
        LNG = -99.133208,
        NOTA = "Lo capturado en el teléfono",
        TIPO_VISITA = "PROMESA",
        ZONA_CLIENTE_ID = ZONA,
        IMPTE_DOCTO_CC_ID = 512345,
        GUARDADO_EN_MICROSIP = 0,
        PROMESA_VENTA_ID = 512345,
        PROMESA_FECHA = "2026-10-20",
        PROMESA_MONTO_CENTAVOS = 20000L,
        CITA_FECHA = null,
        CITA_HORA = null
    )

    /**
     * Servidor falso con la semántica de `internal/visitas/app/sync.go`: los
     * tres canales leen el MISMO conjunto ([servidor]); `ids` ordena por id en
     * bytes y pagina por `after`; `by-ids` devuelve solo lo pedido que está.
     */
    private class FakeApi(
        servidor: List<VisitaZonaDto>,
        val falla: String? = null,
        val digestSinHash: Boolean = false,
        val idsVaciosConHasMore: Boolean = false
    ) : VisitasZonaSyncApi {
        private val filas = servidor.sortedBy { it.id }
        val llamadas = mutableListOf<String>()
        val desdes = mutableListOf<String>()
        val afterDeIds = mutableListOf<String?>()
        val pedidosByIds = mutableListOf<List<String>>()

        fun hashServidor(): String = VisitasZonaReconciler.hashInventario(
            filas.map { IdYUpdatedAt(it.id!!, it.updated_at!!) }
        )

        private fun anotar(canal: String, desde: String) {
            if (llamadas.lastOrNull() != canal) llamadas += canal
            desdes += desde
            if (falla == canal) throw IOException("$canal: red caída")
        }

        override suspend fun sync(
            zona: Int,
            desde: String,
            cursor: String?,
            afterId: String?,
            limit: Int?
        ): SyncVisitasZonaResponse = error("el inventario no usa el sync")

        override suspend fun digest(zona: Int, desde: String): DigestVisitasZonaResponse {
            anotar("digest", desde)
            return DigestVisitasZonaResponse(
                count = filas.size,
                hash = if (digestSinHash) null else hashServidor(),
                max_updated_at = filas.maxOfOrNull { it.updated_at!! }
            )
        }

        override suspend fun ids(
            zona: Int,
            desde: String,
            after: String?,
            limit: Int?
        ): IdsVisitasZonaResponse {
            anotar("ids", desde)
            afterDeIds += after
            if (idsVaciosConHasMore) {
                return IdsVisitasZonaResponse(
                    ids = emptyList(),
                    has_more = true
                )
            }
            val resto = filas.map { it.id!! }.filter { after == null || it > after }
            val pagina = resto.take(limit ?: resto.size)
            return IdsVisitasZonaResponse(ids = pagina, has_more = resto.size > pagina.size)
        }

        override suspend fun byIds(
            zona: Int,
            desde: String,
            ids: String
        ): ByIdsVisitasZonaResponse {
            anotar("byIds", desde)
            val pedidos = ids.split(",")
            pedidosByIds += pedidos
            return ByIdsVisitasZonaResponse(items = filas.filter { it.id in pedidos })
        }
    }
}
