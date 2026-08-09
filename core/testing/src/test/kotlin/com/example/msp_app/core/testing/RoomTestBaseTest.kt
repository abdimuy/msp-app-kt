package com.example.msp_app.core.testing

import com.example.msp_app.core.database.entities.ClienteEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prueba de round-trip para [RoomTestBase] desde su nuevo hogar (Task 5,
 * post-hoist de `AppDatabase` a `:core:database`). Una subclase mínima
 * arma la DB in-memory (heredada de `setUpDatabase`/`tearDownDatabase`),
 * inserta una fila real vía DAO y la relee: si `RoomTestBase` quedó mal
 * cableado (imports rotos, `db` sin inicializar, instancia no registrada
 * en `AppDatabase.setInstanceForTesting`), esto falla.
 */
class RoomTestBaseTest : RoomTestBase() {

    @Test
    fun `RoomTestBase construye la DB in-memory y sostiene un round-trip via DAO`() = runTest {
        val cliente = ClienteEntity(
            CLIENTE_ID = 42,
            NOMBRE = "Rosa Martinez",
            ESTATUS = "A",
            CAUSA_SUSP = null
        )

        db.clienteDao().insertAll(listOf(cliente))
        val fetched = db.clienteDao().searchByPrefix("Rosa")

        assertEquals(1, fetched.size)
        assertEquals("Rosa Martinez", fetched.single().NOMBRE)
    }
}
