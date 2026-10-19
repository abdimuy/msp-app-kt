package com.example.msp_app.core.common.sync.pendingwork.domain.ports

/**
 * ¿Hay pagos o ventas locales esperando subir? (prioridad del dinero en el
 * barrido de fotos de la reconciliación inversa; E-APP-079, E-VIS-032).
 *
 * Mientras haya dinero pendiente, el barrido no reabre visitas: cada visita
 * reabierta es un `POST` multipart que compartiría la red con los pagos. El
 * adaptador solo LEE (cuenta filas); nunca escribe en las tablas de dinero.
 */
fun interface DineroPendiente {
    suspend fun hayPendientes(): Boolean

    companion object {
        /** Sin dinero que esperar (pruebas del use case que no lo miran). */
        val NINGUNO: DineroPendiente = DineroPendiente { false }
    }
}
