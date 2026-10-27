package com.example.msp_app.core.common.sync.pendingwork.domain.ports

import com.example.msp_app.core.common.visitas.envio.CompromisoLocal

/**
 * Una visita soltada cuyo compromiso el teléfono da por entregado
 * (`COMPROMISO_CONFIRMADO_EN` puesta), con el compromiso que tiene guardado.
 */
data class EntregadaConCompromiso(val visitaId: String, val compromiso: CompromisoLocal)

/**
 * Lo que la conciliación inversa del compromiso (plan visitas P2, Tarea 18;
 * spec §4.6-1; E-APP-132) necesita de la base local. Solo LEE las visitas y
 * puede devolver a pendiente las que el servidor no tiene completas; nunca toca
 * pagos ni ventas.
 */
interface CompromisosEntregadosStore {

    /**
     * Las soltadas desde [desde] (o legadas sin confirmar) que tienen promesa o
     * cita y cuyo compromiso el teléfono ya dio por entregado y no resguardado.
     */
    suspend fun entregadasConCompromisoDesde(desde: String): List<EntregadaConCompromiso>

    /** Las devuelve a pendiente (la misma operación de la inversa de P1); devuelve cuántas cambió. */
    suspend fun devolverAPendiente(ids: List<String>): Int
}
