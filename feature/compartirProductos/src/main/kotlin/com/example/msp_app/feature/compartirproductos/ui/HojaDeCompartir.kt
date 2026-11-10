package com.example.msp_app.feature.compartirproductos.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.msp_app.feature.compartirproductos.domain.AvisoDeCatalogo
import com.example.msp_app.feature.compartirproductos.domain.FilaDePrecio
import kotlinx.coroutines.flow.Flow

/**
 * La hoja inferior «Compartir». Se conecta a [CompartirViewModel]: pinta su estado, y cuando las
 * tarjetas están listas abre WhatsApp (o el selector). Si el intent directo falla porque la app no
 * se puede abrir, cae al selector normal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaDeCompartir(viewModel: CompartirViewModel, alLanzar: () -> Unit = {}) {
    val contexto = LocalContext.current
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val ciclo = LocalLifecycleOwner.current.lifecycle
    val alLanzarActual by rememberUpdatedState(alLanzar)
    val generandoActual by rememberUpdatedState(estado.generando)

    // E-APP-186: solo se abre WhatsApp con la app a la vista. Si se fue a segundo plano mientras se
    // generaba, el evento espera en el canal y se lanza al volver (no se pierde ni se lanza «en el aire»).
    LaunchedEffect(viewModel, ciclo) {
        recolectarMientrasEsteVisible(ciclo, viewModel.eventos) { evento ->
            when (evento) {
                is EventoDeCompartir.Lanzar -> {
                    lanzar(contexto, evento.intent)
                    alLanzarActual()
                }
            }
        }
    }

    if (!estado.visible) return
    ModalBottomSheet(
        onDismissRequest = { if (!estado.generando) viewModel.cerrar() },
        // E-APP-186: mientras genera, arrastrar la hoja hacia abajo no la cierra.
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { puedeCambiarLaHoja(generandoActual) }
        )
    ) {
        ContenidoDeLaHoja(
            estado = estado,
            alCambiarTitulo = viewModel::cambiarTitulo,
            alCambiarCasilla = viewModel::cambiarCasilla,
            alCompartir = viewModel::compartir
        )
    }
}

/** Mientras se generan las tarjetas la hoja no se puede cerrar arrastrando. */
internal fun puedeCambiarLaHoja(generando: Boolean): Boolean = !generando

/** Recolecta [eventos] solo mientras [ciclo] esté al menos STARTED; fuera de eso los eventos esperan. */
internal suspend fun <T> recolectarMientrasEsteVisible(
    ciclo: Lifecycle,
    eventos: Flow<T>,
    alEvento: (T) -> Unit
) {
    ciclo.repeatOnLifecycle(Lifecycle.State.STARTED) { eventos.collect { alEvento(it) } }
}

private fun lanzar(contexto: Context, intent: Intent) {
    try {
        contexto.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("HojaDeCompartir", "no se pudo abrir ${intent.`package`}; se usa el selector", e)
        // WhatsApp instalado pero deshabilitado/no abrible: el selector normal de Android.
        val directo = Intent(intent).apply { `package` = null }
        contexto.startActivity(
            Intent.createChooser(
                directo,
                "Compartir"
            ).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }
}

private const val MINIMO_VISIBLE = 0.08f

/** El contenido de la hoja, sin estado propio: lo que se ve y se prueba. */
@Composable
fun ContenidoDeLaHoja(
    estado: EstadoDeCompartir,
    alCambiarTitulo: (String) -> Unit,
    alCambiarCasilla: (FilaDePrecio, Boolean) -> Unit,
    alCompartir: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .verticalScroll(rememberScrollState())
            .testTag(EtiquetasDeHoja.HOJA),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (estado.esUno) "Compartir producto" else "Compartir ${estado.articulos.size} productos",
            style = MaterialTheme.typography.titleLarge
        )
        VistaPrevia(estado)
        if (estado.esUno) {
            OutlinedTextField(
                value = estado.titulo,
                onValueChange = alCambiarTitulo,
                label = { Text("Título") },
                singleLine = true,
                enabled = !estado.generando,
                modifier = Modifier.fillMaxWidth().testTag(EtiquetasDeHoja.TITULO)
            )
        }
        FilaDeCasillas(estado, alCambiarCasilla)
        if (estado.avisoPreciosViejos) {
            Text(
                text = AvisoDeCatalogo.MENSAJE,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(EtiquetasDeHoja.AVISO)
            )
        }
        estado.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Button(
            onClick = alCompartir,
            enabled = !estado.generando,
            modifier = Modifier.fillMaxWidth().testTag(EtiquetasDeHoja.BOTON)
        ) {
            if (estado.generando) {
                // Determinado (hechas / total), no el giratorio infinito: avisa avance real y no deja
                // animaciones sin fin en las pruebas de UI ni en los goldens.
                val avance = (estado.hechas.toFloat() / estado.articulos.size.coerceAtLeast(1)).coerceIn(
                    MINIMO_VISIBLE,
                    1f
                )
                CircularProgressIndicator(
                    progress = { avance },
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    if (estado.articulos.size > 1) "Preparando ${estado.hechas} de ${estado.articulos.size}" else "Preparando…"
                )
            } else {
                Text("Compartir")
            }
        }
    }
}

@Composable
private fun VistaPrevia(estado: EstadoDeCompartir) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .testTag(EtiquetasDeHoja.VISTA_PREVIA),
        contentAlignment = Alignment.Center
    ) {
        val imagen = estado.vistaPrevia
        if (imagen == null) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
        } else {
            Image(
                bitmap = imagen.asImageBitmap(),
                contentDescription = "Vista previa de la tarjeta",
                modifier = Modifier
                    .height(280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .semantics { contentDescription = "Vista previa de la tarjeta" }
            )
        }
    }
}

@Composable
private fun FilaDeCasillas(
    estado: EstadoDeCompartir,
    alCambiarCasilla: (FilaDePrecio, Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilaDePrecio.entries.forEach { fila ->
            val marcada = estado.seleccion.incluye(fila)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = marcada,
                    onCheckedChange = { alCambiarCasilla(fila, it) },
                    enabled = !estado.generando,
                    modifier = Modifier.testTag(EtiquetasDeHoja.casilla(fila))
                )
                Text(
                    fila.etiqueta,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}
