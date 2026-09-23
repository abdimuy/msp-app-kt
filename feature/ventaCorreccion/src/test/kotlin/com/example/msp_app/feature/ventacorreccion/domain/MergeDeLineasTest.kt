package com.example.msp_app.feature.ventacorreccion.domain

import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SALE_ID = "sale-merge-001"

/**
 * [mergeDeLineas] contra el tipo real que usan `LocalSaleProductDao` y sus
 * pruebas ([LocalSaleProductEntity]) — misma clave (`ARTICULO_ID`) y mismo
 * criterio "LA BASE MANDA" para `SERVER_UUID` que `mergeProductsForSale`
 * (Task 1). Pura: nada de Room, nada de base de datos.
 */
class MergeDeLineasTest {

    private fun producto(articuloId: Int, serverUuid: String?, cantidad: Int = 1) =
        LocalSaleProductEntity(
            LOCAL_SALE_ID = SALE_ID,
            ARTICULO_ID = articuloId,
            ARTICULO = "Articulo $articuloId",
            CANTIDAD = cantidad,
            PRECIO_LISTA = 100.0,
            PRECIO_CORTO_PLAZO = 110.0,
            PRECIO_CONTADO = 90.0,
            COMBO_ID = null,
            SERVER_UUID = serverUuid
        )

    private fun merge(
        actuales: List<LocalSaleProductEntity>,
        deseadas: List<LocalSaleProductEntity>
    ) = mergeDeLineas(
        actuales = actuales,
        deseadas = deseadas,
        clave = { it.ARTICULO_ID },
        serverUuid = { it.SERVER_UUID },
        conServerUuid = { linea, uuid -> linea.copy(SERVER_UUID = uuid) }
    )

    @Test
    fun `linea que sobrevive conserva el SERVER_UUID de la base, no el de la entrada`() {
        val actuales = listOf(producto(articuloId = 1, serverUuid = "server-uuid-real"))
        // El editor nunca conoce el SERVER_UUID real: lo que trae la entrada
        // para una línea que sobrevive es basura vieja o null — aquí basura,
        // a propósito, para probar que se descarta.
        val deseadas =
            listOf(producto(articuloId = 1, serverUuid = "basura-del-formulario", cantidad = 5))

        val resultado = merge(actuales, deseadas)

        assertEquals(1, resultado.aActualizar.size)
        assertEquals("server-uuid-real", resultado.aActualizar.single().SERVER_UUID)
        assertEquals(5, resultado.aActualizar.single().CANTIDAD) // la cantidad SÍ es la nueva
        assertTrue(resultado.aInsertar.isEmpty())
        assertTrue(resultado.aBorrar.isEmpty())
    }

    @Test
    fun `linea nueva se inserta tal cual, no hereda ningun SERVER_UUID de otra`() {
        val actuales = listOf(producto(articuloId = 1, serverUuid = "server-uuid-1"))
        val nueva = producto(articuloId = 2, serverUuid = null)
        val deseadas = listOf(producto(articuloId = 1, serverUuid = "x"), nueva)

        val resultado = merge(actuales, deseadas)

        assertEquals(listOf(nueva), resultado.aInsertar)
        assertTrue(resultado.aBorrar.isEmpty())
    }

    @Test
    fun `linea quitada se borra`() {
        val actuales = listOf(
            producto(articuloId = 1, serverUuid = "server-uuid-1"),
            producto(articuloId = 2, serverUuid = "server-uuid-2")
        )
        val deseadas = listOf(producto(articuloId = 1, serverUuid = null))

        val resultado = merge(actuales, deseadas)

        assertEquals(listOf(2), resultado.aBorrar)
        assertEquals(1, resultado.aActualizar.size)
        assertTrue(resultado.aInsertar.isEmpty())
    }

    @Test
    fun `lista deseada vacia borra todo y no actualiza ni inserta nada`() {
        val actuales = listOf(
            producto(articuloId = 1, serverUuid = "server-uuid-1"),
            producto(articuloId = 2, serverUuid = "server-uuid-2")
        )

        val resultado = merge(actuales, emptyList())

        assertEquals(setOf(1, 2), resultado.aBorrar.toSet())
        assertTrue(resultado.aActualizar.isEmpty())
        assertTrue(resultado.aInsertar.isEmpty())
    }

    @Test
    fun `mergear la misma lista deseada dos veces seguidas es idempotente`() {
        val actuales = listOf(producto(articuloId = 1, serverUuid = "server-uuid-1"))
        val deseadas =
            listOf(
                producto(articuloId = 1, serverUuid = null),
                producto(articuloId = 2, serverUuid = null)
            )

        val primeraPasada = merge(actuales, deseadas)
        // Aplica el resultado: lo que queda "en la base" tras la primera
        // corrección es aActualizar + aInsertar, sin lo de aBorrar.
        val nuevasActuales = primeraPasada.aActualizar + primeraPasada.aInsertar

        val segundaPasada = merge(nuevasActuales, deseadas)

        assertTrue("la segunda pasada no debe borrar nada nuevo", segundaPasada.aBorrar.isEmpty())
        assertTrue(
            "la segunda pasada no debe insertar nada nuevo",
            segundaPasada.aInsertar.isEmpty()
        )
        assertEquals(2, segundaPasada.aActualizar.size)
        // El SERVER_UUID que la primera pasada acuñó (o preservó) se mantiene estable.
        assertEquals(
            nuevasActuales.associate { it.ARTICULO_ID to it.SERVER_UUID },
            segundaPasada.aActualizar.associate { it.ARTICULO_ID to it.SERVER_UUID }
        )
    }

    @Test
    fun `ARTICULO_ID repetido en deseadas lanza excepcion, mismo criterio que el DAO`() {
        val deseadas =
            listOf(
                producto(articuloId = 1, serverUuid = null),
                producto(articuloId = 1, serverUuid = null)
            )

        assertThrows(IllegalArgumentException::class.java) {
            merge(emptyList(), deseadas)
        }
    }
}
