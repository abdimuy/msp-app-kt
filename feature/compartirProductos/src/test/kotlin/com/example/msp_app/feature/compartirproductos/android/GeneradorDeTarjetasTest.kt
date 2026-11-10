package com.example.msp_app.feature.compartirproductos.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.ventas.precios.TipoDePrecio
import com.example.msp_app.feature.compartirproductos.domain.ArticuloParaCompartir
import com.example.msp_app.feature.compartirproductos.domain.PrecioDeCatalogo
import com.example.msp_app.feature.compartirproductos.domain.PreferenciasDeCompartir
import com.example.msp_app.feature.compartirproductos.domain.SeleccionDePrecios
import com.example.msp_app.feature.compartirproductos.render.DibujanteDeTarjeta
import com.example.msp_app.feature.compartirproductos.render.RenderBase
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Preferencias en memoria: la prueba de [PreferenciasEnSharedPrefs] cubre la real. */
class PreferenciasEnMemoria : PreferenciasDeCompartir {
    var seleccion = SeleccionDePrecios()
    val titulos = mutableMapOf<Int, String>()
    override fun seleccionDePrecios() = seleccion
    override fun guardarSeleccionDePrecios(seleccion: SeleccionDePrecios) {
        this.seleccion = seleccion
    }
    override fun tituloEditado(articuloId: Int): String? = titulos[articuloId]
    override fun guardarTituloEditado(articuloId: Int, titulo: String?) {
        if (titulo.isNullOrBlank()) {
            titulos.remove(
                articuloId
            )
        } else {
            titulos[articuloId] = titulo.trim()
        }
    }
}

class GeneradorDeTarjetasTest : RenderBase() {

    @get:Rule val tmp = TemporaryFolder()

    private val prefs = PreferenciasEnMemoria()
    private val reloj = object : AppClock {
        override fun now(): Instant = ahora
    }

    private fun generador() = GeneradorDeTarjetas(
        DibujanteDeTarjeta(ApplicationProvider.getApplicationContext()),
        AlmacenDeTemporales(tmp.root, reloj),
        prefs
    )

    private fun GeneradorDeTarjetas.go(
        articulos: List<ArticuloParaCompartir>,
        seleccion: SeleccionDePrecios,
        ahora: Instant,
        tituloSolo: String? = null,
        alAvanzar: (Int, Int) -> Unit = { _, _ -> }
    ) = runBlocking { generar(articulos, seleccion, ahora, tituloSolo, alAvanzar) }

    private fun art(id: Int, nombre: String = "SALA VALENCIA", foto: String? = null) =
        ArticuloParaCompartir(
            id,
            nombre,
            "SALAS",
            listOf(PrecioDeCatalogo(TipoDePrecio.CONTADO, BigDecimal("8990"))),
            foto
        )

    private fun fotoEnDisco(color: Int): File {
        val f = File(tmp.root, "foto_$color.jpg")
        val b = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return f
    }

    @Test fun `escribe un JPEG de 1080 por 1920 por articulo en orden`() {
        val archivos = generador().go(
            listOf(art(1), art(2), art(3)),
            SeleccionDePrecios(),
            ahora
        )
        assertEquals(
            listOf("tarjeta_1_0.jpg", "tarjeta_2_1.jpg", "tarjeta_3_2.jpg"),
            archivos.map {
                it.name
            }
        )
        archivos.forEach { f ->
            val b = BitmapFactory.decodeFile(f.path)
            assertEquals(1080, b.width)
            assertEquals(1920, b.height)
            assertTrue(f.length() > 10_000)
        }
    }

    @Test fun `los envios anteriores se borran al iniciar uno nuevo`() {
        val g = generador()
        val primero = g.go(listOf(art(1), art(2)), SeleccionDePrecios(), ahora)
        assertTrue(primero.all { it.exists() })
        val segundo = g.go(listOf(art(7)), SeleccionDePrecios(), ahora)
        assertTrue(segundo.single().exists())
        assertFalse("el envío anterior no debe quedar", primero.any { it.exists() })
        assertEquals(1, AlmacenDeTemporales(tmp.root, reloj).carpeta.listFiles()!!.size)
    }

    @Test fun `usa la foto del articulo si existe y sale sin foto si falta o esta rota`() {
        val roja = fotoEnDisco(Color.RED)
        val rota = File(tmp.root, "rota.jpg").apply { writeText("no soy una imagen") }
        val archivos = generador().go(
            listOf(
                art(1, foto = roja.path),
                art(2, foto = File(tmp.root, "no_existe.jpg").path),
                art(3, foto = rota.path),
                art(4, foto = null)
            ),
            SeleccionDePrecios(false, false, false),
            ahora
        )
        val px = archivos.map { BitmapFactory.decodeFile(it.path).getPixel(900, 700) }
        assertTrue("con foto: rojo", Color.red(px[0]) > 200 && Color.green(px[0]) < 60)
        (1..3).forEach {
            assertTrue(
                "sin foto #$it: beige",
                cerca(Color.parseColor("#e8e3da"), px[it], 8)
            )
        }
    }

    @Test fun `el titulo recordado se usa y el editado en la hoja gana con un solo producto`() {
        prefs.guardarTituloEditado(1, "Titulo Recordado")
        val g = generador()
        // Con varios: cada uno usa su título recordado.
        val dos = g.go(listOf(art(1), art(2)), SeleccionDePrecios(), ahora)
        assertEquals(2, dos.size)
        // La comparación de contenido se hace por pixeles: dos tarjetas con distinto título difieren.
        val conRecordado = BitmapFactory.decodeFile(dos[0].path)
        val sinRecordado = BitmapFactory.decodeFile(dos[1].path)
        assertFalse(conRecordado.sameAs(sinRecordado))
        // Con uno solo, el título de la hoja gana sobre el recordado.
        val uno = g.go(
            listOf(art(1)),
            SeleccionDePrecios(),
            ahora,
            tituloSolo = "Otro Titulo Mas Largo"
        )
        val ganador = BitmapFactory.decodeFile(uno.single().path)
        assertFalse(ganador.sameAs(conRecordado))
    }

    @Test fun `si se cancela en medio no se siguen dibujando tarjetas`() {
        val archivos = mutableListOf<Int>()
        val resultado = runBlocking {
            val g = generador()
            lateinit var trabajo: kotlinx.coroutines.Job
            trabajo = launch(kotlinx.coroutines.Dispatchers.Default) {
                g.generar(List(10) { art(it) }, SeleccionDePrecios(), ahora, null) { h, _ ->
                    archivos += h
                    if (h == 2) trabajo.cancel()
                }
            }
            trabajo.join()
            trabajo.isCancelled
        }
        assertTrue(resultado)
        assertEquals(listOf(1, 2), archivos)
        assertEquals(2, AlmacenDeTemporales(tmp.root, reloj).carpeta.listFiles()!!.size)
    }

    @Test fun `avisa el progreso articulo por articulo`() {
        val avances = mutableListOf<Pair<Int, Int>>()
        generador().go(
            listOf(art(1), art(2), art(3)),
            SeleccionDePrecios(),
            ahora
        ) { h, t -> avances += h to t }
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), avances)
    }

    @Test fun `treinta tarjetas caben y cada una pesa lo razonable`() {
        val archivos = generador().go(List(30) { art(it) }, SeleccionDePrecios(), ahora)
        assertEquals(30, archivos.size)
        assertTrue(archivos.all { it.length() in 10_000..600_000 })
    }
}
