package com.example.msp_app.core.ubicacion

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

/** Sin estado propio: pinta el aviso o nada. Nunca bloquea; tocarlo abre el ajuste que corresponde. */
@Composable
fun AvisoDeUbicacion(
    estado: EstadoDeUbicacion,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val texto = textoDelAviso(estado) ?: return
    Row(
        modifier = modifier.fillMaxWidth().clickable(
            onClick = alTocar
        ).padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Pon esto en la pantalla del cliente / captura de abono / diálogo de condonación. Inicia la toma
 * anticipada con el alcance de la pantalla (se cancela con ella), la reintenta al volver a primer plano
 * (por si el cobrador acaba de dar el permiso) y muestra el aviso si falta permiso o GPS.
 */
@Composable
fun UbicacionAnticipadaEfecto(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val estado = iniciarUbicacionAnticipada()
    AvisoDeUbicacion(
        estado = estado,
        alTocar = { abrirAjuste(context, estado) },
        modifier = modifier
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

/** Se monta sobre el NavHost: solo hace algo en las rutas de [rutaPideUbicacion]. */
@Composable
fun UbicacionPorRuta(navController: NavController) {
    val entrada by navController.currentBackStackEntryAsState()
    if (rutaPideUbicacion(entrada?.destination?.route)) {
        UbicacionAnticipadaEfecto(
            modifier = Modifier
                .statusBarsPadding()
                .background(MaterialTheme.colorScheme.surface)
        )
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
