package com.example.msp_app.core.speech.domain.port

import com.example.msp_app.core.speech.domain.EstadoDelModelo
import kotlinx.coroutines.flow.Flow

/**
 * **El modelo de alta fidelidad: dónde está y cómo se pide.**
 *
 * Puerto justificado por el contrato de capas (Ruling BF, caso 3): quien lo
 * consume es la pantalla de descarga, que vive en `ui/` y tiene **prohibido**
 * importar el adaptador donde viven WorkManager, OkHttp y el `File`.
 *
 * Separado de [DictadoPort] a propósito, aunque el modelo sea del dictado: son
 * dos ciclos de vida distintos. El dictado dura ocho segundos con el micrófono
 * abierto; la descarga dura media hora, sobrevive a que la app se cierre y la
 * gobierna una restricción de red. Un solo puerto con las dos cosas obligaría a
 * la pantalla de la visita a conocer la descarga, que es justo lo que no debe
 * pasar: **el cobrador sin modelo dicta igual y no se entera de que existe.**
 */
interface ModeloDeDictadoPort {

    /** En qué punto está. Una sola fuente, y la pantalla no infiere nada aparte. */
    fun estado(): Flow<EstadoDelModelo>

    /**
     * Encola la descarga **restringida a wifi**. No baja nada por sí misma: deja
     * el trabajo encolado y el sistema lo corre cuando la red deje de ser
     * medida. Por eso no devuelve `Result` — encolar no falla, y lo que puede
     * fallar (la descarga) se ve en [estado].
     */
    suspend fun pedirLaDescarga()

    /**
     * Cancela y **borra lo bajado**. Es la única forma de recuperar los megas, y
     * quien la llama es el cobrador, nunca la app sola.
     */
    suspend fun cancelarYBorrar()
}
