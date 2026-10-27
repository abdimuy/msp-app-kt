package com.example.msp_app.core.diagnosticoenvio

import com.example.msp_app.navigation.EscanerDeFuentes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-063 — regla dura del spec visitas-compromisos §4.7: **ninguna consulta
 * de envío ni de pendientes lee `envio_diagnostico`**. Fuera de la pantalla de
 * pendientes, el depurador remoto, la telemetría (el propio paquete
 * `core/diagnosticoenvio`) y la definición de la tabla, ningún archivo de
 * producción de ningún módulo puede nombrar algo que la LEA.
 *
 * Lo que sí puede usar cualquiera (p. ej. el worker de visitas) es el lado de
 * solo-escritura: `EnvioDiagnostico.sink`, `EnvioDiagnosticoSink`,
 * `EnvioObservado`, `FalloDeEnvio`, `ClasificadorDeFalloDeEnvio`. Ninguno de
 * esos devuelve nada que se pueda leer de la tabla.
 */
class EnvioDiagnosticoAislamientoTest {

    /** Lo que da acceso de LECTURA a la tabla. */
    private val simbolosDeLectura = Regex(
        """\b(envioDiagnosticoDao|EnvioDiagnosticoDao|EnvioDiagnosticoEntity|LectorDeEnvioDiagnostico|""" +
            """observarFallosVigentes|RegistradorDeEnvioDiagnostico)\b|envio_diagnostico"""
    )

    private val permitidos = listOf(
        "core/database/src/main/kotlin/com/example/msp_app/core/database/AppDatabase.kt",
        "core/database/src/main/kotlin/com/example/msp_app/core/database/entities/EnvioDiagnosticoEntity.kt",
        "core/database/src/main/kotlin/com/example/msp_app/core/database/dao/enviodiagnostico/",
        "core/database/src/main/kotlin/com/example/msp_app/core/database/migrations/Migration33to34.kt",
        // Telemetría + registrador + lector de la pantalla.
        "app/src/main/java/com/example/msp_app/core/diagnosticoenvio/",
        // La pantalla de pendientes.
        "app/src/main/java/com/example/msp_app/features/home/screens/Home.kt",
        // El depurador remoto de Room (consulta SQL libre).
        "app/src/main/java/com/example/msp_app/core/debug/"
    )

    private fun violaciones(fuentes: List<Pair<String, String>>): List<String> =
        fuentes.filter { (ruta, texto) ->
            simbolosDeLectura.containsMatchIn(texto) && permitidos.none { ruta.startsWith(it) }
        }.map { it.first }

    private fun fuentesReales(): List<Pair<String, String>> {
        val raiz = (if (File("src/main/java").isDirectory) File("..") else File(".")).canonicalFile
        return EscanerDeFuentes().archivos.map { f ->
            f.canonicalFile.relativeTo(raiz).invariantSeparatorsPath to f.readText()
        }
    }

    @Test
    fun `E-APP-063 nadie fuera de pantalla, depurador y telemetria lee envio_diagnostico`() {
        val fuentes = fuentesReales()
        assertEquals(
            "archivos que leen envio_diagnostico sin permiso",
            emptyList<String>(),
            violaciones(fuentes)
        )
    }

    /** Control positivo: la MISMA búsqueda sí encuentra los usos permitidos. */
    @Test
    fun `control positivo - el escaner ve los usos permitidos y caza uno prohibido`() {
        val fuentes = fuentesReales()
        val conLectura = fuentes.filter {
            simbolosDeLectura.containsMatchIn(
                it.second
            )
        }.map { it.first }
        assertTrue(
            conLectura.toString(),
            conLectura.any { it.endsWith("core/database/AppDatabase.kt") }
        )
        assertTrue(
            conLectura.toString(),
            conLectura.any { it.endsWith("features/home/screens/Home.kt") }
        )
        assertTrue(
            conLectura.toString(),
            conLectura.any {
                it.endsWith("dao/enviodiagnostico/EnvioDiagnosticoDao.kt")
            }
        )
        // El worker de visitas SÍ usa el lado de escritura (control de que el
        // escáner lo está leyendo) y NO el de lectura.
        val worker = fuentes.single { it.first.endsWith("workers/PendingVisitsWorker.kt") }
        assertTrue(worker.second.contains("EnvioDiagnostico.sink"))

        val inyectada = fuentes + (
            "app/src/main/java/com/example/msp_app/workers/PendingPaymentsWorker.kt" to
                "val f = AppDatabase.getInstance(c).envioDiagnosticoDao().obtener(\"pago\", id)"
            ) + ("core/upload/src/main/kotlin/X.kt" to "SELECT * FROM envio_diagnostico")
        assertEquals(
            listOf(
                "app/src/main/java/com/example/msp_app/workers/PendingPaymentsWorker.kt",
                "core/upload/src/main/kotlin/X.kt"
            ),
            violaciones(inyectada)
        )
    }
}
