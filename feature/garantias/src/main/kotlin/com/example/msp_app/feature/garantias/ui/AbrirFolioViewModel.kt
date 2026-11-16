package com.example.msp_app.feature.garantias.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.garantias.application.AbrirFolioDeGarantia
import com.example.msp_app.feature.garantias.application.FalloDeCaptura
import com.example.msp_app.feature.garantias.application.ResultadoDeCaptura
import com.example.msp_app.feature.garantias.di.GarantiasIoDispatcher
import com.example.msp_app.feature.garantias.domain.model.AperturaDeGarantia
import com.example.msp_app.feature.garantias.domain.model.ArticuloNuevo
import com.example.msp_app.feature.garantias.domain.model.ClienteDeGarantia
import com.example.msp_app.feature.garantias.domain.model.DomicilioDeGarantia
import com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio
import com.example.msp_app.feature.garantias.domain.port.ClientesDeGarantiaPort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Los campos del domicilio que un folio de cliente captura a mano. */
enum class CampoDelDomicilio {
    CALLE,
    NUMERO,
    COLONIA,
    LOCALIDAD,
    CIUDAD,
    CODIGO_POSTAL
}

/**
 * Lo que pinta el formulario de "abrir folio".
 *
 * [clientes] son las coincidencias de la búsqueda por nombre; [clienteElegido]
 * el padrón que el cobrador ya eligió. [domicilio] arranca con lo que el
 * teléfono conoce del cliente (calle y ciudad) y el resto se escribe a mano: no
 * hay GPS del domicilio (D5).
 *
 * [folioCreado] es la referencia local del folio recién encolado: la pantalla la
 * consume una sola vez para navegar a su detalle y la limpia con
 * [AbrirFolioViewModel.folioNavegado].
 */
data class AbrirFolioUiState(
    val origen: OrigenDeFolio = OrigenDeFolio.PISO,
    val descripcion: String = "",
    val claveDelArticulo: String = "",
    val descripcionDelArticulo: String = "",
    val vigencia: String = "",
    val busquedaDeCliente: String = "",
    val clientes: List<ClienteDeGarantia> = emptyList(),
    val clienteElegido: ClienteDeGarantia? = null,
    val domicilio: DomicilioDeGarantia = DomicilioDeGarantia(),
    val guardando: Boolean = false,
    val fallo: FalloDeCaptura? = null,
    val falloTecnico: Boolean = false,
    val folioCreado: String? = null
)

/**
 * El alta de un folio de garantía: `abrir_folio` y su primer artículo.
 *
 * ## Una función por control
 *
 * El formulario tiene quince campos y cada uno tiene su setter. Agruparlos en un
 * `actualizar(campo, valor)` ahorraría líneas y escondería cuál control escribe
 * qué campo; el repo ya eligió lo primero (`RegistrarVisitaViewModel`).
 *
 * ## La búsqueda de cliente no bloquea
 *
 * [buscarClientes] corre en el despachador de E/S y descarta su resultado si la
 * consulta cambió mientras viajaba: una búsqueda vieja no puede pisar a una
 * nueva. Un padrón que no abre devuelve lista vacía, no un error de pantalla.
 */
@HiltViewModel
@Suppress("TooManyFunctions") // una función por control de la captura.
class AbrirFolioViewModel @Inject constructor(
    private val abrirFolio: AbrirFolioDeGarantia,
    private val clientes: ClientesDeGarantiaPort,
    @GarantiasIoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    private val mutable = MutableStateFlow(AbrirFolioUiState())
    val state: StateFlow<AbrirFolioUiState> = mutable.asStateFlow()

    fun elegirOrigen(origen: OrigenDeFolio) {
        mutable.value = mutable.value.copy(origen = origen, fallo = null)
    }

    fun cambiarDescripcion(valor: String) {
        mutable.value = mutable.value.copy(descripcion = valor)
    }

    fun cambiarClaveDelArticulo(valor: String) {
        mutable.value = mutable.value.copy(claveDelArticulo = valor)
    }

    fun cambiarDescripcionDelArticulo(valor: String) {
        mutable.value = mutable.value.copy(descripcionDelArticulo = valor)
    }

    fun cambiarVigencia(valor: String) {
        mutable.value = mutable.value.copy(vigencia = valor)
    }

    /** Busca por nombre y deja las coincidencias en [AbrirFolioUiState.clientes]. */
    fun cambiarBusqueda(valor: String) {
        mutable.value = mutable.value.copy(busquedaDeCliente = valor, clienteElegido = null)
        buscarClientes(valor)
    }

    /** El cobrador eligió un cliente: se prellena lo que el teléfono ya sabe. */
    fun elegirCliente(cliente: ClienteDeGarantia) {
        mutable.value = mutable.value.copy(
            clienteElegido = cliente,
            busquedaDeCliente = cliente.nombre,
            clientes = emptyList(),
            domicilio = mutable.value.domicilio.copy(
                calle = cliente.calle,
                ciudad = cliente.ciudad
            )
        )
    }

    fun cambiarDomicilio(campo: CampoDelDomicilio, valor: String) {
        val actual = mutable.value.domicilio
        mutable.value = mutable.value.copy(
            domicilio = when (campo) {
                CampoDelDomicilio.CALLE -> actual.copy(calle = valor)
                CampoDelDomicilio.NUMERO -> actual.copy(numeroExterior = valor)
                CampoDelDomicilio.COLONIA -> actual.copy(colonia = valor)
                CampoDelDomicilio.LOCALIDAD -> actual.copy(localidad = valor)
                CampoDelDomicilio.CIUDAD -> actual.copy(ciudad = valor)
                CampoDelDomicilio.CODIGO_POSTAL -> actual.copy(codigoPostal = valor)
            }
        )
    }

    /**
     * Encola el folio y su primer artículo.
     *
     * El caso de uso ya valida el contrato del servidor; acá sólo se traduce el
     * resultado a estado. Una base que no abre no puede dejar el formulario
     * colgado con "Guardando": se anota como fallo técnico.
     */
    @Suppress("TooGenericExceptionCaught") // escribir la cola no puede colgar el formulario.
    fun guardar() {
        val actual = mutable.value
        if (actual.guardando) return
        mutable.value = actual.copy(guardando = true, fallo = null, falloTecnico = false)
        viewModelScope.launch {
            val resultado = try {
                withContext(io) { abrirFolio.ejecutar(apertura(actual)) }
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(guardando = false, falloTecnico = true)
                return@launch
            }
            mutable.value = when (resultado) {
                is ResultadoDeCaptura.Encolado ->
                    mutable.value.copy(guardando = false, folioCreado = resultado.folioRef)
                is ResultadoDeCaptura.Bloqueado ->
                    mutable.value.copy(guardando = false, fallo = resultado.fallo)
            }
        }
    }

    /** La pantalla ya navegó al detalle; se suelta la referencia para no repetir. */
    fun folioNavegado() {
        mutable.value = mutable.value.copy(folioCreado = null)
    }

    @Suppress("TooGenericExceptionCaught") // el padrón es un adorno; no puede tumbar el alta.
    private fun buscarClientes(nombre: String) {
        viewModelScope.launch {
            val encontrados = try {
                withContext(io) { clientes.buscar(nombre) }
            } catch (_: Exception) {
                emptyList()
            }
            if (mutable.value.busquedaDeCliente == nombre) {
                mutable.value = mutable.value.copy(clientes = encontrados)
            }
        }
    }

    private fun apertura(actual: AbrirFolioUiState): AperturaDeGarantia {
        val esCliente = actual.origen == OrigenDeFolio.CLIENTE
        val cliente = actual.clienteElegido
        val domicilio = actual.domicilio.takeUnless { it.estaVacio }
        return AperturaDeGarantia(
            origen = actual.origen,
            descripcion = actual.descripcion.trim(),
            primerArticulo = ArticuloNuevo(
                clave = actual.claveDelArticulo.trim(),
                descripcion = actual.descripcionDelArticulo.trim()
            ),
            clienteId = if (esCliente) cliente?.clienteId else null,
            ventaId = if (esCliente) cliente?.ventaId else null,
            estadoCuenta = if (esCliente) cliente?.estadoDeCuenta else null,
            domicilio = if (esCliente) domicilio else null,
            vigenciaHasta = AppTime.parseWireDateOrNull(actual.vigencia.trim())
        )
    }
}
