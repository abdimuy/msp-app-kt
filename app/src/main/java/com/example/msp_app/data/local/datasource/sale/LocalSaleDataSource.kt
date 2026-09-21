package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.localsale.LocalSaleClaimLeases
import com.example.msp_app.core.database.dao.localsale.LocalSaleDao
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.database.entities.LocalSaleImageEntity
import com.example.msp_app.core.database.entities.SaleClaimSnapshot
import javax.inject.Inject

class LocalSaleDataSource @Inject constructor(
    private val localSaleDao: LocalSaleDao
) {
    /**
     * Puente legacy: ViewModels no-Hilt, `PendingLocalSalesWorker` (V1, aún
     * no `@HiltWorker`) y `PendingWorkSyncFactory` siguen construyendo con
     * `context` sin cambios. Delega en la MISMA instancia que `@Inject`
     * recibe vía [com.example.msp_app.core.database.di.DatabaseModule] —
     * ambos resuelven a [AppDatabase.getInstance], una sola conexión a
     * `msp_db`. No abre un builder nuevo: eso duplicaría la ruta de
     * escritura al dinero.
     */
    constructor(context: Context) : this(AppDatabase.getInstance(context).localSaleDao())

    suspend fun insertSale(sale: LocalSaleEntity) {
        localSaleDao.insertSale(sale)
    }

    suspend fun getAllSales(): List<LocalSaleEntity> {
        return localSaleDao.getAllSales()
    }

    suspend fun getSaleById(saleId: String): LocalSaleEntity? {
        return localSaleDao.getSaleById(saleId)
    }

    suspend fun insertSaleImage(saleImage: LocalSaleImageEntity) {
        localSaleDao.insertSaleImage(saleImage)
    }

    suspend fun getImagesForSale(saleId: String): List<LocalSaleImageEntity> {
        return localSaleDao.getImagesForSale(saleId)
    }

    suspend fun deleteImagesForSale(saleId: String) {
        localSaleDao.deleteImagesForSale(saleId)
    }

    suspend fun insertSaleWithImages(sale: LocalSaleEntity, images: List<LocalSaleImageEntity>) {
        // Delega en el método `@Transaction` del DAO: la venta y sus imágenes
        // se escriben como una unidad atómica (todo o nada), evitando ventas a
        // medias si un insert falla a mitad de la secuencia.
        localSaleDao.insertSaleWithImages(sale, images)
    }

    suspend fun changeSaleStatus(saleId: String, enviado: Boolean) {
        localSaleDao.updateSaleStatus(saleId, enviado)
    }

    /**
     * TODAS las ventas sin enviar, tengan o no un candado vigente. Es el
     * número que la UI enseña como "pendientes" (`NewLocalSaleViewModel`):
     * una venta que el dueño está corrigiendo ahora mismo sigue siendo una
     * venta pendiente para él. **No es la lista del barrido** — ésa es
     * [getUploadableSales], que sí respeta el candado.
     */
    suspend fun getPendingSales(): List<LocalSaleEntity> {
        return localSaleDao.getSalesByStatus(false)
    }

    // ─────────────────────────────────────────────────────────────────────
    // La compuerta de la carrera corregir-vs-subir (Task 4 del plan
    // "Corregir una venta antes de que suba"). Los arrendamientos NO son
    // parámetros de estos métodos a propósito: salen de
    // [LocalSaleClaimLeases], la fuente única de producción, para que nadie
    // pueda llamar al DAO con un arrendamiento inventado a mano.
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Ventas que el BARRIDO puede reencolar: sin enviar y sin candado
     * vigente de ningún tipo. Reemplaza a [getPendingSales] en
     * `PendingWorkSyncFactory` — sin esto el barrido reencolaría con
     * `replace = true` en cada apertura de sesión mientras el dueño corrige,
     * reseteando el backoff y peleándose con el candado del subidor.
     */
    suspend fun getUploadableSales(now: Long): List<LocalSaleEntity> {
        return localSaleDao.getUploadableSales(
            now = now,
            editLeaseMs = LocalSaleClaimLeases.EDIT_LEASE_MS,
            uploadLeaseMs = LocalSaleClaimLeases.UPLOAD_LEASE_MS
        )
    }

    /**
     * Reclama la venta para SUBIRLA. `false` = hay un candado vigente (de
     * edición, o de otra subida en vuelo) o la venta ya no es subible: el
     * subidor debe frenarse SIN tocar la red.
     */
    suspend fun claimForUpload(saleId: String, claimId: String, now: Long): Boolean {
        return localSaleDao.claimForUpload(
            saleId = saleId,
            claimId = claimId,
            now = now,
            editLeaseMs = LocalSaleClaimLeases.EDIT_LEASE_MS,
            uploadLeaseMs = LocalSaleClaimLeases.UPLOAD_LEASE_MS
        ) == 1
    }

    /**
     * El latido: renueva el `CLAIMED_AT` del candado de subida propio
     * mientras el POST sigue en vuelo. Devuelve las filas tocadas — 0
     * significa que el candado ya no es nuestro y el latido debe DETENERSE
     * (nunca re-reclamar: eso le robaría la fila al editor).
     */
    suspend fun renewUploadClaim(saleId: String, claimId: String, now: Long): Int {
        return localSaleDao.renewUploadClaim(saleId, claimId, now)
    }

    /** Suelta el candado si sigue siendo nuestro; no-op si ya no lo es. */
    suspend fun releaseClaim(saleId: String, claimId: String) {
        localSaleDao.releaseClaim(saleId, claimId)
    }

    /** Snapshot `(CLAIM_ID, REVISION, ENVIADO)` — el que el subidor toma al reclamar. */
    suspend fun getSaleClaimSnapshot(saleId: String): SaleClaimSnapshot? {
        return localSaleDao.getSaleClaimSnapshot(saleId)
    }

    /**
     * Ancla la `REVISION` del cuerpo que va a viajar, la PRIMERA vez que se
     * emite un `POST` para esta venta. Si ya había ancla no la pisa (y
     * devuelve 0, que no es error). Contra ESE valor compara después
     * [markSentAndCloseEdit] para decidir si hubo divergencia.
     */
    suspend fun recordPostedRevisionIfAbsent(saleId: String, revision: Int): Int {
        return localSaleDao.recordPostedRevisionIfAbsent(saleId, revision)
    }

    /**
     * Borra el ancla que puso ESTE intento, cuando el fallo demuestra que
     * nunca salió un byte del teléfono. Sólo debe llamarlo quien acaba de
     * anclar en esta corrida — ver el KDoc del DAO para el falso negativo que
     * aparece si se llama sin esa condición.
     */
    suspend fun clearPostedRevisionIfMine(saleId: String, revision: Int): Int {
        return localSaleDao.clearPostedRevisionIfMine(saleId, revision)
    }

    /**
     * Marca la venta enviada y cierra cualquier candado en UNA sola
     * sentencia. Si la `REVISION` actual ya no es la del PRIMER cuerpo
     * posteado (`REVISION_POSTEADA`; [revisionAtClaim] sólo se usa como
     * respaldo si no hay ancla), marca además `CORRECCION_NO_ENVIADA = 1`:
     * la divergencia queda visible, nunca pisada en silencio — tanto si el
     * 2xx llegó directo como si la venta se reconcilió por `GET` tras un
     * `409`.
     */
    suspend fun markSentAndCloseEdit(saleId: String, revisionAtClaim: Int) {
        localSaleDao.markSentAndCloseEdit(saleId, revisionAtClaim)
    }

    suspend fun updateSale(sale: LocalSaleEntity) {
        // Usamos UPDATE real en lugar de REPLACE para evitar que el CASCADE
        // de la foreign key elimine las imágenes relacionadas
        localSaleDao.updateSaleFields(
            localSaleId = sale.LOCAL_SALE_ID,
            nombreCliente = sale.NOMBRE_CLIENTE,
            fechaVenta = sale.FECHA_VENTA,
            latitud = sale.LATITUD,
            longitud = sale.LONGITUD,
            direccion = sale.DIRECCION,
            parcialidad = sale.PARCIALIDAD,
            enganche = sale.ENGANCHE,
            telefono = sale.TELEFONO,
            frecPago = sale.FREC_PAGO,
            avalOResponsable = sale.AVAL_O_RESPONSABLE,
            nota = sale.NOTA,
            diaCobranza = sale.DIA_COBRANZA,
            precioTotal = sale.PRECIO_TOTAL,
            tiempoACortoPlazoMeses = sale.TIEMPO_A_CORTO_PLAZOMESES,
            montoACortoPlazo = sale.MONTO_A_CORTO_PLAZO,
            montoDeContado = sale.MONTO_DE_CONTADO,
            enviado = sale.ENVIADO,
            numero = sale.NUMERO,
            colonia = sale.COLONIA,
            poblacion = sale.POBLACION,
            ciudad = sale.CIUDAD,
            tipoVenta = sale.TIPO_VENTA,
            zonaClienteId = sale.ZONA_CLIENTE_ID,
            zonaCliente = sale.ZONA_CLIENTE,
            clienteId = sale.CLIENTE_ID
        )
    }

    suspend fun deleteImageById(imageId: String) {
        localSaleDao.deleteImageById(imageId)
    }

    suspend fun deleteImagesByIds(imageIds: List<String>) {
        if (imageIds.isNotEmpty()) {
            localSaleDao.deleteImagesByIds(imageIds)
        }
    }

    suspend fun updateImageServerUuid(imageId: String, serverUuid: String) {
        localSaleDao.updateImageServerUuid(imageId, serverUuid)
    }
}
