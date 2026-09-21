package com.example.msp_app.feature.ventacorreccion.ui

import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.core.testing.MainDispatcherRule
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.ventacorreccion.data.fake.FakeReencolar
import com.example.msp_app.feature.ventacorreccion.data.fake.FakeReloj
import com.example.msp_app.feature.ventacorreccion.data.fake.FakeVentaLocalCorreccionPort
import com.example.msp_app.feature.ventacorreccion.domain.CamposVentaCorregidos
import com.example.msp_app.feature.ventacorreccion.domain.TextosCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.CancelarCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.GuardarCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.ReclamarCorreccion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val SALE_ID = "sale-vm-001"
private const val EMAIL = "cobrador.pruebas@muebleriamsp.mx"

/**
 * [CorreccionVentaViewModel] contra [FakeVentaLocalCorreccionPort] (sin Room — la correctitud
 * de la persistencia ya la cubre `CorreccionCasosDeUsoTest` contra Room de verdad). Aquí sólo se
 * cubren las transiciones de estado que la UI (Task 5) va a observar.
 */
class CorreccionVentaViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeClock.at("2026-09-20T12:00:00Z")
    private lateinit var port: FakeVentaLocalCorreccionPort
    private lateinit var reencolar: FakeReencolar
    private lateinit var viewModel: CorreccionVentaViewModel

    private fun campos(nombreCliente: String = "Cliente de prueba") = CamposVentaCorregidos(
        nombreCliente = nombreCliente,
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
        val reloj = FakeReloj(clock)
        reencolar = FakeReencolar()
        viewModel = CorreccionVentaViewModel(
            reclamarCorreccion = ReclamarCorreccion(port, reloj, reencolar),
            guardarCorreccion = GuardarCorreccion(port, reloj, reencolar),
            cancelarCorreccion = CancelarCorreccion(port, reencolar)
        )
    }

    @Test
    fun `reclamar sobre una venta corregible deja el estado en Editando con los datos de la venta`() =
        runTest {
            port.siembra(SALE_ID, campos("Rosa Elena Martinez"), listOf(producto(1)))

            viewModel.reclamar(SALE_ID)

            val estado = viewModel.state.value
            assertTrue(estado is CorreccionUiState.Editando)
            estado as CorreccionUiState.Editando
            assertEquals("Rosa Elena Martinez", estado.campos.nombreCliente)
            assertEquals(1, estado.productos.size)
        }

    @Test
    fun `reclamar sobre una venta ya enviada deja el estado en NoCorregible con el texto Ya se envio`() =
        runTest {
            port.siembra(SALE_ID, campos(), enviado = true)

            viewModel.reclamar(SALE_ID)

            assertEquals(
                CorreccionUiState.NoCorregible(TextosCorreccion.YA_SE_ENVIO),
                viewModel.state.value
            )
        }

    @Test
    fun `guardar exitoso deja el estado en Guardada`() = runTest {
        port.siembra(SALE_ID, campos())
        viewModel.reclamar(SALE_ID)

        viewModel.guardar(campos("Nombre corregido"), emptyList(), emptyList(), EMAIL)

        assertEquals(CorreccionUiState.Guardada, viewModel.state.value)
    }

    @Test
    fun `guardar rechazado (la venta se envio mientras se editaba) deja el estado en NoCorregible`() =
        runTest {
            port.siembra(SALE_ID, campos())
            viewModel.reclamar(SALE_ID)

            // Mientras el dueño editaba, la venta se envió (el subidor ganó la carrera): el
            // claimId que la UI todavía sostiene ya no es el vigente.
            port.siembra(SALE_ID, campos(), enviado = true)

            viewModel.guardar(campos("Llega tarde"), emptyList(), emptyList(), EMAIL)

            assertEquals(
                CorreccionUiState.NoCorregible(TextosCorreccion.YA_SE_ENVIO),
                viewModel.state.value
            )
        }

    /**
     * Important #3 de la ronda 1 de arreglo: sin esta guarda, un `LaunchedEffect` que se
     * reejecuta (p. ej. al rotar la pantalla) volvía a reclamar, acuñaba un `claimId` nuevo y
     * re-emitía el estado con los campos RELEÍDOS de la base — tirando lo que el vendedor
     * llevaba tecleado sin guardar.
     */
    @Test
    fun `reclamar dos veces seguidas sobre la MISMA venta no pisa lo que el vendedor lleva tecleado`() =
        runTest {
            port.siembra(SALE_ID, campos("Rosa Elena Martinez"), listOf(producto(1)))
            viewModel.reclamar(SALE_ID)
            val primerEstado = viewModel.state.value as CorreccionUiState.Editando

            // La base cambia bajo los pies (irrelevante: no debería releerse mientras se edita).
            port.siembra(SALE_ID, campos("Otro nombre, de la base"), emptyList())

            viewModel.reclamar(SALE_ID)

            assertEquals(
                "una segunda llamada a reclamar sobre la MISMA venta debe ser un no-op",
                primerEstado,
                viewModel.state.value
            )
        }

    @Test
    fun `reclamar sobre una venta DISTINTA mientras se edita otra SI reclama de verdad`() =
        runTest {
            port.siembra(SALE_ID, campos("Venta uno"), listOf(producto(1)))
            val otraVentaId = "sale-vm-002"
            port.siembra(otraVentaId, campos("Venta dos"))
            viewModel.reclamar(SALE_ID)
            val primerClaimId = (viewModel.state.value as CorreccionUiState.Editando).claimId

            viewModel.reclamar(otraVentaId)

            val estado = viewModel.state.value
            assertTrue(estado is CorreccionUiState.Editando)
            estado as CorreccionUiState.Editando
            assertEquals("Venta dos", estado.campos.nombreCliente)
            assertNotEquals(primerClaimId, estado.claimId)
        }

    /**
     * Minor #1 de la ronda 1 de arreglo: cualquier excepción que NO sea
     * `GuardadoRechazadoException` (p. ej. el `require` de `mergeProductsForSale` ante un
     * `ARTICULO_ID` repetido) debe convertirse en un estado visible, no tirar la app.
     */
    @Test
    fun `guardar con una excepcion generica (no GuardadoRechazadoException) no tira la app`() =
        runTest {
            port.siembra(SALE_ID, campos())
            viewModel.reclamar(SALE_ID)
            port.lanzarEnGuardar = IllegalArgumentException("products trae ARTICULO_ID repetido")

            viewModel.guardar(campos("Con producto duplicado"), emptyList(), emptyList(), EMAIL)

            assertEquals(
                CorreccionUiState.NoCorregible(TextosCorreccion.NO_SE_PUDO_GUARDAR),
                viewModel.state.value
            )
        }

    /**
     * Important #2 de la ronda 1 de arreglo: el caso "candado ajeno pero reentrante" — otra
     * sesión de EDICIÓN (misma venta, misma app) ganó la fila entre el guardia y la relectura.
     * `EstadoCorreccion.Corregible` NO debe mostrar "Corregir venta" (mentiría: ESTE guardado, con
     * ESTE `claimId`, sí falló) — debe mostrar `NO_SE_PUDO_GUARDAR`.
     */
    @Test
    fun `guardar con un candado ajeno pero reentrante (Corregible) muestra No se pudo guardar, no Corregir venta`() =
        runTest {
            port.siembra(SALE_ID, campos())
            viewModel.reclamar(SALE_ID)

            // Otra sesión de edición (reentrante) toma la fila de nuevo, con un claimId nuevo,
            // SIN que el ViewModel de la sesión vieja se entere.
            val otraSesion = ReclamarCorreccion(port, FakeReloj(clock), FakeReencolar())
            otraSesion(SALE_ID)

            // La sesión vieja (este ViewModel) intenta guardar con el claimId ya inválido.
            viewModel.guardar(
                campos("La sesion vieja no debe ganar"),
                emptyList(),
                emptyList(),
                EMAIL
            )

            assertEquals(
                CorreccionUiState.NoCorregible(TextosCorreccion.NO_SE_PUDO_GUARDAR),
                viewModel.state.value
            )
        }

    @Test
    fun `cancelar suelta el candado, una nueva llamada a reclamar vuelve a tomarlo`() = runTest {
        port.siembra(SALE_ID, campos())
        viewModel.reclamar(SALE_ID)
        val primerClaimId = (viewModel.state.value as CorreccionUiState.Editando).claimId

        viewModel.cancelar(EMAIL)
        viewModel.reclamar(SALE_ID)

        val estado = viewModel.state.value
        assertTrue(estado is CorreccionUiState.Editando)
        assertNotEquals(
            "cancelar debe soltar el candado, no dejarlo colgado",
            primerClaimId,
            (estado as CorreccionUiState.Editando).claimId
        )
    }

    private fun producto(articuloId: Int) = LocalSaleProductEntity(
        LOCAL_SALE_ID = SALE_ID,
        ARTICULO_ID = articuloId,
        ARTICULO = "Articulo $articuloId",
        CANTIDAD = 1,
        PRECIO_LISTA = 100.0,
        PRECIO_CORTO_PLAZO = 110.0,
        PRECIO_CONTADO = 90.0
    )
}
