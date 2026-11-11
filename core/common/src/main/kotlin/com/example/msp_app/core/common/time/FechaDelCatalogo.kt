package com.example.msp_app.core.common.time

import java.time.Instant

/**
 * Cuándo se actualizó por última vez el catálogo de productos del teléfono.
 *
 * Se anota ([registrar]) SOLO cuando una descarga completa del catálogo ya quedó guardada en el
 * teléfono; una descarga fallida o una escritura fallida no mueven la marca. Sin marca
 * ([ultima] = `null`, instalación nueva sin sincronizar) quien la consulta no avisa nada: no se
 * inventa una antigüedad.
 *
 * El almacén (preferencias) entra por lambdas para probarlo sin Android; la hora sale de
 * [AppClock] y se guarda como instante (epoch ms): la zona de negocio solo importa al mostrarla.
 */
class FechaDelCatalogo(
    private val leer: () -> Long,
    private val guardar: (Long) -> Unit,
    private val clock: AppClock = AppClock.System
) {
    fun registrar() = guardar(clock.now().toEpochMilli())

    fun ultima(): Instant? = leer().takeIf { it > 0L }?.let(Instant::ofEpochMilli)
}
