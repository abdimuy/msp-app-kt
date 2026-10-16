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

    /** Poda: filas cuyo último éxito es anterior a [limite] (formato de cable). */
    @Query(
        """
        DELETE FROM envio_diagnostico
        WHERE ULTIMO_EXITO_AT IS NOT NULL
          AND ULTIMO_ERROR_CODIGO IS NULL
          AND ULTIMO_EXITO_AT < :limite
        """
    )
    abstract suspend fun podarExitosAnterioresA(limite: String)

    /**
     * Anota un fallo. Devuelve `true` cuando [codigo] es distinto del último ya
     * reportado a la telemetría (y lo deja anotado como reportado), para que el
     * llamador mande a lo más un evento por fila y por error distinto.
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
        val reportar = previa?.REPORTADO_CODIGO != codigo
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
                REPORTADO_CODIGO = codigo
            )
        )
        return reportar
    }

    /**
     * Anota un éxito: limpia el error vigente. Si la fila nunca falló no crea
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
                ULTIMO_EXITO_AT = ahora,
                REPORTADO_CODIGO = null
            )
        )
    }
}
