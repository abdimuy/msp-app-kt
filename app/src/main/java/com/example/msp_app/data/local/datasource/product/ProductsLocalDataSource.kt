package com.example.msp_app.data.local.datasource.product;

import android.content.Context
import com.example.msp_app.data.local.AppDatabase
import com.example.msp_app.data.local.entities.ProductEntity

class ProductsLocalDataSource (context: Context) {
    private val productDao = AppDatabase.getInstance(context).productDao()

    suspend fun getProductById(id:Int): Int {
        return productDao.getProductById(id)
    }

    suspend fun getProductByFolio(folio:String): List<String>{
        return productDao.getProductByFolio(folio)
    }

    suspend fun saveAll(products:List<ProductEntity>){
        productDao.deleteAll()
        productDao.saveAll(products)
    }
}
