package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspProgressBar
import com.example.msp_app.core.designsystem.component.MspThemeRevealHost
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion
import com.example.msp_app.feature.pagos.domain.FiltroDeContactos
import com.example.msp_app.feature.pagos.domain.GruposDeContactos
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.example.msp_app.feature.pagos.ui.components.BarraDeDetalle
import com.example.msp_app.feature.pagos.ui.components.ContactoEnLinea
import com.example.msp_app.feature.pagos.ui.components.CuadroDeEstado
import com.example.msp_app.feature.pagos.ui.components.DockDeAcciones
import com.example.msp_app.feature.pagos.ui.components.DosDatos
import com.example.msp_app.feature.pagos.ui.components.EncabezadoDeGrupo
import com.example.msp_app.feature.pagos.ui.components.EstadoEnGrande
import com.example.msp_app.feature.pagos.ui.components.FilaClaveValor
import com.example.msp_app.feature.pagos.ui.components.FiltrosDeContacto
import com.example.msp_app.feature.pagos.ui.components.HojaDelContacto
import com.example.msp_app.feature.pagos.ui.components.LabelDeSeccion
import com.example.msp_app.feature.pagos.ui.components.RecargaAlVolver
import com.example.msp_app.feature.pagos.ui.components.RitmoDeSemanas
import com.example.msp_app.feature.pagos.ui.components.SIN_DATO
import com.example.msp_app.feature.pagos.ui.components.Tarjeta
import com.example.msp_app.feature.pagos.ui.components.TarjetaDeGarantia
import com.example.msp_app.feature.pagos.ui.components.TarjetaDeLiquidacion
import com.example.msp_app.feature.pagos.ui.components.TarjetaDeSaldo
import com.example.msp_app.feature.pagos.ui.components.ToqueDeLaFila
import com.example.msp_app.feature.pagos.ui.components.VerTodos
import java.time.format.DateTimeFormatter

/** `testTag` del título de la pantalla de venta — el producto. */
const val TITULO_DE_VENTA_TAG: String = "pagos_titulo_venta"

internal val FECHA_DE_VENTA: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "d MMM yyyy",
    BUSINESS_LOCALE
)

/**
 * El destino: conecta el ViewModel con el contenido puro.
 *
 * **Provee el tema.** `:app` nunca provee `MspTheme` —monta `MspappTheme`, el
 * Material legado— y su `NavHost` no envuelve a ningún destino: sin este bloque
 * la primera lectura de `MspTheme.colors` revienta con
 * `IllegalStateException("MspTheme ausente")` al abrir la pantalla. El
 * razonamiento completo —por qué en el `*Screen` y no en la ruta ni en la raíz
 * de `:app`, y cuál es la compuerta— está en el KDoc de [ListaDeClientesScreen].
 *
 * **Y el tema lo envuelve [MspThemeRevealHost], no un `MspTheme` pelado.** Si
 * no, el mismo botón sol/luna se sentiría distinto en dos pantallas de esta app:
 * el del reporte de cobranza anima una reveal circular y el de acá haría un
 * crossfade. Montar el host en la raíz de `:app` está prohibido por la misma
 * Ruling BJ, así que **cada pantalla Msp lo instala sobre sí misma**. El
 * mecanismo es UNO (`:core:designsystem`); lo que cambia por pantalla es qué tema
 * envuelve. Esta **todavía no pinta el glifo**: el host se instala igual, para
 * que el día que lo pinte no lo pinte con otra animación.
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
 *
 * ## [onCondonar] — la condonación, ahora también desde esta pantalla
 *
 * Antes la única puerta a condonar era el detalle de venta LEGADO, alcanzable
 * con "Ver los N abonos" ([onVerAbonos]). El dueño la quiere también aquí, sin
 * depender de que haya oferta de liquidación: **condonar** (perdonar el resto
 * que sobra, sin cobrar) y **usar la liquidación** ([onUsarLiquidacion] dentro
 * de [DetalleVentaContent], sin tocar) son dos acciones de dinero distintas y
 * no se mezclan. Recibe siempre el `ventaId` —igual que [onVerAbonos]—, que es
 * el `DOCTO_CC_ACR_ID` que la captura legada (`NewForgivenessDialog`, sin
 * reescribir) necesita para prellenar el saldo.
 */
@Composable
fun DetalleVentaScreen(
    viewModel: DetalleVentaViewModel,
    onAtras: () -> Unit,
    onRegistrarAbono: (Int) -> Unit,
    onRegistrarVisita: (Int, Int?) -> Unit,
    onVerAbonos: (Int) -> Unit,
    onVerGarantia: (Int) -> Unit,
    onVerUbicacion: (UbicacionDelCobro, String) -> Unit,
    onCondonar: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onVerTicket: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detalle = state.detalle
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
        DetalleVentaContent(
            state = state,
            onAtras = onAtras,
            onRegistrarAbono = { onRegistrarAbono(viewModel.ventaId) },
            // La visita se registra sobre la PUERTA, con esta cuenta como contexto:
            // el `clienteId` sale del detalle ya cargado, que es el único lugar del
            // módulo que lo conoce sin volver a leer Room.
            onRegistrarVisita = {
                detalle?.let { onRegistrarVisita(it.clienteId, viewModel.ventaId) }
            },
            onUsarLiquidacion = { onRegistrarAbono(viewModel.ventaId) },
            onVerAbonos = { onVerAbonos(viewModel.ventaId) },
            onCondonar = { onCondonar(viewModel.ventaId) },
            linea = AccionesDeLaLinea(
                filtro = state.filtro,
                soloEstaVenta = state.soloEstaVenta,
                onFiltrar = viewModel::filtrar,
                onAlcance = viewModel::alcance
            ),
            // El flujo de garantías de `:app` está indexado por VENTA
            // (`getGuaranteeSaleById(DOCTO_CC_ID)`), no por el `EXTERNAL_ID` de la
            // garantía: se manda el crédito, que es la llave que ese flujo entiende.
            onVerGarantia = { detalle?.let { onVerGarantia(it.creditoId) } },
            // El MISMO destino que abre el mapa desde el detalle de cliente y la
            // bitácora, con el punto de ESE renglón. La venta no trae una
            // dirección propia —a diferencia del cliente, no es un domicilio—,
            // así que viaja vacía y la hoja del mapa la enseña como ausente
            // (`HojaDeLaUbicacion`, `direccion.ifBlank { SIN_DIRECCION }`).
            onVerUbicacionDelContacto = { punto -> onVerUbicacion(punto, "") },
            onVerTicket = onVerTicket,
            modifier = modifier
        )
    }
}

/**
 * El detalle de una venta: **el estado del catálogo de ocho en grande** con su
 * explicación, y abajo el historial como **ritmo + riel**.
 *
 * Composable PURO sobre [DetalleVentaUiState]. No deriva estados: consume
 * [com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo].
 */
@Composable
fun DetalleVentaContent(
    state: DetalleVentaUiState,
    onAtras: () -> Unit,
    onRegistrarAbono: () -> Unit,
    onRegistrarVisita: () -> Unit,
    onUsarLiquidacion: () -> Unit,
    onVerAbonos: () -> Unit,
    onVerGarantia: () -> Unit,
    modifier: Modifier = Modifier,
    linea: AccionesDeLaLinea = AccionesDeLaLinea(),
    onVerUbicacionDelContacto: ((UbicacionDelCobro) -> Unit)? = null,
    onVerTicket: ((String) -> Unit)? = null,
    onCondonar: () -> Unit = {}
) {
    // Cuál renglón está preguntando, por su `ContactoDeCobranza.id`. Ver el
    // comentario gemelo en `BitacoraContent` para por qué vive aquí y no en el
    // `UiState`.
    var preguntaPor by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MspTheme.colors.background)
            // Ruling BR — DESPUÉS del `background`, para que el color siga pintándose a
            // sangre bajo la barra de estado y el inset solo baje el CONTENIDO. Sin esto la
            // app corre `enableEdgeToEdge()` y la ventana `StatusBar` del sistema queda
            // ENCIMA del encabezado y se come sus taps (medido: 36 de 168 px útiles en el
            // "atrás"). La compuerta es `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest`.
            // `systemBars` y no `statusBars`: el mismo argumento vale ABAJO. Con
            // `enableEdgeToEdge()` la barra de navegación también queda encima, y el pie de
            // la pantalla se pintaba detrás de los botones de Android (reportado en vidrio,
            // SM-A256E). El fondo sigue a sangre porque este padding va después del
            // `background`; lo único que se corre es el CONTENIDO.
            .systemBarsPadding()
    ) {
        val detalle = state.detalle
        Box(modifier = Modifier.weight(1f)) {
            when {
                state.cargando -> Cargando()
                detalle == null -> MensajeDeError(state.error, onAtras)
                else -> CuerpoDeLaVenta(
                    detalle = detalle,
                    onAtras = onAtras,
                    onUsarLiquidacion = onUsarLiquidacion,
                    onVerAbonos = onVerAbonos,
                    onVerGarantia = onVerGarantia,
                    linea = linea,
                    onVerUbicacionDelContacto = onVerUbicacionDelContacto,
                    toque = ToqueDeLaFila(
                        // La línea del CLIENTE entero y no `delAlcance`: "el
                        // último de la cuenta" se decide sobre todo lo que hay,
                        // no sobre lo que las pastillas dejaron ver. Si no,
                        // filtrar por "Visitas" volvería "último" a un cobro que
                        // no lo es.
                        contactos = detalle.contactos,
                        hoy = detalle.hoy,
                        onPreguntar = onVerTicket?.let {
                            { contacto -> preguntaPor = contacto.id }
                        },
                        // Sin punto medido no hay hoja que abrir: el ticket
                        // sale derecho. Ver `ToqueDelContacto.TICKET`.
                        onVerTicket = onVerTicket?.let { ver ->
                            { contacto -> ver(contacto.id) }
                        }
                    )
                )
            }
        }
        if (detalle != null) {
            DockDeAcciones(
                textoPrimario = "Abonar " + formatMoneyMxn(detalle.parcialidad.amount),
                onPrimario = onRegistrarAbono,
                onVisita = onRegistrarVisita,
                // El "⋯" que llevaba a la pantalla legada ya no existe: el
                // dueño no lo quiere ver más. "Ver los N abonos", más abajo en
                // LineaDeLaVenta, sigue abriendo esa misma pantalla — es una
                // puerta distinta, que él no pidió cerrar.
                //
                // Sin `notas`: a diferencia del detalle de cliente, la nota de
                // esta pantalla es de sólo lectura (`TarjetaDeNotaDeLaVenta`,
                // al fondo) y no tiene editor que abrir desde el dock — no hay
                // a dónde llevar un botón aquí.
                //
                // `condonar` SÍ va, y siempre: no depende de `detalle.liquidacion`
                // —condonar y usar la liquidación son dos acciones de dinero
                // distintas— ni de "Ver los N abonos", que sólo aparece si ya
                // hubo pagos.
                condonar = onCondonar
            )
        }
    }
    // FUERA del `Column`: el velo tiene que tapar la pantalla entera, incluidos
    // el dock y la franja que `systemBarsPadding()` reserva. Mismo montaje que
    // `HojaDeAbono` en el detalle de cliente.
    val preguntando = state.detalle?.contactos?.firstOrNull { it.id == preguntaPor }
    if (preguntando != null) {
        HojaDelContacto(
            onVerUbicacion = {
                preguntaPor = null
                preguntando.ubicacion?.let { punto -> onVerUbicacionDelContacto?.invoke(punto) }
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
private fun CuerpoDeLaVenta(
    detalle: DetalleVenta,
    onAtras: () -> Unit,
    onUsarLiquidacion: () -> Unit,
    onVerAbonos: () -> Unit,
    onVerGarantia: () -> Unit,
    linea: AccionesDeLaLinea,
    onVerUbicacionDelContacto: ((UbicacionDelCobro) -> Unit)? = null,
    toque: ToqueDeLaFila = ToqueDeLaFila()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MspTheme.spacing.md)
    ) {
        BarraDeDetalle(onAtras = onAtras)
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            CuadroDeEstado(estadoVisualDe(detalle.estado), lado = 22.dp)
            Text(
                text = detalle.clienteNombre,
                style = MspTheme.type.subtitle,
                color = MspTheme.colors.onSurfaceMuted
            )
        }
        Spacer(Modifier.height(MspTheme.spacing.sm))
        Text(
            text = detalle.titulo,
            style = MspTheme.type.detailTitle,
            color = MspTheme.colors.onSurface,
            modifier = Modifier.testTag(TITULO_DE_VENTA_TAG)
        )
        Text(
            text = listOfNotNull(
                detalle.folio,
                "Crédito ${detalle.creditoId}",
                detalle.fechaVenta?.let { FECHA_DE_VENTA.format(it) }
            ).joinToString(" · "),
            style = MspTheme.type.subtitle,
            color = MspTheme.colors.onSurfaceMuted
        )

        Spacer(Modifier.height(MspTheme.spacing.md))
        EstadoEnGrande(detalle.estado)

        Spacer(Modifier.height(MspTheme.spacing.md))
        TarjetaDeSaldo(
            label = "saldo de esta venta",
            monto = detalle.saldo,
            pie = { PieDeLaVenta(detalle) }
        )

        detalle.liquidacion?.let { liquidacion ->
            LabelDeSeccion("liquidación de esta venta")
            TarjetaDeLiquidacion(
                label = "hoy liquida con",
                liquidacion = liquidacion,
                onUsar = onUsarLiquidacion
            )
        }

        LabelDeSeccion("ritmo · últimas 12 semanas")
        RitmoDeSemanas(detalle.historial)

        LabelDeSeccion("lo que ha pasado")
        LineaDeLaVenta(
            detalle = detalle,
            linea = linea,
            onVerAbonos = onVerAbonos,
            onVerUbicacion = onVerUbicacionDelContacto,
            toque = toque
        )

        if (detalle.productos.isNotEmpty()) {
            LabelDeSeccion("productos")
            detalle.productos.forEach { producto ->
                FilaClaveValor(
                    clave = producto.nombre,
                    valor = producto.importe?.let { formatMoneyMxn(it.amount) } ?: SIN_DATO
                )
            }
        }

        detalle.garantia?.let { garantia ->
            LabelDeSeccion("garantía")
            TarjetaDeGarantia(garantia = garantia, onVerGarantia = onVerGarantia)
        }

        LabelDeSeccion("datos de la venta")
        DatosDeLaVenta(detalle)

        // AL FONDO, como la ficha del detalle de cliente: no empuja un solo dp
        // del dinero, que es por lo que el cobrador abrió la pantalla. Ver el
        // KDoc de TarjetaDeNotaDeLaVenta para por qué esto NO es la ficha del
        // cliente reusada tal cual.
        TarjetaDeNotaDeLaVenta(nota = detalle.nota)
        Spacer(Modifier.height(MspTheme.spacing.lg))
    }
}

/**
 * **La nota de ESTA venta**, tal como la trae el servidor — de sólo lectura.
 *
 * El dueño pidió que el detalle de venta tuviera notas, "como en detalles de
 * cliente". Ahí viven DOS notas distintas ([SeccionDeLaFicha]): la ficha del
 * cliente —catálogo cerrado, nota libre, editable, local— y la que la venta
 * trae en su columna `NOTAS`, sólo de lectura. Esta pantalla es de una CUENTA,
 * así que lo que el cobrador espera ver aquí es lo que aplica a esa cuenta: la
 * nota de la venta, no la ficha del domicilio entero.
 *
 * La ficha no se trae a esta pantalla a propósito — ver el KDoc de
 * [com.example.msp_app.feature.pagos.domain.model.DetalleVenta.nota] y el de
 * `CargarDetalleCliente` sobre por qué esa lectura no entra a
 * `ReunirCobranzaDelCliente`, que este detalle sí comparte: traerla sería una
 * consulta de más en una pantalla que no la usa, y editarla desde aquí
 * confundiría el dato de la cuenta con el del domicilio.
 *
 * Reusa [Tarjeta] y [LabelDeSeccion] — las mismas piezas con las que
 * [SeccionDeLaFicha] arma su propia sección de notas — y no ese composable
 * completo, que exige una [com.example.msp_app.feature.pagos.domain.model.FichaDelCliente]
 * que esta pantalla no tiene y no debe simular.
 *
 * Sin nota es sin nota: se dice, no se rellena — mismo patrón que el "Sin
 * notas" del detalle de cliente.
 */
@Composable
internal fun TarjetaDeNotaDeLaVenta(nota: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        LabelDeSeccion("Notas")
        Tarjeta(modifier = Modifier.testTag(NOTA_DE_LA_VENTA_TAG)) {
            Text(
                text = nota ?: "Sin notas de esta venta",
                style = MspTheme.type.body,
                color = if (nota == null) {
                    MspTheme.colors.onSurfaceMuted
                } else {
                    MspTheme.colors.onSurface
                }
            )
        }
    }
}

/** `testTag` de la tarjeta de notas del detalle de venta. */
const val NOTA_DE_LA_VENTA_TAG: String = "pagos_venta_nota"

/**
 * El pie de la tarjeta de saldo: la barra de avance y **dos** datos.
 *
 * **Sin el conteo de abonos**, que el dueño pidió fuera y que aquí no se
 * reemplaza por nada: lo abonado en dinero ya tiene su renglón en "datos de la
 * venta", unos dedos más abajo en esta misma pantalla, y repetirlo arriba sería
 * decir dos veces lo mismo. La barra que va justo encima sigue diciendo el
 * progreso, que es lo que el conteo aportaba, y lo dice sin números.
 *
 * Con dos celdas usa [DosDatos] y no [TresDatos] con un hueco: una tercera
 * celda vacía inventa un espacio donde antes había un dato, y una sola cifra
 * centrada en el ancho de tres se lee como un error de layout. Las dos que
 * quedan siguen repartiéndose el ancho completo y apilándose a `GRANDE` y
 * `MUY_GRANDE` por el mismo motivo de siempre.
 */
@Composable
private fun PieDeLaVenta(detalle: DetalleVenta) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MspProgressBar(
            progress = detalle.avance,
            height = 6.dp,
            fillColor = MspTheme.colors.heroProgressFill,
            trackColor = MspTheme.colors.progressTrack
        )
        Spacer(Modifier.height(MspTheme.spacing.md))
        DosDatos(
            primero = { celda ->
                DatoDelPie("parcialidad", formatMoneyMxn(detalle.parcialidad.amount), celda)
            },
            segundo = { celda ->
                DatoDelPie("frecuencia", detalle.frecuencia.ifBlank { SIN_DATO }, celda)
            }
        )
    }
}

@Composable
private fun DatoDelPie(clave: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            // `.pgrid .k` del mock: `10px/700`, `.11em`, `uppercase`.
            text = clave.uppercase(BUSINESS_LOCALE),
            style = MspTheme.type.overline,
            color = MspTheme.colors.onSurfaceMuted
        )
        Spacer(Modifier.height(MspTheme.spacing.xs))
        Text(
            text = valor,
            style = MspTheme.type.metricSmall,
            color = MspTheme.colors.onSurface
        )
    }
}

/**
 * Las cuatro cosas que la línea de contactos de esta pantalla necesita, juntas.
 *
 * En una bolsa y no en cuatro parámetros sueltos por lo mismo que
 * `AccionesDeLaFicha` en el detalle de cliente: [DetalleVentaContent] ya recibe
 * muchas lambdas y detekt corta ahí.
 */
@Immutable
data class AccionesDeLaLinea(
    val filtro: FiltroDeContactos = FiltroDeContactos.TODOS,
    val soloEstaVenta: Boolean = true,
    val onFiltrar: (FiltroDeContactos) -> Unit = {},
    val onAlcance: (Boolean) -> Unit = {}
)

/**
 * **Lo que ha pasado con esta cuenta**, dentro de la línea del cliente entero.
 *
 * Antes esta sección decía "pagos" y listaba **sólo los abonos de esta venta**.
 * El dueño la quiso como la del detalle de cliente y con más contexto, así que
 * ahora es la misma línea de tiempo: cobros y visitas, agrupados por mes, con
 * el subtotal cobrado de cada uno.
 *
 * ## Dos ejes, y son distintos a propósito
 *
 * - **El alcance** —*"Esta venta"* / *"Todo el cliente"*— contesta *de quién*
 *   es lo que veo. Arranca angostado: la pantalla se llama detalle de VENTA.
 * - **El filtro** —Cobros, Visitas, Promesas— contesta *qué* de eso veo.
 *
 * Se aplican en ese orden y no al revés: angostar después de filtrar daría los
 * mismos renglones pero dejaría encabezados de meses vacíos.
 *
 * Con el alcance abierto, lo de ESTA venta sigue distinguiéndose con la barra
 * del borde de [ContactoEnLinea]: ensanchar no puede significar perder de vista
 * cuál era la cuenta que se abrió.
 *
 * ## `internal` y no `private`, sólo para que tenga golden propio
 *
 * La sección vive muy por debajo del pliegue —después del saldo, el ritmo y la
 * liquidación— así que los `pagos_venta_*` de pantalla completa nunca la
 * retratan; es el mismo motivo por el que la garantía y el historial tienen los
 * suyos. Sin esa foto, lo que Robolectric no puede cobrar —que la pastilla
 * apagada se vea como control y que *"Todo el cliente"* quepa entero— no lo
 * cobra nadie.
 */
@Composable
internal fun LineaDeLaVenta(
    detalle: DetalleVenta,
    linea: AccionesDeLaLinea,
    onVerAbonos: () -> Unit,
    onVerUbicacion: ((UbicacionDelCobro) -> Unit)? = null,
    toque: ToqueDeLaFila = ToqueDeLaFila()
) {
    val delAlcance = if (linea.soloEstaVenta) {
        detalle.contactos.filter { it.ventaId == detalle.ventaId }
    } else {
        detalle.contactos
    }
    val visibles = delAlcance.filter(linea.filtro::deja)
    // Los conteos salen de `delAlcance` y no de `detalle.contactos`: con
    // "Esta venta" puesto, un conteo sobre TODO el cliente contaría filas que
    // el alcance ya escondió, y "Cobros 3" enseñaría sólo 1.
    val conteos = FiltroDeContactos.conteos(delAlcance)
    AlcanceDeLaLinea(soloEstaVenta = linea.soloEstaVenta, onAlcance = linea.onAlcance)
    FiltrosDeContacto(elegido = linea.filtro, conteos = conteos, onElegir = linea.onFiltrar)
    if (visibles.isEmpty()) {
        Tarjeta {
            Text(
                text = if (linea.soloEstaVenta) {
                    "Sin movimientos en esta cuenta"
                } else {
                    "Nada en “${linea.filtro.etiqueta}”"
                },
                style = MspTheme.type.body,
                color = MspTheme.colors.onSurfaceMuted
            )
        }
    } else {
        GruposDeContactos.porMes(visibles).forEach { grupo ->
            EncabezadoDeGrupo(grupo)
            grupo.contactos.forEach { contacto ->
                ContactoEnLinea(
                    contacto = contacto,
                    deEstaVenta = contacto.ventaId == detalle.ventaId,
                    onVerUbicacion = onVerUbicacion,
                    toque = toque
                )
            }
        }
    }
    // FUERA de la rama de arriba a propósito: este enlace lleva a la pantalla
    // de abonos y **no depende del filtro puesto**. Colgado del `else` se iba
    // en cuanto alguien filtraba por "Visitas" —o cuando la línea venía
    // vacía—, y entonces la única puerta a los abonos desaparecía sin que
    // nada la hubiera cerrado.
    if (detalle.historial.totalPagos > 0) {
        Spacer(Modifier.height(MspTheme.spacing.sm))
        VerTodos("Ver los ${detalle.historial.totalPagos} abonos", onVerAbonos)
    }
}

/**
 * El interruptor de alcance: de quién es lo que se está viendo.
 *
 * ## Se desplaza a lo ancho
 *
 * El defecto que esto cierra: la fila era `fillMaxWidth()` **sin**
 * `horizontalScroll`. Medido a 360 dp, a `MUY_GRANDE` la segunda pastilla va
 * de 164 a 344 dp — toca el borde. Sin desplazamiento no hay a dónde ir, así
 * que la etiqueta quedaba cortada **para siempre** en *"Todo el"*, y el
 * cobrador leía una opción que no existe.
 *
 * **Ya no es "igual que la fila de filtros de abajo".** Hasta la Ronda de
 * arreglo 1 de la Task 4, `FiltrosDeContacto` también rodaba a escala grande
 * — y ESE scroll era el defecto que sacaba su borde y sus márgenes de la
 * pantalla (ver el KDoc de `ControlSegmentado`). Esta pastilla de dos
 * opciones es un caso distinto: sólo dos etiquetas cortas, nunca necesita
 * rejilla, y el scroll aquí sigue siendo la solución correcta.
 *
 * ## Y el texto elide en vez de cortar a media palabra
 *
 * `maxLines = 1` sin `overflow` cae al default `Clip`: corta al píxel, sin
 * elipsis, de modo que una etiqueta mutilada se ve igual que una etiqueta
 * completa. Con `Ellipsis` el recorte **se anuncia**. Son las dos mitades del
 * mismo defecto y por eso van juntas: el scroll da a dónde ir, la elipsis avisa
 * cuando aun así no cupo.
 *
 * ## La pastilla apagada usa `surface2`, no transparente
 *
 * Apagada quedaba texto pelón sobre el fondo, justo encima de una fila de
 * filtros cuyas pastillas apagadas sí traen `surface2`: dos filas de controles
 * pegadas hablando dos idiomas, y la de arriba sin decir que era tocable. Lo
 * que separa encendido de apagado sigue siendo el color de marca, no la
 * existencia de la pastilla.
 */
@Composable
private fun AlcanceDeLaLinea(soloEstaVenta: Boolean, onAlcance: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = MspTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        listOf(true to "Esta venta", false to "Todo el cliente").forEach { (solo, etiqueta) ->
            val activo = solo == soloEstaVenta
            Surface(
                onClick = { onAlcance(solo) },
                shape = MspTheme.shapes.chip,
                color = if (activo) MspTheme.colors.brandTint else MspTheme.colors.surface2,
                modifier = Modifier
                    .heightIn(min = ALTO_DEL_ALCANCE)
                    .testTag(ALCANCE_TAG + solo)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = MspTheme.spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = etiqueta,
                        style = MspTheme.type.captionStrong,
                        color = if (activo) {
                            MspTheme.colors.brand
                        } else {
                            MspTheme.colors.onSurfaceMuted
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Prefijo del `testTag` de cada opción de alcance, más si angosta o no. */
const val ALCANCE_TAG: String = "pagos_venta_alcance_"

/**
 * Alto mínimo de una pastilla de alcance.
 *
 * 50 dp por lo mismo que el segmento de filtro: es el piso tocable del repo y
 * no se baja por gusto visual. Ver `ALTO_TOCABLE_DEL_SEGMENTO` en
 * `ControlSegmentado`.
 */
private val ALTO_DEL_ALCANCE = 50.dp
