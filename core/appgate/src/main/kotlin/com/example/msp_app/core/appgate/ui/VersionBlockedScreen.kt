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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.appgate.download.DownloadProgress
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
        verticalArrangement = Arrangement.Center
    ) {
        if (state.deadlineLabel.isNotBlank()) {
            UpdateCountdownBand(
                deadlineLabel = state.deadlineLabel,
                ready = state.stage is UpdateStage.ReadyToInstall,
                onAction = if (state.stage is UpdateStage.ReadyToInstall) onInstall else onDownload
            )
            Spacer(modifier = Modifier.height(MspTheme.spacing.lg))
        }

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
 */
@Composable
private fun StageProgress(stage: UpdateStage) {
    val progress = stage.progress() ?: return
    val colors = MspTheme.colors
    val paused = stage is UpdateStage.Failed
    Spacer(modifier = Modifier.height(MspTheme.spacing.md))
    MspProgressBar(
        progress = progress.fraction,
        height = PROGRESS_HEIGHT,
        fillColor = if (paused) colors.onSurfaceMuted else colors.brand,
        trackColor = colors.progressTrack,
        modifier = Modifier.testTag(VERSION_BLOCKED_PROGRESS_TAG)
    )
    Spacer(modifier = Modifier.height(MspTheme.spacing.xs))
    Text(
        text = if (paused) {
            "${progress.megabytesLabel()} · en pausa"
        } else {
            "${progress.megabytesLabel()} · ${progress.percent}%"
        },
        style = MspTheme.type.caption,
        color = colors.onSurfaceMuted
    )
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

        is UpdateStage.Downloading -> GateActionButton(
            text = "Instalar",
            onClick = {},
            fillColor = colors.outline,
            contentColor = colors.onSurfaceMuted,
            enabled = false,
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
            GateActionButton(
                text = "Descargar",
                onClick = {},
                fillColor = colors.outline,
                contentColor = colors.onSurfaceMuted,
                enabled = false,
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

        is UpdateStage.Failed -> GateActionButton(
            text = "Reintentar",
            onClick = onDownload,
            fillColor = colors.brand,
            contentColor = colors.onBrand,
            tag = VERSION_BLOCKED_ACTION_TAG
        )
    }
}

private fun UpdateStage.title(): String = when (this) {
    UpdateStage.ReadyToInstall -> "Actualiza para continuar"
    is UpdateStage.Downloading -> "Descargando la actualización"
    is UpdateStage.MeteredOnly -> "Actualiza para continuar"
    UpdateStage.Offline -> "Sin conexión"
    is UpdateStage.Failed -> "No se completó la descarga"
}

private fun UpdateStage.explanation(): String = when (this) {
    UpdateStage.ReadyToInstall ->
        "La actualización ya está descargada en tu teléfono. No usa datos."

    is UpdateStage.Downloading ->
        "Conectado a wifi. Puedes dejar el teléfono, sigue sola."

    is UpdateStage.MeteredOnly ->
        "La descarga automática espera al wifi. Si no puedes esperar, descárgala con tus datos."

    UpdateStage.Offline ->
        "Conéctate a wifi o datos para descargar la actualización."

    is UpdateStage.Failed ->
        "Se interrumpió en ${progress.megabytesLabel()}. Al reintentar continúa desde ahí."
}

private fun UpdateStage.icon(): ImageVector = when (this) {
    UpdateStage.ReadyToInstall -> Icons.Filled.Check
    is UpdateStage.Downloading, is UpdateStage.MeteredOnly -> Icons.Filled.KeyboardArrowDown
    UpdateStage.Offline -> Icons.Filled.Warning
    is UpdateStage.Failed -> Icons.Filled.Refresh
}

/** Solo dos estados llevan barra: los que tienen bytes que enseñar. */
private fun UpdateStage.progress(): DownloadProgress? = when (this) {
    is UpdateStage.Downloading -> progress
    is UpdateStage.Failed -> progress
    else -> null
}
