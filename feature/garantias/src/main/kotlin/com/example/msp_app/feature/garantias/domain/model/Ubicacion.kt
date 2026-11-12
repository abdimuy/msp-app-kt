package com.example.msp_app.feature.garantias.domain.model

/**
 * Un punto del GPS **del cobrador**, no del domicilio.
 *
 * El brief es explícito (D5): la ubicación del agente nunca se rotula "Casa" ni
 * "Trabajo" y nunca se manda como GPS del domicilio al abrir un folio. Este tipo
 * sólo representa lo que el teléfono midió en el momento de la captura; la
 * pantalla lo pinta como "Ubicación de la recolección" o "Ubicación de la
 * entrega".
 *
 * El rango se valida acá porque un `NaN` o un `Inf` que llegue a la ruta de
 * `avanzar` produce un 422 del servidor que el cobrador no puede corregir desde
 * el teléfono. [de] devuelve `null` en vez de lanzar: la ausencia de GPS es un
 * estado legítimo de la recolección (D5) y la pantalla ya sabe decir "Sin
 * ubicación".
 */
data class Ubicacion(
    val latitud: Double,
    val longitud: Double
) {
    init {
        require(latitud.isFinite() && latitud in RANGO_LATITUD) {
            "latitud fuera de rango: $latitud"
        }
        require(longitud.isFinite() && longitud in RANGO_LONGITUD) {
            "longitud fuera de rango: $longitud"
        }
    }

    companion object {
        private val RANGO_LATITUD = -90.0..90.0
        private val RANGO_LONGITUD = -180.0..180.0

        /**
         * La ubicación de [latitud]/[longitud], o `null` si falta alguna o no es
         * un número válido. Es la puerta que usan la captura y el worker: un
         * valor inválido se descarta en el teléfono, no se manda para que el
         * servidor lo rechace.
         */
        fun de(latitud: Double?, longitud: Double?): Ubicacion? {
            if (latitud == null || longitud == null) return null
            if (!latitud.isFinite() || !longitud.isFinite()) return null
            return runCatching { Ubicacion(latitud, longitud) }.getOrNull()
        }
    }
}
