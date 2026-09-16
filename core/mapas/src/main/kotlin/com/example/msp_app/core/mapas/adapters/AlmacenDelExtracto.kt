package com.example.msp_app.core.mapas.adapters

import com.example.msp_app.core.mapas.application.MapasTelemetria
import com.example.msp_app.core.mapas.domain.AvanceDeLaDescarga
import com.example.msp_app.core.mapas.domain.CabeceraInvalida
import com.example.msp_app.core.mapas.domain.ExtractoDeMapa
import com.example.msp_app.core.mapas.domain.LARGO_DE_LA_CABECERA
import com.example.msp_app.core.mapas.domain.MapaDeLaRuta
import com.example.msp_app.core.mapas.domain.leerCabeceraDePmtiles
import com.example.msp_app.core.telemetry.Telemetry
import java.io.File
import java.io.IOException

/**
 * **Dónde vive el `.pmtiles` y cómo se sabe que sirve.**
 *
 * Dos archivos, no uno, con el mismo criterio que `AlmacenDelModelo` de
 * `:core:speech`:
 *
 * - `ruta.parcial` — lo que se está bajando. Reanudable, y **nunca** se le pasa
 *   a MapLibre.
 * - `ruta.pmtiles` — el que ya pasó la verificación. Existir es estar listo.
 *
 * Con un solo archivo, un `.pmtiles` a medias sería indistinguible de uno
 * completo. MapLibre lo abriría, leería la cabecera —que sí llegó, son los
 * primeros 127 bytes— y pintaría **el pedazo del mapa que alcanzó a bajar**:
 * calles que se cortan a la mitad de la colonia, con la misma pinta de mapa de
 * verdad. Eso es un dato falso, que es peor que no tener mapa.
 *
 * ## A diferencia del modelo de voz, acá SÍ se verifica en cada lectura
 *
 * `AlmacenDelModelo.rutaDelModeloListo()` no revalida porque eso costaría leer
 * 43 MB para un SHA-256 antes de cada nota. Acá la verificación son **127 bytes
 * y un `length()`**, así que se paga completa cada vez y "el archivo existe"
 * nunca tiene que significar "el archivo sirve" por fe.
 */
class AlmacenDelExtracto(
    private val carpeta: File,
    private val telemetry: Telemetry
) {

    /** El archivo verificado. Solo existe si pasó la cabecera y la completitud. */
    private val listo: File get() = File(carpeta, "ruta.pmtiles")

    /** Lo que se está bajando. Puede existir a medias, y eso es correcto. */
    val parcial: File get() = File(carpeta, "ruta.parcial")

    /**
     * El mapa utilizable, o `null`. Es la pregunta que hace el puerto.
     *
     * Un archivo que está pero no verifica devuelve `null` **y emite**: es
     * exactamente el caso que no se puede dejar en silencio, porque desde
     * afuera se vería igual que "todavía no lo bajaron".
     */
    fun mapaListo(): MapaDeLaRuta? {
        if (!listo.isFile) return null
        return verificar(listo).getOrElse { fallo ->
            reportarCabecera(fallo, "el extracto que estaba en disco no verifica")
            null
        }
    }

    /** Cuántos bytes lleva bajados el parcial. Cero si no hay nada. */
    fun bytesBajados(): Long = if (parcial.isFile) parcial.length() else 0L

    /** El avance para pintar, sin inventar el total: lo pone [extracto]. */
    fun avance(extracto: ExtractoDeMapa): AvanceDeLaDescarga =
        AvanceDeLaDescarga(bytesBajados(), extracto.tamanoBytes)

    /**
     * Comprueba el parcial y lo promueve a definitivo.
     *
     * @return `true` si quedó listo. `false` borra el parcial: un archivo que no
     *   verifica no se arregla reanudando.
     */
    fun verificarYPromover(): Boolean {
        val mapa = verificar(parcial).getOrElse { fallo ->
            reportarCabecera(fallo, "el extracto bajado no verifica; se descarta")
            parcial.delete()
            return false
        }
        check(mapa.ruta == parcial.absolutePath) { "se verifico un archivo distinto del parcial" }
        listo.delete()
        // `renameTo` dentro de la misma carpeta es atómico en cualquier sistema
        // de archivos de Android: no existe un instante en el que `ruta.pmtiles`
        // esté a medias.
        if (!parcial.renameTo(listo)) {
            telemetry.error(
                code = MapasTelemetria.CODE_EXTRACTO_INVALIDO,
                message = "el extracto verificado no se pudo renombrar a definitivo",
                props = emptyMap()
            )
            return false
        }
        return true
    }

    /** Borra las dos mitades. Lo pide el cobrador para recuperar los megas. */
    fun borrarTodo() {
        val borrados = listOf(listo, parcial).filter { it.exists() }.map { it.delete() }
        if (borrados.any { !it }) {
            telemetry.error(
                code = MapasTelemetria.CODE_EXTRACTO_NO_SE_BORRO,
                message = "el extracto no se pudo borrar; los megas siguen ocupados",
                props = emptyMap()
            )
        }
    }

    /** Crea la carpeta si falta. Idempotente. */
    fun prepararCarpeta() {
        if (!carpeta.isDirectory && !carpeta.mkdirs()) {
            telemetry.error(
                code = MapasTelemetria.CODE_CARPETA_NO_SE_CREO,
                message = "no se pudo crear la carpeta del extracto de mapa",
                props = emptyMap()
            )
        }
    }

    /**
     * Lee la cabecera y **exige que el archivo esté completo**.
     *
     * `finDeLosDatos` es el último byte que el archivo dice tener. Si el archivo
     * real es más corto, la descarga se cortó; si es más largo, no es el archivo
     * que la cabecera describe. Las dos son motivo de rechazo, y las dos las
     * detecta esta línea — sin leer 25 MB.
     */
    private fun verificar(archivo: File): Result<MapaDeLaRuta> {
        val bytes = runCatching { leerLaCabecera(archivo) }.getOrElse { error ->
            return Result.failure(error)
        }
        val cabecera = leerCabeceraDePmtiles(bytes).getOrElse { return Result.failure(it) }
        if (cabecera.finDeLosDatos != archivo.length()) {
            return Result.failure(
                ExtractoIncompleto(esperado = cabecera.finDeLosDatos, real = archivo.length())
            )
        }
        return Result.success(MapaDeLaRuta(archivo.absolutePath, cabecera))
    }

    private fun leerLaCabecera(archivo: File): ByteArray {
        if (!archivo.isFile) throw IOException("no hay archivo que verificar")
        val buffer = ByteArray(LARGO_DE_LA_CABECERA)
        archivo.inputStream().use { entrada ->
            var leidos = 0
            while (leidos < LARGO_DE_LA_CABECERA) {
                val n = entrada.read(buffer, leidos, LARGO_DE_LA_CABECERA - leidos)
                if (n < 0) break
                leidos += n
            }
            if (leidos < LARGO_DE_LA_CABECERA) throw IOException("cabecera truncada")
        }
        return buffer
    }

    /**
     * Un solo camino de reporte para los tres fallos, con el motivo tipado y
     * **sin la ruta del archivo**: ésa lleva el `filesDir` del dispositivo.
     */
    private fun reportarCabecera(fallo: Throwable, mensaje: String) {
        val motivo = when (fallo) {
            is CabeceraInvalida -> fallo.motivo.name
            is ExtractoIncompleto -> "INCOMPLETO"
            else -> "NO_SE_PUDO_LEER"
        }
        telemetry.error(
            code = MapasTelemetria.CODE_EXTRACTO_INVALIDO,
            message = mensaje,
            props = mapOf(
                MapasTelemetria.PROP_MOTIVO to motivo,
                MapasTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName
            )
        )
    }
}

/**
 * El archivo dice medir una cosa y mide otra. Los dos números son largos de
 * archivo, no datos del cliente, así que **no** se pasan a telemetría de todas
 * formas: no aportan nada que el motivo `INCOMPLETO` no diga ya.
 */
class ExtractoIncompleto(val esperado: Long, val real: Long) :
    IOException("el extracto dice terminar en $esperado y el archivo mide $real")
