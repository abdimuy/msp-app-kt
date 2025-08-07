package com.example.msp_app.features.productsInventoryImages.viewmodels

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.data.api.ApiProviderImages
import com.example.msp_app.data.api.services.productsInventoryImages.ProductsInventoryImagesApi
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.datasource.productInventoryImage.ProductInventoryImageLocalDataSource
import com.example.msp_app.data.local.entities.ProductInventoryImageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ProductInventoryImagesViewModel(application: Application) : AndroidViewModel(application) {

    private val localDataSource = ProductInventoryImageLocalDataSource(
        context = application,
        productDao = AppDatabase.getInstance(application).productInventoryDao()
    )
    private val api = ApiProviderImages.create(ProductsInventoryImagesApi::class.java)

    private val _imagesByProduct = MutableStateFlow<Map<Int, List<String>>>(emptyMap())
    val imagesByProduct: StateFlow<Map<Int, List<String>>> = _imagesByProduct

    private val _newImagesCount = MutableStateFlow(0)
    val newImagesCount: StateFlow<Int> = _newImagesCount

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress

    private var cachedApiImages: List<ProductInventoryImageEntity> = emptyList()
    private var downloadJob: Job? = null


    fun checkForNewImages() {
        viewModelScope.launch {
            try {
                val response = api.getAllImages()
                val newImages = response.flatMap { item ->
                    item.urls.mapIndexed { index, url ->
                        ProductInventoryImageEntity(
                            IMAGEN_ID = item.id * 1000 + index,
                            ARTICULO_ID = item.id,
                            RUTA_LOCAL = url
                        )
                    }
                }

                val localImages = localDataSource.getAllImages()
                val newImagesToDownload = newImages.filter { apiImage ->
                    localImages.none { localImage -> localImage.IMAGEN_ID == apiImage.IMAGEN_ID }
                }
                cachedApiImages = newImagesToDownload
                _newImagesCount.value = newImagesToDownload.size
            } catch (e: Exception) {
                Log.e("ProductImagesVM", "Error en checkForNewImages", e)
                e.printStackTrace()
                _newImagesCount.value = 0
            }
        }
    }

    fun downloadNewImages() {
        downloadJob = viewModelScope.launch {
            try {
                val newImagesToDownload = cachedApiImages

                val imagesWithLocalPaths = mutableListOf<ProductInventoryImageEntity>()

                _downloadProgress.value = 0
                val total = newImagesToDownload.size
                var downloaded = 0

                for ((index, imageEntity) in newImagesToDownload.withIndex()) {
                    val remoteUrl = imageEntity.RUTA_LOCAL
                    val localPath = downloadAndSaveImage(remoteUrl, imageEntity.IMAGEN_ID)

                    if (localPath != null) {
                        val productExists =
                            localDataSource.existsByProductId(imageEntity.ARTICULO_ID)
                        if (productExists) {
                            imagesWithLocalPaths.add(
                                imageEntity.copy(RUTA_LOCAL = localPath)
                            )
                        }
                    }
                    downloaded++
                    _downloadProgress.value = if (total > 0) (downloaded * 100 / total) else 100
                }
                if (imagesWithLocalPaths.isNotEmpty()) {
                    localDataSource.insertSafeImages(imagesWithLocalPaths)
                }

                val updateImages = localDataSource.getAllImages()
                val groupedImages = updateImages.groupBy { it.ARTICULO_ID }.mapValues { entry ->
                    entry.value.map { it.RUTA_LOCAL }
                }

                _imagesByProduct.value = groupedImages
                _newImagesCount.value = 0

            } catch (e: Exception) {
                Log.e("ProductImagesVM", "Error en downloadNewImages()", e)
            }
        }
    }

    private suspend fun downloadAndSaveImage(remoteUrl: String, imageId: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(remoteUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext null
                }

                val inputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                connection.disconnect()

                if (bitmap == null) {
                    return@withContext null
                }

                val context = getApplication<Application>().applicationContext
                val imagesDir = File(context.filesDir, "products_images")
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }

                val imageFile = File(imagesDir, "image_$imageId.jpg")
                val outputStream = FileOutputStream(imageFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                outputStream.flush()
                outputStream.close()

                return@withContext imageFile.absolutePath

            } catch (e: Exception) {
                Log.e("ProductImagesVM", "Error descargando o guardando imagen $remoteUrl", e)
                null
            }
        }
    }

    fun loadLocalImages() {
        viewModelScope.launch {
            val allImages = localDataSource.getAllImages()
            val groupedImages = allImages.groupBy { it.ARTICULO_ID }.mapValues { entry ->
                entry.value.map { it.RUTA_LOCAL }
            }
            _imagesByProduct.value = groupedImages
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _downloadProgress.value = 0
    }

}
