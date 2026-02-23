import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import java.io.File

fun saveImageToGallery(context: Context, uri: Uri) {
    try {
        val file = File(uri.path ?: "")
        if (!file.exists()) {
            Toast.makeText(context, "Error: archivo no encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        val inputStream = file.inputStream()
        val filename = "venta_${System.currentTimeMillis()}.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MSP_App")
        }

        val resolver = context.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        if (imageUri == null) {
            Toast.makeText(
                context,
                "Error: no se pudo crear archivo en galería",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        resolver.openOutputStream(imageUri).use { outputStream ->
            if (outputStream == null) {
                Toast.makeText(context, "Error: no se pudo escribir la imagen", Toast.LENGTH_SHORT)
                    .show()
                return
            }
            inputStream.copyTo(outputStream)
        }

        Toast.makeText(context, "✅ Imagen guardada en la galería", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

