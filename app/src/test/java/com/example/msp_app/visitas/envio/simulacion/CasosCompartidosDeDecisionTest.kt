package com.example.msp_app.visitas.envio.simulacion

import com.example.msp_app.core.common.visitas.envio.Existencia
import com.example.msp_app.core.common.visitas.envio.ResultadoGet
import com.example.msp_app.core.common.visitas.envio.VisitaEnServidor
import com.example.msp_app.core.common.visitas.envio.existenciaCompleta
import com.example.msp_app.core.common.visitas.envio.salidaTras2xx
import com.example.msp_app.core.common.visitas.envio.salidaTrasError
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La tabla de decisión que la simulación Go replica (msp-api
 * `internal/visitas/sim/testdata/casos-decision-envio.json`, 52 casos,
 * E-VIS-027) corrida contra las funciones REALES de `:core:common`
 * (E-APP-076). Si Kotlin cambia una decisión y Go no, esto se pone rojo: la
 * simulación Go dejaría de modelar al teléfono.
 *
 * El archivo es una copia byte a byte; su sha256 se fija aquí. Si Go lo
 * regenera (`SIM_ACTUALIZAR_CASOS=1`), hay que volver a copiarlo.
 */
class CasosCompartidosDeDecisionTest {

    private class VisitaJson(
        val id: String?,
        @SerializedName(
            "imagen_ids"
        ) val imagenIds: List<String>?
    )
    private class GetJson(val codigo: Int?, val visita: VisitaJson?)
    private class Caso(
        val origen: String,
        val funcion: String,
        val enviada: String,
        val eco: String?,
        @SerializedName("fotos_pendientes") val fotosPendientes: List<String>?,
        val get: GetJson?,
        val existencia: String?,
        val codigo: Int,
        val cabecera: Boolean,
        val respuesta: VisitaJson?,
        @SerializedName("codigo_get") val codigoGet: Int?,
        val esperado: String
    )
    private class Archivo(val version: Int, val casos: List<Caso>)

    private fun VisitaJson.dominio() = VisitaEnServidor(id, imagenIds)

    @Test
    fun `E-APP-076 los 52 casos compartidos con la simulacion Go dan lo mismo en Kotlin`() {
        val bytes = requireNotNull(
            javaClass.getResourceAsStream("/visitas/casos-decision-envio.json")
        ).readBytes()
        val sha = MessageDigest.getInstance(
            "SHA-256"
        ).digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(SHA_CASOS, sha)
        val archivo = Gson().fromJson(bytes.decodeToString(), Archivo::class.java)
        assertEquals(1, archivo.version)
        assertEquals(CASOS, archivo.casos.size)
        val funciones = mutableSetOf<String>()
        archivo.casos.forEach { c ->
            val pend = c.fotosPendientes.orEmpty().toSet()
            val got = when (c.funcion) {
                "salidaTras2xx" -> salidaTras2xx(
                    c.enviada,
                    c.eco,
                    pend,
                    c.get?.let { ResultadoGet(it.codigo, it.visita?.dominio()) }
                ).name
                "salidaTrasError" -> salidaTrasError(
                    Existencia.valueOf(c.existencia!!),
                    c.codigo,
                    c.cabecera
                ).name
                "existenciaCompleta" -> existenciaCompleta(
                    c.enviada,
                    c.respuesta?.dominio(),
                    pend,
                    c.codigoGet
                ).name
                else -> "funcion_desconocida:${c.funcion}"
            }
            funciones += c.funcion
            assertEquals(c.origen, c.esperado, got)
        }
        assertTrue("control: las tres funciones aparecen", funciones.size == 3)
    }

    private companion object {
        const val CASOS = 52
        const val SHA_CASOS = "cfd4bbf485ea18d022432d005358a93ac6dc20c41241656f2de5e4817a598a3e"
    }
}
