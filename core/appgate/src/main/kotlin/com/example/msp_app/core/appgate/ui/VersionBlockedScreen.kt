package com.example.msp_app.core.appgate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.appgate.download.DownloadProgress
import com.example.msp_app.core.appgate.download.formatMegabytes
import com.example.msp_app.core.appgate.download.megabytesLabel
import com.example.msp_app.core.designsystem.component.MspProgressBar
import com.example.msp_app.core.designsystem.theme.MspTheme

/** `testTag` de la barra de progreso de la descarga. */
const val VERSION_BLOCKED_PROGRESS_TAG = "msp_version_blocked_progress"

/** `testTag` del botón principal (el único de cada estado, salvo "sin conexión"). */
const val VERSION_BLOCKED_ACTION_TAG = "msp_version_blocked_action"

/** `testTag` de la acción secundaria "Reintentar" del estado sin conexión. */
const val VERSION_BLOCKED_RETRY_TAG = "msp_version_blocked_retry"

private val MARK_SIZE = 56.dp
private val ICON_SIZE = 28.dp
private val PROGRESS_HEIGHT = 7.dp
private val SCREEN_PADDING = 24.dp
private val TOP_SPACE = 32.dp

/**
 * Pantalla de bloqueo por versión mínima.
 *
 * Sin salida y sin drawer: se llega con `popUpTo(0) { inclusive = true }`
 * desde `AppNavigation`, igual que la de dispositivo no autorizado. A
 * diferencia de aquella, **no lleva "Cerrar sesión"**: cerrar sesión no
 * arreglaría nada y solo agregaría una salida falsa.
 *
 * **No es un error y no debe parecerlo** — nadie hizo nada mal. De ahí que no
 * use el rojo de `danger` en ningún estado, ni un ícono de alerta salvo cuando
 * literalmente no hay señal.
 */
@Composable
fun VersionBlockedScreen(viewModel: VersionGateViewModel = hiltViewModel()) {
    val state by viewModel.blockedState.collectAsStateWithLifecycle()
    MspTheme {
        VersionBlockedContent(
            state = state,
            onInstall = viewModel::install,
            onDownload = viewModel::downloadNow
        )
    }
}

/**
 * Cuerpo sin estado de [VersionBlockedScreen] — el punto de entrada de los
 * compose-tests (mismo criterio "stateless, spy vía lambda" que
 * `ConfiguracionContent`).
 *
 * Contenido **arriba**, no centrado: con la banda puesta y a `fontScale = 2`
 * el centrado dejaba dos franjas negras enormes y empujaba el botón fuera de
 * la primera pantalla.
 */
@Composable
fun VersionBlockedContent(
    state: VersionBlockedUiState,
    onInstall: () -> Unit,
    onDownload: () -> Unit
) {
    val colors = MspTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(SCREEN_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // La banda depende de que HAYA una actualización de la que hablar, no
        // de que alguien haya escrito la fecha límite: sin `MIN_VERSION_DEADLINE`
        // seguía siendo cierto que hay un archivo que instalar, y la banda
        // simplemente no se montaba (el defecto que se veía en campo).
        if (state.stage.showsCountdownBand()) {
            UpdateCountdownBand(
                deadlineLabel = state.deadlineLabel,
                ready = state.stage is UpdateStage.ReadyToInstall,
                onAction = if (state.stage is UpdateStage.ReadyToInstall) onInstall else onDownload
            )
        }

        Spacer(modifier = Modifier.height(TOP_SPACE))
        StageMark(stage = state.stage)
        Spacer(modifier = Modifier.height(MspTheme.spacing.md))

        Text(
            text = state.stage.title(),
            style = MspTheme.type.screenTitle,
            color = colors.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(MspTheme.spacing.sm))
        Text(
            text = state.stage.explanation(),
            style = MspTheme.type.body,
            color = colors.onSurfaceMuted,
            textAlign = TextAlign.Center
        )

        StageProgress(stage = state.stage)

        Spacer(modifier = Modifier.height(MspTheme.spacing.lg))
        StageActions(stage = state.stage, onInstall = onInstall, onDownload = onDownload)

        Spacer(modifier = Modifier.height(MspTheme.spacing.md))
        Text(
            text = "Tienes ${state.installedVersionName} · necesitas ${state.requiredVersionName}",
            style = MspTheme.type.caption,
            color = colors.onSurfaceMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StageMark(stage: UpdateStage) {
    val colors = MspTheme.colors
    val ready = stage is UpdateStage.ReadyToInstall
    val tint = if (ready) colors.statusPaid else colors.onSurfaceMuted
    Box(
        modifier = Modifier
            .size(MARK_SIZE)
            .clip(MspTheme.shapes.control)
            .background(if (ready) colors.statusPaidTint else colors.surface2),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = stage.icon(),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(ICON_SIZE)
        )
    }
}

/**
 * Progreso en **megas**, no una rueda. Una rueda indeterminada es la que
 * termina en una llamada por teléfono preguntando si se quedó trabada.
 *
 * Sin tamaño total conocido no se pinta barra ni porcentaje: "0 de 0 MB · 0%"
 * es peor que no decir nada.
 */
@Composable
private fun StageProgress(stage: UpdateStage) {
    val progress = stage.progress() ?: return
    val colors = MspTheme.colors
    Spacer(modifier = Modifier.height(MspTheme.spacing.md))
    if (progress.totalBytes > 0L) {
        MspProgressBar(
            progress = progress.fraction,
            height = PROGRESS_HEIGHT,
            fillColor = if (stage.paused()) colors.onSurfaceMuted else colors.brand,
            trackColor = colors.progressTrack,
            modifier = Modifier.testTag(VERSION_BLOCKED_PROGRESS_TAG)
        )
        Spacer(modifier = Modifier.height(MspTheme.spacing.xs))
    }
    Text(
        text = progress.label(stage),
        style = MspTheme.type.caption,
        color = colors.onSurfaceMuted
    )
}

private fun DownloadProgress.label(stage: UpdateStage): String = when {
    totalBytes <= 0L -> "${formatMegabytes(downloadedBytes)} MB"
    stage is UpdateStage.Failed -> "${megabytesLabel()} · en pausa"
    stage is UpdateStage.Stalled -> "${megabytesLabel()} · sin avance"
    else -> "${megabytesLabel()} · $percent%"
}

@Composable
private fun StageActions(stage: UpdateStage, onInstall: () -> Unit, onDownload: () -> Unit) {
    val colors = MspTheme.colors
    when (stage) {
        UpdateStage.ReadyToInstall -> GateActionButton(
            text = "Instalar",
            onClick = onInstall,
            fillColor = colors.statusPaid,
            contentColor = colors.onBrand,
            tag = VERSION_BLOCKED_ACTION_TAG
        )

        // Deshabilitado, pero el texto dice POR QUÉ. Un "Instalar" gris sin
        // explicación se lee como app rota.
        is UpdateStage.Downloading -> DisabledGateActionButton(
            text = "Instalar al terminar",
            tag = VERSION_BLOCKED_ACTION_TAG
        )

        is UpdateStage.MeteredOnly -> GateActionButton(
            text = "Descargar con datos · ${megabytesLabel(stage.sizeBytes)}",
            onClick = onDownload,
            fillColor = colors.brand,
            contentColor = colors.onBrand,
            tag = VERSION_BLOCKED_ACTION_TAG
        )

        UpdateStage.Offline -> {
            DisabledGateActionButton(
                text = "Descargar al reconectar",
                tag = VERSION_BLOCKED_ACTION_TAG
            )
            Spacer(modifier = Modifier.height(MspTheme.spacing.sm))
            GateActionButton(
                text = "Reintentar",
                onClick = onDownload,
                fillColor = Color.Transparent,
                contentColor = colors.brand,
                tag = VERSION_BLOCKED_RETRY_TAG
            )
        }

        is UpdateStage.Failed, is UpdateStage.Stalled -> GateActionButton(
            text = "Reintentar",
            onClick = onDownload,
            fillColor = colors.brand,
            contentColor = colors.onBrand,
            tag = VERSION_BLOCKED_ACTION_TAG
        )

        // Sin botón a propósito: no hay nada que el cobrador pueda tocar para
        // arreglar una configuración que no es suya. Ofrecerle uno que no
        // sirve sería peor que no ofrecer ninguno.
        UpdateStage.Unavailable, is UpdateStage.Unusable -> Unit
    }
}

/**
 * Variante deshabilitada, con relleno propio: `outline` sobre el fondo negro
 * dejaba un gris casi invisible (lo que se veía en el teléfono real).
 * `surface2` + `onSurfaceMuted` pasa el contraste 4.5:1 en los dos temas.
 */
@Composable
private fun DisabledGateActionButton(text: String, tag: String) {
    GateActionButton(
        text = text,
        onClick = {},
        fillColor = MspTheme.colors.surface2,
        contentColor = MspTheme.colors.onSurfaceMuted,
        enabled = false,
        tag = tag
    )
}

/** Solo llevan barra los estados que tienen bytes que enseñar. */
private fun UpdateStage.progress(): DownloadProgress? = when (this) {
    is UpdateStage.Downloading -> progress
    is UpdateStage.Failed -> progress
    is UpdateStage.Stalled -> progress
    else -> null
}

/** La barra se pinta apagada cuando nada se está moviendo. */
private fun UpdateStage.paused(): Boolean =
    this is UpdateStage.Failed || this is UpdateStage.Stalled

/**
 * La banda solo aparece si existe un archivo del que hablar: sin APK
 * publicado, o con uno que no sirve, su acción ("Descargar"/"Instalar") no
 * llevaría a ninguna parte.
 */
private fun UpdateStage.showsCountdownBand(): Boolean =
    this != UpdateStage.Unavailable && this !is UpdateStage.Unusable
