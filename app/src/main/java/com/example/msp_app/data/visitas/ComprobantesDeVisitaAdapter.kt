package com.example.msp_app.data.visitas

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.content.FileProvider
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.visit.VisitImageDao
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.utils.ImageCompressor
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import com.example.msp_app.feature.visitas.domain.ComprobantesDeVisita
import com.example.msp_app.feature.visitas.domain.model.ComprobanteDeVisita
import com.example.msp_app.feature.visitas.domain.model.DestinoDeFoto
import com.example.msp_app.feature.visitas.domain.port.ComprobantesDeVisitaPort
import java.io.File
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.CancellationException

/**
 * Prefijo de todo archivo de comprobante de VISITA que esta app escribe.
 *
 * Distinto del de pagos (`comprobante_pago_`) a propósito, y no por prolijidad:
 * los dos barridos miran el MISMO `filesDir`, y cada uno consulta las rutas
 * vivas de **su** tabla. Con un prefijo compartido, el barrido de pagos vería
 * los archivos de visitas como "archivos que ninguna fila referencia" y los
 * borraría — la foto de una visita que todavía no sube, desaparecida por el
 * barrido del otro módulo.
 */
internal const val PREFIJO_COMPROBANTE_VISITA = "comprobante_visita_"

/**
 * Implementación real de [ComprobantesDeVisitaPort], provista desde el
 * composition root de `:app` (mismo reparto que [RegistroDeVisitaAdapter] y
 * [UbicacionDeVisitaAdapter]): lo que hace falta —`FileProvider`,
 * `ContentResolver`, [ImageCompressor]— vive aquí y no es alcanzable desde
 * `:feature:visitas`.
 *
 * ## Dos archivos por foto, en dos lugares distintos, y no es capricho
 *
 * 1. **El crudo**, en `cacheDir`. Es el único que la cámara puede escribir: el
 *    `FileProvider` de esta app expone `cache-path "."` y de `filesDir` solo
 *    `draft_images/` (`res/xml/file_paths.xml`), así que un destino en
 *    `filesDir` daría `IllegalArgumentException` al pedir su `content://`. Vive
 *    lo que dura la captura.
 * 2. **El comprimido**, en `filesDir`, con el prefijo
 *    [PREFIJO_COMPROBANTE_VISITA]. Ahí es donde [ImageCompressor] escribe, y el
 *    que espera la subida — `cacheDir` lo puede vaciar el sistema cuando el
 *    disco aprieta, que en el teléfono de gama baja del cobrador **pasa**, y se
 *    llevaría el comprobante de una visita que todavía no sube.
 *
 * ## El tipo se decide por los BYTES, no por la extensión
 *
 * El nombre del destino lo pone esta app, así que preguntarle a la extensión qué
 * escribió la cámara es preguntarnos a nosotros mismos. Se leen los primeros
 * bytes y decide [ComprobantesDeVisita.tipoDe]. Un archivo vacío —cámara que
 * contesta OK sin escribir— cae en `application/octet-stream`, que no está
 * permitido, y el ViewModel lo rechaza con su aviso. Y eso pesa: un MIME fuera
 * de la whitelist hace que el servidor conteste 422 a la **visita entera**, no
 * solo a la foto.
 *
 * (Corrección: una versión anterior de este comentario decía que en pagos el
 * rechazo era solo de la imagen. **Es falso** — en cobranza la whitelist vive en
 * el tag `contentType` de `CrearPagoMultipartFields.Imagen`, que Huma valida
 * ANTES del handler y devuelve 422 del request completo. Las dos rutas tumban su
 * escritura entera; lo que cambia es dónde se valida, no la consecuencia.)
 */
class ComprobantesDeVisitaAdapter(
    private val context: Context,
    private val imagenes: VisitImageDao,
    private val telemetry: Telemetry,
    private val clock: AppClock = AppClock.System
) : ComprobantesDeVisitaPort {

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
     * Comprime lo que la cámara dejó y devuelve el comprobante **con el mismo id
     * del destino** — acuñar otro aquí dejaría el reintento sin clave estable, y
     * en visitas el `id_<n>` no es una mejora sino un campo requerido del
     * contrato.
     *
     * Un tipo que no reconocemos NO se comprime: se devuelve tal cual, con su
     * tipo, para que el ViewModel lo rechace y borre. Comprimirlo primero
     * produciría un JPEG válido a partir de basura y escondería el problema.
     */
    override suspend fun aceptar(destino: DestinoDeFoto): ComprobanteDeVisita {
        val crudo = File(destino.archivoCrudo)
        val tipo = ComprobantesDeVisita.tipoDe(primerosBytesDe(crudo))
        if (!ComprobantesDeVisita.permitido(tipo)) {
            return ComprobanteDeVisita(id = destino.id, archivo = crudo.absolutePath, mime = tipo)
        }
        val resultado = ImageCompressor.compressImage(
            context = context,
            uri = Uri.parse(destino.uriParaLaCamara),
            outputFileName = "$PREFIJO_COMPROBANTE_VISITA${destino.id}.jpg"
        )
        // El crudo ya no hace falta: lo que sube es el comprimido.
        crudo.delete()
        return ComprobanteDeVisita(
            id = destino.id,
            archivo = resultado.outputFile.absolutePath,
            // ImageCompressor SIEMPRE escribe JPEG (`Bitmap.CompressFormat.JPEG`),
            // sea cual sea el formato de entrada. El tipo del archivo que se sube
            // es el del archivo que se sube, no el del que entró.
            mime = "image/jpeg"
        )
    }

    override suspend fun descartar(archivo: String) {
        File(archivo).delete()
    }

    private fun primerosBytesDe(archivo: File): ByteArray {
        if (!archivo.exists()) return ByteArray(0)
        return archivo.inputStream().use { entrada ->
            val buffer = ByteArray(ComprobantesDeVisita.BYTES_DE_FIRMA)
            val leidos = entrada.read(buffer)
            if (leidos <= 0) ByteArray(0) else buffer.copyOf(leidos)
        }
    }

    /**
     * **Quién limpia** (el KDoc de `VisitImageEntity` le encarga esto a esta
     * tarea): borra los comprobantes que ninguna fila referencia y que ya son
     * viejos.
     *
     * Barre **dos** cosas, y ninguna es la misma:
     *
     * 1. **Filas cuya visita ya no existe.** `visita_imagenes` no tiene llave
     *    foránea a propósito, así que una visita podada deja a sus comprobantes
     *    sin padre y sin nadie que los mande.
     * 2. **Archivos que ninguna fila referencia.** La foto se captura ANTES de
     *    que la visita se registre, así que un cobrador que se arrepiente y sale
     *    de la pantalla deja el archivo sin fila.
     *
     * ## Huérfana es la que no tiene padre; pendiente NO es huérfana
     *
     * La consulta exige `VISITA_ID NOT IN (SELECT ID FROM Visit)`, así que una
     * imagen pendiente **cuya visita sigue existiendo** no se toca nunca: esa no
     * es basura, es evidencia esperando señal.
     *
     * Lo que sí puede caer es la pendiente **sin padre** — su visita ya se podó
     * y nadie la va a subir jamás. Ahí borrar es correcto (si no, el disco del
     * teléfono crece sin techo), pero **borrarla en silencio no**: eso convierte
     * el barrido en el desagüe mudo de toda evidencia no entregada, incluida la
     * que las mitigaciones de esta misma tarea decían dejar "visible durante la
     * ventana". Por eso se cuenta y se reporta
     * ([VisitasTelemetria.CODE_VISITA_FOTO_BARRIDA_SIN_SUBIR]).
     *
     * Las dos por ANTIGÜEDAD, nunca al instante: un archivo reciente sin fila
     * puede ser el de la captura que está ocurriendo ahora mismo, y una fila sin
     * visita puede estar a medio reinsertar (`VisitDao.insertVisit` es
     * `INSERT OR REPLACE`, que **borra y reinserta**).
     *
     * [VEJEZ] es **la misma ventana** que `VisitsLocalDataSource
     * .RETENCION_DE_COMPROBANTES` usa para dejar de bloquear la poda. Tienen que
     * coincidir: mientras la fila bloquea la poda, el barrido no la toca; en
     * cuanto deja de bloquear, el barrido ya la puede recoger. Con dos números
     * distintos habría un hueco.
     *
     * Es best-effort y corre al preparar la cámara, nunca en el tick del merge de
     * sincronización. Su fallo no puede impedir tomar una foto.
     */
    @VisibleForTesting
    @Suppress(
        "TooGenericExceptionCaught"
    ) // leer un directorio o la base puede fallar; ninguna de las dos toca la captura.
    internal suspend fun barrerHuerfanos() {
        try {
            val corte = clock.now().minus(VEJEZ)
            val huerfanas = imagenes.huerfanasAnterioresA(AppTime.toWireFormat(corte))
            huerfanas.forEach { fila ->
                File(fila.URI).delete()
                imagenes.eliminar(fila.ID)
            }
            reportarLoQueNuncaSubio(huerfanas.count { it.SUBIDA_EN == null })
            val vivas = imagenes.rutasVivas().toSet()
            comprobantesDeVisitaHuerfanos(context.filesDir, vivas, corte.toEpochMilli())
                .forEach { it.delete() }
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Throwable) {
            telemetry.error(
                code = VisitasTelemetria.CODE_VISITA_FOTO_FALLO,
                message = "no se pudo barrer los comprobantes huerfanos; la captura sigue",
                props = mapOf(VisitasTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
            )
        }
    }

    /**
     * Dice **cuántas** fotos se barrieron sin haber subido nunca. Solo el
     * conteo: ni ids, ni rutas, ni nada del cliente (anti-PII).
     *
     * Cero no se reporta — un barrido que no perdió nada no es un evento.
     */
    private fun reportarLoQueNuncaSubio(cuantas: Int) {
        if (cuantas == 0) return
        telemetry.error(
            code = VisitasTelemetria.CODE_VISITA_FOTO_BARRIDA_SIN_SUBIR,
            message = "se barrieron comprobantes de visitas podadas que nunca llegaron a subir",
            props = mapOf(VisitasTelemetria.PROP_OCURRENCIAS to cuantas.toString())
        )
    }

    private companion object {

        /** Prefijo del crudo de la cámara, en `cacheDir`. */
        const val PREFIJO_CRUDO = PREFIJO_COMPROBANTE_VISITA + "crudo_"

        /**
         * Cuánto espera un archivo sin fila antes de que se le dé por
         * abandonado. Una semana: más que cualquier captura en curso, y menos
         * que el tiempo en que un teléfono sin señal llena su disco de fotos.
         */
        val VEJEZ: Duration = Duration.ofDays(7)
    }
}

/**
 * Los comprobantes de visita de [directorio] que **ninguna fila referencia** y
 * que son más viejos que [limiteMillis].
 *
 * Función suelta y sin Android a propósito: es la única parte del barrido con
 * lógica de verdad, y así se prueba con directorios temporales, sin Robolectric
 * y sin cámara. Filtra por el prefijo del comprobante de VISITA para no tocar
 * nada más de `filesDir` — ahí viven también los borradores de venta y los
 * comprobantes de pago, que tienen su propio barrido y su propia tabla.
 */
internal fun comprobantesDeVisitaHuerfanos(
    directorio: File,
    rutasVivas: Set<String>,
    limiteMillis: Long
): List<File> {
    val archivos = directorio.listFiles() ?: return emptyList()
    return archivos.filter { archivo ->
        archivo.isFile &&
            archivo.name.startsWith(PREFIJO_COMPROBANTE_VISITA) &&
            archivo.absolutePath !in rutasVivas &&
            archivo.lastModified() < limiteMillis
    }
}
