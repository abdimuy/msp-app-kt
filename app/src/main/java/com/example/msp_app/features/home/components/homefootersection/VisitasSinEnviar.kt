package com.example.msp_app.features.home.components.homefootersection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.diagnosticoenvio.MotivoDeFila
import com.example.msp_app.core.visitas.envio.ROTULO_EN_REVISION
import com.example.msp_app.core.visitas.envio.ROTULO_PENDIENTE

/**
 * Una visita en la pantalla de pendientes: su fecha ya formateada y su rótulo
 * (`rotuloDeEnvio`). Sin nombre de cliente ni montos: no hacen falta para
 * saber qué falta por enviar.
 */
data class EnvioDeVisita(val id: String, val fecha: String, val rotulo: String)

/**
 * Lo que la pantalla sabe de los envíos. [envios] nulo = todavía no se leyó
 * nunca (se pinta "cargando", no "no hay"). [fallo] = la última lectura
 * falló: se conserva la lista anterior y se avisa, en vez de decir que no hay
 * pendientes cuando sí las hay.
 */
data class EstadoDeEnvios(val envios: List<EnvioDeVisita>? = null, val fallo: Boolean = false) {

    /** Aplica el resultado de una lectura. Una falla nunca borra lo que ya se sabía. */
    fun tras(lectura: Result<List<EnvioDeVisita>>): EstadoDeEnvios = lectura.fold(
        onSuccess = { EstadoDeEnvios(envios = it, fallo = false) },
        onFailure = { copy(fallo = true) }
    )
}

/**
 * Pantalla de pendientes de visitas (plan visitas P1, Tarea 14; spec §4.4).
 *
 * Muestra cada pendiente y cada resguardada ("en revisión de oficina"), y para
 * la que tiene un fallo vigente, su motivo e intentos: la que se reintenta
 * para siempre se ve. Solo pinta.
 *
 * [aviso] es una ranura arriba de todo, vacía por omisión: el aviso de "no
 * retires este teléfono con pendientes" (decisión H15, abierta) cabe ahí sin
 * tocar esta pantalla.
 */
@Composable
fun VisitasSinEnviar(
    envios: List<EnvioDeVisita>?,
    motivos: Map<String, MotivoDeFila>,
    onEnviar: () -> Unit,
    modifier: Modifier = Modifier,
    fallo: Boolean = false,
    aviso: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier) {
        aviso?.invoke()
        if (fallo) {
            Text(
                "no se pudieron leer las visitas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (envios == null) {
            if (!fallo) Text("cargando visitas")
            return@Column
        }
        if (envios.isEmpty()) {
            Text("No hay visitas pendientes")
            return@Column
        }
        Text(resumenDeEnvio(envios), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        envios.take(MAX_FILAS).forEach { envio ->
            Text("${envio.fecha} · ${envio.rotulo}", style = MaterialTheme.typography.bodySmall)
            motivos[envio.id]?.let { motivo ->
                Text(
                    "${motivo.texto} · ${motivo.intentos} ${if (motivo.intentos == 1) "intento" else "intentos"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (envios.size > MAX_FILAS) {
            Text("y ${envios.size - MAX_FILAS} más", style = MaterialTheme.typography.bodySmall)
        }
        if (envios.any { it.rotulo == ROTULO_PENDIENTE }) {
            TextButton(onClick = onEnviar) { Text("enviar ahora") }
        }
    }
}

/** `2 pendientes de envío · 1 en revisión de oficina`; vacío sin filas. */
fun resumenDeEnvio(envios: List<EnvioDeVisita>): String {
    val pendientes = envios.count { it.rotulo == ROTULO_PENDIENTE }
    val enRevision = envios.count { it.rotulo == ROTULO_EN_REVISION }
    return listOfNotNull(
        pendientes.takeIf { it > 0 }?.let { "$it ${if (it == 1) "pendiente" else "pendientes"} de envío" },
        enRevision.takeIf { it > 0 }?.let { "$it $ROTULO_EN_REVISION" }
    ).joinToString(" · ")
}

private const val MAX_FILAS = 20
