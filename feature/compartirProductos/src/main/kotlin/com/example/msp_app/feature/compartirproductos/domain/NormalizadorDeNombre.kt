package com.example.msp_app.feature.compartirproductos.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Convierte el nombre de Microsip (`SALA VALENCIA 3 PZAS C/CAJ. MAT.`) en texto
 * legible para una persona. Solo presentación: el nombre guardado no cambia.
 *
 * Qué hace, en orden:
 *  1. NFC y espacios dobles fuera.
 *  2. Expande abreviaturas **del diccionario** ([ABREVIATURAS], [PREFIJOS]). Lo que
 *     no está ahí se deja tal cual: no se adivina (`6C`, `KS`, `C/U` quedan igual).
 *  3. Si el nombre venía en MAYÚSCULAS lo pasa a tipo título, con artículos y
 *     preposiciones en minúscula. Si ya traía minúsculas mezcladas, respeta el caso.
 *  4. Pone la tilde solo en las palabras de [ACENTOS] (sin tilde en origen no se
 *     puede saber; una lista corta y verificada vale más que una regla que adivina).
 */
@Suppress(
    "TooManyFunctions"
) // una función pequeña por regla del diccionario (abreviatura, caso, acento, siglas…)
object NormalizadorDeNombre {

    private val ES = Locale("es", "MX")

    /** Clave sin el punto final: `MAT.` y `MAT` valen lo mismo. */
    private val ABREVIATURAS = mapOf(
        "MAT" to "matrimonial",
        "MATR" to "matrimonial",
        "IND" to "individual",
        "PZA" to "pieza",
        "PZAS" to "piezas",
        "PZS" to "piezas",
        "CAJ" to "cajones",
        "GDE" to "grande",
        "MED" to "mediano",
        "DOB" to "doble"
    )

    /** `C/CAJ.` = «con cajones»: el prefijo se separa y lo demás se expande solo. */
    private val PREFIJOS = mapOf("C/" to "con", "S/" to "sin", "P/" to "para")

    /** Se escriben así en minúscula y no se tocan. */
    private val CONSERVAR = mapOf("C/U" to "c/u", "S/N" to "s/n")

    private val ACENTOS = mapOf(
        "RECAMARA" to "recámara",
        "RECAMARAS" to "recámaras",
        "SOFA" to "sofá",
        "SOFAS" to "sofás",
        "COMODA" to "cómoda",
        "COMODAS" to "cómodas",
        "BURO" to "buró",
        "BUROS" to "burós",
        "CAJON" to "cajón",
        "COLCHON" to "colchón",
        "SILLON" to "sillón",
        "COJIN" to "cojín",
        "LAMPARA" to "lámpara",
        "LAMPARAS" to "lámparas",
        "TELEVISION" to "televisión",
        "MARMOL" to "mármol",
        "PLASTICO" to "plástico",
        "METALICO" to "metálico",
        "ELECTRICO" to "eléctrico",
        "ORTOPEDICO" to "ortopédico",
        "SABANAS" to "sábanas",
        "EDREDON" to "edredón"
    )

    private val MINUSCULAS = setOf(
        "de", "del", "la", "las", "el", "los", "un", "una", "unos", "unas",
        "y", "e", "o", "u", "a", "al", "con", "sin", "para", "por", "en", "sobre", "entre", "c/u", "s/n"
    )

    /** Siglas que se quedan en mayúscula. */
    private val SIGLAS =
        setOf("TV", "LED", "LCD", "USB", "MDF", "PVC", "HD", "DVD", "XL", "XXL", "WIFI", "LP", "PU", "KS", "QS", "A/C", "LG", "HP", "JBL", "RCA")

    /** Unidad suelta que sigue a una cifra: `1.20 M` → `1.20 m`. */
    private val UNIDADES =
        setOf("m", "mt", "mts", "cm", "mm", "kg", "gr", "l", "lt", "lts", "w", "v", "hp", "plg")

    private const val PUNTUACION_INICIAL = "([¡¿\"'"
    private const val PUNTUACION_FINAL = ")],;:!?\"'"

    fun normalizar(nombre: String): String {
        val limpio = Normalizer.normalize(
            nombre,
            Normalizer.Form.NFC
        ).trim()
            .replace(Regex("[\\s\\u00A0]+"), " ")
            .replace(Regex("(?<=\\S)\\("), " (") // «SILLAS(PLASTICO)» → «SILLAS (PLASTICO)»
            .replace(Regex("\\)(?=[^\\s,.;:)])"), ") ")
        if (limpio.isEmpty()) return ""
        val enMayusculas = esMayusculas(limpio)
        val contexto = tieneContextoDeCama(limpio)
        val palabras = mutableListOf<String>()
        limpio.replace(PEGADA, "$1. ").split(' ').forEach { token ->
            val anterior = palabras.lastOrNull()?.let { partirPuntuacion(it).second }
            expandir(
                token,
                contexto,
                anterior
            ).split(' ').filter { it.isNotEmpty() }.forEach { palabras += it }
        }
        return palabras.mapIndexed { i, palabra ->
            darCaso(
                palabra,
                primera = i == 0,
                tras = palabras.getOrNull(i - 1),
                enMayusculas = enMayusculas,
                ultima = i == palabras.lastIndex
            )
        }.joinToString(" ")
    }

    /** `MAT.CON` pegado: la abreviatura del diccionario seguida de letras se separa en dos palabras. */
    private val PEGADA =
        Regex(
            "\\b(MAT|MATR|IND|CAJ|PZAS|PZS|PZA|DOB|GDE|MED)\\.(?=\\p{L})",
            RegexOption.IGNORE_CASE
        )

    /** Palabras que vuelven inequívoco a «MAT» como «matrimonial» (en otro contexto puede ser «mate», «material»…). */
    private val CONTEXTO_DE_CAMA = setOf("CAMA", "COLCHON", "RECAMARA", "BASE", "BOX")

    private fun tieneContextoDeCama(texto: String): Boolean {
        val sinTildes = Normalizer.normalize(
            texto.uppercase(ES),
            Normalizer.Form.NFD
        ).replace(Regex("\\p{M}+"), "")
        return sinTildes.split(Regex("[^A-Z]+")).any { it in CONTEXTO_DE_CAMA }
    }

    /** Con «1» delante no se pluraliza: «1 CAJ.» → «1 cajón». */
    private val SINGULAR = mapOf("CAJ" to "cajón", "PZAS" to "pieza", "PZS" to "pieza")

    /**
     * Pasa a tipo oración lo que ya está en tipo título (el detalle: «Con cabecera tapizada»).
     * Respeta siglas, cifras y unidades.
     */
    fun aOracion(texto: String): String {
        val palabras = texto.trim().split(' ').filter { it.isNotEmpty() }
        val bajas = palabras.mapIndexed { i, palabra ->
            val (inicio, nucleo, fin) = partirPuntuacion(palabra)
            val letraSuelta =
                esLetraSuelta(nucleo, palabras.getOrNull(i - 1), i == palabras.lastIndex)
            val nuevo = when {
                nucleo.any { it.isDigit() } -> nucleo
                letraSuelta -> nucleo.uppercase(ES)
                nucleo.uppercase(ES) in SIGLAS -> nucleo.uppercase(ES)
                '-' in nucleo -> nucleo.split('-').joinToString("-") { parteEnOracion(it) }
                else -> nucleo.lowercase(ES)
            }
            inicio + nuevo + fin
        }
        return capitalizarPrimera(bajas.joinToString(" "))
    }

    private fun parteEnOracion(parte: String): String =
        if (parte.uppercase(ES) in SIGLAS) parte.uppercase(ES) else parte.lowercase(ES)

    /** Una letra sola que cierra el nombre o sigue a «tipo»/«en» (`Tipo A`, `en U`) es una etiqueta, no una palabra. */
    private fun esLetraSuelta(nucleo: String, tras: String?, ultima: Boolean): Boolean {
        if (nucleo.length != 1 || !nucleo[0].isLetter()) return false
        val previa = tras?.let { partirPuntuacion(it).second.lowercase(ES) }
        return ultima || previa == "tipo" || previa == "en"
    }

    fun esMayusculas(texto: String): Boolean {
        val letras = texto.count { it.isLetter() }
        if (letras == 0) return false
        val minusculas = texto.count { it.isLetter() && it.isLowerCase() }
        return minusculas * UMBRAL_DENOMINADOR < letras
    }

    private const val UMBRAL_DENOMINADOR = 5 // menos de 1 de cada 5 letras en minúscula = venía gritando

    private fun expandir(token: String, contexto: Boolean, anterior: String?): String {
        val (inicio, nucleo, fin) = partirPuntuacion(token)
        val clave = nucleo.uppercase(ES)
        val prefijo = PREFIJOS.entries.firstOrNull {
            clave.startsWith(it.key) && clave.length > it.key.length
        }
        val expandida = when {
            clave in CONSERVAR -> CONSERVAR.getValue(clave)
            prefijo != null -> "${prefijo.value} ${expandir(
                nucleo.substring(prefijo.key.length) + fin,
                contexto,
                null
            )}"
            clave in PREFIJOS -> PREFIJOS.getValue(clave)
            else -> abreviatura(clave.removeSuffix("."), contexto, anterior)
        }
        return when {
            expandida == null -> token
            // En el prefijo, `fin` ya viajó dentro de la llamada recursiva: no se repite.
            prefijo != null && clave !in CONSERVAR -> inicio + expandida
            else -> inicio + expandida + fin
        }
    }

    private fun abreviatura(clave: String, contexto: Boolean, anterior: String?): String? = when {
        clave == "MAT" && !contexto -> null // sin contexto de cama no se adivina
        anterior == "1" && clave in SINGULAR -> SINGULAR.getValue(clave)
        else -> ABREVIATURAS[clave]
    }

    private fun darCaso(
        palabra: String,
        primera: Boolean,
        tras: String?,
        enMayusculas: Boolean,
        ultima: Boolean
    ): String {
        val (inicio, nucleo, fin) = partirPuntuacion(palabra)
        if (nucleo.isEmpty()) return palabra
        val nuevo = when {
            nucleo.any { it.isDigit() } -> nucleo.uppercaseSiMezcla(enMayusculas)
            esUnidadTrasCifra(nucleo, tras) -> nucleo.lowercase(ES)
            !enMayusculas -> conAcento(nucleo)
            esLetraSuelta(nucleo, tras, ultima) -> nucleo.uppercase(ES)
            nucleo.uppercase(ES) in SIGLAS -> nucleo.uppercase(ES)
            !primera && nucleo.lowercase(ES) in MINUSCULAS -> nucleo.lowercase(ES)
            else -> nucleo.split(
                '-'
            ).joinToString("-") {
                if (it.uppercase(ES) in SIGLAS) {
                    it.uppercase(
                        ES
                    )
                } else {
                    capitalizarPrimera(conAcento(it.lowercase(ES)))
                }
            }
        }
        return inicio + (if (primera) capitalizarPrimera(nuevo) else nuevo) + fin
    }

    private fun String.uppercaseSiMezcla(enMayusculas: Boolean): String = if (enMayusculas) {
        uppercase(
            ES
        )
    } else {
        this
    }

    private fun esUnidadTrasCifra(nucleo: String, tras: String?): Boolean {
        if (tras == null) return false
        val cifra = partirPuntuacion(tras).second
        return nucleo.lowercase(ES) in UNIDADES && cifra.isNotEmpty() && cifra.all {
            it.isDigit() || it == '.' || it == ','
        }
    }

    private fun conAcento(palabra: String): String {
        val acentuada = ACENTOS[palabra.uppercase(ES)] ?: return palabra
        return if (palabra.first().isUpperCase()) capitalizarPrimera(acentuada) else acentuada
    }

    /** Sube la primera letra, pero solo si abre el texto: en `6 sillas` la `s` no es inicio. */
    internal fun capitalizarPrimera(texto: String): String {
        val i = texto.indexOfFirst { it.isLetterOrDigit() }
        if (i < 0 || !texto[i].isLetter()) return texto
        return texto.substring(0, i) + texto[i].uppercase(ES) + texto.substring(i + 1)
    }

    private fun partirPuntuacion(token: String): Triple<String, String, String> {
        val inicio = token.takeWhile { it in PUNTUACION_INICIAL }
        val resto = token.substring(inicio.length)
        val fin = resto.takeLastWhile { it in PUNTUACION_FINAL }
        return Triple(inicio, resto.substring(0, resto.length - fin.length), fin)
    }
}
