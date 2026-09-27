package com.example.msp_app.feature.ubicacion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.geo.IndiceDePuntosCompartidos
import com.example.msp_app.core.geo.LugarAgrupado
import com.example.msp_app.core.geo.LugaresDelCliente
import com.example.msp_app.core.geo.MedicionDelCobro
import com.example.msp_app.feature.ubicacion.domain.FiltroDeLugares
import com.example.msp_app.feature.ubicacion.domain.LugarClasificado
import com.example.msp_app.feature.ubicacion.domain.MapaDelCliente
import com.example.msp_app.feature.ubicacion.domain.VisitaMedida
import com.example.msp_app.feature.ubicacion.domain.aplicarAVisitas
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
 * [mapa] ya trae todo decidido —clases, sueltos, encuadre, cambio de lugar—
 * en el dominio; aquí sólo vive lo que se eligió ver y lo que se tocó.
 */
data class UbicacionUiState(
    val cargando: Boolean = true,
    val mapa: MapaDelCliente = MapaDelCliente(emptyList(), emptyList(), emptyList(), null),
    val visitas: List<VisitaMedida> = emptyList(),
    val filtro: FiltroDeLugares = FiltroDeLugares(),
    val direccion: String = "",
    /** `DOCTO_CC_ACR_ID` → nombre corto de la venta ("Sala 3 piezas"). */
    val nombresDeVenta: Map<Int, String> = emptyMap(),
    /** Las ventas que aparecen, con cuántos cobros trae cada una (chips de Venta). */
    val cobrosPorVenta: Map<Int, Int> = emptyMap(),
    val cobradoresDisponibles: List<String> = emptyList(),
    val totalCobros: Int = 0,
    val totalVisitas: Int = 0,
    val totalPromesas: Int = 0,
    /** El lugar abierto en la hoja (tocado en el mapa o en su card). */
    val lugarTocado: LugarClasificado? = null,
    /** "Ahora" del reloj de negocio: decide si una fecha lleva año ("jun 2025"). */
    val ahora: Instant = Instant.EPOCH
) {
    /** `true` cuando el cliente no tiene una sola coordenada medida. */
    val sinNingunPunto: Boolean get() = !cargando && mapa.todos.isEmpty()

    /** Compatibilidad con la ruta anterior: cuántas ventas distintas hay. */
    val ventasDisponibles: List<Int> get() = cobrosPorVenta.keys.sorted()

    fun nombreDeVenta(ventaId: Int): String = nombresDeVenta[ventaId] ?: "Cuenta $ventaId"
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
    private var todasLasVisitas: List<VisitaMedida> = emptyList()
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
            todasLasVisitas = if (id > 0) puntos.visitasDe(id) else emptyList()
            val nombres = if (id > 0) puntos.ventasDe(id) else emptyMap()
            compartidos = IndiceDePuntosCompartidos.de(puntos.puntosDeLaRuta())
            _state.update {
                it.copy(
                    cargando = false,
                    direccion = direccion,
                    ahora = clock.now(),
                    nombresDeVenta = nombres,
                    cobrosPorVenta = todas.groupingBy { m -> m.ventaId }.eachCount(),
                    cobradoresDisponibles = todas.groupingBy { m -> m.cobrador }.eachCount()
                        .entries.sortedByDescending { e -> e.value }.map { e -> e.key },
                    totalCobros = todas.size,
                    totalVisitas = todasLasVisitas.count { v -> !v.esPromesa },
                    totalPromesas = todasLasVisitas.count { v -> v.esPromesa }
                )
            }
            recomponer()
            // Entrar desde una fila de la bitácora abre ESE lugar.
            resaltado?.let { pago ->
                val lugar = _state.value.mapa.todos.firstOrNull { l ->
                    l.lugar.mediciones.any { m -> m.pagoId == pago }
                }
                if (lugar != null) _state.update { it.copy(lugarTocado = lugar) }
            }
        }
    }

    fun cambiarFiltro(nuevo: FiltroDeLugares) {
        _state.update { it.copy(filtro = nuevo) }
        recomponer()
    }

    /** Abre un lugar en la hoja (o la regresa a la lista con `null`). */
    fun tocarLugar(lugar: LugarClasificado?) {
        _state.update { it.copy(lugarTocado = lugar) }
    }

    /** Re-agrupa y re-clasifica con el filtro puesto, en memoria. */
    private fun recomponer() {
        val indice = compartidos ?: return
        val filtro = _state.value.filtro
        val visibles = filtro.aplicar(todas, clock.now())
        // "Ver cada punto" NO re-agrupa con otro radio: la clasificación sale
        // siempre de los 30 m, y el mapa dibuja cada medición con el color de
        // su lugar. Así apagar el agrupamiento no cambia qué lugar es cuál.
        val agrupadas = LugaresDelCliente.de(visibles, indice)
        val mapa = MapaDelCliente.de(agrupadas)
        _state.update {
            it.copy(
                mapa = mapa,
                visitas = filtro.aplicarAVisitas(todasLasVisitas, clock.now()),
                // Un lugar abierto que el filtro deshizo se cierra: su card ya no existe.
                lugarTocado = it.lugarTocado?.let { t ->
                    mapa.todos.firstOrNull { l -> l.centro == t.centro && l.conteo == t.conteo }
                }
            )
        }
    }
}
