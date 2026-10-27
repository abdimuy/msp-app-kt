package com.example.msp_app.features.home.components.avisos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.avisos.GuiaDePermisos
import com.example.msp_app.core.avisos.PasoDePermiso

/**
 * Explica por qué y ofrece abrir cada ajuste (plan visitas P3, Tarea 15;
 * E-APP-134). No bloquea: "Ahora no" lo cierra y el cobro no depende de él.
 */
@Composable
fun AvisosPermisosDialog(
    pasos: List<PasoDePermiso>,
    onAbrir: (PasoDePermiso) -> Unit,
    onAhoraNo: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onAhoraNo,
        title = { Text(GuiaDePermisos.TITULO) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(GuiaDePermisos.EXPLICACION)
                pasos.forEach { paso ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(paso.rotulo)
                        TextButton(onClick = { onAbrir(paso) }) { Text(paso.accion) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAhoraNo) { Text(GuiaDePermisos.AHORA_NO) }
        }
    )
}
