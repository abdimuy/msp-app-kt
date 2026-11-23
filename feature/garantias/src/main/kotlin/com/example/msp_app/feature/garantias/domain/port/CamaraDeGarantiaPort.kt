package com.example.msp_app.feature.garantias.domain.port

import com.example.msp_app.feature.garantias.domain.model.FotoDeCaptura

/**
 * La cámara del sistema, vista desde la captura.
 *
 * ## Por qué el destino es un `String`
 *
 * El contrato `ActivityResultContracts.TakePicture()` recibe un `Uri` y el
 * ViewModel no debe conocer `android.net.Uri`: el estado que sobrevive a la
 * muerte del proceso se guarda en `SavedStateHandle` como texto, y un `Uri` no
 * es serializable sin ceremonia. La pantalla convierte el `content://` de
 * [nuevoDestino] a `Uri` al lanzar la cámara.
 *
 * ## Dos archivos y no uno
 *
 * [nuevoDestino] apunta a `cacheDir`, que es lo único que el `FileProvider` de
 * la app expone para escribir. [archivar] copia el resultado a
 * `filesDir/garantias/fotos/` —el `cacheDir` lo puede vaciar el sistema— y
 * valida que el archivo sea una imagen de las que acepta el servidor. Si no lo
 * es, lo descarta y devuelve `null`: nunca se encola una foto que el servidor
 * rechazaría.
 */
interface CamaraDeGarantiaPort {

    /** Un `content://` nuevo, en `cacheDir`, donde la cámara escribirá el crudo. */
    fun nuevoDestino(): String

    /**
     * Mueve lo que la cámara escribió en [uri] a `filesDir/garantias/fotos/`.
     *
     * Devuelve `null` cuando el archivo no es una imagen JPEG/PNG, pesa más de
     * [LIMITE_DE_BYTES] o no se pudo leer. El crudo se borra en todos los casos.
     */
    suspend fun archivar(uri: String): FotoDeCaptura?

    companion object {
        /** El tope de `POST …/eventos/{eid}/imagenes` en el servidor. */
        const val LIMITE_DE_BYTES: Int = 10 * 1024 * 1024
    }
}
