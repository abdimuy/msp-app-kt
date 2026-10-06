package com.example.msp_app.core.navegacion.application

/** Códigos de telemetría del menú lateral: constantes, únicos y grepeables (principio 20). */
object NavegacionTelemetria {
    const val CODE_CONTADORES_FALLARON: String = "menu_contadores_fallaron"
    const val CODE_ENVIO_FALLO: String = "menu_envio_fallo"
    const val CODE_PLEGADAS_FALLARON: String = "menu_plegadas_fallaron"

    /** Ruling (controlador, 2026-10-06): un catch más por cada puerto que se sumó al estado. */
    const val CODE_ZONA_FALLO: String = "menu_zona_fallo"
    const val CODE_TEMA_FALLO: String = "menu_tema_fallo"
    const val CODE_MONTOS_FALLO: String = "menu_montos_fallo"
}
