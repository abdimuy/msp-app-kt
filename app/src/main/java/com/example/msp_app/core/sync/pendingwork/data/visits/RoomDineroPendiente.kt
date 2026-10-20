package com.example.msp_app.core.sync.pendingwork.data.visits

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.DineroPendiente
import com.example.msp_app.core.database.dao.localsale.LocalSaleDao
import com.example.msp_app.core.database.dao.payment.PaymentDao
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * [DineroPendiente] sobre Room. SOLO LEE, con dos conteos que ya existían y
 * que pinta el menú lateral: [PaymentDao.observarPendientesDeSubir]
 * (`Payment` con `GUARDADO_EN_MICROSIP = 0`) y [LocalSaleDao.observarSinSubir]
 * (`local_sale` sin enviar y sin fallo permanente). La tabla `sales` no tiene
 * estado "por subir": es la copia del servidor.
 */
class RoomDineroPendiente @Inject constructor(
    private val pagos: PaymentDao,
    private val ventas: LocalSaleDao
) : DineroPendiente {
    override suspend fun hayPendientes(): Boolean =
        pagos.observarPendientesDeSubir().first() > 0 || ventas.observarSinSubir().first() > 0
}
