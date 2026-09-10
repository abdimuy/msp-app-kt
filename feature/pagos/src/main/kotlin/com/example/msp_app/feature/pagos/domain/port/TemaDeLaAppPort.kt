package com.example.msp_app.feature.pagos.domain.port

import kotlinx.coroutines.flow.Flow

/**
 * Puerto hacia el tema **GLOBAL** de la app (`ThemeController`, `:app`).
 *
 * Existe porque el encabezado de la lista de clientes monta el botón sol/luna
 * del design system (`MspThemeToggle`) y ese botón tiene que mover el tema de
 * la app **de verdad**, no un espejo local de la pantalla. El precedente exacto
 * es `feature.collectionreport.domain.port.ReportThemePort` —mismo contrato,
 * misma frontera, misma razón— y no se reusa aquí por lo mismo que aquél no
 * reusa `feature.configuracion.domain.port.AppThemePort`: `:feature:pagos` no
 * depende de `:feature:collectionReport` ni debería. Cada feature cruza la
 * frontera hacia `:app` por su cuenta.
 *
 * **El puerto se queda en el módulo y el adaptador se va a `:app`** (precedente
 * `LiquidacionPort` → `SettlementLiquidacionAdapter`, `ReportThemePort` →
 * `ThemeControllerReportThemePort`): `ThemeController` vive en `:app`, que este
 * módulo no puede importar. Justificado frente a YAGNI por la tercera cláusula
 * de la rúbrica (Ruling BG): **cruza módulo**.
 *
 * ## Por qué un espejo local NO alcanza, medido en otro feature
 *
 * El reporte de cobranza tuvo exactamente ese espejo local y produjo un
 * defecto: salir y volver a entrar reiniciaba el tema a claro. El KDoc de
 * `ReportThemePort` lo documenta. La lista de clientes es peor caso todavía,
 * porque **de ella se sale a cada rato**: al cliente, a la venta, al abono.
 */
interface TemaDeLaAppPort {

    /**
     * Tema oscuro vigente — reactivo, refleja **cualquier** cambio: este
     * toggle, el del cajón legado, el de Configuración, el del reporte, o el
     * sistema operativo cuando el modo global es Automático. YA RESUELTO: la
     * implementación real resuelve los 3 modos antes de emitir, así que este
     * puerto nunca expone el enum.
     */
    val oscuro: Flow<Boolean>

    /** Lectura síncrona — siembra el estado inicial del ViewModel sin un frame en claro. */
    fun oscuroAhora(): Boolean

    /**
     * Alterna el tema GLOBAL de la app y **lo persiste** (la implementación real
     * escribe `SharedPreferences`), así que sobrevive a navegar, a que la
     * pantalla se destruya y a que muera el proceso.
     */
    fun alternar()
}
