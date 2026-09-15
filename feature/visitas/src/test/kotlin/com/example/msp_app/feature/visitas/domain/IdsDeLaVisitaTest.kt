package com.example.msp_app.feature.visitas.domain

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **El esquema de derivación de ids.** Es lo que sostiene que reintentar una
 * captura de varias cuentas reescriba las mismas filas en vez de crear filas
 * nuevas al lado de las viejas.
 */
class IdsDeLaVisitaTest {

    private val semilla = "6d1f2e3a-0000-4000-8000-000000000001"

    /**
     * **La propiedad entera, en una prueba:** misma semilla + mismas cuentas,
     * dos veces, mismos ids.
     *
     * Es el caso del proceso que muere después de escribir 2 de 3. El reintento
     * tiene que producir LOS MISMOS TRES ids: el `INSERT` es `REPLACE` sobre
     * `Visit.ID`, así que las dos ya escritas se reescriben sobre sí mismas y
     * solo falta la tercera. Con ids acuñados al azar, el reintento dejaría
     * cinco visitas de una sola visita.
     */
    @Test
    fun `misma semilla y mismas cuentas producen los mismos ids dos veces`() {
        val cuentas = listOf(77021, 77188, 90004)

        val primera = IdsDeLaVisita.paraCuentas(semilla, cuentas)
        val segunda = IdsDeLaVisita.paraCuentas(semilla, cuentas)

        assertEquals(primera, segunda)
        assertEquals(3, primera.size)
    }

    /** Y el orden en que llegan las cuentas no cambia ni un id. */
    @Test
    fun `el orden en que llegan las cuentas no cambia los ids`() {
        val enUnOrden = IdsDeLaVisita.paraCuentas(semilla, listOf(90004, 77021, 77188))
        val enOtro = IdsDeLaVisita.paraCuentas(semilla, listOf(77188, 90004, 77021))

        assertEquals(enUnOrden, enOtro)
        assertEquals(
            "y el recorrido de la escritura siempre va de la cuenta más baja a la más alta",
            listOf(77021, 77188, 90004),
            enUnOrden.keys.toList()
        )
    }

    /**
     * **Desmarcar una cuenta no renumera a las demás.** Si los ids salieran del
     * índice dentro de la lista, quitar la primera correría a todas las otras y
     * el reintento escribiría filas nuevas junto a las viejas. Salen de la
     * cuenta, así que el id de una cuenta solo depende de esa cuenta.
     */
    @Test
    fun `quitar una cuenta no cambia el id de las que quedan`() {
        val tres = IdsDeLaVisita.paraCuentas(semilla, listOf(77021, 77188, 90004))

        val dos = IdsDeLaVisita.paraCuentas(semilla, listOf(77188, 90004))

        assertEquals(tres[77188], dos[77188])
        assertEquals(tres[90004], dos[90004])
    }

    /** Dos cuentas distintas nunca comparten id. */
    @Test
    fun `dos cuentas distintas producen ids distintos`() {
        assertNotEquals(IdsDeLaVisita.idDe(semilla, 77021), IdsDeLaVisita.idDe(semilla, 77188))
    }

    /** Y dos capturas distintas tampoco, aunque marquen la misma cuenta. */
    @Test
    fun `dos semillas distintas producen ids distintos para la misma cuenta`() {
        val otra = "6d1f2e3a-0000-4000-8000-000000000002"

        assertNotEquals(IdsDeLaVisita.idDe(semilla, 77021), IdsDeLaVisita.idDe(otra, 77021))
    }

    /**
     * **Cada id es un UUID canónico.** El servidor hace `uuid.Parse` sobre el id
     * antes de mirar nada más (`visitashttp/handlers.go`), así que un id con
     * forma libre pasaría por Room y por `by-ids` —que solo comparan cadenas— y
     * rebotaría con 422 justo en la subida, dejando la visita reintentando en el
     * teléfono para siempre.
     */
    @Test
    fun `cada id derivado es un UUID canonico`() {
        IdsDeLaVisita.paraCuentas(semilla, listOf(77021, 77188)).values.forEach { id ->
            assertEquals("no es canonico: $id", id, UUID.fromString(id).toString())
            assertTrue("tiene la forma 8-4-4-4-12", Regex(FORMA_UUID).matches(id))
        }
    }

    /** El ancla es la cuenta más baja — el mismo desempate que la atribución. */
    @Test
    fun `el ancla es el id de la cuenta mas baja`() {
        assertEquals(
            IdsDeLaVisita.idDe(semilla, 77021),
            IdsDeLaVisita.ancla(semilla, listOf(90004, 77021, 77188))
        )
    }

    /**
     * Sin cuentas el ancla es la semilla misma: es el alcance de toda la puerta,
     * que escribe una sola visita y no deriva nada.
     */
    @Test
    fun `sin cuentas el ancla es la semilla`() {
        assertEquals(semilla, IdsDeLaVisita.ancla(semilla, emptyList()))
    }

    /** Una cuenta repetida no produce dos visitas de la misma cuenta. */
    @Test
    fun `una cuenta repetida se cuenta una sola vez`() {
        assertEquals(1, IdsDeLaVisita.paraCuentas(semilla, listOf(77021, 77021)).size)
    }

    private companion object {
        const val FORMA_UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    }
}
