package com.example.msp_app.feature.ventacorreccion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.feature.ventacorreccion.domain.CamposVentaCorregidos
import com.example.msp_app.feature.ventacorreccion.domain.EstadoCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.TextosCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.CancelarCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.GuardadoRechazadoException
import com.example.msp_app.feature.ventacorreccion.domain.usecase.GuardarCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.ReclamarCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.ResultadoReclamo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Venta no encontrada al reclamar o al releer tras un rechazo — caso defensivo, no esperado. */
private const val VENTA_NO_ENCONTRADA = "Venta no encontrada"

/**
 * Orquesta [ReclamarCorreccion]/[GuardarCorreccion]/[CancelarCorreccion] para la pantalla de
 * corrección (Task 5 construye el formulario real sobre este estado). No decide corregibilidad
 * ni toca el candado directamente — sólo llama a los casos de uso y refleja su resultado.
 */
@HiltViewModel
class CorreccionVentaViewModel @Inject constructor(
    private val reclamarCorreccion: ReclamarCorreccion,
    private val guardarCorreccion: GuardarCorreccion,
    private val cancelarCorreccion: CancelarCorreccion
) : ViewModel() {

    private val _state = MutableStateFlow<CorreccionUiState>(CorreccionUiState.Inicial)
    val state: StateFlow<CorreccionUiState> = _state.asStateFlow()

    private var saleId: String? = null

    /** Llamado al entrar a la pantalla (`LaunchedEffect`, Task 5). Reentrante: llamarlo de nuevo re-reclama. */
    fun reclamar(saleId: String) {
        this.saleId = saleId
        viewModelScope.launch {
            when (val resultado = reclamarCorreccion(saleId)) {
                is ResultadoReclamo.Reclamada -> _state.value = CorreccionUiState.Editando(
                    claimId = resultado.claimId,
                    campos = resultado.venta.campos,
                    productos = resultado.venta.productos,
                    combos = resultado.venta.combos
                )

                is ResultadoReclamo.NoCorregible ->
                    _state.value = CorreccionUiState.NoCorregible(resultado.estado.aTexto())

                ResultadoReclamo.NoExiste ->
                    _state.value = CorreccionUiState.NoCorregible(VENTA_NO_ENCONTRADA)
            }
        }
    }

    /** Guarda la corrección. Sólo tiene efecto si el estado actual es [CorreccionUiState.Editando]. */
    fun guardar(
        campos: CamposVentaCorregidos,
        productos: List<LocalSaleProductEntity>,
        combos: List<LocalSaleComboEntity>,
        userEmail: String
    ) {
        val editando = _state.value as? CorreccionUiState.Editando ?: return
        val id = saleId ?: return
        viewModelScope.launch {
            try {
                guardarCorreccion(id, editando.claimId, campos, productos, combos, userEmail)
                _state.value = CorreccionUiState.Guardada
            } catch (rechazo: GuardadoRechazadoException) {
                _state.value = CorreccionUiState.NoCorregible(rechazo.estado.aTexto())
            }
        }
    }

    /** Suelta el reclamo sin guardar (botón "atrás"/cerrar del editor). */
    fun cancelar(userEmail: String) {
        val editando = _state.value as? CorreccionUiState.Editando ?: return
        val id = saleId ?: return
        viewModelScope.launch {
            cancelarCorreccion(id, editando.claimId, userEmail)
        }
    }
}

/**
 * [EstadoCorreccion.Corregible] mapea a la invitación a corregir: alcanza esta rama sólo en el
 * caso raro del "candado ajeno pero reentrante" (ver [GuardadoRechazadoException]) — la UI (Task
 * 5) debe tratarlo igual que cualquier otro rechazo, nunca como luz verde para reintentar con el
 * mismo `claimId` ya inválido.
 */
private fun EstadoCorreccion.aTexto(): String = when (this) {
    EstadoCorreccion.Corregible -> TextosCorreccion.CORREGIR_VENTA
    EstadoCorreccion.SeEstaEnviando -> TextosCorreccion.SE_ESTA_ENVIANDO
    EstadoCorreccion.YaSeEnvio -> TextosCorreccion.YA_SE_ENVIO
    EstadoCorreccion.LaRevisaLaOficina -> TextosCorreccion.LA_REVISA_LA_OFICINA
}
