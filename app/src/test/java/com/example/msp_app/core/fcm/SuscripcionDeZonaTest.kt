package com.example.msp_app.core.fcm

import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** E-APP-130 · suscripción al tema `zona-<id>` (la que usa el servidor, `visitasfcm.MensajeDeZona`). */
class SuscripcionDeZonaTest : RobolectricTestBase() {

    private class TemasFalsos : Temas {
        val llamadas = mutableListOf<String>()
        var falla = false
        override suspend fun suscribir(tema: String) {
            llamadas += "+$tema"
            if (falla) error("sin red")
        }
        override suspend fun desuscribir(tema: String) {
            llamadas += "-$tema"
            if (falla) error("sin red")
        }
    }

    private class AlmacenEnMemoria(var zona: Int? = null) : AlmacenDeZonaSuscrita {
        override fun leer() = zona
        override fun guardar(zona: Int) {
            this.zona = zona
        }
    }

    private val temas = TemasFalsos()
    private val almacen = AlmacenEnMemoria()
    private var fcmActivo = true
    private val suscripcion = SuscripcionDeZona(temas, almacen) { fcmActivo }

    @Test
    fun `primera zona suscribe al tema que usa el servidor - E-APP-130`() = runBlocking {
        suscripcion.alCambiarZona(7)
        assertEquals(listOf("+zona-7"), temas.llamadas)
        assertEquals(7, almacen.zona)
    }

    @Test
    fun `cambioDeZonaDesuscribeLaVieja - E-APP-130`() = runBlocking {
        suscripcion.alCambiarZona(7)
        suscripcion.alCambiarZona(9)
        assertEquals(listOf("+zona-7", "-zona-7", "+zona-9"), temas.llamadas)
        assertEquals(9, almacen.zona)
    }

    @Test
    fun `la misma zona no vuelve a llamar a Firebase - E-APP-130`() = runBlocking {
        suscripcion.alCambiarZona(7)
        suscripcion.alCambiarZona(7)
        assertEquals(listOf("+zona-7"), temas.llamadas)
    }

    @Test
    fun `killSwitchFcmApagadoNoSuscribe - E-APP-130`() = runBlocking {
        fcmActivo = false
        suscripcion.alCambiarZona(7)
        assertEquals(emptyList<String>(), temas.llamadas)
        assertNull(almacen.zona)
    }

    @Test
    fun `si el interruptor se enciende despues la siguiente llamada si suscribe - E-APP-130`() =
        runBlocking {
            fcmActivo = false
            suscripcion.alCambiarZona(7)
            fcmActivo = true
            suscripcion.alCambiarZona(7)
            assertEquals(listOf("+zona-7"), temas.llamadas)
        }

    @Test
    fun `un fallo de red no se recuerda como suscrito y se reintenta - E-APP-130`() = runBlocking {
        temas.falla = true
        suscripcion.alCambiarZona(7)
        assertNull(almacen.zona)
        temas.falla = false
        suscripcion.alCambiarZona(7)
        assertEquals(7, almacen.zona)
    }

    @Test
    fun `onNewToken resuscribe a la zona recordada - E-APP-130`() = runBlocking {
        almacen.zona = 7
        suscripcion.resuscribir()
        assertEquals(listOf("+zona-7"), temas.llamadas)
    }

    @Test
    fun `onNewToken sin zona recordada no hace nada - E-APP-130`() = runBlocking {
        suscripcion.resuscribir()
        assertEquals(emptyList<String>(), temas.llamadas)
    }

    @Test
    fun `zona cero o negativa no es una zona - E-APP-130`() = runBlocking {
        suscripcion.alCambiarZona(0)
        suscripcion.alCambiarZona(-1)
        assertEquals(emptyList<String>(), temas.llamadas)
    }
}
