package com.example.msp_app.feature.compartirproductos.android

import com.example.msp_app.core.common.time.AppClock
import java.io.File
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AlmacenDeTemporalesTest {

    @get:Rule val tmp = TemporaryFolder()

    private val ahora = Instant.parse("2026-11-10T18:00:00Z")
    private val reloj = object : AppClock {
        override fun now(): Instant = ahora
    }

    private fun almacen() = AlmacenDeTemporales(tmp.root, reloj)

    private fun archivo(nombre: String, hace: Duration): File {
        val f = File(almacen().asegurarCarpeta(), nombre)
        f.writeBytes(byteArrayOf(1, 2, 3))
        f.setLastModified(ahora.minus(hace).toEpochMilli())
        return f
    }

    @Test fun `la carpeta es cacheDir barra compartir y se crea sola`() {
        val a = almacen()
        assertEquals(File(tmp.root, "compartir"), a.carpeta)
        assertFalse(a.carpeta.exists())
        assertTrue(a.asegurarCarpeta().isDirectory)
    }

    @Test fun `el archivo de cada tarjeta vive en la carpeta y termina en jpg`() {
        val f = almacen().archivoPara(42, 3)
        assertEquals("tarjeta_42_3.jpg", f.name)
        assertEquals(almacen().carpeta, f.parentFile)
    }

    @Test fun `vaciar borra las tarjetas de envios anteriores`() {
        archivo("tarjeta_1_0.jpg", Duration.ofMinutes(5))
        archivo("tarjeta_2_1.jpg", Duration.ofDays(5))
        assertEquals(2, almacen().vaciar())
        assertEquals(0, almacen().carpeta.listFiles()!!.size)
    }

    @Test fun `vaciar con la carpeta inexistente no falla`() {
        assertEquals(0, almacen().vaciar())
        assertEquals(0, almacen().barrerViejos())
    }

    @Test fun `vaciar no toca lo que no es una tarjeta ni sale de la carpeta`() {
        val ajeno = archivo("notas.txt", Duration.ofDays(9))
        val otroJpg = archivo("foto.jpg", Duration.ofDays(9))
        val fuera = File(tmp.root, "tarjeta_9_9.jpg").apply { writeBytes(byteArrayOf(1)) }
        File(almacen().carpeta, "tarjeta_5_5.jpg").mkdirs() // una CARPETA con nombre de tarjeta
        almacen().vaciar()
        almacen().barrerViejos()
        assertTrue(ajeno.exists())
        assertTrue(otroJpg.exists())
        assertTrue(fuera.exists())
        assertTrue(File(almacen().carpeta, "tarjeta_5_5.jpg").isDirectory)
    }

    @Test fun `barrer borra los de mas de un dia y deja los recientes`() {
        val viejo = archivo("tarjeta_1_0.jpg", Duration.ofHours(25))
        val muyViejo = archivo("tarjeta_2_0.jpg", Duration.ofDays(30))
        val reciente = archivo("tarjeta_3_0.jpg", Duration.ofHours(23))
        val casiHoy = archivo("tarjeta_4_0.jpg", Duration.ofMinutes(1))
        assertEquals(2, almacen().barrerViejos())
        assertFalse(viejo.exists())
        assertFalse(muyViejo.exists())
        assertTrue(reciente.exists())
        assertTrue(casiHoy.exists())
    }

    @Test fun `justo un dia no se borra y un milisegundo mas si`() {
        val justo = archivo("tarjeta_1_0.jpg", Duration.ofDays(1))
        val pasado = archivo("tarjeta_2_0.jpg", Duration.ofDays(1).plusMillis(1))
        almacen().barrerViejos()
        assertTrue(justo.exists())
        assertFalse(pasado.exists())
    }

    @Test fun `la edad maxima es configurable`() {
        val f = archivo("tarjeta_1_0.jpg", Duration.ofHours(3))
        almacen().barrerViejos(Duration.ofHours(4))
        assertTrue(f.exists())
        almacen().barrerViejos(Duration.ofHours(2))
        assertFalse(f.exists())
    }
}
