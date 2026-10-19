package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.data.models.sale.toDomain
import com.example.msp_app.data.models.sale.toEntity
import com.example.msp_app.data.models.sale.toSale

/**
 * "Inicio de semana" sobre la tabla `sales`: devuelve todas las ventas a
 * `ESTADO_COBRANZA = PENDIENTE` reescribiendo la tabla completa con la foto
 * que acaba de leer (`getAll` → mapeo → `replaceAll`).
 *
 * Es la escritura que hacía `AuthViewModel.updateStartOfWeekDate` en línea;
 * vive aquí para poder probarla con Room real (E-APP-082).
 */
class InicioDeSemanaDeVentas(
    db: AppDatabase,
    private val saleDao: SaleDao = db.saleDao()
) {
    constructor(context: Context) : this(AppDatabase.getInstance(context))

    suspend fun reiniciarEstadosDeCobranza() {
        val sales = saleDao.getAll().map {
            it.toDomain()
        }
        val salesWithoutStatus = sales.map {
            it.copy(
                ESTADO_COBRANZA = EstadoCobranza.PENDIENTE
            ).toSale().toEntity()
        }
        saleDao.replaceAll(salesWithoutStatus)
    }
}
