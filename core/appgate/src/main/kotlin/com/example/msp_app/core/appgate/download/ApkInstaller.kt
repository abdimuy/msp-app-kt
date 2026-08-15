package com.example.msp_app.core.appgate.download

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

private const val TAG = "ApkInstaller"

/** Sufijo de la autoridad de [AppGateFileProvider], declarada en el manifiesto del módulo. */
private const val PROVIDER_SUFFIX = ".appgate.updates"

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

/**
 * Abre el instalador del sistema con el APK ya descargado.
 *
 * El archivo vive en el almacenamiento privado de la app, así que se entrega
 * por `FileProvider` (un `file://` lo rechaza el sistema desde Android 7). El
 * `Intent` sale del contexto de aplicación y por eso lleva `NEW_TASK`.
 *
 * Que el usuario acepte o no la instalación ya no es asunto de este módulo:
 * la pantalla de bloqueo sigue puesta hasta que el `versionCode` instalado
 * alcance el mínimo, que es la única señal que la compuerta reconoce.
 */
class ApkInstaller(private val context: Context) {

    fun install(apk: File) {
        if (!apk.isFile) {
            Log.w(TAG, "no hay APK que instalar en ${apk.name}")
            return
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + PROVIDER_SUFFIX, apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
