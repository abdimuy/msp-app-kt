package com.example.msp_app.core.appgate.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.msp_app.core.appgate.download.megabytesLabel

/**
 * El texto de cada estado, aparte de la pantalla.
 *
 * Vive en su propio archivo para que se pueda leer de corrido lo que la
 * pantalla le dice a alguien que no puede trabajar — que es lo único que esa
 * persona se lleva. Ninguna cadena habla de configuración, campos ni Firestore:
 * la mitad de estos estados son culpa de la oficina y aun así se explican en
 * términos de qué va a pasar y a quién avisar.
 */
internal fun UpdateStage.title(): String = when (this) {
    UpdateStage.ReadyToInstall -> "Actualiza para continuar"
    is UpdateStage.Downloading -> "Descargando la actualización"
    is UpdateStage.MeteredOnly -> "Actualiza para continuar"
    UpdateStage.Offline -> "Sin conexión"
    is UpdateStage.Failed -> "No se completó la descarga"
    UpdateStage.Unavailable -> "Actualización no disponible"
    is UpdateStage.Stalled -> "La descarga no avanza"
    is UpdateStage.Unusable -> "El archivo no actualiza"
}

internal fun UpdateStage.explanation(): String = when (this) {
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

    UpdateStage.Unavailable ->
        "La oficina todavía no publica el archivo. Avísales para que lo suban."

    is UpdateStage.Stalled ->
        "Lleva rato sin bajar nada. Revisa el wifi y vuelve a intentar."

    is UpdateStage.Unusable ->
        "El archivo publicado es la $offeredVersionName y no alcanza. Avisa a la oficina."
}

internal fun UpdateStage.icon(): ImageVector = when (this) {
    UpdateStage.ReadyToInstall -> Icons.Filled.Check
    is UpdateStage.Downloading, is UpdateStage.MeteredOnly -> Icons.Filled.KeyboardArrowDown
    UpdateStage.Offline -> Icons.Filled.Warning
    is UpdateStage.Failed, is UpdateStage.Stalled -> Icons.Filled.Refresh
    UpdateStage.Unavailable, is UpdateStage.Unusable -> Icons.Filled.Info
}
