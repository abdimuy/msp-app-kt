package com.example.msp_app.core.telemetry.compose

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.msp_app.core.telemetry.Telemetry

private const val TAG = "ScreenScope"

/**
 * El [Telemetry] del árbol Compose actual — la raíz de composición (`:app`,
 * cablear en Plan 5/T8) lo provee UNA vez con el adapter real
 * (`DurableTelemetry`, ver `di/TelemetryModule.kt`). El default ([NoOpTelemetry])
 * existe solo como red de seguridad para composiciones aisladas (previews,
 * subárboles de test que no envuelven este `CompositionLocal` a propósito) —
 * NUNCA debe alcanzar producción sin proveedor real, pero si eso pasara, el
 * mandato "nunca crashear la UI por telemetría" exige que sea un no-op, no
 * una excepción.
 */
val LocalTelemetry: ProvidableCompositionLocal<Telemetry> =
    staticCompositionLocalOf { NoOpTelemetry }

/**
 * Nombre estático de la pantalla actual (id de desarrollador, ver disciplina
 * anti-PII en [Telemetry]) — provisto únicamente por [ScreenScope]. El
 * default es cadena vacía, que [com.example.msp_app.core.telemetry.TelemetryEvent]
 * rechaza como identificador estático: un `Modifier.trackClick` usado FUERA
 * de un `ScreenScope` (bug de integración) descarta su evento en vez de
 * crashear — ver [com.example.msp_app.core.telemetry.compose.trackClick].
 */
val LocalScreenName: ProvidableCompositionLocal<String> = staticCompositionLocalOf { "" }

/**
 * Marca [name] (id estático de pantalla) para todo el subárbol de [content]:
 * provee [LocalScreenName] y emite `screenView(name)` exactamente una vez por
 * entrada a la composición (`LaunchedEffect` keyed en [name] — una
 * recomposición que no cambia [name] NO vuelve a emitir; una pantalla que
 * sale y vuelve a entrar a composición sí, porque es una visita nueva).
 *
 * El disparo automático desde el `NavHost` (spec §7, `currentBackStackEntryFlow`)
 * se cablea en `:app` (Plan 5/T8) llamando a este mismo composable por
 * destino — acá solo vive el mecanismo reutilizable.
 *
 * Best-effort: un [Telemetry] que lanza (sink caído, `screen` inválido) NUNCA
 * impide que [content] se componga — se loguea y se descarta.
 */
@Composable
fun ScreenScope(name: String, content: @Composable () -> Unit) {
    val telemetry = LocalTelemetry.current

    LaunchedEffect(name) {
        runCatching { telemetry.screenView(name) }
            .onFailure { Log.w(TAG, "no se pudo registrar screenView('$name'), best-effort", it) }
    }

    CompositionLocalProvider(LocalScreenName provides name) {
        content()
    }
}

private object NoOpTelemetry : Telemetry {
    override fun screenView(screen: String) = Unit
    override fun tap(screen: String, element: String) = Unit
    override fun event(name: String, props: Map<String, String>) = Unit
    override fun error(code: String, message: String, props: Map<String, String>) = Unit
}
