package com.example.msp_app.features.sales.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.local.datasource.sale.SalesLocalDataSource
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.data.models.sale.toDomain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SaleDetailsViewModel(application: Application) : AndroidViewModel(application) {
    private val saleStore = SalesLocalDataSource(application.applicationContext)

    private val _saleState = MutableStateFlow<ResultState<Sale?>>(ResultState.Idle)
    val saleState: StateFlow<ResultState<Sale?>> = _saleState

    /**
     * Carga la venta por la **PK** (`sales.DOCTO_CC_ACR_ID`), que es lo que resuelve
     * `SaleDao.getById`. Es el camino de `Screen.SaleDetails`.
     */
    fun loadSaleDetails(saleId: Int) = cargar(saleId) { saleStore.getById(it) }

    /**
     * Carga la venta por el id del **crédito** (`sales.DOCTO_CC_ID`), vía
     * `SaleDao.findByDoctoCcId`.
     *
     * Existe porque `GuaranteeScreen` llega por la ruta `guarantee/{saleId}`, y ese argumento
     * es el **crédito**: las garantías están indexadas por crédito
     * (`GuaranteeDao.getGuaranteeByDoctoCcId` filtra `garantias.DOCTO_CC_ID`) y los dos
     * llamadores de la ruta le mandan eso — `GuaranteeSection` y el `onVerGarantia` del dock
     * de `:feature:pagos`, que pasa `DetalleVenta.creditoId`. La pantalla usaba
     * [loadSaleDetails], que filtra la PK: pedía por la columna que no era.
     *
     * **No se arregla cambiando el argumento de la ruta.** Ese argumento es lo que la
     * garantía necesita; cambiarlo por la PK arreglaría la venta y rompería la garantía, que
     * es la razón de ser de la pantalla. Lo que estaba mal era la consulta, no el id.
     */
    fun loadSaleDetailsByCreditId(doctoCcId: Int) =
        cargar(doctoCcId) { saleStore.findByDoctoCcId(it) }

    private fun cargar(id: Int, buscar: suspend (Int) -> SaleEntity?) {
        viewModelScope.launch {
            _saleState.value = ResultState.Loading
            try {
                val res = buscar(id)
                if (res == null) {
                    _saleState.value = ResultState.Error("No se encontró la venta con ID: $id")
                    return@launch
                }

                val sale = res.toDomain().copy(
                    FECHA = AppTime.formatIsoForDisplay(res.FECHA, AppTime.Formats.DATE_SHORT)
                )
                _saleState.value = ResultState.Success(sale)
            } catch (e: Exception) {
                _saleState.value =
                    ResultState.Error(e.message ?: "Error al cargar los detalles de la venta")
            }
        }
    }
}
