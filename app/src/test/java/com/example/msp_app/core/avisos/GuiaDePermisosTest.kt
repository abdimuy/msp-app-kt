package com.example.msp_app.core.avisos

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** E-APP-134 · Cuándo y qué se le pide al cobrador (plan P3, Tarea 15). */
@RunWith(RobolectricTestRunner::class)
class GuiaDePermisosTest {

    private val ahora = Instant.parse("2026-10-27T15:00:00Z")
    private val nada =
        EstadoDePermisos(notificaciones = true, alarmasExactas = false, bateriaSinOptimizar = false)

    private fun pasos(
        estado: EstadoDePermisos = nada,
        sdk: Int = 34,
        hayAvisos: Boolean = true,
        ultima: Instant? = null
    ) = GuiaDePermisos.pasosPendientes(estado, sdk, hayAvisos, ultima, ahora)

    @Test
    fun `sin avisos programados no se pide nada porque no hay momento util - E-APP-134`() {
        assertEquals(emptyList<PasoDePermiso>(), pasos(hayAvisos = false))
    }

    @Test
    fun `con avisos y sin permisos se piden exactas y bateria - E-APP-134`() {
        assertEquals(
            listOf(PasoDePermiso.ALARMAS_EXACTAS, PasoDePermiso.BATERIA),
            pasos()
        )
    }

    @Test
    fun `antes de Android 12 las exactas no se piden - E-APP-134`() {
        assertEquals(listOf(PasoDePermiso.BATERIA), pasos(sdk = 28))
    }

    @Test
    fun `lo que ya esta concedido no se vuelve a pedir - E-APP-134`() {
        assertEquals(
            emptyList<PasoDePermiso>(),
            pasos(EstadoDePermisos(true, alarmasExactas = true, bateriaSinOptimizar = true))
        )
        assertEquals(
            listOf(PasoDePermiso.BATERIA),
            pasos(EstadoDePermisos(true, alarmasExactas = true, bateriaSinOptimizar = false))
        )
    }

    @Test
    fun `si ya se pregunto hace menos de siete dias no se insiste - E-APP-134`() {
        assertEquals(emptyList<PasoDePermiso>(), pasos(ultima = ahora.minus(Duration.ofDays(6))))
        assertEquals(2, pasos(ultima = ahora.minus(Duration.ofDays(8))).size)
    }

    @Test
    fun `los ajustes son intents de lista del sistema y nunca el permiso restringido - E-APP-134`() {
        val c: Context = ApplicationProvider.getApplicationContext()
        val bateria = GuiaDePermisos.intentDe(PasoDePermiso.BATERIA, c.packageName, 34)
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, bateria?.action)
        val exactas = GuiaDePermisos.intentDe(PasoDePermiso.ALARMAS_EXACTAS, c.packageName, 34)
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, exactas?.action)
        assertNotNull(exactas?.data)
        assertNull(GuiaDePermisos.intentDe(PasoDePermiso.ALARMAS_EXACTAS, c.packageName, 28))
    }

    @Test
    fun `los textos llevan mayuscula inicial sin punto final y sin la palabra ciclo - E-APP-134`() {
        val textos = listOf(GuiaDePermisos.TITULO, GuiaDePermisos.EXPLICACION, GuiaDePermisos.AHORA_NO) +
            PasoDePermiso.entries.flatMap { listOf(it.rotulo, it.accion) }
        textos.forEach {
            assert(it.first().isUpperCase()) { "mayúscula inicial: $it" }
            assert(!it.endsWith(".")) { "sin punto final: $it" }
            assert(!it.contains("ciclo", ignoreCase = true)) { it }
        }
    }
}
