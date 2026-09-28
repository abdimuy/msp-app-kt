package com.example.msp_app.data.pagos

import androidx.room.withTransaction
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.entities.FORMA_COBRO_CONDONACION
import com.example.msp_app.core.database.entities.PaymentEntity
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Qué pasó con una condonación que se intentó escribir. */
enum class ResultadoDeLaCondonacion {
    /** Quedó escrita: la fila del pago y el descuento del saldo, las dos. */
    GUARDADA,

    /** El monto no es positivo (o no es un número). No se escribió nada. */
    MONTO_INVALIDO,

    /** El monto excede el saldo vigente de la venta. No se escribió nada. */
    EXCEDE_EL_SALDO,

    /** La venta no está en el teléfono. No se escribió nada. */
    VENTA_NO_ESTA_EN_EL_TELEFONO,

    /** Lo que llegó no es una condonación (`FORMA_COBRO_ID`). No se escribió nada. */
    NO_ES_CONDONACION
}

/**
 * **El único escritor de la condonación**: inserta la fila y descuenta el saldo
 * en UNA transacción, contra el saldo que lee dentro de ella.
 *
 * ## Qué había antes, y por qué costó dinero
 *
 * `NewForgivenessDialog` llamaba a `PaymentsViewModel.savePayment`, que baja a
 * `PaymentsLocalDataSource.insertPaymentAndUpdateSale`: un `@Transaction` que no
 * genera nada fuera de un `@Dao` (E-APP-045) y un `SaleDao.updateTotal` que resta
 * sin piso. La validación del monto corría contra la foto de la venta que la
 * pantalla había cargado al abrir, no contra el saldo del momento (E-APP-043, #4).
 * Resultado medido: cuatro condonaciones de $1,000 sobre la misma venta y
 * `SALDO_REST = -3000.0` en un teléfono de campo (E-APP-029, E-APP-030); y en la
 * prueba de la compuerta del 2026-09-26, tres en siete segundos y `-2530`.
 *
 * ## La invariante, que protege a CUALQUIER puerta
 *
 * > Una condonación sólo se escribe si su monto es positivo y cabe en el
 * > `SALDO_REST` **vigente**; si no, no se escribe nada.
 *
 * Vive aquí y no en la pantalla porque la condonación tiene dos puertas vivas
 * (E-APP-044): `ForgivenessScreen` y `SaleActionSection`. Las dos pasan por
 * este escritor.
 *
 * Dentro de `db.withTransaction`:
 * 1. relee la venta por el cargo al que apunta la condonación
 *    (`SaleDao.findByDoctoCcId`: `Payment.DOCTO_CC_ACR_ID` = `sales.DOCTO_CC_ID`);
 * 2. rechaza, **sin insertar**, un monto que no sea positivo o que exceda el
 *    saldo releído;
 * 3. inserta la fila y descuenta con [SaleDao.descontarSiAlcanza], cuyo `WHERE`
 *    vuelve a exigir que el saldo alcance. Si toca cero filas lanza, y la
 *    transacción deshace el insert.
 *
 * `NonCancellable` por la misma razón que `RegistroDeAbonoAdapter`: la pantalla
 * que llama puede irse mientras la transacción corre, y `withTransaction`
 * lanza al reanudar aunque el bloque haya terminado — con el dinero ya escrito
 * y la pantalla creyendo que falló.
 *
 * El abono y la visita **no** pasan por aquí y no cambian: siguen con
 * `updateTotal` (ver el KDoc de [SaleDao.descontarSiAlcanza]).
 */
class RegistroDeCondonacion(
    private val db: AppDatabase,
    private val saleDao: SaleDao = db.saleDao(),
    private val paymentDao: PaymentDao = db.paymentDao()
) {

    /** El `SALDO_REST` que la venta tiene AHORA en Room, o `null` si no está. */
    suspend fun saldoVigente(ventaId: Int): Double? = saleDao.getById(ventaId)?.SALDO_REST

    suspend fun condonar(condonacion: PaymentEntity): ResultadoDeLaCondonacion {
        if (condonacion.FORMA_COBRO_ID != FORMA_COBRO_CONDONACION) {
            return ResultadoDeLaCondonacion.NO_ES_CONDONACION
        }
        if (!condonacion.IMPORTE.isFinite() || condonacion.IMPORTE <= 0.0) {
            return ResultadoDeLaCondonacion.MONTO_INVALIDO
        }
        return withContext(NonCancellable) {
            try {
                db.withTransaction { escribir(condonacion) }
            } catch (_: SaldoQueNoAlcanza) {
                ResultadoDeLaCondonacion.EXCEDE_EL_SALDO
            }
        }
    }

    private suspend fun escribir(condonacion: PaymentEntity): ResultadoDeLaCondonacion {
        // `Payment.DOCTO_CC_ACR_ID` es el CARGO (= `sales.DOCTO_CC_ID`, ver
        // `SaleIdSpaces`): la venta se resuelve por esa columna, y el descuento va
        // por la PK de la fila que se encontró.
        val venta = saleDao.findByDoctoCcId(
            doctoCcId = condonacion.DOCTO_CC_ACR_ID
        ) ?: return ResultadoDeLaCondonacion.VENTA_NO_ESTA_EN_EL_TELEFONO
        if (enCentavos(condonacion.IMPORTE) > enCentavos(venta.SALDO_REST)) {
            return ResultadoDeLaCondonacion.EXCEDE_EL_SALDO
        }
        paymentDao.savePayment(condonacion)
        val descontadas = saleDao.descontarSiAlcanza(
            saleId = venta.DOCTO_CC_ACR_ID,
            monto = condonacion.IMPORTE,
            estadoCobranza = EstadoCobranza.PAGADO
        )
        // Cero filas: entre la lectura y el UPDATE el saldo dejó de alcanzar. Se
        // lanza para que la transacción deshaga el insert de arriba.
        if (descontadas != 1) throw SaldoQueNoAlcanza()
        return ResultadoDeLaCondonacion.GUARDADA
    }

    /** Comparación al centavo: el mismo medio centavo que tolera `descontarSiAlcanza`. */
    private fun enCentavos(valor: Double): BigDecimal =
        BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP)

    /** Señal interna para deshacer la transacción. Nunca sale de esta clase. */
    private class SaldoQueNoAlcanza : RuntimeException()
}
