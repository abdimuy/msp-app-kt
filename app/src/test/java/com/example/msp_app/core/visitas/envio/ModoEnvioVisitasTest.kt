package com.example.msp_app.core.visitas.envio

import com.google.firebase.firestore.FirebaseFirestore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Kill-switch `config/visitas_envio` (plan visitas P1, tarea 10; Review Focus 5):
 * documento ausente, valor inválido o Firestore caído dejan el modo en V3.
 * Robolectric solo porque el objeto registra con `android.util.Log`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class ModoEnvioVisitasTest {

    @After fun restaurar() = FirestoreModoEnvioVisitas.aplicarParaPrueba(null)

    @Test fun documentoAusenteEsV3() = assertEquals(ModoEnvioVisitas.V3, modoDesde(null))

    @Test fun documentoSinCampoModoEsV3() = assertEquals(ModoEnvioVisitas.V3, modoDesde(emptyMap()))

    @Test fun valorDesconocidoEsV3() = assertEquals(
        ModoEnvioVisitas.V3,
        modoDesde(mapOf("modo" to "v9"))
    )

    @Test fun `tipo equivocado es V3`() = assertEquals(
        ModoEnvioVisitas.V3,
        modoDesde(mapOf("modo" to 3L))
    )

    @Test fun `modo nulo es V3`() = assertEquals(
        ModoEnvioVisitas.V3,
        modoDesde(mapOf("modo" to null))
    )

    @Test fun `variantes que no son exactamente v2_actual son V3`() {
        listOf("V2_ACTUAL", " v2_actual", "v2_actual ", "v2", "v3", "").forEach {
            assertEquals("valor '$it'", ModoEnvioVisitas.V3, modoDesde(mapOf("modo" to it)))
        }
    }

    @Test fun `v3 explicito es V3`() = assertEquals(
        ModoEnvioVisitas.V3,
        modoDesde(mapOf("modo" to "v3"))
    )

    @Test fun `v2_actual explicito vuelve al envio de hoy`() =
        assertEquals(ModoEnvioVisitas.V2_ACTUAL, modoDesde(mapOf("modo" to "v2_actual")))

    @Test fun `sin documento el modo vigente es V3`() {
        FirestoreModoEnvioVisitas.aplicarParaPrueba(null)
        assertEquals(ModoEnvioVisitas.V3, FirestoreModoEnvioVisitas.actual)
    }

    @Test fun `el documento cambia el modo vigente en los dos sentidos`() {
        FirestoreModoEnvioVisitas.aplicarParaPrueba(mapOf("modo" to "v2_actual"))
        assertEquals(ModoEnvioVisitas.V2_ACTUAL, FirestoreModoEnvioVisitas.actual)
        FirestoreModoEnvioVisitas.aplicarParaPrueba(mapOf("modo" to "v3"))
        assertEquals(ModoEnvioVisitas.V3, FirestoreModoEnvioVisitas.actual)
    }

    @Test fun errorDeFirestoreNoCambiaElModo() {
        FirestoreModoEnvioVisitas.aplicarParaPrueba(mapOf("modo" to "v2_actual"))
        FirestoreModoEnvioVisitas.alFallarParaPrueba(RuntimeException("sin red"))
        assertEquals(ModoEnvioVisitas.V2_ACTUAL, FirestoreModoEnvioVisitas.actual)
    }

    @Test fun `error de Firestore con V3 vigente sigue en V3`() {
        FirestoreModoEnvioVisitas.aplicarParaPrueba(null)
        FirestoreModoEnvioVisitas.alFallarParaPrueba(RuntimeException("sin red"))
        assertEquals(ModoEnvioVisitas.V3, FirestoreModoEnvioVisitas.actual)
    }

    // Instancias nuevas de ModoEnvioVisitasVigente: no dependen del estado del
    // objeto global ni de que otra clase del sandbox haya inicializado FirebaseApp.

    @Test fun `un modo recien creado es V3 sin pasar por aplicar`() {
        assertEquals(ModoEnvioVisitas.V3, ModoEnvioVisitasVigente(firestoreQueFalla()).actual)
    }

    @Test fun `un modo recien creado coincide con un documento ausente`() {
        assertEquals(modoDesde(null), ModoEnvioVisitasVigente(firestoreQueFalla()).actual)
    }

    @Test fun `listener con error y V2_ACTUAL vigente conserva V2_ACTUAL`() {
        val modo = ModoEnvioVisitasVigente(firestoreQueFalla())
        modo.alRecibir(mapOf("modo" to "v2_actual"), null)
        assertEquals(ModoEnvioVisitas.V2_ACTUAL, modo.actual)
        modo.alRecibir(null, RuntimeException("sin red"))
        assertEquals(ModoEnvioVisitas.V2_ACTUAL, modo.actual)
    }

    @Test fun `listener con error y datos conserva el modo vigente`() {
        val modo = ModoEnvioVisitasVigente(firestoreQueFalla())
        modo.alRecibir(mapOf("modo" to "v2_actual"), null)
        modo.alRecibir(mapOf("modo" to "v3"), RuntimeException("sin red"))
        assertEquals(ModoEnvioVisitas.V2_ACTUAL, modo.actual)
    }

    @Test fun `listener sin error y sin documento vuelve a V3`() {
        val modo = ModoEnvioVisitasVigente(firestoreQueFalla())
        modo.alRecibir(mapOf("modo" to "v2_actual"), null)
        modo.alRecibir(null, null)
        assertEquals(ModoEnvioVisitas.V3, modo.actual)
    }

    @Test fun `instalar con Firestore que lanza no truena, conserva el modo y reintenta`() {
        var llamadas = 0
        val modo = ModoEnvioVisitasVigente {
            llamadas++
            throw IllegalStateException("Default FirebaseApp is not initialized")
        }
        modo.instalar()
        assertEquals(ModoEnvioVisitas.V3, modo.actual)
        modo.alRecibir(mapOf("modo" to "v2_actual"), null)
        modo.instalar()
        assertEquals(ModoEnvioVisitas.V2_ACTUAL, modo.actual)
        assertEquals("un instalar fallido no debe bloquear el siguiente intento", 2, llamadas)
    }

    private fun firestoreQueFalla(): () -> FirebaseFirestore =
        { throw IllegalStateException("sin Firestore en la prueba") }
}
