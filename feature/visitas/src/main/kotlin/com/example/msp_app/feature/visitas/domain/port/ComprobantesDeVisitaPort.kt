package com.example.msp_app.feature.visitas.domain.port

import com.example.msp_app.feature.visitas.domain.model.ComprobanteDeVisita
import com.example.msp_app.feature.visitas.domain.model.DestinoDeFoto

/**
 * La cámara y el archivo del comprobante de la visita.
 *
 * **El puerto se queda en el módulo, el adaptador se va a `:app`** (mismo
 * reparto que [RegistroDeVisitaPort] → `RegistroDeVisitaAdapter` y que
 * [UbicacionPort] → `UbicacionDeVisitaAdapter`): lo que hace falta del otro
 * lado —`FileProvider`, `ContentResolver`, `ImageCompressor`— vive en `:app` y
 * no es alcanzable desde `:feature:visitas`. Cruza módulo, así que el puerto se
 * gana su existencia; no es una abstracción de ritual.
 *
 * ## Ninguna de estas tres funciones puede detener el registro
 *
 * **Pueden lanzar**, y se espera que lo hagan: la cámara falla, el permiso se
 * niega, el almacenamiento se llena, la compresión revienta con un OOM en un
 * teléfono de gama baja. El llamador
 * ([com.example.msp_app.feature.visitas.ui.RegistrarVisitaViewModel]) las llama
 * SIEMPRE dentro de un `catch (Throwable)` con telemetría, y ninguna corre
 * dentro del camino que escribe la visita. La regla que manda sobre todo lo
 * demás en esta tarea es que **la foto nunca bloquea el guardado** — la misma
 * que ya rige para la ubicación desde la Task 5.
 */
interface ComprobantesDeVisitaPort {

    /**
     * Prepara un destino nuevo para la cámara: acuña el id que viajará como
     * `id_<n>` y devuelve dónde escribir.
     *
     * No toma la foto ni deja nada que valga la pena conservar: si la cámara no
     * vuelve, el archivo queda vacío y [descartar] lo borra.
     */
    suspend fun nuevoDestino(): DestinoDeFoto

    /**
     * La cámara ya escribió en [destino]: comprime, deja el archivo definitivo y
     * devuelve el comprobante con **el mismo id** del destino.
     *
     * Conservar el id es el contrato: el id se acuñó antes de abrir la cámara
     * justo para que sobreviva a la muerte del proceso, y acuñar otro aquí
     * volvería a dejar el reintento sin clave estable — que en visitas ni
     * siquiera es una degradación, es un 422.
     */
    suspend fun aceptar(destino: DestinoDeFoto): ComprobanteDeVisita

    /**
     * Borra un archivo local por su **ruta absoluta**: el crudo de una cámara
     * que se canceló, o el comprobante que el cobrador quitó antes de registrar.
     * Sin esto cada foto descartada se queda ocupando disco para siempre.
     */
    suspend fun descartar(archivo: String)
}
