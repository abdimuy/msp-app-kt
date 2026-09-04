@file:Suppress(
    "TooManyFunctions"
) // una pieza por fold del mock; juntarlas escondería qué pinta cada desenlace.

package com.example.msp_app.feature.visitas.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.common.cobranza.domain.VisitScope
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.visitas.domain.CatalogoDeResultados
import com.example.msp_app.feature.visitas.domain.DiasSugeridos
import com.example.msp_app.feature.visitas.domain.ReglasDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.ui.components.AvisoDeAlcance
import com.example.msp_app.feature.visitas.ui.components.BandaDeFallo
import com.example.msp_app.feature.visitas.ui.components.BandaDeRecomendacion
import com.example.msp_app.feature.visitas.ui.components.BarraDeVisita
import com.example.msp_app.feature.visitas.ui.components.BotonDeFotoEnLinea
import com.example.msp_app.feature.visitas.ui.components.CHIP_TAG
import com.example.msp_app.feature.visitas.ui.components.CalendarioDeVisita
import com.example.msp_app.feature.visitas.ui.components.CampoDeMonto
import com.example.msp_app.feature.visitas.ui.components.CampoDeNota
import com.example.msp_app.feature.visitas.ui.components.ChipDeOpcion
import com.example.msp_app.feature.visitas.ui.components.ChipDeVenta
import com.example.msp_app.feature.visitas.ui.components.DockDeLaVisita
import com.example.msp_app.feature.visitas.ui.components.ETIQUETA_TAG
import com.example.msp_app.feature.visitas.ui.components.OpcionDeResultado
import com.example.msp_app.feature.visitas.ui.components.RelojDeLaCita
import com.example.msp_app.feature.visitas.ui.components.RotuloDeSeccion
import com.example.msp_app.feature.visitas.ui.components.SeccionDeComprobantesDeVisita
import com.example.msp_app.feature.visitas.ui.components.TarjetaDelFold
import com.example.msp_app.feature.visitas.ui.components.TiraDelCliente
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * El destino: conecta el ViewModel con el contenido puro.
 *
 * [onRegistrada] se dispara UNA vez, con el id de la visita, dentro de un
 * `LaunchedEffect` con clave el propio id — una recomposición no lo repite.
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
    RegistrarVisitaContent(
        state = state,
        acciones = AccionesDeLaVisita(
            onAtras = onAtras,
            onResultado = viewModel::onResultado,
            onCambiarResultado = viewModel::limpiarResultado,
            onEtiqueta = viewModel::onEtiqueta,
            onNota = viewModel::onNota,
            onVentaDeLaPromesa = viewModel::onVentaDeLaPromesa,
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
            onAgregarFoto = viewModel::pedirFoto,
            onQuitarFoto = viewModel::quitarFoto
        ),
        modifier = modifier
    )
    val destino = state.destinoDeFoto
    if (destino != null) {
        // Clave el id del destino: una recomposición no reabre la cámara, y un
        // destino nuevo sí la abre.
        LaunchedEffect(destino.id) { camara.launch(Uri.parse(destino.uriParaLaCamara)) }
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
    val onVentaDeLaPromesa: (Int?) -> Unit,
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
            onVentaDeLaPromesa = {},
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
                    texto = if (state.registrada == null) "guardar visita" else "visita guardada",
                    habilitado = state.sePuedeGuardar,
                    razon = state.razonDelBloqueo,
                    onGuardar = acciones.onGuardar
                )
            }
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
            text = state.error?.mensaje ?: "no se pudo cargar",
            style = MspTheme.type.bodyStrong,
            color = MspTheme.colors.onSurface
        )
        ChipDeOpcion(
            texto = "volver a cargar",
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
        BarraDeVisita(
            onAtras = acciones.onAtras,
            alFinal = {
                BotonDeFotoEnLinea(
                    cuantos = state.comprobantes.size,
                    habilitado = state.sePuedeAgregarFoto,
                    onAgregar = acciones.onAgregarFoto
                )
            }
        )
        Text(
            text = "visita",
            style = MspTheme.type.screenTitle,
            color = MspTheme.colors.onSurface
        )
        TiraDelCliente(contexto)
        state.recomendacion?.let {
            BandaDeRecomendacion("${it.etiquetaDePosicion} · ${it.motivo}")
        }
        state.fallo?.let { BandaDeFallo(it.mensaje) }
        RotuloDeSeccion("qué pasó")
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
            if (elegido.alcance == VisitScope.CLIENTE) AvisoDeAlcance(contexto.ventas.size)
            FoldDelResultado(elegido, state, acciones)
        }
        CampoDeNota(
            nota = state.captura.nota,
            habilitado = state.sePuedeCapturar,
            onCambio = acciones.onNota
        )
        SeccionDeComprobantesDeVisita(
            comprobantes = state.comprobantes,
            fallo = state.falloDeLaFoto,
            puedeAgregar = state.sePuedeAgregarFoto,
            onAgregar = acciones.onAgregarFoto,
            onQuitar = acciones.onQuitarFoto
        )
        Box(modifier = Modifier.padding(bottom = MspTheme.spacing.md))
    }
}

@Composable
private fun FoldDelResultado(
    resultado: ResultadoDeVisita,
    state: RegistrarVisitaUiState,
    acciones: AccionesDeLaVisita
) {
    TarjetaDelFold {
        if (CatalogoDeResultados.pideEtiqueta(resultado)) {
            RotuloDeSeccion("cómo estaba")
            FilaDeEtiquetas(resultado, state, acciones)
        }
        when (resultado) {
            ResultadoDeVisita.PROMETIO -> FoldDeLaPromesa(state, acciones)
            ResultadoDeVisita.CITA -> FoldDeLaCita(state, acciones)
            else -> Unit
        }
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
                modifier = Modifier.testTag(ETIQUETA_TAG + indice),
                contenidoActivo = MspTheme.colors.onSurface,
                fondoActivo = MspTheme.colors.surface2
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoldDeLaPromesa(state: RegistrarVisitaUiState, acciones: AccionesDeLaVisita) {
    val contexto = requireNotNull(state.contexto)
    if (contexto.ventas.size > 1) {
        RotuloDeSeccion("de cuál venta")
        contexto.ventas.forEach { venta ->
            ChipDeVenta(
                venta = venta,
                activo = state.captura.ventaDeLaPromesa == venta.ventaId,
                habilitado = state.sePuedeCapturar,
                onElegir = { acciones.onVentaDeLaPromesa(venta.ventaId) }
            )
        }
    }
    RotuloDeSeccion("cuándo")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        DiasSugeridos.paraPromesa(state.hoy).forEach { dia ->
            ChipDeDia(
                dia = dia,
                elegido = state.captura.fechaPromesa,
                state = state,
                contenido = MspTheme.colors.statusOverdue,
                fondo = MspTheme.colors.statusOverdueTint,
                onElegir = acciones.onFechaPromesa
            )
        }
        ChipDeOtroDia(state, acciones)
    }
    CampoDeMonto(
        digitos = digitosDe(state),
        habilitado = state.sePuedeCapturar,
        onCambio = { acciones.onMonto(montoDe(it)) }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoldDeLaCita(state: RegistrarVisitaUiState, acciones: AccionesDeLaVisita) {
    RotuloDeSeccion("qué día")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        DiasSugeridos.paraCita(state.hoy).forEach { dia ->
            ChipDeDia(
                dia = dia,
                elegido = state.captura.fechaCita,
                state = state,
                contenido = MspTheme.colors.promise,
                fondo = MspTheme.colors.promiseTint,
                onElegir = acciones.onFechaCita
            )
        }
        ChipDeOtroDia(state, acciones)
    }
    RotuloDeSeccion("a qué hora")
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
            modifier = Modifier.testTag(CHIP_TAG + "sin_hora"),
            contenidoActivo = MspTheme.colors.promise,
            fondoActivo = MspTheme.colors.promiseTint
        )
        DiasSugeridos.HORAS_SUGERIDAS.forEach { hora ->
            ChipDeOpcion(
                texto = DiasSugeridos.etiquetaDe(hora),
                activo = state.captura.horaCita == hora,
                habilitado = state.sePuedeCapturar,
                onElegir = { acciones.onHoraCita(hora) },
                modifier = Modifier.testTag(CHIP_TAG + "hora_${hora.hour}"),
                contenidoActivo = MspTheme.colors.promise,
                fondoActivo = MspTheme.colors.promiseTint
            )
        }
        ChipDeOpcion(
            texto = "otra hora",
            activo = false,
            habilitado = state.sePuedeCapturar,
            onElegir = acciones.onAbrirReloj,
            modifier = Modifier.testTag(CHIP_TAG + "otra_hora")
        )
    }
}

/**
 * Un chip de día. El acento es **el color del desenlace** (§3 de la tabla de
 * paleta): rojo en la promesa, violeta en la cita — no un azul genérico, porque
 * estos chips sí llevan estado.
 */
@Composable
private fun ChipDeDia(
    dia: LocalDate,
    elegido: LocalDate?,
    state: RegistrarVisitaUiState,
    contenido: Color,
    fondo: Color,
    onElegir: (LocalDate) -> Unit
) {
    ChipDeOpcion(
        texto = DiasSugeridos.etiquetaDe(dia, state.hoy),
        activo = elegido == dia,
        habilitado = state.sePuedeCapturar,
        onElegir = { onElegir(dia) },
        modifier = Modifier.testTag(CHIP_TAG + "dia_$dia"),
        contenidoActivo = contenido,
        fondoActivo = fondo
    )
}

@Composable
private fun ChipDeOtroDia(state: RegistrarVisitaUiState, acciones: AccionesDeLaVisita) {
    ChipDeOpcion(
        texto = DiasSugeridos.OTRO_DIA,
        activo = false,
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
