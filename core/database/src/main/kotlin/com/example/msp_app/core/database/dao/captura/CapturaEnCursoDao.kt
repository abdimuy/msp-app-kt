package com.example.msp_app.core.database.dao.captura

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.core.database.entities.CapturaEnCursoEntity
import kotlinx.coroutines.flow.Flow

/**
 * `capturas_en_curso` (E-APP-084). Toda transición es condicional al estado
 * previo, así que un reintento tardío nunca hace retroceder un marcador.
 *
 * **Ninguna consulta borra una fila `CAPTURADA` ni una con `DISCREPANCIA`**
 * (E-APP-090, sin botón descartar): el único `DELETE` es el barrido de las
 * `CERRADA` viejas.
 */
@Dao
interface CapturaEnCursoDao {

    /** Escritura 1. `IGNORE`: el mismo UUID capturado dos veces es la misma captura. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun capturar(marcador: CapturaEnCursoEntity): Long

    /**
     * Re-captura de lo que todavía NO se escribió (el cobrador corrigió el monto
     * tras un fallo, mismo UUID): se reemplaza el resumen. Una ESCRITA o CERRADA
     * nunca cambia.
     */
    @Query(
        "UPDATE capturas_en_curso SET RESUMEN = :resumen, ACTUALIZADA_EN = :en " +
            "WHERE ID = :id AND ESTADO = 'CAPTURADA'"
    )
    suspend fun actualizarCapturada(id: String, resumen: String, en: String): Int

    @Query("SELECT * FROM capturas_en_curso WHERE ID = :id")
    suspend fun obtener(id: String): CapturaEnCursoEntity?

    /** CAPTURADA → ESCRITA. Va DENTRO de la transacción del dinero. */
    @Query(
        "UPDATE capturas_en_curso SET ESTADO = 'ESCRITA', ACTUALIZADA_EN = :en " +
            "WHERE ID = :id AND ESTADO = 'CAPTURADA'"
    )
    suspend fun marcarEscrita(id: String, en: String): Int

    /** ESCRITA → CERRADA. Nunca cierra una CAPTURADA. */
    @Query(
        "UPDATE capturas_en_curso SET ESTADO = 'CERRADA', ACTUALIZADA_EN = :en " +
            "WHERE ID = :id AND ESTADO = 'ESCRITA'"
    )
    suspend fun marcarCerrada(id: String, en: String): Int

    @Query(
        "UPDATE capturas_en_curso SET DISCREPANCIA = :motivo, DISCREPANCIA_EN = :en, " +
            "ACTUALIZADA_EN = :en WHERE ID = :id"
    )
    suspend fun marcarDiscrepancia(id: String, motivo: String, en: String): Int

    /** La captura abierta (CAPTURADA o ESCRITA) más reciente de esa llave y ese usuario. */
    @Query(
        "SELECT * FROM capturas_en_curso WHERE TIPO = :tipo AND LLAVE = :llave " +
            "AND USUARIO = :usuario AND ESTADO IN ('CAPTURADA', 'ESCRITA') " +
            "ORDER BY CREADA_EN DESC, ID ASC LIMIT 1"
    )
    suspend fun abiertaDe(tipo: String, llave: String, usuario: String): CapturaEnCursoEntity?

    /** Las abiertas de un tipo y usuario (fotos por reponer, barrido de comprobantes). */
    @Query(
        "SELECT * FROM capturas_en_curso WHERE TIPO = :tipo AND USUARIO = :usuario " +
            "AND ESTADO IN ('CAPTURADA', 'ESCRITA') ORDER BY CREADA_EN ASC, ID ASC"
    )
    suspend fun abiertasDe(tipo: String, usuario: String): List<CapturaEnCursoEntity>

    /** Todas las abiertas de cualquier usuario: el barrido de archivos no debe tocar ninguna. */
    @Query("SELECT * FROM capturas_en_curso WHERE ESTADO IN ('CAPTURADA', 'ESCRITA')")
    suspend fun todasLasAbiertas(): List<CapturaEnCursoEntity>

    /**
     * "N capturas sin terminar": abonos CAPTURADA (los únicos con pantalla que
     * los reanuda, "Guardar") o cualquier captura con discrepancia, del usuario.
     * Una visita o garantía CAPTURADA no cuenta todavía: no hay pantalla que la
     * reanude y el aviso no se apagaría nunca.
     *
     * E-APP-117: la discrepancia cuenta **en cualquier estado**. En el flujo
     * normal la pantalla ya cerró el marcador (CERRADA) cuando el worker recibe
     * el 409/422 y anota la discrepancia sobre ese mismo marcador; filtrar
     * `ESTADO <> 'CERRADA'` dejaba "Un envío no coincide con la oficina" con
     * aviso 0 (medición M2 de la compuerta DESPUÉS, 2026-10-26).
     */
    @Query(
        "SELECT COUNT(*) FROM capturas_en_curso WHERE USUARIO = :usuario " +
            "AND ((ESTADO = 'CAPTURADA' AND TIPO = 'ABONO') OR DISCREPANCIA IS NOT NULL)"
    )
    fun observarSinTerminar(usuario: String): Flow<Int>

    /** La venta del abono sin guardar más viejo del usuario: el enlace del aviso. */
    @Query(
        "SELECT LLAVE FROM capturas_en_curso WHERE USUARIO = :usuario AND TIPO = 'ABONO' " +
            "AND ESTADO = 'CAPTURADA' ORDER BY CREADA_EN ASC, ID ASC LIMIT 1"
    )
    fun observarVentaDelPrimerAbonoSinGuardar(usuario: String): Flow<String?>

    /** El único borrado: CERRADA, sin discrepancia, anterior a [antesDe]. */
    @Query(
        "DELETE FROM capturas_en_curso WHERE ESTADO = 'CERRADA' AND DISCREPANCIA IS NULL " +
            "AND ACTUALIZADA_EN < :antesDe"
    )
    suspend fun barrerCerradas(antesDe: String): Int

    /** ¿Ya hay un pago con ese UUID, como PK o como gemelo colapsado? Sólo lectura. */
    @Query("SELECT COUNT(*) FROM Payment WHERE ID = :uuid OR PAGO_RECIBIDO_ID = :uuid")
    suspend fun pagosConUuid(uuid: String): Int

    /** Cuáles de estos comprobantes ya están en `pago_imagenes`. Sólo lectura. */
    @Query("SELECT ID FROM pago_imagenes WHERE ID IN (:ids)")
    suspend fun comprobantesDePagoPresentes(ids: List<String>): List<String>
}
