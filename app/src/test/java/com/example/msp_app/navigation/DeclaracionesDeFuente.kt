package com.example.msp_app.navigation

import java.io.File

/**
 * **Cómo se parte un archivo Kotlin en funciones de nivel superior, una sola vez.**
 *
 * Lo comparten las dos redes que barren las fuentes y deciden algo **por
 * función**: [CadaPantallaMspProveeSuTemaTest] (¿esta pantalla provee su tema?)
 * y [CadaPantallaConTemaAnimaElCambioTest] (¿lo provee animando el cambio?).
 *
 * ## Por qué por función y no por archivo, que es el verde falso que costó
 *
 * La primera versión de la red del tema decidía por **archivo**: aceptaba
 * «llama a un composable declarado en un archivo que llama a `MspTheme`». Medido
 * sobre 749 archivos de producción, eso derivaba 59 proveedores y más de 40 eran
 * hojas —`Aviso`, `Cargando`, `ListaVacia`— que no proveen nada: entraban al
 * conjunto por vivir en el mismo archivo que una pantalla envuelta. Con eso, una
 * pantalla nueva escrita al lado de una envuelta pasaba en verde.
 *
 * Extraerlo acá es lo que evita que la segunda red repita el mismo error por su
 * cuenta: hay un solo parser y un solo criterio de qué cuenta como código.
 */
internal data class Declaracion(val nombre: String, val texto: String)

/** Arranque de una declaración de nivel superior (columna cero). */
private val DECLARACION =
    Regex("""^(?:internal |private |public )?fun ([A-Za-z][A-Za-z0-9]*)\(""")

/** Cualquier otra cosa de nivel superior: cierra la declaración anterior. */
private val OTRO_NIVEL_SUPERIOR = Regex(
    """^(?:@|(?:internal |private |public |abstract |open |data |sealed )*""" +
        """(?:val|var|class|object|interface|enum|typealias|fun)\b)"""
)

/** Parte [codigo] en declaraciones de nivel superior. */
internal fun declaracionesDe(codigo: String): List<Declaracion> {
    val declaraciones = mutableListOf<Declaracion>()
    var nombre: String? = null
    val cuerpo = StringBuilder()
    codigo.lineSequence().forEach { linea ->
        val inicio = DECLARACION.find(linea)
        if (inicio != null || OTRO_NIVEL_SUPERIOR.containsMatchIn(linea)) {
            nombre?.let { declaraciones += Declaracion(it, cuerpo.toString()) }
            cuerpo.setLength(0)
            nombre = inicio?.groupValues?.get(1)
        }
        if (nombre != null) cuerpo.appendLine(linea)
    }
    nombre?.let { declaraciones += Declaracion(it, cuerpo.toString()) }
    return declaraciones
}

/**
 * El código sin comentarios ni KDoc.
 *
 * Este plan documenta en el código los defectos que mató —el KDoc de
 * `CollectionReportScreen` NOMBRA a `MspTheme` sin llamarlo, y el de
 * `DetalleClienteScreen` NOMBRA a `MspThemeRevealHost` explicando el defecto—,
 * así que contar comentarios volvería verde a una pantalla por explicar el bug
 * en vez de arreglarlo.
 */
internal fun codigoDe(archivo: File): String = archivo.readLines()
    .filterNot { linea ->
        val limpia = linea.trim()
        limpia.startsWith("//") || limpia.startsWith("*") || limpia.startsWith("/*")
    }
    .joinToString("\n")

/** Una llamada a [nombre]: `Nombre(` o `Nombre {`, sin comerse `OtroNombre(`. */
internal fun invocacionDe(nombre: String) =
    Regex("(?<![A-Za-z0-9_])" + Regex.escape(nombre) + """\s*[({]""")

/** `MspTheme(...)` o `MspTheme { ... }` — las DOS formas de llamada. */
internal val INVOCA_TEMA = Regex("""(?<![.A-Za-z0-9_])MspTheme\s*[({]""")

/** La ranura de contenido que distingue un envoltorio de una hoja. */
internal val RANURA = Regex("""@Composable\s*\(\s*\)\s*->\s*Unit""")

/** Lo que este repo llama pantalla. */
internal val ES_PANTALLA = Regex("""[A-Z][A-Za-z0-9]*Screen""")
