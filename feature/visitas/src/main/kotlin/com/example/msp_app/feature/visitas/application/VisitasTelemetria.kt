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

    /** Id de pantalla del ticket de visita (Task 20). */
    const val PANTALLA_TICKET: String = "visitas_ticket"

    /**
     * `Visit.FECHA` no se pudo leer. **Sin ella la regla del día no puede
     * decidir**, así que el ticket no se arma: una fecha inventada abriría o
     * cerraría la impresión por accidente.
     */
    const val CODE_TICKET_VISITA_SIN_FECHA: String = "visita_ticket_sin_fecha"

    /** `CITA_HORA` no venía en `HH:mm`. El ticket se imprime sin hora. */
    const val CODE_TICKET_VISITA_HORA_INVALIDA: String = "visita_ticket_hora_invalida"

    /** La ruta del ticket apunta a una visita que el teléfono ya no tiene. */
    const val CODE_TICKET_VISITA_SIN_VISITA: String = "visita_ticket_sin_visita"

    /** Falló la lectura de la visita o del cliente del ticket. */
    const val CODE_TICKET_VISITA_FALLO: String = "visita_ticket_fallo"

    /**
     * La impresión del ticket de visita falló. Viaja el NOMBRE de la clase del
     * fallo, nunca la MAC de la impresora: identifica el equipo del cobrador.
     */
    const val CODE_TICKET_VISITA_NO_SE_IMPRIMIO: String = "visita_ticket_no_se_imprimio"

    /**
     * El punto de escritura RECHAZÓ la impresión porque el día de la visita ya
     * pasó. Llegar aquí significa que se tocó un CTA que debía estar apagado
     * —normalmente porque el día cambió con la pantalla abierta—: es un defecto
     * distinto de "no se imprimió" y por eso lleva código propio.
     */
    const val CODE_TICKET_VISITA_FUERA_DEL_DIA: String = "visita_ticket_fuera_del_dia"

    // ─── Los comprobantes de la visita (Task 23) ─────────────────────────────

    /**
     * La capa de fotos falló: la cámara no se pudo preparar, la compresión
     * reventó, el archivo no se pudo borrar. **La visita se registra igual** —
     * es la misma regla que ya rige para la ubicación. El error no se traga: se
     * emite con su código, exactamente como la norma exige para un fallo que se
     * decide ignorar.
     */
    const val CODE_VISITA_FOTO_FALLO: String = "visita_foto_fallo"

    /**
     * La cámara dejó un archivo de un tipo que el servidor no acepta. Código
     * propio y no [CODE_VISITA_FOTO_FALLO]: no es una falla del teléfono, es un
     * archivo que **la visita entera** habría hecho rebotar con 422
     * (`imagen_mime_no_permitido` corta el request completo), y se diagnostica
     * distinto.
     */
    const val CODE_VISITA_FOTO_TIPO_NO_PERMITIDO: String = "visita_foto_tipo_no_permitido"

    /**
     * La cámara devolvió una foto y ya no había destino que la reclamara. Es la
     * ÚNICA forma que tiene una foto de perderse en este camino, y perder
     * evidencia en silencio es justo lo que la norma prohíbe. Código propio
     * porque se diagnostica distinto de "no se pudo tomar".
     */
    const val CODE_VISITA_FOTO_SIN_DESTINO: String = "visita_foto_sin_destino"

    /**
     * Una entrada de comprobante guardada en el `SavedStateHandle` no se pudo
     * leer al volver de la muerte del proceso. Se descarta —no puede tumbar la
     * pantalla— pero se cuenta y se reporta.
     */
    const val CODE_VISITA_FOTO_ILEGIBLE: String = "visita_foto_ilegible"

    /**
     * La visita quedó escrita y encolada, pero sus comprobantes **no** se
     * pudieron guardar. La visita se conserva: el trabajo de campo pesa más que
     * la foto. Código propio porque el desenlace es distinto de
     * [CODE_VISITA_NO_SE_GUARDO] — ahí no quedó nada, aquí quedó la visita.
     */
    const val CODE_VISITA_COMPROBANTES_NO_SE_GUARDARON: String =
        "visita_comprobantes_no_se_guardaron"

    /** Prop con el MIME rechazado. Valor técnico cerrado, nunca dato del cliente. */
    const val PROP_TIPO: String = "tipo"

    /** Prop con CUÁNTAS veces ocurrió lo que se reporta. Nunca un dato de negocio. */
    const val PROP_OCURRENCIAS: String = "ocurrencias"

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
