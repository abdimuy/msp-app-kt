package com.example.msp_app.feature.compartirproductos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nombres inventados con la forma de los de Microsip (E-APP-180). */
class NormalizadorDeNombreTest {

    private fun igual(esperado: String, original: String) =
        assertEquals("«$original»", esperado, NormalizadorDeNombre.normalizar(original))

    // region — mayúsculas a tipo título

    @Test fun `mayusculas simples pasan a tipo titulo`() {
        igual("Sala Valencia Terciopelo Verde", "SALA VALENCIA TERCIOPELO VERDE")
        igual("Comedor Roble Natural", "COMEDOR ROBLE NATURAL")
        igual("Mesa Redonda", "MESA REDONDA")
    }

    @Test fun `articulos y preposiciones van en minuscula salvo al inicio`() {
        igual("Mesa de Centro", "MESA DE CENTRO")
        igual("Juego de Sala del Valle", "JUEGO DE SALA DEL VALLE")
        igual("Cama para Niños y Niñas", "CAMA PARA NIÑOS Y NIÑAS")
        igual("El Rey de la Casa", "EL REY DE LA CASA")
        igual("Un Sillón en una Sala", "UN SILLON EN UNA SALA")
    }

    @Test fun `la Ñ y los acentos que ya vienen se conservan`() {
        igual("Cama Niño Azul", "CAMA NIÑO AZUL")
        igual("Cañón Dorado", "CAÑÓN DORADO")
        igual("Peña y Cañada", "PEÑA Y CAÑADA")
        igual("Cómoda Blanca", "CÓMODA BLANCA")
    }

    @Test fun `una palabra y vacios`() {
        igual("Colchón", "COLCHON")
        igual("Sala", "SALA")
        igual("", "")
        igual("", "   ")
        igual("", "\t \n")
    }

    @Test fun `espacios dobles y de los bordes se limpian`() {
        igual("Sala Valencia", "  SALA   VALENCIA  ")
        igual("Mesa de Centro", "MESA\tDE\u00A0\u00A0CENTRO")
    }

    @Test fun `NFC se aplica a letras con tilde descompuesta`() {
        val descompuesto = "C" + "O\u0301" + "MODA" // O + tilde combinada
        val r = NormalizadorDeNombre.normalizar(descompuesto)
        assertEquals("Cómoda", r)
    }
    // endregion

    // region — abreviaturas del diccionario

    @Test fun `abreviaturas conocidas se expanden`() {
        igual("Cama Matrimonial", "CAMA MAT.")
        igual("Cama Matrimonial", "CAMA MATR.")
        igual("Cama Matrimonial", "CAMA MAT")
        igual("Cama Individual", "CAMA IND.")
        igual("Cama Individual", "CAMA IND")
        igual("Sala 3 Piezas", "SALA 3 PZAS")
        igual("Sala 3 Piezas", "SALA 3 PZS")
        igual("Sala 3 Piezas", "SALA 3 PZAS.")
        igual("Sala 1 Pieza", "SALA 1 PZA")
        igual("Cómoda Doble", "COMODA DOB.")
        igual("Mesa Grande", "MESA GDE.")
        igual("Mesa Mediano", "MESA MED.")
    }

    @Test fun `C diagonal y S diagonal y P diagonal expanden con sin para`() {
        igual("Cama con Cajones", "CAMA C/CAJ.")
        igual("Cama con Cajones", "CAMA C/CAJ")
        igual("Cama con Cabecera", "CAMA C/CABECERA")
        igual("Cama sin Colchón", "CAMA S/COLCHON")
        igual("Cama sin Colchón", "CAMA S/ COLCHON")
        igual("Silla para Niño", "SILLA P/NIÑO")
        igual("Cama con Cajones Matrimonial", "CAMA C/CAJ. MAT.")
    }

    @Test fun `C diagonal suelto expande y C diagonal U se conserva`() {
        igual("Cama con Base", "CAMA C/ BASE")
        igual("Vaso 6 c/u", "VASO 6 C/U")
        igual("Pieza s/n", "PIEZA S/N")
    }

    @Test fun `lo que no esta en el diccionario no se adivina`() {
        igual("Sala 6C", "SALA 6C")
        igual("Cama KS", "CAMA KS") // sigla conocida: se queda en mayúscula, no se expande
        igual("Cama Xyz", "CAMA XYZ")
        igual("Rec Toscana", "REC TOSCANA")
        igual("Comb Sala", "COMB SALA")
        igual("Ext Mesa", "EXT MESA")
        igual("Col Fina", "COL FINA")
    }

    @Test fun `abreviatura pegada a coma o parentesis se expande`() {
        igual("Cama Matrimonial, Blanca", "CAMA MAT., BLANCA")
        igual("Cama Matrimonial,", "CAMA MAT.,")
        igual("Cama (Individual)", "CAMA (IND.)")
    }
    // endregion

    // region — números y medidas

    @Test fun `cifras con letra se conservan en mayuscula`() {
        igual("Sala 6C", "SALA 6C")
        igual("Recámara 3P", "RECAMARA 3P")
        igual("Silla 4X4", "SILLA 4X4")
    }

    @Test fun `medidas pegadas o sueltas`() {
        igual("Cama 1.20 m", "CAMA 1.20 M")
        igual("Cama 1.20 mts", "CAMA 1.20 MTS")
        // Pegada («1.20M») lleva cifra: se conserva, no se adivina.
        igual("Cama 1.20M", "CAMA 1.20M")
        igual("Tapete 2.5 X 3 m", "TAPETE 2.5 X 3 M")
    }

    @Test fun `cifras fraccion y decimales`() {
        igual("Mesa 1/2 Redonda", "MESA 1/2 REDONDA")
        igual("Cable 2,5 mm", "CABLE 2,5 MM")
        igual("TV 32\"", "TV 32\"")
    }
    // endregion

    // region — acentos que se ponen y los que no

    @Test fun `las palabras del diccionario de acentos reciben tilde`() {
        igual("Recámara Toscana", "RECAMARA TOSCANA")
        igual("Recámaras", "RECAMARAS")
        igual("Sofá Cama", "SOFA CAMA")
        igual("Buró Doble", "BURO DOBLE")
        igual("Cajón", "CAJON")
        igual("Sillón Reclinable", "SILLON RECLINABLE")
        igual("Lámpara de Mesa", "LAMPARA DE MESA")
    }

    @Test fun `una palabra sin tilde que no esta en el diccionario se deja`() {
        igual("Escritorio Ejecutivo", "ESCRITORIO EJECUTIVO")
        igual("Camara", "CAMARA")
    }
    // endregion

    // region — guiones y paréntesis

    @Test fun `guion dentro de palabra capitaliza ambas partes`() {
        igual("Sofá-Cama Gris", "SOFA-CAMA GRIS")
        igual("Porta-Tv", "PORTA-TV")
    }

    @Test fun `guion suelto y parentesis conservan su forma`() {
        igual("Sala - Verde", "SALA - VERDE")
        igual("Cama (Blanca)", "CAMA (BLANCA)")
        igual("Cama (Blanca) Grande", "CAMA (BLANCA) GRANDE")
    }

    @Test fun `siglas se quedan en mayuscula`() {
        igual("Mueble TV", "MUEBLE TV")
        igual("Mesa MDF", "MESA MDF")
        igual("Lámpara LED", "LAMPARA LED")
    }
    // endregion

    // region — nombres que ya traen minúsculas

    @Test fun `un nombre con minusculas respeta su caso y expande abreviaturas`() {
        igual("Sala Valencia", "Sala Valencia")
        igual("Cama matrimonial", "Cama mat.")
        igual("Sala valencia con cajones", "Sala valencia c/caj.")
    }

    @Test fun `un nombre mixto con pocas minusculas cuenta como mayusculas`() {
        assertTrue(NormalizadorDeNombre.esMayusculas("SALA VALENCIA"))
        assertTrue(NormalizadorDeNombre.esMayusculas("SALA 3 PZAS"))
        assertFalse(NormalizadorDeNombre.esMayusculas("Sala Valencia"))
        assertFalse(NormalizadorDeNombre.esMayusculas("123 456"))
        assertFalse(NormalizadorDeNombre.esMayusculas(""))
    }
    // endregion

    // region — extremos

    @Test fun `una palabra larguisima sin espacios no revienta`() {
        val larga = "A".repeat(300)
        val r = NormalizadorDeNombre.normalizar(larga)
        assertEquals("A" + "a".repeat(299), r)
    }

    @Test fun `nombre de trescientos caracteres con espacios`() {
        val nombre = List(60) { "SALON" }.joinToString(" ")
        val r = NormalizadorDeNombre.normalizar(nombre)
        assertEquals(List(60) { "Salon" }.joinToString(" "), r)
    }

    @Test fun `solo puntuacion o numeros`() {
        igual("---", "---")
        igual("1234", "1234")
        igual("()", "()")
    }

    @Test fun `la normalizacion es idempotente`() {
        listOf(
            "SALA VALENCIA 3 PZAS C/CAJ. MAT.",
            "CAMA MAT.",
            "MESA DE CENTRO",
            "Sala Valencia"
        ).forEach {
            val una = NormalizadorDeNombre.normalizar(it)
            assertEquals(una, NormalizadorDeNombre.normalizar(una))
        }
    }
    // endregion

    // region — a tipo oración

    @Test fun `aOracion baja todo salvo la inicial siglas y cifras`() {
        assertEquals(
            "Con cabecera tapizada",
            NormalizadorDeNombre.aOracion("Con Cabecera Tapizada")
        )
        assertEquals("Mueble para TV 32", NormalizadorDeNombre.aOracion("Mueble Para TV 32"))
        assertEquals("Cómoda de 6C", NormalizadorDeNombre.aOracion("Cómoda de 6C"))
        assertEquals("", NormalizadorDeNombre.aOracion("   "))
    }
    // endregion
}
