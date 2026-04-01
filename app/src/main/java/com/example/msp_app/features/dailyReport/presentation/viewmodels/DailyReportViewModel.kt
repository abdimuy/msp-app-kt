package com.example.msp_app.features.dailyReport.presentation.viewmodels

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.PdfGenerator
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.dailyReport.data.repository.DailyReportRepository
import com.example.msp_app.features.dailyReport.domain.models.DailyReportData
import com.example.msp_app.features.dailyReport.domain.usecases.GenerateDailyReportUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DailyReportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DailyReportRepository(application.applicationContext)
    private val useCase = GenerateDailyReportUseCase(repository)

    private val _reportState = MutableStateFlow<ResultState<DailyReportData>>(ResultState.Idle)
    val reportState: StateFlow<ResultState<DailyReportData>> = _reportState.asStateFlow()

    private val _pdfUri = MutableStateFlow<Uri?>(null)
    val pdfUri: StateFlow<Uri?> = _pdfUri.asStateFlow()

    fun generateReport(
        camionetaId: Int,
        warehouseName: String,
        vendedorName: String,
        currentProducts: List<ProductInventory>
    ) {
        viewModelScope.launch {
            _reportState.value = ResultState.Loading
            try {
                val result = useCase.execute(
                    camionetaId = camionetaId,
                    warehouseName = warehouseName,
                    vendedorName = vendedorName,
                    currentProducts = currentProducts
                )

                result.fold(
                    onSuccess = { data ->
                        val pdfGenerated = generatePdf(data)
                        if (pdfGenerated) {
                            _reportState.value = ResultState.Success(data)
                        } else {
                            _reportState.value =
                                ResultState.Error(message = "Error al generar PDF")
                        }
                    },
                    onFailure = { error ->
                        _reportState.value = ResultState.Error(
                            message = error.message ?: "Error al generar reporte"
                        )
                    }
                )
            } catch (e: Exception) {
                _reportState.value = ResultState.Error(
                    message = e.message ?: "Error inesperado"
                )
            }
        }
    }

    private suspend fun generatePdf(data: DailyReportData): Boolean {
        val context = getApplication<Application>().applicationContext
        val file = withContext(Dispatchers.IO) {
            PdfGenerator.generateDailyReportPdf(
                context = context,
                data = data
            )
        }

        if (file != null && file.exists()) {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            _pdfUri.value = uri
            return true
        }
        return false
    }

    fun clearState() {
        _reportState.value = ResultState.Idle
        _pdfUri.value = null
    }
}
