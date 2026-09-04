package com.example.msp_app.core.database.dao.visit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.core.database.entities.VisitEntity

@Dao
interface VisitDao {
    @Query(
        """
        SELECT 
            ID,
            CLIENTE_ID,
            COBRADOR,
            COBRADOR_ID,
            FECHA,
            FORMA_COBRO_ID,
            LAT,
            LNG,
            NOTA,
            TIPO_VISITA,
            ZONA_CLIENTE_ID,
            IMPTE_DOCTO_CC_ID,
            GUARDADO_EN_MICROSIP,
            PROMESA_VENTA_ID,
            PROMESA_FECHA,
            PROMESA_MONTO_CENTAVOS,
            CITA_FECHA,
            CITA_HORA
        FROM Visit
        WHERE ID = :id
        """
    )
    suspend fun getVisitById(id: String): VisitEntity

    /**
     * La MISMA fila que [getVisitById], pero **anulable**.
     *
     * [getVisitById] se declara no-nula y por eso revienta con
     * `NullPointerException` cuando el id no existe — los tests del repo ya la
     * envuelven en `runCatching { }.getOrNull()` para tolerarlo. El ticket de
     * visita (Task 20) entra por una ruta que puede apuntar a una visita ya
     * podada, y "no está" es un estado normal de esa pantalla, no una
     * excepción. Se agrega una consulta nueva en vez de cambiar la firma de la
     * existente: hay una docena de llamadores que dependen del tipo no-nulo.
     *
     * No toca el schema: es una `@Query` de lectura sobre las mismas columnas.
     */
    @Query(
        """
        SELECT 
            ID,
            CLIENTE_ID,
            COBRADOR,
            COBRADOR_ID,
            FECHA,
            FORMA_COBRO_ID,
            LAT,
            LNG,
            NOTA,
            TIPO_VISITA,
            ZONA_CLIENTE_ID,
            IMPTE_DOCTO_CC_ID,
            GUARDADO_EN_MICROSIP,
            PROMESA_VENTA_ID,
            PROMESA_FECHA,
            PROMESA_MONTO_CENTAVOS,
            CITA_FECHA,
            CITA_HORA
        FROM Visit
        WHERE ID = :id
        """
    )
    suspend fun findVisitById(id: String): VisitEntity?

    @Insert(
        onConflict = OnConflictStrategy.REPLACE
    )
    suspend fun insertVisit(visit: VisitEntity)

    @Query(
        """
        SELECT 
            ID,
            CLIENTE_ID,
            COBRADOR,
            COBRADOR_ID,
            FECHA,
            FORMA_COBRO_ID,
            LAT,
            LNG,
            NOTA,
            TIPO_VISITA,
            ZONA_CLIENTE_ID,
            IMPTE_DOCTO_CC_ID,
            GUARDADO_EN_MICROSIP,
            PROMESA_VENTA_ID,
            PROMESA_FECHA,
            PROMESA_MONTO_CENTAVOS,
            CITA_FECHA,
            CITA_HORA
        FROM Visit
        WHERE GUARDADO_EN_MICROSIP = 0
        """
    )
    suspend fun getPendingVisits(): List<VisitEntity>

    @Query(
        """
        SELECT 
            ID,
            CLIENTE_ID,
            COBRADOR,
            COBRADOR_ID,
            FECHA,
            FORMA_COBRO_ID,
            LAT,
            LNG,
            NOTA,
            TIPO_VISITA,
            ZONA_CLIENTE_ID,
            IMPTE_DOCTO_CC_ID,
            GUARDADO_EN_MICROSIP,
            PROMESA_VENTA_ID,
            PROMESA_FECHA,
            PROMESA_MONTO_CENTAVOS,
            CITA_FECHA,
            CITA_HORA
        FROM Visit
        WHERE FECHA >= :start AND FECHA < :end
        ORDER BY FECHA DESC
        """
    )
    suspend fun getVisitsByDate(start: String, end: String): List<VisitEntity>

    /**
     * Todas las visitas de un cliente, de la más reciente a la más vieja —
     * la bitácora "últimos contactos" del detalle de cliente
     * (`:feature:pagos`, Task 16).
     *
     * Solo lectura: NO toca el schema (Room es inmutable, hay datos en
     * producción). Corre sobre el índice `CLIENTE_ID` que la tabla ya declara,
     * así que no hace falta índice nuevo — que sería un cambio de schema.
     *
     * Barre TODO el histórico del cliente, no una ventana: la bitácora es
     * precisamente lo que pasó antes del periodo en curso, y acotarla por fecha
     * la dejaría vacía justo en el cliente al que nadie ha visitado esta semana,
     * que es el que más falta hace conocer.
     */
    @Query(
        """
        SELECT 
            ID,
            CLIENTE_ID,
            COBRADOR,
            COBRADOR_ID,
            FECHA,
            FORMA_COBRO_ID,
            LAT,
            LNG,
            NOTA,
            TIPO_VISITA,
            ZONA_CLIENTE_ID,
            IMPTE_DOCTO_CC_ID,
            GUARDADO_EN_MICROSIP,
            PROMESA_VENTA_ID,
            PROMESA_FECHA,
            PROMESA_MONTO_CENTAVOS,
            CITA_FECHA,
            CITA_HORA
        FROM Visit
        WHERE CLIENTE_ID = :clienteId
        ORDER BY FECHA DESC
        """
    )
    suspend fun getVisitsByClienteId(clienteId: Int): List<VisitEntity>

    @Query("UPDATE Visit SET GUARDADO_EN_MICROSIP = :newState WHERE id = :id")
    suspend fun updateState(id: String, newState: Int)

    @Query("UPDATE Visit SET LAT = :lat, LNG = :lng WHERE id = :id")
    suspend fun updateLocation(id: String, lat: Double, lng: Double)

    /**
     * Flips `GUARDADO_EN_MICROSIP` to 1 for [ids] — and only for [ids].
     *
     * The one caller is the visitas reconciler
     * (`ReconcileVisitsUseCase` in `:core:common`, wired through
     * `RoomPendingVisitsStore`), which passes exclusively ids that
     * `GET /v2/visitas/by-ids` confirmed the server already holds. It never
     * passes an id on the strength of an HTTP status.
     *
     * `WHERE ID IN (:ids)` binds one SQL parameter per id, so the caller must
     * keep the batch bounded: every Android below API 31 ships
     * `SQLITE_MAX_VARIABLE_NUMBER = 999`, and this repo's `minSdk` is 24. An
     * unbounded `IN (...)` is the exact failure `CLAUDE.md` records — "too many
     * SQL variables" swallowed by an outer catch and turned into a silent error
     * on every tick. `RoomPendingVisitsStore` chunks below that ceiling.
     *
     * @return how many rows actually changed, so a caller can tell "marked" from
     *   "the id was not there".
     */
    @Query("UPDATE Visit SET GUARDADO_EN_MICROSIP = 1 WHERE ID IN (:ids)")
    suspend fun markSyncedByIds(ids: List<String>): Int

    @Query("DELETE FROM Visit")
    suspend fun deleteAllVisits()

    /**
     * Prunes only visitas already confirmed by the server (GUARDADO_EN_MICROSIP = 1)
     * **and that no longer carry a local-only commitment**.
     * Unlike [deleteAllVisits], this never touches a visita still pending upload —
     * see [com.example.msp_app.features.sales.viewmodels.SalesViewModel.syncSales].
     *
     * ## Por qué la segunda condición (Ruling V del plan `pagos-y-visitas`)
     *
     * `PROMESA_FECHA`, `PROMESA_MONTO_CENTAVOS`, `CITA_FECHA` y `CITA_HORA`
     * **no existen en el servidor**: el contrato de `POST /v2/visitas` no las
     * lleva y el plan decidió no expandirlo. O sea que son datos que solo viven
     * en este teléfono. Con la poda anterior —`GUARDADO_EN_MICROSIP = 1` a
     * secas— una promesa duraba hasta el siguiente `syncSales` y desaparecía
     * para siempre, sin que nadie pudiera recuperarla de ningún lado. Eso vacía
     * el propósito entero de haberla estructurado: la promesa existe **para
     * poder consultarse**, es el insumo del recomendador y la única forma de
     * medir si se cumplió.
     *
     * La condición se lee: *"borra la visita subida cuyo compromiso más lejano
     * ya quedó antes de [conservarDesde]"*. `MAX(...)` es el escalar de dos
     * argumentos de SQLite, y los `COALESCE(..., '')` hacen que una visita SIN
     * compromiso (las dos columnas en `NULL`) dé `''`, que es menor que
     * cualquier fecha `yyyy-MM-dd` — o sea que se poda igual que antes. **El
     * comportamiento para una visita sin promesa ni cita no cambia.**
     *
     * No es "nunca borrar": es una ventana de retención. Ver
     * `VisitsLocalDataSource.deleteUploadedVisits`, que la calcula desde el
     * reloj de negocio y documenta cuánto dura.
     *
     * @param conservarDesde fecha de corte en formato de cable (`yyyy-MM-dd`).
     *   Una visita cuyo compromiso caiga en ese día o después SOBREVIVE.
     */
    @Query(
        """
        DELETE FROM Visit
        WHERE GUARDADO_EN_MICROSIP = 1
          AND MAX(COALESCE(PROMESA_FECHA, ''), COALESCE(CITA_FECHA, '')) < :conservarDesde
        """
    )
    suspend fun deleteUploadedVisits(conservarDesde: String)
}
