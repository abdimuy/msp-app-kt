package com.example.msp_app.feature.garantias.data.files

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.msp_app.feature.garantias.domain.model.FotoDeCaptura
import com.example.msp_app.feature.garantias.domain.port.CamaraDeGarantiaPort
import com.example.msp_app.feature.garantias.domain.port.GeneradorDeClavesPort
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La cámara del sistema, con el `FileProvider` de `:app`.
 *
 * ## Dos archivos
 *
 * La cámara escribe en `cacheDir/garantias_camara/` porque el `FileProvider` de
 * la app sólo expone `cache-path "."` para escritura. En cuanto la foto llega,
 * [archivar] la copia a `filesDir/garantias/fotos/` —el sistema puede vaciar
 * `cacheDir` en cualquier momento— y **borra el crudo siempre**, incluso cuando
 * rechaza la foto. Un crudo huérfano se quedaría para siempre en la caché.
 *
 * ## El tipo se decide por los bytes
 *
 * El MIME que declara el proveedor no se cree: se leen los primeros bytes y se
 * compara con las firmas de JPEG y PNG, los dos tipos que acepta el servidor. Un
 * archivo vacío —cámara que contesta OK sin escribir— no coincide con ninguna
 * firma y se descarta. La clave de la foto se acuña **aquí**, una sola vez: es
 * también el `id` de la imagen en el servidor y no se puede regenerar en un
 * reintento (D5).
 */
internal class CamaraDeGarantiaAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directorio: DirectorioDeGarantias,
    private val claves: GeneradorDeClavesPort
) : CamaraDeGarantiaPort {

    override fun nuevoDestino(): String {
        val carpeta = File(context.cacheDir, CARPETA_CRUDA)
        carpeta.mkdirs()
        val crudo = File(carpeta, "${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, autoridad(), crudo).toString()
    }

    override suspend fun archivar(uri: String): FotoDeCaptura? {
        val crudo = archivoCrudo(uri)
        return try {
            withContext(Dispatchers.IO) { copiar(uri) }
        } finally {
            crudo?.delete()
        }
    }

    /**
     * El crudo que la cámara escribió, deducido del último segmento del
     * `content://`.
     *
     * No se guarda en memoria a propósito: la cámara puede matar el proceso y el
     * `content://` es lo único que sobrevive en el `SavedStateHandle`. Si el
     * segmento no existe (un `content://` ajeno), no hay nada que borrar y el
     * archivo igual se lee por el `ContentResolver`.
     */
    private fun archivoCrudo(uri: String): File? {
        val nombre = runCatching { Uri.parse(uri).lastPathSegment }.getOrNull() ?: return null
        if (nombre.isBlank() || nombre.contains('/')) return null
        return File(File(context.cacheDir, CARPETA_CRUDA), nombre)
    }

    private fun copiar(uri: String): FotoDeCaptura? = try {
        val crudo = Uri.parse(uri)
        val tamano = tamanoDe(crudo)
        if (tamano == null || tamano <= 0 || tamano > CamaraDeGarantiaPort.LIMITE_DE_BYTES) {
            null
        } else {
            val bytes = context.contentResolver.openInputStream(crudo)?.use { it.readBytes() }
            val mime = bytes?.let(::mimeDe)
            if (bytes == null || mime == null) {
                null
            } else {
                guardar(bytes, mime)
            }
        }
    } catch (_: IOException) {
        null
    }

    private fun guardar(bytes: ByteArray, mime: String): FotoDeCaptura? {
        val clave = claves.nueva()
        val destino = directorio.nuevoArchivo(
            DirectorioDeGarantias.FOTOS,
            "$clave.${extensionDe(mime)}"
        )
        return try {
            destino.writeBytes(bytes)
            FotoDeCaptura(
                clave = clave,
                rutaArchivo = directorio.relativa(destino),
                mime = mime
            )
        } catch (_: IOException) {
            destino.delete()
            null
        }
    }

    private fun tamanoDe(uri: Uri): Long? = try {
        context.contentResolver.openFileDescriptor(uri, MODO_LECTURA)?.use { it.statSize }
    } catch (_: IOException) {
        null
    }

    private fun mimeDe(bytes: ByteArray): String? = when {
        bytes.coincideCon(FIRMA_JPEG) -> MIME_JPEG
        bytes.coincideCon(FIRMA_PNG) -> MIME_PNG
        else -> null
    }

    private fun extensionDe(mime: String): String = if (mime == MIME_PNG) "png" else "jpg"

    private fun ByteArray.coincideCon(firma: ByteArray): Boolean =
        size >= firma.size && copyOfRange(0, firma.size).contentEquals(firma)

    private fun autoridad(): String = "${context.packageName}.fileprovider"

    private companion object {
        const val CARPETA_CRUDA = "garantias_camara"
        const val MODO_LECTURA = "r"
        const val MIME_JPEG = "image/jpeg"
        const val MIME_PNG = "image/png"

        // Firmas de archivo, no números sueltos: `0xFFD8FF` es el inicio de un
        // JPEG y los ocho bytes `89 50 4E 47 0D 0A 1A 0A` son la de un PNG. Se
        // comparan como bloques para que un archivo corto no reviente el índice.
        val FIRMA_JPEG: ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val FIRMA_PNG: ByteArray = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A
        )
    }
}
