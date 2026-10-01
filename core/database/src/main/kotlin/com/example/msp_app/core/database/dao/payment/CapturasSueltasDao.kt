package com.example.msp_app.core.database.dao.payment

import androidx.room.Dao
import androidx.room.Query
import com.example.msp_app.core.database.entities.DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR
import com.example.msp_app.core.database.entities.PaymentEntity

/**
 * Las consultas de la resolución de **capturas sueltas** (E-APP-048), aparte de
 * [PaymentDao] para no seguir engordándolo. Misma tabla `Payment`, sin cambio de
 * schema.
 */
@Dao
interface CapturasSueltasDao {

    /**
     * **Capturas sueltas** (E-APP-048): capturas del teléfono (`ID LIKE '%-%'`)
     * soltadas (`GUARDADO_EN_MICROSIP = 1`) sin documento (`DOCTO_CC_ID = 0`) que
     * ninguna fila del servidor nombra por `PAGO_RECIBIDO_ID`, de **cualquier**
     * forma de cobro. Es exactamente el conjunto que
     * [PaymentDao.sumImporteNoReconocidoPorElServidor] resta del saldo, menos los
     * pendientes de subir (`= 0`), que sí están en vuelo.
     *
     * La flota medida el 2026-09-30 tenía 2,206 (E-APP-048): 2,188 eran abonos
     * del 8 al 13 de agosto que el API Node aplicó sin dejar
     * `MSP_PAGOS_RECIBIDOS.IMPTE_DOCTO_CC_ID`, así que el sync nunca las nombra
     * y se restaban otra vez del saldo del servidor. Las lee
     * `ResolucionDeCapturasSueltas`, que le pregunta al servidor por cada una.
     */
    @Query(
        """
        SELECT
            ID, COBRADOR, DOCTO_CC_ACR_ID, DOCTO_CC_ID, FECHA_HORA_PAGO,
            GUARDADO_EN_MICROSIP, IMPORTE, LAT, LNG, CLIENTE_ID, COBRADOR_ID,
            FORMA_COBRO_ID, ZONA_CLIENTE_ID, NOMBRE_CLIENTE, PAGO_RECIBIDO_ID
        FROM Payment
        WHERE GUARDADO_EN_MICROSIP = 1
          AND DOCTO_CC_ID = 0
          AND ID LIKE '%-%'
          AND ID NOT IN (
              SELECT PAGO_RECIBIDO_ID FROM Payment
              WHERE PAGO_RECIBIDO_ID IS NOT NULL AND PAGO_RECIBIDO_ID <> ID
          )
        """
    )
    suspend fun capturasSueltas(): List<PaymentEntity>

    /**
     * ¿Ya bajó por el sync el abono del servidor con el documento [documento],
     * en el mismo [cargo] y por el mismo [importe]?
     *
     * Es la **segunda prueba** que exige reconocer una captura suelta. El
     * `GET /v2/cobranza/pagos/{id}` de una captura de la era Node trae el
     * documento pero el cargo y el importe en cero, y contesta 200 aunque el
     * documento ya no exista en `DOCTOS_CC` (medido en dev el 2026-10-01). La
     * fila numérica, en cambio, sólo la entrega el sync de un abono vivo de
     * `MSP_PAGOS_VENTAS`: si existe con ese documento, cargo e importe, el abono
     * está aplicado así en Microsip.
     *
     * La tolerancia de medio centavo absorbe la conversión a `Double`; el
     * servidor guarda `NUMERIC(14,2)`.
     */
    @Query(
        """
        SELECT COUNT(*) FROM Payment
        WHERE ID NOT LIKE '%-%'
          AND DOCTO_CC_ID = :documento
          AND DOCTO_CC_ACR_ID = :cargo
          AND ABS(IMPORTE - :importe) < 0.005
        """
    )
    suspend fun gemelosDelServidor(documento: Int, cargo: Int, importe: Double): Int

    /**
     * Anota el documento de una captura suelta que el servidor reconoce. Una sola
     * vez: sólo si sigue siendo captura del teléfono, soltada y sin documento.
     * Una segunda corrida toca cero filas. Devuelve cuántas filas tocó.
     */
    @Query(
        """
        UPDATE Payment
        SET DOCTO_CC_ID = :documento
        WHERE ID = :id
          AND ID LIKE '%-%'
          AND DOCTO_CC_ID = 0
          AND GUARDADO_EN_MICROSIP = 1
          AND :documento > 0
        """
    )
    suspend fun anotarDocumentoDeCapturaSuelta(id: String, documento: Int): Int

    /**
     * Marca una captura suelta que el servidor **no tiene** (404 del propio API
     * con `pago_no_encontrado`) como [DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR]: deja
     * de restarse del saldo (decisión del dueño del 2026-09-29: el servidor es la
     * verdad y un saldo menor esconde deuda) y la fila se queda —nunca se borra
     * una captura que el servidor no nombró—. Mismos cerrojos que
     * [anotarDocumentoDeCapturaSuelta].
     */
    @Query(
        """
        UPDATE Payment
        SET DOCTO_CC_ID = $DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR
        WHERE ID = :id
          AND ID LIKE '%-%'
          AND DOCTO_CC_ID = 0
          AND GUARDADO_EN_MICROSIP = 1
        """
    )
    suspend fun marcarCapturaSueltaNoAplicada(id: String): Int
}
