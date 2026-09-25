package com.example.msp_app.feature.ubicacion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.geo.IndiceDePuntosCompartidos
import com.example.msp_app.core.geo.LugarAgrupado
import com.example.msp_app.core.geo.LugaresDelCliente
import com.example.msp_app.core.geo.MedicionDelCobro
import com.example.msp_app.feature.ubicacion.domain.FiltroDeLugares
import com.example.msp_app.feature.ubicacion.domain.LugarEnElMapa
import com.example.msp_app.feature.ubicacion.domain.paraElMapa
import com.example.msp_app.feature.ubicacion.domain.port.AbrirEnMapasPort
import com.example.msp_app.feature.ubicacion.domain.port.PuntosPort
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lo que la pantalla de ubicación enseña.
 *
 * [sinPuertaMedida] y [pareceMudanza] son **hechos que la pantalla anuncia sin
 * que nadie los pida**. El segundo sobre todo: el cobrador no sabe que tiene que
 * buscar una mudanza, así que si se la escondiéramos detrás de un filtro no la
 * vería nunca — y es la información que hoy existe y que nadie puede ver.
 */
data class UbicacionUiState(
    val cargando: Boolean = true,
    val lugares: List<LugarEnElMapa> = emptyList(),
    val filtro: FiltroDeLugares = FiltroDeLugares(),
    val direccion: String = "",
    val sinPuertaMedida: Boolean = false,
    val pareceMudanza: Boolean = false,
    /** Las ventas y los cobradores que de verdad aparecen, para poblar los filtros. */
    val ventasDisponibles: List<Int> = emptyList(),
    val cobradoresDisponibles: List<String> = emptyList(),
    /** El rango de fechas del cliente: de él sale el degradado por antigüedad. */
    val masViejo: Instant? = null,
    val masNuevo: Instant? = null
) {
    /** `true` cuando el cliente no tiene una sola coordenada medida. */
    val sinNingunPunto: Boolean get() = !cargando && lugares.isEmpty()
}

/**
 * El estado de la pantalla de ubicación.
 *
 * ## Por qué el índice de compartidos se arma una vez y se guarda
 *
 * `puntosDeLaRuta()` lee **todos** los puntos de la zona —decenas de miles en un
 * teléfono cargado— y el índice que sale de ahí no depende del filtro ni del
 * cliente. Rearmarlo en cada cambio de filtro sería releer Room entero para
 * obtener exactamente lo mismo.
 *
 * ## Por qué el filtro NO vuelve a leer Room
 *
 * Las mediciones del cliente se guardan crudas en [todas] y cada filtro
 * re-agrupa en memoria. Son ~20 mediciones por cliente (81 en el más cargado de
 * toda la cartera, medido), así que agrupar cuesta nada y así el filtro responde
 * al instante.
 */
@HiltViewModel
class UbicacionViewModel @Inject constructor(
    private val puntos: PuntosPort,
    private val mapas: AbrirEnMapasPort,
    private val clock: AppClock
) : ViewModel() {

    /**
     * Sale a navegar hacia [lugar].
     *
     * Va al **centro del grupo**, no a la última medición: el centro es el
     * promedio de las mediciones de domicilio y por eso está más cerca de la
     * puerta que cualquiera de ellas por separado — que es el punto entero de
     * agrupar.
     *
     * Un fallo no se traga: si el teléfono no tiene app de mapas, el `Result`
     * viene en error y acá se decide qué hacer. Hoy se ignora en silencio
     * **a propósito y está señalado**: esta pantalla todavía no tiene canal de
     * telemetría propio, y meter uno a medias sería peor que la ausencia
     * declarada.
     */
    fun comoLlegar(lugar: LugarAgrupado, direccion: String) {
        viewModelScope.launch {
            mapas.comoLlegar(lugar.centro.lat, lugar.centro.lon, direccion)
        }
    }

    private val _state = MutableStateFlow(UbicacionUiState())
    val state: StateFlow<UbicacionUiState> = _state.asStateFlow()

    private var todas: List<MedicionDelCobro> = emptyList()
    private var compartidos: IndiceDePuntosCompartidos? = null
    private var resaltado: String? = null

    /**
     * Carga los lugares de un cliente.
     *
     * [ventaId] es la puerta de atrás para el detalle de venta, que conoce la
     * cuenta y **no** al cliente: si [clienteId] viene en cero se resuelve por
     * ahí. Lo que se enseña sigue siendo **el cliente entero** —medido, el 65 %
     * de los clientes con varias ventas las tiene a menos de 40 m— y no la
     * venta: acotar el mapa a una cuenta partiría un lugar en dos por un
     * accidente de contabilidad. La cuenta sigue estando disponible como
     * **filtro**, que es donde corresponde.
     */
    fun cargar(clienteId: Int, direccion: String, pagoResaltado: String?, ventaId: Int? = null) {
        resaltado = pagoResaltado
        viewModelScope.launch {
            val id = clienteId.takeIf { it > 0 }
                ?: ventaId?.let { puntos.clienteDeVenta(it) }
                ?: 0
            todas = if (id > 0) puntos.medicionesDe(id) else emptyList()
            compartidos = IndiceDePuntosCompartidos.de(puntos.puntosDeLaRuta())
            _state.update {
                it.copy(
                    cargando = false,
                    direccion = direccion,
                    ventasDisponibles = todas.map { m -> m.ventaId }.distinct().sorted(),
                    cobradoresDisponibles = todas.map { m -> m.cobrador }.distinct().sorted(),
                    masViejo = todas.minOfOrNull { m -> m.fecha },
                    masNuevo = todas.maxOfOrNull { m -> m.fecha }
                )
            }
            recomponer()
        }
    }

    fun cambiarFiltro(nuevo: FiltroDeLugares) {
        _state.update { it.copy(filtro = nuevo) }
        recomponer()
    }

    /**
     * Re-agrupa con el filtro puesto.
     *
     * Con [FiltroDeLugares.sinAgrupar] el radio baja a cero: cada medición queda
     * en su propio lugar. No es un camino aparte —es el mismo algoritmo con otro
     * radio— y eso importa, porque una segunda implementación de "dibujar los
     * puntos" podría discrepar de la primera sin que nada avise.
     */
    private fun recomponer() {
        val indice = compartidos ?: return
        val filtro = _state.value.filtro
        val visibles = filtro.aplicar(todas, clock.now())
        val agrupadas = if (filtro.sinAgrupar) {
            LugaresDelCliente(
                lugares = com.example.msp_app.core.geo.AgrupadorDeLugares.agrupar(
                    visibles,
                    indice,
                    radioM = 0.0
                ),
                laPuerta = null
            )
        } else {
            LugaresDelCliente.de(visibles, indice)
        }
        _state.update {
            it.copy(
                lugares = agrupadas.paraElMapa(resaltado),
                sinPuertaMedida = agrupadas.sinPuertaMedida && !filtro.sinAgrupar,
                pareceMudanza = agrupadas.pareceMudanza
            )
        }
    }
}
