@file:Suppress(
    "MagicNumber",
    "TooManyFunctions"
) // fábrica de datos: las probabilidades SON los datos

package com.example.msp_app.core.testing.padron

import com.example.msp_app.core.database.entities.ClienteEntity
import java.util.Random

/**
 * Padrón de clientes SINTÉTICO y determinista (semilla fija) para probar el buscador
 * contra un volumen y una distribución parecidos a los reales — E-APP-157.
 *
 * Todos los nombres son inventados a partir de listas de nombres y apellidos comunes;
 * ningún dato sale del padrón real. Lo único que se imita del real son las FRECUENCIAS:
 * en ~44,000 clientes, HERNANDEZ ronda los 4,200, MARIA los 4,000, SANCHEZ los 3,150,
 * MARTINEZ los 3,100, GARCIA los 2,700, JOSE los 1,800 y GUADALUPE los 1,300. Esa cola
 * pesada es justo lo que hacía fallar al buscador viejo: la palabra más larga de un nombre
 * completo suele ser común y el `LIMIT 300` se llenaba de homónimos.
 *
 * [normalizar] llega por parámetro para que la MISMA fábrica arme el padrón con la
 * normalización vieja (la que ya tienen guardada los teléfonos) y con la nueva.
 */
object PadronSintetico {

    const val SEMILLA_POR_DEFECTO = 20_261_110L
    const val TAMANO_REAL_APROXIMADO = 44_000

    fun generar(
        cantidad: Int = TAMANO_REAL_APROXIMADO,
        semilla: Long = SEMILLA_POR_DEFECTO,
        normalizar: (String) -> String
    ): List<ClienteEntity> {
        val azar = Random(semilla)
        val vistos = HashSet<Int>(cantidad * 2)
        return List(cantidad) { indice ->
            var id: Int
            do {
                id = 1_000 + azar.nextInt(9_000_000)
            } while (!vistos.add(id))
            val nombre = nombreCompleto(azar)
            ClienteEntity(
                CLIENTE_ID = id,
                NOMBRE = nombre,
                ESTATUS = if (indice % 17 == 0) "B" else "A",
                CAUSA_SUSP = null,
                NOMBRE_NORMALIZADO = normalizar(nombre)
            )
        }
    }

    /** Cuenta en cuántos nombres aparece [palabra] como palabra completa (sin acentos). */
    fun frecuencia(padron: List<ClienteEntity>, palabra: String): Int =
        padron.count { cliente -> sinAcentos(cliente.NOMBRE).split(' ').contains(palabra) }

    private fun nombreCompleto(azar: Random): String {
        val mujer = azar.nextBoolean()
        val partes = ArrayList<String>(5)
        val primero = primerNombre(azar, mujer)
        partes += primero
        if (azar.nextDouble() < if (mujer) 0.55 else 0.35) partes += segundoNombre(azar, mujer, primero)
        partes += apellido(azar)
        partes += apellido(azar)
        return partes.joinToString(" ") { conAcentoAlAzar(it, azar) }
    }

    private fun primerNombre(azar: Random, mujer: Boolean): String {
        if (mujer && azar.nextDouble() < 0.15) return "MARIA"
        if (!mujer && azar.nextDouble() < 0.078) return "JOSE"
        return elegir(azar, if (mujer) NOMBRES_MUJER else NOMBRES_HOMBRE)
    }

    private fun segundoNombre(azar: Random, mujer: Boolean, primero: String): String {
        val tirada = azar.nextDouble()
        // "MARIA GUADALUPE" es el par más común del padrón real: GUADALUPE sigue a MARIA a menudo.
        val guadalupe = when {
            primero == "MARIA" -> 0.35
            mujer -> 0.02
            else -> 0.045
        }
        return when {
            tirada < guadalupe -> "GUADALUPE"
            mujer && tirada < guadalupe + 0.065 -> "MARIA"
            else -> elegir(azar, if (mujer) NOMBRES_MUJER else NOMBRES_HOMBRE)
        }
    }

    private fun apellido(azar: Random): String {
        val base = apellidoBase(azar)
        val tirada = azar.nextDouble()
        return when {
            tirada < 0.025 -> "DE LA $base"
            tirada < 0.04 -> "DEL $base"
            tirada < 0.048 -> "DE LOS $base"
            tirada < 0.06 -> "DE $base"
            else -> base
        }
    }

    private fun apellidoBase(azar: Random): String {
        var tirada = azar.nextDouble() * 100.0
        for ((apellido, peso) in APELLIDOS_COMUNES) {
            tirada -= peso
            if (tirada < 0) return apellido
        }
        return apellidoRaro(azar)
    }

    /** Cola larga: miles de apellidos distintos, cada uno poco frecuente. */
    private fun apellidoRaro(azar: Random): String =
        SILABAS_INICIO[azar.nextInt(SILABAS_INICIO.size)] +
            SILABAS_MEDIO[azar.nextInt(SILABAS_MEDIO.size)] +
            SILABAS_FIN[azar.nextInt(SILABAS_FIN.size)]

    private fun elegir(azar: Random, lista: List<String>): String {
        // Zipf suave: los primeros de la lista salen más.
        val indice = (lista.size * Math.pow(azar.nextDouble(), 1.8)).toInt()
        return lista[indice.coerceIn(0, lista.size - 1)]
    }

    private fun conAcentoAlAzar(texto: String, azar: Random): String {
        if (azar.nextDouble() >= 0.35) return texto
        return texto.split(' ').joinToString(" ") { palabra -> ACENTOS[palabra] ?: palabra }
    }

    private fun sinAcentos(texto: String): String =
        java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
            .replace("\\p{M}".toRegex(), "")

    private val ACENTOS = mapOf(
        "MARIA" to "MARÍA", "JOSE" to "JOSÉ", "JESUS" to "JESÚS", "ANGEL" to "ÁNGEL",
        "HERNANDEZ" to "HERNÁNDEZ", "SANCHEZ" to "SÁNCHEZ", "MARTINEZ" to "MARTÍNEZ",
        "GARCIA" to "GARCÍA", "LOPEZ" to "LÓPEZ", "GONZALEZ" to "GONZÁLEZ",
        "PEREZ" to "PÉREZ", "RODRIGUEZ" to "RODRÍGUEZ", "RAMIREZ" to "RAMÍREZ",
        "GOMEZ" to "GÓMEZ", "DIAZ" to "DÍAZ", "JIMENEZ" to "JIMÉNEZ",
        "GUTIERREZ" to "GUTIÉRREZ", "ALVAREZ" to "ÁLVAREZ", "MENDEZ" to "MÉNDEZ",
        "VAZQUEZ" to "VÁZQUEZ", "MUÑOZ" to "MUÑOZ"
    )

    private val NOMBRES_MUJER = listOf(
        "ANA", "ROSA", "PATRICIA", "LAURA", "CARMEN", "LETICIA", "MARTHA",
        "SILVIA", "ELIZABETH", "ALEJANDRA", "VERONICA", "ARACELI", "CLAUDIA", "GABRIELA",
        "ADRIANA", "TERESA", "JUANA", "MARGARITA", "ELENA", "SOFIA", "DIANA", "YOLANDA",
        "ISABEL", "LUCIA", "BEATRIZ", "ESTELA", "IRMA", "ROCIO", "MONICA", "FERNANDA",
        "PAOLA", "CECILIA", "ALMA", "NORMA", "BERTHA", "OLGA", "LORENA", "ESPERANZA",
        "FLOR", "DOLORES", "CONCEPCION", "MIRIAM", "KARLA", "XIMENA", "DANIELA", "ITZEL",
        "JAZMIN", "BRENDA", "NAYELI", "YESENIA", "CITLALI", "AIDA", "ROSARIO", "REBECA"
    )

    private val NOMBRES_HOMBRE = listOf(
        "JUAN", "LUIS", "JESUS", "MIGUEL", "CARLOS", "FRANCISCO", "ANTONIO", "PEDRO",
        "JORGE", "ROBERTO", "MANUEL", "ALEJANDRO", "FERNANDO", "RICARDO", "DANIEL",
        "EDUARDO", "RAUL", "ARTURO", "SERGIO", "ENRIQUE", "RAFAEL", "OSCAR", "ALFREDO",
        "JAVIER", "VICTOR", "MARIO", "ERNESTO", "GERARDO", "ADRIAN", "HECTOR", "GUSTAVO",
        "ROGELIO", "IGNACIO", "SALVADOR", "ABEL", "ANGEL", "ALBERTO", "ARMANDO", "RUBEN",
        "FELIPE", "CESAR", "MARCOS", "ISAAC", "EMILIO", "TOMAS", "IVAN", "ULISES",
        "BRAYAN", "DIEGO", "SANTIAGO", "MATEO", "LEONARDO", "OMAR", "GILBERTO", "NOE"
    )

    // Porcentaje de PUESTOS de apellido (hay dos por cliente) que ocupa cada uno.
    private val APELLIDOS_COMUNES = listOf(
        "HERNANDEZ" to 4.9, "SANCHEZ" to 3.7, "MARTINEZ" to 3.6, "GARCIA" to 3.1,
        "LOPEZ" to 3.5, "GONZALEZ" to 3.2, "PEREZ" to 3.0, "RODRIGUEZ" to 2.9,
        "RAMIREZ" to 2.4, "FLORES" to 2.2, "GOMEZ" to 2.0, "DIAZ" to 1.8, "CRUZ" to 1.8,
        "REYES" to 1.7, "MORALES" to 1.7, "GUTIERREZ" to 1.6, "ORTIZ" to 1.5,
        "JIMENEZ" to 1.5, "TORRES" to 1.5, "RUIZ" to 1.5, "VAZQUEZ" to 1.4,
        "MENDOZA" to 1.4, "CASTILLO" to 1.3, "ROMERO" to 1.3, "ALVAREZ" to 1.2,
        "MENDEZ" to 1.2, "HERRERA" to 1.1, "AGUILAR" to 1.1, "VARGAS" to 1.0,
        "MEDINA" to 1.0, "CASTRO" to 1.0, "ROJAS" to 0.9, "RIVERA" to 0.9,
        "SANTIAGO" to 0.9, "JUAREZ" to 0.8, "RAMOS" to 0.8, "MUÑOZ" to 0.8,
        "GUZMAN" to 0.8, "SALAZAR" to 0.7, "PEÑA" to 0.7, "NUÑEZ" to 0.6,
        "CORTES" to 0.6, "DOMINGUEZ" to 0.6, "SOTO" to 0.6, "DELGADO" to 0.6,
        "VELAZQUEZ" to 0.6, "CAMPOS" to 0.5, "FUENTES" to 0.5, "NAVARRO" to 0.5,
        "ESPINOZA" to 0.5, "CERVANTES" to 0.5, "LUNA" to 0.5, "ACOSTA" to 0.4,
        "PACHECO" to 0.4, "BAUTISTA" to 0.4, "CARRILLO" to 0.4, "ZAMORA" to 0.4,
        "IBARRA" to 0.4, "SOLIS" to 0.4, "VEGA" to 0.4, "MIRANDA" to 0.4,
        "VALDEZ" to 0.3, "TREJO" to 0.3, "ESCOBAR" to 0.3, "ORTEGA" to 0.3,
        "CHAVEZ" to 0.3, "ROSAS" to 0.3, "ARELLANO" to 0.3, "TELLEZ" to 0.3,
        "ANGEL" to 0.2, "BRAVO" to 0.2, "CABRERA" to 0.2, "SANDOVAL" to 0.2
    )

    private val SILABAS_INICIO = listOf(
        "AL", "BA", "CO", "DE", "ES", "FA", "GA", "HU", "IL", "JA", "LA", "MA", "NA", "OR",
        "PA", "QUI", "RO", "SA", "TA", "UR", "VI", "ZA", "BE", "CA", "DO", "FE", "GO", "LE"
    )
    private val SILABAS_MEDIO = listOf(
        "BAR", "CAN", "DOR", "FAL", "GUE", "LIN", "MON", "NOS", "PIL", "RAN", "SEN", "TUR",
        "VAL", "ZUR", "MEN", "CHI", "TOR", "LLA", "BRE", "CRU", "DRA", "GRO", "PLA", "TRE"
    )
    private val SILABAS_FIN = listOf(
        "A", "O", "AS", "OS", "EZ", "AZ", "IZ", "ES", "ERO", "ANO", "ILLO", "ADO", "IDO",
        "ON", "AN", "ARA", "ERA", "ENA", "ITO", "UCCI"
    )
}
