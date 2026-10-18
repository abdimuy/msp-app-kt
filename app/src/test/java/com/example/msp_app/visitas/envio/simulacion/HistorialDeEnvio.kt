package com.example.msp_app.visitas.envio.simulacion

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import java.io.File

/*
 * Historial de la simulación Kotlin del envío de visitas (plan visitas P1,
 * Tarea 17; E-APP-076) y su verificador.
 *
 * El formato es EXACTAMENTE el de la simulación Go (msp-api
 * `internal/visitas/sim/historial_test.go`, Tarea 16, E-VIS-027): una línea
 * JSON por evento, `{"paso":1,"tipo":"captura","visita_id":"…","detalle":"…"}`,
 * con el detalle en pares `clave=valor` separados por espacio. El mismo
 * archivo lo vuelve a verificar fuera de línea `TestVerificarHistorialesEnDisco`
 * de msp-api con `SIM_HISTORIAL_DIR` apuntando a `app/build/sim-historial`.
 *
 * [verificar] es una traducción línea por línea del `Verificar` de Go, con UNA
 * diferencia pedida por los revisores: aquí `estampadas=` es OBLIGATORIO en
 * cada `marca_confirmada` (en Go, `VerificarEstricto`). Sin esa exigencia, una
 * falla `foto_sin_archivo_local` seguida de una marca que no dice qué estampó
 * eximiría a una foto que el teléfono sí estampó y borró.
 */

data class Evento(
    @SerializedName("paso") val paso: Int,
    @SerializedName("tipo") val tipo: String,
    @SerializedName("visita_id") val visitaId: String,
    @SerializedName("detalle") val detalle: String
)

internal object TipoEvento {
    const val CAPTURA = "captura"
    const val FOTO_AGREGADA = "foto_agregada"
    const val ENVIO = "envio"
    const val RESPUESTA = "respuesta"
    const val GET = "get"
    const val SERVIDOR = "servidor"
    const val BANDEJA = "bandeja"
    const val FALLA = "falla"
    const val CONFIRMADA = "marca_confirmada"
    const val RESGUARDADA = "marca_resguardada"
    const val DEVUELTA = "devuelta"
    const val FINAL = "final"
}

/** Detalle de la falla que emite el INYECTOR al borrar el archivo local de una foto pendiente. */
internal const val FALLA_FOTO_SIN_ARCHIVO = "foto_sin_archivo_local"

class InvarianteError(val inv: String, val paso: Int, val visita: String, mensaje: String) :
    AssertionError("$inv (paso $paso, visita $visita): $mensaje")

private fun campos(detalle: String): Map<String, String> =
    detalle.split(' ').filter { it.isNotEmpty() }.associate { par ->
        val i = par.indexOf('=')
        if (i < 0) par to "" else par.substring(0, i) to par.substring(i + 1)
    }

private fun lista(v: String?): List<String> =
    if (v.isNullOrEmpty() || v == "-") emptyList() else v.split(',')

private class Vista(
    val fotos: MutableList<String>,
    /** Fotos con archivo agregadas DESPUÉS de confirmar (E-APP-079): se cobran al final. */
    val deuda: MutableList<String> = mutableListOf(),
    var filas: Int = 0,
    var enDisco: List<String> = emptyList(),
    var bandejaEntera: Boolean = false,
    var estado: String = "pendiente",
    var finalVisto: Boolean = false
) {
    fun completaEnServidor(): Boolean = filas >= 1 && fotos.all { it in enDisco }
}

/**
 * Comprueba I1, I2, I3 e I3-foto sobre [h] sin mirar nada más que el
 * historial (mismas reglas y mismos mensajes que el `Verificar` de Go).
 * Devuelve el primer [InvarianteError] o `null`.
 *
 * @param exigirEstampadas `true` para todo historial que escribe esta
 *   simulación (ver el comentario del archivo); `false` solo para los
 *   historiales hechos a mano que se copian tal cual de Go.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "NestedBlockDepth")
fun verificar(
    h: List<Evento>,
    exigirEstampadas: Boolean = true,
    permitirDeuda: Boolean = false
): InvarianteError? {
    val vs = linkedMapOf<String, Vista>()
    fun get(e: Evento): Vista? = vs[e.visitaId]
    fun nunca(e: Evento) =
        InvarianteError("I2", e.paso, e.visitaId, "evento ${e.tipo} de una visita nunca capturada")
    if (h.isEmpty()) return InvarianteError("formato", 0, "", "historial vacío")
    var anterior = 0
    h.forEachIndexed { i, e ->
        if (i > 0 && e.paso <= anterior) {
            return InvarianteError(
                "formato",
                e.paso,
                e.visitaId,
                "paso ${e.paso} después de $anterior: los pasos deben crecer"
            )
        }
        anterior = e.paso
        val c = campos(e.detalle)
        when (e.tipo) {
            TipoEvento.CAPTURA -> {
                if (vs.containsKey(
                        e.visitaId
                    )
                ) {
                    return InvarianteError("I2", e.paso, e.visitaId, "capturada dos veces")
                }
                val sin = lista(c["sin_archivo"])
                vs[e.visitaId] = Vista(lista(c["fotos"]).filterNot { it in sin }.toMutableList())
            }
            TipoEvento.FOTO_AGREGADA -> {
                val v = get(e) ?: return nunca(e)
                if (c["archivo"] == "1") {
                    // E-APP-079: ya no es violación en el acto. Queda como
                    // deuda: la visita tiene que volver a pendiente (barrido)
                    // o la foto llegar al servidor antes del final.
                    if (v.estado == "confirmada") v.deuda += c["foto"].orEmpty() else v.fotos += c["foto"].orEmpty()
                }
            }
            TipoEvento.SERVIDOR -> {
                val v = get(e) ?: return nunca(e)
                v.filas = c["filas"]?.toIntOrNull() ?: 0
                v.enDisco = lista(c["fotos"])
                if (v.filas > 1) {
                    return InvarianteError(
                        "I2",
                        e.paso,
                        e.visitaId,
                        "el servidor tiene la visita más de una vez"
                    )
                }
                if (v.estado == "confirmada" && !v.completaEnServidor()) {
                    return InvarianteError(
                        "I3-foto",
                        e.paso,
                        e.visitaId,
                        "confirmada en el teléfono y el servidor ya no la tiene completa: ${e.detalle}"
                    )
                }
            }
            TipoEvento.BANDEJA -> {
                val v = get(e) ?: return nunca(e)
                if (c["entera"] == "1") v.bandejaEntera = true
            }
            TipoEvento.CONFIRMADA -> {
                val v = get(e) ?: return nunca(e)
                if (exigirEstampadas && !c.containsKey("estampadas")) {
                    return InvarianteError(
                        "formato",
                        e.paso,
                        e.visitaId,
                        "marca_confirmada sin estampadas=: no se sabe qué fotos borró el teléfono"
                    )
                }
                if (v.filas < 1) {
                    return InvarianteError(
                        "I3",
                        e.paso,
                        e.visitaId,
                        "confirmada sin estar en el servidor"
                    )
                }
                lista(c["estampadas"]).forEach { if (it !in v.fotos) v.fotos += it }
                v.deuda.removeAll { it in v.fotos }
                if (!v.completaEnServidor()) {
                    return InvarianteError(
                        "I3-foto",
                        e.paso,
                        e.visitaId,
                        "confirmada con fotos ${v.fotos} y en disco solo ${v.enDisco}"
                    )
                }
                v.estado = "confirmada"
            }
            TipoEvento.RESGUARDADA -> {
                val v = get(e) ?: return nunca(e)
                if (!v.bandejaEntera) {
                    return InvarianteError(
                        "I3",
                        e.paso,
                        e.visitaId,
                        "resguardada sin una fila entera en la bandeja"
                    )
                }
                v.estado = "resguardada"
            }
            TipoEvento.DEVUELTA -> {
                val v = get(e) ?: return nunca(e)
                v.estado = "pendiente"
                // Una fila de bandeja anterior ya no prueba nada: resguardar
                // otra vez exige una bandeja nueva (R4 del revisor, E-APP-079).
                v.bandejaEntera = false
                // La deuda vuelve a ser una foto pendiente normal: el reenvío la exige.
                v.fotos += v.deuda.filterNot { it in v.fotos }
                v.deuda.clear()
            }
            TipoEvento.FINAL -> {
                val v = get(e) ?: return nunca(e)
                v.finalVisto = true
                if (e.detalle != v.estado) {
                    return InvarianteError(
                        "I3",
                        e.paso,
                        e.visitaId,
                        "estado final ${e.detalle} no sale de las marcas (${v.estado})"
                    )
                }
                if (!permitirDeuda && v.estado == "confirmada" && v.deuda.isNotEmpty()) {
                    return InvarianteError(
                        "I3-foto",
                        e.paso,
                        e.visitaId,
                        "foto con archivo agregada después de confirmar y nunca enviada: ${v.deuda}"
                    )
                }
                if (e.detalle != "pendiente" && !v.bandejaEntera && !v.completaEnServidor()) {
                    return InvarianteError(
                        "I1",
                        e.paso,
                        e.visitaId,
                        "no está pendiente, ni completa en el servidor, ni entera en la bandeja"
                    )
                }
            }
            TipoEvento.FALLA -> {
                if (e.detalle.substringBefore(' ') == FALLA_FOTO_SIN_ARCHIVO) {
                    val v = get(e) ?: return nunca(e)
                    if (v.estado != "confirmada") v.fotos.removeAll { it == c["foto"] }
                    // Una deuda que pierde su archivo ya no la puede mandar nadie (como sin_archivo).
                    v.deuda.removeAll { it == c["foto"] }
                }
            }
            TipoEvento.ENVIO, TipoEvento.RESPUESTA, TipoEvento.GET -> Unit
            else -> return InvarianteError(
                "formato",
                e.paso,
                e.visitaId,
                "tipo desconocido \"${e.tipo}\""
            )
        }
    }
    vs.forEach { (id, v) ->
        if (!v.finalVisto) return InvarianteError("I1", 0, id, "capturada y sin estado final")
    }
    return null
}

private val gson = GsonBuilder().disableHtmlEscaping().create()

fun escribirJsonl(archivo: File, h: List<Evento>) {
    archivo.parentFile?.mkdirs()
    archivo.bufferedWriter().use { w ->
        h.forEach { e ->
            w.write(gson.toJson(e))
            w.newLine()
        }
    }
}

fun leerJsonl(archivo: File): List<Evento> =
    archivo.readLines().filter { it.isNotBlank() }.map { gson.fromJson(it, Evento::class.java) }
