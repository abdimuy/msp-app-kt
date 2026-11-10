package com.example.msp_app.feature.compartirproductos.domain

import com.example.msp_app.core.common.time.BUSINESS_ZONE
import java.time.Duration
import java.time.Instant

/** «Precios vigentes al 10 nov 2026»: la fecha de HOY en la zona del negocio. */
object FechaVigente {
    // A mano y no con `MMM`: el abreviado de la JVM/Android cambia entre versiones
    // («sep.» / «sept.», con o sin punto) y la tarjeta es una imagen que no puede variar.
    private val MESES =
        listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    fun fecha(instante: Instant): String {
        val dia = instante.atZone(BUSINESS_ZONE).toLocalDate()
        return "${dia.dayOfMonth} ${MESES[dia.monthValue - 1]} ${dia.year}"
    }

    fun leyenda(instante: Instant): String = "Precios vigentes al ${fecha(instante)}"
}

/** El aviso «Los precios pueden no estar actualizados» (más de 3 días sin actualizar el catálogo). */
object AvisoDeCatalogo {
    val UMBRAL: Duration = Duration.ofDays(3)
    const val MENSAJE = "Los precios pueden no estar actualizados"

    /** Sin marca de actualización no se avisa: no se inventa una antigüedad. */
    fun desactualizado(ultimaActualizacion: Instant?, ahora: Instant): Boolean =
        ultimaActualizacion != null && Duration.between(ultimaActualizacion, ahora) > UMBRAL
}
