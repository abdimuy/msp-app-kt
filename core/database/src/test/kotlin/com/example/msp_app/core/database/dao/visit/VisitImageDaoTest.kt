package com.example.msp_app.core.database.dao.visit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.database.entities.VisitImageEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Las consultas de `visita_imagenes` contra el Room real (Robolectric, DB en
 * memoria) — el SQL, no la intención.
 *
 * Lo que se prueba aquí y no río arriba: que el filtro sea por la columna que
 * dice su nombre. `getByVisitaId` filtra por **`VISITA_ID`** y `marcarSubida`
 * por **`ID`**; son dos columnas con dos significados (la visita y la imagen) y
 * este plan ya cazó siete defectos de la familia "un id donde iba el otro". Un
 * test con una sola fila no distinguiría un filtro del otro, así que todos los
 * de abajo siembran **dos visitas** y **dos imágenes**.
 */
class VisitImageDaoTest : RobolectricTestBase() {

    private lateinit var database: AppDatabase
    private lateinit var dao: VisitImageDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.visitImageDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getByVisitaId trae solo las de la visita pedida, en orden`() = runTest {
        dao.insertAll(
            listOf(
                imagen("IMG-B", visitaId = "VISITA-1", orden = 1),
                imagen("IMG-A", visitaId = "VISITA-1", orden = 0),
                imagen("IMG-C", visitaId = "VISITA-2", orden = 0)
            )
        )

        assertEquals(listOf("IMG-A", "IMG-B"), dao.getByVisitaId("VISITA-1").map { it.ID })
        assertEquals(listOf("IMG-C"), dao.getByVisitaId("VISITA-2").map { it.ID })
    }

    /**
     * El orden es el de captura, y es el que decide la posición `n` de `id_<n>`
     * en el multipart. `ORDEN` manda sobre el orden de inserción.
     */
    @Test
    fun `getPendientesDe respeta el ORDEN, no el de insercion`() = runTest {
        dao.insertAll(
            listOf(
                imagen("IMG-TERCERA", orden = 2),
                imagen("IMG-PRIMERA", orden = 0),
                imagen("IMG-SEGUNDA", orden = 1)
            )
        )

        assertEquals(
            listOf("IMG-PRIMERA", "IMG-SEGUNDA", "IMG-TERCERA"),
            dao.getPendientesDe("VISITA-1").map { it.ID }
        )
    }

    /** `SUBIDA_EN IS NULL` es la definición de "pendiente". */
    @Test
    fun `getPendientesDe deja fuera las ya subidas`() = runTest {
        dao.insertAll(
            listOf(
                imagen("IMG-A", orden = 0, subidaEn = "2026-09-01T10:00:00Z"),
                imagen("IMG-B", orden = 1)
            )
        )

        assertEquals(listOf("IMG-B"), dao.getPendientesDe("VISITA-1").map { it.ID })
    }

    /** Y filtra por la visita: las pendientes de otra visita no se cuelan. */
    @Test
    fun `getPendientesDe no trae las de otra visita`() = runTest {
        dao.insertAll(
            listOf(
                imagen("IMG-MIA", visitaId = "VISITA-1"),
                imagen("IMG-AJENA", visitaId = "VISITA-2")
            )
        )

        assertEquals(listOf("IMG-MIA"), dao.getPendientesDe("VISITA-1").map { it.ID })
    }

    /**
     * **`marcarSubida` filtra por el id de la IMAGEN.** Con las dos de la misma
     * visita, un filtro por `VISITA_ID` marcaría las dos — y declararía
     * entregada una foto que el servidor nunca recibió.
     */
    @Test
    fun `marcarSubida toca una sola fila, la de esa imagen`() = runTest {
        dao.insertAll(listOf(imagen("IMG-A", orden = 0), imagen("IMG-B", orden = 1)))

        dao.marcarSubida("IMG-A", "2026-09-04T18:00:00Z")

        val filas = dao.getByVisitaId("VISITA-1").associateBy { it.ID }
        assertEquals("2026-09-04T18:00:00Z", filas.getValue("IMG-A").SUBIDA_EN)
        assertNull(filas.getValue("IMG-B").SUBIDA_EN)
    }

    /**
     * La PK es el UUID del teléfono: reinsertar la misma captura no la duplica.
     * Es la mitad local de la idempotencia — la otra la pone el servidor con su
     * clave de storage determinista.
     */
    @Test
    fun `insertar dos veces la misma imagen no la duplica`() = runTest {
        dao.insertAll(listOf(imagen("IMG-A", orden = 0)))
        dao.insertAll(listOf(imagen("IMG-A", orden = 0)))

        assertEquals(1, dao.getByVisitaId("VISITA-1").size)
    }

    @Test
    fun `rutasVivas devuelve las rutas de todas las filas`() = runTest {
        dao.insertAll(listOf(imagen("IMG-A"), imagen("IMG-B", visitaId = "VISITA-2")))

        assertEquals(
            setOf("/files/comprobante_visita_IMG-A.jpg", "/files/comprobante_visita_IMG-B.jpg"),
            dao.rutasVivas().toSet()
        )
    }

    // --- El barrido: las DOS condiciones, cada una con su control ------------

    /**
     * Huérfana **y** vieja: su visita ya no está en `Visit` y se creó antes del
     * corte. Es la única que se barre.
     */
    @Test
    fun `huerfanasAnterioresA trae la vieja cuya visita ya no existe`() = runTest {
        sembrarVisita("VISITA-VIVA")
        dao.insertAll(
            listOf(
                imagen("IMG-HUERFANA", visitaId = "VISITA-IDA", creadaEn = "2026-08-01T10:00:00Z"),
                imagen(
                    "IMG-CON-VISITA",
                    visitaId = "VISITA-VIVA",
                    creadaEn = "2026-08-01T10:00:00Z"
                ),
                imagen("IMG-RECIENTE", visitaId = "VISITA-IDA", creadaEn = "2026-09-04T10:00:00Z")
            )
        )

        val barridas = dao.huerfanasAnterioresA("2026-08-28T18:00:00Z")

        assertEquals(listOf("IMG-HUERFANA"), barridas.map { it.ID })
    }

    /**
     * **El caso que el KDoc de `VisitImageEntity` obliga a proteger.**
     * `VisitDao.insertVisit` es `INSERT OR REPLACE`, que **borra la fila antes
     * de reinsertarla**: durante ese instante los comprobantes quedan sin padre
     * visible. Barrer sin mirar la antigüedad se llevaría las fotos de una
     * visita que el cobrador acaba de volver a guardar.
     */
    @Test
    fun `una huerfana reciente NO se barre`() = runTest {
        dao.insertAll(
            listOf(
                imagen("IMG-RECIEN", visitaId = "VISITA-IDA", creadaEn = "2026-09-04T10:00:00Z")
            )
        )

        assertTrue(dao.huerfanasAnterioresA("2026-08-28T18:00:00Z").isEmpty())
    }

    @Test
    fun `eliminar borra solo la fila de esa imagen`() = runTest {
        dao.insertAll(listOf(imagen("IMG-A", orden = 0), imagen("IMG-B", orden = 1)))

        dao.eliminar("IMG-A")

        assertEquals(listOf("IMG-B"), dao.getByVisitaId("VISITA-1").map { it.ID })
    }

    private suspend fun sembrarVisita(id: String) {
        database.visitDao().insertVisit(
            VisitEntity(
                ID = id,
                CLIENTE_ID = 11486,
                COBRADOR = "Ramirez Ortiz, Fernando",
                COBRADOR_ID = 200,
                FECHA = "2026-09-01T09:30:00Z",
                FORMA_COBRO_ID = 0,
                LAT = 0.0,
                LNG = 0.0,
                NOTA = "Visita de prueba",
                TIPO_VISITA = "SIN_PAGO",
                ZONA_CLIENTE_ID = 21552,
                IMPTE_DOCTO_CC_ID = 5000,
                GUARDADO_EN_MICROSIP = 0
            )
        )
    }

    private fun imagen(
        id: String,
        visitaId: String = "VISITA-1",
        orden: Int = 0,
        creadaEn: String = "2026-09-04T17:00:00Z",
        subidaEn: String? = null
    ) = VisitImageEntity(
        ID = id,
        VISITA_ID = visitaId,
        URI = "/files/comprobante_visita_$id.jpg",
        MIME = "image/jpeg",
        ORDEN = orden,
        CREADA_EN = creadaEn,
        SUBIDA_EN = subidaEn
    )
}
