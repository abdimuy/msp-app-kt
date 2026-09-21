package com.example.msp_app.core.database.dao.localsale

/**
 * Fuente única de PRODUCCIÓN de los dos arrendamientos del candado de
 * `local_sale` (columnas `CLAIM_ID`/`CLAIM_KIND`/`CLAIMED_AT` — ver
 * [LocalSaleDao]).
 *
 * Nace en la Task 2 del plan "Corregir una venta antes de que suba"
 * (`docs/superpowers/plans/2026-09-20-editar-venta-antes-de-subir.md`): el
 * dominio de `:feature:ventaCorreccion` (`evaluarCorregibilidad`) decide si
 * una venta es corregible mirando si el candado vigente venció, y necesita
 * EXACTAMENTE los mismos dos valores que `claimForEdit`/`claimForUpload`/
 * `getUploadableSales` ya toman como parámetro. Copiarlos a mano en el
 * módulo nuevo es justo el riesgo que el brief de esa tarea nombra: "si tu
 * dominio tiene su propio 30 min escrito, los dos se despegarán". Esta es la
 * única definición en código de PRODUCCIÓN — Task 3 (casos de uso) y Task 4
 * (el subidor), los próximos llamadores reales del DAO, deben importar de
 * aquí en vez de redeclarar.
 *
 * Los tests del DAO (`LocalSaleClaimDaoTest`, `LocalSaleClaimLifecycleDaoTest`)
 * deliberadamente NO importan de aquí — ver el comentario sobre
 * `EDIT_LEASE_MS`/`UPLOAD_LEASE_MS` en ese segundo archivo: la duplicación
 * ahí es a propósito, para que cada archivo de prueba se pueda leer solo sin
 * saltar a un tercer archivo. Esa decisión es sobre legibilidad de PRUEBAS;
 * esta fuente única es sobre no tener dos definiciones de PRODUCCIÓN que
 * puedan despegarse una de otra.
 */
object LocalSaleClaimLeases {
    /** Arrendamiento del candado de EDICIÓN: 30 minutos. */
    const val EDIT_LEASE_MS: Long = 30 * 60 * 1000L

    /**
     * Arrendamiento del candado de SUBIDA: 180 segundos. No es un tope real
     * de OkHttp — un cuerpo multipart no tiene uno (ver el comentario largo
     * de `UPLOAD_LEASE_MS` en `LocalSaleClaimDaoTest`, y "Qué pasa si la
     * subida ya empezó" en el plan). Es el valor que se usa para decidir si
     * el candado de subida venció; renovarlo mientras el POST sigue en
     * vuelo es trabajo de la Task 4, no de esta constante.
     */
    const val UPLOAD_LEASE_MS: Long = 180 * 1000L
}
