package com.example.msp_app.core.mapas.domain.port

import com.example.msp_app.core.mapas.domain.EstadoDelExtracto
import kotlinx.coroutines.flow.Flow

/**
 * **El extracto del mapa: dónde está y cómo se pide.**
 *
 * Puerto justificado por el contrato de capas (Ruling BF, caso 3): quienes lo
 * consumen son el suelo del mapa y la pantalla de descarga, que viven en `ui/`
 * y tienen **prohibido** importar el adaptador donde viven WorkManager, OkHttp
 * y el `File`.
 */
interface ExtractoDeMapaPort {

    /** En qué punto está. Una sola fuente, y la UI no infiere nada aparte. */
    fun estado(): Flow<EstadoDelExtracto>

    /**
     * Encola la descarga **restringida a wifi**. No baja nada por sí misma: deja
     * el trabajo encolado y el sistema lo corre cuando la red deje de ser
     * medida. Por eso no devuelve `Result` — encolar no falla, y lo que puede
     * fallar (la descarga) se ve en [estado].
     *
     * Sin origen publicado no hace nada: no hay a dónde ir.
     */
    suspend fun pedirLaDescarga()

    /**
     * Cancela y **borra lo bajado**. Es la única forma de recuperar los megas, y
     * quien la llama es el cobrador, nunca la app sola.
     */
    suspend fun cancelarYBorrar()
}
