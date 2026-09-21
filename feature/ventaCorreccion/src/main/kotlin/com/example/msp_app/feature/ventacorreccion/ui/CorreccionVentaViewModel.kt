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
import kotlinx.coroutines.CancellationException
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

    /**
     * Llamado al entrar a la pantalla (`LaunchedEffect`, Task 5). Guarda de reentrada (Important
     * #3 de la ronda 1 de arreglo): si YA estamos editando esta MISMA venta (el candado está a
     * salvo, reentrante — ver `LocalSaleDao.claimForEdit`), una segunda llamada es un no-op. Sin
     * esta guarda, un `LaunchedEffect` que se reejecuta (p. ej. al rotar la pantalla) volvía a
     * reclamar, acuñaba un `claimId` nuevo y RE-EMITÍA el estado con los campos releídos de la
     * base — tirando lo que el vendedor llevaba tecleado sin guardar. El candado nunca corrió
     * peligro (es reentrante); los datos del formulario sí. Llamar a `reclamar` con un `saleId`
     * DISTINTO, o cuando el estado no es [CorreccionUiState.Editando], sí reclama de verdad.
     */
    fun reclamar(saleId: String) {
        val estadoActual = _state.value
        if (estadoActual is CorreccionUiState.Editando && this.saleId == saleId) {
            return
        }
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

    /**
     * Guarda la corrección. Sólo tiene efecto si el estado actual es [CorreccionUiState.Editando].
     *
     * Minor #1 de la ronda 1 de arreglo: además de [GuardadoRechazadoException] (el rechazo
     * "normal", clasificado), atrapa CUALQUIER OTRA excepción — alcanzable hoy mismo, p. ej.
     * `LocalSaleProductDao.mergeProductsForSale` lanza `IllegalArgumentException` si el
     * formulario manda un `ARTICULO_ID` repetido. Sin este `catch`, esa excepción salía de
     * `viewModelScope.launch` y tiraba la app — los datos quedaban bien (Room revierte la
     * transacción), pero la app no. `CancellationException` se re-lanza: atraparla rompería la
     * cancelación estructurada de la corrutina. El `catch` genérico es deliberado (red de
     * seguridad de última instancia, no un manejo clasificado) y no tiene dónde loguear el
     * detalle todavía — este módulo no tiene un puerto de telemetría cableado; se prefiere
     * documentar la excepción explícitamente a fingir que se usa.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
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
                _state.value = CorreccionUiState.NoCorregible(rechazo.estado.aTextoDeRechazoDeGuardado())
            } catch (cancelacion: CancellationException) {
                throw cancelacion
            } catch (cualquierOtro: Exception) {
                _state.value = CorreccionUiState.NoCorregible(TextosCorreccion.NO_SE_PUDO_GUARDAR)
            }
        }
    }

    /**
     * Suelta el reclamo sin guardar (botón "atrás"/cerrar del editor). Vuelve el estado a
     * [CorreccionUiState.Inicial] — necesario para que la guarda de reentrada de [reclamar] no
     * confunda "ya no tengo el candado" con "sigo editando" (el candado se soltó, pero sin esto
     * el estado se hubiera quedado congelado en [CorreccionUiState.Editando]).
     */
    fun cancelar(userEmail: String) {
        val editando = _state.value as? CorreccionUiState.Editando ?: return
        val id = saleId ?: return
        _state.value = CorreccionUiState.Inicial
        viewModelScope.launch {
            cancelarCorreccion(id, editando.claimId, userEmail)
        }
    }
}

/**
 * Mapeo para [ResultadoReclamo.NoCorregible] (falló RECLAMAR, no guardar). Aquí
 * [EstadoCorreccion.Corregible] es defensivo/inalcanzable en la práctica: el predicado SQL de
 * `claimForEdit` y el predicado de dominio de `evaluarCorregibilidad` están alineados a
 * propósito (ver `ResultadoReclamo.NoCorregible`), así que si el candado no se pudo tomar, el
 * estado releído nunca debería clasificar como "sí se puede". Se deja [TextosCorreccion.CORREGIR_VENTA]
 * como valor de una rama que un `when` exhaustivo obliga a llenar, no porque se espere mostrarlo.
 */
private fun EstadoCorreccion.aTexto(): String = when (this) {
    EstadoCorreccion.Corregible -> TextosCorreccion.CORREGIR_VENTA
    EstadoCorreccion.SeEstaEnviando -> TextosCorreccion.SE_ESTA_ENVIANDO
    EstadoCorreccion.YaSeEnvio -> TextosCorreccion.YA_SE_ENVIO
    EstadoCorreccion.LaRevisaLaOficina -> TextosCorreccion.LA_REVISA_LA_OFICINA
}

/**
 * Mapeo para el rechazo de GUARDAR ([GuardadoRechazadoException]) — a diferencia de [aTexto],
 * aquí [EstadoCorreccion.Corregible] SÍ es alcanzable (Important #2 de la ronda 1 de arreglo):
 * es el caso "candado ajeno pero reentrante", otra sesión de EDICIÓN ganó la fila entre el
 * guardia y la relectura. Mostrar [TextosCorreccion.CORREGIR_VENTA] ahí mentiría — el botón dice
 * "puedes corregir" cuando la razón real es que ESTE guardado, con ESTE `claimId`, no se pudo
 * completar. [TextosCorreccion.NO_SE_PUDO_GUARDAR] no promete nada sobre si se puede reintentar.
 */
private fun EstadoCorreccion.aTextoDeRechazoDeGuardado(): String = when (this) {
    EstadoCorreccion.Corregible -> TextosCorreccion.NO_SE_PUDO_GUARDAR
    EstadoCorreccion.SeEstaEnviando -> TextosCorreccion.SE_ESTA_ENVIANDO
    EstadoCorreccion.YaSeEnvio -> TextosCorreccion.YA_SE_ENVIO
    EstadoCorreccion.LaRevisaLaOficina -> TextosCorreccion.LA_REVISA_LA_OFICINA
}
