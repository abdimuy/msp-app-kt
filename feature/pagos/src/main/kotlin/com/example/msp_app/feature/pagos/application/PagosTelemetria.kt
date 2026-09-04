package com.example.msp_app.feature.pagos.application

/**
 * Los códigos de error de `:feature:pagos`. Constantes nombradas, únicas y
 * **grepeables** — la NORMA DE ERRORES (`global-constraints.md`) existe porque
 * un código que no se puede grepear no sirve para debuggear, que es el punto
 * entero de la norma.
 *
 * Disciplina anti-PII: ningún código ni ningún `props` de este módulo lleva
 * nombre, teléfono, dirección ni monto de cliente. Los `props` que sí se
 * emiten son conteos y nombres de clase de excepción.
 */
object PagosTelemetria {

    /** Falló la carga del detalle de cliente. El id del cliente NO se emite. */
    const val CODE_DETALLE_CLIENTE_FALLO: String = "pagos_detalle_cliente_fallo"

    /** Falló la carga del detalle de venta. El id de la venta NO se emite. */
    const val CODE_DETALLE_VENTA_FALLO: String = "pagos_detalle_venta_fallo"

    /**
     * No se pudo leer `FECHA_CARGA_INICIAL`, así que no hay ventana de cobro y
     * el periodo no se puede derivar. Se emite igual —"es esperado" no autoriza
     * el silencio, autoriza un código propio— porque un cobrador viendo todas
     * sus cuentas en "sin trabajar" tiene que ser diagnosticable.
     */
    const val CODE_PERIODO_DESCONOCIDO: String = "pagos_periodo_desconocido"

    /**
     * `CITA_HORA` no venía en `HH:mm`. La visita se conserva sin hora; el
     * evento existe para que un build que escriba mal ese campo se vea.
     */
    const val CODE_CITA_HORA_INVALIDA: String = "pagos_cita_hora_invalida"

    /**
     * Un abono con `FECHA_HORA_PAGO` impresentable. **Se cae del historial**, y
     * con él del subtotal del mes en el riel — o sea, desaparece dinero de una
     * pantalla de dinero. No es un `catch` (`parseWireFormatOrNull` devuelve
     * `null` por contrato) pero es exactamente lo que la NORMA DE ERRORES
     * existe para que no pase en silencio.
     */
    const val CODE_ABONO_SIN_FECHA_LEGIBLE: String = "pagos_abono_sin_fecha_legible"

    /**
     * La fecha de vigencia que devolvió el cálculo de liquidación no se pudo
     * leer. La cifra se conserva (es lo que el cobrador va a cobrar); lo que se
     * pierde es el "vigente hasta". Se emite porque una liquidación sin
     * vigencia visible es una oferta sin caducidad, y eso es dinero.
     */
    const val CODE_LIQUIDACION_VIGENCIA_ILEGIBLE: String = "pagos_liquidacion_vigencia_ilegible"

    /**
     * `Sale.FECHA` no se pudo leer, así que la venta no tiene fecha con la cual
     * ordenarse y cae al final de su grupo en la lista de cobranza.
     *
     * No es un `catch` (`parseWireFormatOrNull` devuelve `null` por contrato),
     * pero **mueve de lugar una puerta en el día del cobrador**, que es
     * exactamente el tipo de cambio que no puede ocurrir sin señal.
     */
    const val CODE_VENTA_SIN_FECHA_LEGIBLE: String = "pagos_venta_sin_fecha_legible"

    /** Falló la carga de la lista de clientes. Ningún dato de cliente se emite. */
    const val CODE_LISTA_CLIENTES_FALLO: String = "pagos_lista_clientes_fallo"

    /** Clave estática de `props` con el conteo de ocurrencias de una incidencia. */
    const val PROP_OCURRENCIAS: String = "ocurrencias"

    /** Clave estática de `props` con el nombre simple de la clase de excepción. */
    const val PROP_EXCEPCION: String = "excepcion"
}
