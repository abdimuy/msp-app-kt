@file:Suppress(
    "TooManyFunctions"
) // una funcion por hoja de la pantalla; fusionarlas rehace el muro que el rediseño partio.

package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.designsystem.component.MspPrivacyEyeToggle
import com.example.msp_app.core.designsystem.component.MspThemeRevealHost
import com.example.msp_app.core.designsystem.component.MspThemeToggle
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion
import com.example.msp_app.feature.pagos.domain.CuentaDelAbono
import com.example.msp_app.feature.pagos.domain.GruposDeContactos
import com.example.msp_app.feature.pagos.domain.model.DetalleCliente
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.example.msp_app.feature.pagos.ui.components.AccionDeNotas
import com.example.msp_app.feature.pagos.ui.components.AccionesDelCliente
import com.example.msp_app.feature.pagos.ui.components.AfordanteDeLaFicha
import com.example.msp_app.feature.pagos.ui.components.BloqueDeIdentidad
import com.example.msp_app.feature.pagos.ui.components.CifrasDelCliente
import com.example.msp_app.feature.pagos.ui.components.ContactoEnLinea
import com.example.msp_app.feature.pagos.ui.components.CuadroDeLaPuerta
import com.example.msp_app.feature.pagos.ui.components.DatosDeLaPuerta
import com.example.msp_app.feature.pagos.ui.components.DockDeAcciones
import com.example.msp_app.feature.pagos.ui.components.EncabezadoDeGrupo
import com.example.msp_app.feature.pagos.ui.components.HojaContinua
import com.example.msp_app.feature.pagos.ui.components.HojaDeAbono
import com.example.msp_app.feature.pagos.ui.components.HojaDeLaFicha
import com.example.msp_app.feature.pagos.ui.components.ProductoDelCliente
import com.example.msp_app.feature.pagos.ui.components.RitmoDelCliente
import com.example.msp_app.feature.pagos.ui.components.SaldoDelCliente
import com.example.msp_app.feature.pagos.ui.components.SeccionDeHoja
import com.example.msp_app.feature.pagos.ui.components.SeccionDeLaFicha
import com.example.msp_app.feature.pagos.ui.components.Separador
import com.example.msp_app.feature.pagos.ui.components.TituloDeHoja
import com.example.msp_app.feature.pagos.ui.components.VentaEnLaHoja
import com.example.msp_app.feature.pagos.ui.components.VerLosContactos
import com.example.msp_app.feature.pagos.ui.components.VerTodos

/** `testTag` del título de la pantalla — el nombre del cliente. */
const val TITULO_DE_CLIENTE_TAG: String = "pagos_titulo_cliente"

/**
 * El destino: conecta el ViewModel con el contenido puro.
 *
 * ## A qué cuenta va el abono (arreglo de esta pasada)
 *
 * Antes esto resolvía la cuenta con `cuentaQueEncabeza`, o sea
 * `ventas.firstOrNull()`: **el dinero entraba a la primera cuenta de la lista sin
 * decirlo en ninguna parte**. Con dos cuentas, el abono podía caer en la
 * equivocada y nadie se enteraba hasta que cuadraban. No era una decisión de
 * diseño, era un defecto.
 *
 * Ahora el dock no elige: pide la cuenta. Con una sola venta va directo —el flujo
 * es idéntico al de siempre—, y con dos o más abre la hoja que pregunta a cuál
 * entra, con la de atrasos preseleccionada. Ver `HojaDeAbono`.
 *
 * **Provee el tema.** `:app` nunca provee `MspTheme` —monta `MspappTheme`, el
 * Material legado— y su `NavHost` no envuelve a ningún destino: sin este bloque
 * la primera lectura de `MspTheme.colors` revienta con
 * `IllegalStateException("MspTheme ausente")` al abrir la pantalla. El
 * razonamiento completo está en el KDoc de [ListaDeClientesScreen].
 *
 * ## Y lo provee por [MspThemeRevealHost], no por `MspTheme` pelado
 *
 * **Éste era el defecto.** El dueño tocó el sol/luna acá y el tema cambió en
 * seco, sin la reveal circular que sí anima en la lista: el mismo control se
 * sentía distinto en dos pantallas de la misma app, que es justo la incoherencia
 * que el KDoc de [MspThemeRevealHost] documenta y que ninguna captura muestra.
 * La lista instalaba el host (`ListaDeClientesScreen.kt:106`) y esta pantalla no,
 * aunque pinta el MISMO [MspThemeToggle] dos renglones abajo.
 *
 * El mecanismo es UNO y vive en `:core:designsystem`; lo que cambia por pantalla
 * es qué tema envuelve. La compuerta que impide la próxima omisión es
 * `CadaPantallaConTemaAnimaElCambioTest` de `:app`, que barre las fuentes de
 * todos los módulos y no tiene lista de nombres.
 *
 * ## El suelo del cuadro de ubicación entra por [suelo]
 *
 * Se cablea acá y no dentro del contenido, y esa línea es la que mantiene
 * deterministas los goldens: quien fotografía es [DetalleClienteContent], que se
 * queda con el respaldo dibujado. Un mapa de verdad trae red y bitmaps, y
 * ninguna de las dos cosas entra a `captureRoboImage`.
 *
 * El punto que recibe es el MISMO `ultimoCobroAqui` que decide si hay pin: un
 * mapa centrado en cualquier otra cosa diría "es aquí" sobre una puerta que
 * nadie midió.
 *
 * ## Un renglón de "últimos contactos" también abre el mapa
 *
 * El dueño lo pidió así: *"cuando se dé click en un pago o visita se debe abrir
 * el mapa también, pero solo con la ubicación de ese pago o visita en
 * particular"*. Un renglón CON punto abre el mapa en el punto de ESE contacto;
 * uno sin punto no se puede tocar.
 *
 * Reusa [onVerUbicacion] —el mismo destino y los mismos dos argumentos— en vez de
 * pedirle a `:app` un callback nuevo: lo que cambia entre el cuadro y el renglón
 * es el punto, no la operación. La dirección que viaja sigue siendo la del
 * cliente: es la puerta, y el abono se cobró y la visita se hizo ahí.
 */
@Composable
fun DetalleClienteScreen(
    viewModel: DetalleClienteViewModel,
    onAtras: () -> Unit,
    onAbrirVenta: (Int) -> Unit,
    onRegistrarAbono: (Int) -> Unit,
    onRegistrarVisita: (Int, Int?) -> Unit,
    onVerContactos: (Int) -> Unit,
    onVerUbicacion: (UbicacionDelCobro, String) -> Unit,
    modifier: Modifier = Modifier,
    suelo: (@Composable (UbicacionDelCobro?, onTocar: () -> Unit) -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MspThemeRevealHost(
        onToggleTheme = viewModel::alternarTema,
        // Las DOS señales de movimiento reducido (principio 13): la de
        // accesibilidad del sistema operativo **o** la preferencia propia de la
        // app. Un solo criterio para las ocho pantallas — ver su KDoc.
        reducedMotion = rememberMspReducedMotion(),
        tema = { animateColors, contenido ->
            MspTheme(animateColors = animateColors, content = contenido)
        }
    ) {
        DetalleClienteContent(
            state = state,
            onAtras = onAtras,
            onAbrirVenta = onAbrirVenta,
            // El ViewModel contesta la cuenta cuando hay una sola; con dos o más
            // abre la hoja y contesta `null`, así que no se navega todavía.
            onRegistrarAbono = { viewModel.registrarAbono()?.let(onRegistrarAbono) },
            abono = AccionesDelAbono(
                onElegir = viewModel::elegirCuenta,
                onContinuar = { viewModel.confirmarCuenta()?.let(onRegistrarAbono) },
                onCerrar = viewModel::cerrarEleccionDeCuenta
            ),
            onRegistrarVisita = { onRegistrarVisita(viewModel.clienteId, null) },
            onVerContactos = { onVerContactos(viewModel.clienteId) },
            // Las tres acciones que salen de la app las resuelve el ViewModel
            // por `AccionesExternasPort`, no la navegación: `:app` no tiene por
            // qué saber cómo se marca un teléfono, y el fallo de abrirlas tiene
            // que quedar reportado (norma de errores).
            contacto = AccionesDeContacto(
                onLlamar = viewModel::marcar,
                onWhatsApp = viewModel::escribirPorWhatsApp,
                onComoLlegar = viewModel::comoLlegar
            ),
            onAlternarTema = viewModel::alternarTema,
            onAlternarPrivacidad = viewModel::alternarPrivacidad,
            fichaDelCliente = AccionesDeLaFicha(
                onEditar = viewModel::editarFicha,
                onCerrar = viewModel::cerrarFicha,
                onSenal = viewModel::alternarSenal,
                onNota = viewModel::escribirNota,
                onGuardar = viewModel::guardarFicha
            ),
            modifier = modifier,
            onVerUbicacion = {
                val detalle = state.detalle
                detalle?.ultimoCobroAqui?.let { punto ->
                    onVerUbicacion(punto, detalle.direccion)
                }
            },
            // El MISMO callback del cuadro, con otro punto. No hace falta un
            // destino nuevo ni un miembro nuevo en `UbicacionEnElDetalle`: "abrir
            // el mapa en este punto, con esta dirección" es UNA operación, y
            // duplicarla dejaría dos lambdas que `:app` tendría que cablear a la
            // misma ruta (principio 5).
            onVerUbicacionDelContacto = { punto ->
                state.detalle?.let { onVerUbicacion(punto, it.direccion) }
            },
            suelo = suelo?.let { puesto ->
                { tocar -> puesto(state.detalle?.ultimoCobroAqui, tocar) }
            }
        )
    }
}

/**
 * Las tres acciones que salen de la app: llamar, WhatsApp y cómo llegar.
 *
 * Van juntas por la misma razón que [AccionesDeLaFicha]: el contenido ya recibe
 * suficientes lambdas y detekt corta en siete (`LongParameterList`).
 */
@Immutable
data class AccionesDeContacto(
    val onLlamar: () -> Unit = {},
    val onWhatsApp: () -> Unit = {},
    val onComoLlegar: () -> Unit = {}
)

/**
 * El detalle de cliente, **hoja continua** (variante B del lienzo).
 *
 * ## Qué cambió y por qué
 *
 * La versión anterior era una pila de siete tarjetas sueltas, con el racimo de
 * estados pegado al nombre y un "⋯" que abría la pantalla legada. El dueño la
 * rechazó con tres frases: *"se ve medio rara"*, *"quiero tenerla más completa"*,
 * *"que se vea como una app cara"*. Las tres tienen traducción concreta:
 *
 *  - **"Medio rara"** era el ritmo vertical. Siete tarjetas con el mismo aire
 *    entre todo se leen como cosas sueltas. Ahora son cinco hojas, y dentro de
 *    cada una el aire antes de un separador es mayor que el aire entre dos
 *    renglones del mismo bloque — que es lo que hace que un bloque se lea como
 *    un bloque.
 *  - **"Más completa"** eran datos que la base YA tenía y nadie pintaba:
 *    `IMPORTE_PAGO_PROMEDIO` ("suele dar"), `FECHA_ULT_PAGO`,
 *    `NUM_PAGOS_ATRASADOS`, el día de la ruta y los productos con su importe.
 *  - **"App cara"** son las acciones como iconos. El cuadro de mapa que también
 *    respondía a esa frase se fue con `:core:mapas`, y "cómo llegar" quedó como
 *    la cuarta de esas acciones.
 *
 * ## Sin botón de volver, y el nombre como título
 *
 * Igual que la lista: el nombre del cliente ocupa el renglón completo y a su
 * derecha van el ojo de privacidad y el cambio de tema. La flecha de atrás se
 * fue porque el gesto del sistema ya vuelve y la franja que ocupaba vale más
 * como nombre.
 *
 * ## Sin "⋯" y sin "hoy liquida todo con"
 *
 * El "⋯" abría la pantalla legada y era el único camino a ella desde aquí. Lo que
 * sí llevaba —"ver los N contactos"— tiene ahora su propio destino, así que
 * quitarlo no borró ninguna función.
 *
 * "Hoy liquida todo con" salió porque **liquidar es por venta**, contra un
 * `DOCTO_CC_ACR_ID`: una suma a nivel cliente no corresponde a ninguna operación
 * que la app pueda ejecutar. Vive dentro del detalle de cada venta.
 *
 * Composable PURO sobre [DetalleClienteUiState]: no lee puertos, no deriva
 * estados y no emite telemetría.
 */
@Composable
fun DetalleClienteContent(
    state: DetalleClienteUiState,
    onAtras: () -> Unit,
    onAbrirVenta: (Int) -> Unit,
    onRegistrarAbono: () -> Unit,
    onRegistrarVisita: () -> Unit,
    onVerContactos: () -> Unit,
    onAlternarTema: () -> Unit,
    onAlternarPrivacidad: () -> Unit,
    modifier: Modifier = Modifier,
    contacto: AccionesDeContacto = AccionesDeContacto(),
    abono: AccionesDelAbono = AccionesDelAbono(),
    fichaDelCliente: AccionesDeLaFicha = AccionesDeLaFicha(),
    onVerUbicacion: (() -> Unit)? = null,
    onVerUbicacionDelContacto: ((UbicacionDelCobro) -> Unit)? = null,
    suelo: (@Composable (onTocar: () -> Unit) -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MspTheme.colors.background)
            // Ruling BR — DESPUÉS del `background`, para que el color siga pintándose a
            // sangre bajo la barra de estado y el inset solo baje el CONTENIDO. Sin esto la
            // app corre `enableEdgeToEdge()` y la ventana `StatusBar` del sistema queda
            // ENCIMA del encabezado y se come sus taps. La compuerta es
            // `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest`.
            .systemBarsPadding()
    ) {
        val detalle = state.detalle
        Box(modifier = Modifier.weight(1f)) {
            when {
                state.cargando -> Cargando()
                detalle == null -> MensajeDeError(state.error, onAtras)
                else -> CuerpoDelCliente(
                    detalle = detalle,
                    ocultos = state.montosOcultos,
                    temaOscuro = state.temaOscuro,
                    onAbrirVenta = onAbrirVenta,
                    onVerContactos = onVerContactos,
                    onAlternarTema = onAlternarTema,
                    onAlternarPrivacidad = onAlternarPrivacidad,
                    contacto = contacto,
                    onEditarFicha = fichaDelCliente.onEditar,
                    onVerUbicacion = onVerUbicacion,
                    onVerUbicacionDelContacto = onVerUbicacionDelContacto,
                    suelo = suelo
                )
            }
        }
        if (detalle != null) {
            DockDeAcciones(
                textoPrimario = "Registrar abono",
                onPrimario = onRegistrarAbono,
                onVisita = onRegistrarVisita,
                // El tercer espacio del dock, que el rediseño dejó diseñado y
                // vacío. Las Notas viven aquí y no en la fila de iconos porque
                // aquí se ven sin desplazar y pueden llevar el distintivo: hoy
                // no se puede saber si una puerta tiene algo anotado sin abrirla.
                //
                // Con la ficha ILEGIBLE el botón sigue abriendo: `HojaDeLaFicha`
                // no monta nada si el estado no trae edición, así que el camino
                // ya está cerrado donde tiene que estarlo, y apagarlo aquí
                // también dejaría un control muerto sin decir por qué.
                notas = AccionDeNotas(
                    onAbrir = fichaDelCliente.onEditar,
                    conContenido = detalle.ficha?.vacia == false,
                    advierte = detalle.ficha?.advertencias?.isNotEmpty() == true
                )
            )
        }
    }
    HojaDeLaFicha(
        edicion = state.edicionDeLaFicha,
        onCerrar = fichaDelCliente.onCerrar,
        onSenal = fichaDelCliente.onSenal,
        onNota = fichaDelCliente.onNota,
        onGuardar = fichaDelCliente.onGuardar
    )
    val eleccion = state.eleccionDeCuenta
    if (eleccion != null && state.detalle != null) {
        HojaDeAbono(
            cuentas = CuentaDelAbono.cobrables(state.detalle.ventas),
            elegida = eleccion.elegida,
            onElegir = abono.onElegir,
            onContinuar = abono.onContinuar,
            onCerrar = abono.onCerrar,
            ocultos = state.montosOcultos
        )
    }
}

/**
 * Las tres acciones de la hoja "¿a cuál cuenta?".
 *
 * Juntas por la misma razón que [AccionesDeContacto] y [AccionesDeLaFicha]: el
 * contenido ya recibe muchas lambdas y detekt corta en siete.
 */
@Immutable
data class AccionesDelAbono(
    val onElegir: (Int) -> Unit = {},
    val onContinuar: () -> Unit = {},
    val onCerrar: () -> Unit = {}
)

/**
 * Las cinco acciones de la ficha, juntas.
 *
 * Van en un objeto y no en cinco parámetros sueltos porque
 * [DetalleClienteContent] ya recibe muchas lambdas y detekt corta ahí
 * (`LongParameterList`); además así la pantalla del golden pasa un default
 * inerte en vez de repetir cinco `{}`. Mismo criterio que
 * `AccionesDeLaVisita` en `:feature:visitas`.
 */
@Immutable
data class AccionesDeLaFicha(
    val onEditar: () -> Unit = {},
    val onCerrar: () -> Unit = {},
    val onSenal: (SenalDeFicha) -> Unit = {},
    val onNota: (String) -> Unit = {},
    val onGuardar: () -> Unit = {}
)

@Composable
private fun CuerpoDelCliente(
    detalle: DetalleCliente,
    ocultos: Boolean,
    temaOscuro: Boolean,
    onAbrirVenta: (Int) -> Unit,
    onVerContactos: () -> Unit,
    onAlternarTema: () -> Unit,
    onAlternarPrivacidad: () -> Unit,
    contacto: AccionesDeContacto,
    onEditarFicha: () -> Unit,
    onVerUbicacion: (() -> Unit)?,
    onVerUbicacionDelContacto: ((UbicacionDelCobro) -> Unit)?,
    suelo: (@Composable (onTocar: () -> Unit) -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MspTheme.spacing.md)
    ) {
        EncabezadoDelCliente(
            detalle = detalle,
            ocultos = ocultos,
            temaOscuro = temaOscuro,
            onAlternarTema = onAlternarTema,
            onAlternarPrivacidad = onAlternarPrivacidad,
            onEditarFicha = onEditarFicha
        )
        Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
        HojaDeIdentidad(detalle, contacto, onVerUbicacion, suelo)
        Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
        HojaDeDinero(detalle, ocultos)
        Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
        HojaDeVentas(detalle, ocultos, onAbrirVenta)
        if (detalle.productos.isNotEmpty()) {
            Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
            HojaDeProductos(detalle)
        }
        if (detalle.contactos.isNotEmpty()) {
            Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
            HojaDeContactos(detalle, ocultos, onVerContactos, onVerUbicacionDelContacto)
        }
        // La ficha vive AL FONDO, donde la puso la Task 16 y donde no empuja un
        // solo dp de lo que está arriba — que es el dinero, por lo que el
        // cobrador abrió esta pantalla. `LaFichaSeVeYSeTocaTest` lo mide. Lo que
        // sí sube, y gratis, es el afordante del encabezado: ahí es donde una
        // advertencia grita.
        SeccionDeLaFicha(
            ficha = detalle.ficha,
            notaDeLaVenta = detalle.notaDeLaVenta,
            onEditar = onEditarFicha,
            hoy = detalle.hoy
        )
        Spacer(Modifier.height(MspTheme.spacing.lg))
    }
}

/**
 * El nombre como título, con el ojo y el tema a su derecha — el mismo renglón que
 * la lista, para que las dos pantallas de nivel de cliente se sientan una sola.
 *
 * El nombre se recorta con elipsis en vez de partirse en dos renglones: a dos
 * líneas el encabezado empuja todo lo de abajo y el saldo deja de verse sin
 * desplazar.
 */
@Composable
private fun EncabezadoDelCliente(
    detalle: DetalleCliente,
    ocultos: Boolean,
    temaOscuro: Boolean,
    onAlternarTema: () -> Unit,
    onAlternarPrivacidad: () -> Unit,
    onEditarFicha: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MspTheme.spacing.sm)
            // Alto FIJO, y no el que resulte del contenido. El afordante aparece y
            // desaparece con el estado de la ficha, así que sin este piso la fila
            // mediría distinto con advertencia que sin ella y **la ficha movería el
            // dinero** — que es justo lo que `LaFichaSeVeYSeTocaTest` prohíbe. El
            // valor es el alto del afordante, que es la pieza más alta de la fila.
            .heightIn(min = ALTO_DEL_ENCABEZADO),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Text(
            text = detalle.nombre,
            style = MspTheme.type.greeting,
            color = MspTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .testTag(TITULO_DE_CLIENTE_TAG)
        )
        // El afordante va EN el renglón del título —cuesta cero dp verticales, que
        // es su razón de ser— y **solo cuando tiene algo que gritar**.
        //
        // En el golden se vio el costo de ponerlo siempre: con "ver ficha" puesto,
        // el nombre se recortaba a "Victoria Fl…". Y "ver ficha" no aporta nada
        // nuevo, porque la ficha ya tiene su icono dos filas abajo, en las
        // acciones: la pastilla era el MISMO camino dicho dos veces, comiéndose el
        // título de la pantalla.
        //
        // Lo que el icono NO puede decir es la advertencia. *"Hay perro"* y *"no ir
        // solo"* tienen que llegarle al cobrador antes de que abra el portón, y una
        // ficha ilegible tiene que avisarse para que nadie crea que está vacía. En
        // esos dos casos la pastilla aparece y el nombre cede el ancho: entre un
        // nombre entero y una advertencia a tiempo, gana la advertencia.
        if (detalle.ficha == null || detalle.ficha.advertencias.isNotEmpty()) {
            AfordanteDeLaFicha(ficha = detalle.ficha, onEditar = onEditarFicha)
        }
        MspPrivacyEyeToggle(masked = ocultos, onToggle = onAlternarPrivacidad)
        MspThemeToggle(darkTheme = temaOscuro, onToggle = onAlternarTema)
    }
}

@Composable
private fun HojaDeIdentidad(
    detalle: DetalleCliente,
    contacto: AccionesDeContacto,
    onVerUbicacion: (() -> Unit)?,
    suelo: (@Composable (onTocar: () -> Unit) -> Unit)?
) {
    val visuales = detalle.ventas.take(CUADROS_EN_EL_RACIMO).map { estadoVisualDe(it.estado) }
    HojaContinua {
        SeccionDeHoja(primera = true) {
            BloqueDeIdentidad(
                estados = visuales.map { it.icono },
                colores = visuales.map { it.fondo to it.contenido },
                zona = detalle.zona,
                direccion = detalle.direccion
            )
            // Los dos datos que el rediseño perdió sin que nadie lo notara, DENTRO
            // de esta sección y no en una propia: todo lo que se agrega a la hoja
            // de identidad empuja la hoja del dinero, y una sección aparte costaba
            // 88 dp de más — medidos con `LaFichaSeVeYSeTocaTest` en rojo. Ver el
            // KDoc de `DatosDeLaPuerta`.
            Spacer(Modifier.height(MspTheme.spacing.xs))
            DatosDeLaPuerta(
                aval = detalle.aval,
                telefonoAval = detalle.telefonoAval,
                ultimaVisita = detalle.ultimaVisita?.let(AppTime::toBusinessDate)
            )
        }
        // El cuadro de ubicación, **a sangre**: sin el padding de `SeccionDeHoja`,
        // porque un mapa con margen se lee como una foto pegada encima de la hoja
        // y no como una banda de la hoja. Su hairline lo pone el `Separador` de
        // arriba, que es el mismo que usa cualquier otra sección.
        //
        // Se pinta SIEMPRE, con punto medido y sin él. Antes era condicional y sin
        // punto quedaba un hueco: el dueño lo vio en vidrio y pidió lo contrario
        // —*"tiene que ser un mapa o un dibujo"*—. Lo que cambia entre los dos
        // casos es el suelo, no si hay banda: con coordenada el suelo es el mapa
        // de verdad que cablea `:app`, y sin ella el respaldo dibujado, que se lee
        // como ilustración y no como un mapa que no cargó.
        Separador()
        CuadroDeLaPuerta(
            ubicacion = detalle.ultimoCobroAqui,
            onVerUbicacion = onVerUbicacion,
            suelo = suelo
        )
        // "Cómo llegar" es una acción más de la fila, permanente, y NO vuelve a
        // vivir dentro del cuadro. Cuando vivía ahí, cuál de los dos caminos se
        // pintaba lo decidía un dato —había cobro con GPS o no había—, así que el
        // cobrador veía una pantalla distinta para una acción que siempre se puede
        // hacer. Lo cerró `5417e65e` y se queda cerrado.
        //
        // Lo que abre es la app de mapas del teléfono, con la coordenada del
        // último cobro cuando se midió una y la dirección escrita cuando no. En
        // una colonia sin numeración esa es la diferencia entre llegar a la puerta
        // y llegar a la calle. Ver `IntentAccionesExternasAdapter`.
        SeccionDeHoja {
            // Sin "Ficha": las Notas se fueron al dock, donde se ven sin
            // desplazar y pueden llevar su distintivo. Ver `AccionesDelCliente`.
            AccionesDelCliente(
                onLlamar = contacto.onLlamar,
                onWhatsApp = contacto.onWhatsApp,
                onComoLlegar = contacto.onComoLlegar
            )
        }
    }
}

@Composable
private fun HojaDeDinero(detalle: DetalleCliente, ocultos: Boolean) {
    HojaContinua {
        SeccionDeHoja(primera = true) {
            SaldoDelCliente(
                saldo = detalle.saldoTotal,
                atrasos = detalle.resumen.atrasos,
                ocultos = ocultos
            )
        }
        SeccionDeHoja { CifrasDelCliente(detalle.resumen, ocultos = ocultos) }
        SeccionDeHoja {
            RitmoDelCliente(
                resumen = detalle.resumen,
                diaDeRuta = detalle.diaDeRuta,
                frecuencia = detalle.frecuencia
            )
        }
    }
}

@Composable
private fun HojaDeVentas(detalle: DetalleCliente, ocultos: Boolean, onAbrirVenta: (Int) -> Unit) {
    HojaContinua {
        TituloDeHoja("sus ventas")
        detalle.ventas.forEachIndexed { indice, venta ->
            if (indice > 0) Separador()
            VentaEnLaHoja(
                venta = venta,
                onAbrir = { onAbrirVenta(venta.ventaId) },
                ocultos = ocultos
            )
        }
    }
}

@Composable
private fun HojaDeProductos(detalle: DetalleCliente) {
    HojaContinua {
        TituloDeHoja("productos")
        detalle.productos.forEachIndexed { indice, producto ->
            if (indice > 0) Separador()
            ProductoDelCliente(producto)
        }
    }
}

/**
 * **La línea de contactos del detalle**, agrupada por cercanía.
 *
 * Por cercanía y no por mes porque aquí se enseñan tres: con tres filas el mes
 * casi siempre da UN solo encabezado, o sea un separador que no separa nada.
 * *"Hoy / Esta semana / Antes"* sí parte, y es como se habla parado en la
 * puerta. La lista completa —"ver los N"— sí va por mes, con su subtotal.
 *
 * **Sin filtros, a propósito.** Unas pastillas arriba de tres filas ocuparían
 * más alto que las filas que filtran, en la pantalla que ya pelea cada dp
 * contra el saldo. Viven en las listas largas — ver `FiltroDeContactos`.
 *
 * ## [ocultos] baja hasta la fila, y no es opcional
 *
 * El defecto que esto cierra: esta hoja no recibía el ojo de privacidad y sus
 * piezas caían al default `ocultos = false`. Con "esconder cantidades" puesto,
 * el saldo de arriba se enmascaraba y el `Cobré · $350` de acá abajo seguía a
 * la vista **en la misma pantalla** — o sea que el ojo mentía justo donde el
 * cobrador lo prende: parado frente a alguien que está mirando el teléfono.
 * `BitacoraScreen` ya lo cableaba bien; aquí se copia.
 *
 * `internal` y no `private`: es la superficie donde el dueño vio el defecto
 * original de la fila de contactos, y hasta la Task 5 era la única de las
 * tres (bitácora, detalle de venta, detalle de cliente) sin golden propio —
 * queda bajo el pliegue en `pagos_cliente_*`, que fotografía la pantalla
 * completa sin scroll. `DetalleMatrixScreenshotTest` la monta directo, sin
 * pasar por `DetalleClienteContent` entero, para fotografiar la tarjeta sola.
 */
@Composable
internal fun HojaDeContactos(
    detalle: DetalleCliente,
    ocultos: Boolean,
    onVerContactos: () -> Unit,
    onVerUbicacionDelContacto: ((UbicacionDelCobro) -> Unit)?
) {
    HojaContinua {
        TituloDeHoja("últimos contactos")
        Column(modifier = Modifier.padding(horizontal = MspTheme.spacing.md)) {
            GruposDeContactos.porCercania(detalle.contactos, detalle.hoy).forEach { grupo ->
                EncabezadoDeGrupo(grupo = grupo, ocultos = ocultos)
                grupo.contactos.forEach { contacto ->
                    ContactoEnLinea(
                        contacto = contacto,
                        ocultos = ocultos,
                        onVerUbicacion = onVerUbicacionDelContacto
                    )
                }
            }
        }
        Separador()
        VerLosContactos(cuantos = detalle.totalContactos, onVer = onVerContactos)
    }
}

@Composable
internal fun Cargando() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MspTheme.colors.brand)
    }
}

@Composable
internal fun MensajeDeError(error: ErrorDeDetalle?, onAtras: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MspTheme.spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when (error) {
                ErrorDeDetalle.NO_ESTA_EN_EL_TELEFONO -> "No está en el teléfono"
                else -> "No se pudo abrir"
            },
            style = MspTheme.type.cardTitle,
            color = MspTheme.colors.onSurface
        )
        Spacer(Modifier.height(MspTheme.spacing.sm))
        VerTodos("Volver", onAtras)
    }
}

/**
 * Cuántos cuadros de estado caben en el racimo de identidad.
 *
 * Cuatro, como antes: a 22 dp más su aire, un quinto empieza a comerse el
 * renglón de zona y dirección, que es texto y pesa más que un cuadro de más.
 */
private const val CUADROS_EN_EL_RACIMO = 4

/**
 * El alto fijo del renglón del título.
 *
 * Es el alto del afordante de la ficha —la pieza más alta de esa fila— para que
 * la fila mida lo mismo con advertencia y sin ella. Ver el comentario en
 * [EncabezadoDelCliente].
 */
private val ALTO_DEL_ENCABEZADO = 56.dp
