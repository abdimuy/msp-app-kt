package com.example.msp_app.feature.usodedatos.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.msp_app.core.designsystem.component.MspThemeRevealHost
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion
import com.example.msp_app.feature.usodedatos.FormatoDeMegas
import com.example.msp_app.feature.usodedatos.PeriodoDeUso
import com.example.msp_app.feature.usodedatos.UsoDeDatosUiState

const val USO_DE_DATOS_BACK_TAG = "msp_uso_de_datos_back"
const val USO_DE_DATOS_VACIO_TAG = "msp_uso_de_datos_vacio"
const val USO_DE_DATOS_SIN_DETALLE_TAG = "msp_uso_de_datos_sin_detalle"

/** Ruta del destino: la declara este módulo y `:app` la registra. */
object UsoDeDatosRutas {
    const val USO_DE_DATOS = "uso_de_datos"
}

/**
 * "Uso de datos": cuánto consume la app, hoy y en los últimos 7 días, con datos
 * móviles y con Wi-Fi, abierta o en segundo plano, y por servicio. Solo lee; no
 * tiene botones de acción. Como `ConfiguracionScreen`, envuelve su propio [MspTheme].
 */
@Composable
fun UsoDeDatosScreen(
    navController: NavController,
    viewModel: UsoDeDatosViewModel = hiltViewModel()
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    // La pantalla se envuelve a sí misma (`:app` no provee `MspTheme`): lo cobran
    // `CadaPantallaMspProveeSuTemaTest` y `CadaPantallaConTemaAnimaElCambioTest`. El tema lo
    // manda la app (`appDarkTheme()`); esta pantalla no tiene sol/luna, de ahí el `{}`.
    MspThemeRevealHost(
        onToggleTheme = {},
        reducedMotion = rememberMspReducedMotion(),
        tema = { animateColors, contenido ->
            MspTheme(
                animateColors = animateColors,
                content = contenido
            )
        }
    ) {
        UsoDeDatosContenido(estado = estado, onBack = { navController.popBackStack() })
    }
}

@Composable
fun UsoDeDatosContenido(estado: UsoDeDatosUiState, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MspTheme.colors.background)
            .statusBarsPadding()
    ) {
        Encabezado(onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.lg)
        ) {
            Spacer(Modifier.height(MspTheme.spacing.xs))
            when {
                estado.cargando -> Unit
                estado.vacio -> Text(
                    text = "Todavía no hay datos",
                    style = MspTheme.type.body,
                    color = MspTheme.colors.onSurfaceMuted,
                    modifier = Modifier.testTag(USO_DE_DATOS_VACIO_TAG)
                )
                else -> {
                    estado.hoy?.let { Periodo("Hoy", it) }
                    estado.semana?.let { Periodo("Últimos 7 días", it) }
                }
            }
            Spacer(Modifier.height(MspTheme.spacing.lg))
        }
    }
}

@Composable
private fun Encabezado(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MspTheme.colors.surface)
            .padding(horizontal = MspTheme.spacing.sm, vertical = MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag(USO_DE_DATOS_BACK_TAG)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Regresar",
                tint = MspTheme.colors.onSurface
            )
        }
        Text(
            text = "Uso de datos",
            style = MspTheme.type.screenTitle,
            color = MspTheme.colors.onSurface
        )
    }
}

@Composable
private fun Periodo(titulo: String, p: PeriodoDeUso) {
    Column {
        Text(
            text = titulo,
            style = MspTheme.type.sectionHeader,
            color = MspTheme.colors.onSurfaceMuted
        )
        Spacer(Modifier.height(MspTheme.spacing.sm))
        Surface(
            shape = MspTheme.shapes.tile,
            color = MspTheme.colors.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(MspTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
            ) {
                Fila("Total", p.total, destacada = true)
                Fila("Con datos móviles", p.movil)
                Fila("Con Wi-Fi", p.wifi)
                Fila("Con la app abierta", p.abierta)
                Fila("En segundo plano", p.segundoPlano)
            }
        }
        Spacer(Modifier.height(MspTheme.spacing.md))
        if (p.sinDetalle) {
            Text(
                text = "Sin detalle por servicio",
                style = MspTheme.type.body,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.testTag(USO_DE_DATOS_SIN_DETALLE_TAG)
            )
        } else if (p.servicios.isNotEmpty()) {
            Text(
                text = "Por servicio",
                style = MspTheme.type.sectionHeader,
                color = MspTheme.colors.onSurfaceMuted
            )
            Spacer(Modifier.height(MspTheme.spacing.sm))
            Surface(
                shape = MspTheme.shapes.tile,
                color = MspTheme.colors.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(MspTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
                ) {
                    p.servicios.forEach { Fila(it.nombre, it.bytes) }
                }
            }
        }
    }
}

// Apilada nunca: el nombre ocupa el ancho que sobra y el número va a la derecha sin partirse.
@Composable
private fun Fila(nombre: String, bytes: Long, destacada: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Text(
            text = nombre,
            style = if (destacada) MspTheme.type.cardTitle else MspTheme.type.body,
            color = MspTheme.colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = FormatoDeMegas.mb(bytes),
            style = if (destacada) MspTheme.type.cardTitle else MspTheme.type.body,
            color = MspTheme.colors.onSurface
        )
    }
}
