package com.example.msp_app.core.ubicacion

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.msp_app.core.common.location.captura.EstadoDeUbicacion
import com.example.msp_app.feature.pagos.ui.PagosRutas

/** Texto del aviso, o `null` si no hace falta (E-APP-144). Mayúscula inicial, sin punto final. */
fun textoDelAviso(estado: EstadoDeUbicacion): String? = when (estado) {
    EstadoDeUbicacion.SinPermiso -> TEXTO_SIN_PERMISO
    EstadoDeUbicacion.GpsApagado -> TEXTO_GPS_APAGADO
    else -> null
}

const val TEXTO_SIN_PERMISO = "Activa la ubicación para registrar dónde cobras"
const val TEXTO_GPS_APAGADO = "Enciende el GPS para registrar dónde cobras"

const val TEXTO_ACTIVAR = "Activar"
const val TEXTO_AHORA_NO = "Ahora no"
const val TAG_AVISO_UBICACION = "aviso_ubicacion"

/** Si el cobrador ya dijo «Ahora no» en esta sesión de la app (no se persiste: reaparece al reabrirla). */
class DescarteDelAviso {
    var descartado by mutableStateOf(false)
        private set

    fun descartar() {
        descartado = true
    }

    companion object {
        /** La instancia de la sesión del proceso. */
        val Sesion = DescarteDelAviso()
    }
}

/** Sin estado propio: pinta el aviso o nada. Nunca bloquea; ocupa solo su tarjeta y no intercepta otros toques. */
@Composable
fun AvisoDeUbicacion(
    estado: EstadoDeUbicacion,
    alActivar: () -> Unit,
    alDescartar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val texto = textoDelAviso(estado) ?: return
    Surface(
        modifier = modifier.testTag(TAG_AVISO_UBICACION),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = texto,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp)
            )
            TextButton(onClick = alDescartar) {
                Text(TEXTO_AHORA_NO, color = MaterialTheme.colorScheme.inverseOnSurface)
            }
            TextButton(onClick = alActivar) {
                Text(TEXTO_ACTIVAR, color = MaterialTheme.colorScheme.inversePrimary)
            }
        }
    }
}

/**
 * El aviso como tarjeta flotante abajo (E-APP-149, H2): no toca la barra superior ni intercepta toques
 * fuera de sí misma. Se oculta al tocar «Ahora no» durante la sesión.
 */
@Composable
fun BoxScope.AvisoFlotanteDeUbicacion(
    estado: EstadoDeUbicacion,
    descarte: DescarteDelAviso,
    alActivar: () -> Unit
) {
    if (descarte.descartado) return
    AvisoDeUbicacion(
        estado = estado,
        alActivar = alActivar,
        alDescartar = descarte::descartar,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    )
}

/**
 * Pon esto dentro del `Box` raíz. Inicia la toma anticipada con el alcance de la pantalla (se cancela con
 * ella), la reintenta al volver a primer plano (por si el cobrador acaba de dar el permiso) y muestra el
 * aviso si falta permiso o GPS.
 */
@Composable
fun BoxScope.UbicacionAnticipadaEfecto(descarte: DescarteDelAviso = DescarteDelAviso.Sesion) {
    val context = LocalContext.current
    val estado = iniciarUbicacionAnticipada()
    AvisoFlotanteDeUbicacion(
        estado = estado,
        descarte = descarte,
        alActivar = { abrirAjuste(context, estado) }
    )
}

/** Inicia la toma anticipada con el alcance de la composición y devuelve su estado, sin pintar nada. */
@Composable
fun iniciarUbicacionAnticipada(): EstadoDeUbicacion {
    val context = LocalContext.current
    val tomador = remember { UbicacionAnticipada.tomador(context) }
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tomador.iniciar(scope) }
    val estado by tomador.estado.collectAsState()
    return estado
}

/** Rutas donde se abre un cliente o se captura un abono: ahí arranca la toma anticipada. */
fun rutaPideUbicacion(route: String?): Boolean = route in RUTAS_QUE_PIDEN_UBICACION

private val RUTAS_QUE_PIDEN_UBICACION = setOf(
    PagosRutas.DETALLE_CLIENTE,
    PagosRutas.DETALLE_VENTA,
    PagosRutas.REGISTRAR_ABONO
)

/** Se monta dentro del `Box` del NavHost: solo hace algo en las rutas de [rutaPideUbicacion]. */
@Composable
fun BoxScope.UbicacionPorRuta(navController: NavController) {
    val entrada by navController.currentBackStackEntryAsState()
    if (rutaPideUbicacion(entrada?.destination?.route)) {
        UbicacionAnticipadaEfecto()
    }
}

private fun abrirAjuste(context: Context, estado: EstadoDeUbicacion) {
    val intent = when (estado) {
        EstadoDeUbicacion.GpsApagado -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        else -> Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
