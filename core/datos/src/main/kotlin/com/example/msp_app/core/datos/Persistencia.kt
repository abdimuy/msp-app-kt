package com.example.msp_app.core.datos

import com.google.gson.Gson
import java.io.File

/** Lo que se guarda: agregados por día, la última lectura de `TrafficStats` y los días ya reportados. */
data class EstadoPersistido(
    val dias: List<UsoDelDia>,
    val ultimaMuestra: MuestraDeTrafico?,
    val reportados: List<String>
)

interface AlmacenDeUsoDeDatos {
    fun leer(): EstadoPersistido?
    fun escribir(estado: EstadoPersistido)
}

/**
 * Archivo JSON propio (sin Room: el esquema está en v39 y no se toca). Escritura
 * atómica: temporal + renombrado, así un corte a media escritura deja el archivo
 * anterior intacto. Un archivo corrupto se ignora (se empieza de cero).
 */
class AlmacenEnArchivo(private val archivo: File) : AlmacenDeUsoDeDatos {
    private val gson = Gson()

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun leer(): EstadoPersistido? = try {
        if (!archivo.exists()) {
            null
        } else {
            gson.fromJson(archivo.readText(), EstadoPersistido::class.java)?.sanear()
        }
    } catch (_: Exception) {
        null
    }

    override fun escribir(estado: EstadoPersistido) {
        val temporal = File(archivo.parentFile, archivo.name + ".tmp")
        temporal.writeText(gson.toJson(estado))
        if (!temporal.renameTo(archivo)) {
            archivo.delete()
            check(temporal.renameTo(archivo)) { "no se pudo reemplazar ${archivo.name}" }
        }
    }

    // Gson crea objetos sin pasar por el constructor: listas/mapas ausentes llegan null.
    @Suppress("USELESS_ELVIS", "UNNECESSARY_SAFE_CALL", "SENSELESS_COMPARISON")
    private fun EstadoPersistido.sanear(): EstadoPersistido = EstadoPersistido(
        // Un día sin fecha (null en el JSON) no se puede ubicar: se descarta, los demás se conservan.
        dias = (dias ?: emptyList()).filter {
            it.fecha != null
        }.map { it.copy(endpoints = it.endpoints ?: emptyMap()) },
        ultimaMuestra = ultimaMuestra,
        reportados = reportados ?: emptyList()
    )
}
