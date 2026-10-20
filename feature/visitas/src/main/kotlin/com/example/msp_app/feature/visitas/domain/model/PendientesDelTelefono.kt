package com.example.msp_app.feature.visitas.domain.model

/**
 * Lo que este teléfono tiene sin enviar, por fuente (E-APP-096). Lo lee el
 * aviso de Inicio "N sin enviar · no cambies de teléfono": la regla del
 * runbook es cero pendientes de todo antes de cambiar de aparato, porque lo que
 * no subió se queda en este teléfono.
 *
 * - [visitas]: `Visit.GUARDADO_EN_MICROSIP = 0`.
 * - [fotos]: `visita_imagenes.SUBIDA_EN IS NULL` cuya visita sigue en el teléfono.
 * - [pagos] y [ventas]: las MISMAS lecturas que la tarjeta de envío del menú
 *   lateral (`PaymentDao.observarPendientesDeSubir`, `LocalSaleDao.observarSinSubir`).
 *
 * Una foto de una visita pendiente cuenta dos veces —la visita y la foto— a
 * propósito: son dos envíos distintos y el aviso no tiene que adivinar cuál
 * subirá primero.
 */
data class PendientesDelTelefono(
    val visitas: Int = 0,
    val fotos: Int = 0,
    val pagos: Int = 0,
    val ventas: Int = 0
) {
    /** N. Un conteo negativo (que un `COUNT(*)` no da) se trata como cero: nunca resta pendientes reales. */
    val total: Int
        get() = listOf(visitas, fotos, pagos, ventas).sumOf { it.coerceAtLeast(0) }

    /** El texto del aviso, o `null` cuando no hay nada que avisar. */
    fun textoDelAviso(): String? = total.takeIf { it > 0 }?.let { "$it $SUFIJO_DEL_AVISO" }

    private companion object {
        const val SUFIJO_DEL_AVISO = "sin enviar · no cambies de teléfono"
    }
}
