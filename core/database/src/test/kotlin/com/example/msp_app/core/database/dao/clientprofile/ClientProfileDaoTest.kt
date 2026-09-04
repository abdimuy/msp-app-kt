package com.example.msp_app.core.database.dao.clientprofile

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.ClientProfileEntity
import com.example.msp_app.core.database.entities.ClientProfileSignalEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `cliente_ficha` y `cliente_ficha_senales` contra el Room real (Robolectric,
 * DB en memoria) — **el SQL y la llave, no la intención**.
 *
 * Lo que se prueba aquí y no río arriba:
 *
 * - **La unicidad de la señal la pone la PK compuesta**, no una guarda en
 *   Kotlin. Por eso el duplicado se inserta con una `List` que trae la misma
 *   señal dos veces: un `Set` en la firma habría hecho pasar el test sin que la
 *   base tuviera nada que ver.
 * - **La nota y las señales son independientes** en los dos sentidos.
 * - **Guardar no borra el literal que nadie nombró**, que es lo que protege a
 *   una señal retirada del catálogo.
 *
 * Todas las pruebas siembran **dos clientes**: con uno solo, un `WHERE` que
 * olvidara el `CLIENTE_ID` pasaría igual.
 */
class ClientProfileDaoTest : RobolectricTestBase() {

    private lateinit var database: AppDatabase
    private lateinit var dao: ClientProfileDao

    private val victoria = 4021
    private val ramon = 4022
    private val ahora = "2026-09-01T18:00:00Z"

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.clientProfileDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun senal(cliente: Int, literal: String) =
        ClientProfileSignalEntity(CLIENTE_ID = cliente, SENAL = literal, ACTUALIZADA_EN = ahora)

    private fun nota(cliente: Int, texto: String?, cobrador: Int? = 77) = ClientProfileEntity(
        CLIENTE_ID = cliente,
        NOTA = texto,
        ACTUALIZADA_EN = ahora,
        COBRADOR_ID = cobrador
    )

    // --- El catálogo cerrado -------------------------------------------------

    @Test
    fun `la misma senal dos veces deja UNA fila - lo impide la llave, no Kotlin`() = runTest {
        dao.marcar(
            listOf(
                senal(victoria, "ESTA_EN_LA_NOCHE"),
                senal(victoria, "ESTA_EN_LA_NOCHE")
            )
        )
        assertEquals(1, dao.senalesDe(victoria).size)

        // Y otra vez, en una llamada aparte: la idempotencia no depende de que
        // los repetidos vengan juntos.
        dao.marcar(listOf(senal(victoria, "ESTA_EN_LA_NOCHE")))
        assertEquals(1, dao.senalesDe(victoria).size)
    }

    @Test
    fun `control positivo - dos senales DISTINTAS si dejan dos filas`() = runTest {
        dao.marcar(
            listOf(
                senal(victoria, "ESTA_EN_LA_NOCHE"),
                senal(victoria, "ATIENDE_OTRA_PERSONA")
            )
        )
        assertEquals(2, dao.senalesDe(victoria).size)
    }

    @Test
    fun `la misma senal en DOS clientes no se pisa - la llave es compuesta`() = runTest {
        dao.marcar(listOf(senal(victoria, "ESTA_EN_LA_NOCHE"), senal(ramon, "ESTA_EN_LA_NOCHE")))
        assertEquals(listOf("ESTA_EN_LA_NOCHE"), dao.senalesDe(victoria).map { it.SENAL })
        assertEquals(listOf("ESTA_EN_LA_NOCHE"), dao.senalesDe(ramon).map { it.SENAL })
    }

    @Test
    fun `desmarcar quita solo la senal de ese cliente`() = runTest {
        dao.marcar(
            listOf(
                senal(victoria, "ESTA_EN_LA_NOCHE"),
                senal(victoria, "ATIENDE_OTRA_PERSONA"),
                senal(ramon, "ESTA_EN_LA_NOCHE")
            )
        )
        dao.desmarcar(victoria, "ESTA_EN_LA_NOCHE")
        assertEquals(listOf("ATIENDE_OTRA_PERSONA"), dao.senalesDe(victoria).map { it.SENAL })
        assertEquals(listOf("ESTA_EN_LA_NOCHE"), dao.senalesDe(ramon).map { it.SENAL })
    }

    // --- La nota libre -------------------------------------------------------

    @Test
    fun `la nota se guarda y se lee tal cual, con sus saltos de linea`() = runTest {
        val texto = "trabaja de noche\natiende la suegra"
        dao.guardarNota(nota(victoria, texto))
        assertEquals(texto, dao.fichaDe(victoria)?.NOTA)
        assertNull("otro cliente no tiene ficha", dao.fichaDe(ramon))
    }

    @Test
    fun `nota vacia y nota nula NO son el mismo estado en la base`() = runTest {
        dao.guardarNota(nota(victoria, ""))
        dao.guardarNota(nota(ramon, null))
        assertEquals("", dao.fichaDe(victoria)?.NOTA)
        assertNull(dao.fichaDe(ramon)?.NOTA)
        assertNotNull("la fila existe aunque la nota sea nula", dao.fichaDe(ramon))
    }

    @Test
    fun `volver a guardar reescribe la MISMA fila - la nota es 0 a 1`() = runTest {
        dao.guardarNota(nota(victoria, "primera"))
        dao.guardarNota(nota(victoria, "segunda"))
        assertEquals("segunda", dao.fichaDe(victoria)?.NOTA)
    }

    @Test
    fun `el cobrador nulo se guarda como nulo y no como cero`() = runTest {
        dao.guardarNota(nota(victoria, "sin sesion", cobrador = null))
        assertNull(dao.fichaDe(victoria)?.COBRADOR_ID)
    }

    // --- Las dos mitades son independientes ---------------------------------

    @Test
    fun `borrar la nota NO borra las senales`() = runTest {
        dao.guardar(
            ficha = nota(victoria, "hay perro"),
            aMarcar = listOf(senal(victoria, "ESTA_EN_LA_NOCHE")),
            aDesmarcar = emptyList()
        )
        dao.guardar(
            ficha = nota(victoria, null),
            aMarcar = listOf(senal(victoria, "ESTA_EN_LA_NOCHE")),
            aDesmarcar = emptyList()
        )
        assertNull(dao.fichaDe(victoria)?.NOTA)
        assertEquals(listOf("ESTA_EN_LA_NOCHE"), dao.senalesDe(victoria).map { it.SENAL })
    }

    @Test
    fun `quitar todas las senales NO borra la nota`() = runTest {
        dao.guardar(
            ficha = nota(victoria, "hay perro"),
            aMarcar = listOf(senal(victoria, "ESTA_EN_LA_NOCHE")),
            aDesmarcar = emptyList()
        )
        dao.guardar(
            ficha = nota(victoria, "hay perro"),
            aMarcar = emptyList(),
            aDesmarcar = listOf("ESTA_EN_LA_NOCHE")
        )
        assertEquals("hay perro", dao.fichaDe(victoria)?.NOTA)
        assertTrue(dao.senalesDe(victoria).isEmpty())
    }

    /**
     * El defecto que este test existe para cazar: un `DELETE WHERE CLIENTE_ID`
     * seguido del insert habría evaporado `SE_MUDO_DE_CASA` —una señal de un
     * catálogo anterior, que el esquema conserva a propósito— sin que nadie se
     * enterara. Con la lista explícita, lo que nadie nombra sobrevive.
     */
    @Test
    fun `guardar NO borra un literal que este build ya no conoce`() = runTest {
        dao.marcar(listOf(senal(victoria, "SE_MUDO_DE_CASA")))
        dao.guardar(
            ficha = nota(victoria, "hay perro"),
            aMarcar = listOf(senal(victoria, "ESTA_EN_LA_NOCHE")),
            aDesmarcar = listOf(
                "ESTA_EN_LA_MANANA",
                "ESTA_EN_LA_TARDE",
                "ATIENDE_OTRA_PERSONA",
                "HAY_PERRO",
                "NO_IR_SOLO"
            )
        )
        assertEquals(
            listOf("ESTA_EN_LA_NOCHE", "SE_MUDO_DE_CASA"),
            dao.senalesDe(victoria).map { it.SENAL }
        )
    }

    @Test
    fun `guardar deja la nota y el conjunto exactos en una sola llamada`() = runTest {
        dao.guardar(
            ficha = nota(victoria, "hay perro"),
            aMarcar = listOf(
                senal(victoria, "ESTA_EN_LA_NOCHE"),
                senal(victoria, "ATIENDE_OTRA_PERSONA")
            ),
            aDesmarcar = listOf("ESTA_EN_LA_MANANA", "ESTA_EN_LA_TARDE")
        )
        assertEquals("hay perro", dao.fichaDe(victoria)?.NOTA)
        assertEquals(
            listOf("ATIENDE_OTRA_PERSONA", "ESTA_EN_LA_NOCHE"),
            dao.senalesDe(victoria).map { it.SENAL }
        )
    }

    @Test
    fun `el orden de las senales es el del literal, estable entre corridas`() = runTest {
        dao.marcar(
            listOf(
                senal(victoria, "ESTA_EN_LA_TARDE"),
                senal(victoria, "ATIENDE_OTRA_PERSONA"),
                senal(victoria, "ESTA_EN_LA_MANANA")
            )
        )
        assertEquals(
            listOf("ATIENDE_OTRA_PERSONA", "ESTA_EN_LA_MANANA", "ESTA_EN_LA_TARDE"),
            dao.senalesDe(victoria).map { it.SENAL }
        )
    }

    // --- Desde cuándo se sabe cada señal -------------------------------------

    /**
     * `ACTUALIZADA_EN` de una señal significa **desde cuándo se sabe eso**.
     * Reescribirla en cada guardado la volvía inútil justo para esa pregunta:
     * *"lo del perro, ¿es de esta semana o de hace dos años?"*.
     */
    @Test
    fun `una senal que ya estaba conserva su fecha al volver a guardar`() = runTest {
        val viejo = "2025-01-15T18:00:00Z"
        dao.guardar(
            ficha = ClientProfileEntity(victoria, "hay perro", viejo, 77),
            aMarcar = listOf(ClientProfileSignalEntity(victoria, "HAY_PERRO", viejo)),
            aDesmarcar = emptyList()
        )
        dao.guardar(
            ficha = ClientProfileEntity(victoria, "hay perro y muerde", ahora, 77),
            aMarcar = listOf(ClientProfileSignalEntity(victoria, "HAY_PERRO", ahora)),
            aDesmarcar = emptyList()
        )
        assertEquals(viejo, dao.senalesDe(victoria).single().ACTUALIZADA_EN)
        assertEquals(
            "la NOTA sí es 'última edición' y sí se reescribe",
            ahora,
            dao.fichaDe(victoria)?.ACTUALIZADA_EN
        )
    }

    @Test
    fun `control positivo - una senal NUEVA si estrena la fecha de hoy`() = runTest {
        val viejo = "2025-01-15T18:00:00Z"
        dao.guardar(
            ficha = ClientProfileEntity(victoria, null, viejo, 77),
            aMarcar = listOf(ClientProfileSignalEntity(victoria, "HAY_PERRO", viejo)),
            aDesmarcar = emptyList()
        )
        dao.guardar(
            ficha = ClientProfileEntity(victoria, null, ahora, 77),
            aMarcar = listOf(
                ClientProfileSignalEntity(victoria, "HAY_PERRO", ahora),
                ClientProfileSignalEntity(victoria, "NO_IR_SOLO", ahora)
            ),
            aDesmarcar = emptyList()
        )
        assertEquals(
            mapOf("HAY_PERRO" to viejo, "NO_IR_SOLO" to ahora),
            dao.senalesDe(victoria).associate { it.SENAL to it.ACTUALIZADA_EN }
        )
    }

    @Test
    fun `desmarcar y volver a marcar SI resetea la fecha - dejo de saberse`() = runTest {
        val viejo = "2025-01-15T18:00:00Z"
        dao.guardar(
            ficha = ClientProfileEntity(victoria, null, viejo, 77),
            aMarcar = listOf(ClientProfileSignalEntity(victoria, "HAY_PERRO", viejo)),
            aDesmarcar = emptyList()
        )
        dao.guardar(
            ficha = ClientProfileEntity(victoria, null, ahora, 77),
            aMarcar = emptyList(),
            aDesmarcar = listOf("HAY_PERRO")
        )
        dao.guardar(
            ficha = ClientProfileEntity(victoria, null, ahora, 77),
            aMarcar = listOf(ClientProfileSignalEntity(victoria, "HAY_PERRO", ahora)),
            aDesmarcar = emptyList()
        )
        assertEquals(ahora, dao.senalesDe(victoria).single().ACTUALIZADA_EN)
    }

    @Test
    fun `la fecha de un cliente no contagia a la del otro`() = runTest {
        val viejo = "2025-01-15T18:00:00Z"
        dao.guardar(
            ficha = ClientProfileEntity(victoria, null, viejo, 77),
            aMarcar = listOf(ClientProfileSignalEntity(victoria, "HAY_PERRO", viejo)),
            aDesmarcar = emptyList()
        )
        dao.guardar(
            ficha = ClientProfileEntity(ramon, null, ahora, 77),
            aMarcar = listOf(ClientProfileSignalEntity(ramon, "HAY_PERRO", ahora)),
            aDesmarcar = emptyList()
        )
        assertEquals(viejo, dao.senalesDe(victoria).single().ACTUALIZADA_EN)
        assertEquals(ahora, dao.senalesDe(ramon).single().ACTUALIZADA_EN)
    }

    @Test
    fun `un cliente sin ficha contesta nulo y lista vacia, no una excepcion`() = runTest {
        assertNull(dao.fichaDe(victoria))
        assertTrue(dao.senalesDe(victoria).isEmpty())
    }
}
