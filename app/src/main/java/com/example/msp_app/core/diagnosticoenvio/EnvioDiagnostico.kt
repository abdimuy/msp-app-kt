package com.example.msp_app.core.diagnosticoenvio

import android.content.Context
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.logging.Logger
import com.example.msp_app.core.logging.RemoteLogger
import com.example.msp_app.core.network.diagnostico.EnvioDiagnosticoSink
import com.example.msp_app.core.network.diagnostico.EnvioObservado

/**
 * Punto único de instalación del diagnóstico de envío (E-APP-063..066).
 *
 * [sink] existe desde el classload y no hace nada hasta [instalar]
 * (`MspApplication.onCreate`); así el cliente v2 (`appRetrofitClientFactory`)
 * y el worker de visitas lo pueden tomar sin depender del orden de arranque.
 */
object EnvioDiagnostico {

    @Volatile
    private var registrador: RegistradorDeEnvioDiagnostico? = null

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    val sink: EnvioDiagnosticoSink = EnvioDiagnosticoSink { observado: EnvioObservado ->
        try {
            registrador?.registrar(observado)
        } catch (_: Throwable) {
            // Diagnóstico: jamás afecta al envío.
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun instalar(context: Context) {
        if (registrador != null) return
        try {
            val app = context.applicationContext
            registrador = RegistradorDeEnvioDiagnostico(
                dao = { AppDatabase.getInstance(app).envioDiagnosticoDao() },
                reportar = { datos -> reportarFallo(datos) }
            )
        } catch (_: Throwable) {
            // Sin diagnóstico la app sigue igual.
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun reportarFallo(datos: Map<String, Any>) {
        try {
            Logger.get().logEvent(
                module = "ENVIO_DIAGNOSTICO",
                eventType = RemoteLogger.EventType.NETWORK_EVENT,
                eventName = "envio_fallido",
                data = datos
            )
        } catch (_: Throwable) {
            // Telemetría caída: no pasa nada.
        }
    }
}
