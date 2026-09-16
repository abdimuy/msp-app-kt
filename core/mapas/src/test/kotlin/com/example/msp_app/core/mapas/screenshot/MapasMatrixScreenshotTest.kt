package com.example.msp_app.core.mapas.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.mapas.domain.AvanceDeLaDescarga
import com.example.msp_app.core.mapas.domain.EstadoDelExtracto
import com.example.msp_app.core.mapas.domain.ExtractoDeMapa
import com.example.msp_app.core.mapas.fake.ExtractoReal
import com.example.msp_app.core.mapas.fake.mapaDePrueba
import com.example.msp_app.core.mapas.ui.DescargaDelMapaContenido
import com.example.msp_app.core.mapas.ui.SueloDeLaRuta
import org.junit.Test

/**
 * Los dibujos nuevos del mapa, en la matriz: claro × oscuro × 1.0/1.5/2.0.
 *
 * ## Qué hay que mirar en estas imágenes
 *
 * 1. Que **la atribución se lea** sobre el lienzo — su pastilla existe justo
 *    para eso, porque debajo puede haber un lago, un parque o una manzana.
 * 2. Que a escala 2.0 la atribución **siga cabiendo** en el cuadro de 130 dp y
 *    no se salga por la derecha ni se coma el espacio del pin.
 * 3. Que el suelo sin extracto sea **liso de verdad**: si aparece cualquier
 *    trazo, el módulo está inventando calles.
 * 4. Que el pie "sin mapa también sabes dónde es" siga entero a 2.0 — es la
 *    frase que impide que el cobrador crea que la pantalla no sirve sin bajar
 *    25 MB.
 */
class MapasMatrixScreenshotTest : MapasScreenshotTest() {

    @Test
    fun `bloque sin extracto light normal`() = bloque(null, false, FontSizeLevel.NORMAL)

    @Test
    fun `bloque sin extracto dark normal`() = bloque(null, true, FontSizeLevel.NORMAL)

    @Test
    fun `bloque con extracto light normal`() = bloque(CON_MAPA, false, FontSizeLevel.NORMAL)

    @Test
    fun `bloque con extracto light grande`() = bloque(CON_MAPA, false, FontSizeLevel.GRANDE)

    @Test
    fun `bloque con extracto light muy grande`() = bloque(CON_MAPA, false, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `bloque con extracto dark normal`() = bloque(CON_MAPA, true, FontSizeLevel.NORMAL)

    @Test
    fun `bloque con extracto dark grande`() = bloque(CON_MAPA, true, FontSizeLevel.GRANDE)

    @Test
    fun `bloque con extracto dark muy grande`() = bloque(CON_MAPA, true, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `descarga sin origen light`() =
        descarga(null, EstadoDelExtracto.SinOrigen, "sin_origen", false, FontSizeLevel.NORMAL)

    @Test
    fun `descarga sin origen dark`() =
        descarga(null, EstadoDelExtracto.SinOrigen, "sin_origen", true, FontSizeLevel.NORMAL)

    @Test
    fun `descarga ausente light normal`() =
        descarga(PAQUETE, EstadoDelExtracto.Ausente, "ausente", false, FontSizeLevel.NORMAL)

    @Test
    fun `descarga ausente light grande`() =
        descarga(PAQUETE, EstadoDelExtracto.Ausente, "ausente", false, FontSizeLevel.GRANDE)

    @Test
    fun `descarga ausente light muy grande`() =
        descarga(PAQUETE, EstadoDelExtracto.Ausente, "ausente", false, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `descarga ausente dark normal`() =
        descarga(PAQUETE, EstadoDelExtracto.Ausente, "ausente", true, FontSizeLevel.NORMAL)

    @Test
    fun `descarga ausente dark grande`() =
        descarga(PAQUETE, EstadoDelExtracto.Ausente, "ausente", true, FontSizeLevel.GRANDE)

    @Test
    fun `descarga ausente dark muy grande`() =
        descarga(PAQUETE, EstadoDelExtracto.Ausente, "ausente", true, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `descarga bajando light normal`() =
        descarga(PAQUETE, BAJANDO, "bajando", false, FontSizeLevel.NORMAL)

    @Test
    fun `descarga bajando light muy grande`() =
        descarga(PAQUETE, BAJANDO, "bajando", false, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `descarga bajando dark normal`() =
        descarga(PAQUETE, BAJANDO, "bajando", true, FontSizeLevel.NORMAL)

    @Test
    fun `descarga bajando dark muy grande`() =
        descarga(PAQUETE, BAJANDO, "bajando", true, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `descarga lista light`() = descarga(PAQUETE, LISTA, "lista", false, FontSizeLevel.NORMAL)

    @Test
    fun `descarga lista dark`() = descarga(PAQUETE, LISTA, "lista", true, FontSizeLevel.NORMAL)

    // -----------------------------------------------------------------------

    private fun bloque(
        mapa: com.example.msp_app.core.mapas.domain.MapaDeLaRuta?,
        dark: Boolean,
        nivel: FontSizeLevel
    ) {
        val nombre = if (mapa == null) "mapa_bloque_sin_extracto" else "mapa_bloque_con_extracto"
        capture("${nombre}_${tema(dark)}_${sufijoDe(nivel)}", dark, nivel) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MspTheme.spacing.md)
                    .height(ALTO_DEL_BLOQUE)
            ) {
                SueloDeLaRuta(mapa = mapa, punto = null, lienzo = { _, _ -> TeselaDeMentira() })
            }
        }
    }

    /**
     * El lienzo de mentira: **un color plano que no se parece a un mapa**.
     *
     * A propósito no dibuja calles ni manzanas. Un lienzo falso con pinta de mapa
     * haría que el golden se viera "bien" y lo que se está comprobando acá no es
     * el mapa —MapLibre no corre en Robolectric— sino que la atribución se lea
     * encima de lo que sea. Un color liso y saturado es el peor caso razonable
     * para la legibilidad.
     */
    @Composable
    private fun TeselaDeMentira() {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF3F6B8C)))
    }

    private fun descarga(
        paquete: ExtractoDeMapa?,
        estado: EstadoDelExtracto,
        nombre: String,
        dark: Boolean,
        nivel: FontSizeLevel
    ) {
        capture("mapa_descarga_${nombre}_${tema(dark)}_${sufijoDe(nivel)}", dark, nivel) {
            DescargaDelMapaContenido(
                paquete = paquete,
                estado = estado,
                onDescargar = {},
                onBorrar = {}
            )
        }
    }

    private companion object {
        /** Los 130 dp del bloque de mapa del detalle de cliente. */
        val ALTO_DEL_BLOQUE = 130.dp

        val CON_MAPA = mapaDePrueba()

        /** El paquete real, con su peso medido: la pantalla anuncia 25.5 MB. */
        val PAQUETE = ExtractoDeMapa(
            url = "https://ejemplo.invalido/ruta-cobranza-z14.pmtiles",
            tamanoBytes = ExtractoReal.TAMANO_MEDIDO
        )

        val BAJANDO = EstadoDelExtracto.Descargando(
            AvanceDeLaDescarga(
                bytesBajados = 12_000_000L,
                bytesTotales = ExtractoReal.TAMANO_MEDIDO
            )
        )

        val LISTA = EstadoDelExtracto.Listo(mapaDePrueba())
    }
}
