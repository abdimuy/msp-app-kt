package com.example.msp_app.core.common.ventas.precios

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.math.BigDecimal
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-062: juego de casos compartido con msp-api
 * (`internal/ventas/domain/testdata/precios-orden/casos.json`), copiado a
 * `src/test/resources/precios-orden/` sin editar. `VERSION` es el SHA-256 del
 * archivo: el mismo VERSION aquí y en msp-api demuestra que los dos repos
 * prueban los mismos bytes.
 */
class CasosPreciosOrdenTest {

    private class Juego(val version: Int?, val casos: List<Caso>?)

    private class Caso(
        val nombre: String?,
        @SerializedName("tipo_venta") val tipoVenta: String?,
        val lineas: List<Linea>?,
        val esperado: Esperado?
    )

    private class Linea(
        val tipo: String?,
        val cantidad: String?,
        @SerializedName("precio_contado") val precioContado: String?,
        @SerializedName("precio_corto") val precioCorto: String?,
        @SerializedName("precio_anual") val precioAnual: String?,
        // v3: hijo del combo inmediato anterior; ausente = false.
        @SerializedName("dentro_de_combo") val dentroDeCombo: Boolean?
    )

    private class Esperado(val ok: Boolean?, val codigo: String?)

    private fun directorio(): File {
        val url = requireNotNull(javaClass.classLoader?.getResource(DIRECTORIO)) {
            "no está src/test/resources/$DIRECTORIO en el classpath de prueba"
        }
        require(
            url.protocol == "file"
        ) { "los casos deben leerse de disco, llegaron como ${url.protocol}" }
        return File(url.toURI())
    }

    private fun archivo() = File(directorio(), ARCHIVO)

    private fun juego(): Juego = Gson().fromJson(
        archivo().readText(Charsets.UTF_8),
        Juego::class.java
    )

    @Test
    fun `VERSION coincide con el SHA-256 de casos_json`() {
        val sha = MessageDigest.getInstance("SHA-256").digest(archivo().readBytes())
            .joinToString("") { "%02x".format(it) }
        assertEquals(File(directorio(), "VERSION").readText(Charsets.US_ASCII).trim(), sha)
    }

    @Test
    fun `el juego trae casos de los dos tipos de venta y de los dos codigos`() {
        val casos = requireNotNull(juego().casos)
        assertTrue(casos.isNotEmpty())
        assertTrue(casos.any { it.tipoVenta == "CONTADO" })
        assertTrue(casos.any { it.tipoVenta == "CREDITO" })
        assertTrue(casos.any { it.esperado?.codigo == ReglaOrdenDePrecios.CODIGO_LINEA })
        assertTrue(casos.any { it.esperado?.codigo == ReglaOrdenDePrecios.CODIGO_TOTALES })
    }

    @Test
    fun `E-APP-062 cada caso compartido da el veredicto esperado`() {
        val juego = juego()
        // La versión 1 del juego codifica sólo la regla de orden; desde la 2 entran las de
        // anual en crédito (E-APP-067) y, desde la 3, contado en contado (E-APP-068).
        val conReglasDeLineaSuelta = requireNotNull(juego.version) >= VERSION_CON_ANUAL
        val fallas = requireNotNull(juego.casos).mapNotNull { caso ->
            val nombre = requireNotNull(caso.nombre)
            // El tipo de venta no cambia la regla (igual que el servidor); sólo se exige que venga.
            require(
                caso.tipoVenta in setOf("CONTADO", "CREDITO")
            ) { "$nombre: tipo_venta ${caso.tipoVenta}" }
            val lineas = requireNotNull(caso.lineas) { nombre }.map { l ->
                LineaDePrecios(
                    tipo = when (l.tipo) {
                        "producto" -> TipoDeLinea.PRODUCTO
                        "combo" -> TipoDeLinea.COMBO
                        else -> error("$nombre: tipo de línea desconocido ${l.tipo}")
                    },
                    nombre = nombre,
                    cantidad = BigDecimal(requireNotNull(l.cantidad) { nombre }),
                    contado = BigDecimal(requireNotNull(l.precioContado) { nombre }),
                    corto = BigDecimal(requireNotNull(l.precioCorto) { nombre }),
                    anual = BigDecimal(requireNotNull(l.precioAnual) { nombre }),
                    dentroDeCombo = l.dentroDeCombo == true
                )
            }
            // Un hijo (`dentro_de_combo`) cuelga del combo inmediato anterior.
            caso.lineas.forEachIndexed { i, l ->
                if (l.dentroDeCombo == true) {
                    val padre = caso.lineas.take(i).lastOrNull { it.dentroDeCombo != true }
                    require(
                        padre?.tipo == "combo"
                    ) { "$nombre: hijo sin combo anterior en la línea $i" }
                }
            }
            val esperado = requireNotNull(caso.esperado) { nombre }
            val obtenido = if (conReglasDeLineaSuelta) {
                ReglasDePreciosDeVenta.validar(
                    esCredito = caso.tipoVenta == "CREDITO",
                    lineas = lineas
                )
            } else {
                ReglaOrdenDePrecios.validar(lineas)
            }
            val codigo = (obtenido as? VeredictoOrdenDePrecios.Invalido)?.codigo
            val okObtenido = obtenido is VeredictoOrdenDePrecios.Valido
            if (okObtenido == esperado.ok && codigo == esperado.codigo) {
                null
            } else {
                "$nombre: esperado ok=${esperado.ok} codigo=${esperado.codigo}, obtenido ok=$okObtenido codigo=$codigo"
            }
        }
        assertEquals(emptyList<String>(), fallas)
    }

    private companion object {
        const val DIRECTORIO = "precios-orden"
        const val ARCHIVO = "casos.json"
        const val VERSION_CON_ANUAL = 2
    }
}
