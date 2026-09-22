package com.example.msp_app.feature.ventacorreccion.usecase

import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.ventacorreccion.data.fake.FakeReloj
import com.example.msp_app.feature.ventacorreccion.data.fake.FakeVentaLocalCorreccionPort
import com.example.msp_app.feature.ventacorreccion.domain.CamposVentaCorregidos
import com.example.msp_app.feature.ventacorreccion.domain.CorreccionRemotaTerminal
import com.example.msp_app.feature.ventacorreccion.domain.EstadoCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.ConsultarEstadoCorreccion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private const val SALE_ID = "sale-consulta-001"

/**
 * [ConsultarEstadoCorreccion] es la única pieza de dominio nueva de Task 5 (UI) y, al cierre de
 * la ronda 1 de arreglo, la única de esa tarea que se había quedado SIN rojo sembrado: que hoy
 * sea de sólo lectura lo garantizaba la lectura del código, no una prueba — nada impedía que
 * mañana alguien le agregara un `port.reclamarParaEditar(...)` "por si acaso" y ningún test se
 * enterara.
 *
 * Por eso estas pruebas no miran sólo el [EstadoCorreccion] devuelto: cuentan invocaciones sobre
 * [FakeVentaLocalCorreccionPort] (ver sus contadores, agregados en esta misma ronda) y exigen que
 * SÓLO `leerEstado` se haya llamado — nunca `reclamarParaEditar`/`soltar`/`guardarCorreccion`
 * (escriben) ni `leerVenta` (lee de más: sólo [com.example.msp_app.feature.ventacorreccion.domain.usecase.ReclamarCorreccion]
 * lo necesita, para poblar el formulario de verdad).
 */
class ConsultarEstadoCorreccionTest {

    private val clock = FakeClock.at("2026-09-20T12:00:00Z")
    private lateinit var port: FakeVentaLocalCorreccionPort
    private lateinit var consultar: ConsultarEstadoCorreccion

    private fun campos() = CamposVentaCorregidos(
        nombreCliente = "Cliente de prueba",
        fechaVenta = "2026-09-18T15:30:00Z",
        latitud = 19.0,
        longitud = -99.0,
        direccion = "Calle 1",
        parcialidad = 500.0,
        enganche = null,
        telefono = "5555555555",
        frecPago = "SEMANAL",
        avalOResponsable = null,
        nota = null,
        diaCobranza = "LUNES",
        precioTotal = 5000.0,
        tiempoACortoPlazoMeses = 4,
        montoACortoPlazo = 4500.0,
        montoDeContado = 4000.0
    )

    @Before
    fun setUp() {
        port = FakeVentaLocalCorreccionPort()
        consultar = ConsultarEstadoCorreccion(port, FakeReloj(clock))
    }

    private fun assertSoloLeyoEstado() {
        assertEquals("leerEstado debe llamarse exactamente una vez", 1, port.leerEstadoCallCount)
        assertEquals(
            "no debe leer la venta completa (eso es cosa de ReclamarCorreccion)",
            0,
            port.leerVentaCallCount
        )
        assertEquals(
            "una consulta de sólo lectura NUNCA reclama el candado",
            0,
            port.reclamarParaEditarCallCount
        )
        assertEquals(
            "una consulta de sólo lectura nunca suelta nada (no tiene nada que soltar)",
            0,
            port.soltarCallCount
        )
        assertEquals(
            "una consulta de sólo lectura nunca guarda",
            0,
            port.guardarCorreccionCallCount
        )
    }

    @Test
    fun `consulta una venta corregible sin reclamar nada`() = runTest {
        port.siembra(SALE_ID, campos())

        val estado = consultar(SALE_ID)

        assertEquals(EstadoCorreccion.Corregible, estado)
        assertSoloLeyoEstado()
    }

    /**
     * El cambio de fondo del nivel 2 visto desde el caso de uso: una venta ya enviada y limpia
     * es [EstadoCorreccion.CorregibleEnviada], no [EstadoCorreccion.YaSeEnvio] — el servidor la
     * tiene en `borrador` y lo que se corrija viaja por la cola de correcciones remotas.
     */
    @Test
    fun `consulta una venta ya enviada y limpia sin reclamar nada`() = runTest {
        port.siembra(SALE_ID, campos(), enviado = true)

        val estado = consultar(SALE_ID)

        assertEquals(EstadoCorreccion.CorregibleEnviada, estado)
        assertSoloLeyoEstado()
    }

    @Test
    fun `consulta una venta con una correccion ya en la cola sin reclamar nada`() = runTest {
        port.siembra(SALE_ID, campos(), enviado = true, correccionRemotaPendiente = true)

        val estado = consultar(SALE_ID)

        assertEquals(EstadoCorreccion.CorreccionEnCamino, estado)
        assertSoloLeyoEstado()
    }

    @Test
    fun `consulta una venta que el servidor ya cerro sin reclamar nada`() = runTest {
        port.siembra(
            SALE_ID,
            campos(),
            enviado = true,
            correccionRemotaEstado = CorreccionRemotaTerminal.RECHAZADA_ESTADO
        )

        val estado = consultar(SALE_ID)

        assertEquals(EstadoCorreccion.LaOficinaYaLaAplico, estado)
        assertSoloLeyoEstado()
    }

    @Test
    fun `consulta una venta enviada con divergencia marcada sin reclamar nada`() = runTest {
        port.siembra(SALE_ID, campos(), enviado = true, correccionNoEnviada = true)

        val estado = consultar(SALE_ID)

        assertEquals(EstadoCorreccion.LaRevisaLaOficina, estado)
        assertSoloLeyoEstado()
    }

    @Test
    fun `una venta inexistente devuelve null sin reclamar nada`() = runTest {
        val estado = consultar(SALE_ID)

        assertNull(estado)
        assertSoloLeyoEstado()
    }
}
