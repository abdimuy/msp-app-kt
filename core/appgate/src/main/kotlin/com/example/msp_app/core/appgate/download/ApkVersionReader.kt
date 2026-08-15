package com.example.msp_app.core.appgate.download

import android.content.Context
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

private const val TAG = "ApkVersionReader"

/** Qué versión trae un APK que está en disco. */
data class ApkVersion(
    val versionCode: Int,
    val versionName: String
)

/**
 * Lee la versión **del archivo**, no la que la configuración remota promete.
 *
 * Es un puerto por la misma razón que [com.example.msp_app.core.appgate.MinVersionConfigSource]:
 * de esta lectura depende decidir si el APK publicado sirve de algo, y eso
 * tiene que poder probarse sin `PackageManager` de por medio.
 */
fun interface ApkVersionReader {
    /** `null` si el archivo no existe, está a medias o no es un APK legible. */
    fun read(apk: File): ApkVersion?
}

/**
 * Implementación sobre `PackageManager.getPackageArchiveInfo`.
 *
 * **Por qué se lee el archivo y no se agrega un campo de configuración:** el
 * dato ya está dentro del APK, lo puso el mismo build que lo generó, y no se
 * puede escribir mal en Firestore. Un `MIN_VERSION_APK_CODE` a mano sería una
 * segunda fuente de verdad que la oficina puede equivocar exactamente igual
 * que se equivocó con la URL.
 */
class PackageManagerApkVersionReader(private val context: Context) : ApkVersionReader {

    override fun read(apk: File): ApkVersion? {
        if (!apk.isFile) return null
        // `getPackageArchiveInfo` devuelve null ante un archivo truncado o que
        // no es un APK; además puede lanzar en algunos OEM al parsear basura.
        val info = runCatching {
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        }.onFailure { error ->
            Log.w(TAG, "no se pudo leer la versión de ${apk.name}", error)
        }.getOrNull() ?: return null
        return ApkVersion(
            versionCode = PackageInfoCompat.getLongVersionCode(info).toInt(),
            versionName = info.versionName.orEmpty()
        )
    }
}
