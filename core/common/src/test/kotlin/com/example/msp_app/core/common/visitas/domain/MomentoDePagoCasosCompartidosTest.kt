package com.example.msp_app.core.common.visitas.domain

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Un caso de `testdata/fecha-pago-sync` de msp-api: la fila como la deja el sync y lo que se lee de ella. */
internal data class CasoDeFechaPagoSync(
    val archivo: String,
    val id: String,
    val fechaSync: String,
    val pagoRecibidoId: String?,
    val esperado: MomentoDePago
) {
    override fun toString() = archivo

    companion object {
        private fun JsonObject.texto(k: String): String? =
            get(k)?.takeUnless { it.isJsonNull }?.asString

        fun desdeJson(archivo: String, json: String): CasoDeFechaPagoSync {
            val raiz = JsonParser.parseString(json).asJsonObject
            val sync = raiz.getAsJsonObject("sync")
            val esperado = raiz.getAsJsonObject("esperado")
            return CasoDeFechaPagoSync(
                archivo = archivo,
                id = requireNotNull(sync.texto("id")),
                fechaSync = requireNotNull(sync.texto("fecha")),
                pagoRecibidoId = sync.texto("pago_recibido_id"),
                esperado = MomentoDePago(
                    instante = esperado.texto("instante")?.let(Instant::parse),
                    fecha = LocalDate.parse(requireNotNull(esperado.texto("fecha")))
                )
            )
        }
    }
}

/**
 * El juego `fecha-pago-sync` compartido con msp-api (copia byte a byte en
 * `src/test/resources/fecha-pago-sync/`, `scripts/casos-compromisos.sh copiar`).
 * msp-api comprueba con los mismos bytes que el servidor produce esa `fecha`
 * en el sync y que su agenda la lee igual (`TestFechaPagoSync_CadaCaso_EAPP112`);
 * aquí, que el teléfono la lee igual que el servidor (E-APP-112).
 */
internal object JuegoDeFechaPagoSync {
    private const val DIRECTORIO = "fecha-pago-sync"

    fun directorio(): File {
        val url = requireNotNull(javaClass.classLoader?.getResource(DIRECTORIO)) {
            "no está src/test/resources/$DIRECTORIO; corre scripts/casos-compromisos.sh copiar"
        }
        return File(url.toURI())
    }

    fun casos(): List<CasoDeFechaPagoSync> = JuegoDeCompromisos.archivosDeCaso(directorio())
        .map { CasoDeFechaPagoSync.desdeJson(it.name, it.readText(Charsets.UTF_8)) }
}

@RunWith(Parameterized::class)
internal class MomentoDePagoCasosCompartidosTest(private val caso: CasoDeFechaPagoSync) {

    @Test
    fun `cada caso del sync se lee como lo lee el servidor - E-APP-112`() {
        assertEquals(
            caso.esperado,
            momentoDePago(caso.id, caso.fechaSync, caso.pagoRecibidoId, NEGOCIO)
        )
    }

    companion object {
        private val NEGOCIO: ZoneId = ZoneId.of("America/Mexico_City")

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun casos(): List<CasoDeFechaPagoSync> = JuegoDeFechaPagoSync.casos()
    }
}

class JuegoDeFechaPagoSyncTest {

    @Test
    fun `VERSION coincide con el SHA-256 de los casos - E-APP-112`() {
        val dir = JuegoDeFechaPagoSync.directorio()
        val archivos = JuegoDeCompromisos.archivosDeCaso(dir)
        assertEquals(JuegoDeCompromisos.versionDeclarada(dir), JuegoDeCompromisos.sha256(archivos))
    }

    @Test
    fun `el juego trae oficina sin hora y app con hora`() {
        val casos = JuegoDeFechaPagoSync.casos()
        assertTrue(casos.any { it.esperado.instante == null })
        assertTrue(casos.any { it.esperado.instante != null })
        assertTrue(
            "al menos un pago cuyo día UTC no es su día civil",
            casos.any { !it.fechaSync.startsWith(it.esperado.fecha.toString()) }
        )
    }
}

/** Lo que el juego compartido no cubre porque no viene del sync (E-APP-112). */
class MomentoDePagoTest {

    private val negocio: ZoneId = ZoneId.of("America/Mexico_City")

    @Test
    fun `captura local a medianoche conserva su hora`() {
        val m = momentoDePago(UUID_LOCAL, "2026-10-16T06:00:00Z", null, negocio)
        assertEquals(
            MomentoDePago(Instant.parse("2026-10-16T06:00:00Z"), LocalDate.parse("2026-10-16")),
            m
        )
    }

    /** Solo un id de dígitos es fila del sync (`impte_docto_cc_id`); los bordes del rango cuentan. */
    @Test
    fun `solo un id de digitos es fila del sync`() {
        val medianoche = "2026-08-24T06:00:00Z"
        for (id in listOf("0", "9", "1234567890")) {
            val instante = momentoDePago(id, medianoche, null, negocio)?.instante
            assertEquals(id, null, instante)
        }
        // '/' y ':' rodean a '0'..'9' en ASCII; el id vacío no es fila.
        for (id in listOf("", "/", ":", "12/4", "12:4", "1a")) {
            val instante = momentoDePago(id, medianoche, null, negocio)?.instante
            assertEquals(id, Instant.parse(medianoche), instante)
        }
    }

    @Test
    fun `fila del sync un segundo despues de medianoche tiene hora`() {
        val m = momentoDePago("14998988", "2026-08-24T06:00:01Z", null, negocio)
        assertEquals(Instant.parse("2026-08-24T06:00:01Z"), m?.instante)
    }

    @Test
    fun `solo el dia es un pago sin hora`() {
        assertEquals(
            MomentoDePago(null, LocalDate.parse("2026-10-16")),
            momentoDePago(UUID_LOCAL, "2026-10-16", null, negocio)
        )
    }

    @Test
    fun `fecha ilegible no entra`() {
        assertEquals(null, momentoDePago("14998988", "basura", null, negocio))
        assertEquals(null, momentoDePago("14998988", "2026-13-40", null, negocio))
    }

    private companion object {
        const val UUID_LOCAL = "6b0c1f2e-3d4a-4b5c-8d6e-7f8091a2b3c4"
    }
}
