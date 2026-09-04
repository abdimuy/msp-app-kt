package com.example.msp_app.feature.visitas.application

/**
 * Los códigos de telemetría de `:feature:visitas`.
 *
 * Constantes nombradas, únicas y **grepeables**: un código que no se puede
 * grepear no sirve para debuggear, que es el punto entero de la NORMA DE
 * ERRORES. Ninguno lleva PII — ni la nota del cobrador, ni el nombre del
 * cliente, ni el monto prometido.
 */
object VisitasTelemetria {

    /** Id de pantalla de registrar visita. */
    const val PANTALLA: String = "visitas_registrar"

    /** No se pudo leer el cliente ni sus cuentas. La pantalla queda en error. */
    const val CODE_CONTEXTO_FALLO: String = "visita_contexto_fallo"

    /**
     * No se pudo obtener la ubicación (permiso negado, proveedor mudo, hardware
     * caído). **La visita se registra igual** — es la propiedad de la Task 5: el
     * envío no cuelga del servicio de ubicación. El error no se traga: se emite
     * con su código, exactamente como la norma exige para un fallo que se decide
     * ignorar.
     */
    const val CODE_UBICACION_NO_DISPONIBLE: String = "visita_ubicacion_denegada"

    /** La escritura de la visita falló. Nada quedó escrito: es una transacción. */
    const val CODE_VISITA_NO_SE_GUARDO: String = "visita_no_se_guardo"

    /**
     * La pantalla dejó pasar una captura bloqueada hasta el caso de uso. Nunca
     * debería ocurrir; que exista este código es lo que hace observable el
     * segundo cinturón en vez de silencioso.
     */
    const val CODE_CAPTURA_BLOQUEADA_EN_APLICACION: String = "visita_captura_bloqueada"

    /**
     * Había una recomendación que atender y al guardar ya no estaba: la fila
     * desapareció entre la lectura y la escritura. El par sugerencia/desenlace
     * se pierde para esa visita, así que **no** se calla.
     */
    const val CODE_RECOMENDACION_NO_LIGADA: String = "visita_recomendacion_no_ligada"

    /** No se pudo leer la recomendación del cliente. La pantalla sigue sin ella. */
    const val CODE_RECOMENDACION_FALLO: String = "visita_recomendacion_fallo"

    /** Prop con el NOMBRE de la clase de la excepción — nunca su texto. */
    const val PROP_EXCEPCION: String = "exception"

    /** Prop que distingue por qué no hubo ubicación. */
    const val PROP_CAUSA: String = "causa"

    /** El proveedor lanzó. Va acompañado de [PROP_EXCEPCION]. */
    const val CAUSA_EXCEPCION: String = "excepcion"

    /** El proveedor contestó sin dato — el permiso negado cae aquí. */
    const val CAUSA_SIN_DATO: String = "sin_dato"

    /** Prop con el nombre del [com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro]. */
    const val PROP_RESULTADO: String = "resultado"

    /** Prop con los NOMBRES de los bloqueos, separados por coma. Nunca sus datos. */
    const val PROP_BLOQUEOS: String = "bloqueos"
}
