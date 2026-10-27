package com.example.msp_app.core.database.dao.enviodiagnostico

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.msp_app.core.database.entities.EnvioDiagnosticoEntity
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a `envio_diagnostico` (E-APP-063). Solo lo pueden usar el registrador
 * de diagnóstico, la pantalla de pendientes, el depurador remoto y la
 * telemetría — ver `EnvioDiagnosticoAislamientoTest`. Ninguna consulta de aquí
 * participa en decidir si un pago o una visita se manda.
 */
@Dao
abstract class EnvioDiagnosticoDao {

    @Query("SELECT * FROM envio_diagnostico WHERE TIPO = :tipo AND FILA_ID = :filaId")
    abstract suspend fun obtener(tipo: String, filaId: String): EnvioDiagnosticoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun guardar(fila: EnvioDiagnosticoEntity)

    /**
     * Filas con un fallo vigente de [tipo], la más reciente primero. Acotada por
     * construcción: solo tienen fila los envíos que fallaron alguna vez, y un
     * éxito limpia el error.
     */
    @Query(
        """
        SELECT * FROM envio_diagnostico
        WHERE TIPO = :tipo AND ULTIMO_ERROR_CODIGO IS NOT NULL
        ORDER BY ULTIMO_INTENTO_AT DESC, FILA_ID ASC
        """
    )
    abstract fun observarFallosVigentes(tipo: String): Flow<List<EnvioDiagnosticoEntity>>

    /**
     * Fallos vigentes de [tipo] agrupados por código, para la telemetría
     * (plan visitas P1, Tarea 14, H14): cuántas filas y su máximo de intentos.
     * Solo diagnóstico.
     */
    @Query(
        """
        SELECT ULTIMO_ERROR_CODIGO AS codigo, COUNT(*) AS filas, MAX(INTENTOS) AS intentosMax
        FROM envio_diagnostico
        WHERE TIPO = :tipo AND ULTIMO_ERROR_CODIGO IS NOT NULL
        GROUP BY ULTIMO_ERROR_CODIGO
        ORDER BY ULTIMO_ERROR_CODIGO ASC
        """
    )
    abstract suspend fun fallosVigentesPorCodigo(tipo: String): List<FallosPorCodigo>

    /**
     * Poda: borra toda fila cuyo último envío observado es anterior a [limite]
     * (formato de cable). Cubre también las filas de lo que se soltó sin un 2xx
     * (p. ej. un 4xx resguardado): dejan de recibir intentos y se van solas.
     * Una fila que sigue fallando sigue recibiendo intentos y no se poda.
     */
    @Query(
        "DELETE FROM envio_diagnostico WHERE ULTIMO_INTENTO_AT IS NULL OR ULTIMO_INTENTO_AT < :limite"
    )
    abstract suspend fun podarIntentosAnterioresA(limite: String): Int

    /**
     * Anota un fallo. Devuelve `true` cuando hay que mandar el evento de
     * telemetría: **a lo más una vez por (fila, código)**, con un tope de
     * [MAX_CODIGOS_REPORTADOS] códigos por fila. `REPORTADO_CODIGO` guarda la
     * lista de códigos ya reportados separados por coma (los códigos son
     * `[a-z0-9_]`), y un éxito NO la reinicia: el mismo código en la misma fila
     * no se vuelve a reportar nunca, así que la secuencia `A, B, A, B…` reporta
     * dos eventos, no uno por intento.
     */
    @Transaction
    open suspend fun registrarFallo(
        tipo: String,
        filaId: String,
        codigo: String,
        mensaje: String,
        http: Int,
        ahora: String
    ): Boolean {
        val previa = obtener(tipo, filaId)
        val reportados = previa?.REPORTADO_CODIGO.orEmpty().split(',').filter { it.isNotEmpty() }
        val reportar = codigo !in reportados && reportados.size < MAX_CODIGOS_REPORTADOS
        val nuevosReportados = if (reportar) reportados + codigo else reportados
        guardar(
            EnvioDiagnosticoEntity(
                TIPO = tipo,
                FILA_ID = filaId,
                ULTIMO_ERROR_CODIGO = codigo,
                ULTIMO_ERROR_MENSAJE = mensaje,
                ULTIMO_ERROR_HTTP = http,
                INTENTOS = (previa?.INTENTOS ?: 0) + 1,
                ULTIMO_INTENTO_AT = ahora,
                ULTIMO_EXITO_AT = previa?.ULTIMO_EXITO_AT,
                REPORTADO_CODIGO = nuevosReportados.joinToString(",").ifEmpty { null }
            )
        )
        return reportar
    }

    /**
     * Anota un éxito: limpia el error vigente (conserva los códigos ya
     * reportados, ver [registrarFallo]). Si la fila nunca falló no crea
     * nada (sin historia de fallo no hay qué diagnosticar y la tabla no crece
     * con cada pago entregado).
     */
    @Transaction
    open suspend fun registrarExito(tipo: String, filaId: String, ahora: String) {
        val previa = obtener(tipo, filaId) ?: return
        guardar(
            previa.copy(
                ULTIMO_ERROR_CODIGO = null,
                ULTIMO_ERROR_MENSAJE = null,
                ULTIMO_ERROR_HTTP = null,
                INTENTOS = previa.INTENTOS + 1,
                ULTIMO_INTENTO_AT = ahora,
                ULTIMO_EXITO_AT = ahora
            )
        )
    }

    companion object {
        /** Tope de eventos de telemetría por fila. */
        const val MAX_CODIGOS_REPORTADOS = 8
    }
}
