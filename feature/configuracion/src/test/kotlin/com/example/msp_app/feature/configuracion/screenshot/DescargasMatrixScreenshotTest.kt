package com.example.msp_app.feature.configuracion.screenshot

import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.feature.configuracion.ui.DescargaOpcional
import com.example.msp_app.feature.configuracion.ui.EstadoDeLaDescarga
import com.example.msp_app.feature.configuracion.ui.FilaDeDescarga
import com.example.msp_app.feature.configuracion.ui.components.DescargasSection
import org.junit.Test

/**
 * La sección "Descargas" en la matriz: claro × oscuro × 1.0/1.5/2.0.
 *
 * ## Qué hay que mirar en estas imágenes
 *
 * 1. Que el renglón del mapa **no anuncie megas** en `sin_origen`: ahí no hay
 *    paquete, y un peso inventado en la sección que existe para decir la verdad
 *    sobre los megas sería el peor lugar para mentir.
 * 2. Que a escala 2.0 el estado de la derecha **no se encime** con el título ni
 *    empuje el texto a una columna de dos letras. Es el defecto exacto que el
 *    golden a 2.0 de la pantalla del dictado ya destapó una vez ("Descargan /
 *    do" con los megas encima).
 * 3. Que "Todavía no se puede" se lea **apagado** —`onSurfaceMuted`— y no como
 *    una alarma: no se perdió nada, simplemente todavía no hay archivo.
 * 4. Que "Listo" sea el único verde. El verde es estado, nunca acción.
 * 5. Que los dos renglones tengan el mismo alto cuando dicen lo mismo: si el
 *    del mapa se encoge por no traer peso, la sección se ve rota.
 */
class DescargasMatrixScreenshotTest : ConfiguracionScreenshotTest() {

    @Test
    fun `hoy light normal`() = seccion(HOY, "hoy", false, FontSizeLevel.NORMAL)

    @Test
    fun `hoy light grande`() = seccion(HOY, "hoy", false, FontSizeLevel.GRANDE)

    @Test
    fun `hoy light muy grande`() = seccion(HOY, "hoy", false, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `hoy dark normal`() = seccion(HOY, "hoy", true, FontSizeLevel.NORMAL)

    @Test
    fun `hoy dark grande`() = seccion(HOY, "hoy", true, FontSizeLevel.GRANDE)

    @Test
    fun `hoy dark muy grande`() = seccion(HOY, "hoy", true, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `bajando light normal`() = seccion(BAJANDO, "bajando", false, FontSizeLevel.NORMAL)

    @Test
    fun `bajando light muy grande`() = seccion(BAJANDO, "bajando", false, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `bajando dark normal`() = seccion(BAJANDO, "bajando", true, FontSizeLevel.NORMAL)

    @Test
    fun `bajando dark muy grande`() = seccion(BAJANDO, "bajando", true, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `listas light normal`() = seccion(LISTAS, "listas", false, FontSizeLevel.NORMAL)

    @Test
    fun `listas light muy grande`() = seccion(LISTAS, "listas", false, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `listas dark normal`() = seccion(LISTAS, "listas", true, FontSizeLevel.NORMAL)

    @Test
    fun `listas dark muy grande`() = seccion(LISTAS, "listas", true, FontSizeLevel.MUY_GRANDE)

    // -----------------------------------------------------------------------

    private fun seccion(
        filas: List<FilaDeDescarga>,
        nombre: String,
        dark: Boolean,
        nivel: FontSizeLevel
    ) {
        capture("config_descargas_${nombre}_${tema(dark)}_${sufijoDe(nivel)}", dark, nivel) {
            DescargasSection(descargas = filas, onAbrir = {})
        }
    }

    private companion object {

        /** El peso del modelo de voz, medido: 43 537 433 B. */
        const val MEGAS_DEL_DICTADO = "43.5"

        /** El peso del extracto, medido: 25 507 515 B. */
        const val MEGAS_DEL_MAPA = "25.5"

        /**
         * **Lo que el cobrador ve HOY**, y por eso es el caso que va en las tres
         * escalas y en los dos temas: el dictado se puede bajar y el mapa no
         * tiene de dónde, porque el `.pmtiles` no está publicado en ningún
         * servidor.
         */
        val HOY = listOf(
            FilaDeDescarga(DescargaOpcional.DICTADO, MEGAS_DEL_DICTADO, EstadoDeLaDescarga.AUSENTE),
            FilaDeDescarga(DescargaOpcional.MAPA, null, EstadoDeLaDescarga.SIN_ORIGEN)
        )

        /** Una bajando y la otra cortada a medias: los dos estados "en curso". */
        val BAJANDO = listOf(
            FilaDeDescarga(
                DescargaOpcional.DICTADO,
                MEGAS_DEL_DICTADO,
                EstadoDeLaDescarga.DESCARGANDO
            ),
            FilaDeDescarga(
                DescargaOpcional.MAPA,
                MEGAS_DEL_MAPA,
                EstadoDeLaDescarga.INTERRUMPIDA
            )
        )

        /** Las dos en el teléfono. El día que el extracto se publique, esto. */
        val LISTAS = listOf(
            FilaDeDescarga(DescargaOpcional.DICTADO, MEGAS_DEL_DICTADO, EstadoDeLaDescarga.LISTA),
            FilaDeDescarga(
                DescargaOpcional.MAPA,
                MEGAS_DEL_MAPA,
                EstadoDeLaDescarga.ESPERANDO_WIFI
            )
        )
    }
}
