package com.example.msp_app.feature.visitas.domain.port

/**
 * Puerto hacia el tema **GLOBAL** de la app (`ThemeController`, `:app`), para
 * las pantallas de visita.
 *
 * ## Por qué existe
 *
 * Porque el host de la reveal circular de tema
 * (`com.example.msp_app.core.designsystem.component.MspThemeRevealHost`) **no
 * voltea el tema él mismo: lo pide**. Su mecanismo exige un orden exacto —
 * graba el frame viejo con `contentLayer.toImageBitmap()` y SOLO DESPUÉS llama
 * a `onToggleTheme()` — porque el disco que crece pinta por fuera la foto del
 * tema anterior. Si el flip ocurriera antes, la foto ya sería del tema nuevo y
 * no habría reveal: habría un cambio en seco con una animación encima que no
 * revela nada.
 *
 * Eso obliga a que el flip **se pueda pedir desde este módulo**, en el momento
 * que el host decide, y no en el momento del tap. Por eso hay un puerto y no una
 * llamada directa: `ThemeController` vive en `:app`, que `:feature:visitas` no
 * puede importar. **El puerto se queda en el módulo y el adaptador se va a
 * `:app`** (Ruling BF, tercera cláusula: cruza módulo; precedentes
 * `LiquidacionPort`, `ReportThemePort` y el `TemaDeLaAppPort` de
 * `:feature:pagos`).
 *
 * ## Por qué NO se reusa el de `:feature:pagos`
 *
 * Misma razón que ya explica el KDoc de
 * `feature.pagos.domain.port.TemaDeLaAppPort` para no reusar `ReportThemePort`,
 * y que aquél da para no reusar `AppThemePort` de `:feature:configuracion`: **un
 * feature no depende de otro feature.** Cada uno cruza la frontera hacia `:app`
 * por su cuenta, y el costo de esa duplicación es una interfaz de un solo método
 * contra el costo de atar dos módulos que hoy no se conocen.
 *
 * ## Por qué solo tiene [alternar]
 *
 * El de `:feature:pagos` además expone `oscuro` y `oscuroAhora()`, porque la
 * lista y el detalle de cliente **pintan el glifo sol/luna** (`MspThemeToggle`)
 * y necesitan saber cuál dibujar. Ninguna de las dos pantallas de visita lo
 * pinta todavía: instalan el host —para que el mecanismo esté donde tiene que
 * estar el día que el glifo llegue— pero no tienen control que dependa del
 * estado del tema. Un miembro que nadie llama es un contrato que hay que
 * mantener sin nada que lo verifique; cuando la pantalla pinte el glifo, se
 * agrega entonces.
 */
interface TemaDeLaAppPort {

    /**
     * Alterna el tema GLOBAL de la app y **lo persiste** (la implementación real
     * escribe `SharedPreferences`), así que sobrevive a navegar, a que la
     * pantalla se destruya y a que muera el proceso.
     *
     * Síncrono a propósito: el host de la reveal lo llama entre el snapshot del
     * frame viejo y el arranque de la animación del radio. Cualquier suspensión
     * ahí metería frames del tema viejo después de la foto.
     */
    fun alternar()
}
