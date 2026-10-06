package com.example.msp_app.core.navegacion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.application.ReglasDelMenu
import com.example.msp_app.core.navegacion.domain.model.ActivoEnElMenu
import com.example.msp_app.core.navegacion.domain.model.CabeceraDelMenu
import com.example.msp_app.core.navegacion.domain.model.ElementoDelMenu
import com.example.msp_app.core.navegacion.domain.model.SeccionDelMenu
import com.example.msp_app.core.navegacion.ui.components.BuscadorDelMenu
import com.example.msp_app.core.navegacion.ui.components.EncabezadoDelMenu
import com.example.msp_app.core.navegacion.ui.components.SeccionPlegable

const val MENU_LATERAL_TAG: String = "menu_lateral"
const val FILA_DEL_MENU_TAG: String = "menu_lateral_fila_"
const val SECCION_DEL_MENU_TAG: String = "menu_lateral_seccion_"
const val BUSCADOR_DEL_MENU_TAG: String = "menu_lateral_buscador"
const val DESVANECIDO_DEL_MENU_TAG: String = "menu_lateral_desvanecido"
const val ENVIO_DEL_MENU_TAG: String = "menu_lateral_envio"
const val ID_CONFIGURACION: String = "configuracion"

/** La columna de nombre + zona de la cabecera (revisión Task 3: no debe apretarse). */
const val NOMBRE_DEL_MENU_TAG: String = "menu_lateral_nombre"

/** 360 dp de teléfono menos 56 dp de velo para tocar fuera y cerrar. */
val ANCHO_DEL_MENU: Dp = 304.dp

/**
 * La hoja del menú, sin cajón ni datos: todo llega por parámetro. Se compone
 * DENTRO de `MspTheme` (lo pone `MenuLateralContenedor`).
 *
 * El cuerpo es un `Column` con scroll y no una lista perezosa a propósito: el
 * máximo son 10 opciones (spec §2.1.3) y no crece con los datos (CLAUDE.md §3).
 *
 * [temaOscuro], [montosOcultos], [onAlternarTema] y [onAlternarMontos] son del
 * encabezado (ruling R2, decisión del dueño): el botón sol/luna y el de
 * ocultar/mostrar dinero viven ahí, pero la hoja sigue siendo pura — no lee
 * ningún estado global, sólo refleja lo que le dan por parámetro.
 */
@Composable
fun MenuLateral(
    cabecera: CabeceraDelMenu,
    secciones: List<SeccionDelMenu>,
    activo: ActivoEnElMenu,
    seccionesPlegadas: Set<String>,
    apertura: AperturaDelMenu,
    onTogglePlegado: (seccionId: String, plegar: Boolean) -> Unit,
    onElegir: (ElementoDelMenu) -> Unit,
    temaOscuro: Boolean,
    montosOcultos: Boolean,
    onAlternarTema: () -> Unit,
    onAlternarMontos: () -> Unit,
    pie: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val desplazamiento = rememberScrollState()
    val visibles = ReglasDelMenu.filtrar(secciones, apertura.consulta)
    Column(modifier = modifier.fillMaxHeight().background(MspTheme.colors.surface)) {
        EncabezadoDelMenu(
            cabecera = cabecera,
            temaOscuro = temaOscuro,
            montosOcultos = montosOcultos,
            onAlternarTema = onAlternarTema,
            onAlternarMontos = onAlternarMontos
        )
        if (ReglasDelMenu.muestraBuscador(secciones)) {
            BuscadorDelMenu(consulta = apertura.consulta, onConsulta = { apertura.consulta = it })
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(desplazamiento)
                    .padding(horizontal = MspTheme.spacing.sm, vertical = MspTheme.spacing.sm)
            ) {
                if (visibles.isEmpty() && apertura.consulta.isNotBlank()) {
                    Text(
                        text = "Sin resultados",
                        style = MspTheme.type.body,
                        color = MspTheme.colors.onSurfaceMuted,
                        modifier = Modifier.padding(MspTheme.spacing.md)
                    )
                }
                visibles.forEach { seccion ->
                    val abierta = ReglasDelMenu.estaAbierta(
                        seccionId = seccion.id,
                        plegadas = seccionesPlegadas,
                        activa = activo.seccionId,
                        tocadas = apertura.tocadas,
                        consulta = apertura.consulta
                    )
                    SeccionPlegable(
                        seccion = seccion,
                        abierta = abierta,
                        habilitada = apertura.consulta.isBlank(),
                        itemActivoId = activo.itemId,
                        onToggle = {
                            apertura.tocar(seccion.id)
                            onTogglePlegado(seccion.id, abierta)
                        },
                        onElegir = onElegir
                    )
                }
            }
            if (desplazamiento.canScrollForward) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(MspTheme.spacing.lg + MspTheme.spacing.sm)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MspTheme.colors.surface.copy(alpha = 0f),
                                    MspTheme.colors.surface
                                )
                            )
                        )
                        .testTag(DESVANECIDO_DEL_MENU_TAG)
                )
            }
        }
        HorizontalDivider(color = MspTheme.colors.outline)
        pie()
    }
}
