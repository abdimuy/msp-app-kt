package com.example.msp_app.core.visitas.envio

import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ConciliacionInversa
import com.example.msp_app.core.database.dao.enviodiagnostico.FallosPorCodigo
import com.example.msp_app.core.database.dao.visit.ConteosDeEnvio
import java.io.File
import java.time.Instant
import java.util.Random
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan visitas P1, Tarea 14 (cierra H14): telemetría de visitas sin datos
 * personales. Mismo criterio que `COBRANZA-SYNC.md:506-511`: lista blanca
 * CERRADA de claves, y solo conteos, antigüedades y la versión.
 */
class TelemetriaDeVisitasTest {

    private val ahora = Instant.parse("2026-10-16T12:00:00Z")

    @After
    fun limpiar() {
        TelemetriaDeVisitas.sinkParaPrueba = null
    }

    private fun capturar(): MutableList<Pair<String, Map<String, Any>>> {
        val capturado = mutableListOf<Pair<String, Map<String, Any>>>()
        TelemetriaDeVisitas.sinkParaPrueba = { e, m -> capturado += e to m }
        return capturado
    }

    /**
     * La lista blanca, escrita a mano. Si alguien agrega una clave a
     * [TelemetriaDeVisitas.CLAVES] esta prueba se pone roja: el cambio tiene
     * que hacerse aquí también, a la vista de quien revisa, y la clave nueva
     * tiene que ser un conteo o una antigüedad.
     */
    @Test
    fun `H14 la lista blanca es exactamente esta`() {
        val base = setOf(
            "app.version",
            "visitas.pendientes",
            "visitas.pendiente_mas_vieja_min",
            "visitas.resguardadas",
            "visitas.confirmadas_24h",
            "visitas.fallos.intentos_max",
            "visitas.fallos.otro",
            "visitas.reconcilia.devuelta_a_pendiente",
            "visitas.conciliacion.revisadas",
            "visitas.conciliacion.faltantes",
            "visitas.conciliacion.confirmadas",
            "visitas.conciliacion.tandas_fallidas",
            "visitas.conciliacion.ids_invalidos",
            "visitas.conciliacion.ids_ajenos",
            "visitas.conciliacion.retenidas_por_comprobante",
            "visitas.conciliacion.confirmar_fallido",
            "visitas.conciliacion.devolver_fallido",
            "visitas.conciliacion.entregadas_ilegibles",
            "visitas.conciliacion.devueltas_por_foto",
            "visitas.conciliacion.fotos_abandonadas",
            "visitas.resguardadas_por_podar"
        )
        val fallos = TelemetriaDeVisitas.CODIGOS_DE_FALLO.map { "visitas.fallos.$it" }.toSet()
        assertEquals(base + fallos, TelemetriaDeVisitas.CLAVES)
        assertEquals(
            setOf("visitas_estado", "visitas_conciliacion", "visitas_poda"),
            TelemetriaDeVisitas.EVENTOS
        )
    }

    @Test
    fun `ningun evento emite datos personales`() {
        val d = TelemetriaDeVisitas.estado(
            ConteosDeEnvio(3, "2026-10-16T10:00:00Z", 1, 7),
            ahora,
            "2.21.0"
        )
        assertTrue(TelemetriaDeVisitas.CLAVES.containsAll(d.keys))
        assertEquals(120L, d["visitas.pendiente_mas_vieja_min"])
        assertEquals(3, d["visitas.pendientes"])
        assertEquals(1, d["visitas.resguardadas"])
        assertEquals(7, d["visitas.confirmadas_24h"])
        assertEquals("2.21.0", d["app.version"])
    }

    @Test
    fun `H14 sin pendientes no hay antiguedad`() {
        val d = TelemetriaDeVisitas.estado(ConteosDeEnvio(0, null, 0, 0), ahora, "2.21.0")
        assertFalse(d.containsKey("visitas.pendiente_mas_vieja_min"))
        assertEquals(0, d["visitas.pendientes"])
    }

    /**
     * Las filas que se reintentan para siempre (cuerpo inválido, fecha futura,
     * 413 sin custodia…) se VEN: su código vigente se cuenta por clave propia
     * y el máximo de intentos dice cuánto lleva la peor.
     */
    @Test
    fun `H14 las filas atoradas se cuentan por codigo y el desconocido cae en otro`() {
        val d = TelemetriaDeVisitas.estado(
            ConteosDeEnvio(4, "2026-10-16T10:00:00Z", 0, 0),
            ahora,
            "2.21.0",
            listOf(
                FallosPorCodigo("visita_body_too_large_to_capture", 1, 37),
                FallosPorCodigo("visita_body_json_invalido", 2, 12),
                FallosPorCodigo("visita_confirmada_por_otro_camino_con_foto_nueva", 1, 3),
                FallosPorCodigo("rosa_maria_hernandez", 1, 2),
                FallosPorCodigo("codigo_que_nadie_conoce", 3, 1)
            )
        )
        assertEquals(1, d["visitas.fallos.visita_body_too_large_to_capture"])
        assertEquals(2, d["visitas.fallos.visita_body_json_invalido"])
        assertEquals(1, d["visitas.fallos.visita_confirmada_por_otro_camino_con_foto_nueva"])
        assertEquals(4, d["visitas.fallos.otro"])
        assertEquals(37, d["visitas.fallos.intentos_max"])
        assertTrue(TelemetriaDeVisitas.CLAVES.containsAll(d.keys))
        assertTrue(d.keys.none { it.contains("rosa") || it.contains("nadie") })
    }

    /**
     * T13: cada código que hoy sale solo por `SyncErrorReporter` (que termina
     * en `StubTelemetrySink`) tiene su conteo. `devuelta_a_pendiente` es la
     * alerta §12-1.
     */
    @Test
    fun `H14 la conciliacion lleva todos los conteos de la corrida`() {
        val d = TelemetriaDeVisitas.conciliacion(
            ConciliacionInversa(
                revisadas = 40,
                faltantes = 2,
                confirmadas = 35,
                tandasFallidas = 1,
                entregadasIlegibles = false,
                idsInvalidos = 3,
                idsAjenos = 4,
                retenidasPorComprobante = 5,
                confirmacionesFallidas = 6,
                devolucionesFallidas = 7,
                devueltasPorFoto = 8,
                fotosAbandonadas = 9
            )
        )
        assertEquals(
            mapOf<String, Any>(
                "visitas.reconcilia.devuelta_a_pendiente" to 2,
                "visitas.conciliacion.revisadas" to 40,
                "visitas.conciliacion.faltantes" to 2,
                "visitas.conciliacion.confirmadas" to 35,
                "visitas.conciliacion.tandas_fallidas" to 1,
                "visitas.conciliacion.ids_invalidos" to 3,
                "visitas.conciliacion.ids_ajenos" to 4,
                "visitas.conciliacion.retenidas_por_comprobante" to 5,
                "visitas.conciliacion.confirmar_fallido" to 6,
                "visitas.conciliacion.devolver_fallido" to 7,
                "visitas.conciliacion.entregadas_ilegibles" to 0,
                "visitas.conciliacion.devueltas_por_foto" to 8,
                "visitas.conciliacion.fotos_abandonadas" to 9
            ),
            d
        )
    }

    @Test
    fun `H14 la poda reporta solo el conteo de resguardadas por podar`() {
        assertEquals(
            mapOf<String, Any>("visitas.resguardadas_por_podar" to 9),
            TelemetriaDeVisitas.poda(9)
        )
    }

    /**
     * El valor de la clave ajena es un `Int` a propósito: así solo el filtro de
     * claves puede descartarla (un texto lo descartaría también el de valores).
     */
    @Test
    fun `emitir descarta claves fuera de la lista blanca`() {
        val capturado = capturar()
        TelemetriaDeVisitas.emitir(
            "visitas_estado",
            mapOf("visitas.pendientes" to 1, "cliente" to 30144)
        )
        assertEquals(setOf("visitas.pendientes"), capturado.single().second.keys)
    }

    /** Una clave permitida con un valor que no es conteo (un nombre, una coordenada) no sale. */
    @Test
    fun `H14 emitir descarta valores que no son conteos aunque la clave este permitida`() {
        val capturado = capturar()
        TelemetriaDeVisitas.emitir(
            "visitas_estado",
            mapOf(
                "visitas.pendientes" to "Rosa María Hernández",
                "visitas.resguardadas" to 19.043415,
                "visitas.confirmadas_24h" to -1,
                "app.version" to "Calle 5 de Mayo 214, Puebla",
                "visitas.fallos.otro" to 2
            )
        )
        assertEquals(mapOf<String, Any>("visitas.fallos.otro" to 2), capturado.single().second)
    }

    @Test
    fun `H14 emitir no manda eventos fuera de la lista`() {
        val capturado = capturar()
        TelemetriaDeVisitas.emitir("visita_de_rosa", mapOf("visitas.pendientes" to 1))
        assertEquals(emptyList<Pair<String, Map<String, Any>>>(), capturado)
    }

    /** Propiedad, 500 casos con semilla fija: nada fuera de la lista blanca sale nunca. */
    @Test
    fun `H14 propiedad cualquier mapa sale filtrado a la lista blanca`() {
        val random = Random(14)
        val capturado = capturar()
        val ajenas =
            listOf("cliente", "nombre", "lat", "lng", "nota", "monto", "visitas.cliente_id", "")
        repeat(500) {
            val datos = buildMap<String, Any> {
                repeat(random.nextInt(8)) {
                    val clave = if (random.nextBoolean()) {
                        TelemetriaDeVisitas.CLAVES.random(kotlin.random.Random(random.nextLong()))
                    } else {
                        ajenas[random.nextInt(ajenas.size)]
                    }
                    put(
                        clave,
                        if (random.nextBoolean()) {
                            random.nextInt(
                                100
                            )
                        } else {
                            "texto ${random.nextInt()}"
                        }
                    )
                }
            }
            TelemetriaDeVisitas.emitir("visitas_estado", datos)
        }
        capturado.forEach { (_, m) ->
            assertTrue(TelemetriaDeVisitas.CLAVES.containsAll(m.keys))
            m.forEach { (k, v) -> assertTrue("$k=$v", k == "app.version" || v is Int || v is Long) }
        }
    }

    @Test
    fun `si la telemetria revienta el envio sigue`() {
        TelemetriaDeVisitas.sinkParaPrueba = { _, _ -> error("boom") }
        TelemetriaDeVisitas.emitir("visitas_estado", mapOf("visitas.pendientes" to 1)) // no lanza
    }

    @Test
    fun `H14 sin Logger inicializado emitir no lanza`() {
        TelemetriaDeVisitas.sinkParaPrueba = null
        TelemetriaDeVisitas.emitir("visitas_estado", mapOf("visitas.pendientes" to 1)) // no lanza
    }

    /**
     * Control positivo de la lista de códigos: todo código que el worker de
     * visitas (T11) o el clasificador de red anotan está en
     * [TelemetriaDeVisitas.CODIGOS_DE_FALLO]. Si alguien agrega un `FalloDeEnvio`
     * nuevo al worker, esta prueba lo obliga a darle clave en vez de que caiga
     * mudo en "otro".
     */
    @Test
    fun `H14 todo codigo que anota el worker o el clasificador tiene clave propia`() {
        val raiz = (if (File("src/main/java").isDirectory) File("..") else File(".")).canonicalFile
        val worker = File(
            raiz,
            "app/src/main/java/com/example/msp_app/workers/PendingVisitsWorker.kt"
        ).readText()
        val clasificador = File(
            raiz,
            "core/network/src/main/kotlin/com/example/msp_app/core/network/diagnostico/ClasificadorDeFalloDeEnvio.kt"
        ).readText()
        val delWorker = Regex("""codigo = "([a-z0-9_]+)"""").findAll(worker).map {
            it.groupValues[1]
        }.toSet()
        val deRed = Regex(
            """red\("([a-z0-9_]+)""""
        ).findAll(clasificador).map { it.groupValues[1] }.toSet()
        // Control positivo: la búsqueda encuentra los que sabemos que existen.
        assertTrue(
            delWorker.containsAll(
                setOf("visita_sin_eco", "visita_confirmada_por_otro_camino_con_foto_nueva")
            )
        )
        assertTrue(deRed.containsAll(setOf("timeout", "network_error")))
        assertEquals(emptySet<String>(), (delWorker + deRed) - TelemetriaDeVisitas.CODIGOS_DE_FALLO)
    }
}
