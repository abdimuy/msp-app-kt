package com.example.msp_app.feature.compartirproductos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PartidorDeNombreTest {

    private fun parte(original: String, titulo: String, detalle: String?) {
        val r = PartidorDeNombre.partir(original)
        assertEquals("título de «$original»", titulo, r.titulo)
        assertEquals("detalle de «$original»", detalle, r.detalle)
    }

    @Test fun `sin separador todo es titulo`() {
        parte("SALA VALENCIA TERCIOPELO", "Sala Valencia Terciopelo", null)
        parte("COLCHON", "Colchón", null)
    }

    @Test fun `parte en con y el detalle empieza por Con`() {
        parte("RECAMARA TOSCANA CON CABECERA TAPIZADA", "Recámara Toscana", "Con cabecera tapizada")
        parte("COMEDOR ROBLE CON 6 SILLAS", "Comedor Roble", "Con 6 sillas")
    }

    @Test fun `C diagonal cuenta como con`() {
        parte("CAMA C/CAJ. MAT.", "Cama", "Con cajones matrimonial")
        parte(
            "SALA VALENCIA 3 PZAS C/CAJ. MAT. TERCIOPELO",
            "Sala Valencia 3 Piezas",
            "Con cajones mat. terciopelo"
        )
    }

    @Test fun `parte en la primera coma`() {
        parte("SALA ESQUINERA, GRIS OXFORD, 3 PZAS", "Sala Esquinera", "Gris oxford, 3 piezas")
    }

    @Test fun `parte en guion con espacios`() {
        parte("COMEDOR NOGAL - 6 SILLAS", "Comedor Nogal", "6 sillas")
        parte("COMEDOR NOGAL – 6 SILLAS", "Comedor Nogal", "6 sillas")
    }

    @Test fun `un guion dentro de palabra no parte`() {
        parte("SOFA-CAMA GRIS", "Sofá-Cama Gris", null)
    }

    @Test fun `parte en parentesis y los quita del detalle`() {
        parte("SALA VALENCIA (VERDE MUSGO)", "Sala Valencia", "Verde musgo")
        parte("SALA (VERDE) 3 PZAS", "Sala", "Verde 3 piezas")
    }

    @Test fun `gana el primer separador aunque haya otros despues`() {
        parte("SALA VALENCIA, CON PUFF (GRIS)", "Sala Valencia", "Con puff gris")
        parte("COMODA (BLANCA) C/ESPEJO", "Cómoda", "Blanca con espejo")
    }

    @Test fun `el separador al principio no parte porque el titulo no puede quedar vacio`() {
        parte("CON RUEDAS", "Con Ruedas", null)
        parte("(GRIS) SALA", "(Gris) Sala", null)
        parte(", SALA", ", Sala", null)
    }

    @Test fun `separador sin nada despues no deja detalle vacio`() {
        parte("SALA,", "Sala", null)
        parte("SALA (", "Sala", null)
        parte("SALA - ", "Sala", null)
    }

    @Test fun `nombres con minusculas conservan su caso y capitalizan el detalle`() {
        parte("Sala Valencia con cajones", "Sala Valencia", "Con cajones")
        parte("Cama king, blanca", "Cama king", "Blanca")
    }

    @Test fun `con dentro de otra palabra no parte`() {
        parte("CONSOLA CONTEMPORANEA", "Consola Contemporanea", null)
        parte("SILLA ICON", "Silla Icon", null)
    }

    @Test fun `vacios`() {
        val r = PartidorDeNombre.partir("   ")
        assertEquals("", r.titulo)
        assertNull(r.detalle)
        assertEquals(TituloYDetalle("", null), PartidorDeNombre.partir(""))
    }

    @Test fun `trescientos caracteres con coma`() {
        val largo = "PALABRA ".repeat(20) + ", " + "DETALLE ".repeat(30)
        val r = PartidorDeNombre.partir(largo)
        assertEquals("Palabra ".repeat(19) + "Palabra", r.titulo)
        assertEquals(
            ("Detalle ".repeat(30)).trim().let { "D" + it.substring(1).lowercase() },
            r.detalle
        )
    }

    @Test fun `el nombre completo original no se pierde en el titulo mas el detalle`() {
        // Titulo + detalle cubren el nombre: nada de texto se descarta salvo el separador.
        val r = PartidorDeNombre.partir("CAMA MAT. C/CAJ. BLANCA")
        assertEquals("Cama Matrimonial", r.titulo)
        assertEquals("Con cajones blanca", r.detalle)
    }
}
