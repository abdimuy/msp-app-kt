package com.example.msp_app.core.sync

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * Helper para construir requests multipart de manera sencilla.
 * Útil para sincronizar entidades con imágenes/archivos adjuntos.
 */
class MultipartRequestBuilder {

    private val gson = Gson()
    private val parts = mutableListOf<MultipartBody.Part>()
    private var jsonBody: RequestBody? = null

    /**
     * Agrega el body JSON principal (campo "datos")
     */
    fun <T> withJsonData(data: T, fieldName: String = "datos"): MultipartRequestBuilder {
        val json = gson.toJson(data)
        jsonBody = json.toRequestBody("application/json".toMediaTypeOrNull())
        return this
    }

    /**
     * Agrega un archivo de imagen
     */
    fun addImage(
        file: File,
        fieldName: String = "imagenes",
        fileName: String? = null
    ): MultipartRequestBuilder {
        if (file.exists()) {
            val mimeType = getMimeType(file)
            val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData(
                fieldName,
                fileName ?: file.name,
                requestFile
            )
            parts.add(part)
        }
        return this
    }

    /**
     * Agrega múltiples imágenes
     */
    fun addImages(
        files: List<File>,
        fieldName: String = "imagenes"
    ): MultipartRequestBuilder {
        files.forEach { file ->
            addImage(file, fieldName)
        }
        return this
    }

    /**
     * Agrega imágenes desde rutas de archivo
     */
    fun addImagePaths(
        paths: List<String>,
        fieldName: String = "imagenes"
    ): MultipartRequestBuilder {
        paths.forEach { path ->
            val file = File(path)
            addImage(file, fieldName)
        }
        return this
    }

    /**
     * Agrega un campo de texto
     */
    fun addTextField(
        fieldName: String,
        value: String
    ): MultipartRequestBuilder {
        val part = MultipartBody.Part.createFormData(fieldName, value)
        parts.add(part)
        return this
    }

    /**
     * Agrega una descripción para una imagen (descripcion_0, descripcion_1, etc.)
     */
    fun addImageDescription(index: Int, description: String): MultipartRequestBuilder {
        return addTextField("descripcion_$index", description)
    }

    /**
     * Agrega un ID para una imagen (id_0, id_1, etc.)
     */
    fun addImageId(index: Int, imageId: String): MultipartRequestBuilder {
        return addTextField("id_$index", imageId)
    }

    /**
     * Construye el request multipart
     */
    fun build(): MultipartRequest {
        return MultipartRequest(
            jsonData = jsonBody,
            images = parts.toList()
        )
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}

/**
 * Request multipart construido
 */
data class MultipartRequest(
    private val jsonData: RequestBody?,
    private val images: List<MultipartBody.Part>
) {
    /**
     * Obtiene solo las partes de imagen
     */
    fun getImageParts(): List<MultipartBody.Part> = images

    /**
     * Verifica si tiene imágenes
     */
    fun hasImages(): Boolean = images.isNotEmpty()

    /**
     * Obtiene el body JSON o un objeto vacío si es null
     */
    fun getJsonBody(): RequestBody = jsonData
        ?: "{}".toRequestBody("application/json".toMediaTypeOrNull())
}

/**
 * Extension function para crear un builder
 */
fun multipartRequest(block: MultipartRequestBuilder.() -> Unit): MultipartRequest {
    return MultipartRequestBuilder().apply(block).build()
}

/**
 * Helper object con métodos estáticos para casos simples
 */
object MultipartHelper {

    private val gson = Gson()

    /**
     * Crea un RequestBody JSON desde cualquier objeto
     */
    fun <T> createJsonBody(data: T): RequestBody {
        val json = gson.toJson(data)
        return json.toRequestBody("application/json".toMediaTypeOrNull())
    }

    /**
     * Crea un MultipartBody.Part desde un archivo
     */
    fun createImagePart(
        file: File,
        fieldName: String = "imagenes"
    ): MultipartBody.Part? {
        if (!file.exists()) return null

        val mimeType = getMimeType(file)
        val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(fieldName, file.name, requestFile)
    }

    /**
     * Crea lista de partes de imagen desde archivos
     */
    fun createImageParts(
        files: List<File>,
        fieldName: String = "imagenes"
    ): List<MultipartBody.Part> {
        return files.mapNotNull { file ->
            createImagePart(file, fieldName)
        }
    }

    /**
     * Crea lista de partes de imagen desde rutas
     */
    fun createImagePartsFromPaths(
        paths: List<String>,
        fieldName: String = "imagenes"
    ): List<MultipartBody.Part> {
        return paths.mapNotNull { path ->
            val file = File(path)
            createImagePart(file, fieldName)
        }
    }

    /**
     * Crea un RequestBody de texto plano
     */
    fun createTextBody(text: String): RequestBody {
        return text.toRequestBody("text/plain".toMediaTypeOrNull())
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}
