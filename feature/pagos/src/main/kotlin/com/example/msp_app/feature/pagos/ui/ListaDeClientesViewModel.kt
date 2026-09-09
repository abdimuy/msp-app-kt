package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.application.Cartera
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.application.ReunirCartera
import com.example.msp_app.feature.pagos.di.PagosIoDispatcher
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado observable de la lista de clientes. */
@Immutable
data class ListaDeClientesUiState(
    val cargando: Boolean = true,
    /** Los clientes YA buscados, filtrados por chip y ordenados. */
    val clientes: List<ClienteEnLista> = emptyList(),
    val conteos: Map<SegmentoDeCobranza, Int> = emptyMap(),
    val segmento: SegmentoDeCobranza = SegmentoDeCobranza.TODOS,
    val query: String = "",
    /**
     * La carga falló. No hay una segunda rama "no está en el teléfono" como en
     * el detalle: una ruta sin clientes es un resultado legítimo (cobrador
     * nuevo, sync pendiente) y se dice con el vacío, no con un error.
     */
    val fallo: Boolean = false
)

/**
 * La lista de cobranza **por cliente** — el reemplazo de las dos listas que
 * mostraban lo mismo con lógicas distintas: la de `SalesScreen` (tres pestañas
 * sobre `ESTADO_COBRANZA`, retirada por la Task 21) y la de Home ("ventas
 * cercanas", ordenada por centroides).
 *
 * ## Dónde ocurre cada cosa, y por qué importa
 *
 * - **Leer y derivar** es de `ReunirCartera`, una vez por [cargar]. Con ella se
 *   emiten las incidencias del catálogo, una sola vez por carga.
 * - **Buscar, filtrar y ordenar** es de [CarteraEnPantalla], una función pura
 *   que se ejecuta aquí y no dentro de un `@Composable`. Una pantalla se
 *   recompone N veces por el mismo estado; ordenar ahí sería reordenar la ruta
 *   del cobrador en cada scroll.
 *
 * [SavedStateHandle] sostiene el texto buscado y el chip elegido: son la
 * pregunta que el cobrador está haciendo, y volver de la cámara (Tasks 22-23) o
 * de una captura con el filtro en blanco lo obligaría a rehacerla.
 *
 * `@HiltViewModel`, sin `@Singleton` (kill-switch de baseURL).
 */
@HiltViewModel
class ListaDeClientesViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val reunirCartera: ReunirCartera,
    private val telemetry: Telemetry,
    @PagosIoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    private val mutableState = MutableStateFlow(ListaDeClientesUiState())
    val state: StateFlow<ListaDeClientesUiState> = mutableState.asStateFlow()

    private var cartera: Cartera? = null

    private var query: String
        get() = savedStateHandle[CLAVE_QUERY] ?: ""
        set(value) {
            savedStateHandle[CLAVE_QUERY] = value
        }

    private var segmento: SegmentoDeCobranza
        get() = SegmentoDeCobranza.entries[savedStateHandle[CLAVE_SEGMENTO] ?: 0]
        set(value) {
            savedStateHandle[CLAVE_SEGMENTO] = value.ordinal
        }

    init {
        telemetry.screenView(PANTALLA)
        cargar()
    }

    /** Vuelve a leer la ruta completa. Cada llamada es UNA carga. */
    fun cargar() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(cargando = true, fallo = false)
            cartera = leer()
            proyectar()
        }
    }

    /** El texto tecleado. Con búsqueda activa el orden de cobranza se apaga. */
    fun buscar(texto: String) {
        query = texto
        proyectar()
    }

    /** El chip elegido. Filtra; no reordena. */
    fun elegirSegmento(nuevo: SegmentoDeCobranza) {
        segmento = nuevo
        proyectar()
    }

    private fun proyectar() {
        val cargada = cartera
        val proyeccion = cargada?.let {
            CarteraEnPantalla.proyectar(
                clientes = it.clientes,
                segmento = segmento,
                query = query,
                hoy = it.hoy
            )
        }
        mutableState.value = ListaDeClientesUiState(
            cargando = false,
            clientes = proyeccion?.clientes.orEmpty(),
            conteos = proyeccion?.conteos.orEmpty(),
            segmento = segmento,
            query = query,
            fallo = cargada == null
        )
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // cualquier fallo de Room/Firestore degrada igual; se reporta.
    private suspend fun leer(): Cartera? = try {
        withContext(io) { reunirCartera() }
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (fallo: Throwable) {
        telemetry.error(
            code = PagosTelemetria.CODE_LISTA_CLIENTES_FALLO,
            message = "no se pudo armar la lista de clientes",
            props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        null
    }

    private companion object {
        /** Id estático de pantalla para telemetría — sin PII. */
        const val PANTALLA = "pagos_lista_clientes"

        /**
         * El texto buscado. **Nunca sale a telemetría**: es entrada libre del
         * usuario y puede traer el nombre o el teléfono de un cliente.
         */
        const val CLAVE_QUERY = "pagos_lista_query"

        /** El chip elegido, por `ordinal` — un `Int` sobrevive al bundle sin ceremonia. */
        const val CLAVE_SEGMENTO = "pagos_lista_segmento"
    }
}
