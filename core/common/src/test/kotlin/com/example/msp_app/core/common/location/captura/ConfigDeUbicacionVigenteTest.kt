package com.example.msp_app.core.common.location.captura

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-141: lectura de `config/ubicacion` con una fuente falsa. */
class ConfigDeUbicacionVigenteTest {

    private class FuenteFalsa : FuenteDeConfigRemota {
        var cb: ((Map<String, Any?>?, Throwable?) -> Unit)? = null
        var registros = 0

        override fun escuchar(alRecibir: (Map<String, Any?>?, Throwable?) -> Unit) {
            registros++
            cb = alRecibir
        }

        fun emitir(datos: Map<String, Any?>?, error: Throwable? = null) = cb!!(datos, error)
    }

    @Test
    fun `E-APP-141 sin documento rige la config por defecto 30 s 30 m`() {
        val f = FuenteFalsa()
        val v = ConfigDeUbicacionVigente(f)
        assertEquals(ConfigDeAceptacion.POR_DEFECTO, v.actual)
        v.instalar()
        f.emitir(null)
        assertEquals(30L, v.actual.maxEdadSeg)
        assertEquals(30.0, v.actual.maxPrecisionM, 0.0)
    }

    @Test
    fun `E-APP-141 el documento remoto cambia los umbrales sin publicar app`() {
        val f = FuenteFalsa()
        val v = ConfigDeUbicacionVigente(f).also { it.instalar() }
        f.emitir(mapOf("max_edad_seg" to 60L, "max_precision_m" to 40.0))
        assertEquals(ConfigDeAceptacion(maxEdadSeg = 60, maxPrecisionM = 40.0), v.actual)
    }

    @Test
    fun `E-APP-141 un error de Firestore conserva el valor vigente`() {
        val f = FuenteFalsa()
        val v = ConfigDeUbicacionVigente(f).also { it.instalar() }
        f.emitir(mapOf("max_edad_seg" to 60L))
        f.emitir(null, IllegalStateException("sin red"))
        assertEquals(60L, v.actual.maxEdadSeg)
    }

    @Test
    fun `E-APP-141 valores fuera de rango o de otro tipo conservan el vigente`() {
        val f = FuenteFalsa()
        val v = ConfigDeUbicacionVigente(f).also { it.instalar() }
        f.emitir(mapOf("max_edad_seg" to 60L, "max_precision_m" to 40.0))
        f.emitir(
            mapOf("max_edad_seg" to 99_999L, "max_precision_m" to "10", "max_velocidad_ms" to 3.0)
        )
        assertEquals(60L, v.actual.maxEdadSeg)
        assertEquals(40.0, v.actual.maxPrecisionM, 0.0)
        assertEquals(3.0, v.actual.maxVelocidadMs, 0.0)
    }

    @Test
    fun `E-APP-141 borrar el documento devuelve los defaults`() {
        val f = FuenteFalsa()
        val v = ConfigDeUbicacionVigente(f).also { it.instalar() }
        f.emitir(mapOf("max_edad_seg" to 60L))
        f.emitir(null)
        assertEquals(ConfigDeAceptacion.POR_DEFECTO, v.actual)
    }

    @Test
    fun `E-APP-141 instalar es idempotente y una fuente que lanza no tumba`() {
        val f = FuenteFalsa()
        val v = ConfigDeUbicacionVigente(f)
        v.instalar()
        v.instalar()
        assertEquals(1, f.registros)
        val malo = ConfigDeUbicacionVigente { error("firestore no inicializado") }
        malo.instalar()
        assertTrue(malo.actual == ConfigDeAceptacion.POR_DEFECTO)
    }
}
