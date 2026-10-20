package com.example.msp_app.core.fcm

import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * E-APP-130 (plan visitas P3, Tarea 14; spec §6.4): el push solo despierta el
 * sync. Nunca confía en el contenido del mensaje: lo único que se mira es
 * `tipo == "sync"`; todo lo demás se ignora y NADA del mensaje llega al sync.
 */
class DespachadorDePushTest : RobolectricTestBase() {

    private var encolados = 0
    private var fcmActivo = true
    private val despachador = DespachadorDePush(
        fcmActivo = { fcmActivo },
        encolar = { encolados++ }
    )

    @Test
    fun `payloadSyncEncolaUnSync - E-APP-130`() {
        despachador.alMensaje(mapOf("tipo" to "sync"))
        assertEquals(1, encolados)
    }

    @Test
    fun `onDeletedMessagesHaceSyncCompleto - E-APP-130`() {
        despachador.alMensajesBorrados()
        assertEquals(1, encolados)
    }

    @Test
    fun `payloadDesconocidoSeIgnora - E-APP-130`() {
        despachador.alMensaje(emptyMap())
        despachador.alMensaje(mapOf("tipo" to "pago"))
        despachador.alMensaje(mapOf("tipo" to "SYNC"))
        despachador.alMensaje(mapOf("otro" to "sync"))
        assertEquals(0, encolados)
    }

    @Test
    fun `un mensaje con datos de dinero solo dispara el sync y no los usa - E-APP-130`() {
        // El despachador solo conoce un callback sin argumentos: no hay por donde
        // pasar el contenido al sync.
        despachador.alMensaje(mapOf("tipo" to "sync", "pago_id" to "9", "monto" to "-500"))
        assertEquals(1, encolados)
    }

    @Test
    fun `con el interruptor fcm apagado no encola nada - E-APP-130`() {
        fcmActivo = false
        despachador.alMensaje(mapOf("tipo" to "sync"))
        despachador.alMensajesBorrados()
        assertEquals(0, encolados)
    }

    @Test
    fun `un fallo al encolar no revienta el servicio - E-APP-130`() {
        val d = DespachadorDePush(fcmActivo = { true }, encolar = { error("sin WorkManager") })
        d.alMensaje(mapOf("tipo" to "sync"))
        d.alMensajesBorrados()
    }
}
