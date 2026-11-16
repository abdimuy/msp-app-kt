@file:Suppress("TooManyFunctions") // una pieza por bloque de la recolección.

package com.example.msp_app.feature.garantias.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.component.PrimaryFieldButtonVariant
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.garantias.domain.model.AccionDeGarantia
import com.example.msp_app.feature.garantias.domain.model.FotoDeCaptura
import com.example.msp_app.feature.garantias.domain.model.Ubicacion
import com.example.msp_app.feature.garantias.ui.components.AvisoDeGarantia
import com.example.msp_app.feature.garantias.ui.components.EncabezadoDeGarantia
import com.example.msp_app.feature.garantias.ui.components.EtiquetaDeSeccion
import com.example.msp_app.feature.garantias.ui.components.FilaDeDato

/**
 * El contenido de la recolección: puro, sin ViewModel ni Hilt.
 *
 * El cobrador ve la etapa actual, la acción que el catálogo declara válida, las
 * fotos que ya tomó y el GPS —opcional—. El GPS no bloquea: sin coordenadas la
 * recolección se guarda igual y acá se dice "Sin ubicación" (D5).
 *
 * La cámara no vive acá: [onTomarFoto] sólo pide un destino nuevo y el wrapper
 * de la ruta lanza el contrato `TakePicture()`. Así este composable se pinta en
 * una captura sin cámara ni permisos.
 */
@Composable
fun RecoleccionContenido(
    state: RecoleccionUiState,
    onAccion: (String) -> Unit,
    onTomarFoto: () -> Unit,
    onQuitarFoto: (String) -> Unit,
    onPedirUbicacion: () -> Unit,
    onGuardar: () -> Unit,
    onAtras: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        EncabezadoDeGarantia(titulo = TextosDeGarantias.TITULO_RECOLECCION, onAtras = onAtras)
        if (state.cargando) {
            Text(
                text = TextosDeGarantias.CARGANDO,
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
            ) {
                FilaDeDato(etiqueta = TextosDeGarantias.ETAPA, valor = state.etapa)
                SeccionDeAcciones(state = state, onAccion = onAccion)
                SeccionDeFotos(
                    fotos = state.fotos,
                    onTomarFoto = onTomarFoto,
                    onQuitarFoto = onQuitarFoto
                )
                SeccionDeUbicacion(
                    ubicacion = state.ubicacion,
                    pidiendoUbicacion = state.pidiendoUbicacion,
                    onPedirUbicacion = onPedirUbicacion
                )
                state.fallo?.let { fallo ->
                    AvisoDeGarantia(texto = TextosDeGarantias.textoDe(fallo), esError = true)
                }
                if (state.falloTecnico) {
                    AvisoDeGarantia(
                        texto = TextosDeGarantias.NO_SE_PUDO_GUARDAR,
                        esError = true
                    )
                }
            }
        }
        MspPrimaryFieldButton(
            text = if (state.guardando) {
                TextosDeGarantias.GUARDANDO_RECOLECCION
            } else {
                TextosDeGarantias.GUARDAR_RECOLECCION
            },
            onClick = onGuardar,
            enabled = state.sePuedeGuardar,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * La acción que el cobrador manda.
 *
 * Con catálogo, sus acciones son las únicas válidas (D2) y se pintan todas,
 * marcando la elegida. Sin catálogo —hoy `GET /v2/garantias/catalogos` no
 * existe— se ofrece el respaldo de la captura con la nota de que el servidor
 * todavía no publica la tabla. Sin ninguna de las dos, se dice que no hay
 * acciones en vez de dejar la pantalla muda.
 */
@Composable
private fun SeccionDeAcciones(state: RecoleccionUiState, onAccion: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
        EtiquetaDeSeccion(texto = TextosDeGarantias.ACCION)
        if (state.acciones.isEmpty()) {
            AvisoDeGarantia(
                texto = if (state.catalogoVacio) {
                    TextosDeGarantias.SIN_CATALOGO
                } else {
                    TextosDeGarantias.SIN_ACCIONES
                }
            )
        } else {
            if (state.catalogoVacio) {
                AvisoDeGarantia(texto = TextosDeGarantias.SIN_CATALOGO)
            }
            state.acciones.forEach { accion ->
                BotonDeAccion(
                    accion = accion,
                    elegida = accion.etapaDestino == state.accionElegida,
                    onAccion = onAccion
                )
            }
        }
    }
}

/** Una acción del catálogo; la elegida es `Primary` y el resto `Ghost`. */
@Composable
private fun BotonDeAccion(accion: AccionDeGarantia, elegida: Boolean, onAccion: (String) -> Unit) {
    MspPrimaryFieldButton(
        text = accion.etiqueta,
        onClick = { onAccion(accion.etapaDestino) },
        modifier = Modifier.fillMaxWidth(),
        variant = if (elegida) {
            PrimaryFieldButtonVariant.Primary
        } else {
            PrimaryFieldButtonVariant.Ghost
        }
    )
}

/** Las fotos de la evidencia: tomar una nueva y quitar las que no sirven. */
@Composable
private fun SeccionDeFotos(
    fotos: List<FotoDeCaptura>,
    onTomarFoto: () -> Unit,
    onQuitarFoto: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
        EtiquetaDeSeccion(texto = TextosDeGarantias.FOTOS)
        MspPrimaryFieldButton(
            text = TextosDeGarantias.TOMAR_FOTO,
            onClick = onTomarFoto,
            modifier = Modifier.fillMaxWidth(),
            variant = PrimaryFieldButtonVariant.Ghost
        )
        if (fotos.isEmpty()) {
            Text(
                text = TextosDeGarantias.SIN_FOTOS,
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
        } else {
            fotos.forEach { foto ->
                FilaDeFoto(foto = foto, onQuitar = { onQuitarFoto(foto.clave) })
            }
        }
    }
}

/**
 * Una foto ya archivada.
 *
 * Se pinta el **nombre del archivo** y no una miniatura: el módulo no tiene un
 * cargador de imágenes autenticado y una foto local sin él se vería en blanco.
 * La limitación se declara en el reporte.
 */
@Composable
private fun FilaDeFoto(foto: FotoDeCaptura, onQuitar: () -> Unit) {
    MspCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            Text(
                text = foto.rutaArchivo.substringAfterLast('/'),
                style = MspTheme.type.listTitle,
                color = MspTheme.colors.onSurface
            )
            Text(
                text = foto.mime,
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
            MspPrimaryFieldButton(
                text = TextosDeGarantias.QUITAR,
                onClick = onQuitar,
                modifier = Modifier.fillMaxWidth(),
                variant = PrimaryFieldButtonVariant.Ghost
            )
        }
    }
}

/**
 * El GPS del cobrador, si lo hay.
 *
 * No bloquea —a diferencia de la entrega—: sin coordenadas la recolección se
 * guarda igual. El botón permite reintentar cuando el proveedor no contestó.
 */
@Composable
private fun SeccionDeUbicacion(
    ubicacion: Ubicacion?,
    pidiendoUbicacion: Boolean,
    onPedirUbicacion: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
        EtiquetaDeSeccion(texto = TextosDeGarantias.UBICACION_DE_LA_RECOLECCION)
        Text(
            text = when {
                pidiendoUbicacion -> TextosDeGarantias.ESPERANDO_UBICACION
                ubicacion != null ->
                    "${TextosDeGarantias.UBICACION_REGISTRADA}: " +
                        "${ubicacion.latitud}, ${ubicacion.longitud}"
                else -> TextosDeGarantias.SIN_UBICACION
            },
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted
        )
        MspPrimaryFieldButton(
            text = TextosDeGarantias.PEDIR_UBICACION,
            onClick = onPedirUbicacion,
            enabled = !pidiendoUbicacion,
            modifier = Modifier.fillMaxWidth(),
            variant = PrimaryFieldButtonVariant.Ghost
        )
    }
}
