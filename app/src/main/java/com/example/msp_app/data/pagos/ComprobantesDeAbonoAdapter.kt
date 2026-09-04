package com.example.msp_app.data.pagos

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.payment.PaymentImageDao
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.utils.ImageCompressor
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.domain.Comprobantes
import com.example.msp_app.feature.pagos.domain.model.ComprobanteDelAbono
import com.example.msp_app.feature.pagos.domain.model.DestinoDeFoto
import com.example.msp_app.feature.pagos.domain.port.ComprobantesPort
import java.io.File
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.CancellationException

/**
 * Prefijo de todo archivo de comprobante que esta app escribe. Es lo que hace
 * que el barrido pueda distinguir sus archivos de los demás que viven en
 * `filesDir` (los borradores de venta, entre otros) — un barrido que borrara
 * por antigüedad sin mirar el nombre se llevaría cosas ajenas.
 */
internal const val PREFIJO_COMPROBANTE = "comprobante_pago_"

/**
 * Implementación real de [ComprobantesPort], provista desde el composition root
 * de `:app` (mismo reparto que [RegistroDeAbonoAdapter]): lo que hace falta
 * —`FileProvider`, `ContentResolver`, [ImageCompressor]— vive aquí y no es
 * alcanzable desde `:feature:pagos`.
 *
 * ## Dos archivos por foto, en dos lugares distintos, y no es capricho
 *
 * 1. **El crudo**, en `cacheDir`. Es el único que la cámara puede escribir: el
 *    `FileProvider` de esta app expone `cache-path "."` y de `filesDir` solo
 *    `draft_images/` (`res/xml/file_paths.xml`), así que un destino en
 *    `filesDir` daría `IllegalArgumentException` al pedir su `content://`. Vive
 *    lo que dura la captura.
 * 2. **El comprimido**, en `filesDir`, con el prefijo [PREFIJO_COMPROBANTE]. Ahí
 *    es donde [ImageCompressor] escribe, y el que espera la subida — `cacheDir` lo
 *    puede vaciar el sistema cuando el disco aprieta, que en el teléfono de
 *    gama baja del cobrador **pasa**, y se llevaría el comprobante de un pago
 *    que todavía no sube.
 *
 * ## El tipo se decide por los BYTES, no por la extensión
 *
 * El nombre del destino lo pone esta app, así que preguntarle a la extensión
 * qué escribió la cámara es preguntarnos a nosotros mismos. Se leen los
 * primeros bytes y decide [Comprobantes.tipoDe]. Un archivo vacío —cámara que
 * contesta OK sin escribir— cae en `application/octet-stream`, que no está
 * permitido, y el ViewModel lo rechaza con su aviso en vez de guardar una foto
 * que ninguna subida podrá entregar.
 */
class ComprobantesDeAbonoAdapter(
    private val context: Context,
    private val imagenes: PaymentImageDao,
    private val telemetry: Telemetry,
    private val clock: AppClock = AppClock.System
) : ComprobantesPort {

    override suspend fun nuevoDestino(): DestinoDeFoto {
        barrerHuerfanos()
        val id = UUID.randomUUID().toString()
        val crudo = File(context.cacheDir, "$PREFIJO_CRUDO$id.jpg")
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            crudo
        )
        return DestinoDeFoto(
            id = id,
            uriParaLaCamara = uri.toString(),
            archivoCrudo = crudo.absolutePath
        )
    }

    /**
     * Comprime lo que la cámara dejó y devuelve el comprobante **con el mismo
     * id del destino** — acuñar otro aquí volvería a dejar el reintento de
     * subida sin clave estable, que es justo lo que el id existe para evitar.
     *
     * Un tipo que no reconocemos NO se comprime: se devuelve tal cual, con su
     * tipo, para que el ViewModel lo rechace y borre. Comprimirlo primero
     * produciría un JPEG válido a partir de basura y escondería el problema.
     */
    override suspend fun aceptar(destino: DestinoDeFoto): ComprobanteDelAbono {
        val crudo = File(destino.archivoCrudo)
        val tipo = Comprobantes.tipoDe(primerosBytesDe(crudo))
        if (!Comprobantes.permitido(tipo)) {
            return ComprobanteDelAbono(id = destino.id, archivo = crudo.absolutePath, mime = tipo)
        }
        val resultado = ImageCompressor.compressImage(
            context = context,
            uri = Uri.parse(destino.uriParaLaCamara),
            outputFileName = "$PREFIJO_COMPROBANTE${destino.id}.jpg"
        )
        // El crudo ya no hace falta: lo que sube es el comprimido.
        crudo.delete()
        return ComprobanteDelAbono(
            id = destino.id,
            archivo = resultado.outputFile.absolutePath,
            // ImageCompressor SIEMPRE escribe JPEG (`Bitmap.CompressFormat.JPEG`),
            // sea cual sea el formato de entrada. El tipo del archivo que se
            // sube es el del archivo que se sube, no el del que entró.
            mime = "image/jpeg"
        )
    }

    override suspend fun descartar(archivo: String) {
        File(archivo).delete()
    }

    private fun primerosBytesDe(archivo: File): ByteArray {
        if (!archivo.exists()) return ByteArray(0)
        return archivo.inputStream().use { entrada ->
            val buffer = ByteArray(Comprobantes.BYTES_DE_FIRMA)
            val leidos = entrada.read(buffer)
            if (leidos <= 0) ByteArray(0) else buffer.copyOf(leidos)
        }
    }

    /**
     * **Quién limpia** (el KDoc de `PaymentImageEntity` le encarga esto a esta
     * tarea): borra los comprobantes que ninguna fila referencia y que ya son
     * viejos.
     *
     * Barre **dos** cosas, y ninguna es la misma:
     *
     * 1. **Filas cuyo pago ya no existe.** Pasa cuando la subida se reconcilió
     *    por otra vía y `CobranzaSyncManager` re-llaveó el pago: la foto se
     *    queda sin padre y sin nadie que la mande.
     * 2. **Archivos que ninguna fila referencia.** Una foto se captura ANTES de
     *    que el abono se registre, así que un cobrador que se arrepiente y sale
     *    de la pantalla deja el archivo sin fila.
     *
     * Las dos por ANTIGÜEDAD, nunca al instante: un archivo reciente sin fila
     * puede ser el de la captura que está ocurriendo ahora mismo, y un pago sin
     * fila puede estar a media re-llaveada.
     *
     * Es best-effort y corre al preparar la cámara, nunca en el tick del merge
     * de sincronización. Su fallo no puede impedir tomar una foto.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // leer un directorio o la base puede fallar; ninguna de las dos toca la captura.
    private suspend fun barrerHuerfanos() {
        try {
            val corte = clock.now().minus(VEJEZ)
            // 1. Las filas cuyo pago ya no existe: se van con su archivo. Es lo
            //    que el KDoc de `PaymentImageEntity` le encarga a esta tarea, y
            //    corre por ANTIGÜEDAD para no confundir el re-llaveado del sync
            //    (rutina, instantáneo) con un pago que se fue de verdad.
            imagenes.huerfanasAnterioresA(AppTime.toWireFormat(corte)).forEach { fila ->
                File(fila.URI).delete()
                imagenes.eliminar(fila.ID)
            }
            // 2. Los archivos que ninguna fila referencia — la foto que se
            //    capturó y cuyo abono nunca llegó a registrarse.
            val vivas = imagenes.rutasVivas().toSet()
            comprobantesHuerfanos(context.filesDir, vivas, corte.toEpochMilli())
                .forEach { it.delete() }
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Throwable) {
            telemetry.error(
                code = PagosTelemetria.CODE_ABONO_FOTO_FALLO,
                message = "no se pudo barrer los comprobantes huerfanos; la captura sigue",
                props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
            )
        }
    }

    private companion object {

        /** Prefijo del crudo de la cámara, en `cacheDir`. */
        const val PREFIJO_CRUDO = PREFIJO_COMPROBANTE + "crudo_"

        /**
         * Cuánto espera un archivo sin fila antes de que se le dé por
         * abandonado. Una semana: más que cualquier captura en curso, y menos
         * que el tiempo en que un teléfono sin señal llena su disco de fotos.
         */
        val VEJEZ: Duration = Duration.ofDays(7)
    }
}

/**
 * Los comprobantes comprimidos de [directorio] que **ninguna fila referencia**
 * y que son más viejos que [limiteMillis].
 *
 * Función suelta y sin Android a propósito: es la única parte del barrido con
 * lógica de verdad, y así se prueba con directorios temporales, sin Robolectric
 * y sin cámara. Filtra por el prefijo del comprobante para no tocar nada más de
 * `filesDir` — ahí viven también los borradores de venta.
 */
internal fun comprobantesHuerfanos(
    directorio: File,
    rutasVivas: Set<String>,
    limiteMillis: Long
): List<File> {
    val archivos = directorio.listFiles() ?: return emptyList()
    return archivos.filter { archivo ->
        archivo.isFile &&
            archivo.name.startsWith(PREFIJO_COMPROBANTE) &&
            archivo.absolutePath !in rutasVivas &&
            archivo.lastModified() < limiteMillis
    }
}
