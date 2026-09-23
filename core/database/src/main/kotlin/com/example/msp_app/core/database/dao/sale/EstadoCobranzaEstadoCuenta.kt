package com.example.msp_app.core.database.dao.sale

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta

/**
 * Puente del enum legado [EstadoCobranza] (5 valores, escrito en
 * `Sale.ESTADO_COBRANZA` desde 2024) al catálogo de ocho de la Task 14.
 *
 * ## Para qué existe
 *
 * Para que el **histórico** y el **orden de la lista** no se rompan.
 * `SalesScreen` —la pantalla que la Task 21 retiró— particionaba por
 * `ESTADO_COBRANZA` (`PENDIENTE || VISITADO` a un lado, `PAGADO` al otro) y las
 * filas viejas solo tienen esa columna. Este mapeo las traduce al vocabulario nuevo sin reescribir una sola
 * fila.
 *
 * ## Para qué NO existe
 *
 * **No** decide el periodo en curso. `EstadoCuentaDeriver` deriva desde los
 * pagos y las visitas de la ventana y jamás consulta esta columna, porque
 * `PaymentsViewModel.savePayment` la escribe en `PAGADO` con **cada** pago
 * guardado sin comparar el monto contra `PARCIALIDAD`. Usarla como fallback
 * dejaría "Pagó" pintado al abrir la semana nueva y "sin tocar" no aparecería
 * nunca.
 *
 * ## Por qué vive en `:core:database` y no junto al catálogo
 *
 * [EstadoCobranza] vive acá porque `SaleDao.updateTotal` lo enlaza como
 * parámetro de una `@Query`. `:core:database` depende de `:core:common`, no al
 * revés, así que la traducción tiene que estar de este lado. Es un `when`
 * exhaustivo sin `else`: agregar un sexto valor a [EstadoCobranza] rompe la
 * compilación en vez de caer en silencio a un default.
 */
fun EstadoCobranza.aEstadoCuenta(): EstadoCuenta = when (this) {
    // Lo que el flujo legado marcaba como cobrado. Que sea "Pagó" completo y no
    // "Abonó parcial" es exactamente la información que la columna NO tiene —
    // por eso el periodo en curso se deriva del dinero, no de acá.
    EstadoCobranza.PAGADO -> EstadoCuenta.PAGO

    // Lo escribe `VisitStatusMapper` solo para `NO_VA_A_DAR_PAGO`: una negativa.
    EstadoCobranza.NO_PAGADO -> EstadoCuenta.SE_NEGO

    // El bucket ancho de `VisitStatusMapper`: pasaste y no se resolvió.
    EstadoCobranza.VOLVER_VISITAR -> EstadoCuenta.VISITE_VUELVO

    // Ningún camino del código actual lo escribe, y su único lector era el filtro
    // de `SalesScreen` (retirada por la Task 21); son filas viejas que
    // significan "alguien pasó por aquí".
    EstadoCobranza.VISITADO -> EstadoCuenta.VISITE_VUELVO

    // El estado inicial que pone `AuthViewModel` al sincronizar ventas: nadie la
    // ha trabajado.
    EstadoCobranza.PENDIENTE -> EstadoCuenta.SIN_TOCAR
}
