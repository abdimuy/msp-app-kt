package com.example.msp_app.core.common.location.captura

/** De dónde llega el documento remoto de umbrales (en producción, Firestore `config/ubicacion`). */
fun interface FuenteDeConfigRemota {
    /** Registra [alRecibir]; cada cambio del documento la invoca con sus datos (o `null`) o con un error. */
    fun escuchar(alRecibir: (datos: Map<String, Any?>?, error: Throwable?) -> Unit)
}

/**
 * Los umbrales vigentes (E-APP-141). Editables sin publicar app: el documento `config/ubicacion`
 * se lee con [ConfigDeAceptacion.actualizadaCon] (tipo exacto, rango seguro, valor inválido conserva
 * el vigente). Un error de lectura NO cambia nada. Solo lee.
 */
class ConfigDeUbicacionVigente(private val fuente: FuenteDeConfigRemota) {
    @Volatile
    var actual: ConfigDeAceptacion = ConfigDeAceptacion.POR_DEFECTO
        private set

    @Volatile
    private var instalado = false

    @Synchronized
    @Suppress("TooGenericExceptionCaught")
    fun instalar() {
        if (instalado) return
        try {
            fuente.escuchar(::alRecibir)
            instalado = true
        } catch (_: Exception) {
            // Sin fuente se conserva el vigente (los defaults).
        }
    }

    fun alRecibir(datos: Map<String, Any?>?, error: Throwable?) {
        if (error != null) return
        actual = actual.actualizadaCon(datos ?: emptyMap())
    }
}
