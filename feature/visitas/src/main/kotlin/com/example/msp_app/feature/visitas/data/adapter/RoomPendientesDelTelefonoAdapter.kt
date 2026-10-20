package com.example.msp_app.feature.visitas.data.adapter

import com.example.msp_app.core.database.dao.localsale.LocalSaleDao
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.database.dao.visit.VisitImageDao
import com.example.msp_app.feature.visitas.domain.model.PendientesDelTelefono
import com.example.msp_app.feature.visitas.domain.port.PendientesDelTelefonoPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * E-APP-096. Cuatro `COUNT(*)` observados, todos de solo lectura. Los de pagos y
 * ventas son los que ya existían para el menú lateral —este adaptador no agrega
 * ni cambia nada en `PaymentDao` ni en `LocalSaleDao`—; los de visitas y fotos
 * son nuevos y viven en `VisitDao` / `VisitImageDao`.
 */
class RoomPendientesDelTelefonoAdapter(
    private val visitDao: VisitDao,
    private val visitImageDao: VisitImageDao,
    private val paymentDao: PaymentDao,
    private val localSaleDao: LocalSaleDao
) : PendientesDelTelefonoPort {

    override fun observar(): Flow<PendientesDelTelefono> = combine(
        visitDao.observarSinEnviar(),
        visitImageDao.observarSinSubir(),
        paymentDao.observarPendientesDeSubir(),
        localSaleDao.observarSinSubir()
    ) { visitas, fotos, pagos, ventas ->
        PendientesDelTelefono(visitas = visitas, fotos = fotos, pagos = pagos, ventas = ventas)
    }.distinctUntilChanged()
}
