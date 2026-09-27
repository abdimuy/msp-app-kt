@file:Suppress(
    "TooManyFunctions"
) // una función por pieza de la pantalla; fusionarlas rehace el muro que el rediseño partió.

package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MASKED_MONEY
import com.example.msp_app.core.designsystem.component.MspBackdrop
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.component.MspPrivacyEyeToggle
import com.example.msp_app.core.designsystem.component.MspProgressBar
import com.example.msp_app.core.designsystem.component.MspThemeRevealHost
import com.example.msp_app.core.designsystem.component.MspThemeToggle
import com.example.msp_app.core.designsystem.component.altoDeLaBarra
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.component.mspBackdropSource
import com.example.msp_app.core.designsystem.component.rememberMspBackdrop
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion
import com.example.msp_app.feature.pagos.domain.BitacoraDelCliente
import com.example.msp_app.feature.pagos.domain.GruposDeContactos
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.example.msp_app.feature.pagos.ui.components.AIRE_DEL_DOCK_TAG
import com.example.msp_app.feature.pagos.ui.components.AccionDeCondonar
import com.example.msp_app.feature.pagos.ui.components.BloqueDelMes
import com.example.msp_app.feature.pagos.ui.components.ContactoEnLinea
import com.example.msp_app.feature.pagos.ui.components.ControlesFlotantes
import com.example.msp_app.feature.pagos.ui.components.CuadroDeEstado
import com.example.msp_app.feature.pagos.ui.components.DockDeAcciones
import com.example.msp_app.feature.pagos.ui.components.EncabezadoCompacto
import com.example.msp_app.feature.pagos.ui.components.EncabezadoDeGrupo
import com.example.msp_app.feature.pagos.ui.components.EstadoEnGrande
import com.example.msp_app.feature.pagos.ui.components.FilaClaveValor
import com.example.msp_app.feature.pagos.ui.components.HojaDelContacto
import com.example.msp_app.feature.pagos.ui.components.LabelDeSeccion
import com.example.msp_app.feature.pagos.ui.components.MenuDelDock
import com.example.msp_app.feature.pagos.ui.components.RecargaAlVolver
import com.example.msp_app.feature.pagos.ui.components.RitmoDeSemanas
import com.example.msp_app.feature.pagos.ui.components.SIN_DATO
import com.example.msp_app.feature.pagos.ui.components.Separador
import com.example.msp_app.feature.pagos.ui.components.SueloDelControlFlotante
import com.example.msp_app.feature.pagos.ui.components.Tarjeta
import com.example.msp_app.feature.pagos.ui.components.TarjetaDeGarantia
import com.example.msp_app.feature.pagos.ui.components.TarjetaDeLiquidacion
import com.example.msp_app.feature.pagos.ui.components.ToqueDeLaFila
import com.example.msp_app.feature.pagos.ui.components.VeloDeLaBarraDeEstado
import com.example.msp_app.feature.pagos.ui.components.VerLosContactos
import com.example.msp_app.feature.pagos.ui.components.VerTodos
import java.time.format.DateTimeFormatter

/** `testTag` del título de la pantalla de venta — el producto. */
const val TITULO_DE_VENTA_TAG: String = "pagos_titulo_venta"

/** `testTag` de la tarjeta del saldo con sus cifras. */
const val TARJETA_DEL_SALDO_DE_VENTA_TAG: String = "pagos_venta_tarjeta_saldo"

/** `testTag` del renglón "Último pago" de la tarjeta del saldo. */
const val ULTIMO_PAGO_DE_VENTA_TAG: String = "pagos_venta_ultimo_pago"

/** `testTag` del enlace "Datos de la venta · N datos ›". */
const val VER_DATOS_DE_LA_VENTA_TAG: String = "pagos_venta_ver_datos"

/** `testTag` de la tira deslizable de pastillas con las cifras de la venta. */
const val PASTILLAS_DE_LA_VENTA_TAG: String = "pagos_venta_pastillas"

internal val FECHA_DE_VENTA: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "d MMM yyyy",
    BUSINESS_LOCALE
)

private val DIA_Y_MES: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", BUSINESS_LOCALE)

/**
 * El destino: conecta el ViewModel con el contenido puro.
 *
 * **Provee el tema por [MspThemeRevealHost]**, no por un `MspTheme` pelado: el
 * sol/luna de esta pantalla anima la misma reveal que la lista y el detalle de
 * cliente. `:app` nunca provee `MspTheme` (ver [ListaDeClientesScreen]).
 *
 * ## [onVerTicket] — la reimpresión del último cobro del día
 *
 * Sobre el cobro de HOY que además es el ÚLTIMO de esa cuenta, tocar el renglón
 * **pregunta** qué abrir —ubicación o ticket—. Quién lo decide es
 * [com.example.msp_app.feature.pagos.domain.ToqueDelContacto].
 *
 * ## [onCondonar] — desde el "⋯" del dock
 *
 * Recibe siempre el `ventaId` —el `DOCTO_CC_ACR_ID` que la captura legada
 * (`NewForgivenessDialog`) necesita para prellenar el saldo—. Condonar y usar la
 * liquidación son dos acciones de dinero distintas y no se mezclan.
 *
 * ## [onVerAbonos] y [onVerContactos]
 *
 * "Ver los N abonos" sigue abriendo la pantalla legada; "Ver los N contactos"
 * lleva a la bitácora de ESTA cuenta.
 */
@Composable
fun DetalleVentaScreen(
    viewModel: DetalleVentaViewModel,
    onAtras: () -> Unit,
    onRegistrarAbono: (Int) -> Unit,
    onRegistrarVisita: (Int, Int?) -> Unit,
    onVerAbonos: (Int) -> Unit,
    onVerGarantia: (Int) -> Unit,
    onVerUbicacion: (UbicacionDelCobro, direccion: String, pagoId: String) -> Unit,
    onCondonar: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onVerContactos: (Int) -> Unit = {},
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
            MspTheme(animateColors = animateColors, content = contenido)
        }
    ) {
        DetalleVentaContent(
            state = state,
            onAtras = onAtras,
            onRegistrarAbono = { onRegistrarAbono(viewModel.ventaId) },
            // La visita se registra sobre la PUERTA, con esta cuenta como contexto.
            onRegistrarVisita = {
                detalle?.let { onRegistrarVisita(it.clienteId, viewModel.ventaId) }
            },
            onUsarLiquidacion = { onRegistrarAbono(viewModel.ventaId) },
            onVerAbonos = { onVerAbonos(viewModel.ventaId) },
            onVerContactos = { onVerContactos(viewModel.ventaId) },
            onCondonar = { onCondonar(viewModel.ventaId) },
            // El flujo de garantías de `:app` está indexado por VENTA
            // (`getGuaranteeSaleById(DOCTO_CC_ID)`): se manda el crédito.
            onVerGarantia = { detalle?.let { onVerGarantia(it.creditoId) } },
            // La venta no trae una dirección propia: viaja vacía y la hoja del
            // mapa la enseña como ausente.
            onVerUbicacionDelContacto = { punto, pagoId -> onVerUbicacion(punto, "", pagoId) },
            onVerTicket = onVerTicket,
            controles = ControlesDeLaVenta(
                onAlternarTema = viewModel::alternarTema,
                onAlternarPrivacidad = viewModel::alternarPrivacidad
            ),
            modifier = modifier
        )
    }
}

/** El ojo y el sol/luna, juntos para no pasar de siete lambdas sueltas. */
data class ControlesDeLaVenta(
    val onAlternarTema: () -> Unit = {},
    val onAlternarPrivacidad: () -> Unit = {}
)

/**
 * El detalle de una venta, con el diseño aprobado en
 * `docs/design/mocks/detalle-de-venta-final.html`:
 *
 *  - **Sin flecha de volver.** El ojo y el sol/luna van fijos arriba a la
 *    derecha, como en el detalle de cliente, y al desplazar entra el
 *    encabezado compacto con el producto arriba y el cliente abajo.
 *  - **Sin nota de la venta.** Era la nota del cliente, no de la cuenta.
 *  - **El trato arriba**: el saldo y, en la misma tarjeta, la parcialidad, la
 *    barra, las cifras en pastillas deslizables, el aval, el último pago de
 *    ESTA venta y el enlace a la hoja con los once datos.
 *  - **El ojo tapa toda cantidad** —saldo, parcialidad, promesa, pastillas,
 *    liquidación, hoja, línea de tiempo— y el CTA queda en "Abonar".
 *  - **Condonar vive en el "⋯"**, idéntico al del cliente.
 *
 * Composable PURO sobre [DetalleVentaUiState].
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
    onVerContactos: () -> Unit = {},
    onVerUbicacionDelContacto: ((UbicacionDelCobro, pagoId: String) -> Unit)? = null,
    onVerTicket: ((String) -> Unit)? = null,
    onCondonar: () -> Unit = {},
    controles: ControlesDeLaVenta = ControlesDeLaVenta()
) {
    // Cuál renglón está preguntando, por su `ContactoDeCobranza.id`.
    var preguntaPor by rememberSaveable { mutableStateOf<String?>(null) }
    var verDatos by rememberSaveable { mutableStateOf(false) }
    val backdrop = rememberMspBackdrop()
    val riel = rememberScrollState()
    // El recorrido del encabezado compacto es lo que mide el bloque del título
    // (cliente, producto, folio): cuando eso salió de pantalla, entra el
    // compacto al 70 %, igual que en el detalle de cliente.
    var recorrido by remember { mutableFloatStateOf(0f) }
    val avance = { if (recorrido <= 0f) 0f else (riel.value / recorrido).coerceIn(0f, 1f) }
    val ocultos = state.montosOcultos
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MspTheme.colors.background)
            // Ruling BR — DESPUÉS del `background`. La compuerta es
            // `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest`.
            .statusBarsPadding()
    ) {
        val detalle = state.detalle
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.cargando -> Cargando()
                detalle == null -> MensajeDeError(state.error, onAtras)
                else -> CuerpoDeLaVenta(
                    riel = riel,
                    aireAbajo = backdrop.altoDeLaBarra(),
                    backdrop = backdrop,
                    detalle = detalle,
                    ocultos = ocultos,
                    onRecorrido = { recorrido = it },
                    acciones = AccionesDelCuerpo(
                        onUsarLiquidacion = onUsarLiquidacion,
                        onVerAbonos = onVerAbonos,
                        onVerGarantia = onVerGarantia,
                        onVerContactos = onVerContactos,
                        onVerDatos = { verDatos = true },
                        onVerUbicacionDelContacto = onVerUbicacionDelContacto
                    ),
                    toque = ToqueDeLaFila(
                        // La línea del CLIENTE entero: "el último de la cuenta"
                        // se decide sobre todo lo que hay.
                        contactos = detalle.contactos,
                        hoy = detalle.hoy,
                        onPreguntar = onVerTicket?.let {
                            { contacto -> preguntaPor = contacto.id }
                        },
                        onVerTicket = onVerTicket?.let { ver ->
                            { contacto -> ver(contacto.id) }
                        }
                    )
                )
            }
        }
        if (detalle != null) {
            VeloDeLaBarraDeEstado(modifier = Modifier.align(Alignment.TopCenter))
            EncabezadoCompacto(
                nombre = detalle.titulo,
                direccion = detalle.clienteNombre,
                avance = avance,
                backdrop = backdrop
            )
            // Después del compacto, para que su degradado no los lave.
            ControlesFlotantes(modifier = Modifier.align(Alignment.TopEnd)) {
                SueloDelControlFlotante {
                    MspPrivacyEyeToggle(masked = ocultos, onToggle = controles.onAlternarPrivacidad)
                }
                SueloDelControlFlotante {
                    MspThemeToggle(
                        darkTheme = state.temaOscuro,
                        onToggle = controles.onAlternarTema
                    )
                }
            }
            val letraGrande = LocalFontSizeLevel.current != FontSizeLevel.NORMAL
            DockDeAcciones(
                // "Abonar $220" a letra normal; "Abonar" a las grandes —para
                // que el dock no se apile— y con el ojo cerrado, que no enseña
                // ninguna cantidad.
                textoPrimario = if (ocultos || letraGrande) {
                    "Abonar"
                } else {
                    "Abonar " + formatMoneyMxn(detalle.parcialidad.amount)
                },
                onPrimario = onRegistrarAbono,
                onVisita = onRegistrarVisita,
                backdrop = backdrop,
                // Condonar en el "⋯", idéntico al del cliente, con un solo
                // renglón. Aquí no hay "¿a cuál cuenta?": la venta ya es una.
                menu = MenuDelDock(condonar = AccionDeCondonar(onAbrir = onCondonar, cuentas = 1)),
                unaSolaFila = true
            )
        }
    }
    val preguntando = state.detalle?.contactos?.firstOrNull { it.id == preguntaPor }
    if (preguntando != null) {
        HojaDelContacto(
            onVerUbicacion = {
                preguntaPor = null
                preguntando.ubicacion?.let { punto ->
                    onVerUbicacionDelContacto?.invoke(punto, preguntando.id)
                }
            },
            onVerTicket = {
                preguntaPor = null
                onVerTicket?.invoke(preguntando.id)
            },
            onCerrar = { preguntaPor = null }
        )
    }
    HojaDeDatosDeLaVenta(
        detalle = state.detalle.takeIf { verDatos },
        ocultos = ocultos,
        onCerrar = { verDatos = false }
    )
}

/** Lo que el cuerpo puede abrir. Junto, por el tope de parámetros de detekt. */
private data class AccionesDelCuerpo(
    val onUsarLiquidacion: () -> Unit,
    val onVerAbonos: () -> Unit,
    val onVerGarantia: () -> Unit,
    val onVerContactos: () -> Unit,
    val onVerDatos: () -> Unit,
    val onVerUbicacionDelContacto: ((UbicacionDelCobro, pagoId: String) -> Unit)?
)

@Composable
private fun CuerpoDeLaVenta(
    riel: ScrollState,
    aireAbajo: Dp,
    backdrop: MspBackdrop,
    detalle: DetalleVenta,
    ocultos: Boolean,
    onRecorrido: (Float) -> Unit,
    acciones: AccionesDelCuerpo,
    toque: ToqueDeLaFila
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .mspBackdropSource(backdrop)
            .verticalScroll(riel)
            .padding(horizontal = MspTheme.spacing.md)
    ) {
        // El nombre del cliente vive en la franja de los controles: misma banda
        // de 48 dp, arrancando 8 dp abajo del inset, con la derecha libre para
        // el ojo y el sol/luna.
        Spacer(Modifier.height(MspTheme.spacing.sm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(end = ESQUINA_DE_LOS_CONTROLES),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            CuadroDeEstado(estadoVisualDe(detalle.estado), lado = 22.dp)
            Text(
                text = detalle.clienteNombre,
                style = MspTheme.type.body.copy(fontSize = 14.sp),
                color = MspTheme.colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
            style = MspTheme.type.body,
            color = MspTheme.colors.onSurfaceMuted,
            modifier = Modifier
                .padding(top = 2.dp)
                .onPlaced { onRecorrido(it.positionInParent().y + it.size.height) }
        )
        Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
        EstadoEnGrande(detalle.estado, ocultos = ocultos)
        Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
        TarjetaDelSaldo(detalle, ocultos, acciones.onVerDatos)

        detalle.liquidacion?.let { liquidacion ->
            LabelDeSeccion("liquidación de esta venta")
            TarjetaDeLiquidacion(
                label = "hoy liquida con",
                liquidacion = liquidacion,
                onUsar = acciones.onUsarLiquidacion,
                ocultos = ocultos
            )
        }

        LabelDeSeccion("ritmo · últimas 12 semanas")
        RitmoDeSemanas(detalle.historial, ocultos = ocultos)

        LabelDeSeccion("lo que ha pasado")
        LineaDeLaVenta(
            detalle = detalle,
            onVerAbonos = acciones.onVerAbonos,
            onVerContactos = acciones.onVerContactos,
            onVerUbicacion = acciones.onVerUbicacionDelContacto,
            toque = toque,
            ocultos = ocultos
        )

        if (detalle.productos.isNotEmpty()) {
            LabelDeSeccion("productos")
            detalle.productos.forEach { producto ->
                FilaClaveValor(
                    clave = producto.nombre,
                    valor = when {
                        producto.importe == null -> SIN_DATO
                        ocultos -> MASKED_MONEY
                        else -> formatMoneyMxn(producto.importe.amount)
                    }
                )
            }
        }

        detalle.garantia?.let { garantia ->
            LabelDeSeccion("garantía")
            TarjetaDeGarantia(garantia = garantia, onVerGarantia = acciones.onVerGarantia)
        }
        Spacer(Modifier.height(MspTheme.spacing.lg))
        // El aire que el dock tapa, con el alto que la barra misma midió.
        Spacer(Modifier.height(aireAbajo).testTag(AIRE_DEL_DOCK_TAG))
    }
}

/**
 * **El saldo y el trato, en una sola tarjeta** (`.hero` del mock): el saldo con
 * la parcialidad a la derecha, la barra de avance, las cifras en pastillas que
 * se deslizan de lado, el aval, el último pago de ESTA venta y el enlace a la
 * hoja con los once datos.
 *
 * "Último pago" **no se pinta sin pagos**: una raya ahí se leería como "se
 * perdió", y la venta simplemente no tiene abonos todavía.
 */
@Composable
private fun TarjetaDelSaldo(detalle: DetalleVenta, ocultos: Boolean, onVerDatos: () -> Unit) {
    MspCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TARJETA_DEL_SALDO_DE_VENTA_TAG),
        shape = MspTheme.shapes.card
    ) {
        Column {
            Column(
                modifier = Modifier.padding(
                    start = MspTheme.spacing.md,
                    end = MspTheme.spacing.md,
                    top = 14.dp
                )
            ) {
                Text(
                    text = "Saldo de esta venta".uppercase(BUSINESS_LOCALE),
                    style = MspTheme.type.eyebrow,
                    color = MspTheme.colors.onSurfaceMuted
                )
                SaldoYParcialidad(detalle, ocultos)
                Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
                MspProgressBar(
                    progress = detalle.avance,
                    height = 6.dp,
                    fillColor = MspTheme.colors.heroProgressFill,
                    trackColor = MspTheme.colors.progressTrack
                )
            }
            PastillasDeLaVenta(detalle, ocultos)
            PersonasDeLaVenta(detalle)
            Separador()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .clickable(onClick = onVerDatos)
                    .padding(horizontal = MspTheme.spacing.md)
                    .testTag(VER_DATOS_DE_LA_VENTA_TAG),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Datos de la venta",
                    style = MspTheme.type.methodLabel.copy(fontSize = 13.5.sp),
                    color = MspTheme.colors.brand,
                    modifier = Modifier.weight(1f)
                )
                val cuantos = cuantosDatos(detalle)
                Text(
                    text = "$cuantos datos ›",
                    style = MspTheme.type.subtitle.copy(fontSize = 12.sp),
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
        }
    }
}

/**
 * El saldo con la parcialidad a la derecha. **A letra grande la parcialidad
 * baja de renglón** (apilar antes de partir): a 2.0× los dos no caben juntos y
 * el saldo se partía a media cifra ("$1,4 / 50"), que en una pantalla de dinero
 * es un defecto, no un detalle.
 */
@Composable
private fun SaldoYParcialidad(detalle: DetalleVenta, ocultos: Boolean) {
    val parcialidad = listOf(
        if (ocultos) MASKED_MONEY else formatMoneyMxn(detalle.parcialidad.amount),
        detalle.frecuencia
    ).filter { it.isNotBlank() }.joinToString(" ")
    val saldo = @Composable { modifier: Modifier ->
        MspMoneyText(
            amount = detalle.saldo.amount,
            masked = ocultos,
            style = MspTheme.type.amountLarge.copy(lineHeight = 36.sp),
            color = MspTheme.colors.onSurface,
            modifier = modifier
        )
    }
    if (LocalFontSizeLevel.current != FontSizeLevel.NORMAL) {
        saldo(Modifier)
        DatoDeLaPersona("Parcialidad", parcialidad)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        saldo(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Parcialidad",
                style = MspTheme.type.body.copy(lineHeight = 16.sp),
                color = MspTheme.colors.onSurfaceMuted
            )
            Text(
                text = parcialidad,
                style = MspTheme.type.cardTitle.copy(lineHeight = 21.sp),
                color = MspTheme.colors.onSurface,
                textAlign = TextAlign.End
            )
        }
    }
}

/**
 * Las cifras de la venta en pastillas: total y abonado primero (lo que más se
 * pregunta), luego contado, enganche y el precio a corto plazo cuando lo hay.
 *
 * Se deslizan de lado dentro de la tarjeta; el desvanecido del borde avisa que
 * hay más de ese lado y desaparece al llegar al final.
 */
@Composable
private fun PastillasDeLaVenta(detalle: DetalleVenta, ocultos: Boolean) {
    val cifras = listOfNotNull(
        "Total venta" to detalle.totalVenta,
        "Abonado" to detalle.abonado,
        "Contado" to detalle.precioContado,
        "Enganche" to detalle.enganche,
        precioCorto(detalle)?.let { (plazo, monto) -> "A $plazo" to monto }
    )
    val deslizable = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val antes = if (deslizable.canScrollBackward) FADE_AL_INICIO else 0f
                val despues = if (deslizable.canScrollForward) FADE_AL_FINAL else 1f
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent.copy(alpha = if (antes > 0f) 0f else 1f),
                        antes to Color.Black,
                        despues to Color.Black,
                        1f to Color.Black.copy(alpha = if (despues < 1f) 0f else 1f)
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
            .horizontalScroll(deslizable)
            .padding(
                start = MspTheme.spacing.md,
                end = MspTheme.spacing.md,
                top = 12.dp,
                bottom = 14.dp
            )
            .testTag(PASTILLAS_DE_LA_VENTA_TAG),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        cifras.forEach { (rotulo, monto) -> Pastilla(rotulo, monto, ocultos) }
    }
}

@Composable
private fun Pastilla(rotulo: String, monto: Money, ocultos: Boolean) {
    val forma = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .clip(forma)
            .background(MspTheme.colors.surface2)
            .border(1.dp, MspTheme.colors.outline, forma)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(
            text = rotulo,
            style = MspTheme.type.caption.copy(lineHeight = 13.sp),
            color = MspTheme.colors.onSurfaceMuted,
            maxLines = 1
        )
        MspMoneyText(
            amount = monto.amount,
            masked = ocultos,
            style = MspTheme.type.heroStatValue.copy(lineHeight = 18.sp),
            color = MspTheme.colors.onSurface
        )
    }
}

/** El aval y el último abono de ESTA venta. Lo que no hay, no se pinta. */
@Composable
private fun PersonasDeLaVenta(detalle: DetalleVenta) {
    val aval = detalle.aval.takeIf { it.isNotBlank() }
    val ultimo = detalle.ultimoPago
    if (aval == null && ultimo == null) return
    Separador()
    Column(
        modifier = Modifier.padding(horizontal = MspTheme.spacing.md, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        aval?.let { DatoDeLaPersona("Aval o responsable", it) }
        ultimo?.let {
            DatoDeLaPersona(
                "Último pago",
                DIA_Y_MES.format(it),
                Modifier.testTag(ULTIMO_PAGO_DE_VENTA_TAG)
            )
        }
    }
}

@Composable
private fun DatoDeLaPersona(rotulo: String, valor: String, modifier: Modifier = Modifier) {
    // UN texto con dos pesos y no dos `Text` en fila: así un nombre largo a
    // letra grande sigue el renglón en vez de partirse en una columna angosta.
    val fuerte = MspTheme.type.methodLabel
    Text(
        text = buildAnnotatedString {
            append(rotulo)
            append(" ")
            withStyle(
                SpanStyle(fontWeight = fuerte.fontWeight, color = MspTheme.colors.onSurface)
            ) {
                append(valor)
            }
        },
        style = MspTheme.type.body,
        color = MspTheme.colors.onSurfaceMuted,
        modifier = modifier
    )
}

/** Lo que el nombre del cliente le deja a la esquina del ojo y el sol/luna. */
private val ESQUINA_DE_LOS_CONTROLES = 116.dp

/** Desvanecido del borde de las pastillas: 7 % al inicio, 16 % al final (el mock). */
private const val FADE_AL_INICIO = 0.07f
private const val FADE_AL_FINAL = 0.84f

/**
 * **Lo que ha pasado con esta cuenta**, y sólo con esta cuenta.
 *
 * Hasta el detalle previo esta sección tenía dos pastillas de alcance —*"Esta
 * venta"* / *"Todo el cliente"*— y un filtro por tipo. El dueño las quitó: la
 * pantalla se llama detalle de VENTA y mezclar aquí lo de otras cuentas del
 * mismo cliente confundía de quién era cada renglón. Los filtros por tipo y el
 * alcance de cliente completo siguen existiendo, pero en la bitácora —"ver los
 * N contactos"—, que es la lista larga donde de verdad hacen falta.
 *
 * ## Qué se pinta aquí
 *
 * Sólo los [com.example.msp_app.feature.pagos.domain.BitacoraDelCliente.VISIBLES_EN_LA_VENTA]
 * contactos más recientes cuyo [com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza.ventaId]
 * es el de esta venta, agrupados por mes con [GruposDeContactos.muestraPorMes]
 * —**sin subtotal**, porque son una muestra y no la historia completa (ver su
 * KDoc: sumar sobre una muestra es una cifra falsa en una pantalla de dinero).
 *
 * Debajo, "Ver los N contactos" —[onVerContactos]— lleva a la bitácora
 * COMPLETA de esta cuenta, con `N` = el total de contactos de esta venta, no
 * los cinco que se alcanzan a pintar. Se muestra siempre que haya al menos uno,
 * aunque quepan los cinco en la muestra: es la puerta a los filtros por tipo
 * que esta sección ya no tiene.
 *
 * "Ver los N abonos" —[onVerAbonos]— es aparte y LEGADO: sigue abriendo la
 * pantalla vieja donde vive la condonación. El dueño lo pidió explícito:
 * las dos puertas se quedan, una al lado de la otra.
 *
 * ## `internal` y no `private`, sólo para que tenga golden propio
 *
 * La sección vive muy por debajo del pliegue —después del saldo, el ritmo y la
 * liquidación— así que los `pagos_venta_*` de pantalla completa nunca la
 * retratan; es el mismo motivo por el que la garantía y el historial tienen los
 * suyos.
 */
@Composable
internal fun LineaDeLaVenta(
    detalle: DetalleVenta,
    onVerAbonos: () -> Unit,
    onVerContactos: () -> Unit = {},
    onVerUbicacion: ((UbicacionDelCobro, pagoId: String) -> Unit)? = null,
    toque: ToqueDeLaFila = ToqueDeLaFila(),
    ocultos: Boolean = false
) {
    // SÓLO lo de esta cuenta: a diferencia de `toque` —que necesita la línea del
    // CLIENTE entero para decidir "el último cobro de la cuenta", ver el KDoc de
    // `ToqueDelContacto.de`—, lo que esta sección PINTA es siempre de esta
    // venta. `detalle.contactos` ya viene ordenado de lo más reciente a lo más
    // viejo (ver `BitacoraDelCliente.de`), así que tomar el prefijo es tomar los
    // más recientes.
    val deEstaVenta = detalle.contactos.filter { it.ventaId == detalle.ventaId }
    val visibles = deEstaVenta.take(BitacoraDelCliente.VISIBLES_EN_LA_VENTA)
    if (visibles.isEmpty()) {
        Tarjeta {
            Text(
                text = "Sin movimientos en esta cuenta",
                style = MspTheme.type.body,
                color = MspTheme.colors.onSurfaceMuted
            )
        }
    } else {
        // Aquí sí cabe el bloque entero: esta sección vive en un `Column` con
        // scroll, no en una lista perezosa, así que el mes puede ser UN
        // contenedor. La bitácora tiene que repartirlo tramo por tramo — ver
        // `tramoDelMes`. Las dos comparten el tono y la forma.
        //
        // `muestraPorMes` y no `porMes`: esto es una MUESTRA (los cinco más
        // recientes), y un subtotal sobre una muestra es una cifra falsa. Ver
        // el KDoc de `GruposDeContactos.muestraPorMes`.
        GruposDeContactos.muestraPorMes(visibles).forEachIndexed { indice, grupo ->
            BloqueDelMes(indice = indice) {
                EncabezadoDeGrupo(grupo, ocultos = ocultos)
                grupo.contactos.forEach { contacto ->
                    ContactoEnLinea(
                        contacto = contacto,
                        ocultos = ocultos,
                        deEstaVenta = true,
                        onVerUbicacion = onVerUbicacion,
                        toque = toque
                    )
                }
            }
        }
    }
    // "Ver los N contactos" cuenta TODOS los de esta venta, no sólo los cinco
    // que la muestra pinta — es la puerta a verlos completos. Se enseña con
    // cualquier cantidad mayor que cero, aunque los cinco ya hayan cabido: es
    // también la única puerta a los filtros por tipo, que esta sección no
    // tiene.
    if (deEstaVenta.isNotEmpty()) {
        Spacer(Modifier.height(MspTheme.spacing.sm))
        VerLosContactos(cuantos = deEstaVenta.size, onVer = onVerContactos)
    }
    // Aparte y SIEMPRE que haya abonos, sin depender de lo de arriba: este
    // enlace lleva a la pantalla legada donde vive la condonación, y el dueño
    // pidió que se quedara al lado de "ver los N contactos", no en su lugar.
    val totalPagos = detalle.historial.totalPagos
    if (totalPagos > 0) {
        Spacer(Modifier.height(MspTheme.spacing.sm))
        // "Ver 1 abono" en singular — mismo defecto y mismo arreglo que
        // `VerLosContactos`: "Ver los 1 abonos" lee mal.
        val texto = if (totalPagos == 1) "Ver 1 abono" else "Ver los $totalPagos abonos"
        VerTodos(texto, onVerAbonos)
    }
}
