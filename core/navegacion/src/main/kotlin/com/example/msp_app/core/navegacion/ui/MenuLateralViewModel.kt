package com.example.msp_app.core.navegacion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.navegacion.application.EstadoDelMenu
import com.example.msp_app.core.navegacion.application.NavegacionTelemetria
import com.example.msp_app.core.navegacion.application.TogglesDelMenu
import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import com.example.msp_app.core.navegacion.domain.port.ContadoresDelMenuPort
import com.example.msp_app.core.navegacion.domain.port.EstadoDeEnvioPort
import com.example.msp_app.core.navegacion.domain.port.SeccionesPlegadasPort
import com.example.msp_app.core.navegacion.domain.port.ZonaDelUsuarioPort
import com.example.msp_app.core.telemetry.Telemetry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * El estado de la hoja del menú lateral, combinado desde sus puertos.
 *
 * ## Por qué [TogglesDelMenu] y no dos puertos sueltos
 *
 * Ruling R2 del controlador (2026-10-06, decisión del dueño): el botón
 * sol/luna y el ojo de privacidad SÍ van en el menú. Sumarlos como dos
 * parámetros más —junto con [ZonaDelUsuarioPort] (ruling R3)— cruzaría el
 * umbral `LongParameterList` de detekt (7), así que van agrupados igual que
 * `ListaDeClientesViewModel` agrupa los mismos dos puertos en
 * `TogglesDeCabecera`.
 *
 * ## Por qué CADA fuente tiene su propio `catch` con telemetría
 *
 * Ninguna fuente puede tumbar el menú completo: una falla de Room leyendo la
 * zona, o de `SharedPreferences`/DataStore leyendo el tema o la privacidad,
 * deja esa porción del estado en su valor neutro (el mismo que el `initialValue`
 * de [estado]) y lo reporta por [NavegacionTelemetria], nunca lanza. A
 * diferencia de `ListaDeClientesViewModel` (que no envuelve tema/privacidad
 * porque ahí esas fuentes se dan por confiables), aquí el brief exige
 * telemetría en cada `catch` del estado combinado.
 */
@HiltViewModel
class MenuLateralViewModel @Inject constructor(
    private val contadoresPort: ContadoresDelMenuPort,
    envioPort: EstadoDeEnvioPort,
    private val plegadasPort: SeccionesPlegadasPort,
    zonaPort: ZonaDelUsuarioPort,
    private val toggles: TogglesDelMenu,
    private val telemetry: Telemetry
) : ViewModel() {

    private val contadores = MutableStateFlow<Map<String, String>>(emptyMap())

    val estado: StateFlow<EstadoDelMenu> = combinar(envioPort, zonaPort)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUSCRIPCION_MS), EstadoDelMenu())

    /** Refresca los contadores de las opciones visibles. Se llama cada vez que el menú se abre. */
    fun alAbrir(ids: Set<String>) {
        viewModelScope.launch { contadores.value = leerContadores(ids) }
    }

    fun cambiarPlegado(seccionId: String, plegar: Boolean) {
        viewModelScope.launch { escribirPlegado(seccionId, plegar) }
    }

    /**
     * Alterna el tema GLOBAL vía [TemaDelMenuPort][com.example.msp_app.core.navegacion.domain.port.TemaDelMenuPort].
     * No escribe `temaOscuro` aquí: lo hace la colecta de [TogglesDelMenu.tema]
     * instalada en [estado], igual razón que `ListaDeClientesViewModel.alternarTema`.
     * `alternar()` real es síncrono, así que no hace falta una corrutina.
     */
    fun alternarTema() {
        toggles.tema.alternar()
    }

    /**
     * Esconde o enseña los montos. A diferencia del tema,
     * [PrivacidadDelMenuPort.alternar][com.example.msp_app.core.navegacion.domain.port.PrivacidadDelMenuPort.alternar]
     * SÍ suspende, así que va en una corrutina.
     */
    fun alternarMontos() {
        viewModelScope.launch { toggles.privacidad.alternar() }
    }

    private fun combinar(
        envioPort: EstadoDeEnvioPort,
        zonaPort: ZonaDelUsuarioPort
    ): Flow<EstadoDelMenu> = combine(
        combine(
            envioPort.estado()
                .map<EstadoDeEnvio, EstadoDeEnvio?> { it }
                // `null` de arranque: sin esto `combine` no emitiría NADA (ni plegadas ni
                // contadores) hasta la primera lectura de Room.
                .onStart { emit(null) }
                .catch { falla ->
                    reportar(NavegacionTelemetria.CODE_ENVIO_FALLO, falla)
                    emit(null)
                },
            plegadasPort.plegadas()
                .catch { falla ->
                    reportar(NavegacionTelemetria.CODE_PLEGADAS_FALLARON, falla)
                    emit(emptySet())
                },
            contadores
        ) { envio, plegadas, cuentas -> Triple(envio, plegadas, cuentas) },
        combine(
            zonaPort.nombreDeZona()
                .onStart { emit(null) }
                .catch { falla ->
                    reportar(NavegacionTelemetria.CODE_ZONA_FALLO, falla)
                    emit(null)
                },
            toggles.tema.oscuro
                .onStart { emit(toggles.tema.oscuroAhora()) }
                .catch { falla ->
                    reportar(NavegacionTelemetria.CODE_TEMA_FALLO, falla)
                    emit(toggles.tema.oscuroAhora())
                },
            toggles.privacidad.ocultos
                .onStart { emit(toggles.privacidad.ocultosAhora()) }
                .catch { falla ->
                    reportar(NavegacionTelemetria.CODE_MONTOS_FALLO, falla)
                    emit(toggles.privacidad.ocultosAhora())
                }
        ) { zona, temaOscuro, montosOcultos -> Triple(zona, temaOscuro, montosOcultos) }
    ) { base, extra ->
        EstadoDelMenu(
            envio = base.first,
            plegadas = base.second,
            contadores = base.third,
            nombreDeZona = extra.first,
            temaOscuro = extra.second,
            montosOcultos = extra.third
        )
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // cualquier falla de una fuente deja la opción sin contador, nunca tumba el menú
    private suspend fun leerContadores(ids: Set<String>): Map<String, String> = try {
        contadoresPort.contadores(ids)
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (falla: Exception) {
        reportar(NavegacionTelemetria.CODE_CONTADORES_FALLARON, falla)
        emptyMap()
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // una preferencia que no se guarda no debe tumbar el menú
    private suspend fun escribirPlegado(seccionId: String, plegar: Boolean) {
        try {
            plegadasPort.setPlegada(seccionId, plegar)
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (falla: Exception) {
            reportar(NavegacionTelemetria.CODE_PLEGADAS_FALLARON, falla)
        }
    }

    private fun reportar(code: String, falla: Throwable) {
        telemetry.error(
            code = code,
            message = "el menu lateral no pudo leer o guardar una fuente",
            props = mapOf("excepcion" to falla::class.java.simpleName)
        )
    }

    private companion object {
        const val SUSCRIPCION_MS = 5_000L
    }
}
