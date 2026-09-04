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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.DetalleCliente
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import com.example.msp_app.feature.pagos.ui.components.AfordanteDeLaFicha
import com.example.msp_app.feature.pagos.ui.components.BarraDeDetalle
import com.example.msp_app.feature.pagos.ui.components.CuadroDeEstado
import com.example.msp_app.feature.pagos.ui.components.DockDeAcciones
import com.example.msp_app.feature.pagos.ui.components.FilaClaveValor
import com.example.msp_app.feature.pagos.ui.components.FilaDeContacto
import com.example.msp_app.feature.pagos.ui.components.FilaDeVenta
import com.example.msp_app.feature.pagos.ui.components.HojaDeLaFicha
import com.example.msp_app.feature.pagos.ui.components.LabelDeSeccion
import com.example.msp_app.feature.pagos.ui.components.SIN_DATO
import com.example.msp_app.feature.pagos.ui.components.SeccionDeLaFicha
import com.example.msp_app.feature.pagos.ui.components.TarjetaDeLiquidacion
import com.example.msp_app.feature.pagos.ui.components.TarjetaDeSaldo
import com.example.msp_app.feature.pagos.ui.components.VerTodos
import java.time.format.DateTimeFormatter

/** `testTag` del título de la pantalla — el nombre del cliente. */
const val TITULO_DE_CLIENTE_TAG: String = "pagos_titulo_cliente"

/** `testTag` del aviso de cuentas pendientes del encabezado. */
const val AVISO_DE_CUENTAS_TAG: String = "pagos_aviso_cuentas"

private val DIA_Y_MES: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", BUSINESS_LOCALE)

/**
 * El destino: conecta el ViewModel con el contenido puro.
 *
 * ## La cuenta que encabeza "sus ventas" (arreglo de la Task 21)
 *
 * Antes esta función pasaba el **`clienteId`** a `onRegistrarAbono`. El destino
 * del abono es `pagos/abono/{ventaId}`, así que ese id aterrizaba en el lugar de
 * un `DOCTO_CC_ACR_ID` y la pantalla del dinero abría una venta que no era la
 * del cliente —o ninguna—. Un cliente no tiene saldo que cobrar: lo tienen sus
 * cuentas.
 *
 * La cuenta elegida es **la primera de `detalle.ventas`**, que es exactamente la
 * fila de arriba de "sus ventas" en la pantalla que el cobrador está mirando —
 * no una elección escondida— y la misma que `CargarDetalleCliente` ya usa como
 * representante del cliente (nombre, teléfono, zona, aval). La pantalla del
 * abono encabeza con el folio, el producto y el saldo de esa venta, así que un
 * cliente con varias cuentas ve cuál es antes de teclear un peso.
 *
 * `null` no es alcanzable en producción: el dock solo se pinta con `detalle`
 * cargado, y `CargarDetalleCliente` devuelve `null` cuando el cliente no tiene
 * ni una venta. El `?.let` está por totalidad, no por un caso vivo.
 */
@Composable
fun DetalleClienteScreen(
    viewModel: DetalleClienteViewModel,
    onAtras: () -> Unit,
    onAbrirVenta: (Int) -> Unit,
    onRegistrarAbono: (Int) -> Unit,
    onRegistrarVisita: (Int, Int?) -> Unit,
    onMasAcciones: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cuenta = cuentaQueEncabeza(state)
    DetalleClienteContent(
        state = state,
        onAtras = onAtras,
        onAbrirVenta = onAbrirVenta,
        onRegistrarAbono = { cuenta?.let(onRegistrarAbono) },
        onRegistrarVisita = { onRegistrarVisita(viewModel.clienteId, null) },
        onMasAcciones = { cuenta?.let(onMasAcciones) },
        onUsarLiquidacion = { cuenta?.let(onRegistrarAbono) },
        onVerContactos = { cuenta?.let(onMasAcciones) },
        fichaDelCliente = AccionesDeLaFicha(
            onEditar = viewModel::editarFicha,
            onCerrar = viewModel::cerrarFicha,
            onSenal = viewModel::alternarSenal,
            onNota = viewModel::escribirNota,
            onGuardar = viewModel::guardarFicha
        ),
        modifier = modifier
    )
}

/**
 * La cuenta a la que apunta el dock del cliente: **la primera de "sus ventas"**,
 * o sea la fila de arriba de la lista que el cobrador está mirando.
 *
 * Devuelve un `DOCTO_CC_ACR_ID`, **nunca** un `CLIENTE_ID`. Esa confusión era el
 * defecto: `pagos/abono/{ventaId}` recibía el id del cliente y la pantalla del
 * dinero abría una cuenta que no era la suya —o ninguna—. Un cliente no tiene
 * saldo que cobrar; lo tienen sus cuentas.
 *
 * **"La primera" es una cuenta concreta, no la que tocó.** El orden de esa lista
 * lo fija
 * [com.example.msp_app.feature.pagos.application.CargarDetalleCliente] con un
 * desempate total antes de emitir, así que lo que se pinta arriba y lo que se
 * cobra son la misma cuenta **por construcción**. Este `firstOrNull` no elige
 * nada: lee la decisión que el caso de uso ya tomó. Que el orden aquí fuera el
 * azar de `SaleDao.getByClientId` —que agrupa sin `ORDER BY`— era el defecto que
 * la ronda 1 de arreglo cerró.
 *
 * `null` solo cuando todavía no hay detalle cargado, y en ese estado el dock ni
 * se pinta.
 */
internal fun cuentaQueEncabeza(state: DetalleClienteUiState): Int? =
    state.detalle?.ventas?.firstOrNull()?.ventaId

/**
 * El detalle de cliente. **El nombre del cliente es el título** y sus ventas
 * van dentro, cada una con su estado — decisiones del `task-16-brief.md`.
 *
 * Composable PURO sobre [DetalleClienteUiState]: no lee puertos, no deriva
 * estados y no emite telemetría. Lo primero lo hace el ViewModel, lo segundo el
 * catálogo de la Task 14, y lo tercero
 * [com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo] una
 * vez por sincronización — emitir desde aquí sería emitir una vez por
 * recomposición.
 */
@Composable
fun DetalleClienteContent(
    state: DetalleClienteUiState,
    onAtras: () -> Unit,
    onAbrirVenta: (Int) -> Unit,
    onRegistrarAbono: () -> Unit,
    onRegistrarVisita: () -> Unit,
    onMasAcciones: () -> Unit,
    onUsarLiquidacion: () -> Unit,
    onVerContactos: () -> Unit,
    modifier: Modifier = Modifier,
    fichaDelCliente: AccionesDeLaFicha = AccionesDeLaFicha()
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MspTheme.colors.background)
    ) {
        val detalle = state.detalle
        Box(modifier = Modifier.weight(1f)) {
            when {
                state.cargando -> Cargando()
                detalle == null -> MensajeDeError(state.error, onAtras)
                else -> CuerpoDelCliente(
                    detalle = detalle,
                    onAtras = onAtras,
                    onAbrirVenta = onAbrirVenta,
                    onUsarLiquidacion = onUsarLiquidacion,
                    onVerContactos = onVerContactos,
                    onEditarFicha = fichaDelCliente.onEditar
                )
            }
        }
        if (detalle != null) {
            DockDeAcciones(
                textoPrimario = "registrar abono",
                onPrimario = onRegistrarAbono,
                onVisita = onRegistrarVisita,
                onMasAcciones = onMasAcciones
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
}

/**
 * Las cinco acciones de la ficha, juntas.
 *
 * Van en un objeto y no en cinco parámetros sueltos porque
 * [DetalleClienteContent] ya recibe ocho lambdas y detekt corta ahí
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
    onAtras: () -> Unit,
    onAbrirVenta: (Int) -> Unit,
    onUsarLiquidacion: () -> Unit,
    onVerContactos: () -> Unit,
    onEditarFicha: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MspTheme.spacing.md)
    ) {
        BarraDeDetalle(
            onAtras = onAtras,
            accion = {
                AfordanteDeLaFicha(ficha = detalle.ficha, onEditar = onEditarFicha)
            }
        )
        EncabezadoDelCliente(detalle)
        Spacer(Modifier.height(MspTheme.spacing.md))
        TarjetaDeSaldo(
            label = "saldo total",
            monto = detalle.saldoTotal,
            pie = { PieDelSaldo(detalle) }
        )
        EstadoCuentaUi.avisoDeCuentas(detalle.ventas.map { it.estado })?.let { aviso ->
            Spacer(Modifier.height(MspTheme.spacing.sm))
            Aviso(aviso)
        }

        LabelDeSeccion("sus ventas")
        detalle.ventas.forEach { venta ->
            FilaDeVenta(venta = venta, onAbrir = { onAbrirVenta(venta.ventaId) })
            Spacer(Modifier.height(MspTheme.spacing.sm))
        }

        detalle.liquidacion?.let { liquidacion ->
            LabelDeSeccion("liquidación")
            TarjetaDeLiquidacion(
                label = "hoy liquida todo con",
                liquidacion = liquidacion,
                onUsar = onUsarLiquidacion
            )
        }

        if (detalle.contactos.isNotEmpty()) {
            LabelDeSeccion("últimos contactos")
            detalle.contactos.forEach { FilaDeContacto(it) }
            Spacer(Modifier.height(MspTheme.spacing.sm))
            VerTodos("ver los ${detalle.totalContactos} contactos", onVerContactos)
        }

        // La ficha vive AL FONDO, en el mismo lugar donde la Task 16 puso su
        // antecesora: así no empuja un solo dp de lo que está arriba, que es
        // el dinero — por lo que el cobrador abrió esta pantalla.
        // `LaFichaSeVeYSeTocaTest` lo mide. Lo que sí sube, y gratis, es el
        // afordante de la barra: ahí es donde una advertencia grita.
        SeccionDeLaFicha(
            ficha = detalle.ficha,
            notaDeLaVenta = detalle.notaDeLaVenta,
            onEditar = onEditarFicha
        )

        LabelDeSeccion("datos del cliente")
        FilaClaveValor("zona", detalle.zona)
        FilaClaveValor("aval o responsable", detalle.aval)
        // El mock pide el teléfono DEL AVAL, no el del cliente: ese ya está en el
        // encabezado, y a quien el cobrador llama cuando el cliente no contesta es
        // al aval. Se pinta solo cuando el dato existe (hoy no existe la columna,
        // ver `DetalleCliente.telefonoAval`); una fila permanentemente en "—" es
        // el mismo ruido que se quitó del chip de saldo.
        detalle.telefonoAval?.let { FilaClaveValor("teléfono del aval", it) }
        FilaClaveValor("dirección", detalle.direccion)
        Spacer(Modifier.height(MspTheme.spacing.lg))
    }
}

@Composable
private fun EncabezadoDelCliente(detalle: DetalleCliente) {
    Row(horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)) {
            detalle.ventas.take(CUADROS_EN_EL_CLUSTER).forEach { venta ->
                CuadroDeEstado(estadoVisualDe(venta.estado), lado = 22.dp)
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = detalle.nombre,
                style = MspTheme.type.detailTitle,
                color = MspTheme.colors.onSurface,
                modifier = Modifier.testTag(TITULO_DE_CLIENTE_TAG)
            )
            Text(
                text = listOf(detalle.telefono, detalle.zona).filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { SIN_DATO },
                style = MspTheme.type.subtitle,
                color = MspTheme.colors.onSurfaceMuted
            )
            Text(
                text = detalle.direccion.ifBlank { SIN_DATO },
                style = MspTheme.type.subtitle,
                color = MspTheme.colors.onSurfaceMuted
            )
            Spacer(Modifier.height(MspTheme.spacing.sm))
            Text(
                text = "${detalle.cuentas} cuentas",
                style = MspTheme.type.chipLabel,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier
                    .clip(MspTheme.shapes.control)
                    .background(MspTheme.colors.surface2)
                    .padding(horizontal = MspTheme.spacing.sm, vertical = MspTheme.spacing.xs)
            )
        }
    }
}

@Composable
private fun PieDelSaldo(detalle: DetalleCliente) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${detalle.cuentas} cuentas activas",
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = detalle.ultimaVisita
                ?.let { "última visita " + DIA_Y_MES.format(AppTime.toBusinessDate(it)) }
                ?: "sin visitas registradas",
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted
        )
    }
}

/** La banda del encabezado (`.tip`): ámbar de "vuelvo", nunca rojo de "no cae". */
@Composable
private fun Aviso(texto: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MspTheme.shapes.field)
            .background(MspTheme.colors.statusPartialTint)
            .padding(MspTheme.spacing.md)
    ) {
        Text(
            text = texto,
            style = MspTheme.type.bodyStrong,
            color = MspTheme.colors.statusPartial,
            modifier = Modifier.testTag(AVISO_DE_CUENTAS_TAG)
        )
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
                ErrorDeDetalle.NO_ESTA_EN_EL_TELEFONO -> "no está en el teléfono"
                else -> "no se pudo abrir"
            },
            style = MspTheme.type.cardTitle,
            color = MspTheme.colors.onSurface
        )
        Spacer(Modifier.height(MspTheme.spacing.sm))
        VerTodos("volver", onAtras)
    }
}

private const val CUADROS_EN_EL_CLUSTER = 4
