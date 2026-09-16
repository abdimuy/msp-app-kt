package com.example.msp_app.core.speech.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.speech.domain.AvanceDeLaDescarga
import com.example.msp_app.core.speech.domain.EstadoDelDictado
import com.example.msp_app.core.speech.domain.EstadoDelModelo
import com.example.msp_app.core.speech.domain.FalloDelDictado
import com.example.msp_app.core.speech.domain.GrabacionDictada
import com.example.msp_app.core.speech.domain.ModeloDeDictado
import com.example.msp_app.core.speech.ui.CampoDictado
import com.example.msp_app.core.speech.ui.DescargaDelDictadoContenido
import com.example.msp_app.core.speech.ui.avisoDe
import org.junit.Test

/**
 * **Los dos dibujos nuevos del dictado**, en la matriz completa: claro ×
 * oscuro × 1.0/1.5/2.0.
 *
 * ## Qué hay que mirar en estas imágenes
 *
 * 1. Que el campo en **reposo** y el campo **escuchando** midan **lo mismo**.
 *    Es el principio 12 y es lo único que un assert no puede ver: dos capturas
 *    del mismo alto.
 * 2. Que a escala 2.0 el cronómetro no se coma la etiqueta, y que las barritas
 *    y el botón sigan dentro de su columna.
 * 3. Que el pie "sin descargar también puedes dictar" siga entero a 2.0 — es la
 *    frase que impide que el cobrador crea que el dictado cuesta 43.5 MB.
 */
class DictadoMatrixScreenshotTest : SpeechScreenshotTest() {

    @Test
    fun `campo reposo light normal`() = campoEnReposo(false, FontSizeLevel.NORMAL)

    @Test
    fun `campo reposo light grande`() = campoEnReposo(false, FontSizeLevel.GRANDE)

    @Test
    fun `campo reposo light muy grande`() = campoEnReposo(false, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `campo reposo dark normal`() = campoEnReposo(true, FontSizeLevel.NORMAL)

    @Test
    fun `campo reposo dark grande`() = campoEnReposo(true, FontSizeLevel.GRANDE)

    @Test
    fun `campo reposo dark muy grande`() = campoEnReposo(true, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `campo escuchando light normal`() = campoEscuchando(false, FontSizeLevel.NORMAL)

    @Test
    fun `campo escuchando light grande`() = campoEscuchando(false, FontSizeLevel.GRANDE)

    @Test
    fun `campo escuchando light muy grande`() = campoEscuchando(false, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `campo escuchando dark normal`() = campoEscuchando(true, FontSizeLevel.NORMAL)

    @Test
    fun `campo escuchando dark grande`() = campoEscuchando(true, FontSizeLevel.GRANDE)

    @Test
    fun `campo escuchando dark muy grande`() = campoEscuchando(true, FontSizeLevel.MUY_GRANDE)

    /**
     * El campo **sin permiso**: sin micrófono, con el aviso ámbar, y con el
     * texto igual de escribible. Es el estado donde la nota no se pierde.
     */
    @Test
    fun `campo sin permiso light`() = campoSinPermiso(false)

    @Test
    fun `campo sin permiso dark`() = campoSinPermiso(true)

    /** El campo con el audio ya adjunto: la regla dura, hecha pixel. */
    @Test
    fun `campo con audio light`() = campoConAudio(false)

    @Test
    fun `campo con audio dark`() = campoConAudio(true)

    @Test
    fun `descarga ausente light normal`() = descarga(
        EstadoDelModelo.Ausente,
        "ausente",
        false,
        FontSizeLevel.NORMAL
    )

    @Test
    fun `descarga ausente light grande`() = descarga(
        EstadoDelModelo.Ausente,
        "ausente",
        false,
        FontSizeLevel.GRANDE
    )

    @Test
    fun `descarga ausente light muy grande`() =
        descarga(EstadoDelModelo.Ausente, "ausente", false, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `descarga ausente dark normal`() = descarga(
        EstadoDelModelo.Ausente,
        "ausente",
        true,
        FontSizeLevel.NORMAL
    )

    @Test
    fun `descarga ausente dark grande`() = descarga(
        EstadoDelModelo.Ausente,
        "ausente",
        true,
        FontSizeLevel.GRANDE
    )

    @Test
    fun `descarga ausente dark muy grande`() =
        descarga(EstadoDelModelo.Ausente, "ausente", true, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `descarga bajando light normal`() = descarga(
        BAJANDO,
        "bajando",
        false,
        FontSizeLevel.NORMAL
    )

    @Test
    fun `descarga bajando light muy grande`() =
        descarga(BAJANDO, "bajando", false, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `descarga bajando dark normal`() = descarga(BAJANDO, "bajando", true, FontSizeLevel.NORMAL)

    @Test
    fun `descarga bajando dark muy grande`() =
        descarga(BAJANDO, "bajando", true, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `descarga lista light`() = descarga(
        EstadoDelModelo.Listo,
        "lista",
        false,
        FontSizeLevel.NORMAL
    )

    @Test
    fun `descarga lista dark`() = descarga(
        EstadoDelModelo.Listo,
        "lista",
        true,
        FontSizeLevel.NORMAL
    )

    private fun campoEnReposo(dark: Boolean, nivel: FontSizeLevel) = campo(
        nombre = "dictado_campo_reposo",
        dark = dark,
        nivel = nivel,
        texto = "",
        estado = EstadoDelDictado.Reposo,
        puedeDictar = true,
        grabacion = null,
        aviso = null
    )

    private fun campoEscuchando(dark: Boolean, nivel: FontSizeLevel) = campo(
        nombre = "dictado_campo_escuchando",
        dark = dark,
        nivel = nivel,
        texto = "Dice que hasta que le arreglen el refri",
        estado = EstadoDelDictado.Escuchando(
            parcial = "no paga",
            nivel = 0.8f,
            transcurridoMs = 6_000L
        ),
        puedeDictar = true,
        grabacion = null,
        aviso = null
    )

    private fun campoSinPermiso(dark: Boolean) = campo(
        nombre = "dictado_campo_sin_permiso",
        dark = dark,
        nivel = FontSizeLevel.NORMAL,
        texto = "",
        estado = EstadoDelDictado.Reposo,
        puedeDictar = false,
        grabacion = null,
        aviso = avisoDe(FalloDelDictado.SIN_PERMISO)
    )

    private fun campoConAudio(dark: Boolean) = campo(
        nombre = "dictado_campo_con_audio",
        dark = dark,
        nivel = FontSizeLevel.NORMAL,
        texto = "Dice que hasta que le arreglen el refri no paga",
        estado = EstadoDelDictado.Reposo,
        puedeDictar = true,
        grabacion = GrabacionDictada("g-1", "/datos/dictado.wav", 8_000L),
        aviso = null
    )

    @Suppress("LongParameterList") // es la matriz del campo: cada parámetro es un eje.
    private fun campo(
        nombre: String,
        dark: Boolean,
        nivel: FontSizeLevel,
        texto: String,
        estado: EstadoDelDictado,
        puedeDictar: Boolean,
        grabacion: GrabacionDictada?,
        aviso: String?
    ) {
        capture("${nombre}_${tema(dark)}_${sufijoDe(nivel)}", dark, nivel) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // El mismo margen de la pantalla de la visita: así el ancho
                    // del campo es el real y no uno de laboratorio.
                    .padding(MspTheme.spacing.md)
            ) {
                CampoDictado(
                    etiqueta = "Nota — opcional",
                    marcador = "Lo que dijo, en sus palabras",
                    texto = texto,
                    estado = estado,
                    puedeDictar = puedeDictar,
                    grabacion = grabacion,
                    aviso = aviso,
                    habilitado = true,
                    onTexto = {},
                    onMicrofono = {},
                    onQuitarAudio = {}
                )
            }
        }
    }

    private fun descarga(
        estado: EstadoDelModelo,
        nombre: String,
        dark: Boolean,
        nivel: FontSizeLevel
    ) {
        capture("dictado_descarga_${nombre}_${tema(dark)}_${sufijoDe(nivel)}", dark, nivel) {
            DescargaDelDictadoContenido(
                modelo = MODELO,
                estado = estado,
                onDescargar = {},
                onBorrar = {}
            )
        }
    }

    private companion object {
        /**
         * El modelo real del módulo, con sus números medidos: la pantalla
         * anuncia **43.5 MB** porque eso es lo que pesa `ggml-tiny-q8_0.bin`, no
         * los 75 del mock (que son el `tiny` sin cuantizar).
         */
        val MODELO = ModeloDeDictado(
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q8_0.bin",
            tamanoBytes = 43_537_433L,
            sha256 = "c2085835d3f50733e2ff6e4b41ae8a2b8d8110461e18821b09a15c40c42d1cca"
        )

        val BAJANDO = EstadoDelModelo.Descargando(
            AvanceDeLaDescarga(bytesBajados = 27_000_000L, bytesTotales = 43_537_433L)
        )
    }
}
