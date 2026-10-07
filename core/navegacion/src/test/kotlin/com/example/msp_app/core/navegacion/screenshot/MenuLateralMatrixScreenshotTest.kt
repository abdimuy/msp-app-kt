package com.example.msp_app.core.navegacion.screenshot

import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.navegacion.MenuFixtures
import com.example.msp_app.core.navegacion.domain.model.ActivoEnElMenu
import com.example.msp_app.core.navegacion.domain.model.CabeceraDelMenu
import com.example.msp_app.core.navegacion.domain.model.SeccionDelMenu
import com.example.msp_app.core.navegacion.ui.AperturaDelMenu
import com.example.msp_app.core.navegacion.ui.MenuLateral
import com.example.msp_app.core.navegacion.ui.components.PieDelMenu
import org.junit.Test

/**
 * Qué mirar en cada imagen:
 * 1. Ninguna fila por debajo de 50 dp (era 56 hasta el 2026-10-07) y ninguna etiqueta truncada a 2.0 (envuelve).
 * 2. "Clientes" resaltado en `brandTint`/`brand`; ningún otro.
 * 3. VENTAS plegada (completo) / ALMACÉN plegada (esperando) con su chevron a la derecha.
 * 4. Punto verde con "Todo enviado · hace 3 min"; punto ámbar con "2 pagos esperando señal" — nunca rojo.
 * 5. Buscador visible en "completo" y "esperando" (10 opciones), ausente en "sin módulos".
 * 6. Desvanecido abajo sólo si hay más contenido por desplazar.
 * 7. Los dos botones del encabezado (ocultar/mostrar dinero y tema) arriba a la derecha, completos
 *    y sin recortar, en la fila 1 — nunca comparten renglón con el avatar.
 * 8. Un nombre largo real ("María Guadalupe Hernández de la Cruz") cabe en máximo 2 renglones y
 *    termina en "…" si no alcanza; nunca empuja la zona fuera de vista ni se parte en 3+ líneas.
 * 9. `nombreDeZona` ocupa un único renglón (p. ej. "R/25"); si es `null` no se pinta nada — ni la
 *    fila, ni un respaldo "Zona N".
 */
class MenuLateralMatrixScreenshotTest : MenuLateralScreenshotTest() {

    @Test fun `completo light normal`() = completo(false, FontSizeLevel.NORMAL)

    @Test fun `completo light grande`() = completo(false, FontSizeLevel.GRANDE)

    @Test fun `completo light muy grande`() = completo(false, FontSizeLevel.MUY_GRANDE)

    @Test fun `completo dark normal`() = completo(true, FontSizeLevel.NORMAL)

    @Test fun `completo dark grande`() = completo(true, FontSizeLevel.GRANDE)

    @Test fun `completo dark muy grande`() = completo(true, FontSizeLevel.MUY_GRANDE)

    @Test fun `esperando light normal`() = esperando(false, FontSizeLevel.NORMAL)

    @Test fun `esperando light muy grande`() = esperando(false, FontSizeLevel.MUY_GRANDE)

    @Test fun `esperando dark normal`() = esperando(true, FontSizeLevel.NORMAL)

    @Test fun `esperando dark muy grande`() = esperando(true, FontSizeLevel.MUY_GRANDE)

    @Test fun `sin modulos light normal`() = sinModulos(false)

    @Test fun `sin modulos dark normal`() = sinModulos(true)

    /** Nombre largo real: debe envolver a máximo 2 renglones con elipsis, nunca 3+. */
    @Test
    fun `nombre largo light normal`() = menu(
        "nombre_largo",
        dark = false,
        nivel = FontSizeLevel.NORMAL,
        secciones = MenuFixtures.todas,
        plegadas = setOf("VENTAS"),
        cabecera = CabeceraDelMenu(
            iniciales = "MG",
            nombre = "María Guadalupe Hernández de la Cruz",
            nombreDeZona = "R/25",
            envio = MenuFixtures.TODO_ENVIADO_HACE_3
        )
    )

    /** `nombreDeZona = null`: no debe pintarse ni la fila ni un respaldo "Zona N". */
    @Test
    fun `sin zona light normal`() = menu(
        "sin_zona",
        dark = false,
        nivel = FontSizeLevel.NORMAL,
        secciones = MenuFixtures.todas,
        plegadas = setOf("VENTAS"),
        cabecera = CabeceraDelMenu(
            iniciales = "ED",
            nombre = "Efraín Domínguez Reyes",
            nombreDeZona = null,
            envio = MenuFixtures.TODO_ENVIADO_HACE_3
        )
    )

    private fun completo(dark: Boolean, nivel: FontSizeLevel) =
        menu("completo", dark, nivel, MenuFixtures.todas, setOf("VENTAS"), MenuFixtures.cabecera())

    private fun esperando(dark: Boolean, nivel: FontSizeLevel) = menu(
        "esperando",
        dark,
        nivel,
        MenuFixtures.todas,
        setOf("ALMACEN"),
        MenuFixtures.cabecera(envio = MenuFixtures.DOS_PAGOS_ESPERANDO)
    )

    private fun sinModulos(dark: Boolean) = menu(
        "sin_modulos",
        dark,
        FontSizeLevel.NORMAL,
        emptyList(),
        emptySet(),
        MenuFixtures.cabecera()
    )

    private fun menu(
        caso: String,
        dark: Boolean,
        nivel: FontSizeLevel,
        secciones: List<SeccionDelMenu>,
        plegadas: Set<String>,
        cabecera: CabeceraDelMenu
    ) {
        capture("menu_${caso}_${tema(dark)}_${sufijoDe(nivel)}", dark, nivel) {
            MenuLateral(
                cabecera = cabecera,
                secciones = secciones,
                activo = ActivoEnElMenu("COBRO", "clientes"),
                seccionesPlegadas = plegadas,
                apertura = AperturaDelMenu(),
                onTogglePlegado = { _, _ -> },
                onElegir = {},
                temaOscuro = dark,
                montosOcultos = false,
                onAlternarTema = {},
                onAlternarMontos = {},
                pie = { PieDelMenu(version = "Versión 2.19.3", onConfiguracion = {}) }
            )
        }
    }
}
