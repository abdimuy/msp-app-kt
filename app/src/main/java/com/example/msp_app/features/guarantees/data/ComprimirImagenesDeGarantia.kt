package com.example.msp_app.features.guarantees.data

import android.content.Context
import android.net.Uri
import com.example.msp_app.core.utils.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Comprime las fotos de una garantía a `filesDir`, en orden. La usan la
 * pantalla y la reanudación (E-APP-119), así que las dos escriben los MISMOS
 * archivos: nombre fijo por (garantía, posición), un reintento sobrescribe el
 * mismo archivo en vez de dejar copias huérfanas (E-APP-091). Una foto que no
 * se puede leer se omite.
 */
@Suppress("TooGenericExceptionCaught") // una foto ilegible se omite; las demás siguen.
internal suspend fun comprimirImagenesDeGarantia(
    context: Context,
    uris: List<Uri>,
    guaranteeId: String
): List<Pair<String, String>> = withContext(Dispatchers.IO) {
    val savedPaths = mutableListOf<Pair<String, String>>()
    uris.forEachIndexed { index, uri ->
        try {
            if (!ImageCompressor.isValidImage(context, uri)) return@forEachIndexed
            val fileName = "guarantee_${guaranteeId}_$index.jpg"
            val result = ImageCompressor.compressImage(
                context = context,
                uri = uri,
                outputFileName = fileName
            )
            savedPaths.add(result.outputFile.absolutePath to "image/jpeg")
        } catch (_: Exception) {
        }
    }
    savedPaths
}
