@file:Suppress(
    "TooManyFunctions"
) // la pantalla y sus piezas privadas: el destino, la captura y los textos de la franja.

package com.example.msp_app.feature.pagos.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.component.MspThemeRevealHost
import com.example.msp_app.core.designsystem.component.PrimaryFieldButtonVariant
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion
import com.example.msp_app.feature.pagos.domain.AvisoDelMonto
import com.example.msp_app.feature.pagos.domain.BloqueoDelAbono
import com.example.msp_app.feature.pagos.domain.Comprobantes
import com.example.msp_app.feature.pagos.domain.MontosSugeridos
import com.example.msp_app.feature.pagos.domain.NivelDeAviso
import com.example.msp_app.feature.pagos.domain.OrigenDeLaCuota
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.ui.components.AVISO_TAG
import com.example.msp_app.feature.pagos.ui.components.BLOQUEO_TAG
import com.example.msp_app.feature.pagos.ui.components.BotonDeFoto
import com.example.msp_app.feature.pagos.ui.components.CIFRA_MAXIMA
import com.example.msp_app.feature.pagos.ui.components.CUOTA_DUDOSA_TAG
import com.example.msp_app.feature.pagos.ui.components.ChipsSugeridos
import com.example.msp_app.feature.pagos.ui.components.ConTopeDeLetra
import com.example.msp_app.feature.pagos.ui.components.DesplegableDelProducto
import com.example.msp_app.feature.pagos.ui.components.FranjaDelAbono
import com.example.msp_app.feature.pagos.ui.components.HojaDeConfirmacion
import com.example.msp_app.feature.pagos.ui.components.HojaDeFotos
import com.example.msp_app.feature.pagos.ui.components.HojaDeOrigenDelComprobante
import com.example.msp_app.feature.pagos.ui.components.MensajeDeLaFranja
import com.example.msp_app.feature.pagos.ui.components.NombreDelCliente
import com.example.msp_app.feature.pagos.ui.components.SelectorDeMetodo
import com.example.msp_app.feature.pagos.ui.components.TOPE_DEL_BOTON
import com.example.msp_app.feature.pagos.ui.components.TOQUE_DEL_ABONO
import com.example.msp_app.feature.pagos.ui.components.TarjetaDeLaCifra
import com.example.msp_app.feature.pagos.ui.components.TarjetaDelProducto
import com.example.msp_app.feature.pagos.ui.components.TecladoDeMontos
import com.example.msp_app.feature.pagos.ui.components.TonoDeLaFranja
import com.example.msp_app.feature.pagos.ui.components.ZonaQueCede
import com.example.msp_app.feature.pagos.ui.components.altoDeLaFranja
import com.example.msp_app.feature.pagos.ui.components.cesionesPara
import com.example.msp_app.feature.pagos.ui.components.esLetraGrande
import com.example.msp_app.feature.pagos.ui.components.saldoNuevo

/** `testTag` del CTA que abre el paso uno de la confirmación. */
const val CTA_ABONO_TAG: String = "pagos_abono_cta"

/** `testTag` de la banda que dice por qué el abono no quedó. */
const val FALLO_DEL_ABONO_TAG: String = "pagos_abono_fallo"

/** `testTag` del reintento REAL: vuelve a cargar y resuelve la verificación pendiente. */
const val REVISAR_DE_NUEVO_TAG: String = "pagos_abono_revisar"

/**
 * El destino: conecta el ViewModel con el contenido puro.
 *
 * [onRegistrado] se dispara UNA vez, con el id del abono — la Task 20 lo lleva
 * al ticket. Va en un `LaunchedEffect` con clave el propio id para que una
 * recomposición no lo vuelva a disparar.
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
 */
@Composable
fun RegistrarAbonoScreen(
    viewModel: RegistrarAbonoViewModel,
    onAtras: () -> Unit,
    onRegistrado: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) viewModel.fotoTomada() else viewModel.fotoCancelada()
    }
    // El selector de fotos del sistema. **No pide ningún permiso** —ni
    // `READ_EXTERNAL_STORAGE` ni `READ_MEDIA_IMAGES`—: corre fuera del proceso y
    // solo devuelve lo que el cobrador escogió. `PickMultipleVisualMedia` y no
    // `PickVisualMedia` porque la hoja promete "puedes escoger varias", y una
    // hoja que promete lo que el selector no hace es la forma que miente.
    val galeria = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(Comprobantes.MAXIMO)
    ) { uris ->
        viewModel.archivosElegidos(uris.map(Uri::toString))
    }
    // El explorador de archivos (SAF). Es el único de los tres que alcanza un
    // PDF: el selector de fotos solo enseña imágenes y video, y cobranza acepta
    // PDF a propósito porque los recibos SAT llegan así. Tampoco pide permisos.
    // Se lanza con `*/*` y NO con la whitelist: el tipo se decide por los BYTES
    // del archivo (`Comprobantes.tipoDe`), y filtrar por el MIME que declara el
    // proveedor sería confiar en el dato que este módulo decidió no creerle a
    // nadie.
    val archivo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.archivosElegidos(listOfNotNull(uri?.toString()))
    }
    MspThemeRevealHost(
        onToggleTheme = viewModel::alternarTema,
        reducedMotion = rememberMspReducedMotion(),
        tema = { animateColors, contenido ->
            // `darkTheme` queda en su default (`appDarkTheme()` → `LocalAppDarkTheme` →
            // `ThemeController.isDarkMode`): el tema lo manda la app, no esta pantalla.
            MspTheme(animateColors = animateColors, content = contenido)
        }
    ) {
        RegistrarAbonoContent(
            state = state,
            onAtras = onAtras,
            onDigito = viewModel::onDigito,
            onPunto = viewModel::onPunto,
            onBorrar = viewModel::onBorrar,
            onMetodo = viewModel::onMetodo,
            onSugerido = viewModel::onSugerido,
            onRegistrar = viewModel::pedirConfirmacion,
            onConfirmar = viewModel::confirmar,
            onEditar = viewModel::descartarConfirmacion,
            onRevisar = viewModel::cargar,
            onAgregarFoto = viewModel::abrirOrigenes,
            onOrigen = viewModel::onOrigen,
            onCerrarOrigenes = viewModel::cerrarOrigenes,
            onQuitarFoto = viewModel::quitarFoto,
            modifier = modifier
        )
    }
    // El destino no nulo ES la petición de abrir la cámara: el ViewModel lo
    // acuña y lo persiste ANTES de que el intent salga, así que si el proceso
    // muere con la cámara encima la foto vuelve con el id que ya tenía — que es
    // lo que mantiene idempotente al reintento de subida. La clave del efecto
    // es ese id: una recomposición no vuelve a abrir la cámara.
    val destino = state.destinoDeFoto
    if (destino != null) {
        LaunchedEffect(destino.id) { camara.launch(Uri.parse(destino.uriParaLaCamara)) }
    }
    val selector = state.selectorPedido
    if (selector != null) {
        // Mismo patrón que la cámara, con la petición de clave: se lanza una vez
        // y el ViewModel la suelta en el acto, así una recomposición no reabre el
        // selector. La petición NO se persiste — si el proceso muere con el
        // selector arriba no hay nada acuñado que proteger, y reponerla al volver
        // lo reabriría solo.
        LaunchedEffect(selector) {
            when (selector) {
                OrigenDeLaFoto.GALERIA -> galeria.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
                else -> archivo.launch("*/*")
            }
            viewModel.selectorAtendido()
        }
    }
    val registrado = state.registrado
    if (registrado != null) {
        LaunchedEffect(registrado) { onRegistrado(registrado) }
    }
}

/** `testTag` de la franja cuando dice que el abono ya quedó. */
const val REGISTRADO_DEL_ABONO_TAG: String = "pagos_abono_registrado"

/**
 * Registrar abono: **captura**, **bloqueo duro**, **confirmar** y **monto
 * raro**, con el teclado anclado (mock `docs/design/mocks/registrar-abono-fijo.html`,
 * aprobado por el dueño el 2026-09-29).
 *
 * Composable PURO sobre [RegistrarAbonoUiState]. No decide nada de dinero: el
 * veredicto llega hecho y aquí solo se pinta — el borde rojo de la cifra, el
 * mensaje de la franja, el CTA apagado y el color de la hoja salen todos del
 * mismo [com.example.msp_app.feature.pagos.domain.VeredictoDelAbono].
 *
 * ## De abajo hacia arriba
 *
 * La regla del dueño: *"siempre el teclado numérico se vea completo cuando se
 * entra y no se ha hecho scroll aún"*. Antes todo iba en una columna que hacía
 * scroll y un aviso de un renglón empujaba el teclado 48dp hacia abajo —su
 * última fila quedaba debajo del botón—, y a letra grande el teclado ni se
 * veía. Ahora el botón, el teclado y la fila del método van **anclados abajo**
 * con alto fijo; lo demás vive en [ZonaQueCede], que tiene el alto que sobre y
 * cede (renglones, rótulos, tamaño de la cifra) en vez de empujar. La medida es
 * `ElTecladoNoSeMueveTest`.
 *
 * ## Lo que se movió de lugar, sin cambiar lo que hace
 *
 * - El aviso en vivo, el bloqueo, la parcialidad dudosa, el error y "Abono
 *   registrado" dejaron de ser bandas sueltas que empujaban: ahora son **un
 *   solo mensaje** en la franja fija de la cifra ([mensajeDeLaFranja]).
 * - "Volver a revisar" toma el lugar del botón Registrar, que en ese estado
 *   está muerto de todas formas (`sePuedeRegistrar` es falso con la
 *   verificación pendiente), y el teclado se pinta apagado.
 * - La rejilla de comprobantes vive en una hoja que abre el botón "Foto"
 *   ([HojaDeFotos]); con cero comprobantes el botón abre directo el origen.
 *
 * Ninguna de las piezas de seguridad —bloqueo, dos pasos, alerta roja— cambió
 * de lógica: sólo de lugar.
 */
@Composable
fun RegistrarAbonoContent(
    state: RegistrarAbonoUiState,
    onAtras: () -> Unit,
    onDigito: (Int) -> Unit,
    onPunto: () -> Unit,
    onBorrar: () -> Unit,
    onMetodo: (MetodoDeCobro) -> Unit,
    onSugerido: (Money) -> Unit,
    onRegistrar: () -> Unit,
    onConfirmar: () -> Unit,
    onEditar: () -> Unit,
    onRevisar: () -> Unit,
    onAgregarFoto: () -> Unit,
    onOrigen: (OrigenDeLaFoto) -> Unit,
    onCerrarOrigenes: () -> Unit,
    onQuitarFoto: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var fotosAbiertas by rememberSaveable { mutableStateOf(false) }
    var desplegado by rememberSaveable { mutableStateOf(false) }
    // Sólo mientras hay algo abierto: sin hojas, el atrás es del sistema y
    // vuelve a la pantalla anterior, como en el detalle de cliente y de venta.
    BackHandler(enabled = fotosAbiertas || desplegado) {
        fotosAbiertas = false
        desplegado = false
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MspTheme.colors.background)
            // Ruling BR — DESPUÉS del `background`, para que el color siga pintándose a
            // sangre bajo la barra de estado y el inset solo baje el CONTENIDO. Sin esto la
            // app corre `enableEdgeToEdge()` y la ventana `StatusBar` del sistema queda
            // ENCIMA de la pantalla y se come sus taps. La compuerta es
            // `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest`. El botón de abajo
            // consume por su lado el inset de la barra de navegación.
            .statusBarsPadding()
    ) {
        val venta = state.venta
        when {
            state.cargando -> CargandoElAbono()
            venta == null -> MensajeDeErrorDelAbono(state.error, onAtras)
            else -> CapturaDelAbono(
                state = state,
                venta = venta,
                desplegado = desplegado,
                onDesplegado = { desplegado = it },
                onAbrirFotos = { fotosAbiertas = true },
                onDigito = onDigito,
                onPunto = onPunto,
                onBorrar = onBorrar,
                onMetodo = onMetodo,
                onSugerido = onSugerido,
                onRegistrar = onRegistrar,
                onRevisar = onRevisar,
                onAgregarFoto = onAgregarFoto
            )
        }
        if (fotosAbiertas && venta != null) {
            HojaDeFotos(
                comprobantes = state.comprobantes,
                miniaturas = state.miniaturas,
                intentos = state.intentos,
                puedeAgregar = state.sePuedeAgregarFoto,
                onAgregar = onAgregarFoto,
                onQuitar = onQuitarFoto,
                onCerrar = { fotosAbiertas = false }
            )
        }
        if (state.eligiendoOrigen) {
            HojaDeOrigenDelComprobante(
                espaciosLibres = state.espaciosLibres,
                onOrigen = onOrigen,
                onCerrar = onCerrarOrigenes
            )
        }
        val confirmacion = state.confirmacion
        if (confirmacion != null && state.venta != null) {
            HojaDeConfirmacion(
                cliente = state.venta.clienteNombre,
                producto = state.venta.titulo,
                folio = state.venta.folio,
                importe = confirmacion.importe,
                metodo = confirmacion.metodo,
                veredicto = confirmacion.veredicto,
                esperadoHoy = MontosSugeridos.esperadoHoy(state.venta),
                comprobantes = state.comprobantes.size,
                onConfirmar = onConfirmar,
                onEditar = onEditar,
                aviso = confirmacion.aviso
            )
        }
    }
}

/**
 * La captura: la zona que cede arriba y, anclados abajo, método, teclado y
 * botón. El desplegable del producto se pinta ENCIMA, con su velo, y nunca baja
 * del método.
 */
@Suppress("LongParameterList")
@Composable
private fun CapturaDelAbono(
    state: RegistrarAbonoUiState,
    venta: DetalleVenta,
    desplegado: Boolean,
    onDesplegado: (Boolean) -> Unit,
    onAbrirFotos: () -> Unit,
    onDigito: (Int) -> Unit,
    onPunto: () -> Unit,
    onBorrar: () -> Unit,
    onMetodo: (MetodoDeCobro) -> Unit,
    onSugerido: (Money) -> Unit,
    onRegistrar: () -> Unit,
    onRevisar: () -> Unit,
    onAgregarFoto: () -> Unit
) {
    val densidad = LocalDensity.current
    var origen by remember { mutableFloatStateOf(0f) }
    var productoAbajo by remember { mutableFloatStateOf(0f) }
    var tecladoArriba by remember { mutableFloatStateOf(0f) }
    var metodoAbajo by remember { mutableFloatStateOf(0f) }
    // Cualquier otro toque de la captura cierra el desplegable: es la "primera
    // tecla" del mock. Sólo se cierra; lo que el toque hace, lo sigue haciendo.
    val cerrar = { if (desplegado) onDesplegado(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origen = it.positionInRoot().y }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = MspTheme.spacing.md)
        ) {
            ZonaDeArriba(
                state = state,
                venta = venta,
                desplegado = desplegado,
                onAlternar = { onDesplegado(!desplegado) },
                onSugerido = {
                    cerrar()
                    onSugerido(it)
                },
                onProductoAbajo = { productoAbajo = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            Spacer(Modifier.height(MspTheme.spacing.sm))
            val hayFotos = state.comprobantes.isNotEmpty() || state.intentos.isNotEmpty()
            SelectorDeMetodo(
                seleccionado = state.metodo,
                onMetodo = {
                    cerrar()
                    onMetodo(it)
                },
                modifier = Modifier.onGloballyPositioned { metodoAbajo = it.boundsInRoot().bottom }
            ) {
                BotonDeFoto(
                    comprobantes = state.comprobantes.size,
                    intentosFallidos = state.intentos.size,
                    habilitado = hayFotos || state.sePuedeAgregarFoto,
                    onClick = {
                        cerrar()
                        if (hayFotos) onAbrirFotos() else onAgregarFoto()
                    }
                )
            }
            Spacer(Modifier.height(MspTheme.spacing.sm))
            TecladoDeMontos(
                onDigito = {
                    cerrar()
                    onDigito(it)
                },
                onPunto = {
                    cerrar()
                    onPunto()
                },
                onBorrar = {
                    cerrar()
                    onBorrar()
                },
                habilitado = !state.verificacionPendiente,
                modifier = Modifier.onGloballyPositioned { tecladoArriba = it.boundsInRoot().top }
            )
            Spacer(Modifier.height(MspTheme.spacing.md))
            BotonDelAbono(state = state, onRegistrar = onRegistrar, onRevisar = onRevisar)
            Spacer(Modifier.height(MspTheme.spacing.sm))
        }
        if (desplegado) {
            val productos = venta.productos.map { it.nombre }.ifEmpty { listOf(venta.titulo) }
            with(densidad) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((tecladoArriba - origen).coerceAtLeast(0f).toDp())
                        .background(Color.Black.copy(alpha = ALFA_DEL_VELO_DEL_PRODUCTO))
                        .pointerInput(Unit) { detectTapGestures { onDesplegado(false) } }
                )
                val arriba = productoAbajo - origen + HUECO_DEL_DESPLEGABLE.toPx()
                DesplegableDelProducto(
                    productos = productos,
                    onCerrar = { onDesplegado(false) },
                    modifier = Modifier
                        .padding(horizontal = MspTheme.spacing.md)
                        .offset { IntOffset(0, arriba.toInt()) }
                        .heightIn(max = (metodoAbajo - origen - arriba).coerceAtLeast(0f).toDp())
                )
            }
        }
    }
}

/**
 * Lo que se oscurece la captura detrás del desplegable del producto: el velo
 * oscuro del mock (`rgba(0,0,0,.28)`), no el blanqueado de las hojas, porque el
 * desplegable no es una hoja: la captura tiene que seguir leyéndose detrás.
 */
private const val ALFA_DEL_VELO_DEL_PRODUCTO = 0.28f

/** El aire entre la tarjeta del producto y su desplegable. */
private val HUECO_DEL_DESPLEGABLE = 6.dp

/**
 * **La zona de arriba**: el cliente, el producto con su saldo, la cifra con su
 * franja y los sugeridos. Cede —ver [ZonaQueCede] y [cesionesPara]—, nunca
 * empuja.
 */
@Suppress("LongParameterList")
@Composable
private fun ZonaDeArriba(
    state: RegistrarAbonoUiState,
    venta: DetalleVenta,
    desplegado: Boolean,
    onAlternar: () -> Unit,
    onSugerido: (Money) -> Unit,
    onProductoAbajo: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val grande = esLetraGrande()
    val cifraMaxima = if (LocalDensity.current.fontScale >= ESCALA_MUY_GRANDE) {
        CIFRA_MAXIMA * CRECIMIENTO_DE_LA_CIFRA
    } else {
        CIFRA_MAXIMA
    }
    val franjaDeDos = altoDeLaFranja(2)
    val franjaDeUno = altoDeLaFranja(1)
    val mensaje = mensajeDeLaFranja(state, venta)
    val conError = state.monto.esPositivo &&
        BloqueoDelAbono.EXCEDE_EL_SALDO in state.veredicto.bloqueos
    ZonaQueCede(
        cesiones = cesionesPara(grande, cifraMaxima),
        modifier = modifier
    ) { cesion, medir ->
        val hueco = if (cesion.apretado) MspTheme.spacing.xs else MspTheme.spacing.sm
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // La medida es sólo eso: no se pinta ni se anuncia. Sin esto,
                // cada paso que se probó dejaría un segundo "cliente" y un
                // segundo "producto" en el árbol de accesibilidad.
                .then(if (medir) Modifier.clearAndSetSemantics {} else Modifier.fillMaxHeight()),
            verticalArrangement = Arrangement.spacedBy(hueco)
        ) {
            NombreDelCliente(
                nombre = venta.clienteNombre,
                apretado = cesion.apretado,
                onToque = if (cesion.conProducto) null else onAlternar
            )
            TarjetaDelProducto(
                producto = venta.titulo,
                saldo = venta.saldo,
                cesion = cesion,
                abierto = desplegado,
                onAlternar = onAlternar,
                modifier = if (medir) {
                    Modifier
                } else {
                    Modifier.onGloballyPositioned { onProductoAbajo(it.boundsInRoot().bottom) }
                }
            )
            TarjetaDeLaCifra(
                monto = state.monto.enPantalla(),
                conError = conError,
                conRotulo = cesion.conRotulo,
                cifraMaxima = cifraMaxima,
                cifraMinima = cesion.cifraMinima,
                franjaReservada = if (cesion.franjaEnDosRenglones) franjaDeDos else franjaDeUno,
                desborde = hueco + TOQUE_DEL_ABONO,
                puedeDesbordar = mensaje.tono != TonoDeLaFranja.NEUTRO,
                medir = medir,
                franja = { FranjaDelAbono(mensaje) },
                modifier = Modifier
                    .zIndex(1f)
                    .then(if (medir) Modifier else Modifier.weight(1f))
            )
            ChipsSugeridos(sugeridos = state.sugeridos, onSugerido = onSugerido)
        }
    }
}

/** A partir de esta escala la cifra busca un tamaño mayor (el 2.0× del mock). */
private const val ESCALA_MUY_GRANDE = 2f

/** Cuánto más grande busca ser la cifra a 2.0×: lo que medía en el golden de hoy. */
private const val CRECIMIENTO_DE_LA_CIFRA = 1.21f

/**
 * **Lo único que dice la franja**, con la prioridad del mock: el error del
 * registro, "Abono registrado", el bloqueo, el aviso en vivo, la parcialidad
 * dudosa y, sin nada de eso, el saldo nuevo.
 *
 * Los textos son los mismos de antes, uno por uno: nada nuevo que decir.
 */
@Composable
private fun mensajeDeLaFranja(
    state: RegistrarAbonoUiState,
    venta: DetalleVenta
): MensajeDeLaFranja {
    val fallo = state.fallo
    val bloqueo = textoDelBloqueo(state, venta)
    val aviso = avisoEnVivo(state.aviso)
    return when {
        fallo != null -> MensajeDeLaFranja(
            TonoDeLaFranja.ROJO,
            listOf(AnnotatedString(textoDelFallo(fallo))),
            FALLO_DEL_ABONO_TAG
        )

        state.registrado != null -> MensajeDeLaFranja(
            TonoDeLaFranja.VERDE,
            listOf(AnnotatedString("Abono registrado")),
            REGISTRADO_DEL_ABONO_TAG
        )

        bloqueo != null -> MensajeDeLaFranja(
            TonoDeLaFranja.ROJO,
            listOf(AnnotatedString(bloqueo)),
            BLOQUEO_TAG
        )
        aviso != null -> aviso
        venta.cuota.origen == OrigenDeLaCuota.DUDOSA -> MensajeDeLaFranja(
            tono = TonoDeLaFranja.AMBAR,
            renglones = listOf(AnnotatedString("Revisa la parcialidad")),
            tag = CUOTA_DUDOSA_TAG,
            detalle = detalleDeLaCuotaDudosa(venta)
        )

        else -> saldoNuevo(state.veredicto.saldoNuevo)
    }
}

/** "La venta dice $3,000 · en esta ruta nadie paga tanto". El hecho, no el adjetivo. */
private fun detalleDeLaCuotaDudosa(venta: DetalleVenta): String {
    val cuota = formatMoneyMxn(venta.parcialidad.amount)
    return "La venta dice $cuota · en esta ruta nadie paga tanto"
}

/**
 * El bloqueo duro. Silencioso con el teclado en blanco: el CTA apagado ya
 * cubre "no hay nada que registrar". Dice el **máximo registrable**, que es el
 * saldo.
 */
private fun textoDelBloqueo(state: RegistrarAbonoUiState, venta: DetalleVenta): String? {
    val bloqueos = state.veredicto.bloqueos
    return when {
        !state.monto.esPositivo -> null
        BloqueoDelAbono.VENTA_SIN_SALDO in bloqueos -> "Esta venta ya no debe nada"
        BloqueoDelAbono.EXCEDE_EL_SALDO in bloqueos ->
            "El abono excede el saldo · máximo " + formatMoneyMxn(venta.saldo.amount)

        else -> null
    }
}

/**
 * **El aviso en vivo**, el que sale mientras se teclea. Sólo hablan los dos
 * niveles raros: `NINGUNO` no tiene nada que decir, `BLOQUEO` ya tiene su
 * mensaje con el máximo registrable y `NOTA` tiene el suyo dentro de la hoja.
 *
 * El `when` es **exhaustivo y sin `else`**: un nivel nuevo no compila hasta que
 * alguien decida si se pinta y de qué color.
 */
private fun avisoEnVivo(aviso: AvisoDelMonto): MensajeDeLaFranja? {
    val tono = when (aviso.nivel) {
        NivelDeAviso.NINGUNO, NivelDeAviso.BLOQUEO, NivelDeAviso.NOTA -> null
        NivelDeAviso.CONFIRMAR -> TonoDeLaFranja.AMBAR
        NivelDeAviso.AFIRMAR -> TonoDeLaFranja.ROJO
    } ?: return null
    // Un nivel que habla pero sin nada que decir no pinta una franja vacía.
    if (aviso.mensajes.isEmpty()) return null
    return MensajeDeLaFranja(tono, aviso.mensajes.map { AnnotatedString(it) }, AVISO_TAG)
}

/** Por qué el abono no quedó. */
private fun textoDelFallo(fallo: FalloDelAbono): String = when (fallo) {
    FalloDelAbono.VENTA_NO_ESTA -> "La venta ya no está en el teléfono"
    FalloDelAbono.SIN_COBRADOR -> "Falta el cobrador, vuelve a entrar"
    FalloDelAbono.NO_SE_PUDO_GUARDAR -> "No se pudo guardar, intenta de nuevo"
    FalloDelAbono.BLOQUEADO -> "El monto no se puede registrar"
    FalloDelAbono.NO_SE_PUDO_VERIFICAR -> "No se pudo confirmar, revisa de nuevo"
}

/**
 * El botón de abajo. **Apagado es apagado** — el `enabled` sale del veredicto.
 *
 * Con la verificación pendiente el botón Registrar está muerto (el guard sigue
 * puesto a propósito), así que en su lugar va **"Volver a revisar"**: el
 * reintento de verdad, que vuelve a cargar y resuelve la duda mirando el
 * historial. Sin él la única salida sería salirse de la pantalla.
 */
@Composable
private fun BotonDelAbono(
    state: RegistrarAbonoUiState,
    onRegistrar: () -> Unit,
    onRevisar: () -> Unit
) {
    ConTopeDeLetra(TOPE_DEL_BOTON) {
        if (state.sePuedeRevisar) {
            MspPrimaryFieldButton(
                text = "Volver a revisar",
                onClick = onRevisar,
                variant = PrimaryFieldButtonVariant.Ghost,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TOQUE_DEL_ABONO)
                    .testTag(REVISAR_DE_NUEVO_TAG)
            )
        } else {
            MspPrimaryFieldButton(
                text = "Registrar abono " + formatMoneyMxn(state.monto.importe.amount),
                onClick = onRegistrar,
                enabled = state.sePuedeRegistrar,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TOQUE_DEL_ABONO)
                    .testTag(CTA_ABONO_TAG)
            )
        }
    }
}

@Composable
private fun CargandoElAbono() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MspTheme.colors.brand, strokeWidth = 2.dp)
    }
}

@Composable
private fun MensajeDeErrorDelAbono(error: ErrorDeDetalle?, onAtras: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MspTheme.spacing.md),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when (error) {
                ErrorDeDetalle.NO_ESTA_EN_EL_TELEFONO -> "Esta venta no está en el teléfono"
                else -> "No se pudo cargar la venta"
            },
            style = MspTheme.type.body,
            color = MspTheme.colors.onSurfaceMuted
        )
        MspPrimaryFieldButton(
            text = "Volver",
            onClick = onAtras,
            modifier = Modifier.padding(top = MspTheme.spacing.md)
        )
    }
}
