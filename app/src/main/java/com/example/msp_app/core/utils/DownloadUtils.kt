package com.example.msp_app.core.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

suspend fun downloadApk(context: Context, url: String): File = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()
    val response = client.newCall(request).execute()

    val apkFile = File(context.cacheDir, "app-release.apk")
    apkFile.outputStream().use { output ->
        response.body?.byteStream()?.copyTo(output)
    }

    apkFile
}
