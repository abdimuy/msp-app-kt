package com.example.msp_app.core.telemetry.compose

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private const val TAG = "TrackClick"

/**
 * [Modifier.clickable] instrumentado: emite `tap(LocalScreenName.current, element)`
 * ANTES de delegar a [onClick] (spec §7, taxonomía `tap`).
 *
 * ## `element` DEBE ser un identificador estático (LFPDPPP)
 *
 * `element` es un id de botón/acción definido en código (p.ej. `"guardar_pago"`,
 * `"cancelar_venta"`), NUNCA derivado de texto de usuario, `contentDescription`,
 * estado de negocio, o cualquier dato capturado en tiempo de ejecución — mismo
 * invariante que el resto del puerto [com.example.msp_app.core.telemetry.Telemetry]
 * (ver su KDoc). El revisor adversarial de este módulo verifica esto en cada
 * call site.
 *
 * ## Best-effort: nunca rompe el click de negocio
 *
 * Si [com.example.msp_app.core.telemetry.Telemetry.tap] lanza (p.ej. `screen`
 * inválido porque no hay [ScreenScope] activo, o el adapter real falla), el
 * fallo se loguea y se descarta — [onClick] SIEMPRE se ejecuta.
 */
@Composable
fun Modifier.trackClick(element: String, onClick: () -> Unit): Modifier {
    val telemetry = LocalTelemetry.current
    val screen = LocalScreenName.current

    return clickable {
        runCatching { telemetry.tap(screen, element) }
            .onFailure { Log.w(TAG, "no se pudo registrar el tap '$element', best-effort", it) }
        onClick()
    }
}
