package com.example.msp_app.features.productsInventoryImages.viewmodels

import android.app.Application
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
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
    private val _downloadedCount = MutableStateFlow(0)
    val downloadedCount: StateFlow<Int> = _downloadedCount

    private val _totalToDownload = MutableStateFlow(0)
    val totalToDownload: StateFlow<Int> = _totalToDownload

    private var cachedApiImages: List<ProductInventoryImageEntity> = emptyList()
    private var downloadJob: Job? = null


    fun checkForNewImages() {
        viewModelScope.launch {
            try {
                val response = api.getAllImages()

                // Obtener los IDs de productos que existen localmente
                val localProducts = withContext(Dispatchers.IO) {
                    localDataSource.getAllProducts()
                }
                val existingProductIds = localProducts.map { it.ARTICULO_ID }.toSet()

                val newImages = response.flatMap { item ->
                    item.urls.mapIndexed { index, url ->
                        ProductInventoryImageEntity(
                            IMAGEN_ID = item.id * 1000 + index,
                            ARTICULO_ID = item.id,
                            RUTA_LOCAL = url
                        )
                    }
                }.filter { image ->
                    existingProductIds.contains(image.ARTICULO_ID)
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

                _downloadProgress.value = 0
                _downloadedCount.value = 0
                val total = newImagesToDownload.size
                _totalToDownload.value = total
                val downloaded = java.util.concurrent.atomic.AtomicInteger(0)

                val semaphore = Semaphore(10)

                val imagesWithLocalPaths =
                    java.util.concurrent.CopyOnWriteArrayList<ProductInventoryImageEntity>()

                val downloadTasks = newImagesToDownload.map { imageEntity ->
                    async(Dispatchers.IO) {
                        semaphore.acquire()
                        try {
                            val remoteUrl = imageEntity.RUTA_LOCAL
                            val localPath =
                                downloadAndSaveImageOptimized(remoteUrl, imageEntity.IMAGEN_ID)

                            if (localPath != null) {
                                imagesWithLocalPaths.add(
                                    imageEntity.copy(RUTA_LOCAL = localPath)
                                )
                            }

                            val currentCount = downloaded.incrementAndGet()
                            _downloadedCount.value = currentCount
                            _downloadProgress.value =
                                if (total > 0) (currentCount * 100 / total) else 100

                            localPath
                        } finally {
                            semaphore.release()
                        }
                    }
                }

                downloadTasks.awaitAll()

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

    private suspend fun downloadAndSaveImageOptimized(remoteUrl: String, imageId: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(remoteUrl)
                val connection = url.openConnection() as HttpURLConnection

                connection.connectTimeout = 5000 // 5 segundos
                connection.readTimeout = 10000 // 10 segundos
                connection.setRequestProperty("User-Agent", "Android")

                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext null
                }

                val context = getApplication<Application>().applicationContext
                val imagesDir = File(context.filesDir, "products_images")
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }

                val imageFile = File(imagesDir, "image_$imageId.jpg")

                connection.inputStream.use { input ->
                    FileOutputStream(imageFile).use { output ->
                        input.copyTo(output, bufferSize = 16384) // Increased buffer size
                    }
                }

                connection.disconnect()

                if (!imageFile.exists() || imageFile.length() == 0L) {
                    return@withContext null
                }

                return@withContext imageFile.absolutePath

            } catch (e: Exception) {
                Log.e("ProductImagesVM", "Error descargando imagen $remoteUrl", e)
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
        _downloadedCount.value = 0
        _totalToDownload.value = 0
    }
}
