package com.example.msp_app.feature.configuracion.screenshot

import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.feature.configuracion.ui.DescargaOpcional
import com.example.msp_app.feature.configuracion.ui.EstadoDeLaDescarga
import com.example.msp_app.feature.configuracion.ui.FilaDeDescarga
import com.example.msp_app.feature.configuracion.ui.components.DescargasSection
import org.junit.Test

/**
 * La sección "Descargas": la geometría en la matriz de escalas y **cada estado
 * en su color**.
 *
 * ## Un renglón por imagen, y por qué cambió
 *
 * Fueron dos renglones hasta que el del mapa se fue con `:core:mapas` —47.9 MB
 * de `.so` que viajaban en el APK aunque nadie bajara las teselas—. Queda el del
 * dictado, y **una sección con dos renglones "Dictado por voz" sería una foto de
 * algo que no puede pasar** (principio 2: la forma dice la verdad), así que cada
 * imagen lleva el único renglón que existe.
 *
 * Los cuatro estados que antes viajaban montados en el renglón del mapa tienen
 * ahora cada uno su caso propio, que es más cobertura y no menos: `SIN_ORIGEN`
 * era el quinto y se fue con el mapa, porque el dictado no puede caer ahí —su
 * URL es una constante del módulo.
 *
 * ## Qué hay que mirar en estas imágenes
 *
 * 1. Que el renglón **anuncie siempre sus megas**: el número sale del paquete
 *    del módulo, y ésta es la sección que existe para decir la verdad sobre los
 *    megas.
 * 2. Que a escala 2.0 el estado **no se encime** con el título ni empuje el
 *    texto a una columna de dos letras. Es el defecto exacto que el golden a 2.0
 *    de la pantalla del dictado ya destapó una vez ("Descargan / do" con los
 *    megas encima).
 * 3. Que "A medias" se lea **ámbar** y nunca rojo: una descarga cortada no
 *    perdió lo bajado, se reanuda con `Range` (principio 14).
 * 4. Que "Listo" sea el único verde. El verde es estado, nunca acción.
 */
class DescargasMatrixScreenshotTest : ConfiguracionScreenshotTest() {

    // El caso de HOY, en las tres escalas: es la geometría la que se mira.

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

    // "Descargando" es el estado de texto más largo: va también a 2.0, que es
    // donde el golden del dictado vio partirse la palabra.

    @Test
    fun `bajando light normal`() = seccion(BAJANDO, "bajando", false, FontSizeLevel.NORMAL)

    @Test
    fun `bajando light muy grande`() = seccion(BAJANDO, "bajando", false, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `bajando dark normal`() = seccion(BAJANDO, "bajando", true, FontSizeLevel.NORMAL)

    @Test
    fun `bajando dark muy grande`() = seccion(BAJANDO, "bajando", true, FontSizeLevel.MUY_GRANDE)

    // Los tres restantes se miran por el COLOR de su estado, no por su
    // geometría: a escala normal, claro y oscuro.

    @Test
    fun `esperando light normal`() = seccion(ESPERANDO, "esperando", false, FontSizeLevel.NORMAL)

    @Test
    fun `esperando dark normal`() = seccion(ESPERANDO, "esperando", true, FontSizeLevel.NORMAL)

    @Test
    fun `a medias light normal`() = seccion(A_MEDIAS, "a_medias", false, FontSizeLevel.NORMAL)

    @Test
    fun `a medias dark normal`() = seccion(A_MEDIAS, "a_medias", true, FontSizeLevel.NORMAL)

    @Test
    fun `lista light normal`() = seccion(LISTA, "lista", false, FontSizeLevel.NORMAL)

    @Test
    fun `lista dark normal`() = seccion(LISTA, "lista", true, FontSizeLevel.NORMAL)

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

        fun fila(estado: EstadoDeLaDescarga) = listOf(
            FilaDeDescarga(DescargaOpcional.DICTADO, MEGAS_DEL_DICTADO, estado)
        )

        /** **Lo que el cobrador ve HOY**: el dictado sin bajar. */
        val HOY = fila(EstadoDeLaDescarga.AUSENTE)

        val BAJANDO = fila(EstadoDeLaDescarga.DESCARGANDO)

        val ESPERANDO = fila(EstadoDeLaDescarga.ESPERANDO_WIFI)

        val A_MEDIAS = fila(EstadoDeLaDescarga.INTERRUMPIDA)

        val LISTA = fila(EstadoDeLaDescarga.LISTA)
    }
}
