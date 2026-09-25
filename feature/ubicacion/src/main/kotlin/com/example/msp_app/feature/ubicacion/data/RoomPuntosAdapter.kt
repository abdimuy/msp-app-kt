package com.example.msp_app.feature.ubicacion.data

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.payment.MedicionDelClienteRow
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.geo.MedicionDelCobro
import com.example.msp_app.core.geo.Punto
import com.example.msp_app.core.geo.PuntoDeLaRuta
import com.example.msp_app.feature.ubicacion.domain.port.PuntosPort

/**
 * [PuntosPort] sobre Room. **No toca el API**: todo lo que esta pantalla
 * necesita ya está en el teléfono.
 */
class RoomPuntosAdapter(private val paymentDao: PaymentDao) : PuntosPort {

    override suspend fun medicionesDe(clienteId: Int): List<MedicionDelCobro> =
        paymentDao.getPuntosDelCliente(clienteId).mapNotNull { it.aMedicion() }

    override suspend fun clienteDeVenta(ventaId: Int): Int? = paymentDao.getClienteDeVenta(ventaId)

    override suspend fun puntosDeLaRuta(): List<PuntoDeLaRuta> =
        paymentDao.getPuntosDeLaRuta().map {
            PuntoDeLaRuta(
                clienteId = it.clienteId,
                cobrador = it.cobrador,
                punto = Punto(it.lat, it.lng)
            )
        }
}

/**
 * Un abono con fecha impresentable **se descarta** en vez de aterrizar en una
 * fecha inventada.
 *
 * Aquí la fecha no es decorativa: de ella salen el degradado por antigüedad y la
 * detección de mudanza. Un abono con fecha falsa podría inventar una era que no
 * existió —o tapar una que sí—, que es peor que no pintarlo. Es el mismo
 * criterio que `RoomPagosAdapter` ya aplica sobre el historial.
 */
private fun MedicionDelClienteRow.aMedicion(): MedicionDelCobro? {
    val fecha = AppTime.parseWireFormatOrNull(fechaHoraPago) ?: return null
    return MedicionDelCobro(
        pagoId = id,
        punto = Punto(lat, lng),
        fecha = fecha,
        ventaId = ventaId,
        cobrador = cobrador,
        esTransferencia = formaCobroId == FORMA_TRANSFERENCIA
    )
}

/**
 * El id de "transferencia" en Microsip.
 *
 * **OJO, y está medido:** la tabla catálogo `FORMAS_COBRO` trae `67/68/71/27773`
 * y **ninguno de esos ids aparece en un solo pago**. Los reales son 157
 * (efectivo), 158 (cheque) y 52569 (transferencia) — ver `E-COB-017` en
 * `msp-api/docs/evidencia/cobranza.md`. Quien "corrija" esta constante contra el
 * catálogo rompe la pantalla en silencio: dejaría de marcar transferencias y
 * volverían a votar por la puerta.
 */
private const val FORMA_TRANSFERENCIA = 52569
