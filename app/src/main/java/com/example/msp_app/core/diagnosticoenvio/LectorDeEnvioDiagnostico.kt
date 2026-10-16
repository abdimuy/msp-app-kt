package com.example.msp_app.core.diagnosticoenvio

import com.example.msp_app.core.database.dao.enviodiagnostico.EnvioDiagnosticoDao
import com.example.msp_app.core.database.entities.EnvioDiagnosticoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Lectura de `envio_diagnostico` para la pantalla de pendientes (E-APP-063).
 * **Solo pinta**: nada de aquí vuelve a la decisión de envío.
 */
class LectorDeEnvioDiagnostico(private val dao: EnvioDiagnosticoDao) {

    /**
     * El motivo del fallo más reciente entre [pendientes], como texto para la
     * pantalla, o `null` si ninguno tiene un fallo anotado.
     */
    fun motivoMasReciente(tipo: String, pendientes: Set<String>): Flow<String?> =
        dao.observarFallosVigentes(tipo)
            .map { filas -> textoDelMotivo(filas.firstOrNull { it.FILA_ID in pendientes }) }
            .catch { emit(null) }

    companion object {
        const val TIPO_PAGO = EnvioDiagnosticoEntity.TIPO_PAGO
        const val TIPO_VISITA = EnvioDiagnosticoEntity.TIPO_VISITA

        /** `Último motivo: Sin conexión (network_error)`. */
        fun textoDelMotivo(fila: EnvioDiagnosticoEntity?): String? {
            val codigo = fila?.ULTIMO_ERROR_CODIGO ?: return null
            val mensaje = fila.ULTIMO_ERROR_MENSAJE?.takeIf { it.isNotBlank() } ?: "Error"
            return "Último motivo: $mensaje ($codigo)"
        }
    }
}
