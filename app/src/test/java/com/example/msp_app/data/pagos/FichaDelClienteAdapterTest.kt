package com.example.msp_app.data.pagos

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.clientprofile.ClientProfileDao
import com.example.msp_app.core.database.entities.ClientProfileEntity
import com.example.msp_app.core.database.entities.ClientProfileSignalEntity
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.domain.model.FichaDelCliente
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import com.example.msp_app.feature.pagos.domain.port.ResultadoDeLaFicha
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La ficha contra una base Room en memoria: **la persistencia de los dos
 * campos**, sus bordes, y los caminos de error que la norma obliga a reportar.
 *
 * Lo que se prueba aquí y no río arriba: que guardar **no borra** un literal
 * que este build ya no conoce, que "no se pudo leer" llega como `null` y no
 * como ficha vacía —la distinción que impide sobrescribir conocimiento—, y que
 * la atribución faltante nunca impide guardar.
 */
class FichaDelClienteAdapterTest : RoomTestBase() {

    private val clock = FakeClock(Instant.parse("2026-09-01T18:00:00Z"))
    private val telemetria = RecordingTelemetry(clock)

    private var usuario: User? = COBRADOR
    private var fallaElUsuario: Throwable? = null

    private val victoria = 4021
    private val ramon = 4022

    private fun adaptador(fichas: ClientProfileDao = db.clientProfileDao()) =
        FichaDelClienteAdapter(
            fichas = fichas,
            telemetry = telemetria,
            clock = clock,
            traerUsuario = {
                fallaElUsuario?.let { throw it }
                usuario
            }
        )

    /**
     * Un DAO que truena en cada consulta — el fallo de Room que el adaptador
     * tiene que degradar.
     *
     * Fake escrito a mano y **no** `db.close()`: se comprobó que cerrar una base
     * en memoria no hace fallar la siguiente consulta —Room la reabre vacía— y
     * el test pasaba en verde sin ejercitar un solo `catch`. Regla de control
     * positivo: una ausencia no es un hallazgo hasta probar que el método
     * la habría encontrado.
     */
    private class DaoQueTruena : ClientProfileDao {
        override suspend fun fichaDe(clienteId: Int): ClientProfileEntity =
            throw IllegalStateException("room caido")

        override suspend fun senalesDe(clienteId: Int): List<ClientProfileSignalEntity> =
            throw IllegalStateException("room caido")

        override suspend fun guardarNota(ficha: ClientProfileEntity): Unit =
            throw IllegalStateException("room caido")

        override suspend fun marcar(senales: List<ClientProfileSignalEntity>): Unit =
            throw IllegalStateException("room caido")

        override suspend fun desmarcar(clienteId: Int, senal: String): Unit =
            throw IllegalStateException("room caido")
    }

    private fun errores(code: String) =
        telemetria.recorded.filter { it.type == TelemetryEventType.ERROR && it.name == code }

    // --- La persistencia de los DOS campos -----------------------------------

    @Test
    fun `guarda y vuelve a leer la nota y las senales`() = runTest {
        val adaptador = adaptador()
        val guardado = adaptador.guardar(
            clienteId = victoria,
            ficha = FichaDelCliente(
                senales = setOf(
                    SenalDeFicha.ESTA_EN_LA_NOCHE,
                    SenalDeFicha.ATIENDE_OTRA_PERSONA
                ),
                nota = "atiende la suegra\ncasa azul, portón negro"
            )
        )
        assertTrue(guardado is ResultadoDeLaFicha.Guardada)

        val leida = checkNotNull(adaptador.fichaDe(victoria))
        assertEquals(
            setOf(SenalDeFicha.ESTA_EN_LA_NOCHE, SenalDeFicha.ATIENDE_OTRA_PERSONA),
            leida.senales
        )
        assertEquals("atiende la suegra\ncasa azul, portón negro", leida.nota)
        assertEquals(clock.now(), leida.actualizada)
    }

    @Test
    fun `la ficha de un cliente NO se ve desde otro`() = runTest {
        val adaptador = adaptador()
        adaptador.guardar(
            victoria,
            FichaDelCliente(senales = setOf(SenalDeFicha.ESTA_EN_LA_NOCHE), nota = "hay perro")
        )
        val otra = checkNotNull(adaptador.fichaDe(ramon))
        assertTrue("Ramón no tiene ficha", otra.vacia)
    }

    @Test
    fun `un cliente sin ficha llega VACIO, no nulo`() = runTest {
        val leida = checkNotNull(adaptador().fichaDe(victoria)) {
            "sin ficha no es lo mismo que no se pudo leer"
        }
        assertTrue(leida.vacia)
        assertNull(leida.actualizada)
    }

    @Test
    fun `solo senales, sin nota`() = runTest {
        val adaptador = adaptador()
        adaptador.guardar(victoria, FichaDelCliente(senales = setOf(SenalDeFicha.ESTA_EN_LA_TARDE)))
        val leida = checkNotNull(adaptador.fichaDe(victoria))
        assertNull(leida.nota)
        assertEquals(setOf(SenalDeFicha.ESTA_EN_LA_TARDE), leida.senales)
    }

    @Test
    fun `solo nota, sin senales`() = runTest {
        val adaptador = adaptador()
        adaptador.guardar(victoria, FichaDelCliente(nota = "hay perro"))
        val leida = checkNotNull(adaptador.fichaDe(victoria))
        assertEquals("hay perro", leida.nota)
        assertTrue(leida.senales.isEmpty())
    }

    @Test
    fun `una nota vacia guardada se lee como AUSENTE, no como tarjeta en blanco`() = runTest {
        // La normalización a `null` vive en el caso de uso; aquí se prueba que
        // aunque una cadena vacía llegara hasta la base, la lectura no inventa
        // una nota que pintar.
        db.clientProfileDao().guardarNota(
            ClientProfileEntity(
                CLIENTE_ID = victoria,
                NOTA = "",
                ACTUALIZADA_EN = AppTime.toWireFormat(clock.now()),
                COBRADOR_ID = null
            )
        )
        assertNull(adaptador().fichaDe(victoria)?.nota)
    }

    @Test
    fun `quitar la nota no se lleva las senales, y al reves tampoco`() = runTest {
        val adaptador = adaptador()
        adaptador.guardar(
            victoria,
            FichaDelCliente(senales = setOf(SenalDeFicha.ESTA_EN_LA_NOCHE), nota = "hay perro")
        )

        adaptador.guardar(victoria, FichaDelCliente(senales = setOf(SenalDeFicha.ESTA_EN_LA_NOCHE)))
        assertNull(adaptador.fichaDe(victoria)?.nota)
        assertEquals(setOf(SenalDeFicha.ESTA_EN_LA_NOCHE), adaptador.fichaDe(victoria)?.senales)

        adaptador.guardar(victoria, FichaDelCliente(nota = "hay perro"))
        assertEquals("hay perro", adaptador.fichaDe(victoria)?.nota)
        assertTrue(checkNotNull(adaptador.fichaDe(victoria)).senales.isEmpty())
    }

    // --- El catálogo que crece: lo desconocido ni se pinta ni se borra --------

    @Test
    fun `una senal que este build no conoce se ignora al leer y se reporta`() = runTest {
        db.clientProfileDao().marcar(
            listOf(
                ClientProfileSignalEntity(victoria, "SE_MUDO_DE_CASA", "2026-01-01T00:00:00Z"),
                ClientProfileSignalEntity(victoria, "ESTA_EN_LA_NOCHE", "2026-01-01T00:00:00Z")
            )
        )
        val leida = checkNotNull(adaptador().fichaDe(victoria))
        assertEquals(setOf(SenalDeFicha.ESTA_EN_LA_NOCHE), leida.senales)

        val evento = errores(PagosTelemetria.CODE_FICHA_SENAL_DESCONOCIDA).single()
        assertEquals("1", evento.props[PagosTelemetria.PROP_OCURRENCIAS])
        assertNull(
            "el literal viene del disco: no puede viajar en props",
            evento.props.values.firstOrNull { it.contains("SE_MUDO") }
        )
    }

    @Test
    fun `sin senales desconocidas no se emite nada`() = runTest {
        db.clientProfileDao().marcar(
            listOf(ClientProfileSignalEntity(victoria, "ESTA_EN_LA_NOCHE", "2026-01-01T00:00:00Z"))
        )
        adaptador().fichaDe(victoria)
        assertTrue(errores(PagosTelemetria.CODE_FICHA_SENAL_DESCONOCIDA).isEmpty())
    }

    /**
     * El defecto que este test caza: guardar con un `DELETE WHERE CLIENTE_ID`
     * habría evaporado `SE_MUDO_DE_CASA` en la primera edición posterior, sin
     * que nadie lo viera. La señal desconocida ni se pinta ni se borra.
     */
    @Test
    fun `guardar NO borra la senal que este build no conoce`() = runTest {
        db.clientProfileDao().marcar(
            listOf(ClientProfileSignalEntity(victoria, "SE_MUDO_DE_CASA", "2026-01-01T00:00:00Z"))
        )
        adaptador().guardar(
            victoria,
            FichaDelCliente(senales = setOf(SenalDeFicha.ESTA_EN_LA_MANANA))
        )
        assertEquals(
            listOf("ESTA_EN_LA_MANANA", "SE_MUDO_DE_CASA"),
            db.clientProfileDao().senalesDe(victoria).map { it.SENAL }
        )
    }

    @Test
    fun `desmarcar una senal la quita de verdad de la base`() = runTest {
        val adaptador = adaptador()
        adaptador.guardar(
            victoria,
            FichaDelCliente(
                senales = setOf(SenalDeFicha.ESTA_EN_LA_MANANA, SenalDeFicha.ESTA_EN_LA_NOCHE)
            )
        )
        adaptador.guardar(victoria, FichaDelCliente(senales = setOf(SenalDeFicha.ESTA_EN_LA_NOCHE)))
        assertEquals(
            listOf("ESTA_EN_LA_NOCHE"),
            db.clientProfileDao().senalesDe(victoria).map { it.SENAL }
        )
    }

    // --- La atribución: nullable a propósito ---------------------------------

    @Test
    fun `el cobrador autenticado queda en COBRADOR_ID`() = runTest {
        adaptador().guardar(victoria, FichaDelCliente(nota = "hay perro"))
        assertEquals(COBRADOR.COBRADOR_ID, db.clientProfileDao().fichaDe(victoria)?.COBRADOR_ID)
    }

    @Test
    fun `sin sesion la ficha SI se guarda, con COBRADOR_ID nulo`() = runTest {
        usuario = null
        val resultado = adaptador().guardar(victoria, FichaDelCliente(nota = "hay perro"))
        assertTrue(resultado is ResultadoDeLaFicha.Guardada)
        assertEquals("hay perro", db.clientProfileDao().fichaDe(victoria)?.NOTA)
        assertNull(db.clientProfileDao().fichaDe(victoria)?.COBRADOR_ID)
    }

    @Test
    fun `un COBRADOR_ID cero es sin resolver, no el cobrador numero cero`() = runTest {
        usuario = COBRADOR.copy(COBRADOR_ID = 0)
        adaptador().guardar(victoria, FichaDelCliente(nota = "hay perro"))
        assertNull(db.clientProfileDao().fichaDe(victoria)?.COBRADOR_ID)
    }

    @Test
    fun `si resolver al cobrador LANZA, la ficha se guarda igual y se reporta`() = runTest {
        fallaElUsuario = IllegalStateException("firestore inalcanzable")
        val resultado = adaptador().guardar(victoria, FichaDelCliente(nota = "hay perro"))
        assertTrue(resultado is ResultadoDeLaFicha.Guardada)
        assertEquals("hay perro", db.clientProfileDao().fichaDe(victoria)?.NOTA)
        assertNull(db.clientProfileDao().fichaDe(victoria)?.COBRADOR_ID)

        val evento = errores(PagosTelemetria.CODE_FICHA_SIN_COBRADOR).single()
        assertEquals(
            "IllegalStateException",
            evento.props[PagosTelemetria.PROP_EXCEPCION]
        )
    }

    // --- Los caminos de error ------------------------------------------------

    @Test
    fun `si la LECTURA falla contesta nulo -no una ficha vacia- y lo reporta`() = runTest {
        assertNull(
            "una ficha vacia invitaria a escribir encima de lo guardado",
            adaptador(DaoQueTruena()).fichaDe(victoria)
        )
        val evento = errores(PagosTelemetria.CODE_FICHA_NO_SE_PUDO_LEER).single()
        assertEquals("IllegalStateException", evento.props[PagosTelemetria.PROP_EXCEPCION])
    }

    @Test
    fun `si la ESCRITURA falla contesta FalloElGuardado y lo reporta`() = runTest {
        val resultado = adaptador(DaoQueTruena())
            .guardar(victoria, FichaDelCliente(nota = "hay perro"))
        assertEquals(ResultadoDeLaFicha.FalloElGuardado, resultado)
        val evento = errores(PagosTelemetria.CODE_FICHA_NO_SE_GUARDO).single()
        assertEquals("IllegalStateException", evento.props[PagosTelemetria.PROP_EXCEPCION])

        // Control positivo: con el DAO real, el MISMO guardado SÍ queda.
        assertTrue(
            adaptador().guardar(victoria, FichaDelCliente(nota = "hay perro"))
                is ResultadoDeLaFicha.Guardada
        )
    }

    @Test
    fun `ningun evento de la ficha lleva la nota ni el id del cliente`() = runTest {
        val adaptador = adaptador(DaoQueTruena())
        adaptador.guardar(victoria, FichaDelCliente(nota = "atiende la suegra"))
        adaptador.fichaDe(victoria)
        telemetria.recorded.forEach { evento ->
            evento.props.values.forEach { valor ->
                assertTrue("PII en props: $valor", !valor.contains("suegra"))
                assertTrue("id de cliente en props: $valor", !valor.contains("$victoria"))
            }
        }
    }

    private companion object {
        val COBRADOR =
            User(ID = "u-1", NOMBRE = "Aldo Rivera", EMAIL = "aldo@msp.mx", COBRADOR_ID = 77)
    }
}
