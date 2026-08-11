package com.example.msp_app.features.sales.viewmodels

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.entities.OverduePaymentsEntity
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.sales.SalesApi
import com.example.msp_app.data.local.datasource.guarantee.GuaranteesLocalDataSource
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.local.datasource.sale.SalesLocalDataSource
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.guarantee.toEntity
import com.example.msp_app.data.models.payment.PaymentLocationsGroup
import com.example.msp_app.data.models.payment.toEntity
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.data.models.sale.SaleWithProducts
import com.example.msp_app.data.models.sale.toDomain
import com.example.msp_app.data.models.sale.toEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SalesViewModel(application: Application) : AndroidViewModel(application) {
    // Not a constructor param: the default `viewModel()` factory resolves this class via
    // reflection on an (Application)-only constructor. Adding `clock` as a constructor
    // parameter (even with a default) breaks that reflection — same pattern as
    // NewTransferViewModel / CreateGuaranteeViewModel.
    private val clock: AppClock = AppClock.System
    private val api: SalesApi get() = ApiProvider.create(SalesApi::class.java)
    private val saleStore = SalesLocalDataSource(application.applicationContext)
    private val paymentStore = PaymentsLocalDataSource(application.applicationContext)
    private val visitsStore = VisitsLocalDataSource(application.applicationContext)
    private val guaranteeStore = GuaranteesLocalDataSource(application.applicationContext)

    private val _salesState =
        MutableStateFlow<ResultState<List<SaleWithProducts>>>(ResultState.Idle)
    val salesState: StateFlow<ResultState<List<SaleWithProducts>>> = _salesState

    private val _syncSalesState =
        MutableStateFlow<ResultState<List<Sale>>>(ResultState.Idle)
    val syncSalesState: StateFlow<ResultState<List<Sale>>> = _syncSalesState

    private val _paymentsLocationsState =
        MutableStateFlow<ResultState<List<PaymentLocationsGroup>>>(ResultState.Idle)
    val paymentsLocationsState: StateFlow<ResultState<List<PaymentLocationsGroup>>> =
        _paymentsLocationsState

    private val _salesByClientState =
        MutableStateFlow<ResultState<List<SaleWithProducts>>>(ResultState.Idle)
    val salesByClientState: StateFlow<ResultState<List<SaleWithProducts>>> = _salesByClientState

    private val _overduePaymentsState =
        MutableStateFlow<ResultState<List<OverduePaymentsEntity>>>(ResultState.Loading)
    val overduePaymentsState = _overduePaymentsState.asStateFlow()

    private val _overduePaymentBySaleState =
        MutableStateFlow<ResultState<OverduePaymentsEntity?>>(ResultState.Loading)
    val overduePaymentBySaleState = _overduePaymentBySaleState.asStateFlow()

    fun getOverduePayments() {
        viewModelScope.launch {
            _overduePaymentsState.value = ResultState.Loading
            try {
                val result = paymentStore.getOverduePayments()
                _overduePaymentsState.value = ResultState.Success(result)
            } catch (e: Exception) {
                _overduePaymentsState.value =
                    ResultState.Error(e.message ?: "Error al obtener pagos atrasados")
            }
        }
    }

    fun getOverduePaymentBySaleId(saleId: Int) {
        viewModelScope.launch {
            _overduePaymentBySaleState.value = ResultState.Loading
            try {
                val result = paymentStore.getPagosAtrasadosBySaleId(saleId)
                _overduePaymentBySaleState.value = ResultState.Success(result)
            } catch (e: Exception) {
                _overduePaymentBySaleState.value =
                    ResultState.Error(e.message ?: "Error al obtener pago atrasado por venta")
            }
        }
    }

    /**
     * Cargada una sola vez: arranca un colector de Room que emite cada vez
     * que cambia la tabla `sales` (ya sea por el sync incremental, por un
     * pago local que actualiza SALDO_REST, etc.). Llamar a esta funcion
     * mas de una vez es idempotente — el colector previo se cancela.
     */
    private var localSalesJob: Job? = null

    fun getLocalSales() {
        if (localSalesJob?.isActive == true) return
        _salesState.value = ResultState.Loading
        localSalesJob = saleStore.observeAll()
            .onEach { rows ->
                _salesState.value = ResultState.Success(rows.map { it.toDomain() })
            }
            .catch { e ->
                _salesState.value = ResultState.Error(e.message ?: "Error leyendo ventas locales")
            }
            .launchIn(viewModelScope)
    }

    fun getSalesByClientId(clientId: Int) {
        viewModelScope.launch {
            _salesByClientState.value = ResultState.Loading
            try {
                val cached = saleStore.getByClientId(clientId).map { it.toDomain() }
                _salesByClientState.value = ResultState.Success(cached)
            } catch (e: Exception) {
                _salesByClientState.value =
                    ResultState.Error(e.message ?: "Error leyendo ventas locales")
            }
        }
    }

    /**
     * Sincroniza catálogos auxiliares (garantías y eventos de garantías)
     * desde el endpoint legacy del backend Node. Las ventas, los pagos y los
     * productos ya no se traen por esta ruta:
     *   - Ventas/pagos los mantiene el
     *     [com.example.msp_app.core.sync.cobranza.CobranzaSyncManager] cada
     *     30 s contra el backend v2 de forma incremental y offline-aware.
     *   - Productos ahora viajan embebidos en cada venta del sync v2
     *     (`VentaDto.productos`) y se persisten por folio en
     *     [com.example.msp_app.core.sync.cobranza.CobranzaSyncManager.mergeVentas].
     *     Este endpoint legacy ya NO debe tocar la tabla `products` — hacía
     *     un `deleteAll()` global que borraría el upsert por folio del
     *     backend Go en cuanto corriera después.
     */
    fun syncSales(zona: Int, dateInit: String) {
        viewModelScope.launch {
            _syncSalesState.value = ResultState.Loading
            try {
                val salesData = api.getAll(
                    zona = zona,
                    dateInit = dateInit
                )

                val guarantees = salesData.body.garantias
                val guaranteesEvent = salesData.body.eventosGarantias

                guaranteeStore.saveAllGurantees(guarantees.map { it.toEntity() })
                guaranteeStore.saveAllGuaranteeEvents(guaranteesEvent.map { it.toEntity() })
                // Solo se podan las visitas ya confirmadas por el servidor
                // (GUARDADO_EN_MICROSIP = 1). Las pendientes son datos de campo
                // del cobrador aún sin enviar y deben sobrevivir hasta que
                // PendingVisitsWorker las entregue — deleteAllVisits() borraba
                // TODO sin condición y perdía visitas nunca subidas.
                visitsStore.deleteUploadedVisits()

                _syncSalesState.value = ResultState.Success(emptyList())

                saveLastSyncDate(currentSalesLastSync(clock))
            } catch (e: Exception) {
                if (_syncSalesState.value !is ResultState.Success) {
                    _syncSalesState.value = ResultState.Error(e.message ?: "Error al cargar productos y garantías")
                }
            }
        }
    }

    private fun saveLastSyncDate(date: String) {
        val prefs = getApplication<Application>()
            .getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putString("last_sync_date", date)
        }
    }

    fun getLastSyncDate(): String {
        val prefs = getApplication<Application>()
            .getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        return prefs.getString("last_sync_date", "") ?: ""
    }
}
