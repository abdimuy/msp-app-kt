package com.example.msp_app.core.common.cobranza.domain.casos

import java.io.File
import java.security.MessageDigest

/**
 * Acceso al juego de casos compartido con msp-api
 * (`src/test/resources/estado-cuenta/`, copiado con
 * `scripts/casos-estado-cuenta.sh copiar`; nunca se edita a mano).
 */
internal object JuegoDeCasos {

    const val DIRECTORIO = "estado-cuenta"
    private const val VERSION = "VERSION"
    private val NOMBRE_CASO = Regex("^[0-9]{3}-[a-z0-9-]+\\.json$")

    fun directorio(): File {
        val url = requireNotNull(javaClass.classLoader?.getResource(DIRECTORIO)) {
            "no está src/test/resources/$DIRECTORIO en el classpath de prueba; corre scripts/casos-estado-cuenta.sh copiar"
        }
        require(
            url.protocol == "file"
        ) { "los casos deben leerse de disco, llegaron como ${url.protocol}" }
        return File(url.toURI())
    }

    /** Los .json del juego en orden de nombre (= `LC_ALL=C sort`, nombres solo ASCII). */
    fun archivosDeCaso(dir: File = directorio()): List<File> {
        val visibles = dir.listFiles().orEmpty().filterNot { it.name.startsWith(".") }
        val fueraDeFormato = visibles.filterNot { it.name == VERSION || NOMBRE_CASO.matches(it.name) }
        require(fueraDeFormato.isEmpty()) {
            "archivos fuera de formato en $DIRECTORIO: ${fueraDeFormato.map { it.name }.sorted()}"
        }
        return visibles.filter { NOMBRE_CASO.matches(it.name) }.sortedBy { it.name }
    }

    fun sha256(archivos: List<File>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        archivos.forEach { digest.update(it.readBytes()) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun versionDeclarada(dir: File = directorio()): String =
        File(dir, VERSION).readText(Charsets.US_ASCII).trim()
}
