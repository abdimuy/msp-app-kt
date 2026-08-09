package com.example.msp_app.data.local.datasource

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.entities.ClienteEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite exhaustiva de [ClienteDataSource] construido por el **constructor de
 * DAO inyectado** (la forma Hilt) contra la DB in-memory de [RoomTestBase].
 * Cubre `replaceAll` (reemplazo completo), las dos busquedas (`LIKE
 * '%..%'` vs `LIKE 'prefijo%'`) y la equivalencia con el puente `context`
 * que sigue usando `ClienteRepository`.
 */
class ClienteDataSourceTest : RoomTestBase() {

    private lateinit var store: ClienteDataSource

    @Before
    fun setUpStore() {
        store = ClienteDataSource(db.clienteDao())
    }

    private fun cliente(id: Int, nombre: String, estatus: String = "A", causaSusp: String? = null) =
        ClienteEntity(CLIENTE_ID = id, NOMBRE = nombre, ESTATUS = estatus, CAUSA_SUSP = causaSusp)

    @Test
    fun replaceAll_and_getCount_roundTrips() = runTest {
        store.replaceAll(
            listOf(
                cliente(1, "Rosa Elena Martinez"),
                cliente(2, "Guadalupe Hernandez Soto")
            )
        )

        assertEquals(2, store.getCount())
    }

    @Test
    fun replaceAll_replacesPreviousContents() = runTest {
        store.replaceAll(listOf(cliente(1, "Rosa Elena Martinez")))
        store.replaceAll(listOf(cliente(2, "Guadalupe Hernandez Soto")))

        assertEquals(1, store.getCount())
        assertEquals(
            listOf("Guadalupe Hernandez Soto"),
            store.searchByNombre("Guadalupe").map { it.NOMBRE }
        )
    }

    @Test
    fun searchByNombre_matchesSubstringAnywhere() = runTest {
        store.replaceAll(
            listOf(
                cliente(1, "Rosa Elena Martinez"),
                cliente(2, "Guadalupe Hernandez Soto")
            )
        )

        val result = store.searchByNombre("Martinez")

        assertEquals(listOf("Rosa Elena Martinez"), result.map { it.NOMBRE })
    }

    @Test
    fun searchByPrefix_matchesOnlyLeadingPrefix() = runTest {
        store.replaceAll(
            listOf(
                cliente(1, "Rosa Elena Martinez"),
                cliente(2, "Guadalupe Hernandez Soto")
            )
        )

        // "Elena" es substring pero NO prefijo del NOMBRE completo.
        assertTrue(store.searchByPrefix("Elena").isEmpty())
        assertEquals(
            listOf("Rosa Elena Martinez"),
            store.searchByPrefix("Rosa").map { it.NOMBRE }
        )
    }

    @Test
    fun searchByNombre_emptyWhenNoMatch() = runTest {
        store.replaceAll(listOf(cliente(1, "Rosa Elena Martinez")))

        assertTrue(store.searchByNombre("Zapata").isEmpty())
    }

    @Test
    fun injectedFormEquivalentToContextForm() = runTest {
        store.replaceAll(listOf(cliente(1, "Fernando Ramirez")))

        val contextForm = ClienteDataSource(ApplicationProvider.getApplicationContext<Context>())

        assertEquals(
            "ambos constructores resuelven a la misma DB",
            store.getCount(),
            contextForm.getCount()
        )
    }
}
