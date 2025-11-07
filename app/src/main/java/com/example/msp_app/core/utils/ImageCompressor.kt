package com.example.msp_app.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Utilidad profesional para compresión inteligente de imágenes
 *
 * Características:
 * - Compresión adaptativa según tamaño del archivo
 * - Redimensionamiento inteligente preservando aspecto
 * - Corrección automática de orientación EXIF
 * - Optimización de calidad según contenido
 * - Conversión a formato óptimo (JPEG para fotos)
 */
object ImageCompressor {

    private const val TAG = "ImageCompressor"

    // Configuración inteligente basada en mejores prácticas
    private const val MAX_DIMENSION_SMALL = 1024   // Para imágenes pequeñas
    private const val MAX_DIMENSION_MEDIUM = 1920  // Para imágenes medianas
    private const val MAX_DIMENSION_LARGE = 2560   // Para imágenes grandes

    // Umbrales de tamaño de archivo (en bytes)
    private const val SIZE_THRESHOLD_SMALL = 500 * 1024    // 500 KB
    private const val SIZE_THRESHOLD_MEDIUM = 2 * 1024 * 1024  // 2 MB
    private const val SIZE_THRESHOLD_LARGE = 5 * 1024 * 1024   // 5 MB

    // Calidades de compresión JPEG (0-100)
    private const val QUALITY_HIGH = 85
    private const val QUALITY_MEDIUM = 75
    private const val QUALITY_LOW = 65

    /**
     * Configuración de compresión calculada según las características de la imagen
     */
    data class CompressionConfig(
        val maxDimension: Int,
        val quality: Int,
        val shouldResize: Boolean,
        val reason: String
    )

    /**
     * Resultado de la compresión con estadísticas
     */
    data class CompressionResult(
        val outputFile: File,
        val originalSize: Long,
        val compressedSize: Long,
        val compressionRatio: Float,
        val originalDimensions: Pair<Int, Int>,
        val finalDimensions: Pair<Int, Int>,
        val config: CompressionConfig
    )

    /**
     * Comprime una imagen de forma inteligente según su tamaño y dimensiones
     *
     * @param context Contexto de la aplicación
     * @param uri URI de la imagen original
     * @param outputFileName Nombre del archivo de salida
     * @return CompressionResult con estadísticas de la compresión
     */
    fun compressImage(
        context: Context,
        uri: Uri,
        outputFileName: String
    ): CompressionResult {
        val startTime = System.currentTimeMillis()

        // 1. Obtener información de la imagen original
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("No se pudo abrir la imagen")

        val originalSize = inputStream.available().toLong()
        inputStream.close()

        // 2. Decodificar opciones para obtener dimensiones sin cargar la imagen completa
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        val originalWidth = options.outWidth
        val originalHeight = options.outHeight
        val originalDimensions = Pair(originalWidth, originalHeight)

        // 3. Determinar configuración óptima de compresión
        val config = determineCompressionConfig(
            originalSize,
            originalWidth,
            originalHeight
        )

        // 4. Calcular factor de escala si es necesario
        val inSampleSize = if (config.shouldResize) {
            calculateInSampleSize(
                originalWidth,
                originalHeight,
                config.maxDimension
            )
        } else {
            1
        }

        // 5. Decodificar la imagen con el factor de escala
        val decodedBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("No se pudo decodificar la imagen")

        // 6. Redimensionar con precisión si es necesario
        val scaledBitmap = if (config.shouldResize &&
            (decodedBitmap.width > config.maxDimension ||
             decodedBitmap.height > config.maxDimension)) {

            val scale = min(
                config.maxDimension.toFloat() / decodedBitmap.width,
                config.maxDimension.toFloat() / decodedBitmap.height
            )

            val newWidth = (decodedBitmap.width * scale).toInt()
            val newHeight = (decodedBitmap.height * scale).toInt()

            Bitmap.createScaledBitmap(decodedBitmap, newWidth, newHeight, true).also {
                if (it != decodedBitmap) {
                    decodedBitmap.recycle()
                }
            }
        } else {
            decodedBitmap
        }

        // 7. Corregir orientación EXIF
        val rotatedBitmap = correctImageOrientation(context, uri, scaledBitmap)
        if (rotatedBitmap != scaledBitmap) {
            scaledBitmap.recycle()
        }

        val finalDimensions = Pair(rotatedBitmap.width, rotatedBitmap.height)

        // 8. Guardar con la calidad calculada
        val outputFile = File(context.filesDir, outputFileName)
        FileOutputStream(outputFile).use { out ->
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, config.quality, out)
            out.flush()
        }

        rotatedBitmap.recycle()

        val compressedSize = outputFile.length()
        val compressionRatio = (compressedSize.toFloat() / originalSize.toFloat())

        return CompressionResult(
            outputFile = outputFile,
            originalSize = originalSize,
            compressedSize = compressedSize,
            compressionRatio = compressionRatio,
            originalDimensions = originalDimensions,
            finalDimensions = finalDimensions,
            config = config
        )
    }

    /**
     * Determina la configuración óptima de compresión basándose en el tamaño
     * y dimensiones de la imagen
     *
     * Esta función es internal para permitir testing
     */
    internal fun determineCompressionConfig(
        fileSize: Long,
        width: Int,
        height: Int
    ): CompressionConfig {
        val maxDimension = maxOf(width, height)
        val megapixels = (width * height) / 1_000_000f

        return when {
            // Imágenes muy grandes (>5MB o >8MP)
            fileSize > SIZE_THRESHOLD_LARGE || megapixels > 8 -> {
                CompressionConfig(
                    maxDimension = MAX_DIMENSION_LARGE,
                    quality = QUALITY_MEDIUM,
                    shouldResize = true,
                    reason = "Imagen muy grande: ${formatFileSize(fileSize)}, " +
                            "${String.format("%.1f", megapixels)}MP - " +
                            "Compresión agresiva aplicada"
                )
            }

            // Imágenes medianas (2-5MB o 4-8MP)
            fileSize > SIZE_THRESHOLD_MEDIUM || megapixels > 4 -> {
                CompressionConfig(
                    maxDimension = MAX_DIMENSION_MEDIUM,
                    quality = QUALITY_MEDIUM,
                    shouldResize = true,
                    reason = "Imagen mediana: ${formatFileSize(fileSize)}, " +
                            "${String.format("%.1f", megapixels)}MP - " +
                            "Compresión moderada aplicada"
                )
            }

            // Imágenes pequeñas pero con alta resolución (>2MP)
            megapixels > 2 -> {
                CompressionConfig(
                    maxDimension = MAX_DIMENSION_SMALL,
                    quality = QUALITY_HIGH,
                    shouldResize = true,
                    reason = "Alta resolución: ${String.format("%.1f", megapixels)}MP - " +
                            "Reducción de resolución conservando calidad"
                )
            }

            // Imágenes que ya están optimizadas (<500KB y <2MP)
            fileSize < SIZE_THRESHOLD_SMALL -> {
                CompressionConfig(
                    maxDimension = maxDimension,
                    quality = QUALITY_HIGH,
                    shouldResize = false,
                    reason = "Imagen ya optimizada: ${formatFileSize(fileSize)}, " +
                            "${String.format("%.1f", megapixels)}MP - " +
                            "Solo recompresión ligera"
                )
            }

            // Caso por defecto: compresión balanceada
            else -> {
                CompressionConfig(
                    maxDimension = MAX_DIMENSION_MEDIUM,
                    quality = QUALITY_HIGH,
                    shouldResize = maxDimension > MAX_DIMENSION_MEDIUM,
                    reason = "Compresión balanceada: ${formatFileSize(fileSize)}, " +
                            "${String.format("%.1f", megapixels)}MP"
                )
            }
        }
    }

    /**
     * Calcula el factor de muestreo óptimo para reducir la imagen
     * inSampleSize siempre es potencia de 2 para mejor rendimiento
     *
     * Esta función es internal para permitir testing
     */
    internal fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDimension: Int
    ): Int {
        var inSampleSize = 1
        val maxOriginalDimension = maxOf(width, height)

        if (maxOriginalDimension > maxDimension) {
            val halfWidth = width / 2
            val halfHeight = height / 2

            while ((halfWidth / inSampleSize) >= maxDimension &&
                   (halfHeight / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Corrige la orientación de la imagen según los metadatos EXIF
     * Esto es crucial para fotos tomadas con cámara
     */
    private fun correctImageOrientation(
        context: Context,
        uri: Uri,
        bitmap: Bitmap
    ): Bitmap {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap

        return try {
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(bitmap, horizontal = true)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(bitmap, horizontal = false)
                else -> bitmap
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer EXIF, usando orientación original", e)
            bitmap
        } finally {
            inputStream.close()
        }
    }

    /**
     * Rota un bitmap el ángulo especificado
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it != bitmap) bitmap.recycle() }
    }

    /**
     * Voltea un bitmap horizontal o verticalmente
     */
    private fun flipBitmap(bitmap: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix().apply {
            if (horizontal) {
                postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            } else {
                postScale(1f, -1f, bitmap.width / 2f, bitmap.height / 2f)
            }
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it != bitmap) bitmap.recycle() }
    }

    /**
     * Formatea el tamaño de archivo a formato legible
     *
     * Esta función es internal para permitir testing
     */
    internal fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.2f MB", bytes / (1024f * 1024f))
        }
    }

    /**
     * Valida que el URI sea una imagen válida
     */
    fun isValidImage(context: Context, uri: Uri): Boolean {
        return try {
            val mimeType = context.contentResolver.getType(uri)
            mimeType?.startsWith("image/") == true
        } catch (e: Exception) {
            Log.e(TAG, "Error validando imagen", e)
            false
        }
    }

    /**
     * Estima el tamaño de archivo resultante sin procesarlo
     * Útil para mostrar al usuario antes de comprimir
     */
    fun estimateCompressedSize(
        context: Context,
        uri: Uri
    ): Long {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return 0
        val originalSize = inputStream.available().toLong()
        inputStream.close()

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        val config = determineCompressionConfig(
            originalSize,
            options.outWidth,
            options.outHeight
        )

        // Estimación basada en experiencia empírica
        val estimatedRatio = when {
            !config.shouldResize -> 0.7f  // Solo recompresión
            config.maxDimension == MAX_DIMENSION_SMALL -> 0.15f
            config.maxDimension == MAX_DIMENSION_MEDIUM -> 0.25f
            else -> 0.35f
        }

        return (originalSize * estimatedRatio).toLong()
    }
}
