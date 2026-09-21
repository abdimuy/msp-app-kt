package com.example.msp_app.feature.ventacorreccion.domain.port

/**
 * Reencolar la subida tras corregir (plan "Corregir una venta antes de que suba", mecanismo,
 * sentencia 9): es SIEMPRE una optimización, NUNCA un requisito de correctitud — "la fila
 * manda". Si cualquiera de los dos métodos falla o lanza, la corrección YA quedó commiteada en
 * Room; el barrido (`LocalSalesPendingSynchronizer` vía `LocalSaleDao.getUploadableSales`) la
 * recoge en la siguiente apertura de sesión sin ayuda de este puerto — ver
 * [com.example.msp_app.feature.ventacorreccion.domain.usecase.GuardarCorreccion], que envuelve
 * la llamada en `runCatching`.
 *
 * Implementación real: `WorkManagerReencolarSubidaAdapter` en `:app` (no en
 * `:feature:ventaCorreccion` — necesita referenciar `PendingLocalSalesWorker`, que sólo `:app`
 * puede ver; la dependencia entre módulos es unidireccional, `:app` → `:feature:ventaCorreccion`,
 * nunca al revés). Mismo criterio de frontera que `UserCyclePort`/`ReportThemePort` en
 * `:feature:collectionReport` (ver `CollectionReportDataModule`, "No se bindea aquí").
 */
interface ReencolarSubidaPort {
    /**
     * Cortesía al RECLAMAR: cancela el trabajo de subida que pudiera estar encolado para esta
     * venta, para no competir con la edición en curso. `cancelUniqueWork` no detiene un
     * `doWork()` ya en marcha — el fence del candado (Task 4) es quien de verdad lo frena; esto
     * sólo evita un reintento inmediato inútil mientras el dueño corrige.
     */
    fun cancelarTrabajoEncolado(saleId: String)

    /**
     * Cortesía al GUARDAR o CANCELAR: reencola la subida para no esperar a la siguiente
     * apertura de sesión. Nunca se necesita para que la corrección llegue al servidor.
     *
     * **NO es "con reemplazo"** (decía eso hasta la integración del 2026-09-21): el encolado
     * del camino del dinero es `ExistingWorkPolicy.KEEP` y nada más — ver
     * `WorkManagerReencolarSubidaAdapter.reencolar` y `WorkEnqueuePolicyGuardTest`. Da igual
     * para este puerto, porque esta llamada nunca fue un requisito de correctitud.
     */
    fun reencolar(saleId: String, userEmail: String)
}
