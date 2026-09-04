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

    /**
     * Un abono BLOQUEADO (sobrepago, no positivo o venta sin saldo) llegó hasta
     * el caso de uso. El segundo cinturón lo detuvo y nada se escribió, pero
     * significa que la pantalla dejó pasar algo: es un defecto, no una
     * condición normal, y por eso se emite en vez de solo devolverse.
     */
    const val CODE_ABONO_BLOQUEADO_EN_APLICACION: String = "pagos_abono_bloqueado_en_aplicacion"

    /**
     * La ESCRITURA del abono falló, en el adaptador. Lo emite quien vio la
     * excepción; el par pago + saldo va dentro de una transacción de Room, así
     * que la base quedó como estaba.
     */
    const val CODE_ABONO_NO_SE_GUARDO: String = "pagos_abono_no_se_guardo"

    /**
     * El TERCER cinturón —el del punto de escritura— rechazó el monto contra el
     * `SALDO_REST` recién leído. Distinto de
     * [CODE_ABONO_BLOQUEADO_EN_APLICACION]: aquí el monto era válido cuando la
     * pantalla lo dejó pasar y dejó de serlo antes de escribirse (saldo rancio),
     * que es otro defecto y se diagnostica distinto.
     */
    const val CODE_ABONO_BLOQUEADO_EN_ESCRITURA: String = "pagos_abono_bloqueado_en_escritura"

    /**
     * El abono no quedó registrado, visto desde la PANTALLA. Es un evento
     * distinto de [CODE_ABONO_NO_SE_GUARDO] y por eso lleva código propio:
     * emitir los dos con el mismo código contaría una sola falla dos veces, y
     * el conteo es justo la señal que la norma de errores existe para producir.
     * Cubre además los finales que el adaptador nunca ve (sin cobrador, venta
     * ausente, bloqueado).
     */
    const val CODE_ABONO_NO_QUEDO_REGISTRADO: String = "pagos_abono_no_quedo_registrado"

    /**
     * El puerto reportó un fallo pero el abono **sí** está en el historial: la
     * escritura aterrizó y el resultado se perdió en el camino. La pantalla se
     * queda en su final (no se vuelve a cobrar) y el desacuerdo se reporta,
     * porque un puerto que miente sobre dinero tiene que verse.
     */
    const val CODE_ABONO_FALLO_PERO_SI_QUEDO: String = "pagos_abono_fallo_pero_si_quedo"

    /**
     * No se pudo comprobar si el abono quedó o no. **El guard NO se libera**:
     * soltarlo sin saber es exactamente la suposición que este diseño evita.
     */
    const val CODE_ABONO_SIN_VERIFICAR: String = "pagos_abono_sin_verificar"

    /**
     * El destino de abono volvió con su guard anti-duplicado puesto pero el
     * abono NO aparece en el historial de la venta: la escritura nunca aterrizó
     * (proceso muerto entre el toque y la transacción). Se libera el guard para
     * que el cobrador pueda capturar de nuevo, y se emite porque un guard que
     * se libera solo es exactamente el tipo de cosa que no puede pasar callada
     * en una pantalla de dinero.
     */
    const val CODE_ABONO_GUARD_SIN_ABONO: String = "pagos_abono_guard_sin_abono"

    /** Falló la carga de la venta en la pantalla de abono. El id NO se emite. */
    const val CODE_ABONO_VENTA_FALLO: String = "pagos_abono_venta_fallo"

    /**
     * El abono quedó escrito pero **no se pudo pedir la ubicación**: arrancar
     * `UpdateLocationService` falló (Android 12+ rechaza un
     * `startForegroundService` con la app en segundo plano, y el sistema puede
     * negarlo por otras razones).
     *
     * El dinero NO se toca: el abono ya está en la base y el resultado sigue
     * siendo [com.example.msp_app.feature.pagos.domain.port.ResultadoDelAbono.REGISTRADO].
     * Lo que se pierde es `LAT`/`LNG`, y con ellas el punto del abono en el mapa
     * del día — una pérdida silenciosa sería justo lo que la NORMA DE ERRORES
     * prohíbe, así que lleva código propio y grepeable.
     */
    const val CODE_ABONO_SIN_UBICACION: String = "pagos_abono_sin_ubicacion"

    /**
     * **La cámara no dejó comprobante.** Cubre las tres formas de fallar del
     * puerto de comprobantes —preparar el destino, comprimir la foto tomada, o
     * borrar un archivo descartado—, todas fuera del camino del dinero.
     *
     * El abono se captura y se registra igual: llevar comprobante es opcional y
     * **la foto nunca bloquea el guardado**. Lo que no puede es fallar en
     * silencio: un cobrador que cree haber adjuntado el recibo y no lo adjuntó
     * se entera cuando la oficina se lo pide, semanas después.
     */
    const val CODE_ABONO_FOTO_FALLO: String = "pagos_abono_foto_fallo"

    /**
     * La foto capturada trae un tipo que **el servidor no acepta**
     * ([com.example.msp_app.feature.pagos.domain.Comprobantes.TIPOS_PERMITIDOS],
     * copia de la whitelist de `CrearPagoMultipartFields`). Se descarta al
     * adjuntar, en el teléfono, en vez de guardarse para que la subida la
     * rechace con un 422 que nadie va a ver — pero se reporta: una cámara que
     * empieza a devolver otro tipo es un cambio de plataforma que hay que ver.
     */
    const val CODE_ABONO_FOTO_TIPO_NO_PERMITIDO: String = "pagos_abono_foto_tipo_no_permitido"

    /**
     * Al volver de la muerte del proceso, una entrada de comprobante guardada
     * en el `SavedStateHandle` no se pudo leer. Se descarta solo la ilegible y
     * el resto de la captura sigue viva. Lleva el CONTEO, nunca la ruta: una
     * foto que desaparece de la pantalla sin decir nada es justo lo que la
     * norma de errores prohíbe.
     */
    const val CODE_ABONO_FOTO_ILEGIBLE: String = "pagos_abono_foto_ilegible"

    /**
     * La cámara devolvió una foto y **ya no había destino que la reclamara**.
     *
     * Es la única forma que tiene una foto de perderse en el camino de captura,
     * así que lleva código propio en vez de irse con el de "no se pudo tomar":
     * son fallas distintas y se diagnostican distinto. El abono no se ve
     * afectado —la foto nunca lo bloquea—, pero el cobrador creyó adjuntar algo
     * que no quedó, y eso tiene que ser visible.
     */
    const val CODE_ABONO_FOTO_SIN_DESTINO: String = "pagos_abono_foto_sin_destino"

    /**
     * El abono quedó escrito pero **sus comprobantes no se pudieron guardar**.
     * El dinero no se toca: el resultado sigue siendo `REGISTRADO`, igual que
     * cuando falla la ubicación. Lo que se pierde es la foto, así que lleva
     * código propio y grepeable en vez de irse con el del guardado.
     */
    const val CODE_ABONO_SIN_COMPROBANTES: String = "pagos_abono_sin_comprobantes"

    /**
     * La ruta del ticket de pago apunta a un abono que el teléfono no tiene (o a
     * una condonación, que no sale por el puerto de cobranza). El id NO se
     * emite. Es una ruta rota, no una condición normal, y por eso se reporta.
     */
    const val CODE_TICKET_PAGO_SIN_ABONO: String = "pagos_ticket_sin_abono"

    /** Falló la lectura del abono del ticket. Ningún dato del cliente se emite. */
    const val CODE_TICKET_PAGO_FALLO: String = "pagos_ticket_fallo"

    /**
     * La impresión del ticket de pago falló. Viaja el NOMBRE de la clase del
     * fallo, nunca la MAC de la impresora: identifica el equipo del cobrador.
     */
    const val CODE_TICKET_PAGO_NO_SE_IMPRIMIO: String = "pagos_ticket_no_se_imprimio"

    /**
     * El punto de escritura RECHAZÓ la impresión porque el día del cobro ya
     * pasó. Llegar aquí significa que la pantalla dejó tocar un CTA que debía
     * estar apagado —normalmente porque el día cambió con la pantalla abierta—,
     * y por eso lleva código propio y no el de "no se imprimió": es un defecto
     * distinto y se diagnostica distinto.
     */
    const val CODE_TICKET_PAGO_FUERA_DEL_DIA: String = "pagos_ticket_fuera_del_dia"

    /**
     * **No se pudo LEER la ficha del cliente.** El detalle se pinta igual —la
     * ficha no bloquea una pantalla de dinero—, pero la sección queda en "no se
     * pudo leer" y **sin poder editarse**: una ficha en blanco editable sobre
     * una lectura fallida invitaría a escribir encima del conocimiento que sí
     * estaba guardado.
     */
    const val CODE_FICHA_NO_SE_PUDO_LEER: String = "pagos_ficha_no_se_pudo_leer"

    /**
     * La ESCRITURA de la ficha falló. Nada quedó a medias: nota y señales van
     * dentro de una transacción de Room. Ni el texto de la nota ni el id del
     * cliente se emiten.
     */
    const val CODE_FICHA_NO_SE_GUARDO: String = "pagos_ficha_no_se_guardo"

    /**
     * La ficha se guardó **sin `COBRADOR_ID`** porque el usuario no se pudo
     * resolver. Es un final deliberado y no un fallo —la ficha no es dinero y
     * `COBRADOR_ID` es nullable a propósito—, pero "es esperado" no autoriza el
     * silencio: autoriza un código propio. Una app que empieza a guardar TODA
     * la ficha sin atribución es una sesión rota, y tiene que verse.
     */
    const val CODE_FICHA_SIN_COBRADOR: String = "pagos_ficha_sin_cobrador"

    /**
     * La ficha guardada trae literales de señal que este build no conoce — un
     * valor retirado del catálogo, que *nace corto y crece con evidencia*. Se
     * ignoran al pintar y **no se borran** al guardar. Viaja el CONTEO, nunca el
     * literal: viene del disco y la norma anti-PII exige `props` estático del
     * desarrollador.
     */
    const val CODE_FICHA_SENAL_DESCONOCIDA: String = "pagos_ficha_senal_desconocida"

    /**
     * La ficha no quedó guardada, visto **desde la pantalla**. Es un evento
     * distinto de [CODE_FICHA_NO_SE_GUARDO] —el del adaptador— y por eso lleva
     * código propio: emitir los dos con el mismo código contaría una sola falla
     * dos veces, y el conteo es justo la señal que la norma de errores existe
     * para producir. Cubre además el final que el adaptador nunca ve.
     */
    const val CODE_FICHA_NO_QUEDO_GUARDADA: String = "pagos_ficha_no_quedo_guardada"

    /** Clave estática de `props` con los nombres de los bloqueos de seguridad. */
    const val PROP_BLOQUEOS: String = "bloqueos"

    /** Clave estática de `props` con el nombre del resultado del registro. */
    const val PROP_RESULTADO: String = "resultado"

    /** Clave estática de `props` con el conteo de ocurrencias de una incidencia. */
    const val PROP_OCURRENCIAS: String = "ocurrencias"

    /** Clave estática de `props` con el nombre simple de la clase de excepción. */
    const val PROP_EXCEPCION: String = "excepcion"

    /**
     * Clave estática de `props` con el MIME rechazado. Un MIME es un valor
     * técnico de catálogo cerrado, no un dato del cliente: no es PII.
     */
    const val PROP_TIPO: String = "tipo"
}
