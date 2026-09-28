package com.example.msp_app.core.sync.cobranza

import androidx.room.withTransaction
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.conSaldoAjustadoPorPagosEnVuelo
import com.example.msp_app.data.api.services.cobranza.toEntity
import kotlinx.coroutines.sync.withLock

/** Qué hizo un refresco del saldo de una venta. */
enum class ResultadoDelRefresco {
    /** El servidor dijo su saldo y la venta quedó en él, menos lo que sigue en vuelo. */
    FIJADO,

    /**
     * La respuesta no trajo esa venta (o la trajo cancelada). **No se toca nada**:
     * una ausencia no es un saldo cero — `by-ids` también deja fuera una venta de
     * otra zona. La cancelada la resuelve el sync, que es quien borra.
     */
    SIN_DATO_DEL_SERVIDOR,

    /**
     * Sin dato del servidor y la venta estaba en NEGATIVO: se subió a cero. Sólo
     * cuando quien llama lo pide (la reparación única); nunca sube un saldo que
     * ya era `>= 0`.
     */
    NIVELADO_A_CERO
}

/**
 * **Re-lee del servidor el saldo de UNA venta y lo deja en Room, bajo el mismo
 * mutex de escritura que `mergeVentas`.**
 *
 * Existe para lo que deja una condonación rechazada: el teléfono la descontó y
 * el servidor no la aplicó, así que el único saldo correcto es el que el
 * servidor tiene hoy. Nunca se suma el importe de vuelta — la compuerta
 * posterior del 2026-09-27 midió que eso deja el saldo por encima del real
 * (1000 contra 500).
 *
 * ## Por qué TODO va dentro de [CobranzaWriteMutex]
 *
 * Es una escritura ABSOLUTA desde una lectura del servidor, igual que
 * `CobranzaSyncManager.mergeVentas`. La segunda compuerta posterior midió las
 * dos carreras que abre hacerlo fuera del mutex:
 *
 * - **A**: `by-ids` lee 500, un tick del sync (que sí toma el mutex) escribe
 *   200 tras el abono de otro cobrador, y esta escritura pisa con 500.
 * - **B**: un abono en vuelo de la misma venta se aplica y anota su
 *   `DOCTO_CC_ID` entre la lectura y la escritura; la suma de lo no reconocido
 *   ya no lo incluye y queda 500 en vez de 300. Esto NO lo cierra el mutex
 *   (hacer que la anotación lo esperara abría la carrera contraria: un merge
 *   que resta un abono ya aplicado, 100 contra 300, fósil). Lo cierra que la
 *   subida, al anotar el documento, encole OTRO refresco de la venta, que lee
 *   el servidor ya con el abono y una suma que ya no lo incluye.
 *
 * La lectura de red va dentro del mutex a propósito — `syncNow` hace lo mismo —
 * para que ningún escritor se meta entre la foto y su escritura. La suma de lo
 * no reconocido se re-lee DENTRO de la transacción.
 *
 * ## Una sola fórmula
 *
 * El saldo se arma con `VentaDto.toEntity().conSaldoAjustadoPorPagosEnVuelo`, la
 * MISMA función que usa `mergeVentas` (`CobranzaSyncManager.kt`, rama del
 * upsert): este refresco no agrega una segunda definición de "saldo mostrado".
 *
 * **Excepción preexistente, no cerrada aquí:**
 * `CobranzaReconciler.reconcileSaldosViaIds` inserta las ventas que le faltan
 * al teléfono con `toEntity()` a secas, sin restar lo que sigue en vuelo. Es
 * un camino de escritura de saldo que NO aplica la fórmula; queda como deuda.
 *
 * El canal es `GET /v2/cobranza/sync/saldos/by-ids`, el que ya usa
 * `CobranzaReconciler.reconcileSaldosViaIds`. La zona es la columna por la que
 * el servidor filtra (`WHERE s.ZONA_CLIENTE_ID = ?`, `ventas_repo.go` ByIDs).
 *
 * Cualquier fallo de red o de Room se PROPAGA: quien llama
 * ([com.example.msp_app.workers.RefrescarSaldoDeVentaWorker]) reintenta.
 */
class RefrescoDelSaldoDeLaVenta(
    private val api: V2CobranzaApi,
    private val db: AppDatabase,
    private val paymentDao: PaymentDao = db.paymentDao(),
    private val cobranzaWriteMutex: CobranzaWriteMutex = CobranzaWriteMutexProvider.get()
) {

    /**
     * @param nivelarNegativoSinDato si el servidor no trae la venta y su saldo
     *   local es negativo, subirlo a cero (bajo el mismo mutex y en transacción).
     *   Lo pide la reparación única; el refresco tras una condonación o un pago
     *   no lo pide.
     */
    suspend fun refrescar(
        zona: Int,
        cargo: Int,
        nivelarNegativoSinDato: Boolean = false
    ): ResultadoDelRefresco = cobranzaWriteMutex.mutex.withLock {
        val venta = api.saldosByIds(zonaId = zona, ids = cargo.toString())
            .firstOrNull { it.docto_cc_id == cargo && !it.cargo_cancelado }
            ?: return@withLock sinDato(cargo, nivelarNegativoSinDato)
        db.withTransaction {
            val enVuelo = paymentDao.sumImporteNoReconocidoPorElServidor(cargo)
            paymentDao.fijarSaldoDeLaVenta(
                cargo = cargo,
                saldo = venta.toEntity().conSaldoAjustadoPorPagosEnVuelo(enVuelo).SALDO_REST
            )
        }
        ResultadoDelRefresco.FIJADO
    }

    private suspend fun sinDato(cargo: Int, nivelar: Boolean): ResultadoDelRefresco {
        if (!nivelar) return ResultadoDelRefresco.SIN_DATO_DEL_SERVIDOR
        val niveladas = db.withTransaction { db.saleDao().nivelarSaldoNegativoACero(cargo) }
        return if (niveladas > 0) {
            ResultadoDelRefresco.NIVELADO_A_CERO
        } else {
            ResultadoDelRefresco.SIN_DATO_DEL_SERVIDOR
        }
    }
}
