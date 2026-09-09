package com.example.msp_app.core.common.cobranza.domain

/**
 * Los OCHO estados que el cobrador puede ver por cuenta en el periodo de cobro
 * (`task-14-brief.md`, tabla de los ocho estados).
 *
 * Se **derivan en el teléfono**, no los sirve el API: el inicio de periodo ya
 * vive en el dispositivo (`FECHA_CARGA_INICIAL` de Firestore) y calcular
 * localmente hace que una visita recién capturada se vea al instante, sin
 * esperar sync. Cero API nueva.
 *
 * [alcance] es la columna "Alcance" de esa misma tabla: [VisitScope.CLIENTE]
 * son hechos del domicilio (se propagan a todas las ventas del cliente),
 * [VisitScope.VENTA] son hechos de esa deuda.
 *
 * ## Presentación (regla del plan, no negociable)
 *
 * Color + ícono + texto — **nunca solo color**. Este enum no decide nada de
 * eso: es dominio puro y no conoce `MspColors` ni Compose. Las pantallas de la
 * Fase 5 mapean cada valor a su terna.
 *
 * ## Los dos estados que todavía no se derivan
 *
 * [PROMETIO_PROXIMA] es alcanzable hoy desde `Pidió reagendar visita`, pero
 * **sin** su fecha ni su monto; [CITA_A_UNA_HORA] no es alcanzable desde
 * ningún literal de `TIPO_VISITA` — no existe una etiqueta que signifique
 * "quedamos a tal hora". Ese dato estructurado lo captura la Task 19 y sus
 * columnas llegan en la Task 26. Hasta entonces los campos viajan en `null`
 * dentro de [ResultadoEstadoCuenta] y **no** se reconstruyen desde el texto
 * libre de `NOTA` — esa reconstrucción es exactamente el defecto que este plan
 * vino a arreglar (el retirado `NewVisitDialog` escribía "La cita ha sido
 * reagendada para el …" dentro de la nota; parsear eso es adivinar).
 */
enum class EstadoCuenta(val alcance: VisitScope) {
    /** Cobró completo lo del periodo. No hace falta volver. */
    PAGO(VisitScope.VENTA),

    /** Dio algo pero no lo esperado (*por confirmar*). */
    ABONO_PARCIAL(VisitScope.VENTA),

    /** Pasaste, no se resolvió. Regresas este periodo. */
    VISITE_VUELVO(VisitScope.VENTA),

    /** Da hasta el siguiente periodo. Rojo = no cae este periodo. */
    PROMETIO_PROXIMA(VisitScope.VENTA),

    /** Rojo **sólido** = escalar, atención especial. */
    SE_NEGO(VisitScope.VENTA),

    /** Quedaron de verse. Muestra la hora. Se propaga a las ventas del cliente. */
    CITA_A_UNA_HORA(VisitScope.CLIENTE),

    /** Fuiste y no había quién atendiera. Se propaga a las ventas del cliente. */
    NO_ESTABA(VisitScope.CLIENTE),

    /** Nadie trabajó esta cuenta este periodo. La base. */
    SIN_TOCAR(VisitScope.VENTA)
}
