package com.example.msp_app

import com.example.msp_app.core.visitas.capacidades.CapacidadesDeVisitas
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec visitas §4.2-9 (H15, E-APP-073): Auto Backup no respalda ni transfiere la
 * base de Room, ni las preferencias que solo tienen sentido junto a ESA base.
 *
 * Restaurar `msp_db` en un teléfono reasignado le daría la base de otro cobrador;
 * restaurar `visitas_poda` sin la base deja anotado "ya se reportó hasta X" para
 * filas que este teléfono nunca reportó (`VisitsLocalDataSource.deleteUploadedVisits`
 * poda las resguardadas hasta esa marca).
 *
 * Se quitan los comentarios antes de buscar, para que una exclusión que quedó
 * dentro de un comentario —como en las reglas de ejemplo de Android Studio— no
 * cuente. A propósito sin `DocumentBuilderFactory`: con él, la suite completa de
 * `:app` (una sola JVM) dejó en rojo dos veces cuatro pruebas Robolectric de
 * navegación (`GooglePlayServicesMissingManifestValueException`); sin él, verde.
 * La causa no se aisló.
 */
class ReglasDeRespaldoTest {

    private fun sinComentarios(ruta: String) =
        File(ruta).readText().replace(Regex("""<!--[\s\S]*?-->"""), "")

    private fun reglas(nombre: String) = sinComentarios("src/main/res/xml/$nombre")

    private val atributo = Regex("""([\w:]+)\s*=\s*"([^"]*)"""")

    private fun atributos(texto: String): Map<String, String> =
        atributo.findAll(texto).associate { it.groupValues[1] to it.groupValues[2] }

    /** `<exclude .../>` dentro de [cuerpo], como pares `dominio:ruta`. */
    private fun exclusiones(cuerpo: String): Set<String> =
        Regex("""<exclude\b([^>]*)/>""").findAll(cuerpo).map { m ->
            val attrs = atributos(m.groupValues[1])
            "${attrs["domain"]}:${attrs["path"]}"
        }.toSet()

    /** Cuerpo del único `<nombre>...</nombre>` de [xml]. */
    private fun seccionUnica(xml: String, nombre: String): String {
        val secciones = Regex("""<$nombre\b[^>]*>([\s\S]*?)</$nombre>""").findAll(xml).toList()
        assertEquals("debe haber exactamente un <$nombre>", 1, secciones.size)
        return secciones.single().groupValues[1]
    }

    @Test
    fun `E-APP-073 la base de Room no entra en respaldo en la nube ni en transferencia (API 31+)`() {
        val raiz = reglas("data_extraction_rules.xml")
        for (seccion in listOf("cloud-backup", "device-transfer")) {
            assertTrue(
                "<$seccion> no excluye database:.",
                "database:." in exclusiones(seccionUnica(raiz, seccion))
            )
        }
    }

    @Test
    fun `E-APP-073 API menor a 31 tambien excluye la base`() {
        val raiz = seccionUnica(reglas("backup_rules.xml"), "full-backup-content")
        assertTrue("<full-backup-content> no excluye database:.", "database:." in exclusiones(raiz))
    }

    @Test
    fun `E-APP-073 las marcas de la poda y la conciliacion de visitas no se restauran sin su base`() {
        val marcas = setOf(
            // MemoriaDePodaDeResguardadas.kt (P1 Tarea 12)
            "sharedpref:visitas_poda.xml",
            // UltimaConciliacionDeVisitas.kt (P1 Tarea 13)
            "sharedpref:visitas_conciliacion_ultima.xml"
        )
        val api31 = reglas("data_extraction_rules.xml")
        val secciones = listOf(
            "cloud-backup" to exclusiones(seccionUnica(api31, "cloud-backup")),
            "device-transfer" to exclusiones(seccionUnica(api31, "device-transfer")),
            "full-backup-content" to exclusiones(seccionUnica(reglas("backup_rules.xml"), "full-backup-content"))
        )
        for ((nombre, excluidas) in secciones) {
            assertTrue("<$nombre> no excluye ${marcas - excluidas}", excluidas.containsAll(marcas))
        }
    }

    /**
     * E-APP-097 (P2 Tarea 15): la capacidad negociada es del servidor al que habla
     * ESTE aparato. Restaurada en otro con su marca de refresco, le mandaría
     * hasta 6 h campos que su servidor quizá rechaza con 422.
     */
    @Test
    fun `E-APP-097 la capacidad negociada de visitas no se restaura en otro aparato`() {
        val marca = "sharedpref:${CapacidadesDeVisitas.PREFS}.xml"
        val api31 = reglas("data_extraction_rules.xml")
        listOf(
            "cloud-backup" to exclusiones(seccionUnica(api31, "cloud-backup")),
            "device-transfer" to exclusiones(seccionUnica(api31, "device-transfer")),
            "full-backup-content" to exclusiones(seccionUnica(reglas("backup_rules.xml"), "full-backup-content"))
        ).forEach { (nombre, excluidas) ->
            assertTrue(
                "<$nombre> no excluye $marca",
                marca in excluidas
            )
        }
    }

    @Test
    fun `E-APP-073 el manifest usa estas dos reglas`() {
        val manifest = sinComentarios("src/main/AndroidManifest.xml")
        val app = Regex("""<application\b([^>]*)>""").findAll(manifest).single().groupValues[1]
        val attrs = atributos(app)
        assertEquals("@xml/data_extraction_rules", attrs["android:dataExtractionRules"])
        assertEquals("@xml/backup_rules", attrs["android:fullBackupContent"])
    }
}
