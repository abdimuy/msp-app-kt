package com.example.msp_app.feature.pagos.domain.model

import java.time.LocalDate

/**
 * La garantía abierta de una venta — la tarjeta `.gar` del mock.
 *
 * El brief nombra `garantía` entre lo que vive en la venta, y **ninguna tarea
 * posterior es dueña de esta pantalla** (17 es la lista, 18 el abono, 19 la
 * visita, 20 los tickets, 21 la navegación): diferirla sería dejarla caer.
 *
 * Los cuatro campos salen 1:1 de `GuaranteeEntity`, que ya existe en producción
 * — esto es una LECTURA, no un schema nuevo. La llave de unión es
 * `DOCTO_CC_ID`, que en este módulo es [DetalleVenta.creditoId].
 *
 * **La lógica de garantías no se toca.** Las dos acciones del mock
 * ("recolectar producto", "imprimir aviso") son flujos que ya viven en
 * `:app` (`features/guarantees`); esta tarjeta las alcanza por navegación
 * ([garantiaId]) en vez de reimplementarlas, por la misma razón por la que la
 * liquidación se consume detrás de un puerto y la condonación se queda igual.
 */
data class GarantiaDeLaVenta(
    val garantiaId: String,
    val producto: String,
    val reportadaEl: LocalDate?,
    val falla: String,
    val estado: EstadoDeGarantia
)

/**
 * Los tres estados de una garantía.
 *
 * Los literales son copia deliberada de `Constants.NOTIFICADO`/`RECOLECTADO`/
 * `ENTREGADO` (`:app`), que `:core:common` y los `:feature:*` no pueden
 * importar — mismo reparto que la copia de literales de `TipoVisitaCatalogo`.
 * [de] es TOTAL: un estado que no reconozcamos aterriza en [DESCONOCIDO] y se
 * pinta como tal, nunca en la nada.
 */
enum class EstadoDeGarantia(val crudo: String, val etiqueta: String) {
    /** Se reportó y el cliente está avisado. */
    NOTIFICADA("NOTIFICADO", "notificada"),

    /** El producto ya se recogió. */
    RECOLECTADA("RECOLECTADO", "recolectada"),

    /** Se devolvió al cliente. */
    ENTREGADA("ENTREGADO", "entregada"),

    /** Cualquier otro valor. Se muestra, no se esconde. */
    DESCONOCIDO("", "sin estado");

    companion object {
        fun de(crudo: String): EstadoDeGarantia =
            entries.firstOrNull { it.crudo == crudo } ?: DESCONOCIDO
    }
}
