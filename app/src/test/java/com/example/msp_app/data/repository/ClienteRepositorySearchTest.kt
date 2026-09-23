package com.example.msp_app.data.repository

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.entities.ClienteEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.utils.normalizeForSearch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * Reproduce el defecto de campo: "por un acento o un espacio de más, el buscador de la
 * Nueva Venta no encuentra al cliente". Nombres mexicanos reales, sembrados directo por
 * DAO (como los deja `ClienteRepository.syncFromServer`, con `NOMBRE_NORMALIZADO`
 * calculado) para no depender de la red.
 *
 * Rojo sembrado (verificado manualmente, no queda en el repo): quitar la normalización
 * de un solo lado —por ejemplo, sembrar `NOMBRE_NORMALIZADO = NOMBRE` sin pasar por
 * [normalizeForSearch], o cambiar `searchClientes` para consultar con `query` crudo en
 * vez de `normalizedQuery`— pone en rojo `busca con acento en la consulta encuentra
 * dato sin acento` y `espacios de mas al inicio, al final y dobles en medio no
 * estorban`.
 */
class ClienteRepositorySearchTest : RoomTestBase() {

    private lateinit var repository: ClienteRepository

    /** Sin acentos en el dato: prueba el acento puesto en la CONSULTA. */
    private val joseLuis = cliente(1, "JOSE LUIS MARTINEZ CRUZ")

    /** Con acentos en el dato: prueba el acento puesto en el DATO. */
    private val mariaElena = cliente(2, "MARÍA ELENA VÁZQUEZ RUIZ")

    /** Acento y eñe en el dato: prueba "los dos" y la eñe en la misma fila. */
    private val joseAngel = cliente(3, "JOSÉ ÁNGEL RAMÍREZ PEÑA")

    /** Control negativo: no debe aparecer en ninguna búsqueda por "jose" o "maria". */
    private val roberto = cliente(4, "ROBERTO CARLOS SANCHEZ TORRES")

    private fun cliente(id: Int, nombre: String) = ClienteEntity(
        CLIENTE_ID = id,
        NOMBRE = nombre,
        ESTATUS = "A",
        CAUSA_SUSP = null,
        NOMBRE_NORMALIZADO = normalizeForSearch(nombre)
    )

    @Before
    fun setUpRepository() = runTest {
        repository = ClienteRepository(ApplicationProvider.getApplicationContext())
        db.clienteDao().insertAll(listOf(joseLuis, mariaElena, joseAngel, roberto))
    }

    @Test
    fun `busca con acento en la consulta encuentra dato sin acento`() = runTest {
        val result = repository.searchClientes("José Luis")

        assertEquals(listOf(joseLuis.NOMBRE), result.map { it.NOMBRE })
    }

    @Test
    fun `busca sin acento encuentra dato con acento`() = runTest {
        val result = repository.searchClientes("maria elena")

        assertEquals(listOf(mariaElena.NOMBRE), result.map { it.NOMBRE })
        assertFalse(result.any { it.NOMBRE == roberto.NOMBRE })
    }

    @Test
    fun `acento en la consulta y en el dato a la vez sigue encontrando`() = runTest {
        val result = repository.searchClientes("José Ángel")

        assertEquals(listOf(joseAngel.NOMBRE), result.map { it.NOMBRE })
    }

    @Test
    fun `la ene sin virgulilla encuentra la ene con virgulilla`() = runTest {
        val result = repository.searchClientes("pena")

        assertEquals(listOf(joseAngel.NOMBRE), result.map { it.NOMBRE })
    }

    @Test
    fun `espacios de mas al inicio, al final y dobles en medio no estorban`() = runTest {
        val result = repository.searchClientes("  maria   elena ")

        assertEquals(listOf(mariaElena.NOMBRE), result.map { it.NOMBRE })
    }

    @Test
    fun `minusculas encuentran el nombre en mayusculas del padron`() = runTest {
        val result = repository.searchClientes("jose luis martinez")

        assertEquals(listOf(joseLuis.NOMBRE), result.map { it.NOMBRE })
    }

    @Test
    fun `el apellido tecleado antes que el nombre tambien encuentra`() = runTest {
        val result = repository.searchClientes("vazquez maria")

        assertEquals(listOf(mariaElena.NOMBRE), result.map { it.NOMBRE })
    }

    @Test
    fun `un cliente que no coincide con nada no aparece`() = runTest {
        val result = repository.searchClientes("maria elena")

        assertFalse(
            "Roberto no tiene ninguna palabra en comun con la consulta",
            result.any { it.NOMBRE == roberto.NOMBRE }
        )
    }
}
