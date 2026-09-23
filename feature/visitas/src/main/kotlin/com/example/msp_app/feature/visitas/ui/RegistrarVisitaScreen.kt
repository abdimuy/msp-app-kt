@file:Suppress(
    "TooManyFunctions"
) // una pieza por fold del mock; juntarlas escondería qué pinta cada desenlace.

package com.example.msp_app.feature.visitas.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.component.MspRevealedContent
import com.example.msp_app.core.designsystem.component.MspThemeRevealHost
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion
import com.example.msp_app.core.speech.ui.CampoDictado
import com.example.msp_app.feature.visitas.domain.CatalogoDeResultados
import com.example.msp_app.feature.visitas.domain.ComprobantesDeVisita
import com.example.msp_app.feature.visitas.domain.DiasSugeridos
import com.example.msp_app.feature.visitas.domain.ReglasDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.ui.components.BandaDeFallo
import com.example.msp_app.feature.visitas.ui.components.BandaDeRecomendacion
import com.example.msp_app.feature.visitas.ui.components.BarraDeVisita
import com.example.msp_app.feature.visitas.ui.components.CHIP_TAG
import com.example.msp_app.feature.visitas.ui.components.CalendarioDeVisita
import com.example.msp_app.feature.visitas.ui.components.CampoDeMonto
import com.example.msp_app.feature.visitas.ui.components.ChipDeOpcion
import com.example.msp_app.feature.visitas.ui.components.DockDeLaVisita
import com.example.msp_app.feature.visitas.ui.components.ETIQUETA_TAG
import com.example.msp_app.feature.visitas.ui.components.EncabezadoDeCuentas
import com.example.msp_app.feature.visitas.ui.components.FilaDeCuenta
import com.example.msp_app.feature.visitas.ui.components.HojaDeOrigenDelComprobante
import com.example.msp_app.feature.visitas.ui.components.OpcionDeResultado
import com.example.msp_app.feature.visitas.ui.components.RelojDeLaCita
import com.example.msp_app.feature.visitas.ui.components.RotuloDeSeccion
import com.example.msp_app.feature.visitas.ui.components.SeccionDeComprobantesDeVisita
import com.example.msp_app.feature.visitas.ui.components.TarjetaDelFold
import com.example.msp_app.feature.visitas.ui.components.TiraDelCliente
import com.example.msp_app.feature.visitas.ui.components.TituloDeSeccion
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * El destino: conecta el ViewModel con el contenido puro.
 *
 * [onRegistrada] se dispara UNA vez, con el id de la visita, dentro de un
 * `LaunchedEffect` con clave el propio id — una recomposición no lo repite.
 *
 * **Provee el tema.** `:app` nunca provee `MspTheme` —monta `MspappTheme`, el
 * Material legado— y su `NavHost` no envuelve a ningún destino: sin este bloque
 * la primera lectura de `MspTheme.colors` revienta con
 * `IllegalStateException("MspTheme ausente")` al abrir la pantalla. El
 * razonamiento completo —por qué en el `*Screen` y no en la ruta ni en la raíz
 * de `:app`, y cuál es la compuerta— está en el KDoc de
 * `feature.pagos.ui.ListaDeClientesScreen`.
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
fun RegistrarVisitaScreen(
    viewModel: RegistrarVisitaViewModel,
    onAtras: () -> Unit,
    onRegistrada: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // El contrato de cámara del sistema: escribe en el `content://` que se le
    // pasa y contesta true/false. El ViewModel no lo conoce — deja el destino en
    // el estado y esta capa lo lanza, que es lo que lo mantiene testeable sin
    // Robolectric.
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) viewModel.fotoTomada() else viewModel.fotoCancelada()
    }
    // El selector de fotos del sistema. **No pide ningún permiso** —ni
    // `READ_EXTERNAL_STORAGE` ni `READ_MEDIA_IMAGES`—: corre fuera del proceso y
    // solo devuelve lo que el cobrador escogió. `PickMultipleVisualMedia` y no
    // `PickVisualMedia` porque la hoja promete "puedes escoger varias", y una
    // hoja que promete lo que el selector no hace es la forma que miente.
    val galeria = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ComprobantesDeVisita.MAXIMO)
    ) { uris ->
        viewModel.archivosElegidos(uris.map(Uri::toString))
    }
    // El explorador de archivos (SAF). Es el único de los tres que alcanza un
    // PDF: el selector de fotos solo enseña imágenes y video. Tampoco pide
    // permisos. Se lanza con `*/*` a propósito y NO con la whitelist: el tipo se
    // decide por los BYTES del archivo (`ComprobantesDeVisita.tipoDe`), y filtrar
    // por el MIME que declara el proveedor sería confiar en el dato que este
    // módulo decidió no creerle a nadie.
    val archivo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.archivosElegidos(listOfNotNull(uri?.toString()))
    }
    // **El permiso del micrófono (`RECORD_AUDIO`), que es NUEVO en la app.**
    //
    // Se pide al TOCAR el micrófono y no al abrir la pantalla: un diálogo de
    // permiso que salta al entrar, antes de que el cobrador haya pedido nada,
    // es el que se niega por reflejo. Y negarlo **no pierde la nota** — el
    // ViewModel apaga el micrófono, pinta el aviso ámbar y el campo sigue
    // recibiendo texto escrito a mano.
    val permisoDeMicrofono = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        viewModel.onPermisoDeMicrofono(concedido)
        if (concedido) viewModel.onMicrofono()
    }
    val contexto = LocalContext.current
    MspThemeRevealHost(
        onToggleTheme = viewModel::alternarTema,
        reducedMotion = rememberMspReducedMotion(),
        tema = { animateColors, contenido ->
            // `darkTheme` queda en su default (`appDarkTheme()` → `LocalAppDarkTheme` →
            // `ThemeController.isDarkMode`): el tema lo manda la app, no esta pantalla.
            MspTheme(animateColors = animateColors, content = contenido)
        }
    ) {
        RegistrarVisitaContent(
            state = state,
            acciones = AccionesDeLaVisita(
                onAtras = onAtras,
                onResultado = viewModel::onResultado,
                onCambiarResultado = viewModel::limpiarResultado,
                onEtiqueta = viewModel::onEtiqueta,
                onNota = viewModel::onNota,
                onMicrofono = {
                    // El permiso se consulta con el sistema, no con una copia
                    // en memoria: se puede revocar desde ajustes mientras la
                    // app vive.
                    val concedido = ContextCompat.checkSelfPermission(
                        contexto,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (concedido) {
                        viewModel.onMicrofono()
                    } else {
                        permisoDeMicrofono.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onQuitarAudio = viewModel::quitarAudioDeLaNota,
                onCuenta = viewModel::onCuenta,
                onTodasLasCuentas = viewModel::onTodasLasCuentas,
                onFechaPromesa = viewModel::onFechaPromesa,
                onMonto = viewModel::onMontoPrometido,
                onFechaCita = viewModel::onFechaCita,
                onHoraCita = viewModel::onHoraCita,
                onAbrirCalendario = viewModel::abrirCalendario,
                onCerrarCalendario = viewModel::cerrarCalendario,
                onDiaDelCalendario = viewModel::onDiaDelCalendario,
                onAbrirReloj = viewModel::abrirReloj,
                onCerrarReloj = viewModel::cerrarReloj,
                onHoraDelReloj = viewModel::onHoraDelReloj,
                onGuardar = viewModel::guardar,
                onReintentar = viewModel::cargar,
                onAgregarFoto = viewModel::abrirOrigenes,
                onOrigen = viewModel::onOrigen,
                onCerrarOrigenes = viewModel::cerrarOrigenes,
                onQuitarFoto = viewModel::quitarFoto
            ),
            modifier = modifier
        )
    }
    val destino = state.destinoDeFoto
    if (destino != null) {
        // Clave el id del destino: una recomposición no reabre la cámara, y un
        // destino nuevo sí la abre.
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
                // `*/*` y no `application/pdf`: la whitelist admite además JPEG,
                // PNG, GIF y WebP, y `GetContent` solo acepta UN tipo. Lo que
                // decide sigue siendo la firma de los bytes.
                else -> archivo.launch("*/*")
            }
            viewModel.selectorAtendido()
        }
    }
    val registrada = state.registrada
    if (registrada != null) {
        LaunchedEffect(registrada) { onRegistrada(registrada) }
    }
}

/**
 * Los gestos de la pantalla, en un solo objeto.
 *
 * No es azúcar: la captura tiene dieciséis controles y pasarlos sueltos pondría
 * al composable muy por encima de cualquier lectura razonable en la llamada. Es
 * el mismo criterio con el que `PagosRutas` partió sus registros en tres.
 */
data class AccionesDeLaVisita(
    val onAtras: () -> Unit,
    val onResultado: (ResultadoDeVisita) -> Unit,
    val onCambiarResultado: () -> Unit,
    val onEtiqueta: (String) -> Unit,
    val onNota: (String) -> Unit,
    /** Toca el micrófono: abre el dictado, o lo cierra si ya estaba abierto. */
    val onMicrofono: () -> Unit,
    /** Quita el audio adjunto de ESTA captura. */
    val onQuitarAudio: () -> Unit,
    val onCuenta: (Int) -> Unit,
    val onTodasLasCuentas: () -> Unit,
    val onFechaPromesa: (LocalDate) -> Unit,
    val onMonto: (Money?) -> Unit,
    val onFechaCita: (LocalDate) -> Unit,
    val onHoraCita: (LocalTime?) -> Unit,
    val onAbrirCalendario: () -> Unit,
    val onCerrarCalendario: () -> Unit,
    val onDiaDelCalendario: (LocalDate) -> Unit,
    val onAbrirReloj: () -> Unit,
    val onCerrarReloj: () -> Unit,
    val onHoraDelReloj: (LocalTime) -> Unit,
    val onGuardar: () -> Unit,
    val onReintentar: () -> Unit,
    val onAgregarFoto: () -> Unit,
    val onOrigen: (OrigenDeLaFoto) -> Unit,
    val onCerrarOrigenes: () -> Unit,
    val onQuitarFoto: (String) -> Unit
) {
    companion object {
        /** Todas mudas — para goldens y previews, donde nada se toca. */
        val NINGUNA: AccionesDeLaVisita = AccionesDeLaVisita(
            onAtras = {},
            onResultado = {},
            onCambiarResultado = {},
            onEtiqueta = {},
            onNota = {},
            onMicrofono = {},
            onQuitarAudio = {},
            onCuenta = {},
            onTodasLasCuentas = {},
            onFechaPromesa = {},
            onMonto = {},
            onFechaCita = {},
            onHoraCita = {},
            onAbrirCalendario = {},
            onCerrarCalendario = {},
            onDiaDelCalendario = {},
            onAbrirReloj = {},
            onCerrarReloj = {},
            onHoraDelReloj = {},
            onGuardar = {},
            onReintentar = {},
            onAgregarFoto = {},
            onOrigen = {},
            onCerrarOrigenes = {},
            onQuitarFoto = {}
        )
    }
}

/**
 * Registrar visita, en los tres estados del mock: **elegir**, **prometió** y
 * **no estaba**.
 *
 * Composable PURO sobre [RegistrarVisitaUiState]. No decide nada: qué se puede
 * guardar lo dice `ReglasDeLaVisita` a través del estado, y el color de cada
 * desenlace sale de la tabla de paleta. La pantalla solo pinta.
 *
 * **La foto (Task 23)** entra en el hueco que este KDoc le tenía reservado: al
 * final de la columna que hace scroll, y nada de lo de arriba se movió para que
 * quepa. Su punto de ENTRADA, en cambio, va arriba —en la fila del encabezado,
 * con coste vertical cero—, porque al pie de esta columna la sección queda
 * debajo de la línea de flotación a 360×800dp y el cobrador podría no
 * descubrirla nunca.
 */
@Composable
fun RegistrarVisitaContent(
    state: RegistrarVisitaUiState,
    acciones: AccionesDeLaVisita,
    modifier: Modifier = Modifier
) {
    Box(
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
        Column(modifier = Modifier.fillMaxSize()) {
            val contexto = state.contexto
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.cargando -> CargandoLaVisita()
                    contexto == null -> MensajeDeError(state, acciones)
                    else -> CuerpoDeLaVisita(state, acciones)
                }
            }
            if (contexto != null) {
                DockDeLaVisita(
                    texto = if (state.registrada == null) "Guardar visita" else "Visita guardada",
                    habilitado = state.sePuedeGuardar,
                    pie = state.pieDelCta,
                    onGuardar = acciones.onGuardar
                )
            }
        }
        if (state.eligiendoOrigen) {
            HojaDeOrigenDelComprobante(
                espaciosLibres = state.espaciosLibres,
                onOrigen = acciones.onOrigen,
                onCerrar = acciones.onCerrarOrigenes
            )
        }
        DialogosDeLaVisita(state, acciones)
    }
}

@Composable
private fun CargandoLaVisita() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MspTheme.colors.brand)
    }
}

@Composable
private fun MensajeDeError(state: RegistrarVisitaUiState, acciones: AccionesDeLaVisita) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MspTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        BarraDeVisita(onAtras = acciones.onAtras)
        Text(
            text = state.error?.mensaje ?: "No se pudo cargar",
            style = MspTheme.type.bodyStrong,
            color = MspTheme.colors.onSurface
        )
        ChipDeOpcion(
            texto = "Volver a cargar",
            activo = false,
            habilitado = true,
            onElegir = acciones.onReintentar,
            modifier = Modifier.testTag(CHIP_TAG + "reintentar")
        )
    }
}

@Composable
private fun CuerpoDeLaVisita(state: RegistrarVisitaUiState, acciones: AccionesDeLaVisita) {
    val contexto = requireNotNull(state.contexto)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MspTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        BarraDeVisita(onAtras = acciones.onAtras)
        Text(
            text = "Visita",
            style = MspTheme.type.screenTitle,
            color = MspTheme.colors.onSurface
        )
        TiraDelCliente(contexto)
        state.recomendacion?.let {
            BandaDeRecomendacion("${it.etiquetaDePosicion} · ${it.motivo}")
        }
        state.fallo?.let { BandaDeFallo(it.mensaje) }
        TituloDeSeccion("¿Qué pasó en la puerta?")
        // Elegido un desenlace, la lista se colapsa al elegido y su fold ocupa
        // la pantalla — es la composición del mock, y en un teléfono a una mano
        // evita que la captura de la promesa nazca debajo del pliegue. Tocar el
        // renglón elegido vuelve a abrir los cinco.
        val elegido = state.captura.resultado
        if (elegido == null) {
            ResultadoDeVisita.entries.forEach { resultado ->
                OpcionDeResultado(
                    resultado = resultado,
                    seleccionado = false,
                    habilitado = state.sePuedeCapturar,
                    onElegir = { acciones.onResultado(resultado) }
                )
            }
        } else {
            OpcionDeResultado(
                resultado = elegido,
                seleccionado = true,
                habilitado = state.sePuedeCapturar,
                onElegir = acciones.onCambiarResultado
            )
        }
        // Lo que CADA desenlace revela —cómo estaba, cuáles cuentas, la
        // promesa o la cita— entra y sale con transición en vez de aparecer de
        // golpe (el dueño: "se siente muy agresivo"). El selector de arriba
        // —las cinco opciones, o la elegida sola— NO se anima: sólo lo de
        // abajo. `elegido` es la llave: null no revela nada, y cambiar de un
        // desenlace a otro cruza el contenido viejo con el nuevo en vez de
        // parpadear. Ver el KDoc de `MspRevealedContent`.
        MspRevealedContent(
            targetState = elegido,
            modifier = Modifier.fillMaxWidth()
        ) { resultado ->
            if (resultado != null) {
                Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
                    if (CatalogoDeResultados.pideEtiqueta(resultado)) {
                        TarjetaDelFold {
                            RotuloDeSeccion("cómo estaba")
                            FilaDeEtiquetas(resultado, state, acciones)
                        }
                    }
                    SeccionDeCuentas(state, acciones)
                    when (resultado) {
                        ResultadoDeVisita.PROMETIO -> SeccionDeLaPromesa(state, acciones)
                        ResultadoDeVisita.CITA -> SeccionDeLaCita(state, acciones)
                        else -> Unit
                    }
                }
            }
        }
        // El campo de nota, que ahora se DICTA. Sigue siendo el mismo campo de
        // texto —se toca y se corrige— y no sabe qué motor lo llena: eso es
        // asunto de `DictadoPort`, y es la razón de que el puerto exista.
        CampoDictado(
            etiqueta = "Nota — opcional",
            marcador = "Lo que dijo, en sus palabras",
            texto = state.captura.nota,
            estado = state.dictado,
            puedeDictar = state.sePuedeDictar,
            grabacion = state.audioDeLaNota,
            aviso = state.avisoDelDictado,
            habilitado = state.sePuedeCapturar,
            onTexto = acciones.onNota,
            onMicrofono = acciones.onMicrofono,
            onQuitarAudio = acciones.onQuitarAudio
        )
        SeccionDeComprobantesDeVisita(
            comprobantes = state.comprobantes,
            miniaturas = state.miniaturas,
            intentos = state.intentos,
            puedeAgregar = state.sePuedeAgregarFoto,
            onAgregar = acciones.onAgregarFoto,
            onQuitar = acciones.onQuitarFoto
        )
        Box(modifier = Modifier.padding(bottom = MspTheme.spacing.md))
    }
}

/**
 * **¿De cuáles cuentas?** — la sección que convierte una frase del cliente en
 * las visitas que le corresponden.
 *
 * Solo aparece cuando el desenlace es de una cuenta **y hay más de una**: con
 * una sola cuenta la pregunta ya está contestada y un control de una opción es
 * ruido. Con toda la puerta no aparece nunca, porque ahí no se elige nada — el
 * estado se propaga a todas las cuentas activas del cliente.
 *
 * El atajo "todas / ninguna" solo se pinta cuando se pueden marcar varias: bajo
 * "prometió" sería un botón que promete repartir un monto que no se reparte.
 */
@Composable
private fun SeccionDeCuentas(state: RegistrarVisitaUiState, acciones: AccionesDeLaVisita) {
    if (!state.pideCuentas) return
    val contexto = requireNotNull(state.contexto)
    if (state.variasCuentas) {
        EncabezadoDeCuentas(
            todasMarcadas = state.todasLasCuentasMarcadas,
            habilitado = state.sePuedeCapturar,
            onTodas = acciones.onTodasLasCuentas
        )
        Text(
            text = "Vienen marcadas todas; desmarca las que no",
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted
        )
    } else {
        TituloDeSeccion("¿De cuál cuenta?")
    }
    contexto.ventas.forEach { venta ->
        FilaDeCuenta(
            venta = venta,
            marcada = venta.ventaId in state.captura.cuentas,
            varias = state.variasCuentas,
            habilitado = state.sePuedeCapturar,
            onTocar = { acciones.onCuenta(venta.ventaId) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaDeEtiquetas(
    resultado: ResultadoDeVisita,
    state: RegistrarVisitaUiState,
    acciones: AccionesDeLaVisita
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        CatalogoDeResultados.etiquetasDe(resultado).forEachIndexed { indice, etiqueta ->
            ChipDeOpcion(
                texto = etiqueta,
                activo = state.captura.etiqueta == etiqueta,
                habilitado = state.sePuedeCapturar,
                onElegir = { acciones.onEtiqueta(etiqueta) },
                modifier = Modifier.testTag(ETIQUETA_TAG + indice)
            )
        }
    }
}

/** **¿Cuándo?** y **¿cuánto?** — los dos datos que hacen existir una promesa. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeccionDeLaPromesa(state: RegistrarVisitaUiState, acciones: AccionesDeLaVisita) {
    TituloDeSeccion("¿Cuándo?")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        val sugeridos = DiasSugeridos.paraPromesa(state.hoy)
        sugeridos.forEach { dia ->
            ChipDeDia(
                dia = dia,
                elegido = state.captura.fechaPromesa,
                state = state,
                onElegir = acciones.onFechaPromesa
            )
        }
        ChipDeOtroDia(
            elegido = state.captura.fechaPromesa,
            sugeridos = sugeridos,
            state = state,
            acciones = acciones
        )
    }
    CampoDeMonto(
        digitos = digitosDe(state),
        habilitado = state.sePuedeCapturar,
        onCambio = { acciones.onMonto(montoDe(it)) }
    )
}

/** **¿Qué día?** y **¿a qué hora?** — la cita, con la hora como campo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeccionDeLaCita(state: RegistrarVisitaUiState, acciones: AccionesDeLaVisita) {
    TituloDeSeccion("¿Qué día?")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        val sugeridos = DiasSugeridos.paraCita(state.hoy)
        sugeridos.forEach { dia ->
            ChipDeDia(
                dia = dia,
                elegido = state.captura.fechaCita,
                state = state,
                onElegir = acciones.onFechaCita
            )
        }
        ChipDeOtroDia(
            elegido = state.captura.fechaCita,
            sugeridos = sugeridos,
            state = state,
            acciones = acciones
        )
    }
    TituloDeSeccion("¿A qué hora?")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        // "Sin hora" es una opción de primera clase, no la ausencia de elegir:
        // el mock contempla "otro día sin hora", y una cita sin hora se pinta
        // como pendiente, no como cita (Task 16).
        ChipDeOpcion(
            texto = DiasSugeridos.SIN_HORA,
            activo = state.captura.horaCita == null,
            habilitado = state.sePuedeCapturar,
            onElegir = { acciones.onHoraCita(null) },
            modifier = Modifier.testTag(CHIP_TAG + "sin_hora")
        )
        DiasSugeridos.HORAS_SUGERIDAS.forEach { hora ->
            ChipDeOpcion(
                texto = DiasSugeridos.etiquetaDe(hora),
                activo = state.captura.horaCita == hora,
                habilitado = state.sePuedeCapturar,
                onElegir = { acciones.onHoraCita(hora) },
                modifier = Modifier.testTag(CHIP_TAG + "hora_${hora.hour}")
            )
        }
        // Fuera de las cuatro horas de un toque: la hora que se picó en el
        // reloj, o null si nunca se abrió o coincide con una sugerida.
        val horaEscapada = state.captura.horaCita?.takeIf { it !in DiasSugeridos.HORAS_SUGERIDAS }
        ChipDeOpcion(
            texto = horaEscapada?.let { DiasSugeridos.etiquetaDe(it) } ?: "Otra hora",
            activo = horaEscapada != null,
            habilitado = state.sePuedeCapturar,
            onElegir = acciones.onAbrirReloj,
            modifier = Modifier.testTag(CHIP_TAG + "otra_hora")
        )
    }
}

/**
 * Un chip de día. El acento es `brand`, como toda selección de esta pantalla.
 *
 * Iba en el color del desenlace —rojo en la promesa, violeta en la cita— con el
 * argumento de que "estos chips sí llevan estado". No lo llevan: llevan una
 * elección. El efecto medido era que elegir "prometió" pintaba de rojo el
 * renglón, la cuenta, la fecha y el monto, y una pantalla de captura entera en
 * rojo se lee como un error, no como un formulario contestado.
 */
@Composable
private fun ChipDeDia(
    dia: LocalDate,
    elegido: LocalDate?,
    state: RegistrarVisitaUiState,
    onElegir: (LocalDate) -> Unit
) {
    ChipDeOpcion(
        texto = DiasSugeridos.etiquetaDe(dia, state.hoy),
        activo = elegido == dia,
        habilitado = state.sePuedeCapturar,
        onElegir = { onElegir(dia) },
        modifier = Modifier.testTag(CHIP_TAG + "dia_$dia")
    )
}

/**
 * El chip de escape del calendario. Cuando [elegido] cae fuera de [sugeridos]
 * —se entró por "otro día" y se picó una fecha que no es de las de un
 * toque— el chip se marca elegido y enseña esa fecha, en vez de quedar
 * apagado con la etiqueta genérica y esconder lo que el cobrador ya picó.
 */
@Composable
private fun ChipDeOtroDia(
    elegido: LocalDate?,
    sugeridos: List<LocalDate>,
    state: RegistrarVisitaUiState,
    acciones: AccionesDeLaVisita
) {
    // El día que se picó fuera de los sugeridos, o null si no se ha elegido
    // ninguno o el elegido ya tiene su propio chip entre los sugeridos.
    val diaEscapado = elegido?.takeIf { it !in sugeridos }
    val texto = diaEscapado
        ?.let { DiasSugeridos.etiquetaDe(it, state.hoy) }
        ?: DiasSugeridos.OTRO_DIA
    ChipDeOpcion(
        texto = texto,
        activo = diaEscapado != null,
        habilitado = state.sePuedeCapturar,
        onElegir = acciones.onAbrirCalendario,
        modifier = Modifier.testTag(CHIP_TAG + "otro_dia")
    )
}

@Composable
private fun DialogosDeLaVisita(state: RegistrarVisitaUiState, acciones: AccionesDeLaVisita) {
    if (state.eligiendoDia) {
        val esPromesa = state.captura.resultado == ResultadoDeVisita.PROMETIO
        CalendarioDeVisita(
            inicial = (if (esPromesa) state.captura.fechaPromesa else state.captura.fechaCita)
                ?: state.hoy,
            // Los DOS topes, y los mismos para la promesa y para la cita: el
            // calendario no ofrece lo que `ReglasDeLaVisita` rechazaría después.
            // Antes la cita no llevaba mínimo y ninguna de las dos llevaba
            // máximo, así que un día hacia atrás se podaba en la primera
            // sincronización y un año mal tecleado clavaba la fila por décadas.
            minimo = state.hoy,
            maximo = ReglasDeLaVisita.ultimoDiaValido(state.hoy),
            onElegir = acciones.onDiaDelCalendario,
            onCerrar = acciones.onCerrarCalendario
        )
    }
    if (state.eligiendoHora) {
        RelojDeLaCita(
            inicial = state.captura.horaCita ?: HORA_POR_DEFECTO,
            onElegir = acciones.onHoraDelReloj,
            onCerrar = acciones.onCerrarReloj
        )
    }
}

/** Los dígitos que la pantalla muestra en el campo de monto. Pesos enteros. */
private fun digitosDe(state: RegistrarVisitaUiState): String =
    state.captura.montoPrometido?.amount?.toBigInteger()?.toString().orEmpty()

/**
 * Los dígitos capturados, en [Money]. Vacío = **sin monto**, que es distinto de
 * cero: cero significaría "prometió no pagar".
 */
private fun montoDe(digitos: String): Money? =
    digitos.takeIf { it.isNotBlank() }?.let { Money.of(BigDecimal(it)) }

/** La hora con la que abre el reloj cuando todavía no hay ninguna elegida. */
private val HORA_POR_DEFECTO: LocalTime = LocalTime.of(12, 0)
