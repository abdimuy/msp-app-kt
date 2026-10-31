package com.example.msp_app.core.ubicacion

import com.example.msp_app.core.common.location.captura.FixDeUbicacion

/**
 * Lo que se sabe de la ubicacion en el instante de guardar (E-APP-146).
 *
 * [elapsedNanos] es el instante de guardado en el reloj monotono, o `null` si no se pudo leer (no
 * habra plazo ni espera). [antes] es el fix que la regla acepto como ANTES, o `null`.
 */
data class MomentoDeGuardado(
    val elapsedNanos: Long?,
    val antes: FixDeUbicacion?,
    /**
     * Hora de PARED del guardado, igual para todas las capturas de un lote (las N visitas de una
     * captura): de aqui sale el plazo de envio de cada una, asi el tope de 30 s corre desde el
     * guardado mas antiguo del lote (K6). `null` = que cada anotacion use la hora de su escritura.
     */
    val guardadoEpochMs: Long? = null
) {
    companion object {
        val SinUbicacion = MomentoDeGuardado(elapsedNanos = null, antes = null)
    }
}

/**
 * La ubicacion de una captura (abono, condonacion o visita) vista desde quien la guarda
 * (E-APP-146; decision del dueno 2026-10-30: el envio se RETIENE hasta tener ubicacion o 30 s).
 * Guardar NUNCA espera al GPS.
 *
 * Contrato con quien guarda (el camino del dinero):
 * 1. [alGuardar] ANTES de la transaccion. No suspende y nunca lanza.
 * 2. Con la ubicacion ANTES en la mano, la captura nace con ella. [anotarEnLaTransaccion] se
 *    llama DENTRO de la misma transaccion, escribe la fila de ubicacion (si la hubo) y el plazo de
 *    envio (si no la hubo). Nunca lanza: una falla aqui no puede deshacer ni impedir el dinero.
 * 3. [despuesDeGuardar] DESPUES del commit y solo si el dinero quedo escrito. No suspende y no
 *    debe lanzar. Si no hubo ubicacion, deja la captura esperando el primer fix valido.
 */
interface UbicacionAlGuardar {
    fun alGuardar(): MomentoDeGuardado

    suspend fun anotarEnLaTransaccion(id: String, tipo: String, momento: MomentoDeGuardado)

    fun despuesDeGuardar(id: String, tipo: String, momento: MomentoDeGuardado)

    /** Sin ubicacion: la captura nace en 0,0, sin plazo (el envio no se retiene) y sin espera. */
    object Ninguna : UbicacionAlGuardar {
        override fun alGuardar(): MomentoDeGuardado = MomentoDeGuardado.SinUbicacion

        override suspend fun anotarEnLaTransaccion(
            id: String,
            tipo: String,
            momento: MomentoDeGuardado
        ) = Unit

        override fun despuesDeGuardar(id: String, tipo: String, momento: MomentoDeGuardado) = Unit
    }
}
