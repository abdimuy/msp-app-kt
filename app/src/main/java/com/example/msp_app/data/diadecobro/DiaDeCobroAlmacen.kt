package com.example.msp_app.data.diadecobro

import com.example.msp_app.data.api.services.diadecobro.DiaDeCobroResponse
import com.google.gson.Gson
import java.io.File
import java.time.Instant

/** Lo guardado: la respuesta tal cual, con la zona y el momento de la descarga. */
data class DiaDeCobroGuardado(
    val zona: Int,
    val descargadoEn: Instant,
    val respuesta: DiaDeCobroResponse
)

/** El sobre en disco. Todo nullable por lo mismo que los DTO: Gson ignora los defaults. */
private data class SobreEnDisco(
    val zona: Int? = null,
    val descargado_en: String? = null,
    val respuesta: DiaDeCobroResponse? = null
)

/**
 * Dónde sobrevive el día de cobro sin señal: **un archivo JSON en `filesDir`**.
 *
 * Por qué un archivo y no una tabla Room: (1) el esquema de Room es compartido
 * con las demás ramas y cada versión nueva exige migración inmutable —un archivo
 * no la necesita ni puede romperla—; (2) queda físicamente fuera de las tablas
 * de ventas y pagos y de su sync, que es la garantía que pide el diseño; (3) es
 * un dato derivado y desechable: borrarlo solo cuesta una descarga.
 *
 * **Escritura atómica** (temporal + renombrar): un corte a media escritura deja
 * el archivo anterior intacto, nunca uno a medias. Un archivo ilegible se lee
 * como "nada guardado".
 */
class DiaDeCobroAlmacen(private val archivo: File, private val gson: Gson = Gson()) {

    @Suppress("TooGenericExceptionCaught") // cualquier archivo ilegible = nada guardado
    fun leer(): DiaDeCobroGuardado? = try {
        if (!archivo.exists()) {
            null
        } else {
            val sobre = gson.fromJson(archivo.readText(Charsets.UTF_8), SobreEnDisco::class.java)
            val zona = sobre?.zona
            val cuando = sobre?.descargado_en?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val respuesta = sobre?.respuesta
            if (zona == null || cuando == null || respuesta == null) {
                null
            } else {
                DiaDeCobroGuardado(zona, cuando, respuesta)
            }
        }
    } catch (e: Exception) {
        null
    }

    /** Reemplaza lo guardado. Lanza si no puede escribir (el llamador lo atrapa). */
    fun guardar(guardado: DiaDeCobroGuardado) {
        val sobre = SobreEnDisco(
            zona = guardado.zona,
            descargado_en = guardado.descargadoEn.toString(),
            respuesta = guardado.respuesta
        )
        val temporal = File(archivo.parentFile, archivo.name + ".tmp")
        temporal.writeText(gson.toJson(sobre), Charsets.UTF_8)
        if (!temporal.renameTo(archivo)) {
            temporal.delete()
            error("no se pudo reemplazar ${archivo.name}")
        }
    }
}

/** El archivo del día de cobro en `filesDir`; el mismo para el adaptador y el worker. */
fun almacenDelDiaDeCobro(context: android.content.Context): DiaDeCobroAlmacen =
    DiaDeCobroAlmacen(File(context.filesDir, "dia_de_cobro.json"))
