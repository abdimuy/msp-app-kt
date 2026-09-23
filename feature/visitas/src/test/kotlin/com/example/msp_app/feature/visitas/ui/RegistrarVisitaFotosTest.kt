package com.example.msp_app.feature.visitas.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.visitas.application.AbrirRegistroDeVisita
import com.example.msp_app.feature.visitas.application.RegistrarVisita
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import com.example.msp_app.feature.visitas.data.fake.DictadoFalso
import com.example.msp_app.feature.visitas.data.fake.FakeComprobantesDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeContextoDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeRecomendacionesPort
import com.example.msp_app.feature.visitas.data.fake.FakeRegistroDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeTemaDeLaAppPort
import com.example.msp_app.feature.visitas.data.fake.FakeUbicacionPort
import com.example.msp_app.feature.visitas.data.fake.VisitasFixtures
import com.example.msp_app.feature.visitas.domain.ComprobantesDeVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * **La foto de la visita nunca detiene el registro.**
 *
 * Estas pruebas viven aparte de `RegistrarVisitaViewModelTest` por el mismo
 * motivo que las de pagos: son un camino entero (cámara, tipo, tope, muerte de
 * proceso) y meterlas allá volvería esa clase ilegible.
 *
 * Lo que cada grupo protege:
 *
 * 1. **La foto llega hasta el puerto** — con su id, en su orden.
 * 2. **Nada de lo que falle en la cámara toca la visita** — ni el resultado, ni
 *    el guard, ni el CTA.
 * 3. **La ventana de arranque** — el defecto que la Task 22 tuvo que arreglar
 *    después. Los tests de abajo **actúan sin resolver la carga primero**, que
 *    es lo único que hace que la carrera realmente ocurra.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // una prueba por camino; juntarlas escondería cuál se rompió.
class RegistrarVisitaFotosTest {

    private val ahora = Instant.parse("2026-09-01T15:52:00Z")

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeClock(ahora)
    private val telemetria = RecordingTelemetry(clock)

    private val contextoPort = FakeContextoDeVisitaPort()
    private val recomendacionesPort = FakeRecomendacionesPort()
    private val registroPort = FakeRegistroDeVisitaPort()
    private val ubicacionPort = FakeUbicacionPort()
    private val dictadoPort = DictadoFalso()
    private val camaraPort = FakeComprobantesDeVisitaPort()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── la foto llega hasta el puerto ───────────────────────────────────────

    /** Sin foto, la visita se registra igual y la lista viaja vacía. */
    @Test
    fun `sin foto la visita se registra con la lista vacia`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        registrar(vm)

        assertEquals(emptyList<Any>(), registroPort.registradas.single().comprobantes)
    }

    /**
     * Con una foto, el comprobante viaja **con el id del destino** — el que se
     * acuñó antes de abrir la cámara y el que el servidor exige como `id_<n>`.
     */
    @Test
    fun `con una foto el comprobante viaja con el id del destino`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        tomarFoto(vm)
        registrar(vm)

        val comprobantes = registroPort.registradas.single().comprobantes
        assertEquals(1, comprobantes.size)
        assertEquals(camaraPort.destinos.single().id, comprobantes.single().id)
    }

    /** Con varias, el ORDEN de captura se conserva hasta el puerto. */
    @Test
    fun `con varias fotos el orden de captura se conserva`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        repeat(3) { tomarFoto(vm) }
        registrar(vm)

        assertEquals(
            listOf("IMG-1", "IMG-2", "IMG-3"),
            registroPort.registradas.single().comprobantes.map { it.id }
        )
    }

    /**
     * **Cambiar de desenlace NO borra las fotos.** `onResultado` limpia la
     * captura a propósito —un compromiso arrastrado es un compromiso que el
     * cliente no hizo—, y la foto no es parte de ese compromiso: el cobrador
     * fotografió la puerta y la puerta sigue fotografiada.
     */
    @Test
    fun `cambiar de resultado no borra las fotos`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onResultado(ResultadoDeVisita.PROMETIO)
        tomarFoto(vm)

        vm.onResultado(ResultadoDeVisita.NO_ESTABA)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.comprobantes.size)
    }

    /** Una recarga tampoco: el estado se reconstruye pero lo capturado se cuelga. */
    @Test
    fun `las fotos sobreviven a una recarga`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        tomarFoto(vm)

        vm.cargar()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.comprobantes.size)
    }

    /** Quitar una foto la saca de la lista y borra su archivo. */
    @Test
    fun `quitar una foto borra su archivo`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        tomarFoto(vm)
        val comprobante = vm.state.value.comprobantes.single()

        vm.quitarFoto(comprobante.id)
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), vm.state.value.comprobantes)
        assertTrue(comprobante.archivo in camaraPort.descartados)
    }

    // ─── nada de la cámara toca la visita ────────────────────────────────────

    /**
     * **La cámara falla y la visita se registra igual.** Es la regla que manda
     * sobre esta tarea entera, y la misma que la Task 5 fijó para la ubicación.
     */
    @Test
    fun `si la camara falla la visita se registra igual y se reporta`() = runTest(testDispatcher) {
        camaraPort.fallaAlPreparar = IllegalStateException("no hay camara")
        val vm = viewModel()
        advanceUntilIdle()

        vm.onOrigen(OrigenDeLaFoto.CAMARA)
        advanceUntilIdle()

        assertEquals(
            listOf(FalloDeLaFoto.NO_SE_PUDO_TOMAR),
            vm.state.value.intentos.map { it.motivo }
        )
        assertNull("el fallo de la foto no es el fallo de la visita", vm.state.value.fallo)

        registrar(vm)
        assertEquals(vm.visitaId, vm.state.value.registrada)
        assertEquals(emptyList<Any>(), registroPort.registradas.single().comprobantes)
        assertTrue(
            telemetria.recorded.any {
                it.type == TelemetryEventType.ERROR &&
                    it.name == VisitasTelemetria.CODE_VISITA_FOTO_FALLO
            }
        )
    }

    /** Procesar la foto también puede reventar (un OOM al comprimir). Igual. */
    @Test
    fun `si procesar la foto falla la visita se registra igual`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onOrigen(OrigenDeLaFoto.CAMARA)
        advanceUntilIdle()
        camaraPort.fallaAlAceptar = OutOfMemoryError("bitmap")

        vm.fotoTomada()
        advanceUntilIdle()

        assertEquals(
            listOf(FalloDeLaFoto.NO_SE_PUDO_TOMAR),
            vm.state.value.intentos.map { it.motivo }
        )
        assertEquals(emptyList<Any>(), vm.state.value.comprobantes)
        registrar(vm)
        assertEquals(vm.visitaId, vm.state.value.registrada)
    }

    /**
     * **Un tipo no permitido se descarta acá, con su archivo.** En visitas eso
     * pesa más que en pagos: un MIME fuera de la whitelist hace que el servidor
     * conteste 422 a la **visita entera**, no solo a la foto.
     */
    @Test
    fun `un tipo no permitido no se adjunta, se borra y se reporta`() = runTest(testDispatcher) {
        camaraPort.mimeAceptado = "image/heic"
        val vm = viewModel()
        advanceUntilIdle()

        tomarFoto(vm)

        assertEquals(emptyList<Any>(), vm.state.value.comprobantes)
        assertEquals(
            listOf(FalloDeLaFoto.TIPO_NO_PERMITIDO),
            vm.state.value.intentos.map { it.motivo }
        )
        assertEquals(1, camaraPort.descartados.size)
        val evento = telemetria.recorded.single {
            it.name == VisitasTelemetria.CODE_VISITA_FOTO_TIPO_NO_PERMITIDO
        }
        assertEquals("image/heic", evento.props[VisitasTelemetria.PROP_TIPO])
    }

    /**
     * **Control positivo del anterior.** El mismo montaje con un jpeg SÍ
     * adjunta, así que el rechazo de arriba no puede ser una cámara que dejó de
     * funcionar.
     */
    @Test
    fun `control positivo, un jpeg si se adjunta`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        tomarFoto(vm)

        assertEquals(1, vm.state.value.comprobantes.size)
        assertEquals(emptyList<FalloDeLaFoto>(), vm.state.value.intentos.map { it.motivo })
        assertEquals(emptyList<Any>(), camaraPort.descartados)
    }

    /** El tope: al llegar a [ComprobantesDeVisita.MAXIMO] no se acuña un destino más. */
    @Test
    fun `al llegar al tope no se pide otro destino y avisa`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        repeat(ComprobantesDeVisita.MAXIMO) { tomarFoto(vm) }

        vm.onOrigen(OrigenDeLaFoto.CAMARA)
        advanceUntilIdle()

        assertEquals(ComprobantesDeVisita.MAXIMO, camaraPort.destinos.size)
        assertEquals(listOf(FalloDeLaFoto.YA_NO_CABEN), vm.state.value.intentos.map { it.motivo })
        assertFalse(vm.state.value.sePuedeAgregarFoto)
        assertTrue("el CTA de la visita sigue vivo", vm.state.value.sePuedeCapturar)
    }

    /**
     * Dos toques rápidos no acuñan dos destinos. El guard se pone
     * **sincrónicamente antes** del `launch`, igual que el de la visita.
     */
    @Test
    fun `dos toques rapidos acuñan un solo destino`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onOrigen(OrigenDeLaFoto.CAMARA)
        vm.onOrigen(OrigenDeLaFoto.CAMARA)
        advanceUntilIdle()

        assertEquals(1, camaraPort.destinos.size)
    }

    /** Con la visita ya registrada no se puede adjuntar más. */
    @Test
    fun `con la visita registrada no se adjunta nada`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        registrar(vm)

        vm.onOrigen(OrigenDeLaFoto.CAMARA)
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), camaraPort.destinos)
        assertFalse(vm.state.value.sePuedeAgregarFoto)
    }

    // ─── la ventana de arranque (el defecto de la Task 22) ───────────────────

    /**
     * **La carrera de verdad:** el proceso murió con la cámara encima, el
     * ViewModel se reconstruyó y el resultado llega **antes** de que la carga
     * resuelva.
     *
     * El test actúa SIN `advanceUntilIdle()` previo, y afirma primero que la
     * ventana existe. Ese orden es lo único que hace que la carrera ocurra: con
     * el estado ya resuelto, el defecto no se manifiesta y el test pasa con el
     * bug puesto — que es exactamente lo que pasó en la Task 22.
     */
    @Test
    fun `una foto que llega ANTES de que la carga resuelva no se pierde`() =
        runTest(testDispatcher) {
            val vm = viewModel(handleConDestinoPendiente())

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
     * El otro lado de la misma carrera: la carga que termina **después** no
     * puede reponer un destino que la cámara ya atendió. Si lo repusiera, el
     * `LaunchedEffect` de la pantalla dispararía la cámara otra vez — desde
     * afuera, un bucle.
     */
    @Test
    fun `la carga no repone un destino que la camara ya atendio`() = runTest(testDispatcher) {
        val vm = viewModel(handleConDestinoPendiente())
        vm.fotoTomada()
        advanceUntilIdle()

        assertNull(vm.state.value.destinoDeFoto)
        assertEquals(1, vm.state.value.comprobantes.size)
        vm.cargar()
        advanceUntilIdle()
        assertNull("ni una recarga explicita lo resucita", vm.state.value.destinoDeFoto)
    }

    /** Una cámara cancelada en la MISMA ventana borra el crudo igual. */
    @Test
    fun `una camara cancelada en la ventana borra el crudo igual`() = runTest(testDispatcher) {
        val vm = viewModel(handleConDestinoPendiente())

        vm.fotoCancelada()
        advanceUntilIdle()

        assertEquals(listOf(CRUDO_PENDIENTE), camaraPort.descartados)
        assertNull(vm.state.value.destinoDeFoto)
    }

    /**
     * Una foto que llega sin destino que la reclame **no se traga**: es la única
     * forma que tiene una foto de perderse en este camino.
     */
    @Test
    fun `una foto sin destino se reporta`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.fotoTomada()
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), vm.state.value.comprobantes)
        assertTrue(
            telemetria.recorded.any {
                it.type == TelemetryEventType.ERROR &&
                    it.name == VisitasTelemetria.CODE_VISITA_FOTO_SIN_DESTINO
            }
        )
    }

    /**
     * Una entrada guardada que ya no se puede leer se descarta —no puede tumbar
     * la pantalla— pero se **cuenta y se reporta**.
     */
    @Test
    fun `una entrada ilegible del handle se descarta y se reporta`() = runTest(testDispatcher) {
        val handle = handle().also {
            it[CLAVE_COMPROBANTES] = arrayListOf("basura-sin-campos", "IMG-9\nimage/jpeg\n/f.jpg")
        }
        val vm = viewModel(handle)
        advanceUntilIdle()

        assertEquals(listOf("IMG-9"), vm.state.value.comprobantes.map { it.id })
        val evento = telemetria.recorded.single {
            it.name == VisitasTelemetria.CODE_VISITA_FOTO_ILEGIBLE
        }
        assertEquals("1", evento.props[VisitasTelemetria.PROP_OCURRENCIAS])
    }

    // ─── la ventana del guardado (F1) ────────────────────────────────────────

    /**
     * **La carrera del cobrador con prisa:** la foto todavía se está
     * comprimiendo cuando toca "guardar".
     *
     * El test actúa DENTRO de la ventana —`fotoTomada()` sin `advanceUntilIdle()`
     * después, y `guardar()` acto seguido— y afirma primero que la ventana
     * existe (el estado todavía no tiene la foto).
     *
     * **Lo que se garantiza NO es que la foto entre.** No puede: esperarla sería
     * que la foto bloquee el guardado, que es la regla que manda sobre esta
     * tarea entera. Lo que se garantiza es que **no desaparezca en silencio** —
     * lleva su propio código, el cobrador ve el aviso, y el archivo que nadie va
     * a subir no se queda ocupando disco.
     */
    @Test
    fun `una foto que se comprime mientras se guarda no se pierde en silencio`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.onResultado(ResultadoDeVisita.NO_ESTABA)
            vm.onOrigen(OrigenDeLaFoto.CAMARA)
            advanceUntilIdle()

            vm.fotoTomada()
            assertEquals(
                "la ventana existe: la foto aun no esta en el estado",
                emptyList<Any>(),
                vm.state.value.comprobantes
            )
            vm.guardar()
            advanceUntilIdle()

            assertEquals("la visita se registra igual", vm.visitaId, vm.state.value.registrada)
            assertEquals(
                listOf(FalloDeLaFoto.LLEGO_TARDE),
                vm.state.value.intentos.map { it.motivo }
            )
            assertTrue(
                "el archivo que nadie va a subir no se queda en disco",
                camaraPort.descartados.isNotEmpty()
            )
            val evento = telemetria.recorded.single {
                it.type == TelemetryEventType.ERROR &&
                    it.name == VisitasTelemetria.CODE_VISITA_FOTO_TARDE
            }
            assertEquals(VisitasTelemetria.CODE_VISITA_FOTO_TARDE, evento.name)
        }

    /**
     * **Control positivo de la ventana:** el MISMO montaje, con la compresión
     * resuelta antes del toque, sí lleva la foto al write.
     *
     * Sin esto, la prueba de arriba no distinguiría "llegó tarde y se reportó"
     * de "las fotos nunca llegan al write".
     */
    @Test
    fun `control positivo, con la compresion resuelta la foto si entra al write`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.onResultado(ResultadoDeVisita.NO_ESTABA)
            tomarFoto(vm)

            vm.guardar()
            advanceUntilIdle()

            assertEquals(
                listOf("IMG-1"),
                registroPort.registradas.single().comprobantes.map { it.id }
            )
            assertEquals(emptyList<FalloDeLaFoto>(), vm.state.value.intentos.map { it.motivo })
        }

    /**
     * El otro lado: la foto que termina cuando la escritura **ya se llevó la
     * lista**. No puede entrar —la visita ya está escrita y encolada— así que lo
     * único que se puede hacer bien es **no callarlo** y no dejar el archivo
     * ocupando disco.
     */
    @Test
    fun `una foto que llega despues de la escritura se reporta y se borra`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.onResultado(ResultadoDeVisita.NO_ESTABA)
            vm.onOrigen(OrigenDeLaFoto.CAMARA)
            advanceUntilIdle()

            vm.guardar()
            advanceUntilIdle()
            assertEquals(vm.visitaId, vm.state.value.registrada)

            vm.fotoTomada()
            advanceUntilIdle()

            assertEquals(
                "la visita se escribio sin ella, y no se le agrega despues",
                emptyList<Any>(),
                registroPort.registradas.single().comprobantes
            )
            assertEquals(emptyList<Any>(), vm.state.value.comprobantes)
            assertEquals(
                listOf(FalloDeLaFoto.LLEGO_TARDE),
                vm.state.value.intentos.map { it.motivo }
            )
            assertTrue(
                "el archivo que nadie va a subir no se queda en disco",
                camaraPort.descartados.isNotEmpty()
            )
            assertTrue(
                telemetria.recorded.any {
                    it.type == TelemetryEventType.ERROR &&
                        it.name == VisitasTelemetria.CODE_VISITA_FOTO_TARDE
                }
            )
        }

    /**
     * **Control positivo del anterior.** Si la escritura FALLA, nada quedó
     * escrito y la lista vuelve a estar abierta: la misma foto tardía sí se
     * adjunta, para que el reintento se la lleve. Sin esto, el arreglo de arriba
     * convertiría un guardado fallido en una foto perdida.
     */
    @Test
    fun `si el guardado falla la foto tardia si se adjunta`() = runTest(testDispatcher) {
        registroPort.resultado = ResultadoDelRegistro.FALLO_EL_GUARDADO
        val vm = viewModel()
        advanceUntilIdle()
        vm.onResultado(ResultadoDeVisita.NO_ESTABA)
        vm.onOrigen(OrigenDeLaFoto.CAMARA)
        advanceUntilIdle()

        vm.guardar()
        advanceUntilIdle()
        assertEquals(FalloDeLaVisita.NO_SE_PUDO_GUARDAR, vm.state.value.fallo)

        vm.fotoTomada()
        advanceUntilIdle()

        assertEquals(listOf("IMG-1"), vm.state.value.comprobantes.map { it.id })
        assertEquals(emptyList<Any>(), camaraPort.descartados)
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    // ─── la galería y el archivo: lo que el selector devuelve ────────────────

    /**
     * **Varias de un tirón.** La hoja promete "puedes escoger varias", y las
     * varias llegan hasta el puerto **en el orden en que se eligieron** — el
     * orden es contrato, no presentación: es el `n` de `id_<n>` del multipart.
     */
    @Test
    fun `de la galeria entran varias y en orden`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.archivosElegidos(listOf("content://media/1", "content://media/2"))
        advanceUntilIdle()
        registrar(vm)

        assertEquals(
            listOf("content://media/1", "content://media/2"),
            camaraPort.importados
        )
        assertEquals(
            listOf("ARCH-1", "ARCH-2"),
            registroPort.registradas.single().comprobantes.map { it.id }
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
        repeat(ComprobantesDeVisita.MAXIMO - 2) { tomarFoto(vm) }

        vm.archivosElegidos((1..4).map { "content://media/$it" })
        advanceUntilIdle()

        assertEquals(ComprobantesDeVisita.MAXIMO, vm.state.value.comprobantes.size)
        assertEquals(
            listOf(FalloDeLaFoto.YA_NO_CABEN, FalloDeLaFoto.YA_NO_CABEN),
            vm.state.value.intentos.map { it.motivo }
        )
        assertTrue("la visita no se entera", vm.state.value.sePuedeCapturar)
    }

    /**
     * **El tipo no permitido se rechaza DICIENDO CUÁL**: el cuadro ámbar lleva el
     * id del archivo que se cayó, no un aviso suelto. Y el archivo se borra: nada
     * lo va a subir nunca, y un MIME fuera de la whitelist tumbaría la visita
     * entera con un 422.
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
            assertEquals(
                "el cuadro dice CUÁL archivo se cayó: lleva su id",
                "ARCH-1",
                intento.id
            )
            assertEquals(listOf("/tmp/fake/importado-1.jpg"), camaraPort.descartados)
            assertTrue(
                telemetria.recorded.any {
                    it.type == TelemetryEventType.ERROR &&
                        it.name == VisitasTelemetria.CODE_VISITA_FOTO_TIPO_NO_PERMITIDO
                }
            )
        }

    /** Un fallo al importar deja su cuadro y **no toca la visita**. */
    @Test
    fun `un fallo al importar no detiene la visita`() = runTest(testDispatcher) {
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
        assertNull("el fallo de la foto no es el fallo de la visita", vm.state.value.fallo)
        assertEquals(1, registroPort.registradas.size)
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

        vm.onOrigen(OrigenDeLaFoto.GALERIA)
        assertEquals(OrigenDeLaFoto.GALERIA, vm.state.value.selectorPedido)
        vm.selectorAtendido()

        assertNull(vm.state.value.selectorPedido)
        assertFalse("y la hoja se cerró al elegir", vm.state.value.eligiendoOrigen)
    }

    // ─── la miniatura: es CÓMO se ve, no SI existe ───────────────────────────

    /** La miniatura se pide por la ruta del comprobante y se cuelga por su id. */
    @Test
    fun `la miniatura se pide y se cuelga por el id del comprobante`() = runTest(testDispatcher) {
        camaraPort.miniaturaDeCadaArchivo = VisitaFixtures.miniatura(0)
        val vm = viewModel()
        advanceUntilIdle()

        tomarFoto(vm)

        assertEquals(listOf("/tmp/fake/comprobante-IMG-1.jpg"), camaraPort.miniaturasPedidas)
        assertEquals(setOf("IMG-1"), vm.state.value.miniaturas.keys)
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

            assertEquals(1, registroPort.registradas.single().comprobantes.size)
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
                    it.name == VisitasTelemetria.CODE_VISITA_FOTO_SIN_MINIATURA
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

    private fun tomarFoto(vm: RegistrarVisitaViewModel) {
        vm.onOrigen(OrigenDeLaFoto.CAMARA)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.fotoTomada()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun registrar(vm: RegistrarVisitaViewModel) {
        vm.onResultado(ResultadoDeVisita.NO_ESTABA)
        vm.guardar()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    /** El `SavedStateHandle` de un proceso que murió con la cámara encima. */
    private fun handleConDestinoPendiente() = handle().also {
        it[CLAVE_DESTINO] = listOf(
            DESTINO_PENDIENTE_ID,
            "content://fake/camara/pendiente",
            CRUDO_PENDIENTE
        ).joinToString("\n")
    }

    private fun handle() = SavedStateHandle(
        mapOf(
            VisitasRutas.ARG_CLIENTE_ID to VisitasFixtures.VICTORIA,
            VisitasRutas.ARG_VENTA_ID to VisitasFixtures.REFRIGERADOR
        )
    )

    private fun viewModel(savedStateHandle: SavedStateHandle = handle()) = RegistrarVisitaViewModel(
        savedStateHandle = savedStateHandle,
        abrirRegistro = AbrirRegistroDeVisita(contextoPort, recomendacionesPort, telemetria),
        registrarVisita = RegistrarVisita(registroPort, ubicacionPort, telemetria),
        camara = camaraPort,
        dictado = dictadoPort,
        tema = FakeTemaDeLaAppPort(),
        telemetry = telemetria,
        clock = clock,
        io = testDispatcher
    )

    private companion object {
        const val DESTINO_PENDIENTE_ID = "IMG-SOBREVIVIENTE"
        const val CRUDO_PENDIENTE = "/tmp/fake/crudo-pendiente.jpg"

        /**
         * Las claves REALES del `SavedStateHandle`, copiadas del ViewModel: son
         * privadas allá y esta prueba tiene que hablar el mismo idioma que el
         * sistema operativo cuando restaura el proceso.
         */
        const val CLAVE_DESTINO = "visitas_destino_de_foto"
        const val CLAVE_COMPROBANTES = "visitas_comprobantes"
    }
}
