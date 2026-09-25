package com.example.msp_app.core.geo

import java.time.Instant

/**
 * **Una medición de dónde estaba parado el cobrador al registrar un abono.**
 *
 * El encuadre entero de la pantalla de ubicación cabe en el nombre de este tipo:
 * un cliente con 28 cobros **no tiene 28 lugares, tiene un lugar y 28
 * mediciones de él**. Lo que separa a las mediciones entre sí es el error del
 * GPS, no que el cliente se haya mudado 28 veces.
 *
 * (Medido el 2026-09-24 sobre 198,505 puntos reales: la distancia mediana entre
 * dos mediciones del mismo cliente es de 22.5 m, y la mediana de la distancia al
 * centro de su grupo-puerta es de **7.9 m**.)
 *
 * ## Por qué [esTransferencia] vive aquí y no se deduce después
 *
 * Una transferencia **se registra donde está el cobrador**, no en la puerta del
 * cliente — muchas veces en su propia casa. Su coordenada es **cierta como
 * hecho** (ahí se capturó el pago) y **falsa como evidencia de domicilio**.
 *
 * Eso la vuelve el único caso en el que se puede afirmar sin estadística: no hay
 * que inferir nada, el pago mismo lo dice. Por eso viaja como un hecho del dato
 * y no como algo que [AgrupadorDeLugares] adivine.
 *
 * Alcance medido, para que nadie le pida a esta bandera más de lo que da: las
 * transferencias son el **2.10 %** de los pagos y explican sólo el **14.4 %** de
 * los puntos compartidos — el 85.6 % restante es efectivo. Es una señal
 * confiable y chica; el mecanismo principal es [IndiceDePuntosCompartidos].
 */
data class MedicionDelCobro(
    /** El id del abono. Es lo que permite resaltar el renglón que se tocó. */
    val pagoId: String,
    /** Dónde se capturó. */
    val punto: Punto,
    /** Cuándo. Es lo que pinta la antigüedad y lo que delata una mudanza. */
    val fecha: Instant,
    /** De qué cuenta fue el abono. Alimenta el filtro por venta. */
    val ventaId: Int,
    /** Quién cobró. Alimenta el filtro por cobrador. */
    val cobrador: String,
    /**
     * `true` cuando el abono entró por transferencia.
     *
     * Un punto así **se dibuja** —nada se esconde— pero **no cuenta para decidir
     * cuál grupo es la puerta**. Ver el KDoc de la clase.
     */
    val esTransferencia: Boolean
)
