package com.example.msp_app.feature.pagos.data.adapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.msp_app.feature.pagos.domain.port.AccionesExternasPort
import com.example.msp_app.feature.pagos.domain.port.DestinoEnElMapa

/**
 * Adaptador de [AccionesExternasPort] sobre `Intent`.
 *
 * `FLAG_ACTIVITY_NEW_TASK` va en los tres porque el `Context` que se inyecta es
 * el de la aplicación, no el de la `Activity`: sin la bandera, Android tira
 * `AndroidRuntimeException` al arrancar la actividad desde fuera de una tarea.
 *
 * **Ningún fallo escapa.** `startActivity` lanza `ActivityNotFoundException`
 * cuando el teléfono no tiene app de teléfono, de WhatsApp o de mapas, y los
 * tres casos existen en la flota. [envuelto] los convierte en un `Result`
 * fallido; quien llama decide, y lo que hace es reportarlo.
 */
class IntentAccionesExternasAdapter(
    private val context: Context
) : AccionesExternasPort {

    override suspend fun marcar(telefono: String): Result<Unit> =
        envuelto(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + soloDigitos(telefono))))

    /**
     * `wa.me` y no el esquema `whatsapp://`: es el enlace que WhatsApp
     * documenta, y en un teléfono sin WhatsApp lo atiende el navegador con la
     * página de descarga en vez de no atenderlo nadie.
     *
     * El número viaja **solo con dígitos**: `wa.me` rechaza espacios, guiones y
     * paréntesis, y `TELEFONO` llega de Microsip con los tres.
     */
    override suspend fun escribirPorWhatsApp(telefono: String): Result<Unit> = envuelto(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + soloDigitos(telefono)))
    )

    override suspend fun comoLlegar(destino: DestinoEnElMapa): Result<Unit> =
        envuelto(Intent(Intent.ACTION_VIEW, uriDe(destino)))

    /**
     * `geo:lat,lng?q=lat,lng(etiqueta)` cuando hay punto medido, y
     * `geo:0,0?q=<dirección>` cuando no.
     *
     * El `q=` repetido con las coordenadas no es redundante: sin él, varias apps
     * de mapas centran el mapa en el punto **pero no ponen pin**, y un mapa
     * centrado sin marca no dice cuál de las casas es.
     */
    private fun uriDe(destino: DestinoEnElMapa): Uri {
        val lat = destino.lat
        val lng = destino.lng
        return if (lat != null && lng != null) {
            Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(destino.etiqueta)})")
        } else {
            Uri.parse("geo:0,0?q=" + Uri.encode(destino.direccion))
        }
    }

    private fun envuelto(intent: Intent): Result<Unit> = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object {
        val NO_ES_DIGITO = Regex("[^0-9]")
    }

    private fun soloDigitos(telefono: String): String = telefono.replace(NO_ES_DIGITO, "")
}
