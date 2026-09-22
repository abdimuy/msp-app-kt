package com.example.msp_app.feature.ventacorreccion.ui

import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.core.testing.MainDispatcherRule
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.ventacorreccion.data.fake.FakeReencolar
import com.example.msp_app.feature.ventacorreccion.data.fake.FakeReloj
import com.example.msp_app.feature.ventacorreccion.data.fake.FakeVentaLocalCorreccionPort
import com.example.msp_app.feature.ventacorreccion.domain.CamposVentaCorregidos
import com.example.msp_app.feature.ventacorreccion.domain.CorreccionRemotaTerminal
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

    /**
     * El cambio de fondo del nivel 2: una venta ya enviada y limpia SÍ abre el editor — el
     * servidor la tiene en `borrador` y lo que se guarde viajará por la cola de correcciones
     * remotas. Hasta el nivel 1 esta misma siembra dejaba el estado en `NoCorregible("Ya se
     * envió")` y el dueño se quedaba sin salida sobre su propia venta.
     */
    @Test
    fun `reclamar sobre una venta ya enviada y limpia SI abre el editor`() = runTest {
        port.siembra(SALE_ID, campos("Rosa Elena Martinez"), listOf(producto(1)), enviado = true)

        viewModel.reclamar(SALE_ID)

        val estado = viewModel.state.value
        assertTrue(estado is CorreccionUiState.Editando)
        estado as CorreccionUiState.Editando
        assertEquals("Rosa Elena Martinez", estado.campos.nombreCliente)
    }

    /**
     * `Editando.yaEnviada` es lo único que el formulario (`EditSaleScreen`, en `:app`) tiene para
     * saber que el **tipo de venta** (CONTADO/CRÉDITO) debe ir de SÓLO LECTURA. La razón no es de
     * UI: una venta ya enviada se corrige con TRES peticiones —`PATCH /v2/ventas/{id}` (header),
     * `PATCH /v2/ventas/{id}/cliente` y `PUT /v2/ventas/{id}/lineas`— y **ninguna de las tres
     * lleva el tipo de venta**. Si el ViewModel dejara caer este campo entre
     * [com.example.msp_app.feature.ventacorreccion.domain.usecase.ResultadoReclamo.Reclamada] y
     * [CorreccionUiState.Editando], el desplegable volvería a la vida y el cambio se guardaría en
     * el teléfono sin llegar nunca a la oficina, sin que nada avise.
     *
     * El día que el servidor gane un endpoint que SÍ cambie el tipo de venta, ésta y su gemela de
     * `ReclamarCorreccionTipoDeVentaTest` son el lugar donde quien lo agregue se entera de que el
     * campo se puede reabrir — la respuesta correcta entonces no es ajustar el `assert`, es
     * quitar el bloqueo de `EditSaleScreen` y mandar el tipo en la petición que ya lo lleve.
     */
    @Test
    fun `una venta ya enviada llega a Editando con yaEnviada en true - ninguna peticion de correccion lleva el tipo de venta`() =
        runTest {
            port.siembra(SALE_ID, campos(), listOf(producto(1)), enviado = true)

            viewModel.reclamar(SALE_ID)

            val estado = viewModel.state.value
            assertTrue(estado is CorreccionUiState.Editando)
            assertEquals(
                "sin propagarlo, el tipo de venta queda editable sobre una venta que ya subio",
                true,
                (estado as CorreccionUiState.Editando).yaEnviada
            )
        }

    /**
     * El otro lado: una venta que todavía no sube viaja ENTERA en su `POST` de alta, el tipo de
     * venta incluido, así que no hay nada que bloquear. `false` es además el valor por omisión de
     * [CorreccionUiState.Editando] — el lado conservador, idéntico al comportamiento del nivel 1.
     */
    @Test
    fun `una venta sin enviar llega a Editando con yaEnviada en false - el alta si lleva el tipo de venta`() =
        runTest {
            port.siembra(SALE_ID, campos(), listOf(producto(1)), enviado = false)

            viewModel.reclamar(SALE_ID)

            val estado = viewModel.state.value
            assertTrue(estado is CorreccionUiState.Editando)
            assertEquals(
                "una venta que nunca subio no tiene por que perder campos editables",
                false,
                (estado as CorreccionUiState.Editando).yaEnviada
            )
        }

    @Test
    fun `reclamar sobre una venta con la correccion ya en la cola muestra Correccion en camino`() =
        runTest {
            port.siembra(SALE_ID, campos(), enviado = true, correccionRemotaPendiente = true)

            viewModel.reclamar(SALE_ID)

            assertEquals(
                CorreccionUiState.NoCorregible(TextosCorreccion.CORRECCION_EN_CAMINO),
                viewModel.state.value
            )
        }

    @Test
    fun `reclamar sobre una venta que el servidor ya cerro muestra La aplico la oficina`() =
        runTest {
            port.siembra(
                SALE_ID,
                campos(),
                enviado = true,
                correccionRemotaEstado = CorreccionRemotaTerminal.RECHAZADA_ESTADO
            )

            viewModel.reclamar(SALE_ID)

            assertEquals(
                CorreccionUiState.NoCorregible(TextosCorreccion.LA_APLICO_LA_OFICINA),
                viewModel.state.value
            )
        }

    @Test
    fun `reclamar sobre una venta enviada con divergencia marcada muestra La revisa la oficina`() =
        runTest {
            port.siembra(SALE_ID, campos(), enviado = true, correccionNoEnviada = true)

            viewModel.reclamar(SALE_ID)

            assertEquals(
                CorreccionUiState.NoCorregible(TextosCorreccion.LA_REVISA_LA_OFICINA),
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

    /**
     * Guardar sobre una venta que YA subió: el estado va a `Guardada` igual que siempre, y la
     * fila queda con `ENVIADO = 1` **y** la cola levantada. Este es el invariante caro del nivel
     * 2 visto desde la UI — la prueba que lo clava contra Room de verdad vive en
     * `CorreccionCasosDeUsoTest`; ésta es su gemela rápida sobre el fake, para que el contrato
     * del puerto no se despegue en silencio.
     */
    @Test
    fun `guardar sobre una venta ya enviada deja ENVIADO en 1 y la correccion en la cola`() =
        runTest {
            port.siembra(SALE_ID, campos(), enviado = true)
            viewModel.reclamar(SALE_ID)

            viewModel.guardar(campos("Nombre corregido"), emptyList(), emptyList(), EMAIL)

            assertEquals(CorreccionUiState.Guardada, viewModel.state.value)
            assertEquals(
                "bajar ENVIADO devolveria la venta a la cola de ALTA con su Idempotency-Key",
                true,
                port.enviadoDe(SALE_ID)
            )
            assertEquals(
                "sin la bandera, nadie entrega la correccion y el servidor nunca se entera",
                true,
                port.correccionRemotaPendienteDe(SALE_ID)
            )
        }

    /**
     * El guardado se rechaza porque el candado del llamador ya no es el vigente (aquí: el
     * subidor ganó la carrera y la fila se rehizo). Desde el nivel 2 la relectura clasifica ese
     * rechazo como [com.example.msp_app.feature.ventacorreccion.domain.EstadoCorreccion.CorregibleEnviada]
     * — y ése NO es el texto que se muestra: decirle "Corregir venta" a quien acaba de apretar
     * "Guardar corrección" y falló es exactamente la mentira que
     * `aTextoDeRechazoDeGuardado` existe para evitar.
     */
    @Test
    fun `guardar rechazado (el candado se perdio) muestra No se pudo guardar, no Corregir venta`() =
        runTest {
            port.siembra(SALE_ID, campos())
            viewModel.reclamar(SALE_ID)

            // Mientras el dueño editaba, la venta se envió (el subidor ganó la carrera): el
            // claimId que la UI todavía sostiene ya no es el vigente.
            port.siembra(SALE_ID, campos(), enviado = true)

            viewModel.guardar(campos("Llega tarde"), emptyList(), emptyList(), EMAIL)

            assertEquals(
                CorreccionUiState.NoCorregible(TextosCorreccion.NO_SE_PUDO_GUARDAR),
                viewModel.state.value
            )
        }

    /**
     * El otro choque de sesiones del nivel 2: la otra sesión alcanzó a COMMITEAR su corrección
     * (`CORRECCION_REMOTA_PENDIENTE = 1`) entre el guardia y la relectura. Decir "Corrección en
     * camino" justo después de apretar "Guardar corrección" se leería como acuse de recibo de
     * ESTE guardado, y los cambios que viajan son los de la otra sesión — por eso
     * `aTextoDeRechazoDeGuardado` la manda, a propósito, a
     * [TextosCorreccion.NO_SE_PUDO_GUARDAR], apartándose de `aTexto`.
     */
    @Test
    fun `guardar rechazado con la correccion de otra sesion ya en la cola tampoco acusa recibo`() =
        runTest {
            port.siembra(SALE_ID, campos(), enviado = true)
            viewModel.reclamar(SALE_ID)

            // La otra sesión commiteó primero: la fila queda con la cola levantada y sin el
            // candado de esta sesión.
            port.siembra(SALE_ID, campos(), enviado = true, correccionRemotaPendiente = true)

            viewModel.guardar(campos("Llega tarde"), emptyList(), emptyList(), EMAIL)

            assertEquals(
                CorreccionUiState.NoCorregible(TextosCorreccion.NO_SE_PUDO_GUARDAR),
                viewModel.state.value
            )
        }

    /**
     * En cambio [TextosCorreccion.LA_APLICO_LA_OFICINA] SÍ conserva su texto en el rechazo de un
     * guardado: no se puede leer como que este guardado funcionó, y es la única razón por la que
     * no habrá otra oportunidad — esconderla detrás de "No se pudo guardar" invitaría a
     * reintentar contra una puerta que el servidor cerró para siempre.
     */
    @Test
    fun `guardar rechazado porque el servidor ya cerro la puerta SI dice que la aplico la oficina`() =
        runTest {
            port.siembra(SALE_ID, campos(), enviado = true)
            viewModel.reclamar(SALE_ID)

            port.siembra(
                SALE_ID,
                campos(),
                enviado = true,
                correccionRemotaEstado = CorreccionRemotaTerminal.RECHAZADA_ESTADO
            )

            viewModel.guardar(campos("Llega tarde"), emptyList(), emptyList(), EMAIL)

            assertEquals(
                CorreccionUiState.NoCorregible(TextosCorreccion.LA_APLICO_LA_OFICINA),
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
