package com.example.msp_app.feature.compartirproductos.ui

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.feature.compartirproductos.android.AlmacenDeTemporales
import com.example.msp_app.feature.compartirproductos.android.GeneradorDeTarjetas
import com.example.msp_app.feature.compartirproductos.android.IntentDeCompartir
import com.example.msp_app.feature.compartirproductos.android.PreferenciasEnMemoria
import com.example.msp_app.feature.compartirproductos.android.PreferenciasEnSharedPrefs
import com.example.msp_app.feature.compartirproductos.domain.ArticuloParaCompartir
import com.example.msp_app.feature.compartirproductos.domain.FilaDePrecio
import com.example.msp_app.feature.compartirproductos.domain.PreferenciasDeCompartir
import com.example.msp_app.feature.compartirproductos.domain.SeleccionDePrecios
import com.example.msp_app.feature.compartirproductos.render.DibujanteDeTarjeta
import com.example.msp_app.feature.compartirproductos.render.RenderBase
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** E-APP-182 (la vista previa no se recicla) y E-APP-183 (sin marca de actualización no hay aviso). */
@OptIn(ExperimentalCoroutinesApi::class)
class CompartirViewModelTest : RenderBase() {

    @get:Rule val tmp = TemporaryFolder()

    private val prefs = PreferenciasEnMemoria()
    private val reloj = object : AppClock {
        override fun now(): Instant = ahora
    }
    private val planificador get() = mainDispatcherRule.testDispatcher.scheduler

    private fun vm(
        raiz: File = tmp.root,
        preferencias: PreferenciasDeCompartir = prefs
    ): CompartirViewModel {
        val d = DibujanteDeTarjeta(ApplicationProvider.getApplicationContext())
        return CompartirViewModel(
            generador = GeneradorDeTarjetas(d, AlmacenDeTemporales(raiz, reloj)),
            dibujante = d,
            preferencias = preferencias,
            crearIntent = { archivos, texto ->
                IntentDeCompartir.armar(
                    archivos.map {
                        Uri.fromFile(it)
                    },
                    texto,
                    "com.whatsapp"
                )
            },
            reloj = reloj,
            trabajo = mainDispatcherRule.testDispatcher
        )
    }

    private fun art(
        id: Int,
        nombre: String = "RECAMARA TOSCANA C/CABECERA TAPIZADA"
    ): ArticuloParaCompartir = articulo(
        nombre = nombre
    ).copy(id = id)

    // region — abrir

    @Test fun `abrir con un producto deja las tres casillas marcadas`() {
        val m = vm()
        m.abrir(listOf(art(1)))
        val e = m.estado.value
        assertTrue(e.visible)
        assertTrue(e.esUno)
        assertEquals(SeleccionDePrecios(true, true, true), e.seleccion)
    }

    @Test fun `abrir con varios no es un solo producto`() {
        val m = vm()
        m.abrir(listOf(art(1), art(2)))
        assertFalse(m.estado.value.esUno)
    }

    @Test fun `abrir sin productos no abre nada`() {
        val m = vm()
        m.abrir(emptyList())
        assertFalse(m.estado.value.visible)
    }

    @Test fun `abrir recuerda las casillas de la vez anterior`() {
        prefs.guardarSeleccionDePrecios(SeleccionDePrecios(true, false, false))
        val m = vm()
        m.abrir(listOf(art(1)))
        assertEquals(SeleccionDePrecios(true, false, false), m.estado.value.seleccion)
    }

    @Test fun `dibuja la vista previa de la primera tarjeta a tamano reducido`() {
        val m = vm()
        m.abrir(listOf(art(1)))
        planificador.advanceUntilIdle()
        val v = checkNotNull(m.estado.value.vistaPrevia)
        assertEquals(405, v.width)
        assertEquals(720, v.height)
    }

    @Test fun `con varios la vista previa es la de la primera`() {
        val m = vm()
        m.abrir(listOf(art(1, "SALA UNO"), art(2, "COMEDOR DOS")))
        planificador.advanceUntilIdle()
        val primera = checkNotNull(m.estado.value.vistaPrevia)
        val solo = vm().also {
            it.abrir(listOf(art(1, "SALA UNO")))
            planificador.advanceUntilIdle()
        }
        assertTrue(primera.sameAs(checkNotNull(solo.estado.value.vistaPrevia)))
    }
    // endregion

    // region — aviso de precios viejos

    @Test fun `mas de tres dias sin actualizar avisa`() {
        val m = vm()
        m.abrir(listOf(art(1)), ahora.minus(Duration.ofDays(4)))
        assertTrue(m.estado.value.avisoPreciosViejos)
    }

    @Test fun `hasta tres dias o sin marca no avisa`() {
        val m = vm()
        m.abrir(listOf(art(1)), ahora.minus(Duration.ofDays(3)))
        assertFalse(m.estado.value.avisoPreciosViejos)
        m.abrir(listOf(art(1)), ahora.minus(Duration.ofHours(2)))
        assertFalse(m.estado.value.avisoPreciosViejos)
        m.abrir(listOf(art(1)), null)
        assertFalse(m.estado.value.avisoPreciosViejos)
    }
    // endregion

    // region — editar

    @Test fun `cambiar una casilla cambia la seleccion y redibuja la vista previa`() {
        val m = vm()
        m.abrir(listOf(art(1)))
        planificador.advanceUntilIdle()
        val antes = checkNotNull(m.estado.value.vistaPrevia)
        m.cambiarCasilla(FilaDePrecio.CORTO_PLAZO, false)
        assertEquals(SeleccionDePrecios(true, false, true), m.estado.value.seleccion)
        planificador.advanceUntilIdle()
        assertFalse(
            "sin la fila de corto plazo la tarjeta es distinta",
            antes.sameAs(checkNotNull(m.estado.value.vistaPrevia))
        )
    }

    @Test fun `teclear rapido casillas solo dibuja la ultima version`() {
        val m = vm()
        m.abrir(listOf(art(1)))
        planificador.advanceUntilIdle()
        m.cambiarCasilla(FilaDePrecio.CONTADO, false)
        planificador.advanceTimeBy(100)
        m.cambiarCasilla(FilaDePrecio.CORTO_PLAZO, false)
        planificador.advanceTimeBy(100)
        m.cambiarCasilla(FilaDePrecio.CREDITO, false)
        planificador.advanceUntilIdle()
        val esperada = vm().also {
            it.abrir(listOf(art(1)))
            it.cambiarCasilla(FilaDePrecio.CONTADO, false)
            it.cambiarCasilla(FilaDePrecio.CORTO_PLAZO, false)
            it.cambiarCasilla(FilaDePrecio.CREDITO, false)
            planificador.advanceUntilIdle()
        }.estado.value.vistaPrevia
        assertTrue(checkNotNull(m.estado.value.vistaPrevia).sameAs(esperada))
    }

    // E-APP-180: el vendedor no modifica el título; un valor viejo en preferencias no cuenta.
    @Test fun `un titulo viejo guardado en preferencias no cambia ni la vista previa ni la tarjeta`() {
        val reales =
            PreferenciasEnSharedPrefs(
                ApplicationProvider.getApplicationContext<android.content.Context>()
            )
        val claves = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("compartir_productos", android.content.Context.MODE_PRIVATE)
        claves.edit().remove("titulo_1").commit()

        val limpia = vm(File(tmp.root, "a"), reales)
        limpia.abrir(listOf(art(1)))
        planificador.advanceUntilIdle()
        val previaLimpia = checkNotNull(limpia.estado.value.vistaPrevia)
        limpia.compartir()
        planificador.advanceUntilIdle()
        val tarjetaLimpia = File(tmp.root, "a/compartir").listFiles()!!.single().readBytes()

        claves.edit().putString("titulo_1", "Titulo Viejo Que Nadie Debe Ver Jamas").commit()
        val conViejo = vm(File(tmp.root, "b"), reales)
        conViejo.abrir(listOf(art(1)))
        planificador.advanceUntilIdle()
        assertTrue(previaLimpia.sameAs(checkNotNull(conViejo.estado.value.vistaPrevia)))
        conViejo.compartir()
        planificador.advanceUntilIdle()
        val tarjetaConViejo = File(tmp.root, "b/compartir").listFiles()!!.single().readBytes()

        assertTrue(
            "la tarjeta usa el título automático",
            tarjetaLimpia.contentEquals(tarjetaConViejo)
        )
    }
    // endregion

    // region — compartir

    @Test fun `compartir un producto guarda las casillas y entrega el intent`() = runTest(
        mainDispatcherRule.testDispatcher
    ) {
        val m = vm()
        m.abrir(listOf(art(1, "RECAMARA TOSCANA C/CABECERA TAPIZADA")))
        m.cambiarCasilla(FilaDePrecio.CREDITO, false)
        val evento = launchEvento(m)
        m.compartir()
        planificador.advanceUntilIdle()

        assertEquals(SeleccionDePrecios(true, true, false), prefs.seleccion)
        val intent = (evento.await() as EventoDeCompartir.Lanzar).intent
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("com.whatsapp", intent.`package`)
        val texto = intent.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue(
            "nombre completo de Microsip",
            texto.startsWith("RECAMARA TOSCANA C/CABECERA TAPIZADA")
        )
        assertTrue(texto.contains("Contado: $8,990 · Corto plazo: $10,450"))
        assertFalse("Crédito está desmarcado", texto.contains("Crédito"))
        assertFalse("la hoja se cierra", m.estado.value.visible)
        val archivo =
            File(Uri.parse(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM).toString()).path!!)
        assertTrue(archivo.exists())
        assertEquals(File(tmp.root, "compartir"), archivo.parentFile)
    }

    @Test fun `con varios manda SEND_MULTIPLE con todos los nombres`() = runTest(
        mainDispatcherRule.testDispatcher
    ) {
        val m = vm()
        m.abrir(listOf(art(1, "SALA UNO"), art(2, "SALA DOS"), art(3, "SALA TRES")))
        val evento = launchEvento(m)
        m.compartir()
        planificador.advanceUntilIdle()
        val intent = (evento.await() as EventoDeCompartir.Lanzar).intent
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        assertEquals(3, intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!.size)
        val texto = intent.getStringExtra(Intent.EXTRA_TEXT)!!
        listOf("SALA UNO", "SALA DOS", "SALA TRES").forEach { assertTrue(texto.contains(it)) }
    }

    @Test fun `diez productos se comparten en un solo intent`() = runTest(
        mainDispatcherRule.testDispatcher
    ) {
        val m = vm()
        m.abrir(List(10) { art(it + 1, "SALA $it") })
        val evento = launchEvento(m)
        m.compartir()
        planificador.advanceUntilIdle()
        val intent = (evento.await() as EventoDeCompartir.Lanzar).intent
        assertEquals(10, intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!.size)
        assertEquals(10, File(tmp.root, "compartir").listFiles()!!.size)
    }

    @Test fun `sin casillas la tarjeta se manda sin precios y el texto tampoco los lleva`() = runTest(
        mainDispatcherRule.testDispatcher
    ) {
        prefs.guardarSeleccionDePrecios(SeleccionDePrecios(false, false, false))
        val m = vm()
        m.abrir(listOf(art(1, "SALA UNO")))
        val evento = launchEvento(m)
        m.compartir()
        planificador.advanceUntilIdle()
        val texto = (evento.await() as EventoDeCompartir.Lanzar).intent.getStringExtra(
            Intent.EXTRA_TEXT
        )!!
        assertEquals("SALA UNO\n\nPrecios vigentes al 10 nov 2026", texto)
    }

    @Test fun `un segundo toque mientras genera no duplica el envio`() = runTest(
        mainDispatcherRule.testDispatcher
    ) {
        val m = vm()
        m.abrir(listOf(art(1)))
        val eventos = launchTodos(m)
        m.compartir()
        m.compartir()
        planificador.advanceUntilIdle()
        assertEquals(1, eventos.size)
    }

    @Test fun `si no se puede escribir avisa y deja reintentar sin cerrar la hoja`() = runTest(
        mainDispatcherRule.testDispatcher
    ) {
        val archivoComoCarpeta = tmp.newFile("no_es_carpeta")
        val m = vm(raiz = archivoComoCarpeta)
        m.abrir(listOf(art(1)))
        val eventos = launchTodos(m)
        m.compartir()
        planificador.advanceUntilIdle()
        val e = m.estado.value
        assertTrue(e.visible)
        assertFalse(e.generando)
        assertEquals("No se pudieron preparar las imágenes", e.error)
        assertTrue(eventos.isEmpty())
    }

    @Test fun `sin memoria avisa con un mensaje claro y no cierra la app`() = runTest(
        mainDispatcherRule.testDispatcher
    ) {
        val d = DibujanteDeTarjeta(ApplicationProvider.getApplicationContext())
        val sinMemoria = object : GeneradorDeTarjetas(
            d,
            AlmacenDeTemporales(tmp.root, reloj)
        ) {
            override suspend fun generar(
                articulos: List<ArticuloParaCompartir>,
                seleccion: SeleccionDePrecios,
                ahora: Instant,
                alAvanzar: (Int, Int) -> Unit
            ): List<File> = throw OutOfMemoryError("createBitmap")
        }
        val m = CompartirViewModel(
            sinMemoria,
            d,
            prefs,
            { a, t ->
                IntentDeCompartir.armar(
                    a.map {
                        Uri.fromFile(it)
                    },
                    t,
                    "com.whatsapp"
                )
            },
            reloj,
            mainDispatcherRule.testDispatcher
        )
        m.abrir(listOf(art(1)))
        val eventos = launchTodos(m)
        m.compartir()
        planificador.advanceUntilIdle()
        val e = m.estado.value
        assertTrue(e.visible)
        assertFalse(e.generando)
        assertEquals(CompartirViewModel.MENSAJE_SIN_MEMORIA, e.error)
        assertTrue(eventos.isEmpty())
    }

    @Test fun `cerrar limpia el estado`() {
        val m = vm()
        m.abrir(listOf(art(1)))
        planificador.advanceUntilIdle()
        m.cerrar()
        assertFalse(m.estado.value.visible)
        assertNull(m.estado.value.vistaPrevia)
        assertTrue(m.estado.value.articulos.isEmpty())
    }
    // endregion

    private fun kotlinx.coroutines.test.TestScope.launchEvento(m: CompartirViewModel) =
        async(mainDispatcherRule.testDispatcher) { m.eventos.first() }

    private fun kotlinx.coroutines.test.TestScope.launchTodos(
        m: CompartirViewModel
    ): MutableList<EventoDeCompartir> {
        val lista = mutableListOf<EventoDeCompartir>()
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { m.eventos.toList(lista) }
        return lista
    }
}
