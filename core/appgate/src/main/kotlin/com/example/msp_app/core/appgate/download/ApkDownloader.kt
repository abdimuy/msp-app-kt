package com.example.msp_app.core.appgate.download

import android.util.Log
import com.example.msp_app.core.appgate.UpdatePackage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import okhttp3.ResponseBody

private const val TAG = "ApkDownloader"
private const val HTTP_PARTIAL_CONTENT = 206
private const val BUFFER_SIZE = 64 * 1024
private const val HEX_MASK = 0xFF
private const val HEX_RADIX_PAD = 0x100
private const val HEX_RADIX = 16
private const val HEX_PAD_PREFIX = 1

/** Cómo terminó una descarga. */
sealed interface DownloadOutcome {
    /** El archivo está completo y su checksum coincide. Listo para instalar. */
    data class Completed(val file: File) : DownloadOutcome

    /**
     * Bajó entero pero el SHA-256 no coincide. El archivo parcial **se borra**:
     * un APK corrupto que se quedara en disco haría fallar la instalación una y
     * otra vez sin que nadie entienda por qué.
     */
    data object IntegrityFailed : DownloadOutcome

    /**
     * Se cortó a medias (red, 4xx/5xx). Lo bajado **se conserva** — es
     * justamente lo que permite reanudar, y decirle al usuario que no perdió
     * lo que ya bajó es lo que hace que vuelva a intentar.
     */
    data class Failed(val reason: String) : DownloadOutcome
}

/**
 * Descarga del APK: **reanudable** y con **verificación de integridad**.
 *
 * Reanudación por `Range: bytes=N-`. Tres caminos posibles y los tres son
 * correctos:
 * - el servidor responde `206` → se **agrega** a lo que ya había;
 * - responde `200` (no soporta rangos) → se **trunca** y se baja de cero;
 * - ya está completo en disco → ni se pide, se pasa directo a verificar.
 *
 * Integridad por SHA-256 contra [UpdatePackage.sha256]. No es paranoia de
 * seguridad: una descarga cortada a mitad de un byte produce un APK que el
 * instalador rechaza con un error ilegible, y el checksum convierte eso en
 * "vuelve a intentar" antes de molestar al usuario.
 *
 * El progreso se informa en bytes y la UI lo muestra en megas
 * ([DownloadProgress.megabytesLabel]).
 *
 * Esta clase NO decide *cuándo* se descarga — eso es política de red y vive en
 * [UpdateDownloadScheduler] (automática solo por wifi, manual en cualquier
 * red).
 */
class ApkDownloader(
    private val callFactory: Call.Factory,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun download(
        update: UpdatePackage,
        destination: File,
        onProgress: (DownloadProgress) -> Unit = {}
    ): DownloadOutcome = withContext(ioDispatcher) {
        try {
            fetch(update, destination, onProgress)
        } catch (e: IOException) {
            Log.w(TAG, "descarga interrumpida, lo bajado se conserva", e)
            DownloadOutcome.Failed(e.message ?: "error de red")
        }
    }

    private fun fetch(
        update: UpdatePackage,
        destination: File,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadOutcome {
        destination.parentFile?.mkdirs()
        val alreadyHave = if (destination.isFile) destination.length() else 0L

        // Ya está entero en disco (típicamente: se bajó por wifi y el bloqueo
        // llegó después). Pedirlo otra vez solo gastaría datos.
        if (update.sizeBytes > 0L && alreadyHave >= update.sizeBytes) {
            onProgress(DownloadProgress(update.sizeBytes, update.sizeBytes))
            return verify(destination, update.sha256)
        }

        callFactory.newCall(rangeRequest(update.url, alreadyHave)).execute().use { response ->
            if (!response.isSuccessful) return DownloadOutcome.Failed("http ${response.code}")
            val body = response.body ?: return DownloadOutcome.Failed("respuesta sin cuerpo")
            val resumed = response.code == HTTP_PARTIAL_CONTENT
            val startAt = if (resumed) alreadyHave else 0L
            writeBody(
                body,
                destination,
                resumed,
                startAt,
                totalOf(update, startAt, body),
                onProgress
            )
        }

        return verify(destination, update.sha256)
    }

    private fun writeBody(
        body: ResponseBody,
        destination: File,
        append: Boolean,
        startAt: Long,
        totalBytes: Long,
        onProgress: (DownloadProgress) -> Unit
    ) {
        var written = startAt
        onProgress(DownloadProgress(written, totalBytes))
        // `append = false` trunca: es exactamente lo que se quiere cuando el
        // servidor ignoró el `Range` y mandó el archivo entero desde cero.
        FileOutputStream(destination, append).use { out ->
            val input = body.byteStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var read = input.read(buffer)
            while (read != -1) {
                out.write(buffer, 0, read)
                written += read
                onProgress(DownloadProgress(written, totalBytes))
                read = input.read(buffer)
            }
        }
    }

    private fun verify(file: File, expectedSha256: String): DownloadOutcome {
        val actual = sha256Of(file)
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            Log.w(TAG, "checksum del APK no coincide, se descarta lo bajado")
            file.delete()
            return DownloadOutcome.IntegrityFailed
        }
        return DownloadOutcome.Completed(file)
    }
}

private fun rangeRequest(url: String, alreadyHave: Long): Request {
    val builder = Request.Builder().url(url)
    if (alreadyHave > 0L) {
        builder.header("Range", "bytes=$alreadyHave-")
    }
    return builder.build()
}

/**
 * El tamaño anunciado por la configuración remota manda; si no lo hay, se usa
 * el `Content-Length` de esta respuesta sumado a lo que ya estaba en disco.
 * Un `Content-Length` desconocido (`-1`) deja el total en `0` y la UI cae al
 * modo "sin barra" en vez de pintar una fracción inventada.
 */
private fun totalOf(update: UpdatePackage, startAt: Long, body: ResponseBody): Long {
    if (update.sizeBytes > 0L) return update.sizeBytes
    val remaining = body.contentLength()
    return if (remaining > 0L) startAt + remaining else 0L
}

private fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(BUFFER_SIZE)
        var read = input.read(buffer)
        while (read != -1) {
            digest.update(buffer, 0, read)
            read = input.read(buffer)
        }
    }
    return digest.digest().joinToString("") { byte ->
        // `and HEX_MASK` para que un byte negativo no se imprima con signo;
        // `+ HEX_RADIX_PAD` fuerza los dos dígitos y luego se recorta el "1".
        ((byte.toInt() and HEX_MASK) + HEX_RADIX_PAD).toString(HEX_RADIX).substring(HEX_PAD_PREFIX)
    }
}
