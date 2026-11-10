package com.example.msp_app.feature.compartirproductos.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cuarenta nombres inventados con la forma de los de Microsip, de punta a punta (normalizar + partir).
 * Cada fila se revisó a mano. Lo que NO está en el diccionario sale tal cual (`C/CAB.` → «Con cab.»):
 * es a propósito, no se adivina.
 */
class TablaDeNombresTest {

    private val tabla: List<Triple<String, String, String?>> = listOf(
        Triple(
            "SALA ESQUINERA LEIDEN 3 PZAS GRIS OXFORD",
            "Sala Esquinera Leiden 3 Piezas Gris Oxford",
            null
        ),
        Triple(
            "RECAMARA TOSCANA MAT. C/CAB. TAPIZADA",
            "Recámara Toscana Matrimonial",
            "Con cab. tapizada"
        ),
        Triple("COMEDOR 6 SILLAS ROBLE C/CRISTAL", "Comedor 6 Sillas Roble", "Con cristal"),
        Triple("COLCHON ORTOPEDICO KS 1.80 M", "Colchón Ortopédico KS 1.80 m", null),
        Triple("COLCHON MAT. RESORTES ENSACADOS", "Colchón Matrimonial Resortes Ensacados", null),
        Triple("LITERA IND. C/ESCALERA Y CAJONES", "Litera Individual", "Con escalera y cajones"),
        Triple("ROPERO 3 PUERTAS C/ESPEJO, CAFE", "Ropero 3 Puertas", "Con espejo, cafe"),
        Triple("BURO 2 CAJ. MADERA - NOGAL", "Buró 2 Cajones Madera", "Nogal"),
        Triple("CAMA IND. C/CAJ. (BLANCA)", "Cama Individual", "Con cajones blanca"),
        Triple("SOFA CAMA MATR. TELA GRIS", "Sofá Cama Matrimonial Tela Gris", null),
        Triple("ESTUFA 6 QUEMADORES ACERO INOX.", "Estufa 6 Quemadores Acero Inox.", null),
        Triple("REFRIGERADOR 14 PIES S/ESCARCHA", "Refrigerador 14 Pies sin Escarcha", null),
        Triple("TV 55\" LED SMART", "TV 55\" LED Smart", null),
        Triple("MESA DE CENTRO REDONDA", "Mesa de Centro Redonda", null),
        Triple("MESA DE NOCHE C/1 CAJON", "Mesa de Noche", "Con 1 cajón"),
        Triple("JUEGO DE SABANAS MAT.", "Juego de Sábanas Matrimonial", null),
        Triple("PEÑA COLONIAL SILLON ÑANDU", "Peña Colonial Sillón Ñandu", null),
        Triple("MUEBLE P/TV HASTA 65\"", "Mueble para TV Hasta 65\"", null),
        Triple("ESCRITORIO EJECUTIVO L C/LIBRERO", "Escritorio Ejecutivo L", "Con librero"),
        Triple("SILLA  PLEGABLE   NEGRA", "Silla Plegable Negra", null),
        Triple("ALMOHADA", "Almohada", null),
        Triple("CAJONERA 5 CAJ.", "Cajonera 5 Cajones", null),
        Triple("COMODA 6C BLANCA", "Cómoda 6C Blanca", null),
        Triple("BASE P/CAMA MAT. 1.40M", "Base para Cama Matrimonial 1.40M", null),
        Triple("LAVADORA 19 KG CARGA SUPERIOR", "Lavadora 19 kg Carga Superior", null),
        Triple("PERCHERO PZA", "Perchero Pieza", null),
        Triple("Sala Moderna Gris", "Sala Moderna Gris", null),
        Triple("sala moderna c/puff", "Sala moderna", "Con puff"),
        Triple("TAPETE 2.00 X 3.00 M", "Tapete 2.00 X 3.00 m", null),
        Triple("COCINETA 4 SILLAS - CRISTAL", "Cocineta 4 Sillas", "Cristal"),
        Triple(
            "CENTRO DE ENTRETENIMIENTO C/REPISAS Y 2 PUERTAS, ACABADO NOGAL",
            "Centro de Entretenimiento",
            "Con repisas y 2 puertas, acabado nogal"
        ),
        Triple("FUTON MED. C/COLCHON", "Futon Mediano", "Con colchón"),
        Triple("LAMPARA DE PIE MARMOL", "Lámpara de Pie Mármol", null),
        Triple("GABINETE METALICO 2 PUERTAS", "Gabinete Metálico 2 Puertas", null),
        Triple("A/C MINISPLIT 1 TON", "A/C Minisplit 1 Ton", null),
        Triple("ANTECOMEDOR 4 SILLAS(PLASTICO)", "Antecomedor 4 Sillas", "Plástico"),
        Triple("BANCO ALTO 2 PZS, NEGRO", "Banco Alto 2 Piezas", "Negro"),
        Triple("", "", null),
        Triple("   ", "", null),
        Triple("S/N", "S/n", null)
    )

    @Test fun `cada nombre da su titulo y su detalle`() {
        tabla.forEach { (original, titulo, detalle) ->
            val r = PartidorDeNombre.partir(original)
            assertEquals("título de «$original»", titulo, r.titulo)
            assertEquals("detalle de «$original»", detalle, r.detalle)
        }
    }

    @Test fun `ninguna letra o cifra del original se pierde fuera de lo que expande el diccionario`() {
        // Solo cuentan las cifras: ninguna abreviatura del diccionario las toca, así que deben sobrevivir todas.
        tabla.forEach { (original, titulo, detalle) ->
            val digitosOriginal = original.filter { it.isDigit() }
            val digitosSalida = (titulo + (detalle ?: "")).filter { it.isDigit() }
            assertEquals("cifras de «$original»", digitosOriginal, digitosSalida)
        }
    }
}
