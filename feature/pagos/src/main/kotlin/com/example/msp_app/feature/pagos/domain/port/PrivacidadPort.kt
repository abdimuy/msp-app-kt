package com.example.msp_app.feature.pagos.domain.port

import kotlinx.coroutines.flow.Flow

/**
 * "Esconder cantidades" — la preferencia de privacidad de la app, vista desde
 * `:feature:pagos`.
 *
 * ## Por qué un puerto y no `SettingsRepository` directo
 *
 * La preferencia vive en `:core:settings` y este módulo no lo ve (ni debe: el
 * `ui/` tiene prohibido importar la capa de datos). Es el mismo caso —y la misma
 * forma— que [TemaDeLaAppPort]: el puerto se queda aquí y el adaptador vive en
 * `:app`, que es el único que ve las dos caras.
 *
 * ## Por qué [ocultosAhora] existe
 *
 * Igual que en el tema: sin una lectura síncrona la pantalla pintaría un frame
 * con los montos **visibles** antes de la primera emisión del `Flow`. En un
 * botón de tema eso es un parpadeo; en uno de privacidad es enseñar el dinero de
 * alguien en la calle, que es justo lo que el botón existe para evitar.
 */
interface PrivacidadPort {

    /** `true` mientras los montos deban ir enmascarados. */
    val ocultos: Flow<Boolean>

    /** Lectura síncrona del último valor conocido — siembra el estado inicial. */
    fun ocultosAhora(): Boolean

    /** Invierte la preferencia y la persiste. */
    suspend fun alternar()
}
