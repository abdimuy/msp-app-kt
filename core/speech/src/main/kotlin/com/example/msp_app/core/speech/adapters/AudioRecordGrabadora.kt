package com.example.msp_app.core.speech.adapters

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import com.example.msp_app.core.speech.domain.GrabacionDictada
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * **El micrófono real: PCM 16 kHz mono, a un WAV, en un hilo propio.**
 *
 * ## Lo que NO cubre ningún test de este repo
 *
 * `AudioRecord` bajo Robolectric es un no-op: no hay micrófono, no hay bytes y
 * el hilo no lee nada. O sea que **la captura en sí solo se comprueba en un
 * teléfono**. Lo que sí está probado es todo lo que la rodea, porque vive
 * detrás de [GrabadoraDeAudio] y los adaptadores se prueban con un fake.
 *
 * ## WAV y no M4A
 *
 * Porque whisper.cpp lee PCM y nada más. Un M4A pesaría menos y obligaría a
 * decodificar antes de transcribir — con el `tiny` en gama media, ese paso
 * extra se nota. Ocho segundos de PCM 16 kHz mono son ~256 KB, que al lado de
 * una foto de la visita no es nada.
 *
 * ## El pipe es lo que hace que haya UN micrófono
 *
 * [fuenteCompartida] entrega el extremo de lectura de un `ParcelFileDescriptor`
 * al que este hilo escribe los MISMOS bytes que van al archivo. Es lo que
 * permite que el reconocedor de Android escuche sin abrir el micrófono por su
 * cuenta, y por lo tanto que la grabación exista también en ese modo.
 */
class AudioRecordGrabadora(
    private val carpeta: File,
    private val dispatcher: CoroutineDispatcher
) : GrabadoraDeAudio {

    private var captura: AudioRecord? = null
    private var destino: File? = null
    private var id: String? = null
    private var tuberia: Array<ParcelFileDescriptor>? = null
    private val grabando = AtomicBoolean(false)
    private val muestrasEscritas = AtomicInteger(0)

    /** Último pico absoluto normalizado, `0..1000`, para no usar flotantes entre hilos. */
    private val pico = AtomicInteger(0)

    @SuppressLint("MissingPermission") // el llamador ya consultó `PermisoDeMicrofono`.
    override suspend fun comenzar(): Result<Unit> = withContext(dispatcher) {
        runCatching {
            if (!carpeta.isDirectory && !carpeta.mkdirs()) {
                throw IOException("no se pudo crear la carpeta del audio")
            }
            val tamano = AudioRecord.getMinBufferSize(MUESTREO_HZ, CANAL, CODIFICACION)
            check(tamano > 0) { "el microfono no reporta buffer" }
            val nuevoId = UUID.randomUUID().toString()
            val archivo = File(carpeta, "dictado_$nuevoId.wav")
            val nueva = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MUESTREO_HZ,
                CANAL,
                CODIFICACION,
                tamano * HOLGURA_DEL_BUFFER
            )
            check(nueva.state == AudioRecord.STATE_INITIALIZED) { "el microfono no inicializo" }
            val pipe = ParcelFileDescriptor.createPipe()
            captura = nueva
            destino = archivo
            id = nuevoId
            tuberia = pipe
            muestrasEscritas.set(0)
            grabando.set(true)
            nueva.startRecording()
            bombear(nueva, archivo, pipe[1], tamano)
        }
    }

    override suspend fun terminar(): Result<GrabacionDictada> = cerrar()

    override suspend fun cancelar(): Result<GrabacionDictada> = cerrar()

    override fun nivel(): Float = pico.get() / PICO_MAXIMO

    override fun fuenteCompartida(): ParcelFileDescriptor? = tuberia?.get(0)

    /**
     * Cierra la captura y entrega el archivo. Es **el mismo camino** para
     * terminar y para cancelar: la grabación se conserva en los dos casos, y
     * escribirlo como una sola función es la forma de que no se despeguen.
     */
    private suspend fun cerrar(): Result<GrabacionDictada> = withContext(dispatcher) {
        runCatching {
            grabando.set(false)
            captura?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop()
                release()
            }
            captura = null
            val archivo = requireNotNull(destino) { "no habia grabacion abierta" }
            val elId = requireNotNull(id) { "no habia grabacion abierta" }
            tuberia?.forEach { runCatching { it.close() } }
            tuberia = null
            destino = null
            id = null
            escribirCabeceraWav(archivo, muestrasEscritas.get())
            if (!archivo.isFile || archivo.length() <= CABECERA_WAV) {
                throw IOException("la grabacion quedo vacia")
            }
            GrabacionDictada(
                id = elId,
                archivo = archivo.absolutePath,
                duracionMs = muestrasEscritas.get() * MILIS / MUESTREO_HZ
            )
        }
    }

    /**
     * El hilo que mueve los bytes. Escribe al archivo **y** al pipe, y si el
     * pipe se rompe (el reconocedor lo cerró) sigue escribiendo al archivo: la
     * grabación no depende de que el reconocedor siga vivo.
     */
    private fun bombear(
        captura: AudioRecord,
        archivo: File,
        haciaElReconocedor: ParcelFileDescriptor,
        tamano: Int
    ) {
        thread(name = "dictado-audio") {
            FileOutputStream(archivo).use { salida ->
                salida.write(ByteArray(CABECERA_WAV.toInt())) // hueco, se rellena al cerrar
                val alReconocedor = ParcelFileDescriptor.AutoCloseOutputStream(haciaElReconocedor)
                val buffer = ByteArray(tamano)
                while (grabando.get()) {
                    val leidos = captura.read(buffer, 0, buffer.size)
                    if (leidos <= 0) continue
                    salida.write(buffer, 0, leidos)
                    muestrasEscritas.addAndGet(leidos / BYTES_POR_MUESTRA)
                    pico.set(picoDe(buffer, leidos))
                    // Un pipe roto NO puede costar la grabación.
                    runCatching { alReconocedor.write(buffer, 0, leidos) }
                }
                runCatching { alReconocedor.close() }
            }
        }
    }

    private fun picoDe(buffer: ByteArray, leidos: Int): Int {
        var mayor = 0
        var i = 0
        while (i + 1 < leidos) {
            val muestra = (buffer[i + 1].toInt() shl BITS_ALTOS) or (buffer[i].toInt() and BAJO)
            mayor = maxOf(mayor, abs(muestra))
            i += BYTES_POR_MUESTRA * SALTO
        }
        return (mayor * PICO_MAXIMO / RANGO_PCM16).toInt().coerceIn(0, PICO_MAXIMO.toInt())
    }

    /**
     * La cabecera WAV de 44 bytes, escrita **al final**, cuando ya se sabe
     * cuántas muestras hay. Escribirla al principio con un tamaño inventado
     * produce un archivo que algunos reproductores cortan donde dice la
     * cabecera y no donde terminan los datos.
     */
    private fun escribirCabeceraWav(archivo: File, muestras: Int) {
        val datos = muestras * BYTES_POR_MUESTRA
        val cabecera = java.nio.ByteBuffer
            .allocate(CABECERA_WAV.toInt())
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        cabecera.put("RIFF".toByteArray())
        cabecera.putInt(datos + CABECERA_WAV.toInt() - RIFF_YA_CONTADO)
        cabecera.put("WAVE".toByteArray())
        cabecera.put("fmt ".toByteArray())
        cabecera.putInt(TAMANO_FMT_PCM)
        cabecera.putShort(FORMATO_PCM)
        cabecera.putShort(1) // un canal
        cabecera.putInt(MUESTREO_HZ)
        cabecera.putInt(MUESTREO_HZ * BYTES_POR_MUESTRA) // bytes por segundo
        cabecera.putShort(BYTES_POR_MUESTRA.toShort())
        cabecera.putShort(BITS_POR_MUESTRA)
        cabecera.put("data".toByteArray())
        cabecera.putInt(datos)
        java.io.RandomAccessFile(archivo, "rw").use { acceso ->
            acceso.seek(0)
            acceso.write(cabecera.array())
        }
    }

    @Suppress("MagicNumber") // formato WAV y aritmética de PCM, no cifras de negocio.
    private companion object {
        const val MUESTREO_HZ = 16_000
        const val CANAL = AudioFormat.CHANNEL_IN_MONO
        const val CODIFICACION = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_POR_MUESTRA = 2
        const val BITS_POR_MUESTRA: Short = 16
        const val CABECERA_WAV = 44L
        const val RIFF_YA_CONTADO = 8
        const val TAMANO_FMT_PCM = 16
        const val FORMATO_PCM: Short = 1
        const val HOLGURA_DEL_BUFFER = 2
        const val MILIS = 1_000L
        const val PICO_MAXIMO = 1_000f
        const val RANGO_PCM16 = 32_768f
        const val BITS_ALTOS = 8
        const val BAJO = 0xFF

        /** Se mira una muestra de cada cuatro: el pico no necesita más y así el hilo no se frena. */
        const val SALTO = 4
    }
}
