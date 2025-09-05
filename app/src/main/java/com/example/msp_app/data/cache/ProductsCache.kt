package com.example.msp_app.data.cache

import android.content.Context
import com.example.msp_app.data.local.entities.ProductInventoryEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class ProductsCache(private val context: Context) {
    
    private val gson = Gson()
    private val cacheFile = File(context.filesDir, "products_cache.json")
    
    suspend fun saveProducts(products: List<ProductInventoryEntity>) {
        try {
            val json = gson.toJson(products)
            cacheFile.writeText(json)
        } catch (e: Exception) {
        }
    }
    
    suspend fun getProducts(): List<ProductInventoryEntity> {
        return try {
            if (!cacheFile.exists()) {
                emptyList()
            } else {
                val json = cacheFile.readText()
                val type = object : TypeToken<List<ProductInventoryEntity>>() {}.type
                gson.fromJson<List<ProductInventoryEntity>>(json, type) ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun hasProducts(): Boolean {
        return cacheFile.exists() && cacheFile.length() > 0
    }
    
    suspend fun searchProducts(query: String): List<ProductInventoryEntity> {
        val products = getProducts()
        return products.filter { 
            it.ARTICULO.contains(query, ignoreCase = true) ||
            it.LINEA_ARTICULO.contains(query, ignoreCase = true)
        }
    }
}