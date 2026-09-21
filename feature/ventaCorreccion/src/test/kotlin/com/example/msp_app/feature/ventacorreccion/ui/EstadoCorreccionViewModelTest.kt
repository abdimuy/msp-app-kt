package com.example.msp_app.feature.ventacorreccion.ui

import com.example.msp_app.core.testing.MainDispatcherRule
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.ventacorreccion.data.fake.FakeReloj
import com.example.msp_app.feature.ventacorreccion.data.fake.FakeVentaLocalCorreccionPort
import com.example.msp_app.feature.ventacorreccion.domain.CamposVentaCorregidos
import com.example.msp_app.feature.ventacorreccion.domain.EstadoCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.ConsultarEstadoCorreccion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val SALE_ID = "sale-estado-vm-001"

/**
 * [EstadoCorreccionViewModel] respalda el punto de entrada de Task 5 (`SaleDescriptionScreen`,
 * `:app`). Contra [FakeVentaLocalCorreccionPort] (sin Room), igual que
 * [CorreccionVentaViewModelTest] — aquí sólo se cubren las transiciones de `estado`, incluida la
 * garantía de que consultarlo nunca reclama (ver [FakeVentaLocalCorreccionPort.reclamarParaEditarCallCount]).
 */
class EstadoCorreccionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeClock.at("2026-09-20T12:00:00Z")
    private lateinit var port: FakeVentaLocalCorreccionPort
    private lateinit var viewModel: EstadoCorreccionViewModel

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
        viewModel = EstadoCorreccionViewModel(ConsultarEstadoCorreccion(port, FakeReloj(clock)))
    }

    @Test
    fun `el estado arranca null, antes de consultar nada`() {
        assertNull(viewModel.estado.value)
    }

    @Test
    fun `consultar una venta corregible refleja Corregible sin reclamar nada`() = runTest {
        port.siembra(SALE_ID, campos())

        viewModel.consultar(SALE_ID)

        assertEquals(EstadoCorreccion.Corregible, viewModel.estado.value)
        assertEquals(0, port.reclamarParaEditarCallCount)
    }

    @Test
    fun `consultar una venta ya enviada refleja YaSeEnvio`() = runTest {
        port.siembra(SALE_ID, campos(), enviado = true)

        viewModel.consultar(SALE_ID)

        assertEquals(EstadoCorreccion.YaSeEnvio, viewModel.estado.value)
    }

    @Test
    fun `consultar una venta inexistente deja el estado en null`() = runTest {
        viewModel.consultar(SALE_ID)

        assertNull(viewModel.estado.value)
    }
}
