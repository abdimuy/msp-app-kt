package com.example.msp_app.core.designsystem.component

import androidx.compose.ui.graphics.Color
import com.example.msp_app.core.designsystem.theme.MspColors

/**
 * Estado semántico de [MspSyncBand]/`MspPaymentSyncPill` (1:1 kollect §8.5,
 * `.sync`/`.syncpill`): la cobranza offline-first tiene pagos capturados en
 * el dispositivo esperando subir al servidor, o ya está al día.
 */
enum class SyncBandState {
    /** Hay pagos capturados localmente sin subir todavía — ámbar, no bloqueante. */
    Pending,

    /** Todo lo capturado ya se subió — verde. */
    Ok
}

/** Color de contenido (dot + texto), resuelto desde [MspColors] — mismo tono que [tintColor]. */
internal fun SyncBandState.contentColor(colors: MspColors): Color = when (this) {
    SyncBandState.Pending -> colors.statusPartial
    SyncBandState.Ok -> colors.statusPaid
}

/** Tint de fondo de la banda/pill, resuelto desde [MspColors]. */
internal fun SyncBandState.tintColor(colors: MspColors): Color = when (this) {
    SyncBandState.Pending -> colors.statusPartialTint
    SyncBandState.Ok -> colors.statusPaidTint
}
