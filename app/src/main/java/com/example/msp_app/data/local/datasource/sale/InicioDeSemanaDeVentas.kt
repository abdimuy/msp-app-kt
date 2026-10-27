package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import androidx.room.withTransaction
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
 *
 * **La lectura, el mapeo y el `replaceAll` van en UNA transacción (E-APP-082).**
 * Reescribe la tabla completa con lo que leyó, y ni el abono, ni la
 * condonación, ni la visita, ni el sync toman un candado común con esta
 * operación: sin la transacción, lo que confirmaran entre el `getAll` y el
 * `replaceAll` quedaba pisado por la foto vieja (abono → 1450/PENDIENTE en vez
 * de 1230/PAGADO; saldo nuevo del servidor → el viejo, 20/20 medido). Con ella
 * esperan a que termine y se aplican después. Qué se escribe no cambia.
 */
class InicioDeSemanaDeVentas(
    private val db: AppDatabase,
    private val saleDao: SaleDao = db.saleDao()
) {
    constructor(context: Context) : this(AppDatabase.getInstance(context))

    suspend fun reiniciarEstadosDeCobranza() {
        db.withTransaction {
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
}
