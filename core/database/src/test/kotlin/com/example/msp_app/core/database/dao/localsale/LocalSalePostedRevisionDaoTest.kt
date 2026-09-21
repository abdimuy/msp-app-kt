package com.example.msp_app.core.database.dao.localsale

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val SALE_ID = "sale-ancla-001"
private const val OTRA_VENTA_ID = "sale-ancla-002"

/**
 * El ANCLA del cuerpo posteado (`REVISION_POSTEADA`) y lo que
 * `markSentAndCloseEdit` decide con ella — Task 6b del plan "Corregir una
 * venta antes de que suba".
 *
 * El hueco que esta columna cierra: antes, la divergencia se medía contra el
 * snapshot de la corrida EN CURSO, así que el camino del "2xx perdido"
 * quedaba invisible — el servidor recibe el cuerpo original y su respuesta se
 * pierde, el dueño corrige, el siguiente intento recibe `409`, la
 * reconciliación por `GET` marca `ENVIADO=1`, y dentro de ESA corrida nada
 * cambió: no había divergencia que marcar aunque el servidor se quedara con
 * lo viejo. Anclando al PRIMER cuerpo que se emitió, los dos caminos (2xx
 * directo y reconciliación por `GET`) ven la misma divergencia.
 *
 * Aquí se mide la sentencia sola; el escenario de punta a punta con el worker
 * real vive en `CorreccionIdempotenciaTest` (escenario D) y
 * `CorreccionDivergenciaTest` en `:app`.
 */
class LocalSalePostedRevisionDaoTest : RobolectricTestBase() {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Suppress("LongParameterList")
    private fun venta(
        saleId: String = SALE_ID,
        revision: Int = 0,
        revisionPosteada: Int? = null,
        correccionNoEnviada: Boolean = false
    ) = LocalSaleEntity(
        LOCAL_SALE_ID = saleId,
        NOMBRE_CLIENTE = "Rosa Elena Martinez Vazquez",
        FECHA_VENTA = "2026-09-18T15:30:00Z",
        LATITUD = 19.043415,
        LONGITUD = -98.198234,
        DIRECCION = "Privada de las Rosas 45",
        PARCIALIDAD = 850.0,
        ENGANCHE = 500.0,
        TELEFONO = "2221234567",
        FREC_PAGO = "SEMANAL",
        AVAL_O_RESPONSABLE = "Juan Martinez Vazquez",
        NOTA = null,
        DIA_COBRANZA = "MARTES",
        PRECIO_TOTAL = 6800.0,
        TIEMPO_A_CORTO_PLAZOMESES = 8,
        MONTO_A_CORTO_PLAZO = 6300.0,
        MONTO_DE_CONTADO = 5800.0,
        ENVIADO = false,
        REVISION = revision,
        CORRECCION_NO_ENVIADA = correccionNoEnviada,
        REVISION_POSTEADA = revisionPosteada
    )

    private suspend fun insert(sale: LocalSaleEntity) = database.localSaleDao().insertSale(sale)

    private suspend fun fila(saleId: String = SALE_ID) =
        database.localSaleDao().getSaleById(saleId)!!

    // ─── recordPostedRevisionIfAbsent ───────────────────────────────────

    /**
     * Instalación nueva: la venta recién capturada no tiene ancla. `NULL` es
     * "todavía no ha salido ningún POST", no "revisión cero" — por eso la
     * columna es nullable y no un `0` que se confundiría con el primer
     * cuerpo.
     */
    @Test
    fun `una venta recien insertada no trae ancla`() = runTest {
        insert(venta())

        assertNull(
            "sin POST emitido no hay cuerpo que anclar",
            fila().REVISION_POSTEADA
        )
    }

    @Test
    fun `el primer POST ancla la REVISION del cuerpo y devuelve 1`() = runTest {
        insert(venta(revision = 0))

        val filas = database.localSaleDao().recordPostedRevisionIfAbsent(SALE_ID, revision = 0)

        assertEquals("la primera vez SÍ escribe", 1, filas)
        assertEquals(0, fila().REVISION_POSTEADA)
    }

    /**
     * El corazón de la regla: **sólo la primera**. El segundo intento de la
     * misma venta llega con una `REVISION` más alta (el dueño corrigió entre
     * intentos) y NO debe pisar el ancla — si la pisara, la comparación de
     * `markSentAndCloseEdit` volvería a medirse contra el cuerpo de la
     * corrida en curso y el "2xx perdido" volvería a ser invisible.
     */
    @Test
    fun `un segundo POST no pisa el ancla del primero`() = runTest {
        insert(venta(revision = 0))
        database.localSaleDao().recordPostedRevisionIfAbsent(SALE_ID, revision = 0)

        val filas = database.localSaleDao().recordPostedRevisionIfAbsent(SALE_ID, revision = 1)

        assertEquals("ya había ancla: no escribe nada", 0, filas)
        assertEquals(
            "el ancla sigue siendo la del PRIMER cuerpo que viajó",
            0,
            fila().REVISION_POSTEADA
        )
    }

    @Test
    fun `anclar una venta no toca el ancla de otra`() = runTest {
        insert(venta(saleId = SALE_ID))
        insert(venta(saleId = OTRA_VENTA_ID))

        database.localSaleDao().recordPostedRevisionIfAbsent(SALE_ID, revision = 3)

        assertEquals(3, fila(SALE_ID).REVISION_POSTEADA)
        assertNull("la otra venta no emitió ningún POST", fila(OTRA_VENTA_ID).REVISION_POSTEADA)
    }

    // ─── markSentAndCloseEdit contra el ancla ───────────────────────────

    /**
     * EL CASO QUE ESTA TAREA ARREGLA, al nivel de la sentencia: el ancla dice
     * que el cuerpo que viajó fue el de `REVISION = 0`, la fila ya va en
     * `REVISION = 1` (el dueño corrigió después de aquel POST), y el
     * `revisionAtClaim` de la corrida actual también es 1 — porque el
     * snapshot de ESTA corrida se tomó con la corrección ya adentro. Con la
     * regla vieja (comparar contra `revisionAtClaim`) no se marcaba nada.
     * Contra el ancla, sí.
     */
    @Test
    fun `marca divergencia cuando la REVISION actual difiere del ancla aunque el snapshot coincida`() =
        runTest {
            insert(venta(revision = 1, revisionPosteada = 0))

            database.localSaleDao().markSentAndCloseEdit(SALE_ID, revisionAtClaim = 1)

            val sale = fila()
            assertTrue("el GET/2xx prueba que el servidor tiene la venta", sale.ENVIADO)
            assertTrue(
                "el servidor se quedó con el cuerpo de REVISION=0 y el teléfono enseña el de 1",
                sale.CORRECCION_NO_ENVIADA
            )
        }

    /**
     * Control negativo, sin el cual la marca no significaría nada: el ancla
     * coincide con la `REVISION` actual — el cuerpo que viajó ES el que el
     * teléfono enseña.
     */
    @Test
    fun `no marca divergencia cuando el ancla coincide con la REVISION actual`() = runTest {
        insert(venta(revision = 2, revisionPosteada = 2))

        database.localSaleDao().markSentAndCloseEdit(SALE_ID, revisionAtClaim = 2)

        val sale = fila()
        assertTrue(sale.ENVIADO)
        assertFalse(
            "lo que el servidor tiene es lo que el teléfono enseña: nada que señalar",
            sale.CORRECCION_NO_ENVIADA
        )
    }

    /**
     * Una corrección hecha ANTES del primer POST viaja en ese POST, así que
     * el ancla nace ya con ella: `REVISION = 1` y ancla `1`. No hay
     * divergencia. Es la prueba de que la marca no se dispara por el sólo
     * hecho de haber corregido.
     */
    @Test
    fun `corregir antes del primer POST no deja divergencia`() = runTest {
        insert(venta(revision = 1))
        database.localSaleDao().recordPostedRevisionIfAbsent(SALE_ID, revision = 1)

        database.localSaleDao().markSentAndCloseEdit(SALE_ID, revisionAtClaim = 1)

        assertFalse(
            "la corrección viajó en el primer POST: no hay nada que revisar",
            fila().CORRECCION_NO_ENVIADA
        )
    }

    /**
     * Respaldo: una fila SIN ancla (ningún camino del subidor la produce hoy
     * —se ancla justo antes del POST— pero la sentencia no puede depender de
     * eso) cae al `revisionAtClaim` de la corrida, que es exactamente la
     * regla anterior. Degradar a la regla vieja es seguro; degradar a "nunca
     * marca" no lo sería.
     */
    @Test
    fun `sin ancla la comparacion cae al snapshot de la corrida`() = runTest {
        insert(venta(revision = 1, revisionPosteada = null))

        database.localSaleDao().markSentAndCloseEdit(SALE_ID, revisionAtClaim = 0)

        assertTrue(
            "sin ancla se comporta como antes: 1 != 0 es divergencia",
            fila().CORRECCION_NO_ENVIADA
        )
    }

    /**
     * La marca no se borra jamás: un `markSent` posterior cuyo ancla SÍ
     * coincide no puede limpiar una divergencia ya señalada.
     */
    @Test
    fun `un markSent coincidente no borra una divergencia ya marcada`() = runTest {
        insert(venta(revision = 1, revisionPosteada = 1, correccionNoEnviada = true))

        database.localSaleDao().markSentAndCloseEdit(SALE_ID, revisionAtClaim = 1)

        assertTrue(
            "la evidencia de una divergencia previa se conserva",
            fila().CORRECCION_NO_ENVIADA
        )
    }
}
