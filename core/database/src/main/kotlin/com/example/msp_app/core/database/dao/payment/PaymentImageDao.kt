package com.example.msp_app.core.database.dao.payment

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.msp_app.core.database.entities.PaymentImageEntity

/**
 * Los comprobantes de un pago (`pago_imagenes`, Task 26 / consumida por la
 * Task 22).
 *
 * ## Las dos columnas que NO son intercambiables
 *
 * [PaymentImageEntity.ID] es el UUID de **la imagen** —el que viaja como
 * `id_<n>` en el multipart y hace idempotente al reintento— y
 * [PaymentImageEntity.PAGO_ID] es el de **el pago**. Todas las consultas de
 * abajo dicen en su nombre por cuál filtran, justamente porque este plan ya
 * lleva siete defectos de la familia "un id donde iba el otro".
 *
 * ## Por qué no hay un `marcarSubidas(ids)` con `IN`
 *
 * `CLAUDE.md:52-56` documenta el caso que costó horas: un `IN (...)` sin
 * trocear lanza *"too many SQL variables"* y el `try/catch` de arriba lo vuelve
 * un error silencioso. Aquí el marcado va **de una en una**: son a lo más
 * [com.example.msp_app.feature.pagos.domain.Comprobantes.MAXIMO] filas por
 * pago, el costo es despreciable, y así no existe el borde que trocear.
 */
@Dao
interface PaymentImageDao {

    /**
     * `REPLACE` y no `IGNORE`: la PK es el UUID que acuñó el teléfono, así que
     * un reintento de la MISMA captura reescribe su propia fila en vez de
     * duplicarla. Es la misma idempotencia que el servidor aplica del otro lado.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(imagenes: List<PaymentImageEntity>)

    /** Todos los comprobantes del pago [pagoId], **en el orden de captura**. */
    @Query("SELECT * FROM pago_imagenes WHERE PAGO_ID = :pagoId ORDER BY ORDEN ASC, ID ASC")
    suspend fun getByPagoId(pagoId: String): List<PaymentImageEntity>

    /**
     * Los que todavía no subieron, del pago [pagoId]. `SUBIDA_EN IS NULL` es la
     * definición de "pendiente" y tiene índice: barrerlo es barato.
     *
     * El orden es el mismo que el de captura porque es el que decide la
     * posición `n` de `id_<n>` en el multipart.
     */
    @Query(
        "SELECT * FROM pago_imagenes WHERE PAGO_ID = :pagoId AND SUBIDA_EN IS NULL " +
            "ORDER BY ORDEN ASC, ID ASC"
    )
    suspend fun getPendientesDe(pagoId: String): List<PaymentImageEntity>

    /**
     * Marca UNA imagen como subida. Filtra por `ID` —el de la imagen— y no por
     * `PAGO_ID`: marcar por pago estamparía también las que el servidor no
     * recibió (un archivo que ya no estaba en disco, un tipo no permitido), y
     * esas tienen que quedarse pendientes para que se vean.
     */
    @Query("UPDATE pago_imagenes SET SUBIDA_EN = :subidaEn WHERE ID = :imagenId")
    suspend fun marcarSubida(imagenId: String, subidaEn: String)

    /** Las rutas de archivo que alguna fila todavía referencia. Tabla chica. */
    @Query("SELECT URI FROM pago_imagenes")
    suspend fun rutasVivas(): List<String>

    /**
     * Las filas **huérfanas y viejas**: su `PAGO_ID` ya no existe en `Payment` y
     * se crearon antes de [limite] (formato de cable de `AppTime`, comparable
     * como texto porque `ISO_INSTANT` está siempre en UTC y con ancho fijo).
     *
     * Las dos condiciones son necesarias, y la segunda es la que el KDoc de
     * `PaymentImageEntity` exige: **el re-llaveado del sync es rutina**. Cuando
     * `CobranzaSyncManager.mergePagos` borra el pago capturado para reinsertarlo
     * bajo su llave numérica, todos sus comprobantes se quedan un instante sin
     * padre. Barrer en ese momento borraría comprobantes de pagos vivos; barrer
     * por antigüedad no puede.
     */
    @Query(
        "SELECT * FROM pago_imagenes WHERE CREADA_EN < :limite " +
            "AND PAGO_ID NOT IN (SELECT ID FROM Payment)"
    )
    suspend fun huerfanasAnterioresA(limite: String): List<PaymentImageEntity>

    /** Borra UNA fila por el id de **la imagen**. */
    @Query("DELETE FROM pago_imagenes WHERE ID = :imagenId")
    suspend fun eliminar(imagenId: String)
}
