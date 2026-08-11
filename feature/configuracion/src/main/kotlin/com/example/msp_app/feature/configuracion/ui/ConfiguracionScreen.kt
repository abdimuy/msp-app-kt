package com.example.msp_app.feature.configuracion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.configuracion.domain.port.AppThemeMode
import com.example.msp_app.feature.configuracion.ui.components.AppearanceSection
import com.example.msp_app.feature.configuracion.ui.components.FontSizeSection

/** `testTag` del botón de regreso del header. */
const val CONFIGURACION_BACK_BUTTON_TAG = "msp_configuracion_back_button"

private val CONTENT_HORIZONTAL_PADDING = 16.dp
private val SECTION_GAP = 28.dp

/**
 * Punto de entrada de la pantalla de Configuración (Task 1, spec
 * `docs/superpowers/specs/2026-08-10-configuracion-tamano-letra-design.md`):
 * header con affordance de regreso + secciones "Tamaño de letra" y
 * "Apariencia". Igual que [com.example.msp_app.feature.collectionreport.ui.CollectionReportScreen]
 * (el otro piloto hexagonal), `:app` NUNCA provee [MspTheme] — este composable
 * es el ÚNICO punto que lo envuelve para su propio subárbol, resuelto con el
 * tema GLOBAL vigente (`state.themeMode`, no un espejo local: a diferencia
 * del reporte de cobranza, esta pantalla SÍ es dueña del tema real de la
 * app vía [AppThemeMode]).
 */
@Composable
fun ConfiguracionScreen(
    navController: NavController,
    viewModel: ConfiguracionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (state.themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> systemDark
    }
    MspTheme(darkTheme = darkTheme) {
        ConfiguracionContent(
            state = state,
            onBack = { navController.popBackStack() },
            onSelectFontSize = viewModel::selectFontSizeLevel,
            onSelectThemeMode = viewModel::selectThemeMode,
            onPrivacyMaskedChanged = viewModel::setPrivacyMasked,
            onReduceMotionChanged = viewModel::setReduceMotion
        )
    }
}

/** Render puro (sin `hiltViewModel()`/`NavController`) — testeable con estado inyectado a mano. */
@Composable
fun ConfiguracionContent(
    state: ConfiguracionUiState,
    onBack: () -> Unit,
    onSelectFontSize: (FontSizeLevel) -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onPrivacyMaskedChanged: (Boolean) -> Unit,
    onReduceMotionChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MspTheme.colors.background)
            .statusBarsPadding()
    ) {
        ConfiguracionHeader(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = CONTENT_HORIZONTAL_PADDING)
        ) {
            Spacer(Modifier.height(MspTheme.spacing.md))
            FontSizeSection(
                selected = state.fontSizeLevel,
                onSelect = onSelectFontSize,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(SECTION_GAP))
            AppearanceSection(
                themeMode = state.themeMode,
                onThemeModeSelected = onSelectThemeMode,
                privacyMasked = state.privacyMasked,
                onPrivacyMaskedChanged = onPrivacyMaskedChanged,
                reduceMotion = state.reduceMotion,
                onReduceMotionChanged = onReduceMotionChanged,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(MspTheme.spacing.lg))
        }
    }
}

@Composable
private fun ConfiguracionHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MspTheme.colors.surface)
            .padding(horizontal = MspTheme.spacing.sm, vertical = MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag(CONFIGURACION_BACK_BUTTON_TAG)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Regresar",
                tint = MspTheme.colors.onSurface
            )
        }
        Text(
            text = "Configuración",
            style = MspTheme.type.screenTitle,
            color = MspTheme.colors.onSurface
        )
    }
}
