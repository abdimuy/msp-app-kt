package com.example.msp_app.feature.compartirproductos.android

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Arma el `Intent` que manda las tarjetas a WhatsApp.
 *
 *  - 1 tarjeta → `ACTION_SEND`; varias → `ACTION_SEND_MULTIPLE`. Siempre `image/jpeg`.
 *  - URIs del `FileProvider` de la app (`${applicationId}.fileprovider`) con
 *    `FLAG_GRANT_READ_URI_PERMISSION` Y el mismo URI en `ClipData`: sin el `ClipData` el permiso
 *    no siempre llega a la app destino.
 *  - `EXTRA_TEXT` = nombres completos y precios elegidos.
 *  - Abre WhatsApp directo si está instalado (`com.whatsapp`, y si no `com.whatsapp.w4b`); si
 *    ninguno, el selector normal de Android.
 */
object IntentDeCompartir {

    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    const val TIPO = "image/jpeg"
    const val TITULO_DEL_SELECTOR = "Compartir"

    /** El paquete de WhatsApp a usar: el normal primero, luego Business; `null` si no hay ninguno. */
    fun paqueteDeWhatsApp(instalado: (String) -> Boolean): String? =
        listOf(WHATSAPP, WHATSAPP_BUSINESS).firstOrNull(instalado)

    @Suppress("SwallowedException") // NameNotFoundException ES la respuesta «no está instalado»
    fun estaInstalado(paquetes: PackageManager, paquete: String): Boolean = try {
        @Suppress("DEPRECATION")
        paquetes.getPackageInfo(paquete, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun uriDe(context: Context, archivo: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)

    /**
     * El intent directo (con [paquete]) o, si [paquete] es `null`, el selector del sistema envolviendo
     * al mismo intent.
     */
    fun armar(uris: List<Uri>, texto: String, paquete: String?): Intent {
        require(uris.isNotEmpty()) { "no hay tarjetas que compartir" }
        val base = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            Intent(
                Intent.ACTION_SEND_MULTIPLE
            ).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        base.type = TIPO
        base.putExtra(Intent.EXTRA_TEXT, texto)
        base.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        base.clipData = ClipData.newRawUri("tarjetas", uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
        if (paquete != null) {
            base.setPackage(paquete)
            return base
        }
        return Intent.createChooser(
            base,
            TITULO_DEL_SELECTOR
        ).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** El intent final según lo que haya instalado en el teléfono. */
    fun paraEsteTelefono(context: Context, uris: List<Uri>, texto: String): Intent =
        armar(uris, texto, paqueteDeWhatsApp { estaInstalado(context.packageManager, it) })
}
