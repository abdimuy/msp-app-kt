package com.example.msp_app.data.local.repository

import android.os.Build
import android.util.Log
import com.example.msp_app.data.local.dao.sale.SaleDao
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaymentsRepository(
    private val paymentStore: PaymentsLocalDataSource,
    private val saleDao: SaleDao
) {

    suspend fun exportPaymentsJsonWithSales(user: User?) {
        try {
            val db = FirebaseFirestore.getInstance()
            val gson = Gson()
            val exportsCollection = db.collection("export")
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            val payments = paymentStore.getAllPayments()
            val paymentsJson = gson.toJson(payments)

            val sales = saleDao.getAll()
            val productsArray = sales.map { sale ->
                mapOf(
                    "FOLIO" to sale.FOLIO,
                    "PRODUCTOS" to sale.PRODUCTOS,
                    "PRECIO_TOTAL" to sale.PRECIO_TOTAL,
                )
            }
            val productsJson = gson.toJson(productsArray)

            val deviceInfo = mapOf(
                "MANUFACTURER" to Build.MANUFACTURER,
                "MODEL" to Build.MODEL,
                "DEVICE" to Build.DEVICE,
                "BRAND" to Build.BRAND,
                "VERSION" to Build.VERSION.RELEASE,
                "SDK_INT" to Build.VERSION.SDK_INT
            )
            val deviceJson = gson.toJson(deviceInfo)

            user?.let {
                val userJson = gson.toJson(it)
                exportsCollection.document("user_info")
                    .set(
                        mapOf(
                            "type" to "user_info",
                            "data" to userJson,
                            "timestamp" to today
                        )
                    )
            }

            exportsCollection.add(
                mapOf(
                    "type" to "payments",
                    "data" to paymentsJson,
                    "timestamp" to today
                )
            )
            exportsCollection.add(
                mapOf(
                    "type" to "products",
                    "data" to productsJson,
                    "timestamp" to today
                )
            )
            exportsCollection.add(
                mapOf(
                    "type" to "device_info",
                    "data" to deviceJson,
                    "timestamp" to today
                )
            )

            Log.d("AutoExport", "✅ Datos exportados correctamente")

        } catch (e: Exception) {
            Log.e("AutoExport", "❌ Error exportando", e)
        }
    }
}
