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
import com.example.msp_app.feature.pagos.domain.port.TemaDeLaAppPort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val fallo: Boolean = false,
    /**
     * Tema oscuro vigente **de la app entera** — lo pinta el botón sol/luna del
     * encabezado. No es un espejo local: lo siembra y lo mantiene
     * [TemaDeLaAppPort], que es `ThemeController`. Ver el KDoc de
     * [ListaDeClientesViewModel.alternarTema].
     */
    val temaOscuro: Boolean = false
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
    private val tema: TemaDeLaAppPort,
    private val telemetry: Telemetry,
    @PagosIoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        // Sembrado SÍNCRONO, no por la colecta de abajo: con el default `false`
        // la pantalla pintaría un frame en claro antes del primer valor del
        // `Flow`, y con la app en oscuro eso es un flash blanco al entrar.
        ListaDeClientesUiState(temaOscuro = tema.oscuroAhora())
    )
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
        // Mantiene `temaOscuro` pegado al tema GLOBAL mientras la pantalla vive
        // — no solo cuando se toca ESTE toggle: si el tema cambia desde otro
        // lado (el cajón legado, Configuración, el reporte, o el sistema
        // operativo en modo Automático) el glifo del encabezado lo refleja.
        // `viewModelScope` cancela la colecta sola. Mismo patrón que
        // `CollectionReportViewModel.init`.
        viewModelScope.launch {
            tema.oscuro.collect { oscuro ->
                mutableState.update { it.copy(temaOscuro = oscuro) }
            }
        }
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

    /**
     * Alterna el tema **GLOBAL** de la app vía [TemaDeLaAppPort] —el mismo que
     * mueven el cajón legado, Configuración y el reporte de cobranza—, y por eso
     * persiste: sobrevive a navegar al cliente y volver, y a que muera el
     * proceso.
     *
     * No escribe `temaOscuro` aquí a propósito. La escritura del `StateFlow` la
     * produce la colecta de [TemaDeLaAppPort.oscuro] instalada en [init], que es
     * lo que hace que el glifo también reaccione a un cambio de tema hecho en
     * otra pantalla. Mismo desacople que `CollectionReportViewModel.toggleTheme`
     * y `ConfiguracionViewModel.selectThemeMode`; el `alternar()` real es
     * síncrono (solo escribe `SharedPreferences`), así que no hace falta lanzar
     * una corrutina.
     */
    fun alternarTema() {
        telemetry.tap(PANTALLA, ACCION_TEMA)
        tema.alternar()
    }

    /**
     * **`copy` y no un `ListaDeClientesUiState(...)` nuevo**, y esa diferencia
     * es un defecto que ya estaba esperando: esta función corre en cada tecla
     * del buscador y en cada toque de chip, así que construir el estado desde
     * cero devuelve al default TODO campo que no se nombre acá. Con
     * `temaOscuro` en el estado eso significaba que **teclear una letra
     * apagaba el tema oscuro del encabezado**. `copy` nombra las seis que esta
     * proyección sí produce y deja intacto lo que no es suyo.
     */
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
        mutableState.update {
            it.copy(
                cargando = false,
                clientes = proyeccion?.clientes.orEmpty(),
                conteos = proyeccion?.conteos.orEmpty(),
                segmento = segmento,
                query = query,
                fallo = cargada == null
            )
        }
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

        /** Id de la acción del toggle en telemetría — mismo nombre que usa el reporte. */
        const val ACCION_TEMA = "theme_toggle"
    }
}
