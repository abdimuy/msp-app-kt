package com.example.msp_app.feature.pagos.domain

/**
 * Las formas en que el cobrador puede ordenar la lista de clientes, elegidas
 * desde la hoja "Ordenar por" (decisión del dueño del 2026-10-05).
 *
 * El criterio de cada orden decide quién va PRIMERO; los empates se resuelven
 * con el orden de cobranza de siempre y, al final, por `clienteId` — ver
 * [com.example.msp_app.feature.pagos.ui.CarteraEnPantalla.proyectar].
 */
enum class OrdenDeLaLista {
    /**
     * El predeterminado, el de siempre: primero quien no ha abonado nada,
     * después la venta más vieja. Es exactamente [OrdenDeCobranza.PRIMERO] vía
     * [RangoDeCobranza] — no se toca.
     */
    COBRANZA,

    /** Primero el cliente con la venta más reciente, descendente. */
    VENTAS_NUEVAS,

    /** Primero el cliente con más pagos atrasados en cualquiera de sus ventas. */
    MAS_ATRASOS,

    /**
     * Primero quien nunca ha pagado, luego por el pago más viejo entre los que
     * sí han pagado alguna vez.
     */
    SIN_PAGAR_HACE_MAS,

    /** Primero el cliente con el saldo total más alto. */
    MAYOR_SALDO,

    /** De la A a la Z, ignorando acentos y mayúsculas. */
    NOMBRE,

    /**
     * Primero el cliente cuya venta más cercana está a menos distancia del
     * cobrador; quien no tiene ninguna ubicación calculada va al final.
     */
    MAS_CERCA
}
