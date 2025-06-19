package com.example.msp_app.data.local.datasource.product;

import android.content.Context
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.ProductEntity

class ProductsLocalDataSource (context: Context) {
    private val productDao = AppDatabase.getInstance(context).productDao()

    suspend fun getProductById(id:Int): ProductEntity {
        return productDao.getProductById(id)
    }

    suspend fun getProductsByFolio(folio:String): List<ProductEntity>{
        return productDao.getProductsByFolio(folio)
    }

    suspend fun saveAll(products:List<ProductEntity>){
        productDao.deleteAll()
        productDao.saveAll(products)
    }
}
