package com.example.msp_app.feature.compartirproductos.android

import com.example.msp_app.core.common.time.AppClock
import java.io.File
import java.time.Duration

/**
 * La carpeta `cacheDir/compartir/` donde viven las tarjetas JPEG mientras WhatsApp las lee.
 *
 *  - Al iniciar un envío se borran los de envíos anteriores ([vaciar]).
 *  - Al arrancar la app se borran los de más de un día ([barrerViejos]).
 *
 * Solo toca archivos `tarjeta_*.jpg` de esa carpeta: nunca sale de ella ni borra lo ajeno.
 */
class AlmacenDeTemporales(private val raiz: File, private val reloj: AppClock = AppClock.System) {

    val carpeta: File get() = File(raiz, NOMBRE_CARPETA)

    /** La carpeta, creada si hace falta. Lanza si no se puede crear (disco lleno / sin permiso). */
    fun asegurarCarpeta(): File = carpeta.also {
        check(it.isDirectory || it.mkdirs()) { "no se pudo crear ${it.path}" }
    }

    fun archivoPara(articuloId: Int, indice: Int): File = File(
        asegurarCarpeta(),
        "$PREFIJO${articuloId}_$indice$EXTENSION"
    )

    /** Borra las tarjetas de envíos anteriores. Devuelve cuántas borró. */
    fun vaciar(): Int = tarjetas().count { it.delete() }

    /** Borra las tarjetas con más de [maximaEdad] (por defecto un día). Devuelve cuántas borró. */
    fun barrerViejos(maximaEdad: Duration = Duration.ofDays(1)): Int {
        val corte = reloj.now().toEpochMilli() - maximaEdad.toMillis()
        return tarjetas().filter { it.lastModified() < corte }.count { it.delete() }
    }

    private fun tarjetas(): List<File> =
        carpeta.listFiles { f -> f.isFile && f.name.startsWith(PREFIJO) && f.name.endsWith(EXTENSION) }?.toList().orEmpty()

    companion object {
        const val NOMBRE_CARPETA = "compartir"
        const val PREFIJO = "tarjeta_"
        const val EXTENSION = ".jpg"
    }
}
