package com.example.msp_app.feature.ubicacion.domain.port

/**
 * Salir a la app de mapas del teléfono.
 *
 * ## Por qué existe un puerto propio y no se reusa el de `:feature:pagos`
 *
 * Porque **un feature no puede depender de otro**. `AccionesExternasPort` y su
 * `IntentAccionesExternasAdapter` viven en `:feature:pagos`, y alcanzarlos desde
 * aquí ataría dos módulos que no se conocen.
 *
 * Lo que **sí** se respeta es la decisión de fondo: *el `geo:` lo arma un solo
 * lugar*. Este puerto no construye ningún intent — lo declara, y `:app`, que
 * tiene los dos módulos en su classpath, lo ata al **mismo** adaptador de
 * siempre. O sea: dos puertos, un constructor de `geo:`.
 *
 * Y por lo mismo que el otro, devuelve `Result` en vez de lanzar: un teléfono
 * **sin app de mapas** es un caso real de la flota, y un
 * `ActivityNotFoundException` dentro de un `onClick` o tumba la pantalla o se
 * traga en un `catch` mudo.
 */
interface AbrirEnMapasPort {
    suspend fun comoLlegar(lat: Double, lon: Double, etiqueta: String): Result<Unit>
}
