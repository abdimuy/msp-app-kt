package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.CargarDetalleVenta
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.application.RegistrarAbono
import com.example.msp_app.feature.pagos.application.ResolverVentanaDeCobro
import com.example.msp_app.feature.pagos.application.ReunirCobranzaDelCliente
import com.example.msp_app.feature.pagos.data.fake.FakeComprobantesPort
import com.example.msp_app.feature.pagos.data.fake.FakeGarantiasPort
import com.example.msp_app.feature.pagos.data.fake.FakeLiquidacionPort
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakeProductosPort
import com.example.msp_app.feature.pagos.data.fake.FakeRegistroDeAbonoPort
import com.example.msp_app.feature.pagos.data.fake.FakeTemaDeLaAppPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import com.example.msp_app.feature.pagos.domain.Comprobantes
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import com.example.msp_app.feature.pagos.domain.port.ResultadoDelAbono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **La foto cuelga del abono y nunca lo detiene** (Task 22).
 *
 * Vive en su propia clase y no dentro de `RegistrarAbonoViewModelTest` porque
 * aquélla ya cubre las tres invariantes del dinero y sumarle quince pruebas más
 * la volvía ilegible (detekt `LargeClass` lo dijo primero). El montaje es el
 * mismo, a propósito: es el ViewModel real con puertos falsos, sin MockK.
 *
 * Lo que estas pruebas protegen, en una línea: **ningún fallo de la cámara —ni
 * al preparar, ni al comprimir, ni al borrar— puede cambiar lo que pasa con el
 * abono.**
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // una prueba por camino de la camara; juntarlas esconde cual falló.
class RegistrarAbonoFotosTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)

    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()
    private val liquidacionPort = FakeLiquidacionPort()
    private val garantiasPort = FakeGarantiasPort()

    private val productosPort = FakeProductosPort()
    private val periodoPort = FakePeriodoDeCobroPort()
    private val registroPort = FakeRegistroDeAbonoPort()
    private val camaraPort = FakeComprobantesPort()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ventasPort.ventas = listOf(AbonoFixtures.datosDeVenta())
        liquidacionPort.liquidaciones = mapOf(
            AbonoFixtures.VENTA_ID to Liquidacion(
                monto = AbonoFixtures.LIQUIDACION,
                vigenteHasta = null,
                categoria = "precio a 4 meses"
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * **Sin foto**, que es el caso normal: el abono se registra igual y la lista
     * que viaja al puerto está vacía.
     */
    @Test
    fun `sin foto el abono se registra con la lista vacia`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        registrar(vm)

        assertEquals(1, registroPort.registrados.size)
        assertEquals(emptyList<Any>(), registroPort.registrados.single().comprobantes)
        assertEquals("nadie abrio la camara", 0, camaraPort.destinos.size)
    }

    /** **Con una foto:** viaja con el mismo id que la cámara acuñó. */
    @Test
    fun `con una foto el comprobante viaja con el id del destino`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        tomarFoto(vm)

        assertEquals(1, vm.state.value.comprobantes.size)
        registrar(vm)

        val comprobantes = registroPort.registrados.single().comprobantes
        assertEquals(1, comprobantes.size)
        assertEquals(
            "el id que viaja es el que se acuño ANTES de abrir la camara",
            camaraPort.destinos.single().id,
            comprobantes.single().id
        )
        assertEquals("image/jpeg", comprobantes.single().mime)
    }

    /**
     * **Con varias:** el ORDEN es contrato. La posición en esta lista es el
     * `ORDEN` con el que se persisten y el `n` de `id_<n>` en el multipart, así
     * que una lista desordenada parearía cada foto con el id de otra.
     */
    @Test
    fun `con varias fotos el orden de captura se conserva`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        repeat(3) { tomarFoto(vm) }
        registrar(vm)

        val comprobantes = registroPort.registrados.single().comprobantes
        assertEquals(3, comprobantes.size)
        assertEquals(camaraPort.destinos.map { it.id }, comprobantes.map { it.id })
    }

    /**
     * **La cámara que falla no toca el dinero.** El puerto lanza al preparar el
     * destino; el abono se captura y se registra exactamente igual, y el fallo
     * se reporta con su código.
     *
     * **Control de reversión:** quitar el `catch (Throwable)` de `pedirFoto`
     * propaga la excepción al `viewModelScope` y este test se pone ROJO.
     */
    @Test
    fun `si la camara falla el abono se registra igual y se reporta`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        camaraPort.fallaAlPreparar = IllegalStateException("camara ocupada por Victoria Flores")

        vm.onOrigen(OrigenDeLaFoto.CAMARA)
        advanceUntilIdle()

        assertNull("no quedo destino en vuelo", vm.state.value.destinoDeFoto)
        assertEquals(
            listOf(FalloDeLaFoto.NO_SE_PUDO_TOMAR),
            vm.state.value.intentos.map { it.motivo }
        )
        assertTrue("el abono NO se bloquea", vm.state.value.sePuedeRegistrar)
        assertNull("y el fallo del abono sigue limpio", vm.state.value.fallo)

        registrar(vm)
        assertEquals(1, registroPort.registrados.size)
        assertEquals(emptyList<Any>(), registroPort.registrados.single().comprobantes)

        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR &&
                it.name == PagosTelemetria.CODE_ABONO_FOTO_FALLO
        }
        assertEquals("IllegalStateException", error.props[PagosTelemetria.PROP_EXCEPCION])
        // Anti-PII: el texto libre de la excepción no viaja.
        assertFalse(error.props.values.any { it.contains("Victoria") })
    }

    /**
     * **El mismo fallo, un paso más adelante:** la cámara volvió pero procesar
     * la foto revienta (un OOM comprimiendo, en el teléfono de gama baja de
     * siempre). El abono tampoco se entera.
     */
    @Test
    fun `si procesar la foto falla el abono se registra igual`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        camaraPort.fallaAlAceptar = OutOfMemoryError("comprimiendo")

        vm.onOrigen(OrigenDeLaFoto.CAMARA)
        advanceUntilIdle()
        vm.fotoTomada()
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), vm.state.value.comprobantes)
        assertNull("el destino se suelta aunque falle", vm.state.value.destinoDeFoto)
        assertEquals(
            listOf(FalloDeLaFoto.NO_SE_PUDO_TOMAR),
            vm.state.value.intentos.map { it.motivo }
        )

        registrar(vm)
        assertEquals(ResultadoDelAbono.REGISTRADO, registroPort.resultado)
        assertEquals(1, registroPort.registrados.size)
    }

    /**
     * **Tipo no permitido:** el archivo no es de un tipo que el servidor acepte
     * —una cámara que contestó OK sin escribir deja `application/octet-stream`—
     * así que NO se adjunta, se borra, y el abono sigue su camino.
     *
     * Guardarlo aplazaría el rechazo hasta un 422 sobre el pago entero, o sea
     * la foto tumbando el dinero: exactamente lo que no puede pasar.
     */
    @Test
    fun `un tipo no permitido no se adjunta, se borra y se reporta`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        camaraPort.mime = "application/octet-stream"

        tomarFoto(vm)

        assertEquals("no se adjunto", emptyList<Any>(), vm.state.value.comprobantes)
        assertEquals(
            listOf(FalloDeLaFoto.TIPO_NO_PERMITIDO),
            vm.state.value.intentos.map { it.motivo }
        )
        assertEquals(
            "el archivo rechazado se borra en vez de quedarse ocupando disco",
            listOf(camaraPort.aceptados.single().archivo),
            camaraPort.descartados
        )
        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR &&
                it.name == PagosTelemetria.CODE_ABONO_FOTO_TIPO_NO_PERMITIDO
        }
        assertEquals("application/octet-stream", error.props[PagosTelemetria.PROP_TIPO])

        registrar(vm)
        assertEquals(1, registroPort.registrados.size)
        assertEquals(emptyList<Any>(), registroPort.registrados.single().comprobantes)
    }

    /**
     * **Control positivo del test de arriba.** El mismo montaje, con un tipo que
     * SÍ está en la whitelist, adjunta y no borra nada: el rechazo de arriba es
     * el filtro haciendo su trabajo, no el fake que nunca adjunta.
     */
    @Test
    fun `control positivo, el mismo montaje con jpeg si adjunta`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        camaraPort.mime = "image/jpeg"

        tomarFoto(vm)

        assertEquals(1, vm.state.value.comprobantes.size)
        assertEquals(emptyList<FalloDeLaFoto>(), vm.state.value.intentos.map { it.motivo })
        assertEquals(emptyList<String>(), camaraPort.descartados)
    }

    /** Quitar una foto la saca de la lista y **borra su archivo**. */
    @Test
    fun `quitar una foto borra su archivo`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        tomarFoto(vm)
        tomarFoto(vm)
        val primera = vm.state.value.comprobantes.first()

        vm.quitarFoto(primera.id)
        advanceUntilIdle()

        assertEquals(listOf(camaraPort.destinos[1].id), vm.state.value.comprobantes.map { it.id })
        assertEquals(listOf(primera.archivo), camaraPort.descartados)
    }

    /** La cámara que vuelve sin foto limpia el crudo vacío que dejó. */
    @Test
    fun `una camara cancelada borra el crudo`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onOrigen(OrigenDeLaFoto.CAMARA)
        advanceUntilIdle()

        vm.fotoCancelada()
        advanceUntilIdle()

        assertNull(vm.state.value.destinoDeFoto)
        assertEquals(emptyList<Any>(), vm.state.value.comprobantes)
        assertEquals(listOf(camaraPort.destinos.single().archivoCrudo), camaraPort.descartados)
    }

    /**
     * **El escenario que motiva el `SavedStateHandle`:** el proceso muere con la
     * cámara encima. Al volver, las fotos ya tomadas siguen ahí **con sus ids**,
     * y el destino en vuelo también — o el reintento de subida perdería su clave
     * estable.
     */
    @Test
    fun `las fotos y el destino sobreviven a la muerte del proceso`() = runTest(testDispatcher) {
        val handle = handle()
        val vm = viewModel(handle)
        advanceUntilIdle()
        tomarFoto(vm)
        vm.onOrigen(OrigenDeLaFoto.CAMARA)
        advanceUntilIdle()
        val idsAntes = vm.state.value.comprobantes.map { it.id }
        val destinoAntes = vm.state.value.destinoDeFoto

        // El proceso muere y el destino se reconstruye con el MISMO handle.
        val revivido = viewModel(handle)
        advanceUntilIdle()

        assertEquals(idsAntes, revivido.state.value.comprobantes.map { it.id })
        assertEquals(destinoAntes, revivido.state.value.destinoDeFoto)
    }

    /** Una entrada corrupta se descarta sin tumbar la pantalla, y se cuenta. */
    @Test
    fun `una entrada de comprobante ilegible se descarta y se reporta`() = runTest(testDispatcher) {
        val handle = handle()
        handle[CLAVE_COMPROBANTES] = arrayListOf("esto-no-es-un-comprobante")

        val vm = viewModel(handle)
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), vm.state.value.comprobantes)
        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR &&
                it.name == PagosTelemetria.CODE_ABONO_FOTO_ILEGIBLE
        }
        assertEquals("1", error.props[PagosTelemetria.PROP_OCURRENCIAS])
    }

    /** El techo del teléfono: la sexta foto no cabe y se dice por qué. */
    @Test
    fun `pasado el maximo el boton se apaga y avisa`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        repeat(Comprobantes.MAXIMO) { tomarFoto(vm) }

        assertFalse(vm.state.value.sePuedeAgregarFoto)
        vm.onOrigen(OrigenDeLaFoto.CAMARA)
        advanceUntilIdle()

        assertEquals(Comprobantes.MAXIMO, vm.state.value.comprobantes.size)
        assertEquals(
            "no se abrio una camara de mas",
            Comprobantes.MAXIMO,
            camaraPort.destinos.size
        )
        assertEquals(listOf(FalloDeLaFoto.YA_NO_CABEN), vm.state.value.intentos.map { it.motivo })
    }

    /**
     * Con la hoja de confirmación arriba **no se puede tocar la evidencia**: la
     * hoja enseña "1 comprobante" y lo que se registre tiene que ser eso. Es la
     * misma guarda que ya apaga el teclado.
     */
    @Test
    fun `con la hoja arriba no se agregan ni se quitan fotos`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        tomarFoto(vm)
        vm.pedirConfirmacion()

        assertFalse(vm.state.value.sePuedeAgregarFoto)
        vm.onOrigen(OrigenDeLaFoto.CAMARA)
        advanceUntilIdle()
        vm.quitarFoto(vm.state.value.comprobantes.single().id)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.comprobantes.size)
        assertEquals(1, camaraPort.destinos.size)
    }

    /** Toma una foto entera: pide el destino, la cámara contesta, se adjunta. */
    // ─── la galería y el archivo: lo que el selector devuelve ────────────────

    /**
     * **Varias de un tirón.** La hoja promete "puedes escoger varias", y las
     * varias llegan hasta el puerto **en el orden en que se eligieron** — el
     * orden es contrato, no presentación: es el `n` de `id_<n>` del multipart y
     * el `ORDEN` con el que se persisten.
     */
    @Test
    fun `de la galeria entran varias y en orden`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.archivosElegidos(listOf("content://media/1", "content://media/2"))
        advanceUntilIdle()
        registrar(vm)

        assertEquals(listOf("content://media/1", "content://media/2"), camaraPort.importados)
        assertEquals(
            listOf("ARCH-001", "ARCH-002"),
            registroPort.registrados.single().comprobantes.map { it.id }
        )
    }

    /**
     * **Las que caben entran; las que no, dejan SU cuadro.** Es el caso que el
     * aviso suelto de antes no podía contar: con cuatro elegidas y hueco para
     * dos, la pantalla tiene que decir cuáles dos no entraron, no "ese archivo no
     * se acepta" una sola vez.
     */
    @Test
    fun `lo que no cabe deja su propio cuadro ambar`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        repeat(Comprobantes.MAXIMO - 2) { tomarFoto(vm) }

        vm.archivosElegidos((1..4).map { "content://media/$it" })
        advanceUntilIdle()

        assertEquals(Comprobantes.MAXIMO, vm.state.value.comprobantes.size)
        assertEquals(
            listOf(FalloDeLaFoto.YA_NO_CABEN, FalloDeLaFoto.YA_NO_CABEN),
            vm.state.value.intentos.map { it.motivo }
        )
        assertTrue("el dinero no se entera", vm.state.value.sePuedeRegistrar)
    }

    /**
     * **El tipo no permitido se rechaza DICIENDO CUÁL**: el cuadro ámbar lleva el
     * id del archivo que se cayó, no un aviso suelto. Y el archivo se borra: nada
     * lo va a subir nunca.
     */
    @Test
    fun `un archivo de tipo no permitido deja su cuadro con su id y se borra`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            camaraPort.mimeImportado = "application/zip"

            vm.archivosElegidos(listOf("content://descargas/factura.zip"))
            advanceUntilIdle()

            assertEquals(emptyList<Any>(), vm.state.value.comprobantes)
            val intento = vm.state.value.intentos.single()
            assertEquals(FalloDeLaFoto.TIPO_NO_PERMITIDO, intento.motivo)
            assertEquals("el cuadro dice CUÁL archivo se cayó: lleva su id", "ARCH-001", intento.id)
            assertEquals(listOf("/tmp/fake/importado-1.jpg"), camaraPort.descartados)
            assertTrue(
                telemetria.recorded.any {
                    it.type == TelemetryEventType.ERROR &&
                        it.name == PagosTelemetria.CODE_ABONO_FOTO_TIPO_NO_PERMITIDO
                }
            )
        }

    /** Un fallo al importar deja su cuadro y **no toca el dinero**. */
    @Test
    fun `un fallo al importar no detiene el abono`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        camaraPort.fallaAlImportar = IllegalStateException("el proveedor no abrio nada")

        vm.archivosElegidos(listOf("content://media/1"))
        advanceUntilIdle()
        registrar(vm)

        assertEquals(
            listOf(FalloDeLaFoto.NO_SE_PUDO_TOMAR),
            vm.state.value.intentos.map { it.motivo }
        )
        assertNull("el fallo de la foto no es el fallo del abono", vm.state.value.fallo)
        assertEquals(1, registroPort.registrados.size)
    }

    /** Salir del selector sin elegir **no es un error**: no anota nada. */
    @Test
    fun `cancelar el selector no anota nada`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.archivosElegidos(emptyList())
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), vm.state.value.intentos)
        assertEquals(emptyList<Any>(), camaraPort.importados)
    }

    /** La petición de selector se suelta al atenderla: nadie reabre el selector solo. */
    @Test
    fun `la peticion de selector se suelta al atenderla`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onOrigen(OrigenDeLaFoto.ARCHIVO)
        assertEquals(OrigenDeLaFoto.ARCHIVO, vm.state.value.selectorPedido)
        vm.selectorAtendido()

        assertNull(vm.state.value.selectorPedido)
        assertFalse("y la hoja se cerró al elegir", vm.state.value.eligiendoOrigen)
    }

    // ─── la miniatura: es CÓMO se ve, no SI existe ───────────────────────────

    /** La miniatura se pide por la ruta del comprobante y se cuelga por su id. */
    @Test
    fun `la miniatura se pide y se cuelga por el id del comprobante`() = runTest(testDispatcher) {
        camaraPort.miniaturaDeCadaArchivo = AbonoFixtures.miniatura(0)
        val vm = viewModel()
        advanceUntilIdle()

        tomarFoto(vm)

        assertEquals(listOf("/tmp/fake/comprobante-IMG-001.jpg"), camaraPort.miniaturasPedidas)
        assertEquals(setOf("IMG-001"), vm.state.value.miniaturas.keys)
    }

    /**
     * **Sin miniatura, el comprobante sigue adjunto.** La miniatura es cómo se
     * ve, no si existe: un PDF nunca tiene una, y eso no es un fallo — por eso
     * tampoco pinta ámbar.
     */
    @Test
    fun `sin miniatura el comprobante sigue adjunto y no hay cuadro ambar`() =
        runTest(testDispatcher) {
            camaraPort.miniaturaDeCadaArchivo = null
            val vm = viewModel()
            advanceUntilIdle()

            tomarFoto(vm)
            registrar(vm)

            assertEquals(1, registroPort.registrados.single().comprobantes.size)
            assertEquals(emptyMap<String, Any>(), vm.state.value.miniaturas)
            assertEquals(emptyList<Any>(), vm.state.value.intentos)
        }

    /**
     * Y si decodificar revienta, se reporta con **su propio código** —no con el
     * de "la foto falló"— y el comprobante sigue adjunto. Un OOM al hacer una
     * vista previa no puede convertirse en evidencia perdida.
     */
    @Test
    fun `una miniatura que revienta se reporta sin pintar ambar`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        camaraPort.fallaAlPedirMiniatura = OutOfMemoryError("no hay heap")

        tomarFoto(vm)

        assertEquals(1, vm.state.value.comprobantes.size)
        assertEquals(emptyList<Any>(), vm.state.value.intentos)
        assertTrue(
            telemetria.recorded.any {
                it.type == TelemetryEventType.ERROR &&
                    it.name == PagosTelemetria.CODE_ABONO_FOTO_SIN_MINIATURA
            }
        )
    }

    /** Quitar un cuadro ÁMBAR lo borra de la rejilla y no manda borrar ningún archivo. */
    @Test
    fun `quitar un cuadro ambar no borra ningun archivo`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        camaraPort.mimeImportado = "application/zip"
        vm.archivosElegidos(listOf("content://descargas/factura.zip"))
        advanceUntilIdle()
        camaraPort.descartados.clear()

        vm.quitarFoto(vm.state.value.intentos.single().id)
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), vm.state.value.intentos)
        assertEquals(
            "el archivo ya se había borrado al rechazarlo; no se borra dos veces",
            emptyList<String>(),
            camaraPort.descartados
        )
    }

    private fun TestScope.tomarFoto(vm: RegistrarAbonoViewModel) {
        vm.onOrigen(OrigenDeLaFoto.CAMARA)
        advanceUntilIdle()
        vm.fotoTomada()
        advanceUntilIdle()
    }

    /** Los dos pasos de la confirmación, sin tocar el monto prellenado. */
    private fun TestScope.registrar(vm: RegistrarAbonoViewModel) {
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()
    }

    // --- La ventana entre la muerte del proceso y la carga (ronda 1, I-1) ----

    /**
     * **La carrera que el arreglo cierra, ejercitada de verdad.**
     *
     * El proceso murió con la cámara encima. El ViewModel se reconstruye y el
     * resultado de la cámara llega **antes** de que la carga resuelva: aquí NO
     * se llama `advanceUntilIdle()` antes de `fotoTomada()`, y esa omisión es el
     * test entero. Con el destino leído del estado, en esta ventana
     * `state.destinoDeFoto` es `null`, la foto se perdía en silencio y la carga
     * reponía después el destino — la pantalla reabría la cámara.
     *
     * **Control de reversión:** volver a `mutableState.value.destinoDeFoto` en
     * `tomarDestinoPendiente` pone este test en ROJO.
     */
    @Test
    fun `una foto que llega ANTES de que la carga resuelva no se pierde`() =
        runTest(testDispatcher) {
            val handle = handleConDestinoPendiente()
            val vm = viewModel(handle)

            // Sin `advanceUntilIdle()`: la carga sigue en vuelo, como en el
            // teléfono real cuando el resultado de la cámara llega primero.
            assertNull(
                "la ventana existe: el estado aun no tiene el destino",
                vm.state.value.destinoDeFoto
            )
            vm.fotoTomada()
            advanceUntilIdle()

            assertEquals(
                "la foto se adjunto con el id que se acuño antes de morir el proceso",
                listOf(DESTINO_PENDIENTE_ID),
                vm.state.value.comprobantes.map { it.id }
            )
            assertNull(
                "y el destino quedo suelto: la camara no se vuelve a abrir",
                vm.state.value.destinoDeFoto
            )
        }

    /**
     * El otro lado de la misma carrera: cuando la carga termina **después**, no
     * puede reponer un destino que la cámara ya atendió. Si lo repusiera, el
     * `LaunchedEffect` de la pantalla dispararía la cámara otra vez.
     */
    @Test
    fun `la carga no repone un destino que la camara ya atendio`() = runTest(testDispatcher) {
        val vm = viewModel(handleConDestinoPendiente())
        vm.fotoTomada()
        advanceUntilIdle()

        assertNull(vm.state.value.destinoDeFoto)
        assertEquals(1, vm.state.value.comprobantes.size)
        // Y una recarga explícita tampoco lo resucita.
        vm.cargar()
        advanceUntilIdle()
        assertNull(vm.state.value.destinoDeFoto)
    }

    /** Una cancelación en la misma ventana también encuentra su crudo y lo borra. */
    @Test
    fun `una camara cancelada en la ventana borra el crudo igual`() = runTest(testDispatcher) {
        val vm = viewModel(handleConDestinoPendiente())

        vm.fotoCancelada()
        advanceUntilIdle()

        assertEquals(listOf(CRUDO_PENDIENTE), camaraPort.descartados)
        assertNull(vm.state.value.destinoDeFoto)
    }

    /**
     * Y si de verdad no hay destino que reclame la foto, **no se traga**: lleva
     * su propio código, porque es la única forma que tiene una foto de perderse
     * en este camino.
     */
    @Test
    fun `una foto sin destino se reporta en vez de desaparecer`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.fotoTomada()
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), vm.state.value.comprobantes)
        assertTrue(
            telemetria.recorded.any {
                it.type == TelemetryEventType.ERROR &&
                    it.name == PagosTelemetria.CODE_ABONO_FOTO_SIN_DESTINO
            }
        )
    }

    /** El `SavedStateHandle` de un proceso que murió con la cámara encima. */
    private fun handleConDestinoPendiente() = handle().also {
        it[CLAVE_DESTINO] = listOf(
            DESTINO_PENDIENTE_ID,
            "content://fake/camara/pendiente",
            CRUDO_PENDIENTE
        ).joinToString("\n")
    }

    /** El `SavedStateHandle` del destino, con la clave del abono fijada. */
    private fun handle() = SavedStateHandle(
        mapOf(
            PagosRutas.ARG_VENTA_ID to AbonoFixtures.VENTA_ID,
            CLAVE_ABONO_ID to "abono-fijo-de-prueba"
        )
    )

    private fun viewModel(savedStateHandle: SavedStateHandle = handle()) = RegistrarAbonoViewModel(
        savedStateHandle = savedStateHandle,
        cargarDetalleVenta = CargarDetalleVenta(
            ventasPort = ventasPort,
            garantiasPort = garantiasPort,
            productosPort = productosPort,
            reunirCobranzaDelCliente = ReunirCobranzaDelCliente(
                ventasPort = ventasPort,
                pagosPort = pagosPort,
                visitasPort = visitasPort,
                liquidacionPort = liquidacionPort,
                resolverVentanaDeCobro = ResolverVentanaDeCobro(periodoPort, clock),
                derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria)
            ),
            clock = clock
        ),
        registrarAbono = RegistrarAbono(registroPort, telemetria),
        camara = camaraPort,
        tema = FakeTemaDeLaAppPort(),
        telemetry = telemetria,
        clock = clock,
        io = testDispatcher
    )

    private companion object {
        /**
         * Las MISMAS llaves que el ViewModel persiste, escritas a mano: son el
         * contrato que sobrevive a la muerte del proceso, y un test que las
         * tomara de la constante privada no notaría que alguien las renombró.
         */
        const val CLAVE_ABONO_ID = "pagos_abono_id"
        const val CLAVE_COMPROBANTES = "pagos_abono_comprobantes"
        const val CLAVE_DESTINO = "pagos_abono_destino_foto"

        /** El id acuñado antes de que el proceso muriera con la cámara encima. */
        const val DESTINO_PENDIENTE_ID = "IMG-SOBREVIVIENTE"
        const val CRUDO_PENDIENTE = "/tmp/fake/crudo-pendiente.jpg"
    }
}
