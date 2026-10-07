package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.application.Cartera
import com.example.msp_app.feature.pagos.application.ControladorDeOrden
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.application.ReunirCartera
import com.example.msp_app.feature.pagos.application.TogglesDeCabecera
import com.example.msp_app.feature.pagos.di.PagosIoDispatcher
import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.domain.port.CercaniaPort
import com.example.msp_app.feature.pagos.domain.port.PrivacidadPort
import com.example.msp_app.feature.pagos.domain.port.TemaDeLaAppPort
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    val segmento: SegmentoDeCobranza = SegmentoDeCobranza.SIN_VISITAR,
    val query: String = "",
    /**
     * La carga falló. No hay una segunda rama "no está en el teléfono" como en
     * el detalle: una ruta sin clientes es un resultado legítimo (cobrador
     * nuevo, sync pendiente) y se dice con el vacío, no con un error.
     */
    val fallo: Boolean = false,
    /**
     * "Esconder cantidades" — lo mantiene [PrivacidadPort], que es la preferencia
     * global de la app, no un espejo local de esta pantalla.
     */
    val montosOcultos: Boolean = false,
    /**
     * Tema oscuro vigente **de la app entera** — lo pinta el botón sol/luna del
     * encabezado. No es un espejo local: lo siembra y lo mantiene
     * [TemaDeLaAppPort], que es `ThemeController`. Ver el KDoc de
     * [ListaDeClientesViewModel.alternarTema].
     */
    val temaOscuro: Boolean = false,
    /**
     * El "hoy" de [com.example.msp_app.feature.pagos.application.Cartera], el
     * mismo que ya usa [CarteraEnPantalla] para ordenar y filtrar — lo necesita
     * cada `FilaDeCliente` para "Vendida hace N días" en una venta sin abonos.
     * [EPOCA] mientras la pantalla está cargando: ahí no se pinta ni una
     * tarjeta, así que el valor nunca se muestra; existe para que el tipo no
     * sea anulable (mismo patrón que `RegistrarVisitaUiState.hoy`).
     */
    val hoy: LocalDate = EPOCA,
    /**
     * El orden elegido en la hoja "Ordenar por" (decisión del dueño,
     * 2026-10-05). [OrdenDeLaLista.COBRANZA] es el de siempre y el único que
     * no se persiste "durante el día" — ver [OrdenGuardadoPort].
     */
    val orden: OrdenDeLaLista = OrdenDeLaLista.COBRANZA,
    /**
     * `true` cuando [orden] es [OrdenDeLaLista.MAS_CERCA] y
     * [CercaniaPort.distanciasPorVenta] YA contestó sin una sola distancia —
     * la lista ya cayó a orden de cobranza ([CarteraEnPantalla] lo garantiza)
     * y el renglón de orden enseña el aviso apagado "Sin ubicación: orden de
     * cobranza". Mientras el GPS todavía no contesta es `false`: ver
     * [ControladorDeOrden.sinUbicacion].
     */
    val avisoSinUbicacion: Boolean = false
)

/**
 * El "hoy" de un estado que todavía no cargó. No es `LocalDate.now()` —la
 * pantalla no pregunta la hora del sistema— ni `LocalDate.EPOCH` (Java 9).
 */
private val EPOCA: LocalDate = LocalDate.of(1970, 1, 1)

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
    /** Sol/luna y el ojo de privacidad del encabezado — ver el KDoc de [TogglesDeCabecera]. */
    private val toggles: TogglesDeCabecera,
    /**
     * El orden de la hoja "Ordenar por" — [CercaniaPort] y
     * [com.example.msp_app.feature.pagos.domain.port.OrdenGuardadoPort]
     * agrupados detrás de una sola clase para no cruzar el umbral
     * `LongParameterList` de detekt. Ver su KDoc.
     */
    private val controladorDeOrden: ControladorDeOrden,
    private val telemetry: Telemetry,
    @PagosIoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    /**
     * La cartera proyectada — **sin el tema**. `temaOscuro` NO se guarda acá: se deriva en
     * [state]. Ver el KDoc de [state] por qué.
     */
    private val mutableState = MutableStateFlow(ListaDeClientesUiState())

    /**
     * **El tema se DERIVA, no se guarda** (Ruling BQ).
     *
     * La primera versión lo guardaba en [mutableState] y lo mantenía con una colecta que hacía
     * `copy(temaOscuro = ...)`. Eso deja un agujero más filoso de lo que parece: cualquier
     * escritor que reconstruya el estado con el **constructor** en vez de `copy` lo pisa al
     * default, y como el `StateFlow` del puerto **no re-emite** un valor que no cambió, el
     * pisón **queda pegado hasta el próximo cambio de tema** — o sea hasta que el cobrador
     * toque el botón otra vez. Se midió: `proyectar()` corre en cada tecla del buscador, así
     * que teclear una letra apagaba el tema del encabezado y ahí se quedaba.
     *
     * Con `combine` el tema se vuelve a aplicar **en cada emisión**, así que ya no hay pisón
     * posible: un escritor descuidado puede perder los campos de la cartera, pero no el tema.
     * Arreglar la clase entera cuesta estas cinco líneas; declararla como riesgo no alcanzaba.
     *
     * `initialValue` con la lectura **síncrona** del puerto ([TemaDeLaAppPort.oscuroAhora]): sin
     * ella la pantalla pintaría un frame en claro antes de la primera emisión, y con la app en
     * oscuro eso es un flash blanco al entrar.
     *
     * `SharingStarted.Eagerly` y no `WhileSubscribed`: el estado tiene que estar correcto para
     * quien lea `state.value` sin coleccionar (los tests lo hacen) y la colecta anterior también
     * era eager, colgada de `viewModelScope`.
     */
    val state: StateFlow<ListaDeClientesUiState> =
        combine(
            mutableState,
            toggles.tema.oscuro,
            toggles.privacidad.ocultos
        ) { cartera, oscuro, ocultos ->
            cartera.copy(temaOscuro = oscuro, montosOcultos = ocultos)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ListaDeClientesUiState(
                temaOscuro = toggles.tema.oscuroAhora(),
                // Síncrono por la misma razón que el tema, y con más motivo: sin
                // esto la pantalla enseñaría los montos un frame antes de
                // esconderlos, que es justo lo que el botón existe para evitar.
                montosOcultos = toggles.privacidad.ocultosAhora()
            )
        )

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

    /**
     * Vuelve a leer la ruta completa. Cada llamada es UNA carga.
     *
     * Siempre pide restaurar el orden persistido
     * ([ControladorDeOrden.restaurarSiCambioElDia]) — "Reintentar" sólo
     * aparece cuando la carga anterior falló, y una carga que falló nunca
     * llegó a enseñar el renglón de orden, así que no hay elección de esta
     * sesión que proteger. [recargar] también puede restaurar, pero sólo si
     * el día de negocio cambió desde la última vez; ver su KDoc.
     *
     * **[proyectar] corre ANTES de pedir distancias** (hallazgo de la revisión
     * final de `feat/lista-venta-nueva-orden`, riesgo de dinero): con "Más
     * cerca" restaurado, [ControladorDeOrden.actualizarDistanciasSiHaceFalta]
     * puede quedarse esperando al GPS, y la cartera recién leída NO tiene por
     * qué esperarla — al volver de registrar un abono, la tarjeta se queda
     * vieja hasta que llega la posición si se proyecta después. Se proyecta
     * dos veces: la primera con la cartera fresca y el orden restaurado (sin
     * distancias todavía, cae a cobranza si hacía falta "Más cerca"); la
     * segunda, cuando las distancias lleguen (o se agote el plazo interno de
     * [ControladorDeOrden]), para reordenar.
     */
    fun cargar() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(cargando = true, fallo = false)
            cartera = leer()
            cartera?.let { controladorDeOrden.restaurarSiCambioElDia(it.hoy) }
            proyectar()
            controladorDeOrden.actualizarDistanciasSiHaceFalta()
            proyectar()
        }
    }

    /**
     * Vuelve a leer la ruta completa **sin parpadeo**: a diferencia de [cargar],
     * no enciende `cargando` antes de leer. La usa la pantalla al reanudarse —
     * ver `RecargaAlVolver` —, y un spinner de cuerpo entero al volver de
     * registrar un abono o una visita se lee como si la lista se hubiera
     * perdido.
     *
     * No hace falta conservar nada a mano como en el detalle: [query] y
     * [segmento] ya viven en el `SavedStateHandle` y no en [mutableState], así
     * que [proyectar] los vuelve a aplicar sobre la cartera fresca sin que
     * nadie los pise. El orden normalmente tampoco se toca —a diferencia de
     * [cargar], una recarga el MISMO día no vuelve a leer la persistencia: el
     * cobrador pudo haber elegido "Más cerca" hace un segundo y un regreso de
     * la cámara no debe pisarlo con lo último persistido—, salvo que el día de
     * negocio haya cambiado desde la última restauración: ver
     * [ControladorDeOrden.restaurarSiCambioElDia], que es lo que impide que el
     * orden de ayer sobreviva si la app se quedó abierta de un día para el
     * otro (sólo [cargar] restauraba antes de este arreglo, y una pantalla
     * que nunca vuelve a [cargar] — sólo a [recargar], al reanudarse — nunca
     * se enteraba de que ya era otro día).
     *
     * Lo que sí se repite siempre si el orden es [OrdenDeLaLista.MAS_CERCA] es
     * la lectura de [CercaniaPort] (decisión del dueño): la posición del
     * cobrador cambia con cada recarga y una distancia vieja dejaría de ser
     * cierta.
     *
     * [proyectar] corre antes Y después de pedir las distancias, mismo motivo
     * que [cargar]: la cartera fresca no espera al GPS para mostrarse.
     */
    fun recargar() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(fallo = false)
            cartera = leer()
            cartera?.let { controladorDeOrden.restaurarSiCambioElDia(it.hoy) }
            proyectar()
            controladorDeOrden.actualizarDistanciasSiHaceFalta()
            proyectar()
        }
    }

    /**
     * Elige un nuevo orden desde la hoja "Ordenar por". [ControladorDeOrden]
     * pide distancias si hace falta, persiste la elección con el día de
     * [cartera] (sin [cartera] — la carga falló — no se persiste; el renglón
     * que dispara esto no se pinta en ese estado, ver
     * [ListaDeClientesUiState.fallo]) y reproyecta.
     */
    fun elegirOrden(nuevo: OrdenDeLaLista) {
        viewModelScope.launch {
            controladorDeOrden.elegir(nuevo, cartera?.hoy)
            proyectar()
        }
    }

    /** "✕ Quitar" del renglón de orden: vuelve a cobranza, y lo persiste igual que cualquier otro. */
    fun quitarOrden() {
        viewModelScope.launch {
            controladorDeOrden.quitar(cartera?.hoy)
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
        toggles.tema.alternar()
    }

    /**
     * Esconde o enseña los montos. A diferencia del tema, [PrivacidadPort.alternar]
     * SÍ suspende —escribe en DataStore—, así que va en una corrutina.
     */
    fun alternarPrivacidad() {
        telemetry.tap(PANTALLA, ACCION_PRIVACIDAD)
        viewModelScope.launch { toggles.privacidad.alternar() }
    }

    /**
     * `copy` en vez de un `ListaDeClientesUiState(...)` nuevo: nombra las seis que esta
     * proyección produce y deja intacto lo que no es suyo. Es lo correcto, pero **ya no es
     * lo que sostiene el tema** — eso lo sostiene la derivación de [state] (Ruling BQ), que
     * es inmune a que alguien vuelva al constructor acá o en una proyección futura.
     */
    private fun proyectar() {
        val cargada = cartera
        val orden = controladorDeOrden.orden
        val proyeccion = cargada?.let {
            CarteraEnPantalla.proyectar(
                clientes = it.clientes,
                segmento = segmento,
                query = query,
                hoy = it.hoy,
                orden = orden,
                distanciasPorVenta = controladorDeOrden.distancias
            )
        }
        mutableState.update {
            it.copy(
                cargando = false,
                clientes = proyeccion?.clientes.orEmpty(),
                conteos = proyeccion?.conteos.orEmpty(),
                segmento = segmento,
                query = query,
                fallo = cargada == null,
                hoy = cargada?.hoy ?: EPOCA,
                orden = orden,
                avisoSinUbicacion = controladorDeOrden.sinUbicacion
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
        const val ACCION_PRIVACIDAD = "privacy_toggle"
    }
}
