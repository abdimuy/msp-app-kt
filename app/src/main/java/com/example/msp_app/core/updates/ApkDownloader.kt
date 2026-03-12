package com.example.msp_app.core.updates

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class DownloadState {
    IDLE,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

object ApkDownloader {

    private val _state = MutableStateFlow(DownloadState.IDLE)
    val state: StateFlow<DownloadState> = _state

    private var downloadId: Long = -1

    fun download(context: Context, url: String, version: String) {
        if (_state.value == DownloadState.DOWNLOADING) return

        _state.value = DownloadState.DOWNLOADING

        val fileName = "msp-app-$version.apk"
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadDir, fileName)
        if (file.exists()) file.delete()

        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("MSP App $version")
                .setDescription("Descargando actualización...")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = dm.enqueue(request)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id != downloadId) return

                    ctx.unregisterReceiver(this)

                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = dm.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val status = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        )
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            _state.value = DownloadState.COMPLETED
                            installApk(ctx, file)
                        } else {
                            _state.value = DownloadState.FAILED
                        }
                        cursor.close()
                    } else {
                        _state.value = DownloadState.FAILED
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
        } catch (e: Exception) {
            Log.e("ApkDownloader", "Error downloading APK: ${e.message}")
            _state.value = DownloadState.FAILED
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("ApkDownloader", "Error installing APK: ${e.message}")
        }
    }

    fun reset() {
        _state.value = DownloadState.IDLE
    }
}
