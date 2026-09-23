package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.designsystem.component.MspThemeRevealHost
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion
import com.example.msp_app.feature.pagos.domain.FiltroDeContactos
import com.example.msp_app.feature.pagos.domain.GruposDeContactos
import com.example.msp_app.feature.pagos.domain.model.BitacoraCompleta
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.example.msp_app.feature.pagos.ui.components.BarraDeDetalle
import com.example.msp_app.feature.pagos.ui.components.ContactoEnLinea
import com.example.msp_app.feature.pagos.ui.components.EncabezadoDeGrupo
import com.example.msp_app.feature.pagos.ui.components.FiltrosDeContacto
import com.example.msp_app.feature.pagos.ui.components.HojaDelContacto
import com.example.msp_app.feature.pagos.ui.components.RecargaAlVolver
import com.example.msp_app.feature.pagos.ui.components.ToqueDeLaFila

/** `testTag` del título de la bitácora. */
const val TITULO_DE_BITACORA_TAG: String = "pagos_titulo_bitacora"

/** `testTag` del mensaje de "nunca se ha tocado esta puerta". */
const val BITACORA_VACIA_TAG: String = "pagos_bitacora_vacia"

/**
 * **La bitácora completa de un domicilio.**
 *
 * Existe porque el "⋯" del detalle se fue. Ese botón abría la pantalla legada y
 * era —sin que se notara— el único camino a "ver los N contactos". Quitarlo sin
 * darle casa a la bitácora habría borrado una función, no un botón.
 *
 * Es la pantalla donde el cobrador contesta *"¿qué ha pasado con esta gente?"*
 * antes de tocar: cada visita y cada abono, en una sola línea de tiempo, de lo
 * más reciente a lo más viejo.
 *
 * **Con botón de volver**, al revés que la lista y el detalle: a esta se llega
 * empujada desde el detalle y no se llega de ninguna otra forma, así que la
 * flecha lleva exactamente a donde el cobrador venía.
 *
 * ## El tema lo envuelve [MspThemeRevealHost], no un `MspTheme` pelado
 *
 * Porque si no, **el mismo botón se sentiría distinto en dos pantallas de esta
 * app**: el `MspThemeToggle` del reporte de cobranza anima una reveal circular y
 * el de acá haría un crossfade. Montar el host en la raíz de `:app` está
 * prohibido (Ruling BJ: `MspTheme` alrededor de `AppNavigation` repintaría la app
 * legada), así que **cada pantalla Msp instala el host sobre sí misma**, igual
 * que ya hace con el tema. El mecanismo es UNO ([MspThemeRevealHost],
 * `:core:designsystem`); lo que cambia por pantalla es qué tema envuelve. El
 * razonamiento completo —incluido por qué el `MspTheme` va en el `*Screen` y no
 * en la ruta— está en el KDoc de [ListaDeClientesScreen].
 *
 * Esta pantalla **todavía no pinta el glifo sol/luna**. El host se instala igual:
 * lo que no puede pasar es que el día que lo pinte, lo pinte con otra animación.
 *
 * ## [onVerUbicacion] — el mapa de UN contacto
 *
 * El dueño lo pidió así: *"cuando se dé click en un pago o visita se debe abrir
 * el mapa también, pero solo con la ubicación de ese pago o visita en
 * particular"*. O sea que el punto que viaja es el del renglón tocado y **no** el
 * del último cobro del cliente, que es otra puerta y otro día.
 *
 * Es un destino de `:app` por la misma razón que el del detalle: el mapa completo
 * necesita `play-services-maps`, que se declara allá y no en este módulo
 * (principio 17). Recibe el punto **y la dirección escrita** porque la hoja al pie
 * del mapa tiene que decir de qué puerta se trata; una coordenada suelta no se lo
 * dice a nadie.
 *
 * ## [onVerTicket] — la reimpresión del último cobro del día
 *
 * Sobre el cobro de HOY que además es el ÚLTIMO de esa cuenta, tocar el renglón
 * **pregunta** qué abrir —ubicación o ticket— en vez de ir derecho al mapa. En
 * cualquier otro renglón el gesto es el de siempre: si el toque cambiara de
 * significado en todos los pagos, dejaría de ser predecible por una función que
 * sólo sirve en un caso. Quién lo decide es
 * [com.example.msp_app.feature.pagos.domain.ToqueDelContacto], dominio puro.
 *
 * Es un destino de `:app` como el mapa, y la ruta ya existía
 * ([PagosRutas.ticketDePago]): la pantalla del ticket es la misma a la que llega
 * la captura de un abono. **La regla de "sólo se imprime el día del cobro" no
 * se toca aquí**: se comprueba al imprimir, dentro del ticket.
 */
@Composable
fun BitacoraScreen(
    viewModel: BitacoraViewModel,
    onAtras: () -> Unit,
    modifier: Modifier = Modifier,
    onVerUbicacion: (UbicacionDelCobro, String) -> Unit = { _, _ -> },
    onVerTicket: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Vuelve a leer al reanudarse — no al recibir un pago o una visita nuevos:
    // esta pantalla no los sabe, solo sabe que estuvo pausada. Ver su KDoc.
    RecargaAlVolver(viewModel::recargar)
    MspThemeRevealHost(
        onToggleTheme = viewModel::alternarTema,
        reducedMotion = rememberMspReducedMotion(),
        tema = { animateColors, contenido ->
            // `darkTheme` queda en su default (`appDarkTheme()` → `LocalAppDarkTheme` →
            // `ThemeController.isDarkMode`): el tema lo manda la app, no esta pantalla.
            MspTheme(animateColors = animateColors, content = contenido)
        }
    ) {
        BitacoraContent(
            state = state,
            onAtras = onAtras,
            modifier = modifier,
            onFiltrar = viewModel::filtrar,
            // La dirección que viaja al mapa es la del CLIENTE, no una del
            // contacto: la app no guarda una calle por abono ni por visita. Ver
            // el KDoc de `BitacoraCompleta.direccion`.
            onVerUbicacion = { punto ->
                state.bitacora?.let { onVerUbicacion(punto, it.direccion) }
            },
            onVerTicket = onVerTicket
        )
    }
}

/**
 * La bitácora, pura sobre [BitacoraUiState].
 *
 * **La lista va perezosa.** Un cliente de años acumula cientos de contactos
 * —cada visita y cada abono— y un `Column` con scroll los compondría todos de
 * una. Es la misma regla que el repo ya aprendió con `RangeCalculator.cycleDays`.
 */
@Composable
fun BitacoraContent(
    state: BitacoraUiState,
    onAtras: () -> Unit,
    modifier: Modifier = Modifier,
    onFiltrar: (FiltroDeContactos) -> Unit = {},
    onVerUbicacion: ((UbicacionDelCobro) -> Unit)? = null,
    onVerTicket: ((String) -> Unit)? = null
) {
    // Cuál renglón está preguntando, por su `ContactoDeCobranza.id`. En un
    // `rememberSaveable` y no en el `UiState`: el id sobrevive a la rotación y a
    // que muera el proceso —que es lo que el estado del ViewModel compra— sin
    // meterle a la bitácora un campo que no es de la bitácora sino del gesto, y
    // sin obligar a las otras dos pantallas a copiarlo en sus ViewModels. Es el
    // id y no el contacto porque un `Parcelable` de dominio no existe ni debe.
    var preguntaPor by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MspTheme.colors.background)
            // Ruling BR — después del `background`, para que el color se pinte a
            // sangre y el inset solo baje el contenido.
            .systemBarsPadding()
    ) {
        val bitacora = state.bitacora
        when {
            state.cargando -> Cargando()
            bitacora == null -> MensajeDeError(state.error, onAtras)
            else -> Contactos(
                bitacora = bitacora,
                ocultos = state.montosOcultos,
                filtro = state.filtro,
                onFiltrar = onFiltrar,
                onAtras = onAtras,
                onVerUbicacion = onVerUbicacion,
                toque = ToqueDeLaFila(
                    contactos = bitacora.contactos,
                    hoy = bitacora.hoy,
                    onPreguntar = onVerTicket?.let { { contacto -> preguntaPor = contacto.id } },
                    // Sin punto medido no hay hoja que abrir: el ticket sale
                    // derecho. Ver `ToqueDelContacto.TICKET`.
                    onVerTicket = onVerTicket?.let { ver -> { contacto -> ver(contacto.id) } }
                )
            )
        }
    }
    // FUERA del `Column`: el velo tiene que tapar la pantalla entera, incluida
    // la franja de las barras del sistema que `systemBarsPadding()` reserva.
    // Mismo montaje que `HojaDeAbono` en el detalle de cliente.
    val preguntando = state.bitacora?.contactos?.firstOrNull { it.id == preguntaPor }
    if (preguntando != null) {
        HojaDelContacto(
            onVerUbicacion = {
                preguntaPor = null
                preguntando.ubicacion?.let { punto -> onVerUbicacion?.invoke(punto) }
            },
            onVerTicket = {
                preguntaPor = null
                onVerTicket?.invoke(preguntando.id)
            },
            onCerrar = { preguntaPor = null }
        )
    }
}

@Composable
private fun Contactos(
    bitacora: BitacoraCompleta,
    ocultos: Boolean,
    filtro: FiltroDeContactos,
    onFiltrar: (FiltroDeContactos) -> Unit,
    onAtras: () -> Unit,
    onVerUbicacion: ((UbicacionDelCobro) -> Unit)?,
    toque: ToqueDeLaFila
) {
    Column(modifier = Modifier.padding(horizontal = MspTheme.spacing.md)) {
        BarraDeDetalle(onAtras = onAtras)
        Text(
            text = bitacora.nombre,
            style = MspTheme.type.greeting,
            color = MspTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(TITULO_DE_BITACORA_TAG)
        )
        Text(
            text = if (bitacora.contactos.size == 1) {
                "1 contacto"
            } else {
                "${bitacora.contactos.size} contactos"
            },
            style = MspTheme.type.subtitle,
            color = MspTheme.colors.onSurfaceMuted
        )
    }
    if (bitacora.contactos.isEmpty()) {
        SinContactos()
    } else {
        // El filtro se aplica ANTES de agrupar: si se agrupara primero,
        // quedarían encabezados de meses cuyas filas el filtro se llevó, y un
        // "AGOSTO 2026" sin nada debajo se lee como un error de carga.
        val visibles = bitacora.contactos.filter(filtro::deja)
        val grupos = GruposDeContactos.porMes(visibles)
        // Los conteos del control salen de los MISMOS contactos que se están
        // filtrando (`bitacora.contactos`, antes del `filter` de arriba), con
        // la misma regla `FiltroDeContactos.deja` — nunca de una consulta
        // aparte que pudiera discrepar.
        val conteos = FiltroDeContactos.conteos(bitacora.contactos)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MspTheme.spacing.md)
        ) {
            item(key = "filtros") {
                FiltrosDeContacto(elegido = filtro, conteos = conteos, onElegir = onFiltrar)
            }
            if (visibles.isEmpty()) {
                item(key = "vacio") { SinContactosConEseFiltro(filtro) }
            }
            grupos.forEach { grupo ->
                item(key = "grupo:${grupo.titulo}") {
                    EncabezadoDeGrupo(grupo = grupo, ocultos = ocultos)
                }
                items(
                    items = grupo.contactos,
                    // La llave es el ID DEL HECHO (`pagoId`/`visitaId`), no
                    // una combinación de sus campos visibles: dos abonos al
                    // MISMO minuto y por el MISMO importe existen de verdad
                    // —dos ventas del mismo cliente, el caso que
                    // `BitacoraDelClienteTest` prueba—, así que
                    // `fecha + etiqueta + importe` puede chocar y Compose
                    // recicla mal la fila (una de las dos filas desaparece).
                    // `ContactoDeCobranza.id` no puede chocar: es la PK de su
                    // propia fila en Room. Ver su KDoc.
                    key = { it.id }
                ) { contacto ->
                    // La fila decide sola si se puede tocar: con `ubicacion` en
                    // `null` no monta el `clickable`. Aquí no se repite.
                    ContactoEnLinea(
                        contacto = contacto,
                        ocultos = ocultos,
                        onVerUbicacion = onVerUbicacion,
                        toque = toque
                    )
                }
            }
        }
    }
}

/**
 * Hay contactos, pero ninguno pasa el filtro puesto.
 *
 * Es **otro estado** que [SinContactos] y por eso se dice distinto: ahí nunca
 * se tocó esa puerta, aquí sí y el filtro los escondió. Aplanarlos haría que el
 * cobrador creyera que nunca fue, con la pastilla "Cobros" encendida arriba.
 */
@Composable
private fun SinContactosConEseFiltro(filtro: FiltroDeContactos) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MspTheme.spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Nada en “${filtro.etiqueta}”",
            style = MspTheme.type.body,
            color = MspTheme.colors.onSurfaceMuted
        )
    }
}

/**
 * Nunca se ha tocado esta puerta.
 *
 * Se dice con palabras y no con una lista vacía: una pantalla en blanco se lee
 * como un defecto de la app, no como un hecho del cliente.
 */
@Composable
private fun SinContactos() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MspTheme.spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "sin visitas ni abonos",
            style = MspTheme.type.body,
            color = MspTheme.colors.onSurfaceMuted,
            modifier = Modifier.testTag(BITACORA_VACIA_TAG)
        )
    }
}
