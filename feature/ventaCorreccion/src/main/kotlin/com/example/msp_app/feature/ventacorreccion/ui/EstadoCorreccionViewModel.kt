package com.example.msp_app.feature.ventacorreccion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.feature.ventacorreccion.domain.EstadoCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.ConsultarEstadoCorreccion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Respalda el punto de entrada a la corrección (Task 5, `SaleDescriptionScreen` en `:app`):
 * expone [EstadoCorreccion] de sólo lectura para decidir si se ofrece "Corregir venta" o el
 * aviso correspondiente. Deliberadamente SEPARADO de [CorreccionVentaViewModel]: éste reclama el
 * candado y cancela trabajo encolado al llamar `reclamar()`, efectos que sólo deben correr al
 * ENTRAR de verdad a editar (`EditSaleScreen`), no al sólo mostrar la pantalla de descripción.
 */
@HiltViewModel
class EstadoCorreccionViewModel @Inject constructor(
    private val consultarEstadoCorreccion: ConsultarEstadoCorreccion
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoCorreccion?>(null)
    val estado: StateFlow<EstadoCorreccion?> = _estado.asStateFlow()

    /** `null` mientras no se ha consultado, o si la venta no existe. */
    fun consultar(saleId: String) {
        viewModelScope.launch {
            _estado.value = consultarEstadoCorreccion(saleId)
        }
    }
}
